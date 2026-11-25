# design.md - 선박 의약품 재고 관리 시스템 설계

`requirements.md`의 요구사항을 어떻게 구현할지 정의한다. 결정의 근거는 `decisions.md`.

## 1. 데이터 모델

### 1. 개념 층위

- 정책 층: `tb_stock_requirement`: 이 배가 이 약품을 최소 몇 개 비치해야 하는지.
- 물리 층: `tb_stock_lot`: 유효기한이 로트마다 다른 실제 재고.
- 이력 층: `tb_usage_record`, `tb_usage_lot_allocation`: 누가, 언제, 얼마를 어느 로트에서 사용했는지.
- 사용자: `tb_user`: 관리자와 의무사관. 의무사관은 소속 선박이 하나.

정책과 물리는 직접 FK로 잇지 않고 둘 다 `(ship, medication)`만 참조하며, 부족 여부는 조회 시점에 조인, 계산 한다.

### 2. ERD

핵심 관계는 두 가지다. `stock_requirement`(정책)와 `stock_lot`(물리)은 직접 연결 없이 각각 `(ship, medication)`을 참조한다. `usage_record` 1건은 여러 `usage_lot_allocation`을 거쳐 `stock_lot`들과 연결되며, 이것이 “사용 1건의 로트별 분배와 취소 복원”의 뼈대다.

### 3. DDL (PostgreSQL)

```
CREATE TABLE tb_ship (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE tb_user (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    login_id      VARCHAR(30)  NOT NULL,
    name          VARCHAR(30)  NOT NULL,
    password      VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    email         VARCHAR(100) NOT NULL,  -- V3에서 NULL 허용으로 추가, V5에서 NOT NULL(decisions 부차 28번)
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    
    ship_id       BIGINT REFERENCES tb_ship(id),
    
    CONSTRAINT uq_user_login UNIQUE (login_id),
    CONSTRAINT uq_user_email UNIQUE (email),  -- V3
    
    CONSTRAINT chk_user_role CHECK (role IN ('ADMIN','MEDICAL_OFFICER')),
    CONSTRAINT chk_user_ship CHECK (
        (role = 'ADMIN' AND ship_id IS NULL) OR
        (role = 'MEDICAL_OFFICER' AND ship_id IS NOT NULL))
);

CREATE TABLE tb_medication (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       VARCHAR(50) NOT NULL,
    category   VARCHAR(30),
    unit       VARCHAR(20)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE tb_stock_requirement (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    min_quantity  INT    NOT NULL CHECK (min_quantity >= 0),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    ship_id       BIGINT NOT NULL REFERENCES tb_ship(id),
    medication_id BIGINT NOT NULL REFERENCES tb_medication(id),
    
    CONSTRAINT uq_requirement UNIQUE (ship_id, medication_id)
);

CREATE TABLE tb_stock_lot (
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    lot_number         VARCHAR(50) NOT NULL,
    initial_quantity   INT  NOT NULL CHECK (initial_quantity > 0),
    remaining_quantity INT  NOT NULL,
    status             VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    discarded_reason   VARCHAR(255),
    expiry_date        DATE NOT NULL,
    received_at        TIMESTAMPTZ NOT NULL,
    discarded_at       TIMESTAMPTZ,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),

    received_by        BIGINT NOT NULL REFERENCES tb_user(id),
    discarded_by       BIGINT REFERENCES tb_user(id),
    ship_id            BIGINT NOT NULL REFERENCES tb_ship(id),
    medication_id      BIGINT NOT NULL REFERENCES tb_medication(id),
    
    CONSTRAINT uq_lot UNIQUE (ship_id, medication_id, lot_number),

    CONSTRAINT chk_lot_remaining_nonneg  CHECK (remaining_quantity >= 0),
    CONSTRAINT chk_lot_remaining_le_init CHECK (remaining_quantity <= initial_quantity),
    CONSTRAINT chk_lot_status            CHECK (status IN ('ACTIVE','DISCARDED'))
);

CREATE TABLE tb_usage_record (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    total_quantity  INT  NOT NULL CHECK (total_quantity > 0),
    status          VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    used_at         TIMESTAMPTZ  NOT NULL,
    cancelled_at    TIMESTAMPTZ,
    cancel_reason   VARCHAR(255),
    idempotency_key VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),

    cancelled_by    BIGINT REFERENCES tb_user(id),
    ship_id         BIGINT NOT NULL REFERENCES tb_ship(id),
    medication_id   BIGINT NOT NULL REFERENCES tb_medication(id),
    user_id         BIGINT NOT NULL REFERENCES tb_user(id),
    
    CONSTRAINT uq_usage_idempotency UNIQUE (user_id, idempotency_key),

    CONSTRAINT chk_usage_status CHECK (status IN ('ACTIVE','CANCELLED'))
);

CREATE TABLE tb_usage_lot_allocation (              -- 사용-로트 분배(append-only)
    id                 BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    allocated_quantity INT    NOT NULL CHECK (allocated_quantity > 0),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),

    usage_id           BIGINT NOT NULL REFERENCES tb_usage_record(id),
    lot_id             BIGINT NOT NULL REFERENCES tb_stock_lot(id),
    
    CONSTRAINT uq_alloc UNIQUE (usage_id, lot_id)  -- 같은 사용의 같은 로트 중복 분배 방지, usage_id 조회 커버
);

CREATE INDEX idx_alloc_lot ON tb_usage_lot_allocation (lot_id);
CREATE INDEX idx_usage_ship_med  ON tb_usage_record (ship_id, medication_id);
CREATE INDEX idx_usage_user_time ON tb_usage_record (user_id, used_at);
CREATE INDEX idx_usage_ship_time ON tb_usage_record (ship_id, used_at);  -- V2. 사용 이력의 선박별 시간순 조회
CREATE INDEX idx_lot_fefo                        -- 차감 대상 조회 조건과 일치하는 부분 인덱스
  ON tb_stock_lot (ship_id, medication_id, expiry_date, received_at, id)
  WHERE status = 'ACTIVE';
```

- 가용 재고, 부족, 만료, 임박의 정의는 `requirements.md` 3장.

### 4. 초기 데이터

