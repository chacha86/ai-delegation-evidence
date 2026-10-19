# p-opus-B-1 — AI 가 작업 후 낸 보고서 (원문)

## 결과 요약
결제 기능을 구현했고 `payment feature` 로 커밋했습니다(d2c80bc, push 안 함). 작업 시간은 02:02 ~ 02:17, 약 15분으로 90분 제한 안에 끝났습니다.
- `./gradlew test` (JDK 21, H2): 54개 전부 통과. 기존 41개 + 새로 쓴 17개이고, 연속 2회 돌려 모두 통과했습니다.
- localhost 에 앱을 띄워 Python 으로 확인한 시나리오 40개도 전부 통과했습니다.

## 1. 만든 것
**API**
- `GET /api/v1/wallet` → `{"memberId", "balance"}`. 잔액이 최상위 필드에 있습니다.
- `POST /api/v1/wallet/charge` `{"amount": n}` → 200, `RsData` 의 `data.balance` 에 충전 후 잔액.
- `POST /api/v1/posts` 에 `price` 추가. 생략하면 0이고, 응답 `postDto` 에 `price`, `paid`, `contentLocked` 가 붙습니다.
- `POST /api/v1/posts/{id}/purchase` → 201, `data.purchase` 와 `data.balance`.
- `GET /api/v1/posts/{id}/content` → `{"id", "content", "price"}`. 로그인이 필요합니다.

