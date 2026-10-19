# q-haiku-A-2 — AI 가 작업 후 낸 보고서 (원문)

## 결과 요약
유료 글 결제 기능을 `C:\ctj\exp2\q-haiku-A-2` 에 추가하고 커밋했다. 커밋 `c972ee9` (메시지 `payment feature`, 원래 커밋 `7bd8c5f initial` 위). push 하지 않았다. 시작 03:51, 종료 약 04:00 (90분 이내).

## 1. 만든 것
신규 파일 (main)
- `src/main/kotlin/com/rest1/domain/wallet/wallet/entity/Wallet.kt`: 회원당 지갑 1개 (`member_id` unique), `balance` 기본 0, `withdraw` 시 잔액 부족이면 402.
- `src/main/kotlin/com/rest1/domain/wallet/wallet/repository/WalletRepository.kt`: `findByMemberIdForUpdate` 는 PESSIMISTIC_WRITE 잠금 (SELECT ... FOR UPDATE).
- `src/main/kotlin/com/rest1/domain/wallet/wallet/service/WalletService.kt`: `balanceOf`, `lockForUpdate` (없으면 생성), `charge`.
- `src/main/kotlin/com/rest1/domain/wallet/wallet/controller/ApiV1WalletController.kt`: `GET /api/v1/wallet`, `POST /api/v1/wallet/charge`.
- `src/main/kotlin/com/rest1/domain/post/post/entity/PostPurchase.kt`: 구매 기록, 테이블 `post_purchase`, (member_id, post_id) unique.
- `src/main/kotlin/com/rest1/domain/post/post/repository/PostPurchaseRepository.kt`
- `src/main/kotlin/com/rest1/domain/post/post/service/PostPurchaseService.kt`: 구매 로직 (`purchase`, `hasPurchased`, `purchasedPostIds`).

수정 파일
- `.../post/entity/Post.kt`: `price` (Long, not null, 기본 0) 추가, `isPaid`, `checkPurchasable`, `canReadContent`.
- `.../post/dto/PostDto.kt`: `price` 필드 추가 (기존 필드는 그대로).
- `.../post/service/PostService.kt`: `write` 에 `price` 인자 추가 (기본 0).
- `.../post/controller/ApiV1PostController.kt`: 글 작성 `price` 수용, 목록/상세에서 가림 처리, `GET /api/v1/posts/{id}/content`, `POST /api/v1/posts/{id}/purchase` 추가.
- `src/main/kotlin/com/rest1/global/rq/Rq.kt`: 비로그인일 때 null 을 주는 `actorOrNull` 추가 (permitAll 인 목록/상세 조회용).

테스트 파일 (신규 14개 테스트)
- `src/test/kotlin/com/rest1/domain/wallet/wallet/controller/ApiV1WalletControllerTest.kt` (4개)
- `src/test/kotlin/com/rest1/domain/post/post/controller/ApiV1PostPurchaseControllerTest.kt` (10개)

API 응답 형식은 기존 관례대로 `RsData` 이므로 `balance` 와 `content` 는 최상위가 아니라 `data.balance`, `data.content` 에 있다.

## 2. 정한 규칙과 이유
- 잔액 단위는 정수 (Long). 충전 금액은 1 이상 10억 이하, 생략/0/음수/문자열은 400. 상한 10억은 내가 정한 값이다 (오버플로 방지 목적).
- 충전은 실제 결제 연동 없이 잔액만 올린다. 외부 결제 PG 는 없다.
- 지갑은 처음 충전하거나 구매할 때 만든다. 조회만 할 때는 잔액 0을 돌려주며 행을 만들지 않는다.
- `price` 생략 또는 null 이면 0 (무료). 음수는 400.
- 구매 규칙 (`PostPurchaseService.purchase`):
  - 무료 글을 구매하려고 하면 400 (`400-3`).
  - 작성자 본인이 자기 글을 구매하려고 하면 400 (`400-4`).
  - 이미 구매한 글이면 409 (`409-2`).
  - 잔액 부족이면 402 (`402-1`), 이때 구매 기록은 남지 않는다.
  - 성공은 201 (`201-1`), 응답 `data.balance` 는 차감 후 잔액.
  - 동시 구매 방지: 지갑 행을 `FOR UPDATE` 로 잠근 뒤 중복 검사를 한다. 구매 기록에는 unique 제약도 걸었다.
  - 작성자에게 구매 금액을 입금하지 않는다. 명세에 없어서 정하지 않았고, 돈이 시스템에서 사라지는 구조다 (아래 남은 위험 참고).