- 첫 관리자: 모든 환경에서 서버 시작 시 `CommandLineRunner`가 만듦.
    - `ADMIN` 역할의 계정이 이미 있으면 만들지 않음. 설정값을 바꿔도 기존 관리자에는 반영되지 않음.
    - 계정 정보는 `shipmed.initial-admin`(`login-id`, `name`, `password`, `email`)으로 프로파일별 설정. prod는 환경변수 `ADMIN_LOGIN_ID`, `ADMIN_NAME`, `ADMIN_PASSWORD`, `ADMIN_EMAIL`로만 받고, 값이 없으면 서버가 시작되지 않음. local, test는 각 프로파일 yaml의 전용 값을 씀.
    - 이미 있는 관리자에는 이메일도 반영되지 않으므로, 이메일 도입 전에 만든 관리자는 계정 화면에서 채움.
- 로컬 프로파일 전용: 선박 100척, 테스트용 의무사관 계정. 스키마 마이그레이션과 경로를 분리해 prod에는 적용되지 않게 함.
    - seed 계정의 이메일은 `R__seed_users.sql`에서 `ON CONFLICT ... DO UPDATE`로 채움. repeatable 마이그레이션은 버전 마이그레이션(V3) 뒤에 실행되므로 컬럼이 있는 상태에서 돎.

### 5. 스키마 변경 이력

- V2: `idx_usage_ship_time`.
- V3: `tb_user.email`(NULL 허용) + `uq_user_email`. 기존 계정은 빈 값으로 남음.
- V4: Spring Batch 메타데이터 테이블(`BATCH_*`). `spring-batch-core`의 `schema-postgresql.sql`을 그대로 옮김. 앱 기동 시 자동 생성(`spring.batch.jdbc.initialize-schema`)은 끔.
- V5: `tb_user.email SET NOT NULL`. 모든 계정에 이메일을 채운 뒤 별도 배포로 적용(확장 후 축소, decisions 부차 28번). 빈 값이 남아 있으면 마이그레이션이 실패해 서버가 시작되지 않음.

---

## 2. 인증, 인가

- 역할별 권한과 권한 규칙은 `requirements.md` 2장.

### 1. 로그인

- 세션 기반 로그인. 로그인에 성공하면 세션 쿠키를 발급하고, 이후 요청의 사용자는 세션에서 꺼냄.
- 로그인, 로그아웃은 Security 필터가 아니라 컨트롤러에서 처리.
    - 로그인: `AuthenticationManager`로 인증하고, 기존 세션이 있으면 ID를 교체(세션 고정 방지)한 뒤 `SecurityContext`를 세션에 저장. 인증 실패는 `LOGIN_FAILED`로 변환.
    - 로그아웃: 기본 `LogoutFilter`를 끄고 컨트롤러에서 세션을 무효화.
- 세션의 principal(`CustomUserDetails`)에는 엔티티 대신 `userId`, `loginId`, `role`, `shipId` 값만 담음.
- 비밀번호는 해시로 저장. 인증이 끝나면 principal의 해시를 지워 세션에 남기지 않음.
- CSRF: 화면 경로에만 켜고 `/api/**`는 끔. 세션 쿠키는 `HttpOnly`, `SameSite=Lax`로 발급. 화면의 보안 설정은 6.2.

### 2. 권한 판정 순서

- 로그인(401) → 역할(403) → 소속 선박(403) → 형식 검증(400) → 대상 존재(404) → 규칙 검증(400) → 비즈니스 규칙(409).
    - 의무사관이 존재하지 않는 `shipId`로 요청하면 소속 선박 판정에서 403. 다른 선박의 존재 여부를 드러내지 않음.
    - 관리자는 소속 선박 판정이 없으므로 존재하지 않는 `shipId`에 404. 본문 형식도 틀렸으면 400이 먼저.
    - 사용 취소의 본인 기록 판정(403)은 대상을 조회해야 하므로 대상 존재(404) 다음에 한다(4.2).
- 판정 위치:
    - 로그인, 역할: 보안 필터(`authorizeHttpRequests`).
    - 소속 선박: `ShipAccessInterceptor`(`HandlerInterceptor`의 `preHandle`). `@Valid`는 컨트롤러 인자를 만들 때 실행되므로 서비스나 `@PreAuthorize`에서 판정하면 400이 403보다 먼저 나감.
        - `/api/ships/{shipId}/**`와 화면의 `/ships/{shipId}/**` 중 컨트롤러에 매핑된 요청만 판정하며, 없는 경로는 404. 인터셉터가 경로 변수 이름으로 값을 읽으므로 선박 API와 화면의 경로 변수는 `shipId`로 통일.
    - 형식 검증: 컨트롤러의 `@Valid` 등. 필수값 누락, 길이 초과, 비밀번호 규칙, 계정의 역할과 `shipId` 조합, 수량 0/음수, 잘못된 JSON, `Idempotency-Key` 형식, `page`·`size` 범위.
    - 대상 존재, 규칙 검증, 비즈니스 규칙: 서비스. 규칙 검증은 `EXPIRED_ON_ARRIVAL`, `RECEIVED_AT_IN_FUTURE`처럼 `Clock`이나 도메인 규칙이 필요한 것.

## 3. API 설계

### 1. 엔드포인트 요약

- 모든 API 경로는 `/api`로 시작한다.
- 사용자는 세션으로 식별. `Idempotency-Key` 헤더는 사용 기록에만 보냄.
- 상태 코드는 조회, 멱등 재요청 200, 생성 201, 입력 오류 400, 미로그인 401, 권한 없음 403, 없음 404, 비즈니스 충돌 409.

