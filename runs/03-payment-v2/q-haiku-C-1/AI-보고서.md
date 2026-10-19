# q-haiku-C-1 — AI 가 작업 후 낸 보고서 (원문)

## 결과 요약
`C:\ctj\exp2\q-haiku-C-1` 에 유료 글 결제 기능을 구현하고 커밋했다 (커밋 `a3eeeb9`, 메시지 `payment feature`, 원격 push 없음). 전체 테스트 49개 중 49개 통과. 작업은 03:00 전후 완료, 시작은 02:54:59 (90분 이내).

## 1. 만든 것
신규 파일 (`src/main/kotlin/com/rest1/domain/wallet/` 아래):
- `entity/Wallet.kt`: 회원당 1행 (`member_id` unique), `balance`, `deposit`/`withdraw`.
- `entity/PostPurchase.kt`: `(buyer_id, post_id)` unique 제약이 있는 구매 기록.
- `repository/WalletRepository.kt`: `findByMemberIdForUpdate` 는 `PESSIMISTIC_WRITE` 잠금.
- `repository/PostPurchaseRepository.kt`
- `service/WalletService.kt`: 지갑 생성, 잔액 조회, 충전, 지갑 잠금.
- `service/PaymentService.kt`: 구매, 본문 열람 판정, 목록 DTO 변환.
- `controller/ApiV1WalletController.kt`: `GET /api/v1/wallet` (응답 `data.balance`), `POST /api/v1/wallet/charge` (`{"amount": n}`).

수정 파일:
- `domain/post/post/entity/Post.kt`: `price: Long = 0`, `isPaid`.
- `domain/post/post/dto/PostDto.kt`: `price`, `locked` 추가. 열람 불가 유료 글은 `content` 가 `null`.
- `domain/post/post/service/PostService.kt`: `write(..., price)` 에서 가격 검증 (음수, 100원 단위).
- `domain/post/post/controller/ApiV1PostController.kt`: `price` 요청 필드 (생략 시 0). `GET /api/v1/posts/{id}/content` 와 `POST /api/v1/posts/{id}/purchase` 추가. 목록과 단건 조회는 열람 권한에 따라 본문을 가린다.
- `domain/member/member/service/MemberService.kt`: 가입 시 지갑 생성.
- `global/rq/Rq.kt`: 비로그인 조회를 위한 `actorIdOrNull`.

API 응답 코드 (기존 규약대로 resultCode 앞 3자리가 HTTP 상태): 충전 201-1, 구매 201-1, 본문 200-1. 실패는 400-3 (잔액 부족, 음수 가격), 400-4 (충전 금액 1 미만, 100원 단위 위반), 400-5 (1회 한도 초과), 400-6 (무료 글 구매), 403-3 (본인 글 구매), 403-4 (열람 권한 없음), 409-1 (중복 구매), 404-1 (글 없음), 401 (비로그인).

## 2. 정한 규칙과 이유
- 잔액 변경은 지갑 행을 `SELECT ... FOR UPDATE` 로 잠근 뒤 한다. 충전과 구매가 같은 트랜잭션 안에서 직렬화되어 충전 누락과 잔액 음수를 막는다.
- 구매는 두 지갑 (구매자, 작성자) 을 회원 id 오름차순으로 잠근다. 두 사람이 서로의 글을 동시에 사도 교착이 생기지 않는다.
- 중복 구매는 잠금 안에서 존재 여부를 확인하고, DB 의 `(buyer_id, post_id)` unique 제약이 백업으로 막는다.
- 작성자 수수료는 `price * 90 / 100` 이다. 가격이 100원 단위이므로 나머지가 생기지 않는다.
- 열람 규칙: 무료 글은 로그인한 누구나 `content` 를 볼 수 있다. 유료 글은 작성자와 구매자만 본다. 목록, 단건 조회, 본문 API 모두 같은 규칙을 쓰고, 비로그인 목록에서는 유료 본문이 `null` 로 나온다. 관리자에게도 우회를 주지 않았다 (정책 5 를 그대로 적용).
- 무료 글 (`price=0`) 구매는 실패 처리했다. 정책에 명시가 없어서 스스로 정했다.
- 본인 글 구매는 403-3 으로 거부했다.
- 지갑은 가입 시 만든다. 가입 전에 만들어진 회원 (기존 dev DB 등) 은 첫 충전이나 구매 때 지연 생성한다.
- 가격 상한은 두지 않았다 (정책에 없음).

## 3. 확인 방법과 결과
- 기존 테스트 기준선 (수정 전, `scratchpad/baseline` 복사본에서 실행): 49개 중 `PaymentPolicyTest` 12개만 실패, 나머지 37개는 통과.
- 수정 후 `./gradlew test` (JDK 21, H2): 49개 전부 통과. `PaymentPolicyTest` 12개 포함. 기존 테스트 파일과 `PaymentPolicyTest.kt` 는 수정하지 않았다.
- 추가 검증 (별도 복사본 `scratchpad/verify2` 에서만 실행, 저장소에는 넣지 않음): 충전 상한 경계 (100000 허용, 100001 거부), 음수 충전 거부, 비로그인 `/wallet` 401, 목록에서 비구매자와 비로그인에게 유료 본문이 `null` 로 나오고 구매자와 작성자에게는 보이는지, 무료 글 본문 접근, 작성자와 구매자 잔액 (300 차감, 270 입금), 두 사람이 서로의 글을 동시에 구매하는 교차 구매 (교착 없음, 잔액 정확), 동시 충전 10건 (잔액 정확) 모두 통과.
- 뮤턴트 확인: 지갑 잠금 애너테이션을 제거하면 `동시에 충전해도 빠지는 금액이 없다` 테스트가 실패한다. 잠금이 실제로 필요하다는 뜻이다.
- 확인하지 못한 것: 실제 MySQL 에서의 동작 (H2 MySQL 모드만 확인), 여러 인스턴스에서의 동시성, 기존 dev DB (`db_dev`) 에 대한 마이그레이션 (`ddl-auto: update` 에 의존), Swagger 문서 화면. 실행 중인 앱 프로세스나 Docker 는 띄우지 않았다.

## 4. 남은 문제와 위험
- 유료 글을 삭제해도 구매자에게 환불하지 않는다. 구매 기록은 남는다 (정책에 없음).
- 가입 전에 만들어진 회원이 같은 순간 동시에 처음 충전하면 지갑 행 중복 생성이 일어날 수 있다. unique 제약 때문에 한쪽이 500 으로 실패한다. 드물고 기존 데이터에만 해당한다.
- `PostDto` 응답에 `price`, `locked` 필드가 추가되었고, 유료 글의 `content` 는 열람 불가 시 `null` 이다. 무료 글 응답은 기존과 같다.
- 충전은 실제 결제 연동 없이 잔액만 올린다.
- 저장소 파일의 줄바꿈은 작업 트리 기준 CRLF 로 맞췄다 (`core.autocrlf=true`). git 이 커밋 시 경고를 출력하지만 실제 내용 문제는 없다.
- 환경 메모: 시스템 환경 정보에는 작업 디렉터리가 `C:\ctj\chavis` 로 나와 있지만, 지시대로 `C:\ctj\exp2\q-haiku-C-1` 에서 작업했다.
- 명령이 차단되거나 `curl` 을 쓴 경우는 없었다.
