# s-haiku-P-1 — AI 가 작업 후 낸 보고서 (원문)

## 포인트 선물하기 구현 보고서

커밋: `8376cb6` (브랜치 main, 메시지 `point gift`). push 하지 않았다. 시작 06:05, 종료 06:14.

### 1. 만든 것

**API** (인증은 기존 방식 그대로, `/api/*/**` 는 인증 필요)
- `GET /api/v1/points` → 200, `{"resultCode":"200-1","msg":"OK","data":{"balance":N}}`
- `POST /api/v1/points/charge` 본문 `{"amount":N}` → 200, `data.balance` 반환
- `POST /api/v1/points/transfer` 본문 `{"toUsername":"아이디","amount":N}` → 201, `data` 에 `id, toUsername, amount, fee, balance`(보낸 뒤 내 잔액)

**새 파일** (`C:\ctj\exp3\s-haiku-P-1\src\main\kotlin\com\rest1\domain\point\point\` 아래)
- `controller\ApiV1PointController.kt`: 3개 엔드포인트
- `service\PointService.kt`: 충전, 선물(잠금, 한도, 잔액 검사)
- `service\PointPolicy.kt`: 최소 금액 1,000P, 일 한도 1,000만P, 수수료 계산
- `entity\PointTransfer.kt`: 선물 1건 기록 (보낸 사람, 받는 사람, 금액, 수수료)
- `repository\PointTransferRepository.kt`

**수정한 기존 파일**
- `C:\ctj\exp3\s-haiku-P-1\src\main\kotlin\com\rest1\domain\member\member\entity\Member.kt`: `point: Long` 컬럼 추가 (`bigint not null default 0`)
- `C:\ctj\exp3\s-haiku-P-1\src\main\kotlin\com\rest1\domain\member\member\repository\MemberRepository.kt`: `findByIdForUpdate` (PESSIMISTIC_WRITE) 추가

**테스트 (신규)**
- `C:\ctj\exp3\s-haiku-P-1\src\test\kotlin\com\rest1\domain\point\point\service\PointPolicyTest.kt`
- `C:\ctj\exp3\s-haiku-P-1\src\test\kotlin\com\rest1\domain\point\point\controller\ApiV1PointControllerTest.kt`

**에러 코드 (스스로 정함)**: 400-11 최소 금액 미만, 400-12 자기 자신, 400-13 잔액 부족(금액+수수료 기준), 400-14 일 한도 초과, 400-15 충전 후 잔액 오버플로, 404-2 받는 아이디 없음. 필드 누락은 기존처럼 400-1.

### 2. 기획서 해석

- **잔액 위치**: 기획서는 "응답 JSON 에 balance 필드"라고만 했다. 기존 `RsData` 규칙에 맞춰 `data.balance` 에 넣었다. 최상위 `balance` 를 기대하면 수정이 필요하다.
- **수수료 구간 경계**: 1만P 이하 500P, 10만P 이하 1,000P, 10만P 초과는 0.1%(최대 5,000P)로 문자 그대로 구현했다. 그 결과 **100,000P 는 수수료 1,000P, 100,001P 는 수수료 100P** 로 경계에서 수수료가 떨어진다. 의도인지 확인이 필요하다.
- **10P 절사**: 계산된 수수료 값을 10 단위로 내림했다. 구간별 고정액(500, 1,000)은 이미 10의 배수라 영향이 없다.
- **최소 금액과 한도의 기준**: 둘 다 수수료를 뺀 "선물 금액"으로 봤다. 일 한도는 그날 보낸 금액의 합계이며, 이번 선물을 더한 값이 1,000만P 를 넘으면 거절한다. 정확히 1,000만P 까지는 허용한다.
- **하루의 기준**: JVM 기본 시간대의 자정 기준(`LocalDate.now()`)이다. 서버 시간대가 한국이 아니면 달라진다.
- **잔액 조건**: 보내는 사람의 잔액이 `금액 + 수수료` 이상이어야 한다.
- **수수료 처리**: 받는 사람에게는 금액만 들어가고, 수수료는 어디에도 적립하지 않고 `point_transfer.fee` 에만 기록한다(운영비 계정 적립은 하지 않았다). 운영비를 어떻게 모을지는 정책 결정이 필요하다.
- **동시성**: 선물 시 두 회원 행을 id 오름차순으로 잠근 뒤 한도와 잔액을 검사한다. 교착을 피하고 같은 사람의 동시 선물이 한도나 잔액을 넘지 못하게 하려는 것이다.
- **충전**: 금액 상한은 두지 않았고 1 이상만 받는다. 잔액이 `Long` 범위를 넘으면 400-15 로 거절한다.
- **인증 방식**: 테스트와 smoke 는 `Authorization: Bearer <apiKey>` 로 했다. 같은 Actor 경로를 쓰는 JWT·쿠키 인증은 따로 검증하지 않았다.

### 3. 확인한 방법과 결과

- **`./gradlew test`**: 변경 전 기준선 37개 통과. 변경 후 51개 전부 통과, 실패 0 (신규 14개: 수수료 단위 2개, API 12개).
  - API 테스트 항목: 잔액 0 시작, 비로그인 401, 충전, 충전 금액 누락, 정상 선물(수수료 500, 잔액 39,500/10,000), 받은 포인트 즉시 사용, 최소 금액 미만, 자기 자신, 없는 아이디, 잔액 부족, 일 한도 초과, 금액 누락.
- **localhost 실행 확인**: `bootJar` 로 만든 jar 를 localhost 18082 에서 띄우고 Python urllib 으로 호출했다. 정상 흐름과 에러 코드(400-12, 400-11, 404-2, 400-14, 400-1)가 기획대로 나왔다. 확인 후 내가 띄운 프로세스는 종료했다.
- **기존 DB 마이그레이션**: 원래 커밋(HEAD)을 scratchpad 에 따로 빌드해 파일 DB 를 만든 뒤, 새 jar 로 같은 DB 를 열었다. 로그에서 `alter table if exists member add column point bigint not null default 0` 가 실행되어 기동에 성공했다.

**확인하지 못한 것**
- 같은 회원의 선물 여러 개를 실제로 동시에 보내는 부하 테스트 (잠금 로직은 코드 검토만 했다).
- JWT 액세스 토큰, 쿠키 인증 경로에서의 동작.
- 서버 시간대가 한국과 다를 때 일 한도 경계 (시간대를 바꿔 본 적 없다).
- Swagger UI 노출 확인.
- dev 프로필(`db_dev` 파일 DB)으로 띄워 본 것 (임시 파일 DB 와 메모리 DB 로 확인했다).

### 4. 남은 문제와 위험

- **정리 필요 (내가 못 한 작업)**: smoke 테스트 때 H2 JDBC URL 에 Windows 가 아닌 경로 형식을 써서 DB 파일이 `C:\c\<임시폴더 경로>` 아래에 생겼다. 삭제 명령이 안전 검사에 막혀 실행하지 못했다. **`C:\c` 폴더는 직접 지워 주면 된다.** 이 폴더에는 내 테스트 DB 파일만 있다. 저장소 안에는 아무것도 남지 않았다.
- 수수료 경계 불연속(100,000P → 100,001P 에서 1,000P → 100P)은 기획 확인이 필요하다.
- 수수료를 적립하지 않아 포인트가 시스템에서 사라진다. 운영비 계정 적립 여부를 정해야 한다.
- 일 한도의 "하루" 기준 시간대를 확정해야 한다.
- 이번 커밋에는 Git 줄바꿈 경고(LF→CRLF)가 나왔다. `.gitattributes` 설정에 따른 것으로, 기능에는 영향이 없다.
