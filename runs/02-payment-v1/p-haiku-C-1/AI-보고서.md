# p-haiku-C-1 — AI 가 작업 후 낸 보고서 (원문)

## 결과 요약
`C:\ctj\exp2\p-haiku-C-1` 에 유료 글 결제 기능을 구현했고 커밋했다 (`48678ac payment feature`). `./gradlew test` 는 47개 전부 통과한다 (기존 37개 + PaymentPolicyTest 10개). 시작 01:28:56, 커밋 01:35 경으로 90분 안에 끝냈다.

## 1. 만든 것
**새 파일 (src/main/kotlin/com/rest1/domain/wallet/)**
- `entity/Wallet.kt`: 회원당 지갑 1개 (memberId unique, balance). `@Check(balance >= 0)`.
- `entity/PostPurchase.kt`: 구매 기록 (buyerId, postId, price), (buyerId, postId) 유일 제약.
- `repository/WalletRepository.kt`: `PESSIMISTIC_WRITE` 잠금 조회 `findByMemberIdForUpdate`.
- `repository/PostPurchaseRepository.kt`
- `service/WalletService.kt`: 충전, 구매, 잔액 조회, 지갑 생성.
- `controller/ApiV1WalletController.kt`: `GET /api/v1/wallet` → `{"balance": n}`, `POST /api/v1/wallet/charge` (201).

**수정 파일**
- `post/entity/Post.kt`: `price` (기본 0), `isPaid` (price > 0).
- `post/service/PostService.kt`: 작성 시 price 음수 거부 (400-7), 본문 열람 판정, 구매 여부 조회, 구매 기록 존재 확인.
- `post/dto/PostDto.kt`: `price`, `locked` 추가. 열람 불가 유료 글은 `content = null`.
- `post/controller/ApiV1PostController.kt`:
  - `POST /api/v1/posts` 에 `price` 추가 (생략 시 0).
  - 목록·단건 조회는 요청자 기준으로 본문을 가린다.
  - `GET /api/v1/posts/{id}/content` → `{"postId", "content"}`, 열람 불가 시 403-3.
  - `POST /api/v1/posts/{id}/purchase` → 201, `{"postId", "price", "balance"}`.
  - 구매 기록이 있는 유료 글 삭제는 409-3.
- `member/service/MemberService.kt`: 가입 시 지갑을 함께 만든다.
- `global/rq/Rq.kt`: 비로그인 시 null 을 주는 `actorIdOrNull` 추가.
- `src/main/resources/application-test.yml`: 테스트 DB URL을 `jdbc:h2:mem:db_test_${random.value}` 로 바꿈 (아래 "정한 규칙" 참고).

## 2. 정한 규칙과 이유
- **동시성**: 충전·구매 모두 지갑 행을 `SELECT ... FOR UPDATE` 로 잠근 뒤 처리한다. 구매는 관련된 두 지갑을 id 순서로 잠가 교차 구매 교착을 막는다. 중복 구매 확인은 잠금을 잡은 뒤에 하고, DB 유일 제약이 한 번 더 막는다. 잔액 음수는 서비스 검사와 DB CHECK 두 곳에서 막는다.
- **지갑 생성**: 가입 시 만든다. 지갑이 없는 기존 회원은 처음 쓸 때 만든다 (이 경로의 동시 생성 경쟁은 막지 않았다).
- **구매 순서 검사**: 무료 글 (400-3) → 작성자 본인 (400-4) → 잠금 → 중복 구매 (409-2) → 잔액 부족 (400-5). 실패 시 잔액은 바뀌지 않는다.
- **열람**: 무료 글은 누구나 (비로그인 목록·단건 포함). 유료 글은 작성자와 구매자만 본문을 본다. 목록·단건 응답에서도 본문은 null 이다. 본문 전용 API는 비로그인 401, 권한 없음 403.
- **충전**: 1 이상 (0·음수는 400-1 검증 실패). 서비스에도 같은 검사를 둔다 (400-6).
- **가격**: 0 이상 (음수는 400-1, 서비스에서 400-7).
- **수정 API**: 글 수정(PUT)으로는 가격을 바꾸지 않는다. 가격은 작성 시점에만 정한다.
- **유료 글 삭제**: 구매 기록이 있으면 409-3 으로 막는다. 환불 정책이 없어서 구매 금액과 기록이 어긋나는 것을 막으려고 정했다.
- **테스트 DB 분리 (범위 밖 변경)**: 기존 테스트는 `@Transactional` 롤백에 의존하고 고정된 회원 수·글 id 를 검증한다. PaymentPolicyTest 는 HTTP 로 커밋하므로 같은 H2 DB 를 쓰면 기존 테스트 4개가 깨졌다 (회원 16명, 글이 섞임). 테스트 파일은 건드리지 않고, 테스트 프로필에서 컨텍스트마다 별도 메모리 DB 를 쓰게 했다. 이 한 줄이 기존 테스트를 살리는 데 필요했다.

## 3. 확인 방법과 결과
- `./gradlew test` (JDK 21, H2): 47/47 통과. 변경 전 기준선은 PaymentPolicyTest 10개만 실패였다.
- 임시 테스트를 추가해 아래를 확인한 뒤 삭제했다 (커밋 포함 안 됨).
  - price 생략 시 무료 (201, price 0), 무료 글 본문은 다른 회원이 열람 가능.
  - 음수 충전 400-1.
  - 구매 201, 잔액 1000→900, 작성자 잔액 +100.
  - 구매된 유료 글 삭제 409-3.
  - 비로그인 목록에 유료 본문 없음, 비로그인 단건은 content null, 본문 API 401.
- **확인하지 못한 것**
  - 실제 기동한 서버에 대한 외부 부하 테스트. 동시성은 테스트 안의 2~5개 동시 요청으로만 확인했다.
  - MySQL 등 H2 외 DB에서의 잠금·CHECK 동작.
  - 지갑 없이 이미 존재하던 기존 DB의 마이그레이션 (ddl-auto update 에 맡겼다).
  - 구매 도중 글이 삭제되는 경쟁 상황.
  - 이번 추가 검증은 `curl` 을 쓰지 않았다 (테스트 HTTP 호출만 사용).

## 4. 남은 문제·위험
- 기존 DB의 지갑 없는 회원은 첫 요청 때 지갑을 만드는데, 그 경로에서 동시에 두 요청이 오면 유일 제약 위반으로 500 이 날 수 있다.
- 환불·취소 기능과 가격 변경 API 는 없다.
- 유료 글 본문 수정은 구매자에게도 새 내용으로 보인다 (정책 밖이라 손대지 않았다).
- 커밋 시 git 이 CRLF 경고를 냈다 (.gitattributes 설정에 따른 것, 기능 영향 없음).
- 원격 저장소 push 는 하지 않았다.
