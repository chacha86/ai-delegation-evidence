# r-haiku-A-1-2 — AI 가 작업 후 낸 보고서 (원문)

## 보고서: 회사 규칙 3개 반영 (커밋 2aecf5b `company rules`)

시작 05:19:04, 종료 약 05:26. 90분 제한 안에 완료했다. 차단된 명령은 없었다.

### 1. 바꾼 것 (파일)
- `C:\ctj\exp2\r-haiku-A-1-2\src\main\kotlin\com\rest1\domain\post\post\service\PostService.kt`
  - `write()`: 가격 0 미만이면 400-5, 100원 단위가 아니면 400-6 으로 거부.
  - `purchase()`: 작성자 입금액을 `authorPayout(price) = price / 100 * 90` 으로 계산. 구매자 차감은 기존대로 전액.
  - `companion object`에 `PRICE_UNIT = 100`, `PLATFORM_FEE_PERCENT = 10` 상수 추가.
- `C:\ctj\exp2\r-haiku-A-1-2\src\main\kotlin\com\rest1\domain\wallet\wallet\service\WalletService.kt`
  - `charge()`: 금액이 100,000 초과면 지갑을 잠그거나 만들기 전에 400-5 로 거부. 잔액은 그대로 둔다.
  - `MAX_CHARGE_AMOUNT = 100_000L` 상수.
- `C:\ctj\exp2\r-haiku-A-1-2\src\main\kotlin\com\rest1\domain\wallet\wallet\controller\ApiV1WalletController.kt`
  - `ChargeReqBody.amount` 에 `@field:Max(WalletService.MAX_CHARGE_AMOUNT)` 추가. 초과 시 400-1.
- `C:\ctj\exp2\r-haiku-A-1-2\src\test\kotlin\com\rest1\domain\wallet\wallet\controller\ApiV1WalletControllerTest.kt`
  - `wallet5`: 작성자 기대 잔액 500 을 450 으로 변경. 규칙 1에 따른 의도된 변경이다.
  - 신규 `wallet9` (가격 0/100/2500 허용, 150 과 -100 거부), `wallet10` (충전 100000 허용, 100001 거부 후 잔액 유지), `wallet11` (2500 구매 시 구매자 0, 작성자 2250).

### 2. 규칙 해석
- 규칙 1: 작성자 입금 = 가격의 90%. 수수료 10%는 플랫폼이 가져간다. 구매자 차감은 전액이다. 가격이 100 단위이므로 나눗셈에 나머지가 없다. 과거 데이터처럼 100 단위가 아닌 값은 내림한다.
- 규칙 2: 적용 대상은 글 작성(`write`)이다. 수정은 가격을 바꾸지 않는다. 검사를 서비스 계층에 두어 컨트롤러 외 호출 경로도 막는다. 기존 `@Min(0)` 은 그대로 두었다. 음수는 DTO 에서 먼저 400-1 로 거부된다.
- 규칙 3: 1회 충전 상한은 충전 경로에만 걸었다. `Wallet.deposit` 에는 넣지 않았다. 작성자 정산도 `deposit` 을 쓰므로 거기 넣으면 큰 정산이 막힌다. 검사는 지갑을 잠그기 전에 하므로 거부 시 잔액과 지갑 행 모두 변하지 않는다.

### 3. 확인 방법과 결과
- 변경 전 `./gradlew test`: BUILD SUCCESSFUL.
- 변경 후 `./gradlew test` (JDK 21, H2): BUILD SUCCESSFUL. 테스트 결과 XML 기준 실패 0, 에러 0. wallet 스위트는 11개로 신규 3개를 포함한다.
- localhost 실제 실행 확인: `./gradlew bootRun`(dev 프로필, 8080), Python urllib 으로 호출 (curl 미사용).
  - 충전 100001: 400, 잔액 0 유지.
  - 충전 100000: 200, 잔액 100000.
  - 가격 150 글 작성: 400.
  - 가격 2500 글 작성: 201.
  - 2500 구매: 201. 구매자 -2500, 작성자 +2250 으로 확인.
- 정리: 내가 띄운 java 프로세스(PID 32796)만 종료했고 8080 포트가 비어 있음을 확인했다. 실행이 만든 `db_dev.mv.db` 는 삭제했다. 빌드 과정에서 뜬 Gradle 데몬은 그대로 두었다.

**확인하지 못한 것**
- 실제 MySQL 등 H2 외 DB 에서의 동작. 테스트는 H2 MySQL 모드로만 돌렸다.
- 동시 구매 시나리오의 부하 테스트. 기존 비관적 잠금 경로는 바꾸지 않았지만 새로 검증하지는 않았다.
- 기존 DB 에 들어 있는 과거 데이터(가격이 100 단위가 아니거나 이미 구매 기록이 있는 글)의 정산 보정. 손대지 않았다.
- 프론트엔드 등 이 API 를 호출하는 다른 클라이언트.
- OpenAPI 문서 내용의 변화 여부.

### 4. 기존 동작에 영향이 있을 수 있는 곳
- 작성자 정산이 10% 적어진다. 기존에 구매된 글의 정산 이력은 소급 보정하지 않았다.
- 글 작성 API: 100 단위가 아닌 가격(예: 150)은 이제 400 이다. 이전에는 통과했다.
- 충전 API: 100,000 초과는 이제 400 이다. 이전에는 통과했다.
- 가격이 1~99 인 과거 유료 글은 구매가 실패한다. 작성자 입금액이 0 이 되어 `Wallet.deposit(0)` 이 400-3 을 던지고, 같은 트랜잭션이라 구매도 롤백된다. 메시지가 "충전 금액은 0보다 커야 합니다"라서 오해를 부를 수 있다. 신규 글은 100 단위 규칙 때문에 생기지 않는다.
- 새 에러 코드 400-5 는 두 가지 의미(가격 음수, 충전 상한 초과)에 쓰였다. 메시지로 구분된다.
- 테스트 픽스처 `wallet7` 은 리포지토리로 가격 300 글을 직접 저장한다. API 검증을 우회하므로 그대로 통과한다. 그대로 두었다.