| 메서드 | 경로                                              | 기능                   | 권한                        | 주요 응답                                    |
|--------|---------------------------------------------------|------------------------|-----------------------------|----------------------------------------------|
| POST   | `/api/auth/login`                                 | 로그인                 | 누구나                      | 200 / 400 / 401                              |
| POST   | `/api/auth/logout`                                | 로그아웃               | 로그인                      | 200 / 401                                    |
| POST   | `/api/users`                                      | 계정 등록              | 관리자                      | 201 / 400 / 404 / 409 로그인 ID 중복         |
| GET    | `/api/users`                                      | 계정 목록              | 관리자                      | 200                                          |
| PATCH  | `/api/users/{userId}/email`                       | 계정 이메일 수정       | 관리자                      | 200 / 400 / 404 / 409 이메일 중복            |
| POST   | `/api/ships`                                      | 선박 등록              | 관리자                      | 201                                          |
| GET    | `/api/ships`                                      | 선박 목록              | 로그인                      | 200                                          |
| POST   | `/api/medications`                                | 약품 등록              | 관리자                      | 201                                          |
| GET    | `/api/medications`                                | 약품 목록              | 로그인                      | 200                                          |
| PUT    | `/api/ships/{shipId}/requirements/{medicationId}` | 비치 기준 설정(upsert) | 관리자                      | 200 수정 / 201 신규 / 409 신규 요청 겹침     |
| GET    | `/api/ships/{shipId}/requirements`                | 비치 기준 목록         | 관리자, 의무사관(소속 선박) | 200                                          |
| POST   | `/api/ships/{shipId}/lots`                        | 재고 입고              | 의무사관                    | 201 / 400 만료·수량·입고시각 / 409 중복 로트 |
| POST   | `/api/ships/{shipId}/usages`                      | 약품 사용 (FEFO 차감)  | 의무사관                    | 201 / 409 재고부족·키 재사용 / 200 멱등      |
| POST   | `/api/usages/{usageId}/cancellation`              | 사용 취소 (로트 복원)  | 의무사관                    | 200 / 200 멱등                               |
| GET    | `/api/ships/{shipId}/inventory`                   | 재고 현황(부족·임박)   | 관리자, 의무사관(소속 선박) | 200                                          |
| GET    | `/api/ships/{shipId}/lots`                        | 로트 목록              | 관리자, 의무사관(소속 선박) | 200 / 400 필터                               |
| POST   | `/api/ships/{shipId}/lots/{lotId}/disposal`       | 로트 폐기              | 의무사관                    | 200 / 200 멱등 / 400 사유 / 404 로트 없음    |
| GET    | `/api/ships/{shipId}/usages`                      | 사용 이력              | 관리자, 의무사관(소속 선박) | 200 / 400 필터                               |

**목록 조회 공통**

`GET /api/users`, `GET /api/ships`, `GET /api/medications`, `GET /api/ships/{shipId}/requirements`, `GET /api/ships/{shipId}/lots`, `GET /api/ships/{shipId}/usages`는 같은 페이지 응답을 쓴다. 재고 현황은 집계 결과라 적용하지 않는다. 값의 범위와 기본값은 `requirements.md` 4장.

```markdown
요청:
	?page=0&size=20
200:
	{ content:[ ... ], page, size, totalElements, totalPages, hasNext }
```

- 조회는 JPA로 한다. MyBatis는 재고 현황 같은 집계와 복잡한 조회에만 쓴다. 로트 목록과 사용 이력은 선택 필터와 여러 테이블 조인이 있어 MyBatis로 한다.
- 로트 목록(FEFO)과 사용 이력(`used_at`·`id` 내림차순)은 `PageQuery`의 `id` 오름차순 대신 각자의 정렬을 쓴다. `page`, `size` 검증은 같다.
- 범위를 벗어난 값을 조용히 보정하지 않도록 `Pageable` 자동 변환 대신 `page`, `size`를 `PageQuery`와 `@Valid @ModelAttribute`로 받아 검증한다. 기본값, 범위, 정렬은 `PageQuery`에 모은다.
- Spring의 `Page`를 그대로 직렬화하지 않고 전용 `PageResponse`로 응답한다.
- 비치 기준 목록은 약품 이름이 필요하므로 fetch join으로 N+1을 막는다.

---

### 2. 핵심 엔드포인트 상세

**약품 사용 — `POST /api/ships/{shipId}/usages`**

```
요청:
	Header: Idempotency-Key
	Body: { medicationId, quantity }

201:
	{ usageId, quantity, status:ACTIVE, usedAt, allocations:[ {lotId, quantity}, ... ] } // 로트별 분배 내역
409:
	{ error:INSUFFICIENT_STOCK, requested, available } // 전부 거부 + 가용량 안내
	{ error:IDEMPOTENCY_KEY_REUSED } // 같은 키인데 shipId·medicationId·quantity가 기존 기록과 다름
400:
	quantity <= 0
	Idempotency-Key 헤더 없음 / 빈 값 / 100자 초과
401:
	로그인 안함
403:
	관리자 / 소속이 아닌 선박
404:
	ship/medication 없음
200:
	동일 Idempotency-Key 재요청 → 기존 기록을 현재 상태로 반환 (취소됐으면 status:CANCELLED)
```

**사용 취소 — `POST /api/usages/{usageId}/cancellation`**

```
요청:
  Body:   { reason } // 필수, 최대 255자

200:
	{ usageId, status:CANCELLED, cancelledAt, cancelledBy, restored:[ {lotId, quantity}, ... ] } // cancelledBy는 취소자 name, restored는 원래 로트로 복원 내역
400:
	reason 없음 / 빈 값 / 공백만 / 255자 초과
401:
	로그인 안 함
403:
	관리자 / 본인이 기록하지 않은 사용
404:
	사용기록 없음 {USAGE_NOT_FOUND}
200:
	이미 취소됨 → 멱등(기존 취소 결과 반환)
```

### 3. 그 밖의 엔드포인트

- POST `/api/auth/login` — 로그인

```markdown
요청:
	{ loginId, password }
200:
	{ userId, loginId, role, shipId }  + 세션 쿠키
400:
	필수값 누락
401:
	{ error:LOGIN_FAILED } // 로그인 ID 또는 비밀번호 불일치. 없는 계정도 같은 응답
```

- POST `/api/auth/logout` — 로그아웃

```markdown
200: 세션 무효화
401: { error:UNAUTHORIZED } // 로그인 안 함
```

- POST `/api/users` — 계정 등록 (관리자)

```markdown
요청:
	{ loginId, name, password, role, shipId, email }
201:
	{ id, loginId, name, role, shipId, email }
400:
	필수값 누락 // 길이 초과 // 비밀번호 규칙 위반 // 이메일 형식 위반 // 의무사관인데 shipId 없음, 관리자인데 shipId 있음
404:
	ship 없음
409:
	{ error:DUPLICATE_LOGIN_ID } // 같은 loginId가 이미 있음 (uq_user_login)
	{ error:DUPLICATE_EMAIL }    // 같은 email이 이미 있음 (uq_user_email)
```

- 이메일은 `@NotBlank`, `@Email`, `@Size(max=100)`로 검증하고, 서비스에서 앞뒤 공백을 지우고 소문자로 바꿔 저장한다. 중복 확인도 바꾼 값으로 한다.

