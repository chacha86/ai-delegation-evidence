# p-sonnet-C-1 — AI 가 작업 후 낸 보고서 (원문)

**보고서: 유료 글 결제 기능**

작업 시각은 01:46에 시작해 01:50에 끝났다(90분 제한 안). 커밋 `4680e8d payment feature`를 만들었다. push는 하지 않았다.

## 1. 만든 것
기본 경로는 `src/main/kotlin/com/rest1/` 아래다.
- **API**
  - `POST /api/v1/wallet/charge`는 `{"amount":N}`을 받고 응답은 `data.balance`에 담긴다.
  - `GET /api/v1/wallet`의 응답은 `{"balance":N}`이다.
  - `POST /api/v1/posts`는 `price`를 받는다. 생략하면 0이다.
  - `POST /api/v1/posts/{id}/purchase`로 구매한다.
  - `GET /api/v1/posts/{id}/content`의 응답은 `{"content":...}`이다.
- **새 파일** (`domain/wallet/` 아래)
  - `entity/Wallet.kt`, `entity/Purchase.kt`
  - `repository/WalletRepository.kt`, `repository/PurchaseRepository.kt`
  - `service/WalletService.kt`
  - `controller/ApiV1WalletController.kt`
- **수정한 파일**
  - `Post.kt`: `price` 컬럼 추가. 기존 DB 파일에도 붙도록 `default 0 not null`로 했다.
  - `PostDto.kt`: `price`, `contentLocked` 필드 추가. 유료 글 본문은 기본으로 숨긴다.
  - `PostService.kt`, `ApiV1PostController.kt`, `Rq.kt`: `Rq`에는 비로그인도 다루는 `actorIdOrNull`을 추가했다.
  - `src/main/resources/application-test.yml`: 테스트마다 별도 인메모리 DB를 쓰도록 바꿨다(아래 2번에서 이유 설명).
- **추가한 테스트**: `src/test/kotlin/com/rest1/domain/wallet/PaymentExtraTest.kt`. 주어진 `PaymentPolicyTest.kt`는 건드리지 않았다.

## 2. 정한 규칙과 이유
- **지갑**: 처음 쓸 때 만들어지고, 없으면 잔액 0으로 본다. 유니크 제약 때문에 동시에 만들어도 중복되지 않는다.
- **금액 형식**: 충전 금액과 가격은 정수만 받는다. 1.5처럼 소수가 조용히 1로 잘리는 것을 막으려고 `Number`로 받아 직접 검사했다. 문자열, null, Long 범위를 넘는 값은 400이다.
- **잔액 한도**: 잔액이 Long 범위를 넘는 충전이나 지급은 거부한다.
- **구매 실패** (모두 4xx이고 돈은 움직이지 않는다)
  - 무료 글: 400
  - 작성자 본인: 403
  - 이미 구매함: 409
  - 잔액 부족: 400
  - 없는 글: 404
- **가격은 작성 후 바꿀 수 없다.** 이미 산 사람과 가격이 어긋나는 것을 피하려는 것이다.
- **본문 열람**
  - 유료 글은 작성자와 구매자만 본다. 그 밖의 사람, 관리자, 비로그인은 목록과 단건 조회에서 `content`가 빈 문자열이고 `contentLocked=true`다. `content` 엔드포인트는 403이다. 비로그인은 401이다.
  - 무료 글은 기존 목록·단건 조회가 원래 비로그인에게도 열려 있어서 그 동작을 유지했다. 그래서 무료 글은 로그인하지 않아도 목록과 단건에서 보인다. 기존 테스트가 깨지지 않게 하려는 선택이다. `content` 엔드포인트는 로그인해야 한다.
- **동시성**
  - 잔액은 읽어서 고치지 않고 DB의 원자적 UPDATE로만 바꾼다. 차감은 `balance >= 가격`이라는 조건을 붙였다.
  - 구매는 한 트랜잭션에서 구매자와 작성자의 지갑 행을 id 순서로 잠근다. 그다음 중복 검사, 차감, 작성자 지급, 구매 기록을 처리한다.
  - `Purchase`의 (buyerId, postId) 유니크 제약이 중복 구매를 막는 마지막 방어선이다.
  - 처음에는 `select for update`로 잠그는 방식을 썼다. 그런데 H2에서 동시 충전 5건 중 4건이 사라지는 문제(`PaymentPolicyTest`의 t10 실패)가 있어 원자적 UPDATE 방식으로 바꿨다.
- **테스트 DB 분리**: `PaymentPolicyTest`가 다른 테스트와 같은 인메모리 DB를 공유해서 회원 수·id 단언이 순서에 따라 깨졌다. 그래서 `application-test.yml`을 컨텍스트마다 다른 DB를 쓰도록 바꿨다(`jdbc:h2:mem:${random.uuid}`).

## 3. 확인한 방법과 결과
- `./gradlew cleanTest test`를 JDK 21, H2로 3번 돌렸고 전부 성공했다. 기존 테스트, `PaymentPolicyTest` 10개, 내가 추가한 `PaymentExtraTest` 6개가 모두 통과했다.
- `PaymentExtraTest`가 확인하는 것:
  - 새 회원 잔액 0, 비정상 충전 금액 거부
  - 비로그인 목록과 단건에서 유료 본문 숨김
  - 구매자와 작성자에게는 본문 노출
  - 무료 글 구매 불가, 소수 가격 거부
  - 같은 구매자의 동시 구매 6건 중 1건만 성공
  - 5명이 동시에 사도 작성자 잔액 정확
  - 서로의 글을 동시에 사도 교착 없음
- **확인하지 못한 것**
  - 실제로 띄운 앱에 Python으로 요청을 보내는 수동 확인은 하지 않았다. 테스트 안의 HTTP 호출(RANDOM_PORT)로만 확인했다.
  - H2 외의 DB, 예를 들어 MySQL에서의 동시성은 확인하지 못했다.
  - 대량 부하 테스트는 하지 않았다.
  - 작성자가 글을 삭제한 뒤의 구매 기록 처리는 확인하지 않았다.

## 4. 남은 문제나 위험
- 글을 삭제해도 구매 기록과 이미 지급된 금액은 그대로 남고, 환불은 없다.
- 지갑 거래 내역(원장) 테이블이 없다. 잔액과 구매 기록만 있고 충전·차감 이력은 남기지 않는다.
- 글 수정(PUT)은 작성자만 가능하다. 유료 글 본문을 구매 후에 바꿀 수 있다는 점은 정책으로 정하지 않았다.
- 같은 요청을 재시도했을 때 충전이 중복 반영되는 것을 막는 장치(멱등키)는 없다.
- 대부분의 비정상 입력은 400으로 나간다. 다만 `price`에 Long 범위를 넘는 큰 수가 들어오면 400이 아니라 Jackson 오류로 처리될 수 있는데, 이 경우는 확인하지 못했다.
