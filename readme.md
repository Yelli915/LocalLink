# LocalLink — B2G2B MSA 기반 지역 공동 물류 플랫폼

> **LocalLink** 는 소상공인과 지자체를 잇는 **지역 공동 물류 플랫폼(B2G2B)** 을 마이크로서비스 아키텍처로 구현한 프로젝트입니다.
> 서비스 디스커버리(Eureka), API Gateway를 통한 OAuth2/JWT 인증 위임, Kafka 기반 이벤트 드리븐 통신,
> 분산 트랜잭션(Saga), 폴리글랏(Java + Python) 서비스, 컨테이너 오케스트레이션까지
> 백엔드·프론트엔드·인프라 전 영역의 패턴을 하나의 도메인 흐름 안에서 end-to-end로 다뤘습니다.

---

## 목차

1. [프로젝트 개요](#1-프로젝트-개요)
2. [아키텍처](#2-아키텍처)
3. [기술 스택 상세 & 프로젝트 내 사용처](#3-기술-스택-상세--프로젝트-내-사용처)
4. [아키텍처 패턴 카탈로그](#4-아키텍처-패턴-카탈로그)
5. [핵심 요청 흐름](#5-핵심-요청-흐름)
6. [설계 의사결정](#6-설계-의사결정)
7. [테스트 · 관측성 · 문서화](#7-테스트--관측성--문서화)
8. [한계와 개선 로드맵](#8-한계와-개선-로드맵)
9. [확장 시나리오](#9-확장-시나리오)
10. [실행 방법](#10-실행-방법)
11. [포트 맵 · 참고 문서](#11-포트-맵--참고-문서)

---

## 1. 프로젝트 개요

| 항목 | 내용 |
|---|---|
| 프로젝트명 | **LocalLink** (B2G2B · MSA 기반 지역 공동 물류 플랫폼, 개인 프로젝트) |
| 도메인 | 지역 공동물류 플랫폼 — 지자체 담당자가 공동물류 프로그램을 개설하고, 소상공인이 참여를 신청·정산 |
| 규모 | 8개 컨테이너 (도메인 서비스 4 + 추천 서비스 1 + 인프라 3), 폴리글랏(Java 21 / Python 3) |
| 통신 | 동기 REST(WebClient) + 비동기 이벤트(Kafka) 혼합 |
| 배포 | Docker Compose 단일 커맨드로 전체 스택 기동, 서비스별 멀티스테이지 Dockerfile |
| 담당 범위 | `eureka-server`(Spring Cloud Netflix Eureka 기본 구성)를 제외한 전 구성요소를 직접 구현했습니다. ① 프론트엔드 도메인 재설계 ② 정산 금액 정합성 수정 ③ 신청 실패 시 재신청 불가 버그 수정 ④ 이벤트 처리 신뢰성 개선 ⑤ 상태 전이 원자화·정산 결과 불명 처리 ⑥ 서비스 간 `/internal/**` 토큰 보호 ⑦ 서비스별 계약·동시성 테스트 |

### 도메인 모델과 용어

백엔드부터 프론트엔드까지(서비스명·패키지·REST 경로·DB 스키마·Kafka 토픽·화면 문구) 같은 도메인 용어를 씁니다.

| 개념 | 화면 표시 | 백엔드 식별자 |
|---|---|---|
| 지자체 담당자 | 지자체 담당자 (프로그램 개설) | `User.role = GOV_OFFICER`, `officerId` |
| 소상공인 | 소상공인 (프로그램 참여 신청) | `User.role = MERCHANT` |
| 공동물류 프로그램 | 공동물류 프로그램 | `program-service`, `/api/programs`, `programs` 테이블, `Program` |
| 참여신청 (PENDING→ACTIVE Saga) | 참여신청 | `participation-service`, `/api/participations`, `participations` 테이블 |
| 정산 | 정산 (배송비 + 지자체 지원금) | `settlement-service`, `/api/settlements`, `settlements` 테이블 |
| 추천 프로그램 | 추천 프로그램 | `recommend-service`, `/api/recommend/{userId}` |
| 배송유형 | 당일 공동배송, 정기 묶음배송 등 | `Program.Category`: `SAME_DAY`, `REGULAR_BUNDLE`, `COLD_CHAIN`, `TRADITIONAL_MARKET`, `LOCAL_COMMERCE`, `SAFE_DELIVERY`, `SHARED_STORAGE`, `OTHER` |
| 이벤트 | — | Kafka 토픽 `settlement.completed`, `participation.completed` |

---

## 2. 아키텍처

```
┌─────────────┐     MariaDB (locallink_db)  +  Kafka                 ← 인프라 계층
│  인프라 부팅 │            │
│   순서 강제  │     eureka-server  :8761                          ← 서비스 디스커버리
│(depends_on) │            │
└─────────────┘     auth-server  :9000  (OAuth2 Authorization Server, JWKS 발급)
                            │
                    api-gateway  :8080  (JWT 검증 → X-User-Id 헤더 릴레이)
                            │
        ┌───────────────────┼────────────────────┬───────────────────┐
   user-service       program-service      participation-service     settlement-service
     :8081               :8082               :8083 (호출 조정)    :8084
                                               │  ▲                    │
                                        WebClient│  │Kafka        Kafka │
                                               ▼  │  settlement.completed  │
                                          recommend-service  :8085  ◄───┘
                                          (FastAPI, participation.completed 소비)
                            │
                     vue-frontend  :3000 (dev) / Nginx (prod)
```

- **부팅 순서**를 `docker-compose.yml`의 `depends_on`으로 인프라 → 디스커버리 → 인증 → 게이트웨이 → 도메인 → 추천 순으로 강제.
- **인증 검증 집중** — 도메인 서비스는 JWT를 재검증하지 않고, 게이트웨이가 넘긴 `X-User-Id` 헤더만 신뢰. (recommend-service만 예외적으로 자체 JWT 검증)
- **participation-service가 흐름의 중심** — program/settlement-service를 `WebClient`(Eureka 서비스 ID 기반, `@LoadBalanced`)로 동기 호출하고 Kafka 토픽 2개를 produce/consume. 중앙 Saga 오케스트레이터(상태 머신)는 없습니다.
- **폴리글랏** — 도메인 로직은 Spring Boot(Java 21), 추천 로직은 FastAPI(Python)로 분리, 동일한 Eureka·Kafka·JWKS 생태계에 참여.

---

## 3. 기술 스택 상세 & 프로젝트 내 사용처

> 각 기술이 **어디서, 왜** 쓰였는지 매핑했습니다. 키워드는 각 표 아래 정리.

### 3-1. 백엔드 — Spring 생태계

| 기술 | 버전 | 프로젝트 내 사용처 |
|---|---|---|
| Java | 21 (toolchain) | 전 도메인 서비스 |
| Spring Boot | 3.4.5 | user / program / participation / settlement / eureka-server |
| Spring Cloud | 2024.0.0 | BOM 관리, Netflix Eureka 통합 |
| Spring Cloud Netflix Eureka | — | 각 서비스가 Eureka Client로 자기 등록, `prefer-ip-address` 설정 |
| Spring Security + OAuth2 Resource Server | — | 각 서비스가 리소스 서버로 `jwk-set-uri` / `issuer-uri` 검증 설정 보유 (학습·시연용 단순화로 일부 `permitAll`) |
| Spring Data JPA + Hibernate | — | 엔티티 매핑, `AuditingEntityListener`로 `createdAt/updatedAt` 자동 기록, `ddl-auto: update` |
| Spring WebFlux (WebClient) | — | participation-service → program/settlement-service 동기 서비스 간 호출 (`.block()`) |
| Spring for Apache Kafka | — | participation/settlement-service의 Producer/Consumer, `@KafkaListener`, `NewTopic` 빈으로 토픽 자동 생성 |
| Spring Boot Validation (Jakarta) | — | 요청 DTO 유효성 검증 |
| Spring Boot Actuator | — | `/actuator/health`, `/actuator/info` 노출, health show-details |
| springdoc-openapi (Swagger UI) | 2.7.0 | 각 서비스 `/swagger-ui.html`, `/api-docs` API 명세 자동화 |
| Lombok | — | 보일러플레이트 제거 (`@Builder`, `@Getter` 등) |
| MariaDB JDBC | — | 공유 `locallink_db` 접속 |

`키워드`: Java 21, Spring Boot 3, Spring Cloud, Spring Security, OAuth2, JWT, Resource Server, JPA, Hibernate, JPA Auditing, WebClient, Spring Kafka, Bean Validation, Actuator, OpenAPI/Swagger, Gradle, 멀티모듈 빌드

### 3-2. 메시징 — Apache Kafka

| 요소 | 내용 |
|---|---|
| 토픽 | `settlement.completed`, `participation.completed` (파티션 3 / 복제 1, 앱 기동 시 `NewTopic` 빈으로 자동 생성) |
| 메시지 키 | `userId` — 같은 사용자 이벤트의 순서 보장 + 파티션 분배 |
| 직렬화 | `JsonSerializer` + `spring.json.add.type.headers: false` (타입 헤더 미포함) |
| 역직렬화 | `ErrorHandlingDeserializer`로 감싼 `JsonDeserializer`, `default.type: HashMap` → `Map<String,Object>` 파싱 |
| 컨슈머 그룹 | `participation-service` (단일 그룹 → 인스턴스 다중 기동 시 파티션 단위 분산 소비) |
| 발행 방식 | settlement는 `.get(10s)`로 브로커 ack 동기 대기(검증 편의), participation는 비동기 `whenComplete` |
| 오프셋 | `auto-offset-reset: earliest` |

`키워드`: Kafka, Event-Driven Architecture, Pub/Sub, Consumer Group, 파티셔닝, 메시지 키 기반 순서 보장, JSON 직렬화 계약, 에러 핸들링 역직렬화, at-least-once 전달 의미

### 3-3. 추천 서비스 — Python / FastAPI

| 기술 | 버전 | 사용처 |
|---|---|---|
| FastAPI | 0.115 | `GET /api/recommend/{userId}` 라우터, 의존성 주입 기반 보안 |
| Uvicorn (standard) | 0.32 | ASGI 서버 |
| Pydantic v2 + pydantic-settings | 2.10 / 2.6 | 응답 스키마, 계층적 설정 로딩(env > .env > 기본값) |
| httpx | 0.28 | program/participation-service 비동기 HTTP 클라이언트 |
| py-eureka-client | 0.11 | 기동 시 Eureka 자체 등록 (Java 생태계에 Python 서비스 편입) |
| kafka-python-ng | 2.2 | 백그라운드 스레드로 `participation.completed` 소비 |
| python-jose[cryptography] | 3.3 | 동일 JWKS로 JWT 서명 검증 (게이트웨이 헤더를 신뢰하지 않고 자체 검증) |
| python-dotenv | 1.0 | 로컬 개발 환경 변수 |

추천 로직: 참여 이력 없음 → 인기순(참여 건수 desc) 상위 5 / 있음 → `Counter.most_common` 최빈 카테고리 기반 추천.

`키워드`: Python, FastAPI, ASGI, Pydantic, 비동기 I/O(async/await), httpx, JWT 검증(JWKS), 폴리글랏 MSA, 규칙 기반 추천, 콘텐츠 기반 추천

### 3-4. 프론트엔드 — Vue 3 SPA

| 기술 | 버전 | 사용처 |
|---|---|---|
| Vue 3 (Composition API) | 3.4 | `src/views/*` 라우팅 페이지, `src/components/*` 재사용 컴포넌트 |
| Vite | 8 | 개발 서버(HMR) / 프로덕션 번들 |
| Pinia | 2 | `auth`(토큰·사용자), `program`(목록·라벨 맵) 상태 관리 |
| Vue Router | 4 | `requiresAuth` / `officerOnly` 네비게이션 가드로 라우트 접근 제어 |
| Axios | 1.6 | 인터셉터로 `Authorization: Bearer` 자동 첨부, 401 재로그인 처리 |
| Pretendard | 1.3 | 한글 최적화 웹폰트 |

- **OAuth2 Authorization Code 클라이언트** 구현 (authorize 리다이렉트 → `/callback` code 교환 → 토큰 `sessionStorage` 저장).
- **도메인 라벨 맵** (`store/program.js`의 `categoryLabelMap`/`thumbnailMap` 패턴)으로 백엔드 필드를 공동물류 용어로 표시 계층에서만 변환.
- 배송 유형 축, 공통 사이드바, 정산 상태 폴링 등 도메인 특화 UI.

`키워드`: Vue 3, Composition API, Vite, Pinia, Vue Router, 라우트 가드, Axios 인터셉터, SPA, OAuth2 Authorization Code, 세션 스토리지 토큰 관리, 상태 폴링, 프론트엔드 도메인 모델링

### 3-5. 인프라 · DevOps

| 기술 | 사용처 |
|---|---|
| Docker | 서비스별 Dockerfile (Java: Gradle 빌드 → JRE 런타임 멀티스테이지 / Python: slim 이미지 / Vue: 빌드 → Nginx) |
| Docker Compose | 8개 컨테이너 오케스트레이션, `depends_on`으로 부팅 순서, 사용자 정의 네트워크(`locallink-net`), 볼륨 |
| Nginx | 프로덕션 프론트엔드 정적 서빙 + SPA 폴백 |
| MariaDB init script | `init-db/01_init.sql`이 첫 기동 시 자동 실행 (`/docker-entrypoint-initdb.d` 마운트) |
| Gradle Wrapper | 서비스별 독립 빌드 (`./gradlew build/test/bootRun`) |
| prebuilt 이미지 배포 | `infra-images.tar` → `docker load` (auth-server / api-gateway) |

`키워드`: Docker, 멀티스테이지 빌드, Docker Compose, 컨테이너 네트워킹, 서비스 의존성 관리, 12-Factor(환경변수 기반 설정 오버라이드), Nginx 리버스 프록시/정적 서빙, DB 초기화 자동화, 이미지 아티팩트 배포

---

## 4. 아키텍처 패턴 카탈로그

> 이 프로젝트에서 확인할 수 있는 MSA 패턴들. (△ = 의도적으로 미적용, 개선 로드맵에 존재)

| 패턴 | 이 프로젝트에서의 구현 |
|---|---|
| **Service Discovery + Client-side LB** | Netflix Eureka. participation-service → program/settlement-service 호출은 `@LoadBalanced WebClient`와 `http://<서비스ID>/...`(포트 없음)로 Eureka 조회·클라이언트 사이드 로드밸런싱을 탑니다. 그 외 호출(recommend-service → program/participation 등)은 컨테이너 DNS 직접 호출 |
| **API Gateway** | Spring Cloud Gateway. 라우팅 + 인증 검증 단일 지점 |
| **Token Relay / 인증 위임** | 게이트웨이가 JWT 검증 후 `X-User-Id` 헤더로 신원 릴레이, 하위 서비스는 재검증 없음 |
| **OAuth2 Resource Server** | 각 서비스가 JWKS로 토큰 서명 검증 (Java + Python 공통) |
| **동기 요청 + 비동기 완료 이벤트 혼합 (Saga 유사)** | participation가 settlement를 동기 REST로 호출(요청)하고, 완료 후 상태 전이는 `settlement.completed` 이벤트로 비동기 처리. 순수 Choreography Saga도, 중앙 오케스트레이터도 아닌 혼합 구조 |
| **Event-Driven Architecture** | `settlement.completed` / `participation.completed` 도메인 이벤트 |
| **Asynchronous State Transition** | 신청 API는 즉시 `PENDING` 응답, `ACTIVE` 전이는 이벤트 소비 후 비동기 |
| **Transaction boundary 분리 + 보상 처리** | `@Transactional(REQUIRES_NEW)`로 PENDING 행을 독립 커밋하고, 정산 요청 실패 시 `FAILED`로 보상(재신청 시 같은 행을 PENDING으로 복구), 오래된 PENDING은 스케줄러가 `FAILED`로 정리 |
| **읽기 경로 분리 (CQRS 지향)** | "내 참여신청" 조회 시 participation 데이터 + program-service 조인을 read-time에 조립 |
| **Internal API (서비스 전용 엔드포인트)** | `/internal/*` — 게이트웨이 라우트 없음, 서비스 간 전용 계약 |
| **Polyglot Persistence/Runtime** | Java 도메인 서비스 + Python 추천 서비스 |
| **Idempotent Consumer** △ | 현재 미적용 — 중복 이벤트 재처리 가능, 멱등 키 도입이 개선점 |
| **Transactional Outbox** △ | 현재 미적용 — DB 커밋과 이벤트 발행이 원자적이지 않음 |
| **Circuit Breaker / Retry** △ | 현재 미적용 — Resilience4j 도입이 개선점 |
| **Dead Letter Queue** △ | 현재 컨슈머가 예외를 삼킴 — DLQ + 재시도가 개선점 |
| **Database per Service** △ | 현재 공유 `locallink_db` — 분리가 최우선 개선점 |

---

## 5. 핵심 요청 흐름

### 5-1. 로그인 (OAuth2 Authorization Code)

```
vue-frontend → auth-server /oauth2/authorize (전체 페이지 리다이렉트)
  → 사용자 인증 → /callback?code=...
  → POST /oauth2/token (Basic auth: base64(client_id:client_secret)) → { access_token }
  → sessionStorage 저장, Axios 인터셉터가 이후 모든 요청에 Bearer 토큰 첨부
  → GET /api/users/me
      → api-gateway가 JWKS로 JWT 검증 → subject 추출 → X-User-Id 헤더로 user-service에 전달
      → user-service는 헤더만 신뢰 → 프로필(id, role) 반환 → Pinia 저장
  → Vue Router 네비게이션 가드가 이 상태로 requiresAuth / officerOnly 라우트 게이트
```

### 5-2. 참여신청 → 정산 (동기 요청 + 비동기 완료 이벤트)

```
POST /api/participations { programId }
  → ParticipationService.participate():
      1. program-service에서 실제 가격 조회 (동기 REST) — 프로그램 존재 확인 + 금액 확정
      2. 중복 신청 확인 (PENDING / ACTIVE 만 중복, FAILED 는 재신청 허용)
      3. createPendingParticipation()   [REQUIRES_NEW — PENDING 행 즉시 커밋,
                                       FAILED 행이 있으면 새 행 대신 PENDING 으로 복구(조건부 UPDATE)]
      4. settlement-service에 정산 요청 (동기 REST, 응답 상한 5초)
         · 확실한 실패(연결 불가/4xx/FAILED 응답) → 행을 FAILED 로 보상 처리 후 5xx 전파 (재신청 가능)
         · 결과 불명(응답 타임아웃/5xx) → FAILED 로 단정하지 않고 PENDING 유지
           (정산이 실제로 됐을 수 있음 → 이벤트가 오면 ACTIVE, 끝내 안 오면 10분 뒤 정리 작업이 FAILED)
  ← 201 Created (이 시점 participation는 아직 PENDING — 호출자는 정산 결과를 모름)

settlement-service:
  1. Settlement 저장(PENDING) → 2. transactionId = UUID (PG 없이 항상 성공)
  → 3. status COMPLETED → 4. settlement.completed 발행 (동기, ack 최대 10초 대기)
  ← { settlementId, COMPLETED } 동기 반환 (participation-service는 로그만, 사용 안 함)

──────── 여기서부터 비동기 ────────

participation-service @KafkaListener (topic: settlement.completed):
  → activateParticipation():
      ① 조건부 UPDATE 로 PENDING/FAILED → ACTIVE 를 먼저 커밋 (실제로 바꾼 호출만 다음 단계 진행)
      ② 커밋 이후에만 program-service 참여 카운트 증가 (동기 REST) → participation.completed 발행

recommend-service Consumer (topic: participation.completed, 백그라운드 스레드):
  → participationId/userId/programId 로깅 (추천 캐시 재계산 확장 지점)
```

### 5-3. 추천

```
GET /api/recommend/{userId}   (recommend-service가 자체 JWKS 검증)
  → 참여 이력 조회 (GET participation-service /api/participations/internal/history/{userId})
      · 이력 없음 → 전체 프로그램 참여 건수 desc 정렬 → 상위 5
      · 이력 있음 → 참여 프로그램의 최빈 카테고리 → 해당 카테고리 추천(참여분 제외) → 상위 5
      · 이력 조회 실패 → "이력 없음"으로 폴백 (추천은 비핵심 기능)
```

---

## 6. 설계 의사결정

| 결정 | 이유 |
|---|---|
| 인증 검증을 게이트웨이에 집중 | 도메인 서비스마다 JWT 파싱 로직을 중복하지 않음. 서비스는 `X-User-Id`만 읽어 비즈니스 로직에 집중 |
| PENDING 행 분리 커밋 (`REQUIRES_NEW`) + FAILED 보상 | "신청 기록은 먼저 남기고 활성화만 나중에" — 검증(존재·가격·중복)을 모두 끝낸 뒤에만 상태를 남기고, 정산 요청 실패 시 FAILED 로 바꿔 재신청을 막지 않음 |
| 동기 REST vs 비동기 이벤트 구분 | 즉시 응답이 필요한 조회/검증은 `WebClient` 동기, 결과를 기다릴 필요 없는 상태 전이는 이벤트 |
| 동기 요청 + 비동기 완료 이벤트 혼합 | participation→settlement는 동기 REST(요청 시점에 실패를 바로 알고 보상 처리 가능), 이후 상태 전이는 `settlement.completed` 이벤트(settlement는 participation의 존재를 모르고 이벤트만 발행). trade-off: 순수 Choreography보다 participation→settlement 결합이 강하고 settlement 장애가 신청 API에 전파되지만, 오케스트레이터 없이도 실패를 즉시 보상할 수 있음. 반대로 DB 커밋과 이벤트 발행이 분리되어 있어 유실은 스케줄러(PENDING 타임아웃)로만 보완 |
| 정산 금액을 program-service에서 조회 | 클라이언트가 금액을 조작하지 못하도록 서버 측에서 실제 가격을 확정해 settlement로 전달 |
| 도메인 용어 일원화 | 서비스명·API·DB·이벤트·화면이 같은 용어(program / participation / settlement)를 써서 코드와 화면 사이의 번역 계층을 없앰 |

---

## 7. 테스트 · 관측성 · 문서화

| 영역 | 현재 상태 |
|---|---|
| 통합 테스트 | `participation-service`: 정산 서비스 장애 → `FAILED` → 복구 후 재신청 성공, 중복 신청 거부, 오래된 PENDING 정리 (`ParticipationRetryIntegrationTest`, H2 + MockMvc, 외부 서비스는 목) |
| 이벤트 멱등성 테스트 | 같은 `settlement.completed` 이벤트 2회 수신 → 참여 카운트 증가 1회 (`SettlementCompletedIdempotencyTest`, `@EmbeddedKafka` 임베디드 브로커 + 실제 `@KafkaListener`, H2, program-service는 목) |
| 정합성·동시성 테스트 | 정산 결과 불명 시 PENDING 유지, 같은 이벤트 동시 8건 처리 시 부수효과 1회, 정리 작업/보상이 ACTIVE 를 덮어쓰지 않음, 느린 프로그램 조회 타임아웃·필드 별칭 흡수 (`ParticipationConsistencyTest`) |
| 컨트랙트 테스트 | 다른 서비스가 호출하는 `internal/*` 계약 고정: `ProgramInternalContractTest`(exists / internal/{id} / participation-count / recommend / 목록 래퍼), `SettlementInternalContractTest`·`SettlementServiceTest`(요청·응답 필드, COMPLETED/FAILED). 추천 규칙은 `recommend-service/tests` (pytest) |
| 컨텍스트 로딩 테스트 | 모든 서비스가 H2·Eureka 비활성으로 자급 (MariaDB 없이 `gradlew test` 통과) |
| 보안 테스트 | 서비스 간 `/internal/**` 토큰 가드: 토큰 없음/오답 → 403, 정상 토큰 → 계약대로 응답 (program·settlement·participation 각 테스트). JWT 경로 테스트는 미작성 |
| 한계 | DB는 MariaDB가 아닌 H2(MariaDB 모드)로 검증. Testcontainers(MariaDB/Kafka) 기반 테스트는 미적용 |
| 재현 가능한 E2E 테스트 | 미작성 (컨트랙트는 위 표의 서비스별 테스트로 부분 대체) |
| API 문서 | springdoc-openapi → 서비스별 `/swagger-ui.html`, `/api-docs` |
| 헬스 체크 | Spring Actuator `/actuator/health` (`show-details: always`) |
| 서비스 등록 확인 | Eureka 대시보드 `http://localhost:8761/` |
| SQL 로깅 | `show-sql: true` + `format_sql: true` |

`키워드`: JUnit 5, Spring Boot Test, MockMvc, OpenAPI 3, Actuator, 헬스 체크

---

## 8. 한계와 개선 로드맵

> 학습·시연 목적의 프로젝트로서 **의도적으로 단순화**한 부분과, 실무화 시 보완 지점을 명시했습니다.
> 

| 항목 | 현재 상태 | 개선 방향 | 우선순위 |
|---|---|---|---|
| DB 분리 | 4개 서비스가 하나의 `locallink_db` 공유 | Database per Service | 높음 |
| 서비스 간 인증 | 각 서비스는 `permitAll`(JWT 는 게이트웨이가 검증). 서비스 간 `/internal/**` 는 program / settlement / participation / user 모두 공유 시크릿 헤더(`X-Internal-Token`, `.env` 의 `INTERNAL_API_TOKEN`)로 보호하며 미설정 시 전부 거부. 프로그램 등록(`POST /api/programs`)은 program-service 가 user-service(`/api/users/internal/{id}`)에서 역할을 조회해 `GOV_OFFICER` 만 허용(소상공인·없는 사용자 403, 확인 불가 503, `X-User-Id` 없음 401). 토큰은 정적이라 회전·서비스별 구분 불가 | OAuth2 client-credentials(`scope service.read`) + 서비스별 리소스 서버 검증, mTLS | 중간 |
| 서비스 디스커버리 활용 | participation-service→program/settlement 호출은 `@LoadBalanced` + 서비스 ID로 Eureka LB 사용. recommend-service(Python)와 게이트웨이 외 일부 호출은 컨테이너 DNS 직접 호출 | 나머지 호출도 서비스 ID 방식으로 통일 | 낮음 |
| 이벤트 발행 원자성 | DB 커밋과 Kafka 발행이 분리 → 유실 가능 | Transactional Outbox + CDC(Debezium) | 높음 |
| 컨슈머 오류 처리 | 예외를 삼키고 로깅만 → 이벤트 처리 실패 시 PENDING 잔존 (일정 시간 후 `FAILED`로 정리되나 정산은 완료된 상태일 수 있음) | 재시도 + DLQ + 알림 | 높음 |
| 멱등성 | 모든 상태 전이가 `WHERE status = ...` 조건부 UPDATE 라서 같은 `settlement.completed` 가 재전달·동시 처리돼도 실제로 전이시킨 1건만 부수효과(카운트·후속 이벤트)를 일으킴 (순차/동시 모두 테스트로 검증). 다른 종류의 이벤트까지 보장하진 않음 | 처리 이력 테이블 기반 멱등 키 | 낮음 |
| 커밋 후 부수효과 | 상태 전이 커밋 → 그 뒤 카운트 증가·이벤트 발행. 둘 사이에 프로세스가 죽으면 유실(at-most-once). 트랜잭션 안에서 하던 때의 "중복 증가" 위험은 제거 | 같은 트랜잭션에 outbox 행 기록 + 별도 릴레이 (위 "이벤트 발행 원자성"과 동일 해법) | 높음 |
| 보상 트랜잭션 | 정산 요청이 확실히 실패하면 `FAILED` 보상 + 재신청 허용, 결과 불명(타임아웃/5xx)이면 PENDING 유지, 오래된 PENDING은 스케줄러가 조건부 UPDATE 로 정리(다중 인스턴스에서도 안전, 이미 ACTIVE 인 건은 덮어쓰지 않음). 정산 성공 후 응답만 유실돼 `FAILED`가 된 건에 뒤늦게 `settlement.completed`가 오면 정산 사실 기준으로 `ACTIVE` 복구(`CANCELLED`는 무시, 환불 확인 로그만). 별도 환불/보상 이벤트는 없음 | 보상 이벤트·환불 흐름 | 중간 |
| 내 목록 조회 | `GET /api/participations/my`가 프로그램마다 program-service를 동기 호출(같은 프로그램은 1회, 서로 다른 프로그램은 전용 bounded 풀에서 `CompletableFuture` 병렬, 항목별 3초 타임아웃, 응답은 타입 DTO 로 매핑). 일부 실패·지연 시 해당 항목 `program=null`로 부분 응답 | program-service 배치 조회 API, 캐시 | 낮음 |
| 카테고리 표현 | participation 응답의 `program.category`는 program-service 원본 enum 그대로 전달, 표시용 라벨 변환은 프론트(`CATEGORY_META`)만 담당 | — | 해소 |
| 회복탄력성 | 동기 호출에 circuit breaker/retry 없음 | Resilience4j (`@CircuitBreaker`, `@Retry`, bulkhead) | 중간 |
| 이벤트 스키마 계약 | 타입 헤더 없이 `Map` 파싱 → 필드 변경이 컴파일 타임에 안 걸림 | 공유 이벤트 DTO 모듈 or Schema Registry(Avro) | 중간 |
| 정산 | PG 연동 없는 시뮬레이션, 항상 성공 | 실제 PG 어댑터, 실패/부분취소/환불 시나리오 | 중간 |
| 서비스 직접 접근 | 도메인 서비스의 `SecurityConfig`가 `anyRequest().permitAll()`이고 `X-User-Id`를 그대로 신뢰 → 서비스 포트에 직접 요청하면 헤더 위조 가능. 완화: compose에서 user/participation/settlement/recommend 포트를 호스트에 게시하지 않음(`expose`만), program-service(개발용 공개 읽기 경로)와 MariaDB는 `127.0.0.1`에만 바인딩. 공개 읽기 경로(nginx·vite `/program-service/`)는 **GET/HEAD 만 통과**(쓰기 403, `/internal/` 404, 클라이언트의 `X-User-Id`·`X-Internal-Token` 헤더 제거). **`/internal/**` scope 제한(client-credentials)은 미구현** | 서비스 간 client-credentials 토큰 + `/internal/**`를 `SCOPE_service.read`로 제한(auth-server에 서비스 클라이언트 등록 필요), mTLS | 높음 |
| 브라우저 client secret | 프론트엔드가 `VITE_CLIENT_SECRET`을 번들에 넣어 토큰 교환(`client_secret_basic`). 브라우저 앱(public client)은 secret을 숨길 수 없으므로 번들에서 추출 가능 | Authorization Code + **PKCE**(secret 없는 public client). auth-server가 prebuilt 이미지라 클라이언트 등록을 바꿀 수 없어 이번에는 미적용 | 높음 |
| 비밀번호 관리 | compose의 DB 비밀번호는 `.env`로 분리(git 제외, `.env.example` 제공). 각 서비스 `application.yml`의 로컬 `bootRun`용 기본값에는 여전히 평문이 남아 있음 | 기본값 제거 후 환경변수/시크릿 매니저 사용 | 중간 |
| 프론트 목업 | 기본값 꺼짐(`VITE_ENABLE_MOCK=true`로 켬). 켜져 실제로 목업이 쓰이면 화면에 "데모 데이터" 배지 표시 | — | 해소 |
| 관측성 | 로그 + Actuator 기본 | 분산 추적(OpenTelemetry/Zipkin), 메트릭(Prometheus/Grafana), 중앙 로깅(ELK) | 낮음 |
| 스키마 관리 | `ddl-auto: update`로 자동 반영, 마이그레이션 이력 없음 | Flyway 도입 (우선순위 낮음, 로드맵만 유지) | 낮음 |
| 실행 가능성 | `infra-images.tar`(auth-server / api-gateway prebuilt)는 저장소에 없어(`.gitignore`) 외부에서 전체 스택을 바로 띄울 수 없음 | 시연 스크린샷/GIF 첨부, 이미지 배포 경로 제공 | 중간 |
| CI/CD | 수동 빌드 | GitHub Actions로 서비스별 빌드·테스트·이미지 푸시 매트릭스 | 낮음 |

---

## 9. 확장 시나리오

이 프로젝트 구조를 기반으로 추가 구현/설명할 수 있는 것들:

- **분산 추적** — OpenTelemetry 에이전트를 각 서비스에 붙여 게이트웨이→participation→Kafka→recommend까지 하나의 trace로 시각화.
- **Saga 전환** — 현재의 동기 요청 + 이벤트 혼합 구조를 순수 choreography 또는 Camunda/Temporal 기반 오케스트레이션으로 리팩터링해 비교.
- **Kubernetes 이관** — Compose → Helm 차트, Eureka를 K8s Service Discovery로 대체, HPA로 컨슈머 오토스케일.
- **API 게이트웨이 레이트 리밋 / 서킷** — Spring Cloud Gateway 필터로 사용자별 rate limit(Redis), fallback 라우트.
- **읽기 모델 프리컴퓨트** — `participation.completed` 컨슈머에서 추천 결과를 캐시(Redis)에 미리 계산 (현재 no-op인 확장 지점).
- **이벤트 소싱** — participation 상태 전이를 이벤트 스트림으로 저장해 감사(audit)·리플레이 지원.
- **멀티 테넌시** — 지자체별 데이터 격리(스키마/행 수준), 라벨 맵을 테넌트 설정으로 확장.
- **BFF 계층** — 프론트 전용 집계 API를 별도 서비스로 분리해 클라이언트 라운드트립 감소.

---

## 10. 실행 방법

### 전체 스택 (Docker Compose)

```bash
cp .env.example .env                                     # DB 비밀번호·INTERNAL_API_TOKEN 설정 (최초 1회, .env 는 git 제외, 값이 없으면 compose 가 기동을 거부)
docker load -i infra-images.tar                          # prebuilt auth-server / api-gateway (최초 1회)
docker compose build --no-cache && docker compose up -d  # 로컬 서비스 빌드 + 전체 기동
docker compose logs -f participation-service                # 특정 서비스 로그
docker compose down                                      # 종료
```

기동 후 `http://localhost:8761/` 에서 전 서비스 등록 상태 확인.

> `infra-images.tar`는 용량 때문에 저장소에 포함되어 있지 않습니다(별도 배포). 이 파일이 없으면 auth-server / api-gateway를 띄울 수 없어 전체 스택은 실행되지 않습니다. 반면 각 서비스의 `./gradlew test` 와 recommend-service 의 `pytest` 는 이 이미지나 MariaDB/Kafka/Eureka 없이(H2 인메모리, 임베디드/목 Kafka) 실행됩니다.
>
> 서비스 간 호출(`/internal/**`)은 `INTERNAL_API_TOKEN` 이 program / settlement / participation 와 호출하는 쪽(participation, recommend)에 같은 값으로 주입돼야 동작합니다. compose 는 `.env` 값을 자동으로 넣어 줍니다.

### 기존 환경(강의 마켓플레이스 시절 스키마/이름)에서 이전할 때

백엔드 식별자까지 바뀌었기 때문에 **이미 띄워 둔 DB·게이트웨이·인증 서버는 그대로 쓸 수 없습니다.**

1. **DB** — 데이터가 필요 없으면 `docker compose down -v` 후 다시 기동하면 `01_init.sql` 이 새 스키마와 시드를 만듭니다. 데이터를 옮겨야 하면 [`init-db/migrations/V3__rename_domain_terms.sql`](./init-db/migrations/V3__rename_domain_terms.sql) 상단의 절차(DB 이름 `lecture_db → locallink_db`, 테이블·컬럼·역할·카테고리 값 변환)를 따르세요.
2. **api-gateway (prebuilt 이미지의 소스 쪽 설정)** — 라우트가 바뀐 서비스 ID/경로를 따라가야 합니다. `/internal/**` 는 계속 라우팅하지 않습니다.

   | 이전 | 현재 |
   |---|---|
   | `/api/courses/**` → `lb://course-service` | `/api/programs/**` → `lb://program-service` |
   | `/api/enrollments/**` → `lb://enrollment-service` | `/api/participations/**` → `lb://participation-service` |
   | `/api/payments/**` → `lb://payment-service` | `/api/settlements/**` → `lb://settlement-service` |
   | `/api/users/**`, `/api/recommend/**` | 변경 없음 |

3. **auth-server (prebuilt 이미지의 소스 쪽 설정)** — `users.role` 값이 `STUDENT / INSTRUCTOR` 에서 `MERCHANT / GOV_OFFICER` 로 바뀌었고(JWT 권한 매핑에 쓰고 있다면 같이 수정), 접속 DB 는 compose 가 `locallink_db` 로 주입합니다.
4. 이미지 태그 `msa-lecture/auth-server:1.0`, `msa-lecture/api-gateway:1.0` 은 prebuilt 이미지 이름이라 그대로 두었습니다. 이 이미지를 위 설정으로 다시 빌드했다면 태그와 `infra-images.tar` 를 함께 갱신하세요.

### 개별 백엔드 서비스

JDK 21 이 필요합니다 (Gradle toolchain).

```bash
cd program-service
./gradlew test                                                       # 전체 테스트 (H2, 외부 인프라 불필요)
./gradlew test --tests "com.locallink.program.ProgramInternalContractTest"
INTERNAL_API_TOKEN=dev-token ./gradlew bootRun                       # 로컬 실행 (MariaDB/Eureka/auth-server 필요)
```

> Windows + OneDrive 폴더에서 `Unable to delete directory ... build` 오류가 나면 OneDrive 가 `build/` 를 잠근 것입니다. 프로젝트를 OneDrive 밖으로 옮기거나 빌드 출력 위치를 바꾸세요.

### 프론트엔드

```bash
cd vue-frontend
npm install
npm run dev     # http://localhost:3000
npm run build
```

### 추천 서비스 (Python)

```bash
cd recommend-service
pip install -r requirements.txt
INTERNAL_API_TOKEN=dev-token python main.py  # 또는: INTERNAL_API_TOKEN=dev-token uvicorn main:app --reload --port 8085

pip install -r requirements-dev.txt && python -m pytest tests   # 추천 규칙 / 라우터 / 클라이언트 계약 테스트
```

---

## 11. 포트 맵 · 참고 문서

| 서비스 | 포트 | 스택 |
|---|---|---|
| eureka-server | 8761 | Spring Cloud Netflix |
| auth-server | 9000 | OAuth2 Authorization Server (prebuilt) |
| api-gateway | 8080 | Spring Cloud Gateway (prebuilt) |
| user-service | 8081 | Spring Boot + JPA |
| program-service | 8082 | Spring Boot + JPA |
| participation-service | 8083 | Spring Boot + JPA + Kafka (호출 조정) |
| settlement-service | 8084 | Spring Boot + JPA + Kafka |
| recommend-service | 8085 | FastAPI (Python) |
| vue-frontend | 3000 (dev) | Vue 3 + Vite |
| MariaDB | 3306 | 공유 `locallink_db` |
| Kafka | 9092 | 이벤트 브로커 |

### 참고 문서

- [`정산_흐름_백엔드_코드_서술.md`](./정산_흐름_백엔드_코드_서술.md) — 정산(Settlement) 흐름 백엔드 코드 상세 (엔티티/DTO/트랜잭션/Kafka 계약/보상·복구)
- [`.env.example`](./.env.example) — 실행 전 필요한 환경변수 (DB 비밀번호, `INTERNAL_API_TOKEN`)
- [`init-db/migrations/`](./init-db/migrations) — 기존 DB용 수동 마이그레이션 (자동 실행되지 않음)