- 비밀번호 규칙은 계정 등록에만 적용한다. 첫 관리자 설정값과 로그인 요청에는 적용하지 않는다.
- `role`과 `shipId` 조합 위반의 `fieldErrors` 키는 `shipMatchingRole`이다. 잘못된 `role` 값은 JSON 변환 실패라 `fieldErrors` 없이 `INVALID_INPUT`으로 응답한다.
- 비밀번호 규칙은 `@Pattern` 하나로 검증한다. 허용 문자가 ASCII라 20자는 BCrypt 상한인 72바이트를 넘지 않으므로 바이트 길이는 따로 검증하지 않는다.

- GET `/api/users` — 계정 목록 (관리자)

```markdown
200: 페이지 응답(3.1), content:[ { id, loginId, name, role, shipId, email }, ... ]  // email은 이메일 도입 전 계정이면 null
```

- PATCH `/api/users/{userId}/email` — 계정 이메일 수정 (관리자)

```markdown
요청:
	{ email }
200:
	{ id, loginId, name, role, shipId, email }  // 지금과 같은 이메일이어도 200
400:
	이메일 누락 // 형식 위반 // 길이 초과
404:
	{ error:USER_NOT_FOUND }
409:
	{ error:DUPLICATE_EMAIL } // 다른 계정이 쓰는 이메일
```

- POST `/api/ships` — 선박 등록 (관리자)

```markdown
요청:
	{ name }
201:
	{ id, name } // 400 필수값 누락, 길이 초과
```

- GET `/api/ships` — 선박 목록

```markdown
200: 페이지 응답(3.1), content:[ { id, name }, ... ]
```

- POST `/api/ships/{shipId}/lots` — 재고 입고

```markdown
요청:
	{ medicationId, lotNumber, quantity, expiryDate, receivedAt }
	// receivedAt: 실제 입고 시각, ISO-8601 오프셋 포함(예: 2026-07-22T09:00:00Z)
201:
	{ lotId, initialQuantity, remainingQuantity, expiryDate, receivedAt, status:"ACTIVE" }
400:
	필수값 누락 // quantity <= 0 // expiryDate < 오늘 → "EXPIRED_ON_ARRIVAL" // receivedAt > 현재(Clock) → "RECEIVED_AT_IN_FUTURE", 404
409:
	{ error:DUPLICATE_LOT } // 같은 배·약품에 같은 lotNumber가 이미 있음 (uq_lot)
```

- POST `/api/medications` — 약품 등록

```markdown
요청:
	{ name, category, unit }
201:
	{ id, name, category, unit } // 400 필수값 누락, 길이 초과, 공백만 있는 category
```

- PUT `/api/ships/{shipId}/requirements/{medicationId}` — 비치 기준 설정(upsert)

```markdown
요청:
	{ minQuantity }
200:
	수정 // 201 신규  { shipId, medicationId, minQuantity }
400:
	minQuantity < 0 // 404 ship·medication 없음
409:
	{ error:REQUIREMENT_CONFLICT } // 신규 요청이 겹침 (uq_requirement)
```

- GET `/api/ships/{shipId}/lots` — 로트 목록

```markdown
요청:
	?medicationId=&status=ACTIVE|DISCARDED&page=0&size=20 // 필터는 모두 선택
200:
	페이지 응답(3.1), content:[ { lotId, medicationId, medicationName, unit, lotNumber, initialQuantity, remainingQuantity,
	                              expiryDate, expired, status, receivedAt, receivedBy, discardedAt, discardedBy, discardedReason }, ... ]
	// FEFO(expiry_date → received_at → id) 정렬. expired는 조회 시점(Clock) 계산. receivedBy·discardedBy는 사용자 name
400:
	잘못된 status·medicationId 형식 // page·size 범위
404:
	ship 없음 (관리자. 의무사관은 소속 선박 판정에서 403)
```

- POST `/api/ships/{shipId}/lots/{lotId}/disposal` — 로트 폐기

```markdown
요청:
	{ reason } // 필수, 최대 255자
200:
	{ lotId, status:"DISCARDED", discardedAt, discardedBy, discardedReason } // discardedBy는 폐기자 name
400:
	reason 없음 / 빈 값 / 공백만 / 255자 초과
401:
	로그인 안 함
403:
	관리자 / 소속이 아닌 선박
404:
	{ error:LOT_NOT_FOUND } // 그 선박에 없는 로트(다른 선박의 로트 포함)
200:
	이미 폐기됨 → 멱등(기존 폐기 결과 반환)
```

- GET `/api/ships/{shipId}/usages` — 사용 이력

```markdown
요청:
	?from=2026-07-01&to=2026-07-10&medicationId=&status=ACTIVE|CANCELLED&page=0&size=20
	// 필터는 모두 선택. from·to는 UTC 날짜, 양 끝 포함(used_at >= from 00:00Z, used_at < to+1일 00:00Z)
200:
	페이지 응답(3.1), content:[ { usageId, medicationId, medicationName, unit, quantity, status, usedAt, userId, userName,
	                              cancelledAt, cancelledBy, cancelReason, allocations:[ {lotId, lotNumber, quantity}, ... ] }, ... ]
	// used_at 내림차순, id 내림차순. userName·cancelledBy는 사용자 name
400:
	from > to // 잘못된 날짜·status·medicationId 형식 // page·size 범위
404:
	ship 없음 (관리자. 의무사관은 소속 선박 판정에서 403)
```

- 사용 이력은 페이지 대상 사용 기록과 전체 건수를 먼저 조회하고, 그 기록들의 allocation을 `usage_id IN (...)` 한 번으로 조회해 합친다. 기록과 allocation을 한 쿼리로 조인하면 페이지가 allocation 행 기준으로 잘리기 때문.
- 없는 `medicationId`로 거르면 오류 없이 빈 결과를 준다. 필터 값이라 대상 존재(404) 판정을 하지 않는다.

- GET `/api/medications`

```markdown
200: 페이지 응답(3.1), content:[ { id, name, category, unit }, ... ]
```

- GET `/api/ships/{shipId}/requirements`

```markdown
200: 페이지 응답(3.1), content:[ { medicationId, medicationName, minQuantity }, ... ]
```

- GET `/api/ships/{shipId}/inventory` — 부족·임박·폐기 대상을 한눈에

조회 대상 약품은 해당 배의 (비치 기준이 있는 약품 ∪ 로트가 있는 약품)이다. 기준만 있고 로트가 없는 약품은 `available:0`, 로트만 있고 기준이 없는 약품은 `requirementSet:false`로 나온다.

약품은 `medicationId` 오름차순, `expiringSoon`과 `expired`는 FEFO(`expiry_date → received_at → id`) 순으로 정렬한다.

