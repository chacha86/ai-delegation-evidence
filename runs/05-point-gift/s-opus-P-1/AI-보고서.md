# s-opus-P-1 — AI 가 작업 후 낸 보고서 (원문)

# 포인트 선물하기 구현 보고서

포인트 충전·잔액 조회·선물 API를 만들었고 `point gift`(079211a)로 커밋했습니다. 기존 37개를 포함한 전체 57개 테스트가 통과합니다. 처리해야 할 일이 두 가지 있습니다.

- **제가 띄운 앱(Windows PID 51132, 포트 18089)이 아직 떠 있습니다.** `taskkill //PID 51132 //F`가 exit 0을 돌려줬지만 프로세스는 남아 있습니다. 메모리 사용량만 401MB에서 167MB로 줄었습니다. 차단 메시지는 없었지만 명령이 실제로 듣지 않는 상황이라, 다른 도구로 우회하지 않고 멈췄습니다. 직접 종료해 주세요. 다른 java 프로세스(33864, 36984)는 제가 띄운 것이 아니어서 건드리지 않았습니다.
- **90분 제한을 넘겼습니다.** 09:53:24에 시작했는데 마지막 확인 중 시각을 보니 14:09였습니다. 도구 호출 사이 대기가 길었던 것으로 보입니다. 그 시점에 수정을 멈추고 그대로 커밋했습니다. 기능과 테스트는 그전에 끝나 있었습니다.

## 1. 만든 것

**API** (인증은 기존 방식 그대로: `Authorization: Bearer {apiKey} [accessToken]` 또는 쿠키)

| API | 성공 응답 |
|---|---|
| `GET /api/v1/points` | 200, `{"memberId", "username", "balance"}` |
| `POST /api/v1/points/charge` `{"amount"}` | 200, `data` 안에 `amount`, `balance` |
| `POST /api/v1/points/transfer` `{"toUsername", "amount"}` | 200, `data` 안에 `toUsername`, `amount`, `fee`, `totalDeducted`, `balance`, `todayTransferredAmount`, `dailyLimit` |

**실패 코드**

| 코드 | 상황 |
|---|---|
| 400-1 | 필수값 누락 (기존 검증 방식) |
| 400-3 | 금액 오류 (최소 금액 미만, 소수, 0 이하) |
| 400-4 | 자기 자신에게 선물 |
| 400-5 | 잔액 부족 |
| 400-6 | 1일 한도 초과 |
| 404-2 | 받는 회원 없음 |
| 409-1 | 동시 요청이 몰려 행 잠금 실패 |
| 401 | 로그인 안 함 (기존 동작) |

