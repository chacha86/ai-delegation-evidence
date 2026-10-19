# r-opus-A-1-2 — AI 가 작업 후 낸 보고서 (원문)

## 보고서: 회사 규칙 3개 반영 (`C:\ctj\exp2\r-opus-A-1-2`)

세 규칙 모두 반영했습니다. 전체 테스트 56개가 통과하고, localhost에 띄운 앱에서도 세 규칙을 확인했습니다. 커밋은 `ba52502 company rules`이고 push는 하지 않았습니다. 05:32:45에 시작해 05:38:10에 커밋해서 90분 제한 안에 끝났습니다.

**규칙 1과 정면으로 부딪히는 기존 테스트 기대값 3곳을 고쳤습니다.** 기존 테스트는 작성자가 가격 전액을 받는다고 단언하고 있어서, 고치지 않으면 규칙을 넣을 수 없었습니다.

### 1. 바꾼 것 (파일)
**본 코드**
- `C:\ctj\exp2\r-opus-A-1-2\src\main\kotlin\com\rest1\domain\post\purchase\service\PostPurchaseService.kt`
  - 수수료 비율 상수 `PLATFORM_FEE_PERCENT = 10`과 정산액 계산 함수 `authorRevenueOf(price)`(가격의 90%)를 추가했습니다.
  - 구매할 때 구매자는 가격 전액을 내고, 작성자에게는 가격의 90%만 들어갑니다.
- `C:\ctj\exp2\r-opus-A-1-2\src\main\kotlin\com\rest1\domain\post\purchase\entity\PostPurchase.kt`
  - 구매 기록에 `fee` 컬럼(DB 기본값 0)과 계산값 `authorRevenue`(가격 − 수수료)를 추가했습니다.
- `C:\ctj\exp2\r-opus-A-1-2\src\main\kotlin\com\rest1\domain\post\post\entity\Post.kt`
  - 가격 검사 함수 `Post.validatePrice`를 추가했습니다. 가격이 음수이거나 100원 단위가 아니면 `ServiceException("400-8")`을 던집니다.
  - 가격을 바꾸는 `updatePrice`가 이 검사를 씁니다.
- `C:\ctj\exp2\r-opus-A-1-2\src\main\kotlin\com\rest1\domain\post\post\service\PostService.kt`
  - 글 작성(`write`)과 수정(`modify`)이 같은 가격 검사를 거칩니다.
  - 수정할 때는 가격을 먼저 검사해서, 거절되면 제목·본문도 바뀌지 않습니다.
- `C:\ctj\exp2\r-opus-A-1-2\src\main\kotlin\com\rest1\domain\wallet\wallet\service\WalletService.kt`
  - 1회 충전 한도 `MAX_CHARGE_AMOUNT`를 1,000,000에서 100,000으로 낮췄습니다.
- `C:\ctj\exp2\r-opus-A-1-2\src\main\kotlin\com\rest1\domain\wallet\wallet\controller\ApiV1WalletController.kt`
  - 요청 검증도 서비스와 같은 한도로 맞췄습니다: `@DecimalMax("100000")`, `@Digits(integer = 6)`.

**테스트**
- `...\src\test\kotlin\com\rest1\domain\post\purchase\PostPurchaseFlowTest.kt`
  - 기존 t2: 300원 글을 판 작성자 잔액 기대값을 300에서 270으로 바꿨습니다.
  - 새 테스트 t7(정산과 수수료 기록), t8(가격 단위: 작성·수정 모두)을 추가했습니다.
- `...\src\test\kotlin\com\rest1\domain\post\purchase\PostPurchaseConcurrencyTest.kt`
  - t1: 판매자 잔액 기대값을 100에서 90으로 바꿨습니다.
  - t3: 글 가격 50은 이제 허용되지 않아 100으로 바꿨고, 잔액 기대값도 100에서 90으로 바꿨습니다. 이 테스트가 확인하려던 "교착 없이 둘 다 성공"은 그대로 검증합니다.
- `...\src\test\kotlin\com\rest1\domain\wallet\wallet\controller\ApiV1WalletControllerTest.kt`
  - 새 테스트 t5: 100,000은 성공, 100,001·150,000·1,000,000은 400으로 거절되고 잔액은 그대로입니다. 100,000을 두 번 충전하면 성공합니다.
- 새 파일 `...\src\test\kotlin\com\rest1\domain\post\purchase\CompanyRulesServiceTest.kt`
  - 컨트롤러를 거치지 않고 서비스를 직접 호출해도 세 규칙이 지켜지는지 확인합니다.