`expired`(폐기 대상, requirements 3장)는 임박 로트와 같은 방식으로 따로 조회해 약품별로 붙인다. 조건만 `expiry_date < 오늘`로 다르다.

```markdown
200:
	[
	  { medicationId, medicationName, unit, available, requirementSet:true,  minQuantity, shortage, expiringSoon:[ {lotId, quantity, expiryDate} ], expired:[ {lotId, quantity, expiryDate} ] },
	  { medicationId, medicationName, unit, available, requirementSet:false, minQuantity:null, shortage:null, expiringSoon:[...], expired:[...] }  // 기준 없는 약품
	]
404: ship 없음 (관리자. 의무사관은 소속 선박 판정에서 403)
```

### 4. 공통 에러 응답

- 모든 에러 응답은 `error`(코드 문자열)와 `message`(기본 메시지)를 가진다. 위 엔드포인트의 `{ error:... }` 표기에서 `message`는 생략했다.
- 추가 정보는 최상위 필드로 응답한다. 검증 실패의 필드별 오류만 요청 필드 이름과 겹치지 않도록 `fieldErrors`로 묶는다.
- 헤더 제약 위반(`Idempotency-Key` 빈 값·100자 초과)도 `fieldErrors`로 묶고, 키는 헤더 이름이다. 헤더가 아예 없으면 `fieldErrors` 없이 `INVALID_INPUT`으로 응답한다.
- `fieldErrors`의 타입 변환 실패(`page=abc` 등)에는 내부 타입 정보가 드러나지 않도록 기본 메시지 대신 일반 문구를 넣는다.
- `error` 값은 `ErrorCode` enum 이름이다. 클라이언트가 분기에 쓰므로 공개한 뒤에는 이름을 바꾸지 않는다.
- 500 응답에는 예외 메시지나 클래스 이름을 넣지 않고, 원인은 서버 로그에 남긴다.

```markdown
409 비즈니스 규칙:
	{ error:"INSUFFICIENT_STOCK", message, requested, available }
400 요청 본문 검증 실패:
	{ error:"INVALID_INPUT", message, fieldErrors:{ receivedAt:"..." } }
500 처리되지 않은 예외:
	{ error:"INTERNAL_SERVER_ERROR", message }
```

| 상태 | `error`                                                         | 발생 조건                                                      |
|------|-----------------------------------------------------------------|----------------------------------------------------------------|
| 400  | `INVALID_INPUT`                                                 | 검증 실패, 잘못된 JSON·타입, 필수 파라미터 누락                |
| 400  | `EXPIRED_ON_ARRIVAL`, `RECEIVED_AT_IN_FUTURE`                   | 입고 규칙 위반                                                 |
| 401  | `UNAUTHORIZED`                                                  | 로그인하지 않은 요청                                           |
| 401  | `LOGIN_FAILED`                                                  | 로그인 ID 또는 비밀번호 불일치                                 |
| 403  | `ACCESS_DENIED`                                                 | 역할에 없는 기능, 소속이 아닌 선박 요청                        |
| 404  | `RESOURCE_NOT_FOUND`                                            | 없는 경로. 없는 대상은 도메인별 코드(`SHIP_NOT_FOUND` 등) 사용 |
| 404  | `SHIP_NOT_FOUND`, `MEDICATION_NOT_FOUND`                        | 없는 선박 또는 약품                                            |
| 404  | `LOT_NOT_FOUND`                                                 | 그 선박에 없는 로트                                            |
| 405  | `METHOD_NOT_ALLOWED`                                            | 지원하지 않는 HTTP 메서드                                      |
| 409  | `DUPLICATE_LOT`, `INSUFFICIENT_STOCK`, `IDEMPOTENCY_KEY_REUSED` | 비즈니스 충돌                                                  |
| 409  | `DUPLICATE_LOGIN_ID`                                            | 로그인 ID 중복                                                 |
| 409  | `REQUIREMENT_CONFLICT`                                          | 비치 기준 신규 요청이 겹침                                     |
| 500  | `INTERNAL_SERVER_ERROR`                                         | 처리되지 않은 예외                                             |

- 그 밖의 4xx(415 등)는 상태 코드를 유지하고 `error`는 `INVALID_INPUT`으로 응답한다.
- 처리 위치는 `GlobalExceptionHandler`(`@RestControllerAdvice`)다. 보안 필터에서 거부된 요청은 여기를 거치지 않으므로, 미로그인 401은 `CustomAuthenticationEntryPoint`가, 역할 위반 403은 `CustomAccessDeniedHandler`가 같은 형식으로 직접 응답한다. 소속 선박 403은 인터셉터가 `BusinessException`을 던져 `GlobalExceptionHandler`가 처리한다.

---

## 4. 핵심 비즈니스 로직

### 1. FEFO 차감

