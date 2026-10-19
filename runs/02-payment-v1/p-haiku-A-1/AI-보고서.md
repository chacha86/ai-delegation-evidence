# p-haiku-A-1 — AI 가 작업 후 낸 보고서 (원문)

## 결제 기능 구현 보고서

작업 폴더: `C:\ctj\exp2\p-haiku-A-1` (브랜치 main, 커밋 `e687da4 payment feature`, push 하지 않음). 시작 01:04, 커밋 01:09, 90분 제한 안에 끝났다.

### 1. 만든 것

신규 파일
- `src/main/kotlin/com/rest1/domain/wallet/entity/Wallet.kt`: 회원당 1개 지갑. 잔액 `balance`(Long, 처음 0), 충전·차감 메서드. 차감 시 잔액 부족이면 `400-4`.
- `src/main/kotlin/com/rest1/domain/wallet/repository/WalletRepository.kt`: 잔액 변경용 `PESSIMISTIC_WRITE` 잠금 조회 포함.
- `src/main/kotlin/com/rest1/domain/wallet/service/WalletService.kt`: 지갑 조회·생성(없으면 생성), 충전.
- `src/main/kotlin/com/rest1/domain/wallet/controller/ApiV1WalletController.kt`: 아래 API 1, 2.
- `src/main/kotlin/com/rest1/domain/post/purchase/entity/PostPurchase.kt`: 구매 기록. (member_id, post_id) 유니크 제약.
- `src/main/kotlin/com/rest1/domain/post/purchase/repository/PostPurchaseRepository.kt`
- `src/main/kotlin/com/rest1/domain/post/purchase/service/PostPurchaseService.kt`: 구매 규칙과 열람 가능 여부 판단.
- 테스트: `src/test/kotlin/com/rest1/domain/wallet/controller/ApiV1WalletControllerTest.kt`(4개), `src/test/kotlin/com/rest1/domain/post/post/controller/ApiV1PaidPostControllerTest.kt`(8개)

수정 파일
- `Post.kt`: `price: Long = 0` 추가(`@ColumnDefault("0")`), `isPaid` 추가.
- `PostDto.kt`: `price` 필드 추가. 유료 글이면 `content` 를 `""` 로 가림.
- `ApiV1PostController.kt`: 글 작성 `price` 입력, 아래 API 3, 4 추가.
- `PostService.kt`: `write` 에 price 인자(기본 0).
- `Rq.kt`: 비로그인이면 null 을 주는 `actorOrNull` 추가.
- `SecurityConfig.kt`: `GET /api/*/posts/{id}/content` 를 permitAll 에 추가. 무료 글 열람을 비로그인에게 열어 두기 위함이고, 유료 글 인증은 코드에서 검사한다.

API
1. `GET /api/v1/wallet` → `{"balance": 숫자}` (최상위 필드, 로그인 필요)
2. `POST /api/v1/wallet/charge`, 본문 `{"amount": 숫자}` → 200, `{"resultCode":"200-1", ..., "data":{"balance": n}}`. amount 는 1 이상이어야 한다(아니면 400-1).
3. `POST /api/v1/posts` 본문에 `price`(선택, 생략·null 이면 0, 음수면 400-1)
4. `POST /api/v1/posts/{id}/purchase` → 200, data.balance
5. `GET /api/v1/posts/{id}/content` → `{"content": "..."}` (최상위 필드)

### 2. 정한 규칙과 이유

