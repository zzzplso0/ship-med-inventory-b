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
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    
    ship_id       BIGINT REFERENCES tb_ship(id),
    
    CONSTRAINT uq_user_login UNIQUE (login_id),
    
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
CREATE INDEX idx_lot_fefo                        -- 차감 대상 조회 조건과 일치하는 부분 인덱스
  ON tb_stock_lot (ship_id, medication_id, expiry_date, received_at, id)
  WHERE status = 'ACTIVE';
```

- 가용 재고, 부족, 만료, 임박의 정의는 `requirements.md` 3장.

### 4. 초기 데이터

- 첫 관리자: 모든 환경에서 서버 시작 시 `CommandLineRunner`가 만듦.
- 로컬 프로파일 전용: 선박 100척, 테스트용 의무사관 계정. 스키마 마이그레이션과 경로를 분리해 prod에는 적용되지 않게 함.

---

## 2. 인증, 인가

- 역할별 권한과 권한 규칙은 `requirements.md` 2장.

### 1. 로그인

- 세션 기반 로그인. 로그인에 성공하면 세션 쿠키를 발급하고, 이후 요청의 사용자는 세션에서 꺼냄.
- 비밀번호는 해시로 저장.
- CSRF: MVP는 화면 없이 API만 제공하므로 CSRF 보호를 끄고, 세션 쿠키는 `HttpOnly`, `SameSite=Lax`로 발급. 화면(Thymeleaf) 단계에서 CSRF 보호를 켬.

### 2. 권한 판정 순서

- 로그인(401) → 역할(403) → 소속 선박(403) → 형식 검증(400) → 대상 존재(404) → 규칙 검증(400) → 비즈니스 규칙(409).
    - 의무사관이 존재하지 않는 `shipId`로 요청하면 소속 선박 판정에서 403. 다른 선박의 존재 여부를 드러내지 않음.
    - 관리자는 소속 선박 판정이 없으므로 존재하지 않는 `shipId`에 404. 본문 형식도 틀렸으면 400이 먼저.
    - 사용 취소의 본인 기록 판정(403)은 대상을 조회해야 하므로 대상 존재(404) 다음에 한다(4.2).
- 판정 위치:
    - 로그인, 역할: 보안 필터(`authorizeHttpRequests`).
    - 소속 선박: `HandlerInterceptor`의 `preHandle`. `@Valid`는 컨트롤러 인자를 만들 때 실행되므로 서비스나 `@PreAuthorize`에서 판정하면 400이 403보다 먼저 나감.
    - 형식 검증: 컨트롤러의 `@Valid` 등. 필수값 누락, 수량 0/음수, 잘못된 JSON, `Idempotency-Key` 형식.
    - 대상 존재, 규칙 검증, 비즈니스 규칙: 서비스. 규칙 검증은 `EXPIRED_ON_ARRIVAL`, `RECEIVED_AT_IN_FUTURE`처럼 `Clock`이나 도메인 규칙이 필요한 것.

## 3. API 설계

### 1. 엔드포인트 요약

- 사용자는 세션으로 식별. `Idempotency-Key` 헤더는 사용 기록에만 보냄.
- 상태 코드는 조회, 멱등 재요청 200, 생성 201, 입력 오류 400, 미로그인 401, 권한 없음 403, 없음 404, 비즈니스 충돌 409.

| 메서드 | 경로                                          | 기능                   | 권한     | 주요 응답                               |
|--------|-----------------------------------------------|------------------------|----------|-----------------------------------------|
| POST   | `/auth/login`                                 | 로그인                 | 누구나   | 200 / 401                               |
| POST   | `/auth/logout`                                | 로그아웃               | 로그인   | 200                                     |
| POST   | `/users`                                      | 계정 등록              | 관리자   | 201                                     |
| GET    | `/users`                                      | 계정 목록              | 관리자   | 200                                     |
| POST   | `/ships`                                      | 선박 등록              | 관리자   | 201                                     |
| GET    | `/ships`                                      | 선박 목록              | 로그인   | 200                                     |
| POST   | `/medications`                                | 약품 등록              | 관리자   | 201                                     |
| GET    | `/medications`                                | 약품 목록              | 로그인   | 200                                     |
| PUT    | `/ships/{shipId}/requirements/{medicationId}` | 비치 기준 설정(upsert) | 관리자   | 200 수정 / 201 신규                     |
| GET    | `/ships/{shipId}/requirements`                | 비치 기준 목록         | 관리자, 의무사관(소속 선박) | 200                                     |
| POST   | `/ships/{shipId}/lots`                        | 재고 입고              | 의무사관 | 201 / 400 만료·수량·입고시각 / 409 중복 로트 |
| POST   | `/ships/{shipId}/usages`                      | 약품 사용 (FEFO 차감)  | 의무사관 | 201 / 409 재고부족·키 재사용 / 200 멱등 |
| POST   | `/usages/{usageId}/cancellation`              | 사용 취소 (로트 복원)  | 의무사관 | 200 / 200 멱등                          |
| GET    | `/ships/{shipId}/inventory`                   | 재고 현황(부족·임박)   | 관리자, 의무사관(소속 선박) | 200                                     |

---

### 2. 핵심 엔드포인트 상세

**약품 사용 — `POST /ships/{shipId}/usages`**

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
	동일 Idempotency-Key 재요청 → 기존 201 본문 반환
```

**사용 취소 — `POST /usages/{usageId}/cancellation`**