```markdown
함수 사용기록(ship_id, medication_id, user_id, 요청수량 Q, idempotency_key):
트랜잭션 시작:

    # 시작 전. 약품 존재 확인 (2.2 판정 순서: 404가 409보다 먼저)
    IF medication_id 가 tb_medication 에 없음:
        404 {MEDICATION_NOT_FOUND}; 종료

    # 0. 멱등성 체크
    IF (현재 사용자.id, idempotency_key) 가 tb_usage_record 에 존재:
        IF 기존 기록의 (ship_id, medication_id, total_quantity) != 요청값:
            409 {IDEMPOTENCY_KEY_REUSED}; 종료
        기존 사용기록 그대로 반환(200); 종료

    # 1. 가용 로트 조회 + 잠금 (id 순으로 잠가 취소 경로와 순서 일치 → 데드락 예방;
    #    취소 경로(4.2)도 id ASC로 잠근다. WHERE 조건은 READ COMMITTED에서
    #    락 대기 후 최신 커밋 값으로 재검증되므로 별도 재조회 불필요)
    가용로트 = SELECT * FROM tb_stock_lot
      WHERE ship_id=? AND medication_id=?
      AND status='ACTIVE' AND expiry_date >= 오늘(UTC) AND remaining_quantity > 0
      ORDER BY id ASC
      FOR UPDATE

    # 1-1. 잠금 뒤 멱등성 재확인
    #    같은 키 요청이 먼저 로트를 잠갔다면 1단계에서 그 커밋을 기다린 상태.
    #    READ COMMITTED라 새 조회에 먼저 커밋된 기록이 보임. 재확인하지 않으면
    #    먼저 커밋한 요청이 재고를 소진했을 때 3단계에서 409 {INSUFFICIENT_STOCK}가 나감.
    IF (현재 사용자.id, idempotency_key) 가 tb_usage_record 에 존재:
        0단계와 같은 규칙으로 200 또는 409 {IDEMPOTENCY_KEY_REUSED}; 종료

    # 2. 잠근 결과를 FEFO 순으로 재정렬 (잠금 순서와 차감 순서는 별개)
    가용로트 = 가용로트.정렬(expiry_date ASC, received_at ASC, id ASC)

    # 3. 재고 충분한지 검사 (전부 거부 + 가용량 안내)
    가용합계 = 가용로트.remaining_quantity 총합
    IF 가용합계 < Q:
        롤백 → 409 {INSUFFICIENT_STOCK, requested:Q, available:가용합계}; 종료

    # 4. 앞에서부터 차감하며 분배 기록
    남은요청 = Q; 분배목록 = []
    FOR 로트 IN 가용로트:
        IF 남은요청 == 0: break
        차감량 = min(로트.remaining_quantity, 남은요청)
        로트.remaining_quantity -= 차감량
        분배목록.추가(로트.id, 차감량); 남은요청 -= 차감량

    # 5. 사용기록 + 분배내역 저장
    사용기록 = INSERT tb_usage_record(..., user_id=현재 사용자.id, total_quantity=Q,                             status='ACTIVE', used_at=now(), idempotency_key=?)
    FOR (로트id, 차감량) IN 분배목록:
        INSERT tb_usage_lot_allocation(usage_id=사용기록.id, lot_id=로트id,                   allocated_quantity=차감량)

커밋 → 201 반환

# 6. 같은 키 동시 요청 (0단계와 1-1단계를 둘 다 통과한 경우. 마지막 방어)
#    두 번째 요청은 5단계 INSERT에서 첫 번째 커밋을 기다린 뒤 uq_usage_idempotency 위반.
#    PostgreSQL은 이 트랜잭션을 중단시키므로 같은 트랜잭션에서 조회할 수 없음.
ON uq_usage_idempotency 위반:
    롤백 (차감도 함께 취소됨)
    새 트랜잭션에서 (현재 사용자.id, idempotency_key)로 기존 기록 조회
    → 0단계와 같은 규칙으로 200 또는 409 {IDEMPOTENCY_KEY_REUSED}
```

### 2. 취소 복원

```markdown
함수 사용취소(usage_id, 현재사용자, reason):
트랜잭션 시작:

    # 0. 대상 조회 + 잠금 + 본인 확인 + 이중취소 방어
    사용기록 = SELECT * FROM tb_usage_record WHERE id=usage_id FOR UPDATE
    IF 없음: 롤백 → 404
    IF 사용기록.user_id != 현재사용자.id: 롤백 -> 403
    IF status == 'CANCELLED': 기존 결과 반환; 종료

    # 1. 분배내역 조회 + 로트 잠금(id 오름차순)
    분배목록 = SELECT * FROM tb_usage_lot_allocation WHERE usage_id=?
    관련 로트를 id ASC 순으로 FOR UPDATE

    # 2. 원래 로트로 정확히 복원 (만료·폐기됐어도 복원, 가용 판정은 조회가)
    FOR (lot_id, 차감량) IN 분배목록:
        로트.remaining_quantity += 차감량

    # 3. 상태 전이 (삭제 아님, 분배내역은 보존)
    사용기록.status='CANCELLED'; cancelled_at=now(); cancelled_by=현재사용자.id;                       cancel_reason=?

커밋 → 200 반환
```

### 3. 로트 폐기

```markdown
함수 로트폐기(ship_id, lot_id, 현재사용자, reason):
트랜잭션 시작:

    # 0. 대상 조회 + 잠금. 경로의 선박에 속한 로트만 찾음
    로트 = SELECT * FROM tb_stock_lot WHERE id=lot_id AND ship_id=ship_id FOR UPDATE
    IF 없음: 롤백 → 404 LOT_NOT_FOUND

    # 1. 이중 폐기 방어
    IF status == 'DISCARDED': 기존 결과 반환; 종료

    # 2. 상태 전이 (잔량은 그대로, 삭제 아님)
    로트.status='DISCARDED'; discarded_at=now(Clock); discarded_by=현재사용자.id; discarded_reason=?

커밋 → 200 반환
```

### 4. 만료·임박 알림 배치

- 동작 기준은 `requirements.md` 3장. Spring Batch의 Job 하나(`expiryNotificationJob`), tasklet Step 하나.
- 스케줄러가 매일 00:05 UTC(`@Scheduled(cron = "0 5 0 * * *", zone = "UTC")`)에 `JobOperator`로 실행하고, Job 파라미터로 오늘 날짜(`Clock`, UTC)를 넘긴다.
    - 같은 날짜로 다시 실행하면 이미 완료된 JobInstance라 실행되지 않아, 하루 한 통이 보장된다. 실패한 실행은 같은 날짜로 다시 실행할 수 있다.
    - test 프로파일은 스케줄러를 끈다(`shipmed.notification.scheduler-enabled: false`). 테스트는 Job을 직접 실행한다.

```markdown
함수 알림Tasklet(today):
    대상 = SELECT 선박별 로트
              WHERE status='ACTIVE' AND remaining_quantity > 0
                AND (expiry_date < today                              # 폐기 대상
                     OR expiry_date IN (today + 30일, today + 7일))   # 임박 D-30·D-7
           ORDER BY ship_id, FEFO
    의무사관 = SELECT role='MEDICAL_OFFICER' AND ship_id IN (대상의 선박)

    FOR 의무사관 IN 의무사관:
        IF email 없음: WARN 로그; 건너뜀          # 이메일 도입 전 계정. V5 이후 이 분기 삭제
        메일 = 선박, 폐기 대상 목록, 임박 목록(D-30·D-7 표시)
        TRY 발송 CATCH: ERROR 로그; 다음 의무사관  # 한 명 실패가 나머지를 막지 않음
    보낸 수·건너뛴 수·실패 수를 로그로 남김
```

