# s-sonnet-O-1 — AI 가 작업 후 낸 보고서 (원문)

포인트 선물하기를 구현했고 `point gift` 메시지로 커밋했습니다(57b1db8). push 는 하지 않았습니다. 전체 `./gradlew test` 가 통과했고 새 테스트 4개도 포함됩니다. 서버는 따로 띄우지 않았습니다.

**API** (인증은 기존 `Authorization: Bearer <apiKey>` 방식 그대로)
- `POST /api/v1/points/charge`: 충전합니다. 1P 이상 1억P 이하만 받습니다. 1억P 상한은 제가 정했습니다.
- `GET /api/v1/points`: 응답 최상위에 `balance` 가 있습니다. 기획서 대로 다른 API 처럼 `RsData` 로 감싸지 않았습니다.
- `POST /api/v1/points/transfer`: 선물합니다. 성공하면 200 이고 `data` 에 `amount`, `fee`, `balance` 가 들어갑니다. 1일 한도는 `PointTransfer` 테이블에 선물 내역을 쌓아서 계산합니다.
- 실패 응답은 기존처럼 `RsData` 에 400/404 코드로 내려갑니다. 최소 금액 미만, 자기 자신에게 선물, 한도 초과, 잔액 부족은 400 이고 없는 아이디는 404 입니다.

**동시성**: 잔액을 바꿀 때 회원 행을 비관적 락으로 잠급니다. 두 회원은 id 순서로 잠가서 데드락을 막았습니다.

**기존 DB**: 기존 코드에서는 `Member` 에 `point` 컬럼(기본값 0)과 `MemberRepository` 에 락 조회를 추가했습니다. 컬럼에 기본값을 둬서 기존 `db_dev` 파일 DB 에도 `ddl-auto: update` 로 추가됩니다.

**기획서에서 제가 가정한 부분**
- 수수료는 `amount` 단위입니다. 수수료는 선물 금액 위에 얹고, 잔액에서는 금액과 수수료를 합쳐 차감합니다. 받는 사람은 금액 전액을 받습니다.
- 1일 한도는 수수료를 뺀 선물 금액의 합으로 셉니다. "하루"는 서버 로컬 날짜 기준입니다.
- 수수료 구간은 "이하"로 해석했습니다. 10,000P 는 500P, 100,000P 는 1,000P 입니다.

**기획서 확인이 필요한 점**: 10만P 초과 구간에 0.1% 를 문구 그대로 적용하면 수수료가 오히려 줄어듭니다. 100,000P 는 1,000P 인데 100,001P 는 100P 가 됩니다. 0.1% 가 1,000P 가 되는 건 100만P 부터입니다. 일단 문구 대로 구현했고 이 동작은 테스트에 박아 두었습니다. 의도가 "최소 1,000P 보장"이면 `PointService.calcFee` 한 줄만 고치면 됩니다.

**테스트**: `src/test/kotlin/com/rest1/domain/point/point/controller/ApiV1PointControllerTest.kt` 에서 수수료 경계값, 충전→선물→잔액 조회, 거절 케이스, 1일 한도를 확인합니다.

주요 코드는 `src/main/kotlin/com/rest1/domain/point/point/` 아래(controller, service, entity, repository)에 있습니다.