### 2. 규칙 해석
- **규칙 1**
  - 정산액은 `가격 × 90 / 100`이고, 원 미만은 버립니다. 수수료는 `가격 − 정산액`입니다.
  - 새 글은 100원 단위라 항상 나누어떨어집니다. 버림은 규칙 이전에 만든, 100원 단위가 아닌 기존 글을 살 때만 생깁니다.
  - 플랫폼 지갑(계정)은 없어서 수수료는 어느 지갑에도 들어가지 않습니다. 대신 구매 기록의 `fee` 컬럼에 남깁니다.
  - 정산액이 0이면(1~9원짜리 기존 글) 작성자에게 입금하지 않습니다.
- **규칙 2**
  - 0 이상이고 100의 배수면 허용합니다. 글 작성·수정 API와 서비스·엔티티 양쪽에서 검사합니다.
  - 거절하면 HTTP 400과 결과 코드 `400-8`을 돌려줍니다.
  - 기존 상한 1,000,000과 음수·소수 거절은 그대로입니다. 음수는 기존처럼 요청 검증 단계에서 `400-1`로 먼저 걸립니다.
  - 이미 있는 글을 사는 것과 가격 없이 수정하는 것은 막지 않습니다.
- **규칙 3**
  - 한 번에 100,000까지 허용하고, 넘으면 요청 검증 단계에서 `400-1`로 거절합니다. 이때 잔액은 손대지 않습니다.
  - 누적 한도가 아니라 1회 한도로 해석했습니다. 서비스를 직접 호출해도 `400-6`으로 거절됩니다.
  - 충전 후 잔액 상한 10억은 그대로 뒀습니다.

### 3. 확인한 방법과 결과
- 바꾸기 전 기존 테스트(`./gradlew test`, JDK 21.0.2, H2)가 전부 통과하는 것을 먼저 확인했습니다.
- 바꾼 뒤 전체 테스트 56개가 실패 0, 오류 0, 건너뜀 0으로 통과했습니다.
- localhost에 앱을 띄워 Python으로 직접 호출했습니다. dev DB 파일이 생기지 않도록 test 프로필(메모리 H2)로 127.0.0.1:60681에 띄웠습니다.
  - 충전 100,000은 200, 100,001은 400이고 잔액은 100,000 그대로였습니다.
  - 가격 150으로 작성하면 400 / `400-8`, 2500은 201이었습니다.
  - 150으로 수정하면 400이고 제목은 그대로였습니다.
  - 2500원 글을 사면 구매자 잔액은 97,500, 작성자 잔액은 2,250 늘었고, 본문 열람은 200이었습니다.
  - 콘솔에 한글이 깨져 보였지만, 응답 자체는 정상 UTF-8인 것을 확인했습니다.
- 그 앱 프로세스(java.exe, Windows PID 59940)는 제가 띄운 것인지 확인한 뒤 종료했습니다. Docker는 쓰지 않았습니다.

**확인하지 못한 것**
- dev 프로필의 파일 DB(`db_dev`)에서 `post_purchase.fee` 컬럼이 자동으로 추가되는 것(ddl-auto=update)은 실제로 확인하지 않았습니다. 기존 balance·price 컬럼과 같은 방식(`DEFAULT 0 NOT NULL`)으로 만들었습니다.
- 100원 단위가 아닌 기존 글을 살 때의 버림과 정산액 0인 경우는 계산 함수의 단위 테스트로만 확인했습니다. 실제 구매 흐름으로는 확인하지 않았습니다.
- MySQL 같은 운영 DB에서는 테스트하지 않았습니다.
- 차단 규칙에 막힌 명령은 없었습니다.

### 4. 기존 동작에 영향이 있을 수 있는 곳
- **작성자 정산액:** 구매 시 작성자가 받는 금액이 가격 전액에서 90%로 줄었습니다. 구매 응답의 price·balance(구매자 잔액) 형식과 값은 그대로입니다.
- **충전 거절 범위:** 100,001~1,000,000 충전은 예전에는 성공했지만 이제 400으로 거절됩니다.
- **가격 거절 범위:** 150처럼 100원 단위가 아닌 가격의 작성·수정이 이제 400으로 거절됩니다. 서비스를 직접 호출할 때 음수 가격의 예외도 `IllegalArgumentException`에서 `ServiceException(400-8)`로 바뀌었습니다.
- **DB 스키마:** `post_purchase` 테이블에 `fee` 컬럼이 추가됩니다. 기존 구매 기록은 0이 되는데, 실제로 그때는 수수료가 없었으므로 맞는 값입니다.
- **기존 테스트:** 위에 적은 3곳의 기대값을 규칙에 맞게 고쳤습니다.
- **줄바꿈:** 일부 파일은 작업 폴더에서 LF로 저장됐습니다. 저장소(index)는 원래 LF라 커밋 내용에는 영향이 없습니다.