- 조회는 MyBatis 쿼리 하나로 한다(로트 + 약품 이름). 대상 로트가 없는 선박의 의무사관은 조회하지 않는다.
- 발송 실패가 있어도 Step은 성공으로 끝낸다. 실패로 끝내면 같은 날 재실행 때 이미 받은 사람에게 다시 보내기 때문. 실패는 로그로 확인한다.
- 메일은 `JavaMailSender`로 보내는 텍스트 메일. 제목 `[shipmed] 선박 {이름} 폐기 대상 N건, 임박 M건`.
- 발송 설정
    - prod: Gmail SMTP(`smtp.gmail.com:587`, STARTTLS). 계정과 앱 비밀번호는 환경변수 `MAIL_USERNAME`, `MAIL_PASSWORD`로만 받는다. 보내는 주소는 `MAIL_USERNAME`.
    - local: SMTP 설정이 없으면 메일 내용을 로그로 남기는 발송기를 쓴다.
    - test: `JavaMailSender`를 mock으로 바꿔 보낸 내용을 검증한다.

---

## 5. 동시성, 정합성 전략

- 차감, 복원 대상 로트를 `SELECT ... FOR UPDATE`로 잠근 뒤 트랜잭션 안에서 처리한다.
- 사용 기록 요청은 `Idempotency-Key`로 식별하고 `UNIQUE(user_id, idempotency_key)`로 재시도 중복 차감을 차단한다.
- 로트 폐기는 로트 한 행만 잠그므로 차감·복원의 `id` 오름차순 잠금과 데드락이 생기지 않는다. 사용 차감이 폐기 중인 로트의 잠금을 기다렸다면, 잠금을 얻은 뒤 PostgreSQL이 `status='ACTIVE'` 조건을 다시 평가해 그 로트를 차감 대상에서 뺀다(READ COMMITTED).
- 같은 키 동시 요청은 로트 잠금 뒤 기존 기록을 다시 확인한다(4.1의 1-1단계). 그래도 UNIQUE 위반이 나면 롤백 후 새 트랜잭션에서 기존 기록을 조회해 반환한다(4.1의 6단계).
- 계정 등록은 먼저 `loginId` 중복을 확인해 409로 응답한다. 동시 요청이 확인을 함께 통과하면 늦은 쪽의 `uq_user_login` 위반을 같은 409로 변환한다.
    - 이메일도 같은 방식이다. 등록·이메일 수정은 먼저 중복을 확인하고, 겹친 요청의 `uq_user_email` 위반을 409 `DUPLICATE_EMAIL`로 변환한다.
    - `saveAndFlush`로 위반을 바로 일으키고, 제약 이름이 `uq_user_login`일 때만 변환한다. 다른 제약 위반은 그대로 던진다.
    - 위반 후 추가 쿼리 없이 예외를 던져 롤백하므로 새 트랜잭션은 필요 없다.
- 비치 기준은 기존 행이 있으면 수정하고, 없으면 `saveAndFlush`로 저장한다. 신규 요청이 겹쳐 `uq_requirement` 위반이 나면 409 `REQUIREMENT_CONFLICT`로 변환하고 롤백한다. 다른 제약 위반은 그대로 던진다.

---

## 6. 화면

- 화면 범위와 동작 기준은 `requirements.md` 6장.
- Thymeleaf로 서버에서 HTML을 그리고, 입력은 폼 POST로 받는다. 화면 컨트롤러(`@Controller`)는 API 컨트롤러와 같은 서비스를 호출하고 비즈니스 로직을 두지 않는다.

### 1. 화면 경로

- 화면 경로는 API 경로에서 `/api`를 뺀 구조를 따른다. 목록 화면에 등록 폼을 함께 둔다.

| 메서드   | 경로                                            | 화면                                 | 권한                                    |
|----------|-------------------------------------------------|--------------------------------------|-----------------------------------------|
| GET/POST | `/login`                                        | 로그인                               | 누구나                                  |
| POST     | `/logout`                                       | 로그아웃                             | 로그인                                  |
| GET      | `/`                                             | 역할별 첫 화면으로 redirect          | 로그인                                  |
| GET/POST | `/users`                                        | 계정 목록·등록                       | 관리자                                  |
| POST     | `/users/{userId}/email`                         | 계정 이메일 수정                     | 관리자                                  |
| GET/POST | `/ships`                                        | 선박 목록·등록                       | 목록 로그인, 등록 관리자                |
| GET/POST | `/medications`                                  | 약품 목록·등록                       | 목록 로그인, 등록 관리자                |
| GET/POST | `/ships/{shipId}/requirements`                  | 비치 기준 목록·설정                  | 목록 관리자·의무사관(소속), 설정 관리자 |
| GET/POST | `/ships/{shipId}/lots`                          | 입고                                 | 의무사관                                |
| GET/POST | `/ships/{shipId}/usages`                        | 사용                                 | 의무사관                                |
| GET      | `/ships/{shipId}/usages/result`                 | 사용 결과(취소 폼)                   | 의무사관                                |
| POST     | `/usages/{usageId}/cancellation`                | 사용 취소                            | 의무사관                                |
| GET      | `/ships/{shipId}/inventory`                     | 재고 현황                            | 관리자, 의무사관(소속 선박)             |
| GET      | `/inventory?shipId=`                            | 선박 선택 → 재고 현황 redirect       | 관리자                                  |
| GET      | `/ships/{shipId}/lots/list`                     | 로트 목록(폐기 폼)                   | 관리자, 의무사관(소속 선박)             |
| POST     | `/ships/{shipId}/lots/{lotId}/disposal`         | 로트 폐기                            | 의무사관                                |
| GET      | `/ships/{shipId}/usages/history`                | 사용 이력(선택한 기록 상세, 취소 폼) | 관리자, 의무사관(소속 선박)             |
| POST     | `/ships/{shipId}/usages/{usageId}/cancellation` | 이력 화면의 사용 취소                | 의무사관                                |

- 로트 목록과 사용 이력은 입고(`/ships/{shipId}/lots`)·사용(`/ships/{shipId}/usages`) 화면 경로와 겹치지 않게 하위 경로에 둔다.
- 관리자는 재고 현황 화면의 링크로 그 선박의 로트 목록·사용 이력에 들어간다. 의무사관은 메뉴로 들어간다.
- 재고 현황 화면의 폐기 대상 링크는 역할과 상관없이 그 선박의 로트 목록(`/ships/{shipId}/lots/list`)으로 보낸다. 폐기 폼은 로트 목록의 기존 규칙대로 의무사관에게만 보인다.
- 사용 이력의 상세는 `selected=usageId` 쿼리 파라미터로 고르고, 현재 페이지의 조회 결과에서 꺼내 그린다. 사용 기록 한 건을 읽는 API는 만들지 않는다.
- 이력 화면의 취소는 기존 화면 취소(`/usages/{usageId}/cancellation`)와 같은 서비스를 쓰고, 돌아갈 이력 화면을 알 수 있게 `shipId`가 있는 경로로 받는다.
- 목록 화면의 `page`, `size`는 API와 같이 `PageQuery`로 받는다.
- 비치 기준 설정은 API의 PUT 대신 폼 POST로 받고, `medicationId`는 폼 값으로 보낸다.
- 계정의 소속 선박과 비치 기준의 약품은 ID를 숫자로 입력한다. 선택 목록은 페이지 크기(최대 100) 때문에 전체를 담지 못한다.
- 약품 등록 폼은 `StringTrimmerEditor(true)`로 앞뒤 공백을 지우고 빈 값을 `null`로 받는다. 비밀번호가 있는 계정 폼에는 적용하지 않는다.
- 폼 DTO가 record라 `th:field`, `th:errors` 대신 필드 이름 → 메시지 Map(`fieldErrors`)을 화면에 넘긴다.

