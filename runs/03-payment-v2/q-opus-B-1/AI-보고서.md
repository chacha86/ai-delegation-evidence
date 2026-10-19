# q-opus-B-1 — AI 가 작업 후 낸 보고서 (원문)

유료 글 결제 기능을 넣고 `C:\ctj\exp2\q-opus-B-1` 에 커밋했습니다(`bac8bfd payment feature`, push 안 함). `./gradlew test` 는 46개 전부 통과합니다(기존 37개 + 새 9개). localhost 에 띄운 앱을 Python 으로 확인한 24개 항목도 모두 통과했습니다. 작업 시간은 03:29:55 ~ 03:39:38, 약 10분입니다.

## 1. 만든 것

**API** (인증은 기존 Bearer apiKey/JWT 방식 그대로)

| API | 결과 |
|---|---|
| `GET /api/v1/wallet` | 200, `{"memberId", "balance"}` |
| `POST /api/v1/wallet/charge` `{"amount": n}` | 200, `{"resultCode":"200-1", "msg", "data":{"memberId","balance"}}` |
| `POST /api/v1/posts` | `price` 를 추가로 받음(생략하면 0) |
| `POST /api/v1/posts/{id}/purchase` | 201, `data` 에 `purchaseId, postId, price, balance` |
| `GET /api/v1/posts/{id}/content` | 200, `{"id","title","price","paid","content"}` |

- 글 목록·단건 응답(PostDto)에 `price`, `paid`, `contentLocked` 가 추가됐습니다. 볼 권한이 없으면 `content` 는 `null` 입니다.

**새 파일** (`src/main/kotlin/com/rest1/` 아래)
- `domain/wallet/wallet/entity/Wallet.kt`: 회원당 지갑 1개(`member_id` 유니크)
- `domain/wallet/wallet/repository/WalletRepository.kt`: 잔액을 바꾸는 원자적 UPDATE 쿼리
- `domain/wallet/wallet/service/WalletService.kt`
- `domain/wallet/wallet/controller/ApiV1WalletController.kt`
- `domain/post/purchase/entity/Purchase.kt`: 구매 기록. `(buyer_id, post_id)` 유니크, 가격·작성자 수입·수수료를 함께 저장
- `domain/post/purchase/repository/PurchaseRepository.kt`
- `domain/post/purchase/service/PurchaseService.kt`
- `standard/ut/Amounts.kt`: 금액이 정수인지 엄격하게 검사
- 테스트: `C:\ctj\exp2\q-opus-B-1\src\test\kotlin\com\rest1\domain\payment\PaymentFeatureTest.kt`

**수정한 파일**
- `Post.kt`: `price` 컬럼(`bigint default 0 not null`), 가격 검증
- `PostService.kt`: 가격 받는 `write` 추가, 본문 열람 권한 판단, 삭제 제한
- `PostDto.kt`, `ApiV1PostController.kt`
- `Rq.kt`: 비로그인이면 null 을 주는 `actorOrNull` 추가
- `MemberService.kt`: 가입할 때 잔액 0 지갑 생성
- `application-dev.yml`, `application-test.yml`: H2 URL 에 `LOCK_TIMEOUT=10000` 추가

## 2. 정한 규칙과 이유

**잔액·충전**
- 잔액은 엔티티 값을 읽고 고쳐 쓰지 않고, 원자적 UPDATE 로만 바꿉니다. 동시 충전이 누락되지 않게 하려는 것입니다.
  - 충전: `balance = balance + :amt`
  - 차감: `balance = balance - :p WHERE balance >= :p`. 바뀐 행이 0건이면 잔액 부족으로 처리하므로 잔액이 음수가 될 수 없습니다.
- 충전 금액은 1 ~ 100,000 의 정수만 받습니다.
  - 0·음수는 400-3, 100,000 초과는 400-4, 빠지거나 null 이면 400-1 로 거부합니다.
  - 소수(1.5)나 너무 큰 수는 400-2 로 거부합니다. Jackson 이 Long 에 1.5 를 넣으면 조용히 1 로 버리기 때문에 BigDecimal 로 받아 직접 검사합니다.
- 기존 회원은 첫 충전·구매 때 지갑을 만듭니다(지갑이 없으면 잔액 조회는 0).
  - 동시에 만들려고 해도 유니크 제약 때문에 하나만 생깁니다.
  - 지갑 만드는 트랜잭션은 구매·충전 트랜잭션을 열기 전에 돌립니다. 커넥션 풀이 바닥나 서로 기다리는 일을 막으려는 것입니다.

**구매** (한 트랜잭션 안에서 처리)
1. 구매 기록을 INSERT 합니다. 유니크 제약이 있어 동시 중복 구매는 DB 가 막고 409 를 돌려줍니다.
2. 구매자 잔액을 조건부로 차감합니다.
3. 작성자에게 `price * 90 / 100` 을 입금합니다.
- 중간에 하나라도 실패하면 전부 롤백되어 돈이 움직이지 않습니다.
- 두 지갑의 UPDATE 는 항상 회원 id 가 작은 쪽부터 실행합니다. A 와 B 가 서로의 글을 동시에 살 때 교착이 생기지 않게 하려는 것입니다.
- 실패 코드: 무료 글 400-6, 자기 글 403-3, 이미 구매 409-2, 잔액 부족 400-5, 없는 글 404, 비로그인 401.
- 무료 글 구매는 의미가 없어서 실패로 처리했습니다.
- 수수료 10% 는 어느 지갑에도 넣지 않고 Purchase 의 `platformFee` 에 기록만 합니다.

