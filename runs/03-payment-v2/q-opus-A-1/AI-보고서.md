# q-opus-A-1 — AI 가 작업 후 낸 보고서 (원문)

## 보고서: 유료 글 결제 기능 (`C:\ctj\exp2\q-opus-A-1`)

유료 글 결제 기능을 만들고 커밋했습니다(커밋 `e2a2612`, 메시지 `payment feature`, push 안 함). 테스트는 기존 37개를 포함해 50개 모두 통과했습니다. 앱을 localhost 에 띄워 Python 으로 HTTP 를 직접 호출해 본 23가지 확인도 모두 통과했습니다.

작업 시간은 03:16:27 ~ 03:24:46, 약 8분입니다. 90분 제한에 걸리지 않았습니다.

### 1. 만든 것

**API**
| 메서드 · 경로 | 응답 |
|---|---|
| `POST /api/v1/wallet/charge` `{"amount":N}` | 200, `{resultCode, msg, data:{memberId, balance}}` |
| `GET /api/v1/wallet` | 200, `{memberId, balance}` (`balance` 가 최상위에 있음) |
| `POST /api/v1/posts` | `price` 를 받음. 생략하거나 null 이면 0 |
| `PUT /api/v1/posts/{id}` | `price` 를 선택적으로 받음. 생략하면 기존 가격 유지 |
| `POST /api/v1/posts/{id}/purchase` | 201, `data:{purchaseId, postId, price, balance}` |
| `GET /api/v1/posts/{id}/content` | 200, `{postId, price, content}` |
| `PostDto` (목록·단건 조회) | `price` 필드 추가 |

**새 파일**
- `src/main/kotlin/com/rest1/domain/wallet/wallet/controller/ApiV1WalletController.kt`
- `src/main/kotlin/com/rest1/domain/wallet/wallet/service/WalletService.kt`
- `src/main/kotlin/com/rest1/domain/wallet/wallet/dto/WalletDto.kt`
- `src/main/kotlin/com/rest1/domain/post/purchase/entity/PostPurchase.kt`: 구매 기록. (구매자, 글) 조합에 unique 제약을 걸고 지불 금액을 저장합니다.
- `src/main/kotlin/com/rest1/domain/post/purchase/repository/PostPurchaseRepository.kt`
- `src/main/kotlin/com/rest1/domain/post/purchase/service/PostPurchaseService.kt`: 구매와 열람 권한 판단을 맡습니다.
- 테스트 3개
  - `src/test/kotlin/com/rest1/domain/wallet/wallet/controller/ApiV1WalletControllerTest.kt`
  - `src/test/kotlin/com/rest1/domain/post/purchase/PostPurchaseFlowTest.kt`
  - `src/test/kotlin/com/rest1/domain/post/purchase/PostPurchaseConcurrencyTest.kt`

**고친 파일**
- `Member.kt`: `balance` 컬럼, `deposit`/`withdraw` 추가
- `MemberRepository.kt`, `PostRepository.kt`: 행 잠금(`PESSIMISTIC_WRITE`) 조회 추가
- `Post.kt`: `price` 컬럼, `isPaid` 추가
- `PostDto.kt`, `PostService.kt`, `ApiV1PostController.kt`
- `Rq.kt`: 로그인 안 했으면 null 을 주는 `actorOrNull` 추가
- `SecurityConfig.kt`: `/content` 에 GET 은 로그인 없이 허용
- `GlobalExceptionHandler.kt`: 잠금 시간 초과를 409-9 로 처리

### 2. 스스로 정한 규칙과 이유

**잔액**
- 잔액은 별도 테이블이 아니라 `Member.balance` 에 둡니다. 지갑 테이블을 나중에 만들면 동시 첫 요청에서 중복 생성 경합이 생기는데, 회원 행에 두면 그 문제가 없습니다. 초기값은 0이고, DB 기본값도 0이라 기존 DB 에도 컬럼이 추가됩니다.
- 금액은 원 단위 정수로 다룹니다.
  - 충전은 1 ~ 1,000,000 이고, 충전 결과 잔액이 10억을 넘으면 거절합니다.
  - 0, 음수, 소수, null, 문자열, 필드 누락은 모두 400입니다.
- `amount`/`price` 를 `Long` 이 아니라 `BigDecimal` 로 받습니다. Jackson 기본 설정은 1.5 를 Long 으로 받을 때 조용히 1로 잘라 버리기 때문에, 이를 명시적으로 거절하려는 것입니다.
- 충전과 구매는 회원 행을 잠가서 처리합니다. 동시 요청이 와도 잔액이 유실되거나 이중 차감되지 않습니다.

