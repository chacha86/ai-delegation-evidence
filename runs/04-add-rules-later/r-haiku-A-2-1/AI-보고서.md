# r-haiku-A-2-1 — AI 가 작업 후 낸 보고서 (원문)

## 작업 결과 (C:\ctj\exp2\r-haiku-A-2-1)

시작 05:03, 종료 약 05:09 (90분 제한 이내). 커밋 `7a9b45d company rules` (push 안 함). 차단된 명령은 없었고, curl/Docker/외부 호출은 사용하지 않았다.

### 1. 바꾼 파일
- `src/main/kotlin/com/rest1/domain/post/post/entity/Post.kt`: `authorPayout`(가격의 90%) 추가, companion에 `checkPrice()` 추가 (0 이상, 100원 단위, 위반 시 `400-5`).
- `src/main/kotlin/com/rest1/domain/post/post/service/PostService.kt`: `write()`에서 `Post.checkPrice(price)` 호출.
- `src/main/kotlin/com/rest1/domain/post/post/service/PostPurchaseService.kt`: 구매 시 구매자 지갑에서 가격만큼 차감하고, 작성자 지갑에 `authorPayout`을 적립. 두 지갑은 회원 id 오름차순으로 잠가 A->B, B->A 동시 구매의 교착을 막음.
- `src/main/kotlin/com/rest1/domain/wallet/wallet/entity/Wallet.kt`: `MAX_CHARGE_AMOUNT = 100_000`. `charge()`에서 초과 시 `400-6` 예외를 던지고 잔액은 변경하지 않음.
- `src/main/kotlin/com/rest1/domain/wallet/wallet/controller/ApiV1WalletController.kt`: 충전 요청 DTO의 `@Max`를 1,000,000,000에서 100,000으로 변경 (초과 시 `400-1`).
- 테스트: `ApiV1PostPurchaseControllerTest.kt`에 t11(작성자 90% 적립), t12(100원 단위 검증) 추가. `ApiV1WalletControllerTest.kt`에 t5(충전 상한 경계값, 100,000 허용 / 100,001 거부 후 잔액 불변) 추가.

### 2. 규칙 해석
- 규칙 1: 수수료 10%는 `price * 90 / 100`으로 계산한다. 100원 단위 규칙이 적용된 새 글은 항상 정확히 나누어떨어진다. 수수료는 별도 저장하지 않았고, `PostPurchase` 레코드 구조도 바꾸지 않았다. 작성자 지갑이 아직 없으면 `lockForUpdate`가 새로 만든다.
- 규칙 2: 0 이상 검사는 기존 `@Min(0)`(음수는 `400-1`)이 그대로 담당한다. 100원 단위 검사는 서비스 계층 `Post.checkPrice`가 담당하며 `400-5`를 반환한다. 무료 글(0)은 허용한다.
- 규칙 3: 요청 DTO 검사(`400-1`)와 도메인 검사(`400-6`) 두 곳에서 막는다. 거부 시 잔액은 바뀌지 않는다.

### 3. 확인 방법과 결과
- 기준선: 수정 전 `./gradlew test --offline` 전체 통과 (51 tests, 실패 0).
- 수정 후: `./gradlew test --offline` 전체 통과. 총 54 tests (기존 51 + 신규 3), 실패 0, 에러 0.
- 기존 테스트는 수정하지 않았다. 기존 구매 테스트(잔액 차감, 중복 구매 409, 본인/무료 글 400 등)와 글 작성 테스트가 그대로 통과했다.

**확인하지 못한 것**
- 실제 앱을 localhost에 띄워 HTTP로 확인하지 않았다. 검증은 MockMvc 테스트(H2)로만 했다.
- 동시 구매에서 교착이 없는지, 동시성 부하 테스트는 하지 않았다. 잠금 순서는 코드로만 보장했다.
- 기존 DB에 100원 단위가 아닌 유료 글이 있으면 마이그레이션이나 확인을 하지 않았다. 해당 글의 작성자 적립액은 내림 계산된다 (예: 150원 -> 135원).
- 실제 MySQL 등 H2 이외 DB에서의 동작은 확인하지 않았다.

### 4. 기존 동작에 영향이 있을 수 있는 곳
- 충전 API: 100,000원 초과 금액을 더 이상 받지 않는다 (기존에는 10억까지 허용). 규칙에 따른 의도된 변경이다.
- 글 작성 API: 100원 단위가 아닌 `price`(예: 150)는 `400-5`로 거부된다. 기존 클라이언트가 이런 값을 보내면 실패한다.
- 구매 API: 구매자 잔액 응답은 그대로다. 대신 작성자의 `GET /api/v1/wallet` 잔액이 구매 발생 시 가격의 90%만큼 늘어난다.
- 작성자 지갑이 없던 회원이 처음 구매를 받으면 지갑 행이 생성된다.
- `Wallet.charge()`에 상한 검사가 들어가서 이 메서드를 호출하는 모든 경로에 적용된다. 현재 호출처는 `WalletService.charge` 하나뿐이다.
