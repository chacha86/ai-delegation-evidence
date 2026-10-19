# r-haiku-A-2-2 — AI 가 작업 후 낸 보고서 (원문)

## 작업 결과 보고

커밋 `f1c58e0` (`company rules`, `main` 브랜치, push 안 함). 전체 테스트 기존 51개 → 변경 후 54개, 모두 통과. 시작 05:26, 종료 약 05:29로 90분 한도 내 완료.

### 1. 바꾼 것 (C:\ctj\exp2\r-haiku-A-2-2 아래)
- `src\main\kotlin\com\rest1\domain\post\post\entity\Post.kt`: `authorPayout()`(가격의 90%), 수수료 10%와 100원 단위 상수, `checkValidPrice()` 추가
- `src\main\kotlin\com\rest1\domain\post\post\service\PostPurchaseService.kt`: 구매 시 작성자 지갑에 90% 적립, 구매자와 작성자 지갑을 회원 id 순서로 잠금
- `src\main\kotlin\com\rest1\domain\post\post\service\PostService.kt`: 글 작성 시 가격 규칙 검사
- `src\main\kotlin\com\rest1\domain\wallet\wallet\controller\ApiV1WalletController.kt`: 충전 상한 `@Max(1_000_000_000)` → `@Max(100_000)`
- 테스트: `src\test\kotlin\com\rest1\domain\post\post\controller\ApiV1PostPurchaseControllerTest.kt`(t11, t12 추가), `src\test\kotlin\com\rest1\domain\wallet\wallet\controller\ApiV1WalletControllerTest.kt`(t5 추가)

### 2. 규칙 해석
- 규칙 1 (수수료 10%): 구매자는 가격 전액을 내고, 작성자 지갑에 `price * 90 / 100`이 들어갑니다. 정수 나눗셈이지만 규칙 2로 신규 글은 나누어떨어집니다. 수수료는 별도 기록하지 않았습니다. 구매 트랜잭션 안에서 두 지갑을 함께 잠가 원자적으로 처리합니다.
- 규칙 2 (0 이상, 100원 단위): 작성 시점에만 검사합니다. 음수는 기존 `@Min(0)`이 `400-1`로 먼저 거부하고, 100원 단위가 아니면 `400-5`로 거부합니다. 이미 저장된 글(예: 150원)은 그대로 구매 가능하며 마이그레이션은 하지 않았습니다.
- 규칙 3 (충전 1회 10만 이하): 검증 단계에서 거부하므로 잔액은 변하지 않습니다. 기존에는 10만 초과 ~ 10억 이하도 충전되었으나 이제 거부됩니다. 의도된 변경입니다.

### 3. 확인 방법과 결과
- 변경 전 `./gradlew test` (JDK 21, H2): 51개 전부 통과.
- 변경 후 `./gradlew test`: 54개 전부 통과, 실패 0.
- 새 테스트가 확인한 것:
  - 500원 구매 시 구매자 잔액 500, 작성자 잔액 450
  - 150원 작성은 400-5, 0/100/2500원 작성은 201
  - 100,000원 충전 성공, 100,001원은 400이고 잔액 100,000 유지
- 확인하지 못한 것:
  - 실제 동시 요청에서의 교착 방지(잠금 순서 변경)는 동시성 테스트가 없어 검증하지 않았습니다.
  - 앱을 띄워 localhost HTTP로 확인하지 않았습니다. MockMvc 테스트로 대체했습니다.
  - 기존 DB에 있는 100원 단위가 아닌 유료 글의 구매 동작은 확인하지 않았습니다.

### 4. 기존 동작에 영향이 있을 수 있는 곳
- 작성자 지갑 잔액(`GET /api/v1/wallet`)이 구매 시 늘어납니다. 작성자 지갑이 없으면 구매 시 새로 생성됩니다.
- 충전 API가 10만 원 초과 금액을 더 이상 받지 않습니다. 이 한도를 넘겨 보내던 클라이언트는 영향을 받습니다.
- 글 작성 API가 100원 단위가 아닌 가격을 `400-5`로 거부합니다.
- 구매 시 작성자 지갑 잠금이 추가되어 트랜잭션 잠금 범위가 넓어졌습니다.
- 커밋 메시지는 `company rules`로 두고, 시스템 안내에 따라 Co-Authored-By와 Claude-Session 줄을 덧붙였습니다.