### 2. 보안

- 필터 체인은 하나로 두고, 요청이 `/api/**`인지로 나눈다.

| 항목      | `/api/**`                                  | 화면                                        |
|-----------|--------------------------------------------|---------------------------------------------|
| CSRF      | 끔                                         | 켬. `th:action` 폼에 토큰이 자동으로 들어감 |
| 미로그인  | 401 JSON(`CustomAuthenticationEntryPoint`) | `/login`으로 redirect                       |
| 역할 위반 | 403 JSON(`CustomAccessDeniedHandler`)      | 403 오류 화면                               |

- 로그인은 API 로그인과 같은 방식(1장 `AuthenticationManager`, 세션 ID 교체, `SecurityContext` 저장)을 화면 컨트롤러에서도 쓴다. 실패하면 로그인 화면에 메시지를 보여 준다. 세션은 화면과 API가 함께 쓴다.
- 로그아웃은 CSRF 토큰이 있는 POST로만 받는다.
- 화면 역할 규칙에서 `/ships/*/usages/**`는 의무사관 전용이므로, 관리자도 보는 GET `/ships/*/usages/history`는 그보다 앞에 둔다. GET `/ships/*/lots/list`는 로그인한 사용자 모두, POST `/ships/*/lots/*/disposal`은 의무사관.

### 3. 오류 처리

- 화면 컨트롤러는 한 패키지에 두고, 화면용 `@ControllerAdvice`는 `basePackages`로 그 패키지에만 적용하며 `@Order`로 `GlobalExceptionHandler`보다 먼저 둔다. `GlobalExceptionHandler`는 바꾸지 않는다. 인터셉터가 던진 소속 선박 403도 대상 핸들러에 따라 둘 중 하나로 간다.
    - `GlobalExceptionHandler`의 적용 대상을 `@RestController`로 좁히지 않는다. 없는 경로의 `NoResourceFoundException`은 컨트롤러 밖에서 나므로, 좁히면 `/api`의 404 `RESOURCE_NOT_FOUND` 응답이 사라진다.
    - 같은 이유로 화면의 없는 경로(예: `/foo`)는 JSON 404로 응답한다. 화면의 메뉴와 redirect는 없는 경로로 보내지 않으므로 주소를 직접 입력할 때만 생긴다.
- 형식 검증(400): `@Valid`와 `BindingResult`로 필드 옆에 메시지를 보여 주고 입력값을 유지한다.
- 목록 조회 필터의 오류(잘못된 상태·날짜 값, 시작일이 종료일보다 늦음, `page`·`size` 범위)는 400 오류 화면으로 보여 준다. 필터는 GET 쿼리 파라미터라 입력 폼처럼 다시 그리지 않는다.
- 규칙 검증(400)과 비즈니스 충돌(409): 컨트롤러가 `BusinessException`을 받아 같은 입력 화면에 `ErrorCode` 메시지를 보여 준다. 재고 부족은 가용 수량을 함께 보여 준다.
- 403, 404, 500: 오류 화면. 500 화면에 예외 정보를 넣지 않는다.

### 4. 제출 처리

- 성공한 POST는 redirect한다(Post-Redirect-Get). 등록은 목록으로, 입고는 연속 입력을 위해 입고 화면으로 돌아와 완료 메시지를 보여 준다.
- 사용 폼은 화면을 그릴 때 UUID를 만들어 `Idempotency-Key` hidden 값으로 넣는다. 같은 폼을 두 번 제출하면 같은 키라 4.1의 멱등 처리로 한 번만 차감된다.
- 사용 결과(`usageId`, 분배 내역)는 flash attribute로 결과 화면에 넘기고, 결과 화면은 취소 폼(`reason`)을 보여 준다. 새로고침해서 flash 값이 없으면 사용 화면으로 redirect한다. 사용 기록을 다시 조회하는 API는 만들지 않는다.
- 취소에 성공하면 복원 내역을 flash attribute로 넘기고 사용 화면으로 redirect한다. 취소 사유 검증에 실패하면 결과 데이터 없이 취소 폼만 다시 보여 준다.
- 입고 폼의 유효기한은 `date`, 입고 시각은 `datetime-local` 입력(분 단위)으로 받고 입고 시각은 UTC로 본다. 기본값은 주입한 `Clock`의 현재 시각.
- 사용 폼은 오류로 다시 그릴 때도 새 `Idempotency-Key`를 만든다. 오류면 기록이 생기지 않았으므로 안전하다.
- 계정 이메일 수정은 계정 목록의 각 행에 폼을 두고, 성공·실패 모두 계정 목록으로 redirect해 메시지를 보여 준다(flash). 폐기 폼과 같은 방식.
- 폐기에 성공하면 로트 목록으로 redirect하고 완료 메시지를 보여 준다. 사유 검증에 실패하면 로트 목록으로 redirect하고 해당 로트 옆에 메시지를 보여 준다(flash). 필터·페이지는 유지하지 않는다.
- 이력 화면에서 취소에 성공하면 `selected`를 그 기록으로 둔 이력 화면으로 redirect하고 완료 메시지를 보여 준다. 사유 검증에 실패해도 같은 화면으로 redirect하고 메시지를 보여 준다(flash).
- 이 redirect는 취소 전의 필터와 페이지(`from`, `to`, `medicationId`, `status`, `page`, `size`)를 유지한다. 상세는 현재 페이지의 조회 결과에서 꺼내므로, 조건이 바뀌면 취소한 기록이 결과에서 빠져 상세가 사라지기 때문. 취소 폼이 현재 조회 조건을 함께 보낸다.
