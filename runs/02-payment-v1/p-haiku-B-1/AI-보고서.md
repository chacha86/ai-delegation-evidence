# p-haiku-B-1 — AI 가 작업 후 낸 보고서 (원문)

## 결과 요약
`C:\ctj\exp2\p-haiku-B-1` 에 유료 글 결제 기능을 구현했고 커밋했다 (`9511fab payment feature`, 원격 push 없음). 시작 01:17:48, 완료 약 01:29 로 90분 안에 끝났다. 전체 `./gradlew test` 는 59개 전부 통과했다 (기존 37개 + 신규 22개).

## 1. 만든 것
추가/수정 파일 (모두 `C:\ctj\exp2\p-haiku-B-1\src\` 아래):
- `main\kotlin\com\rest1\domain\member\member\entity\Member.kt`: `balance: Long` (지갑 잔액, 기본 0), `credit`/`debit`, `@Check(balance >= 0)`
- `main\kotlin\com\rest1\domain\member\member\repository\MemberRepository.kt`: `findAllByIdForUpdate` (PESSIMISTIC_WRITE, id 오름차순)
- `main\kotlin\com\rest1\domain\post\post\entity\Post.kt`: `price` (기본 0, `@Check(price >= 0)`), `isPaid`
- `main\kotlin\com\rest1\domain\post\post\entity\PostPurchase.kt` (신규): 구매 기록, `(post_id, buyer_id)` 유니크
- `main\kotlin\com\rest1\domain\post\post\repository\PostPurchaseRepository.kt` (신규)
- `main\kotlin\com\rest1\domain\post\post\service\PostPurchaseService.kt` (신규): 구매, 본문 열람 판단
- `main\kotlin\com\rest1\domain\post\post\service\PostService.kt`: write 에 price, 구매자 있는 유료 글 삭제 거부
- `main\kotlin\com\rest1\domain\post\post\dto\PostDto.kt`: `price`, `contentLocked` 추가, 비공개 본문은 `content=null`
- `main\kotlin\com\rest1\domain\post\post\controller\ApiV1PostController.kt`: 작성 시 price, `POST /{id}/purchase`, `GET /{id}/content`, 목록/단건 응답 가림
- `main\kotlin\com\rest1\domain\wallet\service\WalletService.kt` (신규), `...\wallet\controller\ApiV1WalletController.kt` (신규)
- `main\kotlin\com\rest1\global\rq\Rq.kt`: `actorOrNull` (비로그인 허용 조회용)
- 테스트 (신규): `test\kotlin\com\rest1\domain\wallet\controller\ApiV1WalletControllerTest.kt` (5개), `test\kotlin\com\rest1\domain\post\post\controller\ApiV1PostPurchaseControllerTest.kt` (13개), `test\kotlin\com\rest1\domain\post\post\service\PostPurchaseConcurrencyTest.kt` (4개)

API:
- `GET /api/v1/wallet` → `{"balance": N}` (평문 JSON)
- `POST /api/v1/wallet/charge`, body `{"amount": N}` → RsData, `data.balance`
- `POST /api/v1/posts` 에 `price` (생략 가능, 음수 400-1)
- `POST /api/v1/posts/{id}/purchase` → RsData, `data.balance`
- `GET /api/v1/posts/{id}/content` → `{"content": "..."}` (평문 JSON)

## 2. 정한 규칙과 이유
- 지갑은 별도 테이블이 아니라 Member 행의 `balance` 컬럼이다. 회원 생성 시점 경합 없이 회원 행 잠금 하나로 충전과 차감을 직렬화하기 위해서다.
- 동시성: 구매는 구매자와 작성자 행을 id 순서로 `SELECT ... FOR UPDATE` 한다. 순서를 고정해 A→B, B→A 교착을 막는다. 같은 회원의 구매는 구매자 행 잠금으로 직렬화되고, DB 유니크 제약이 마지막 방어선이다.
- 충전: 1 이상만 허용. 0, 음수, 누락은 400-1, 문자열은 400-2. 잔액 overflow 는 400-4.
- 구매 실패 코드: 잔액 부족 400-3, 이미 구매 409-1, 작성자 본인 403-3, 없는 글 404-1. 실패 시 잔액과 구매 기록은 변하지 않는다.
- 스스로 정한 것 1: 무료 글 구매는 거부한다 (400-4). 정책에 없던 부분이다.
- 스스로 정한 것 2: 구매자가 있는 유료 글은 삭제를 거부한다 (409-2). 삭제하면 구매자가 돈을 내고 본문 열람 권한을 잃기 때문이다.
- 가격은 작성 후 바꿀 수 없다. PUT 은 title/content 만 받는다.
- 본문 가림: 유료 글은 작성자와 구매자만 본다. 목록과 단건 응답에서는 비공개 본문을 `content: null`, `contentLocked: true` 로 준다. 관리자도 비구매자면 본문을 볼 수 없다 (정책 그대로). 무료 글은 기존처럼 목록과 단건이 비로그인에도 열려 있고, `/content` 는 로그인이 필요하다.
- 응답 형식은 기존 관례를 따랐다. GET 은 기존 GET 들처럼 평문 JSON, POST 는 RsData 다. 스펙의 "응답 JSON 에 balance 필드" 를 최상위 `balance` 로 읽었다. `data.balance` 로 기대하는 쪽이라면 응답 형식만 바꾸면 된다.
- `ddl-auto: update` 에서 기존 DB 파일이 있어도 컬럼 추가가 실패하지 않도록 `columnDefinition = "bigint default 0"` 을 주었다.
- 신규 테스트 3개 클래스에 `@DirtiesContext(AFTER_CLASS)` 를 달았다. 기존 테스트가 auto-increment id (예: 회원 id 6, 글 id 4) 에 의존하는데, 신규 테스트가 같은 H2 컨텍스트에서 id 를 소모해 기존 테스트가 실패했기 때문이다. 기존 테스트는 수정하지 않았다.

## 3. 확인 방법과 결과
- `./gradlew test`: 59개 전부 통과. 기존 테스트는 베이스라인(33초, 전부 통과)과 동일하게 통과한다.
- 동시성 검증: 구매 잠금을 임시로 제거하면 동시성 테스트 3개가 실패했다. 잠금을 복구한 뒤 전부 통과했다. 즉 테스트가 실제로 잠금 효과를 잡는다.
- 실서버 검증: `bootRun` 으로 localhost:8080 에 띄우고 Python `urllib` 로 34개 시나리오를 확인했다 (무료·유료 가림, 구매 규칙, 같은 회원의 동시 구매 10건 중 성공 1건, 충전 10건과 구매 10건 혼합 시 잔액이 `1000 - 300 × 성공 수` 와 일치, 잔액 음수 없음, 구매자 있는 글 삭제 409-2). 34개 전부 통과했다. 실행 후 서버는 종료했고 생성된 `db_dev.mv.db` 는 삭제했다.

확인하지 못한 것:
- H2 MySQL 모드 외 DB (실제 MySQL 등) 에서의 동작.
- 기존 dev DB 파일이 이미 있는 상태에서 `ddl-auto: update` 로 컬럼이 추가되는지. 파일이 없는 상태에서만 실행했다.
- 대규모 동시성 (수십 스레드 이상). 최대 20 스레드까지만 확인했다. H2 lock timeout 여부도 확인하지 않았다.
- 충전 한도 overflow (400-4) 는 단위 테스트가 없다.

## 4. 남은 문제와 위험
- 충전에 결제 연동이 없다. 누구나 무료로 무제한 충전할 수 있다. 스펙에 없어서 두었다.
- 구매 후 작성자가 본문을 수정하면 구매자도 수정된 본문을 본다. 환불 정책도 없다.
- 무료 글의 목록과 단건은 기존대로 비로그인에도 공개다.
- 기존 테스트의 id 의존은 여전히 취약하다. 신규 테스트는 DirtiesContext 로 격리했을 뿐이다.
- 응답 형식 (GET 평문 vs `data` 래핑) 해석이 틀렸다면 `ApiV1WalletController` 와 `ApiV1PostController.getContent` 의 반환 타입만 바꾸면 된다.