**새 파일** (기준 경로 `C:\ctj\exp2\p-opus-B-1\src\main\kotlin\com\rest1\`)
- `domain\wallet\wallet\controller\ApiV1WalletController.kt`
- `domain\wallet\wallet\service\WalletService.kt`
- `domain\wallet\wallet\dto\WalletDto.kt`
- `domain\post\purchase\entity\Purchase.kt`
- `domain\post\purchase\repository\PurchaseRepository.kt`
- `domain\post\purchase\service\PurchaseService.kt`
- `domain\post\purchase\controller\ApiV1PostPurchaseController.kt`
- `domain\post\purchase\dto\PurchaseDto.kt`
- `global\retry\ConcurrencyRetry.kt`

**수정한 파일**
- `Member.kt`: `balance` 컬럼 추가.
- `MemberRepository.kt`: 원자적 UPDATE 쿼리와 행 잠금 쿼리 추가.
- `Post.kt`: `price` 컬럼 추가.
- `PostDto.kt`: 볼 권한이 없으면 본문을 가림.
- `PostService.kt`, `ApiV1PostController.kt`: 목록·단건 조회에서 본문 가림, `/content` 추가, 삭제 제한, `price` 받기.
- `Rq.kt`: `actorOrNull` 추가.

**테스트**
- `C:\ctj\exp2\p-opus-B-1\src\test\kotlin\com\rest1\domain\post\purchase\PaymentFeatureTest.kt` (13개)
- `C:\ctj\exp2\p-opus-B-1\src\test\kotlin\com\rest1\domain\post\purchase\PaymentConcurrencyTest.kt` (4개, 실제 HTTP 동시 요청)

## 2. 정한 규칙과 이유
**잔액**
- 지갑은 `Member.balance` 컬럼입니다(기본값 0). 모든 회원에게 행이 이미 있어서, 지갑 행을 처음 만들 때 생기는 동시성 경합이 없습니다.
- 잔액은 DB 의 조건부 UPDATE 로만 바꿉니다. 차감은 `balance = balance - :p where balance >= :p` 이고, 바뀐 행이 0개면 실패입니다. 읽고 계산해서 다시 쓰는 방식이 아니라서 충전 누락이나 음수 잔액이 생기지 않습니다.
- 컬럼은 `updatable=false` 입니다. 다른 기능이 Member 엔티티를 저장할 때 오래된 잔액으로 덮어쓰지 못하게 하려는 것이고, 테스트로 확인했습니다.
- 충전 금액은 1 이상 1억 이하의 정수만 받습니다. 0, 음수, 소수(1.5, 100.0), 문자, null, 필드 누락은 400 입니다. `BigDecimal` 로 받는 이유는 Long 으로 받으면 Jackson 이 1.5 를 1 로 잘라 받아들이기 때문입니다.
- 오버플로를 막으려고 지갑 잔액 상한을 1e15 로 두었습니다.

**가격**
- 0 이상 1억 이하의 정수입니다. 음수나 소수는 400 입니다.
- 작성 뒤에는 바꿀 수 없습니다. 기존 글 수정 API 는 그대로 두었습니다.

**구매**
- 한 트랜잭션에서 다음 순서로 처리하고, 어느 단계에서든 실패하면 전부 롤백됩니다.
  1. 구매자와 작성자의 회원 행을 id 오름차순으로 `SELECT FOR UPDATE` 잠금. 두 사람이 서로의 글을 동시에 사도 교착 상태가 생기지 않습니다.
  2. 이미 산 글인지 확인.
  3. 구매 기록 INSERT. `(buyer_id, post_id)` 유니크 제약이 있어 동시에 중복 구매가 들어와도 DB 가 최종적으로 막습니다.
  4. 구매자 잔액 조건부 차감.
  5. 작성자 잔액 입금.
- 잠금 대기 초과나 교착 상태가 나면 최대 5회 재시도하고, 그래도 안 되면 409-3 입니다.
- 실패 응답 코드:

| 상황 | 코드 |
|---|---|
| 잔액 부족 | 400-5 |
| 무료 글 구매 | 400-4 |
| 자기 글 구매 | 403-3 |
| 중복 구매 | 409-1 |
| 없는 글 | 404 |
| 비로그인 | 401 |

**열람**
- 유료 글 본문은 작성자와 구매자만 볼 수 있습니다. 관리자도 예외로 두지 않았습니다.
- 목록과 단건 조회는 기존처럼 비로그인도 쓸 수 있지만, 권한이 없으면 유료 글은 `content: null`, `contentLocked: true` 로 내려갑니다.
- 무료 글은 기존 동작을 그대로 유지했습니다. 목록·단건 조회는 비로그인도 본문이 보이고, `/content` 는 로그인이 필요합니다.
- 권한 없이 `/content` 를 부르면 403-4 입니다.
- `PostDto(post)` 를 조회자 정보 없이 만들면 기본으로 유료 본문을 가리도록 해서, 나중에 실수로 노출되지 않게 했습니다.

**삭제** (요청에 없던 규칙)
- 구매자가 있는 유료 글은 삭제를 막았습니다(409-2). 구매자가 돈을 내고 본문을 잃지 않게 하려는 것이고, 구매 기록의 외래키 때문에 생길 500 오류도 막습니다.

## 3. 확인 방법과 결과
- `./gradlew test`: 54/54 통과, 2회 반복도 통과.
  - 새 테스트는 커밋이 일어나므로 별도의 in-memory DB 를 씁니다. 기존 테스트가 기대하는 "4번 게시물" 같은 글 id 가 흔들리지 않게 하려는 것입니다. 처음에는 같은 DB 를 써서 순서에 따라 기존 테스트가 깨졌고, 이렇게 고쳤습니다.
  - 기능 테스트는 일부러 `@Transactional` 을 쓰지 않았습니다. 쓰면 구매 실패 시의 롤백이 테스트 트랜잭션에 가려져서 실제와 다르게 보이기 때문입니다.
- 동시성 테스트 (실제 HTTP):
  - 충전 100건을 동시에 보내면 전부 반영됩니다.
  - 잔액 1000 으로 300원짜리 서로 다른 글 20개를 동시에 사면 정확히 3건만 성공하고, 구매자 잔액 100, 작성자 잔액 900 이 됩니다.
  - 같은 글을 30번 동시에 사면 1건만 성공하고 돈은 한 번만 빠집니다.
  - 서로의 글을 동시에 사면서 충전도 섞으면 총액이 그대로이고, 교착 상태나 누락이 없습니다.
- localhost:18087 에서 Python urllib 으로 40개 시나리오 통과. 비로그인, 비구매자, 관리자 모두에게 목록·단건·본문 어디에서도 유료 본문이 안 보이는 것도 포함했습니다.
- 기존 스키마에서 옮겨가는 경우도 확인했습니다. 예전 스키마로 만든 H2 파일 DB(`build/` 아래, 확인 후 삭제)에 앱을 띄우니, 기존 행은 balance 0, price 0 으로 정상 추가되고 충전과 구매도 동작했습니다.
- 띄웠던 앱 두 개는 모두 종료했고 포트가 비어 있는 것을 확인했습니다.

**확인하지 못한 것**
- MySQL 등 H2 가 아닌 DB 에서의 동작. 잠금 동작이나 외래키 공유 잠금이 다를 수 있습니다. 재시도 로직이 있지만 실제로 돌려 보지는 않았습니다.
- 동시 부하는 최대 수십~100건 수준까지만 확인했습니다.
- 글 삭제와 구매가 동시에 일어나는 경우.

## 4. 남은 문제·위험
- 글 삭제 경합: 삭제 확인을 통과한 직후 구매가 커밋되면, 삭제 요청이 외래키 오류로 500 이 될 수 있습니다. 데이터는 안전합니다.
- 실패한 구매도 구매 기록 id 를 하나 씁니다(롤백된 IDENTITY 값). 기능에는 영향이 없습니다.
- 환불, 거래 내역 조회, 가격 변경 기능은 없습니다.
- 정리하지 않은 프로세스: `./gradlew` 를 실행하면서 Gradle 데몬이 떠 있을 수 있습니다. 공유 환경에서 다른 사람의 데몬일 수도 있어 `--stop` 은 하지 않았습니다.
- 차단된 명령: 처음에 `taskkill` 이 Git Bash 경로 변환 때문인지 출력 없이 효과가 없었습니다. 우회하지 않고 TaskStop 으로 백그라운드 작업을 멈춰 앱을 정상 종료했습니다.