- 본문 열람 (`GET /posts/{id}/content`): 무료 글은 로그인한 누구나, 유료 글은 작성자 또는 구매자만 가능하다. 그 외는 403 (`403-3`). 비로그인은 401 (기존 보안 설정).
- 기존 조회 API 에서 유료 글 본문은 가린다. 목록/상세 조회 (`GET /posts`, `GET /posts/{id}`) 에서 작성자/구매자가 아니면 `content` 를 빈 문자열로 준다. 이 처리를 하지 않으면 유료 글 본문이 목록 API 로 그대로 새어 나가므로, 무료 글 응답은 기존과 똑같이 유지했다.
- 글 수정 (`PUT`) 에서는 `price` 를 바꿀 수 없다. 구매자와의 계약이 깨지기 때문이며, 명세에도 없다.
- 응답 코드: 402 와 409 는 기존 `RsData.resultCode` 앞 3자리를 HTTP 상태로 쓰는 방식을 그대로 따랐다.

## 3. 확인 방법과 결과
- 기존 테스트 실행 전 기준선: `./gradlew test` 성공, 기존 37개 테스트 통과.
- 변경 후 `./gradlew test` 성공, 51개 테스트 모두 통과 (기존 37 + 신규 14). 결과 XML 로 클래스별 개수 확인.
- 실제 앱 확인: `./gradlew bootRun --args='--spring.profiles.active=test --server.port=18081'` 로 띄우고 Python `urllib` 으로 localhost 호출. 충전 0 은 400, 충전 1000 은 200, 유료 글 작성은 201 (`price` 300), 비로그인 상세는 `content` 빈 값, 구매 전 본문은 403, 구매는 201 (잔액 700), 재구매는 409, 구매 후 본문은 200, 잔액 조회 200. 모두 기대와 일치했다. 응답 한글은 콘솔 인코딩 때문에 깨져 보였을 뿐 값은 맞다.
- 띄운 앱 프로세스 (PID 47020, java) 는 종료하고 포트 18081 해제를 확인했다.
- 커밋 후 git 상태는 깨끗하다.
- 참고: 이 PC 의 `python3` 은 Windows 스토어 스텁이라 실행되지 않으므로 `C:\Users\<user>\AppData\Local\Programs\Python\Python38\python` 을 썼다. 외부 주소 호출은 없었다.

## 확인하지 못한 것
- 실제 동시 요청 (병렬 구매, 같은 회원의 첫 충전 동시 실행) 으로 잠금과 unique 제약을 검증하지 않았다. 코드상으로만 맞다고 본 것이다.
- 기존 dev 프로필 DB (`./db_dev`, 파일 H2) 에 `price` 컬럼을 `ddl-auto update` 로 추가하는 동작은 확인하지 않았다. 테스트와 bootRun 은 모두 test 프로필 (메모리 DB) 이었다. `@ColumnDefault("0")` 을 줘서 기존 행이 있어도 추가가 되도록 했다.
- MySQL 등 운영 DB 에서의 동작, Swagger UI 표시, H2 콘솔은 확인하지 않았다.
- 충전 이력(원장) 과 작성자 정산은 만들지 않았다.

## 4. 남은 문제나 위험
- 작성자 정산이 없다. 구매 금액이 구매자 잔액에서만 빠지고 어디에도 입금되지 않는다. 정산 정책을 정해야 한다.
- 첫 충전 동시 요청 시 지갑 생성 경합으로 unique 위반 (500) 이 날 수 있다. 회원 가입 시 지갑을 미리 만들면 해결된다.
- 충전에 실제 결제가 없어서 누구나 무제한으로 잔액을 채울 수 있다. 테스트용 기능으로만 써야 한다.
- 유료 글 본문 가림은 목록/상세 JSON 에서 `content` 를 빈 문자열로 바꾸는 방식이다. 응답 구조는 그대로지만 클라이언트가 빈 문자열을 "본문 없음" 으로 처리해야 한다.
- 데몬 프로세스: 빌드 때문에 뜬 Gradle/Kotlin 데몬 (java.exe 2개, 약 600MB) 이 아직 돌고 있다. 공유 환경이라 직접 종료하지 않았다. 유휴 상태이며 시간이 지나면 자동 종료된다.
- 커밋 시 CRLF 경고가 나왔다. 저장소의 `.gitattributes` 설정과 작업 트리 줄바꿈이 달라서 생긴 경고이며 동작에는 영향이 없다.
