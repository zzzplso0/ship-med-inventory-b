# shipmed

선박 의약품 재고 관리 시스템. 선박별 약품 재고를 로트 단위로 관리하고, FEFO 차감·동시성 제어·멱등성을 보장하는 백엔드.

## 설계 문서

작업 전에 반드시 읽을 것.

- `docs/requirements.md` — 무엇을: MVP 범위, 역할별 권한, 재고 판정 규칙, 엣지 케이스, 테스트 시나리오(인수 조건)
- `docs/design.md` — 어떻게: 데이터 모델(ERD, DDL), 인증·권한 판정 순서, API, 핵심 로직 의사코드, 동시성
- `docs/decisions.md` — 왜: 설계 결정과 근거, 미해결·유보 항목
- `docs/schedule.md` — 언제: 단계별 일정, 완료 기준, 착수 전 결정할 것

규칙

- 문서끼리, 또는 문서와 코드가 충돌하면 임의로 고르지 말고 사용자에게 확인.
- MVP 범위는 requirements 1장을 따른다. "이후 단계" 기능은 요청이 없으면 구현하지 않는다.
- 문서를 고칠 때 동작 기준은 requirements에, 구현 방식은 design에, 근거는 decisions에 적는다. 같은 내용을 여러 문서에 반복하지 않는다.
- 미해결 항목(decisions 3장)에 해당하는 결정이 필요하면 사용자에게 묻는다.

## 스택

- Java 17, Spring Boot 4, Gradle
- PostgreSQL 17, Flyway 마이그레이션
- Spring Data JPA (`ddl-auto: validate`), MyBatis
    - 쓰기(등록·입고·사용·취소)는 JPA, 재고 현황 같은 조회·집계는 MyBatis.
- Spring Security (세션 로그인)
- 테스트: JUnit 5, Testcontainers(PostgreSQL)

## 구조와 설정

- 패키지 루트: `com.shipmed.shipmed`
- 스키마: `src/main/resources/db/migration` (Flyway). 스키마 변경은 엔티티가 아니라 마이그레이션으로 한다.
- 로컬 전용 초기 데이터(선박 100척, 테스트 의무사관): `src/main/resources/db/seed`. `application-local.yaml`에서만 Flyway 경로에 포함되어 prod에는 적용되지 않음.
- 프로파일: `local`(기본), `prod`(DB 접속 정보는 환경변수 `DB_URL`, `DB_USER`, `DB_PASSWORD`), `test`
- JDBC 타임존은 UTC. 만료·임박 판정도 UTC 기준(decisions 부차 3번).

## 구현 시 지킬 것

- "오늘" 판정은 주입한 `Clock`으로 한다. 테스트는 고정 Clock(오늘 = 2026-07-22 UTC)을 쓴다(requirements 5장).
- 로트 잠금은 항상 `id` 오름차순(데드락 방지). 차감 순서는 잠근 뒤 FEFO(`expiry_date → received_at → id`)로 다시 정렬.
- 만료 여부는 저장하지 않고 조회 시 계산한다.
- 권한 판정 순서: 401 → 403(역할) → 403(소속 선박) → 400(형식) → 404 → 400(규칙) → 409 (design 2.2).

## Git

- 기본 브랜치 `main`, 개발 브랜치 `develop`. 작업 브랜치는 `develop`에서 분기.
- PR은 `.github/PULL_REQUEST_TEMPLATE.md` 양식을 따른다.
- `.env`는 커밋하지 않는다(gitignore 됨).
