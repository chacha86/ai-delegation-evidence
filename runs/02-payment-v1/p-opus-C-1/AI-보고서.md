# p-opus-C-1 — AI 가 작업 후 낸 보고서 (원문)

## 보고서: 유료 글 결제 기능 (`C:\ctj\exp2\p-opus-C-1`)

`./gradlew test` 47개(기존 37개 + PaymentPolicyTest 10개)가 모두 통과합니다. 커밋 `b32f24c payment feature` 를 만들었고 push 는 하지 않았습니다. 작업 시간은 02:18:49 부터 02:31:47 까지 약 13분으로, 90분 제한 안에 끝났습니다.

### 1. 만든 것

**API** (인증은 기존 Bearer apiKey/JWT 방식 그대로)
| API | 성공 응답 | 실패 응답 |
|---|---|---|
| `GET /api/v1/wallet` | 200 `{memberId, balance}` | |
| `POST /api/v1/wallet/charge` `{"amount":n}` | 200 RsData, `data.balance` 포함 | 400 |
| `POST /api/v1/posts` | 기존 응답 + `price` 를 받음 (생략하면 0) | 가격이 음수·소수·10억 초과면 400 |
| `POST /api/v1/posts/{id}/purchase` | 201 `{postId, price, balance}` | 없는 글 404 / 무료 글 400 / 자기 글 403 / 중복 구매 409 / 잔액 부족 400 |
| `GET /api/v1/posts/{id}/content` | 200 `{id, price, content}` | 비로그인 401 / 권한 없음 403 |