**파일** (`C:\ctj\exp3\s-opus-P-1\src\...` 아래)
- `main\kotlin\com\rest1\domain\point\point\entity\PointWallet.kt`: 회원별 잔액
- `main\kotlin\com\rest1\domain\point\point\entity\PointHistory.kt`: 충전·선물 이력. 수수료 기록도 여기에 남습니다.
- `main\kotlin\com\rest1\domain\point\point\repository\PointWalletRepository.kt`, `PointHistoryRepository.kt`
- `main\kotlin\com\rest1\domain\point\point\service\PointFeePolicy.kt`: 수수료 계산
- `main\kotlin\com\rest1\domain\point\point\service\PointService.kt`: 충전·선물 처리와 잠금
- `main\kotlin\com\rest1\domain\point\point\controller\ApiV1PointController.kt`
- `main\kotlin\com\rest1\domain\point\point\config\PointConfig.kt`: 날짜 계산용 Clock (Asia/Seoul)
- `main\kotlin\com\rest1\domain\member\member\repository\MemberRepository.kt`: 행 잠금 메서드 `findByIdForUpdate`만 추가하고 기존 메서드는 그대로 둠
- 테스트: `test\kotlin\com\rest1\domain\point\point\` 아래 `controller\ApiV1PointControllerTest.kt`(15개), `service\PointFeePolicyTest.kt`(3개), `service\PointServiceConcurrencyTest.kt`(3개)

## 2. 기획서 해석 (애매한 곳과 제가 정한 것)

- **수수료 구간이 이상합니다. 기획 확인이 필요합니다.** 문구대로 구현하면 10만P는 수수료 1,000P인데 100,001P는 0.1%라서 100P입니다. 100,001P부터 999,999P까지는 금액이 커질수록 수수료가 오히려 줄어듭니다. 사용자에게 기획서보다 많이 받지 않는 쪽을 택해 **문구 그대로** 구현했습니다. 수정은 `PointFeePolicy` 한 곳만 고치면 됩니다.
- "1만P까지", "10만P까지"는 해당 금액을 포함하는 것으로 봤습니다. 10,000P는 500P, 100,000P는 1,000P입니다.
- 10P 미만 절사는 0.1% 구간에만 의미가 있습니다. 예: 1,234,567P는 1,230P. 최대 5,000P는 500만P 이상에서 적용됩니다.
- 잔액은 "선물 금액 + 수수료" 이상이어야 선물할 수 있습니다. 받는 사람은 선물 금액만 받습니다.
- **1일 한도:**
  - 보내는 사람 기준으로 선물 금액만 합산하고 수수료는 넣지 않았습니다.
  - 정확히 1,000만P까지는 허용합니다.
  - 날짜 경계는 한국시간 자정입니다.
- 걷은 수수료는 어느 계정에도 넣지 않고 이력의 `fee`에만 기록합니다. 운영 계정으로 옮기는 기능은 없습니다.
- 금액은 정수만 받습니다. 그냥 숫자로 받으면 1000.5가 조용히 1000으로 잘리기 때문에, 소수를 받아서 직접 거부하도록 했습니다.
- 충전은 1회 10억P를 상한으로 뒀습니다. 기획서에 없는 값입니다.
- `GET /api/v1/points`는 기존 글 조회 API처럼 결과를 감싸지 않고 바로 반환합니다. 그래서 `balance`가 응답 최상위에 있습니다. 충전·선물은 기존 쓰기 API처럼 `resultCode`/`msg`/`data` 형식을 씁니다.
- 잔액은 회원 테이블에 컬럼을 넣지 않고 별도 테이블에 둡니다. 기존 회원 데이터와 응답에 영향을 주지 않기 위해서입니다.
- 동시 요청 처리: 관련 회원 행을 id 순서대로 잠근 뒤 한도와 잔액을 확인합니다. 순서를 고정해서 서로에게 동시에 보내도 교착이 생기지 않게 했습니다.

## 3. 확인한 방법과 결과

- **`./gradlew test` (JDK 21, H2):** 57개 전부 통과. 기존 37개도 변경 전후 모두 통과합니다.
- **동시성 테스트:** 결과가 예상과 정확히 일치합니다.

  | 시나리오 | 결과 |
  |---|---|
  | 15,000P로 1,000P 선물 30건 동시 요청 | 10건 성공, 20건 잔액 부족 |
  | 200만P 선물 10건 동시 요청 | 5건 성공, 5건 한도 초과 |
  | 두 사람이 서로에게 30건 동시 선물 | 포인트 총량 보존 확인 |

- **잠금을 빼고 돌려 본 결과:** 잠금을 일시 제거하고 같은 테스트를 돌리면 실패합니다. 21건이 성공해 잔액 이상으로 빠져나갔습니다. 테스트가 실제로 동시성 문제를 잡는다는 뜻이고, 코드는 원래대로 되돌렸습니다.
- **실제 앱 확인:** 빌드한 앱을 localhost:18089에 띄우고 Python으로 호출했습니다. DB는 임시 폴더를 써서 저장소에는 파일이 생기지 않았습니다.
  - 로그인 토큰 인증, 충전, 선물(수수료 500P), 받은 포인트로 바로 재선물 모두 정상입니다.
  - 자기 자신, 최소 금액 미만, 없는 회원, 잔액 부족, 미인증은 각각 기대한 코드로 거부됩니다.
  - 기존 글 목록과 내 정보 조회도 정상입니다.
  - 한글은 콘솔에서만 깨져 보였고, 서버는 UTF-8로 정상 응답합니다.

**확인하지 못한 것**
- 재시작 후 잔액 유지: 첫 앱이 종료되지 않아 두 번째 기동이 DB 파일 잠금으로 실패했습니다.
- 자정을 넘길 때 한도가 초기화되는지는 실제로 돌려 보지 않았습니다.
- MySQL 등 실제 운영 DB에서의 동작.
- 요청이 아주 몰릴 때 409가 실제로 나오는지.

## 4. 남은 문제와 위험

- **충전 API는 누구나 무제한으로 포인트를 만들 수 있습니다.** 운영에 나가기 전에 끄거나 막아야 합니다.
- 수수료 역전 구간은 기획 확인이 필요합니다(2번 첫 항목).
- MySQL의 기본 격리 수준에서는 오늘 보낸 금액 합계가 오래된 값으로 읽힐 수 있습니다. 운영 DB를 바꾸면 다시 검증해야 합니다. H2에서는 문제가 없었습니다.
- 선물 내역 조회, 취소, 수수료 집계 API는 없습니다.
- 이전 코드에서 원래 있던 문제: 로그인 안 함(401) 응답의 한글 메시지가 `???`로 깨집니다. 제 변경과는 무관합니다.