```
요청:
  Body:   { reason }

200:
	{ usageId, status:CANCELLED, cancelledAt, cancelledBy, restored:[ {lotId, quantity}, ... ] } // 원래 로트로 복원 내역
401:
	로그인 안 함
403:
	관리자 / 본인이 기록하지 않은 사용
404:
	사용기록 없음
200:
	이미 취소됨 → 멱등(기존 취소 결과 반환)
```

### 3. 그 밖의 엔드포인트

- POST `/auth/login` — 로그인

```markdown
요청:
	{ loginId, password }
200:
	{ userId, loginId, role, shipId }  + 세션 쿠키
401:
	로그인 ID 또는 비밀번호 불일치
```

- POST `/auth/logout` — 로그아웃

```markdown
200: 세션 무효화
```

- POST `/users` — 계정 등록 (관리자)

```markdown
요청:
	{ loginId, name, password, role, shipId }
201:
	{ id, loginId, name, role, shipId }
400:
	필수값 누락 // 의무사관인데 shipId 없음, 관리자인데 shipId 있음
404:
	ship 없음
```

- GET `/users` — 계정 목록 (관리자)

```markdown
200: [ { id, loginId, name, role, shipId }, ... ]
```

- POST `/ships` — 선박 등록 (관리자)

```markdown
요청:
	{ name }
201:
	{ id, name } // 400 필수값 누락
```

- GET `/ships` — 선박 목록

```markdown
200: [ { id, name }, ... ]
```

- POST `/ships/{shipId}/lots` — 재고 입고

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

- POST `/medications` — 약품 등록

```markdown
요청:
	{ name, category, unit }
201:
	{ id, name, category, unit } // 400 필수값 누락
```

- PUT `/ships/{shipId}/requirements/{medicationId}` — 비치 기준 설정(upsert)

```markdown
요청:
	{ minQuantity }
200:
	수정 // 201 신규  { shipId, medicationId, minQuantity }
400:
	minQuantity < 0 // 404 ship·medication 없음
```

- POST `/lots/{lotId}/disposal` — 로트 폐기 **(이후 단계, MVP 미포함)**

```markdown
요청:
	{ reason }
200:
	{ lotId, status:"DISCARDED", discardedAt, discardedBy }
404: // 200 이미 폐기됨 → 멱등
```

- GET `/medications`

```markdown
200: [ { id, name, category, unit }, ...]
```

- GET `/ships/{shipId}/requirements`

```markdown
200: [ { medicationId, medicationName, minQuantity }, ...]
```

- GET `/ships/{shipId}/inventory` — 부족·임박을 한눈에

조회 대상 약품은 해당 배의 (비치 기준이 있는 약품 ∪ 로트가 있는 약품)이다. 기준만 있고 로트가 없는 약품은 `available:0`, 로트만 있고 기준이 없는 약품은 `requirementSet:false`로 나온다.

```markdown
200:
	[
	  { medicationId, medicationName, unit, available, requirementSet:true,  minQuantity, shortage, expiringSoon:[ {lotId, quantity, expiryDate} ] },
	  { medicationId, medicationName, unit, available, requirementSet:false, minQuantity:null, shortage:null, expiringSoon:[...] }  // 기준 없는 약품
	]
```

### 4. 공통 에러 응답

- 모든 에러 응답은 `error`(코드 문자열)와 `message`(기본 메시지)를 가진다. 위 엔드포인트의 `{ error:... }` 표기에서 `message`는 생략했다.
- 추가 정보는 최상위 필드로 응답한다. 검증 실패의 필드별 오류만 요청 필드 이름과 겹치지 않도록 `fieldErrors`로 묶는다.
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
| 404  | `RESOURCE_NOT_FOUND`                                            | 없는 경로. 없는 대상은 도메인별 코드(`SHIP_NOT_FOUND` 등) 사용 |
| 405  | `METHOD_NOT_ALLOWED`                                            | 지원하지 않는 HTTP 메서드                                      |
| 409  | `DUPLICATE_LOT`, `INSUFFICIENT_STOCK`, `IDEMPOTENCY_KEY_REUSED` | 비즈니스 충돌                                                  |
| 500  | `INTERNAL_SERVER_ERROR`                                         | 처리되지 않은 예외                                             |

- 그 밖의 4xx(415 등)는 상태 코드를 유지하고 `error`는 `INVALID_INPUT`으로 응답한다.
- 처리 위치는 `GlobalExceptionHandler`(`@RestControllerAdvice`)다. 컨트롤러 밖의 보안 필터에서 거부된 401·403은 여기를 거치지 않으며, 응답 형식은 인증 단계에서 정한다.

---

## 4. 핵심 비즈니스 로직

### 1. FEFO 차감

```markdown
함수 사용기록(ship_id, medication_id, user_id, 요청수량 Q, idempotency_key):
트랜잭션 시작:

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

# 6. 같은 키 동시 요청 (0단계를 둘 다 통과한 경우)
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

---

## 5. 동시성, 정합성 전략

- 차감, 복원 대상 로트를 `SELECT ... FOR UPDATE`로 잠근 뒤 트랜잭션 안에서 처리한다.
- 사용 기록 요청은 `Idempotency-Key`로 식별하고 `UNIQUE(user_id, idempotency_key)`로 재시도 중복 차감을 차단한다.
- 같은 키 동시 요청으로 UNIQUE 위반이 나면 롤백 후 새 트랜잭션에서 기존 기록을 조회해 반환한다(4.1의 6단계).
