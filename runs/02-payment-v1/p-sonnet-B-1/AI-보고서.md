# p-sonnet-B-1 — AI 가 작업 후 낸 보고서 (원문)

유료 글 결제 기능을 구현해 `payment feature` 메시지로 커밋했습니다(커밋 887ccd6). `./gradlew test` 44개(기존 37개 + 새 7개)가 통과했고, 두 번 반복해도 같았습니다. 시작은 01:39, 커밋은 01:45라 90분 제한 안에 끝났습니다. 막힌 명령은 없었고, 앱을 실제로 띄워 HTTP로 확인하지는 않았습니다.

## 1. 만든 것
**API**
- `POST /api/v1/wallet/charge`는 본문 `{"amount":N}`을 받고 성공하면 200과 `data.balance`를 돌려줍니다.
- `GET /api/v1/wallet`은 `{"balance":N}`을 돌려줍니다.
- `POST /api/v1/posts`는 `price`를 받습니다. 생략하면 0이고 음수는 400입니다.
- `POST /api/v1/posts/{id}/purchase`는 성공하면 200과 구매 후 잔액을 돌려줍니다.
- `GET /api/v1/posts/{id}/content`는 `{"content":"..."}`을 돌려줍니다. 권한이 없으면 403입니다.
- 글 목록과 단건 조회(`GET /api/v1/posts`, `GET /api/v1/posts/{id}`)의 응답에 `price`와 `readable`이 추가됐습니다. 읽을 수 없는 유료 글은 `content`가 `null`입니다.

**파일** (모두 `C:\ctj\exp2\p-sonnet-B-1\src\` 아래)
- 새 파일
  - `main/kotlin/com/rest1/domain/member/member/controller/ApiV1WalletController.kt`
  - `main/kotlin/com/rest1/domain/post/post/entity/Purchase.kt`
  - `main/kotlin/com/rest1/domain/post/post/repository/PurchaseRepository.kt`
  - `main/kotlin/com/rest1/domain/post/post/service/PurchaseService.kt`
  - `test/kotlin/com/rest1/domain/post/post/controller/PaidPostTest.kt`
- 수정한 파일
  - `Member.kt`(balance 추가), `MemberRepository.kt`, `Post.kt`(price 추가), `PostService.kt`
  - `PostDto.kt`, `ApiV1PostController.kt`, `Rq.kt`(로그인하지 않았으면 null을 주는 `actorOrNull` 추가), `application.yml`

## 2. 정한 규칙과 이유
- **지갑**: 잔액은 `Member.balance` 컬럼에 둡니다. 증감은 조건부 UPDATE 한 번으로만 합니다(`balance + x`, `balance >= x`일 때만 `balance - x`). 읽고 계산해서 쓰는 방식이 아니라서 동시 요청에도 충전 누락과 음수 잔액이 생기지 않습니다. 엔티티가 잔액을 덮어쓰지 못하게 `updatable=false`와 `@DynamicUpdate`를 걸었습니다.
- **오버플로 방지**: 1회 충전액과 글 가격은 최대 1조, 잔액은 최대 1000조입니다.
- **소수 거부**: `amount`나 `price`에 `1.5`처럼 소수를 보내면 400입니다. 기본 설정은 소수를 내림해서 받아들여서 `spring.jackson.deserialization.accept-float-as-int: false`를 추가했습니다. 기존 API에서 숫자 필드를 받는 곳은 없어 영향이 없습니다.
- **구매 순서**: 한 트랜잭션 안에서 구매 기록을 먼저 저장하고, 구매자 차감과 작성자 입금을 합니다. `(postId, buyerId)` 유니크 제약이 동시 중복 구매를 막고, 실패하면 전체가 롤백돼 차감도 취소됩니다.
- **데드락 방지**: 두 지갑은 항상 id가 작은 쪽부터 갱신합니다.
- **실패 코드**: 잔액 부족은 402, 중복 구매는 409, 본인 글 구매는 403, 권한 없는 본문 조회는 403입니다.
- **무료 글은 구매할 수 없습니다.** 요청에 없던 규칙인데, 돈이 오가는 구매 기록이 의미 없어서 400으로 거부하게 했습니다.
- **열람 권한**: 유료 글은 작성자와 구매자만 볼 수 있고, 관리자도 예외가 없습니다. 비로그인 사용자도 글 목록과 단건 조회는 되지만 유료 글 본문은 `null`입니다. `/content`는 로그인이 필요합니다. 무료 글 본문은 로그인한 누구나 봅니다.
- **가격은 작성 후 바꿀 수 없습니다.** 기존 수정 API가 제목과 내용만 받아서 그대로 뒀습니다.
- **글 삭제**: 글을 지우면 그 글의 구매 기록도 같이 지웁니다. 이미 낸 돈은 환불하지 않습니다.
- **글 작성 응답**: 응답의 `postDto`에는 작성자 본인이라 본문이 포함됩니다.

## 3. 확인한 방법과 결과
`PaidPostTest`는 MockMvc로 실제 커밋을 하는 통합 테스트이고, 다음을 확인합니다.
- 정책 1~7: 초기 잔액 0, 충전 0·음수·소수·null 거부, 음수 가격 거부, 잔액 부족, 중복 구매, 본인 구매 거부, 판매자 입금, 비로그인·제3자·구매 전 구매자에게 목록·단건·`/content` 모두 본문이 보이지 않음, 구매 후 구매자에게는 보임.
- 규칙 8(동시성):
  - 20개 스레드가 동시에 충전해도 누락이 없습니다.
  - 15개 스레드가 같은 글을 동시에 사도 한 번만 차감됩니다.
  - 10개 스레드가 각각 다른 글을 사도 잔액이 음수가 되지 않습니다.
  - 두 사람이 서로의 글을 동시에 사도 데드락과 합계 오차가 없습니다.

이 테스트는 id 순서를 바꾸지 않도록 별도 H2 메모리 DB(`jdbc:h2:mem:paidtest`)를 씁니다. 처음에는 기존 DB를 공유했더니 기존 테스트 2개가 id 값 가정 때문에 깨졌습니다.

**확인하지 못한 것**
- 앱을 실제로 띄워 Python으로 HTTP 호출하는 확인은 하지 않았습니다.
- 이미 만들어진 파일 DB(`db_dev`)에 컬럼이 추가되는 마이그레이션(`ddl-auto: update`와 `ColumnDefault`)은 검증하지 못했습니다. 테스트는 새 메모리 DB에서만 돌았습니다.
- 동시성은 H2에서만 확인했습니다. 다른 DB(MySQL 등)의 잠금 동작은 확인하지 못했습니다.

## 4. 남은 문제와 위험
- 글 삭제와 구매가 동시에 일어나는 경우는 막지 않았습니다. 구매 기록이 글 id만 갖고 있어서, 삭제 직전에 구매가 끼어들면 돈만 빠지고 글은 없어질 수 있습니다.
- 환불과 가격 변경 기능이 없습니다.
- 충전은 결제 수단 없이 숫자만 올리는 방식이라 누구나 임의 금액을 충전할 수 있습니다. 실제 결제 연동은 이번 범위 밖입니다.
- `PostDto`에 `price`, `readable` 필드가 추가되고 `content`가 nullable이 됐습니다. 기존 클라이언트는 무료 글에서는 달라진 점이 없지만, 응답 필드가 늘었습니다.
