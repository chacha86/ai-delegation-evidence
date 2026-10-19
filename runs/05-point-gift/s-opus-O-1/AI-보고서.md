# s-opus-O-1 — AI 가 작업 후 낸 보고서 (원문)

포인트 선물하기 기능을 만들고 `C:\ctj\exp3\s-opus-O-1` 에 커밋했습니다(`392298a point gift`, push 안 함). 전체 테스트 73개(새로 만든 36개 포함)가 모두 통과합니다. 다만 앱을 띄워 curl 로 직접 호출하는 확인은 보안 정책이 `curl` 을 막아서 하지 못했고, 지시대로 다른 방법으로 우회하지 않고 거기서 멈췄습니다. 그 명령은 실행 전에 막혀서 앱도 뜨지 않았습니다.

**기획서에서 결정이 필요한 부분: 수수료 구간이 거꾸로 됩니다**
기획서 문구대로 구현하면 100,001P ~ 999,999P 구간은 0.1% 를 적용해 수수료가 100P ~ 990P 가 됩니다. 10만P 이하의 1,000P 보다 적어서, 많이 보낼수록 수수료가 덜 붙는 구간이 생깁니다(10,001P 선물은 1,000P, 100,001P 선물은 100P). 의도가 "최소 1,000P" 였다면 수수료 계산 함수(`PointPolicy.calcFee`) 한 곳만 고치면 되니 기획 쪽 확인이 필요합니다.

**API 동작**
- **잔액 조회** `GET /api/v1/points`: `{"balance": n}` 을 최상위에 담아 돌려줍니다. 기존 글 조회 API 처럼 감싸지 않은 형태입니다.
- **충전** `POST /api/v1/points/charge`: 200 을 돌려주고, 충전 후 잔액은 `data.balance` 에 있습니다.
- **선물** `POST /api/v1/points/transfer`: 201 을 돌려주고, `data` 에 내역 번호, 받는 사람, 금액, 수수료, 내 잔액이 들어갑니다.
- **인증**: 기존 방식(apiKey / JWT) 그대로이고, 로그인하지 않으면 401 입니다.

**정책 반영**
- 수수료는 보내는 사람이 선물 금액과 별도로 냅니다. 예를 들어 10,000P 를 선물하려면 10,500P 가 있어야 하고, 받는 사람은 10,000P 를 받습니다.
- 수수료는 10P 단위로 절사합니다.
- 실패하면 거절하는 경우:
  - 1,000P 미만: 400
  - 자기 자신에게 선물: 400
  - 잔액 부족: 400
  - 1일 한도 1,000만P 초과: 400
  - 없는 회원: 404
- 소수 금액(1000.5), 0 이하 금액, 비어 있는 값도 400 으로 거절합니다.
- 받은 포인트는 바로 다시 선물할 수 있습니다.
- 동시에 여러 번 선물해도 잔액이 마이너스가 되거나 1일 한도를 넘지 않고, 서로에게 동시에 선물해도 멈추지 않습니다. 실제 동시 요청 테스트로 확인했습니다.

**기획서에 없어서 제가 정한 것**
- **1일 한도**: 선물 금액만 합산하고 수수료는 빼며, 서버 날짜 기준 자정에 초기화됩니다.
- **수수료 처리**: 선물 내역에 금액마다 기록만 하고, 운영 계정 잔액으로 옮기지는 않습니다. 모든 선물이 한 계정에 몰려 느려지는 것을 피하려는 것이고, 운영비는 내역의 수수료 합계로 집계할 수 있습니다.
- **충전**: 테스트용이지만 로그인한 누구나 쓸 수 있습니다. 1회 상한은 10억P 로 정했습니다.
- **잔액 보관**: 회원 정보가 아니라 별도 지갑에 둡니다. 처음 충전하거나 선물을 받을 때 생기고, 지갑이 없는 회원은 잔액 0 으로 보입니다.

**정리 상태**
- 이번에 띄운 프로세스는 남아 있지 않습니다. 지금 떠 있는 java 프로세스 두 개는 이 작업 전(14:12)부터 있던 Gradle/Kotlin 데몬이라 그대로 두었습니다.
- 프로젝트 폴더에 빌드 결과물(`build/`, `.gradle/`)이 생겼는데 둘 다 git 에서 제외되는 폴더라 커밋에는 들어가지 않았습니다.

주요 파일 (모두 `C:\ctj\exp3\s-opus-O-1\src\` 아래):
- `main\kotlin\com\rest1\domain\point\point\service\PointPolicy.kt` (정책 값과 수수료 계산)
- `main\kotlin\com\rest1\domain\point\point\service\PointService.kt`
- `main\kotlin\com\rest1\domain\point\point\controller\ApiV1PointController.kt`
- `main\kotlin\com\rest1\domain\point\point\entity\PointWallet.kt`, `PointTransfer.kt`
- `main\kotlin\com\rest1\domain\point\point\repository\PointWalletRepository.kt`, `PointTransferRepository.kt`
- `main\kotlin\com\rest1\domain\member\member\repository\MemberRepository.kt` (회원 잠금 조회 하나만 추가)
- `test\kotlin\com\rest1\domain\point\point\` 아래 테스트 4개 파일