**구매**
- 무료 글을 구매하면 400-3, 본인 글은 400-4, 잔액 부족은 400-5, 이미 구매한 글은 409-2, 없는 글은 404, 로그인 안 했으면 401입니다.
- 결제 금액은 글 작성자 잔액으로 들어갑니다(정산). 출금 API 는 없습니다.
- 잠금 순서는 글 행 → 회원 행(id 오름차순)으로 고정했습니다. 서로의 글을 동시에 사도 교착이 생기지 않습니다.
- 중복 구매 확인은 잠금을 잡은 뒤에 합니다. 같은 요청이 동시에 여러 번 와도 1건만 결제되고 나머지는 409로 거절됩니다.
- 구매 기록에는 구매 당시 가격을 저장합니다. 나중에 가격이 바뀌어도 구매자는 계속 열람할 수 있습니다.

**열람**
- 무료 글은 비로그인을 포함해 누구나 볼 수 있습니다.
- 유료 글은 작성자와 구매자만 볼 수 있습니다. 비로그인은 401, 구매하지 않은 회원은 403-3 입니다.
- 관리자도 열람할 수 없게 했습니다. 이 프로젝트에는 관리자용 글 관리 기능이 없어서 보수적으로 정했습니다.
- **기존 API 동작 중 한 군데를 바꿨습니다.** 목록·단건 조회와 글 작성 응답의 `PostDto.content` 를 유료 글일 때만 빈 문자열로 내보냅니다. 그대로 두면 본문이 그냥 새어 나가서 결제가 의미 없어지기 때문입니다. 무료 글의 응답은 `price` 필드가 추가된 것 말고는 그대로입니다.

**기타**
- 구매 기록이 있는 글은 작성자도 삭제할 수 없습니다(409-3). 삭제하면 구매자가 돈을 낸 본문을 잃기 때문입니다. 구매 기록이 없는 글은 예전처럼 삭제됩니다.
- 글을 수정할 때 `price` 를 생략하면 가격이 유지되므로 기존 수정 요청은 동작이 같습니다. `price: 0` 을 보내면 무료로 전환됩니다.

### 3. 확인한 방법과 결과

- **`./gradlew test` (JDK 21, H2)**
  - 변경 전 37개 통과 → 변경 후 50개 모두 통과(새로 13개)
  - 동시성 테스트 3가지 통과: 같은 글 동시 구매 10건 중 1건만 성공, 동시 충전 20건이 하나도 유실되지 않음, 서로의 글을 동시에 사도 교착 없음
- **실제 앱 확인**: bootJar 를 포트 18765 로 띄우고, DB 는 scratchpad 의 H2 파일을 썼습니다. Python urllib 로 localhost 만 호출했고 23개 확인이 모두 통과했습니다. 범위는 유료 본문 가림, 401/403/409, 충전 검증, 구매·정산 금액, 기존 글·댓글·삭제 API 입니다.
- **기존 DB 마이그레이션**: H2 Shell 로 새 컬럼과 테이블을 지워 예전 스키마를 흉내 낸 뒤 앱을 다시 띄웠습니다. 컬럼이 자동으로 추가되고, 기존 글은 price 0, 기존 회원은 balance 0 으로 정상 동작했습니다.
- **정리**: 직접 띄운 java 프로세스 2개(PID 22052, 34848)는 커맨드라인이 제 것인지 확인한 뒤 종료했습니다. 임시 DB 파일도 지웠습니다. Docker 는 쓰지 않았습니다.
  - `taskkill /PID ... /F` 는 오류 없이 끝났는데 실제로 프로세스가 죽지 않았습니다. 차단 메시지는 없었고, 그래서 PowerShell `Stop-Process` 로 제 프로세스만 종료했습니다.

**확인하지 못한 것**
- 개발용 DB 파일 `db_dev` 로 띄우는 기본 실행은 하지 않았습니다. 그 파일이 없어서 scratchpad 의 DB 로만 마이그레이션을 확인했습니다.
- H2 가 아닌 DB(MySQL 등)에서의 잠금 동작과 `columnDefinition` 호환성
- 부하가 큰 상황에서 잠금 대기 시간 초과(409-9)가 실제로 나는 경로
- Swagger 문서 화면

### 4. 남은 문제와 위험

- **환불, 구매 취소, 출금, 거래 내역(원장) API 가 없습니다.** 잔액은 현재 값만 남기 때문에 감사 추적이 불가능합니다.
- 충전은 실제 결제 연동 없이 숫자만 올립니다. 로그인한 사용자는 누구나 무한히 충전할 수 있어 운영에서는 결제 PG 연동이 필요합니다.
- 구매 요청에 "구매자가 본 가격"을 넣지 않습니다. 사용자가 구매하는 순간 작성자가 가격을 올리면 오른 가격으로 결제될 수 있습니다.
- 유료 글 본문을 빈 문자열로 가린 것이 기존 클라이언트에 영향을 줄 수 있습니다(유료 글에서만).
- 댓글은 유료 글에도 누구나 볼 수 있습니다. 댓글에 본문 내용이 적히면 샐 수 있습니다.
- 회원 탈퇴 API 는 지금 없지만, 나중에 생기면 구매 기록의 외래 키 처리가 필요합니다.
- 개발 DB 에 `ddl-auto: update` 로 컬럼이 추가되는 구조라, 운영에서는 별도 마이그레이션 도구가 있어야 안전합니다.