**가격**
- 0 이상, 100원 단위만 받습니다(150 은 400-8).
- 오버플로를 막으려고 상한 10억(`MAX_PRICE`)을 따로 정했습니다.
- 글 수정 API 로는 가격을 바꿀 수 없습니다(`val`). 구매자를 보호하려는 것입니다.

**열람**
- 유료 글 본문은 작성자와 구매자만 볼 수 있습니다. 관리자도 볼 수 없습니다.
- 목록·단건 조회에서는 비로그인·제3자에게 `content: null`, `contentLocked: true` 를 줍니다.
- `/content` 는 로그인이 필요하고, 권한이 없으면 403-4 입니다.
- 무료 글은 목록·단건 조회에서 비로그인도 계속 볼 수 있게 두었습니다. 기존 동작이고, 기존 테스트 t1·t2 가 비로그인으로 본문을 확인하기 때문입니다.

**삭제**
- 구매자가 있는 유료 글은 삭제를 막습니다(409-3). 구매자가 산 본문을 잃지 않게 하려는 것입니다.
- 기존 글 삭제 API 의 동작이 바뀌는 건 이 경우뿐입니다.

## 3. 확인한 방법과 결과

- **기존 테스트**: 작업 전 `./gradlew test` 가 통과하는 것을 먼저 확인했고, 작업 후에도 46개가 모두 통과합니다.
- **새 테스트 9개** (`PaymentFeatureTest`, MockMvc 사용)
  - 충전 검증(경계값 포함), 가격 검증, 구매 규칙 5가지, 목록·단건·`/content` 노출(비로그인·제3자·관리자·작성자·구매자), 삭제 제한
  - 동시성 4가지:
    - 같은 글 20건 동시 구매 → 정확히 1건 성공, 차감 1번
    - 잔액 3,000원으로 1,000원짜리 글 10개 동시 구매 → 3건만 성공, 잔액 0
    - 100원 동시 충전 50건 → 잔액 5,000
    - 서로의 글 교차 구매 20건 → 모두 성공, 잔액 맞음
  - 실제 커밋이 일어나야 해서 `@Transactional` 없이 돌리고, 끝나면 `@DirtiesContext` 로 컨텍스트(인메모리 DB)를 버립니다.
- **실제 앱 확인 (24개 항목 통과)**
  - 기존 코드로 앱을 띄워 파일 DB(`build/livedb`)를 만든 뒤, 새 코드로 같은 DB 위에 다시 띄웠습니다(포트 18089).
  - 기존 DB 에 `price` 컬럼이 0 으로 추가되는 것을 확인했습니다.
  - 지갑이 없던 기존 회원이 동시에 첫 충전 50건을 해도 잔액 5,000 이 맞았습니다.
  - 동시 구매 20건은 201 1건, 나머지는 409 였습니다. 노출과 권한 규칙도 모두 확인했습니다.
- 띄운 java 프로세스 2개는 직접 정리했고, 임시 jar·DB·로그도 지웠습니다.
- 한 번 막힌 일: `taskkill` 이 아무것도 출력하지 않고 rc=0 을 돌려주는데 프로세스는 그대로였습니다. 다른 방법으로 우회하지 않고, 하네스의 백그라운드 작업 중지(TaskStop)로 직접 띄운 프로세스를 정리했습니다.

**확인하지 못한 것**
- H2 가 아닌 DB(MySQL 등)에서의 동작과 교착
- 수백 건 이상 동시 요청 같은 고부하
- 여러 서버를 띄운 환경
- 구매와 같은 순간에 글이 삭제되는 경쟁
- JWT 만 있고 DB 에 없는 회원의 충전 (코드상 404-2 로 처리되게 해 두었지만 테스트는 안 했습니다)

## 4. 남은 문제와 위험

- **잠금 대기 시간 초과**: 같은 지갑에 요청이 아주 많이 몰리면 H2 잠금 대기 10초를 넘을 수 있습니다. 그러면 그 요청은 오류로 실패하고 롤백됩니다. 돈은 맞지만, 기존 예외 처리기에 없는 예외라 500 이 나갈 수 있습니다.
- **오류 로그**: 동시 중복 구매나 동시 지갑 생성 때 Hibernate 가 유니크 위반을 ERROR/WARN 로그로 남깁니다. 정상적으로 처리된 것이지만 로그 모니터링에서는 오류로 보입니다.
- **수수료 관리**: 플랫폼 수수료는 기록만 하고, 별도 정산 계정은 없습니다.
- **입력 형식 허용**: `"amount": "100"` 처럼 문자열 숫자도 Jackson 기본 설정대로 받아들입니다.
- **응답 형식 변경**: PostDto 의 `content` 타입이 nullable 로 바뀌었고 필드가 3개 늘었습니다. 엄격하게 검사하는 클라이언트라면 영향이 있을 수 있습니다.
- **환불·잔액 상한 없음**: 환불·구매 취소·출금 기능이 없고, 누적 잔액에도 상한이 없습니다.
