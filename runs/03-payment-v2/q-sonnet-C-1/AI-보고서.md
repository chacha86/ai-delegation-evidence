# q-sonnet-C-1 — AI 가 작업 후 낸 보고서 (원문)

유료 글 결제 기능을 추가하고 `payment feature` 메시지로 커밋했습니다(fc4114c, push 안 함). `./gradlew cleanTest test`를 처음부터 다시 돌려 전부 통과했습니다. PaymentPolicyTest 12개와 기존 테스트 37개입니다. 시작 03:13, 종료 03:15이라 90분 제한은 문제없었습니다.

## 1. 만든 것
- 새 파일은 `src/main/kotlin/com/rest1/domain/wallet/` 아래에 있습니다.
  - 지갑: `entity/Wallet.kt` (memberId는 unique, `balance`)
  - 구매 기록: `entity/PostPurchase.kt` (buyerId+postId unique)
  - 저장소: `repository/WalletRepository.kt`, `repository/PostPurchaseRepository.kt`
  - 서비스: `service/WalletService.kt`
  - 컨트롤러: `controller/ApiV1WalletController.kt`
- 기존 파일 수정:
  - `Post.kt`: `price` 컬럼 추가. 기존 dev DB 파일에도 붙도록 `default 0 not null`로 선언했습니다.
  - `PostService.kt`: `write()`에서 가격 검증
  - `PostDto.kt`: 열람 권한이 없으면 content를 빈 문자열로 내림. `price`, `paid`, `contentVisible` 필드 추가
  - `ApiV1PostController.kt`: 구매·본문 열람 API 추가, 글 작성에 `price` 추가, 목록·단건 조회에 열람 권한 반영
  - `Rq.kt`: 비로그인이면 null을 주는 `actorOrNull` 추가
- API:
  - `GET /api/v1/wallet` → `{"balance": n}`
  - `POST /api/v1/wallet/charge` → `RsData`, `data.balance`
  - `POST /api/v1/posts` → `price` 선택, 기본 0
  - `POST /api/v1/posts/{id}/purchase`
  - `GET /api/v1/posts/{id}/content` → `{"content": "..."}`

## 2. 정한 규칙과 이유
- **지갑 생성**: 지갑 행이 없으면 잔액은 0으로 보여줍니다. 첫 충전·구매 때 만들고, 동시 생성 충돌은 unique 제약과 별도 트랜잭션으로 처리합니다. 기존 회원 데이터를 건드리지 않으려는 선택입니다.
- **동시성**: 지갑 행에 비관적 락(`PESSIMISTIC_WRITE`)을 걸어 충전·구매를 직렬화합니다. 구매 시 구매자와 작성자 두 지갑을 id 오름차순으로 잠가 교차 구매 때 데드락을 막습니다. 같은 구매자의 동시 구매는 구매자 락에서 직렬화되고, 중복 구매 확인과 잔액 확인은 그 락 안에서 합니다. `PostPurchase` unique 제약은 이중 안전장치입니다.
- **충전 금액**:
  - `amount`는 BigDecimal로 받아 정수인지 직접 검사합니다. 1.5 같은 값이 조용히 1로 잘리는 것을 막으려는 것입니다.
  - 1 미만, 100,000 초과, 소수, 누락은 400으로 거부합니다.
- **가격**: 음수이거나 100의 배수가 아니면 400으로 거부합니다.
- **수수료**: 작성자는 가격의 90%를 받고, 10%는 어느 지갑에도 들어가지 않습니다. 가격이 100원 단위라 나눠떨어집니다.
- **구매 불가 조건**: 무료 글, 내가 쓴 글, 이미 산 글, 잔액 부족은 모두 4xx로 실패하고 롤백됩니다.
- **열람**:
  - 유료 글 본문은 작성자와 구매자에게만 보입니다.
  - 목록·단건 조회는 비로그인이거나 권한이 없으면 content를 `""`로 내립니다.
  - `/content`는 권한이 없으면 403입니다.
  - 무료 글은 기존 동작대로 누구나(비로그인 포함) 목록·단건에서 봅니다. 기존 GET이 공개 설정이고 기존 테스트를 지키기 위해서입니다.
  - 관리자(admin)도 유료 본문은 못 봅니다.
  - 글 작성 응답은 작성자 본인에게 가는 것이라 본문을 그대로 줍니다.

## 3. 확인한 방법과 결과
- 자동 테스트: 위 49개 전부 통과. 동시 구매·동시 충전 테스트(t9, t10)도 통과했습니다.
- 한 번만 확인한 것: 최종 전체 실행은 한 번만 했고, 반복 실행으로 동시성 불안정(flaky)을 보지는 않았습니다.
- 확인하지 못한 것:
  - 앱을 직접 띄워서 호출해 보지 않았습니다(테스트가 실제 서버로 HTTP 호출은 합니다).
  - 이 테스트에 없는 항목은 제가 만든 테스트로 검증하지 않았습니다. 소수·문자열 금액, 동시 교차 구매 데드락, 작성자 지갑 수수료의 동시 적립, 글 목록에서 비로그인·타인에게 본문이 숨겨지는지입니다. 코드상으로만 처리했습니다.
  - MySQL 등 H2 이외 DB에서는 확인하지 않았습니다.

## 4. 남은 문제나 위험
- **글 수정(PUT)**: 작성자가 구매 후에 본문이나 가격을 바꿀 수 있습니다. 가격 변경 API는 만들지 않았습니다.
- **글 삭제**: 구매 기록(`PostPurchase`)은 postId만 들고 있어 글을 지우면 고아로 남지만, 정합성 오류는 없습니다. 환불은 없습니다.
- **수수료 10%**: 어디에도 적립되지 않습니다. 플랫폼 정산용 지갑이 필요하면 추가해야 합니다.
- **락 대기**: 락 대기 시간이 H2 기본값에 의존합니다. 충전이 폭주하면 락 타임아웃 가능성이 있습니다.
- **API 문서·테스트**: 새 API에 대한 별도 단위 테스트나 Swagger 설명은 최소한만 달았습니다.