- 잔액은 Member 가 아닌 별도 Wallet 엔티티. 회원 생성 시 지갑을 같이 만드는 대신, 지갑이 없으면 처음 접근할 때 만든다. 기존 가입자와 시드 데이터가 그대로 동작하게 하려는 것.
- 충전·구매는 모두 지갑 행을 `SELECT ... FOR UPDATE`(비관적 잠금)로 잡고 처리한다. 같은 회원의 동시 구매·충전이 직렬화되어 잔액이 음수가 되거나 갱신이 덮어써지지 않게 한다. 중복 구매도 같은 잠금 안에서 확인하고, DB 유니크 제약으로 한 번 더 막는다.
- 구매 규칙: 무료 글 구매는 400-3, 본인 글 구매는 400-5, 이미 구매했으면 409-1, 잔액 부족이면 400-4, 없는 글은 404.
- 열람 규칙: 무료 글은 비로그인도 열람 가능. 유료 글은 비로그인이면 401-1, 로그인했지만 작성자도 구매자도 아니면 403-3, 작성자 또는 구매자는 열람 가능.
- 유료 글 본문은 목록·단건 조회(`GET /api/v1/posts`, `/posts/{id}`)에서 빈 문자열로 가린다. 가리지 않으면 열람 엔드포인트를 막아도 본문이 새기 때문. 대신 작성자 본인도 상세 조회에서는 본문을 못 본다(열람 엔드포인트는 작성자에게 허용).
- 구매 금액은 판매자에게 입금하지 않는다. 요구사항에 판매자 정산이 없어서 임의로 만들지 않았다.
- 가격은 작성 시에만 정한다. 수정 API(PUT)는 건드리지 않았다.
- 응답 형식: GET 두 개(wallet, content)는 요구대로 최상위 필드, POST 두 개는 기존 관례대로 RsData 로 감쌌다.
- 기존 API 호환: `PostDto` 에 `price` 필드가 추가되었다(추가만 함). 무료 글의 응답은 기존과 같다.
- 테스트 격리: 유료 글 테스트가 H2 시퀀스를 올려 기존 `글 작성` 테스트의 기대 id(4)를 깨뜨려서, 이 테스트 클래스에 `@DirtiesContext(AFTER_CLASS)` 를 달았다.

### 3. 확인한 방법과 결과

- `./gradlew test` (JDK 21, H2): 전체 49개 통과. 기존 테스트 37개(시작 전 기준 전부 통과 확인)와 신규 12개 모두 통과.
- `./gradlew bootJar` 로 만든 jar 를 `localhost:8080` 에서 직접 실행하고, Python(urllib, localhost 만)으로 시나리오 확인: 잔액 0 → 충전 1000 → 유료 글(500) 작성 → 상세 조회 시 본문 빈 값 → 비로그인 열람 401 → 구매 전 열람 403 → 구매 200(잔액 500) → 구매 후 열람 200 → 재구매 409 → 잔액 500 → 충전 0 은 400. 모든 상태 코드가 기대와 일치. 확인 후 내가 띄운 jar 프로세스는 종료했고 8080 포트가 해제된 것을 확인했다. 내 빌드가 띄운 Gradle 데몬은 `./gradlew --stop` 으로 정리했다. curl 은 쓰지 않았다.

확인하지 못한 것
- 동시성 테스트(같은 회원이 여러 요청을 동시에 구매·충전하는 경우)는 작성하지 않았다. 잠금 설계는 코드 검토로만 확인했다.
- MySQL 등 실제 DB 에서의 동작. H2 에서만 확인했다.
- 이미 데이터가 있는 기존 DB 에 `post.price` 컬럼을 추가하는 마이그레이션(`@ColumnDefault("0")` 으로 기본값을 줬지만 실제 기존 데이터로 돌려 보지 않았다).
- Swagger UI 의 문서 표시.

### 4. 남은 문제나 위험

- 충전은 실제 결제 연동이 없다. 누구나 금액 제한 없이 잔액을 늘릴 수 있다. 실서비스 전에 결제 검증이 필요하다.
- 판매자 정산이 없어서 구매 금액이 시스템에서 사라진다. 정산 정책을 정해야 한다.
- 지갑 거래 내역(원장)을 남기지 않았다. 충전·구매 이력 추적은 안 된다.
- 유료 글 상세 조회에서는 작성자 본인도 본문이 빈 값으로 보인다(열람 엔드포인트로는 보인다).
- 커밋 시 CRLF 경고가 떴다. `.gitattributes` 설정 때문이며 내용에는 영향 없다.