**새 파일**
- `C:\ctj\exp2\p-opus-C-1\src\main\kotlin\com\rest1\domain\wallet\` 아래 `entity\Wallet.kt`, `repository\WalletRepository.kt`, `service\WalletService.kt`, `controller\ApiV1WalletController.kt`, `dto\WalletDto.kt`
- `C:\ctj\exp2\p-opus-C-1\src\main\kotlin\com\rest1\domain\post\purchase\` 아래 `entity\PostPurchase.kt` ((글, 구매자) 유니크 제약), `repository\PostPurchaseRepository.kt`, `service\PostPurchaseService.kt`

**수정한 파일**
- `Post.kt`: `price` 컬럼 추가 (`@ColumnDefault("0")`).
- `PostDto.kt`: `price`, `paid`, `contentAccessible` 필드 추가. 열람 권한이 없으면 `content` 가 null 입니다.
- `PostService.kt`, `ApiV1PostController.kt`, `PostRepository.kt` / `MemberRepository.kt`: 행 잠금(FOR UPDATE) 조회 추가.
- `Rq.kt`: `actorOrNull` 추가.
- `src\main\resources\application-test.yml`: 테스트 DB 설정 변경 (이유는 2-6).

### 2. 정한 규칙과 이유

1. **동시성**
   - 지갑을 바꾸는 모든 작업은 먼저 해당 회원(Member) 행을 `SELECT … FOR UPDATE` 로 잠급니다. 그래서 같은 회원의 충전·구매·지갑 생성이 한 번에 하나씩만 진행됩니다.
   - 두 회원을 잠글 때는 항상 id 오름차순으로 잠가 교착상태를 막습니다.
   - 잔액 변경은 조건부 UPDATE(`balance >= 금액`, 상한 이하)로만 합니다. 잠금이 빠지더라도 잔액이 음수가 될 수 없습니다.
   - 새 테이블에는 `balance >= 0` 체크 제약도 겁니다.
2. **지갑**
   - 처음 충전하거나 구매에 관여할 때 지갑 행을 만듭니다. 그 전에 조회하면 잔액 0으로 보입니다.
   - 1회 충전은 1 이상 10억 이하, 잔액 상한은 10^15 입니다(Long 오버플로 방지). 상한은 제가 정한 값입니다.
   - 금액은 BigDecimal 로 받습니다. Long 으로 받으면 Jackson 이 1.5 를 1로 잘라 버리기 때문에, 이렇게 받아서 소수를 거부합니다.
3. **구매**
   - 무료 글은 구매할 수 없습니다(400).
   - 구매 1건이 한 트랜잭션입니다. 구매자 잔액 차감, 작성자 잔액 증가, 구매 기록 저장을 같이 하고, 어느 단계에서든 실패하면 전부 롤백됩니다.
   - 중복 구매는 잠금 뒤에 확인하고 409 를 돌려줍니다. DB 유니크 제약이 한 번 더 막습니다.
4. **열람**
   - 유료 글 본문은 작성자와 구매자만 볼 수 있습니다. 관리자도 예외가 아닙니다(정책 5 그대로).
   - 목록·단건 조회에서 권한이 없으면 `content: null` 입니다.
   - 무료 글은 기존 목록·단건 API 가 비로그인 사용자에게도 본문을 보여 주던 동작을 그대로 두었습니다. 기존 테스트가 이 동작을 확인합니다. `/content` 는 로그인이 필요합니다.
5. **가격 변경과 삭제**
   - 가격은 작성 후 바꿀 수 없습니다. PUT 요청은 price 를 무시합니다.
   - 구매자가 있는 유료 글은 삭제를 막습니다(409). 환불 기능이 없어서, 산 사람이 본문을 잃지 않게 하려는 것입니다.
   - 삭제와 구매는 둘 다 글 행을 먼저 잠그므로 서로 엇갈리지 않습니다.
6. **테스트 DB 분리 (기존 설정 변경)**
   - 원래 `application-test.yml` 은 모든 Spring 컨텍스트가 같은 인메모리 DB(`db_dev`)를 공유했습니다.
   - PaymentPolicyTest(RANDOM_PORT, 별도 컨텍스트)가 먼저 돌면, 그 테스트가 커밋한 회원·글 때문에 기존 테스트 4개가 실패했습니다(개수·id 기대값). 실제로 재현했습니다.
   - 그래서 URL 을 `jdbc:h2:mem:db_test_${random.uuid};MODE=MySQL` 로 바꿔 컨텍스트마다 DB 를 분리했습니다. 테스트 코드는 고치지 않았습니다.

### 3. 확인한 방법과 결과
- **전체 테스트**: 기존 37개와 정책 10개가 모두 통과했습니다. 클래스 실행 순서를 바꿔 두 번 확인했습니다.
  - PaymentPolicyTest 가 마지막에 도는 순서
  - 맨 앞에 도는 순서: 일부러 한 번 실패시켜 Gradle 이 실패한 클래스를 먼저 돌리게 한 뒤 원래대로 되돌림
- **반복 실행**: PaymentPolicyTest 를 `--rerun` 으로 4번 더 돌렸고 모두 통과했습니다.
- **localhost 앱 + Python 검사 45개 전부 통과**. 확인한 항목:
  - 잘못된 충전 요청 거부: 0, 음수, 1.5, null, 문자열, 상한 초과, amount 생략
  - 가격 검증
  - 비로그인 / 비구매자 / 작성자 / 구매자 각각의 목록·단건·`/content` 노출 여부
  - 무료 글 구매 실패, 없는 글 404, 수정해도 가격 유지, 구매자 있는 글 삭제 409
  - 동시성:
    - 잔액 350 으로 100원짜리 글 10개를 동시에 구매하면 정확히 3건 성공하고 잔액 50
    - 같은 글 8번 동시 구매에도 한 번만 차감
    - 두 회원이 서로의 글을 동시에 사고 동시에 충전해도 교착이나 5xx 없이 금액 보존
    - 지갑이 없는 상태에서 동시 첫 충전 20건이 모두 반영
    - 구매와 삭제를 동시에 보내도 결과가 일관됨
  - 앱 로그에 ERROR 나 예외가 없었습니다.
- **기존 dev DB 마이그레이션**: 원래 코드로 파일 DB(글 3개)를 만들고 새 코드로 다시 띄웠습니다. 기존 글이 `price=0` 으로 읽혔고, 충전·구매·열람이 정상 동작했습니다.
- **확인하지 못한 것**
  - H2 가 아닌 실제 운영 DB(MySQL 등)에서의 잠금 동작과 교착
  - 기존 테이블에 `balance >= 0` 체크 제약이 붙는지: `ddl-auto=update` 는 기존 테이블에 체크 제약을 추가하지 않습니다.
  - 동시 요청이 아주 많을 때의 성능과 H2 잠금 대기 시간 초과
  - JWT accessToken 만으로 하는 인증 경로는 따로 시험하지 않았습니다. 테스트는 apiKey 로만 했습니다.

### 4. 남은 문제나 위험
- **잠금 범위**: 회원 행을 잠그는 방식이라 같은 회원의 지갑 작업은 직렬로 처리됩니다. 인기 작성자의 글에 구매가 몰리면 그 작성자 행에서 대기가 생깁니다. 같은 글의 구매도 글 행 잠금 때문에 직렬입니다.
- **삭제 제한**: 구매자가 있는 유료 글은 작성자도 지울 수 없습니다. 환불이나 숨김 같은 대안은 아직 없습니다.
- **본문 외 노출**: 댓글은 그대로 공개라, 댓글에 본문을 인용하면 그 내용은 보입니다. 정책 범위 밖으로 판단했습니다.
- **입력 처리**: 숫자 대신 `"100"` 같은 문자열 금액도 Jackson 이 변환해 받아들입니다.
- **잠금 대기 설정**: H2 잠금 대기 시간은 기본값 그대로입니다.
- **작업 중 실수와 정리**:
  - 마이그레이션 확인 때 H2 URL 의 `/c/Users/...` 경로가 `C:\c\Users\...\scratchpad\migdb` 로 해석되어, 이 폴더 밖에 DB 파일이 생겼습니다. 제가 만든 `migdb` 폴더만 지웠고, 원래 있던 `C:\c` 아래 다른 폴더(`new`, `orig` 등)는 건드리지 않았습니다.
  - 제가 띄운 java 프로세스 3개(포트 18089, 18090)는 모두 종료를 확인했습니다. `taskkill` 은 성공이라고 응답했지만 실제로는 종료되지 않아서, 백그라운드 작업 중지 기능으로 멈췄습니다.
- **임시 파일**: 검사 스크립트는 `<임시폴더>\check.py` 입니다. 커밋 시 나온 LF/CRLF 경고는 git 줄바꿈 변환 안내일 뿐 문제는 아닙니다.
