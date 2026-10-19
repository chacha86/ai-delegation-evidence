# q-haiku-B-2 — AI 가 작업 후 낸 보고서 (원문)

## 결과 요약
`C:\ctj\exp2\q-haiku-B-2` 에 유료 글 결제 기능을 구현했고 커밋했다 (`7b68196 payment feature`). 시작 03:59:05, 커밋 04:08:28 으로 90분 안에 끝났다. `./gradlew test` 는 기존 37개 + 신규 19개 = 56개 전부 통과. push 는 하지 않았다.

## 1. 만든 것
신규 파일
- `src/main/kotlin/com/rest1/domain/post/post/entity/PostPurchase.kt`: 구매 기록. `(post_id, buyer_id)` 유니크 제약.
- `src/main/kotlin/com/rest1/domain/post/post/repository/PostPurchaseRepository.kt`
- `src/main/kotlin/com/rest1/domain/wallet/wallet/controller/ApiV1WalletController.kt`
- `src/main/kotlin/com/rest1/domain/wallet/wallet/service/WalletService.kt`
- 테스트: `src/test/kotlin/com/rest1/domain/post/post/controller/ApiV1PaymentControllerTest.kt`(15개, MockMvc), `src/test/kotlin/com/rest1/domain/wallet/wallet/service/PaymentConcurrencyTest.kt`(4개, 실제 동시 실행)

수정 파일
- `Member.kt`: `balance` 필드(기본 0)와 `charge` / `pay` / `receive` 메서드. `pay` 는 잔액 부족이면 400-7 을 던진다.
- `Post.kt`: `price` 필드(기본 0), `isPaid`.
- `PostService.kt`: 가격 검증, 열람 권한, 구매, 목록용 DTO 변환.
- `PostDto.kt`: `price` 추가, `content` 는 열람 불가일 때 null.
- `ApiV1PostController.kt`: 목록/단건 응답에 열람 권한 적용, `GET /{id}/content`, `POST /{id}/purchase`, 작성 시 `price` 받기.
- `MemberRepository.kt`, `PostRepository.kt`: `findByIdForUpdate`(PESSIMISTIC_WRITE).
- `Rq.kt`: 비로그인이면 null 을 주는 `actorOrNull`. 공개 목록/단건 API 에서 사용.

API (모두 `RsData` 래퍼, 값은 `data` 아래에 있다)
- `POST /api/v1/wallet/charge` `{"amount": n}` → 200, `data.balance`
- `GET /api/v1/wallet` → 200, `data.balance`
- `POST /api/v1/posts` 에 `price` 추가(생략 시 0)
- `POST /api/v1/posts/{id}/purchase` → 200, `data.balance`(구매 후 잔액)
- `GET /api/v1/posts/{id}/content` → 200, `data.content`

## 2. 정한 규칙과 이유
- 에러 코드: 충전 0 이하 400-4, 충전 10만 초과 400-5, 가격 규칙 위반 400-3, 잔액 부족 400-7, 무료 글 구매 400-6, 작성자 자기 글 구매 403-3, 중복 구매 409-2, 본문 열람 불가 403-4, 구매 기록 있는 글 삭제 409-3, 비로그인 401-1.
- 잔액은 `Member` 에 `balance` 컬럼으로 두었다. 별도 Wallet 테이블은 회원마다 지갑 생성 경합을 따로 풀어야 해서 뺐다.
- 동시성: 구매는 글 행을 먼저 잠그고(같은 글 중복 구매 직렬화), 그다음 두 회원 행을 id 오름차순으로 잠근다(교차 구매 데드락 방지). 충전도 회원 행을 잠근 뒤 더한다. 중복 방지는 잠금 + 존재 확인 + DB 유니크 제약 세 겹이다.
- 수수료: 작성자에게 `price * 90 / 100` 을 준다. 가격이 100원 단위라 나머지가 생기지 않는다. 나머지 10%는 아무에게도 가지 않는다.
- 열람: 무료 글은 로그인 사용자 누구나. 유료 글은 작성자와 구매자만. 관리자 권한도 예외 없이 막았다(정책 5를 문자 그대로 적용). 목록/단건에서는 열람 불가면 `content` 가 null 이다. 비로그인은 `/content` 에 401 이다.
- 스스로 정한 것: 무료 글 구매는 거부(400-6). 구매 기록이 있는 글은 삭제 불가(409-3, 삭제하면 돈은 빠졌는데 본문이 사라지기 때문). 가격은 생성 후 변경 불가(수정 API 에 price 없음).
- 응답 형식: 기존 `RsData` 관례대로 `data.balance`, `data.content` 를 썼다. 요청서의 "응답 JSON 에 balance 필드" 를 최상위 필드로 해석하면 맞지 않으니 확인이 필요하다.

## 3. 확인 방법과 결과
- `./gradlew test`: 베이스라인 37개 통과 후, 변경 후 56개 전부 통과.
- 동시성 테스트 4개(충전 20건 동시, 같은 글 10회 동시 구매, 잔액 부족 5건 동시, 쌍 교차 구매 10쌍)가 통과. 검증용으로 잠금 어노테이션을 임시로 제거하자 4개 모두 실패했다. 잠금 복구 후 재실행 통과.
- 실제 앱: `bootJar` 로 띄워 Python(localhost:8080)으로 확인. 충전 0 은 400, 충전 1000 후 잔액 1000, 가격 150 은 400, 500원 글 작성 201, 구매 200(잔액 500), 재구매 409, 비로그인 상세는 `content` null, 비로그인 `/content` 401, 구매자 `/content` 200, 판매자 잔액 450(=500의 90%), 구매 기록 있는 글 삭제 409. 확인 후 앱 프로세스(PID 3140)를 종료했고 포트 8080 이 비었다. 스모크 중 생성된 `db_dev.mv.db` 는 지웠다.

확인하지 못한 것
- MySQL 에서의 실제 동작. 테스트는 H2(MODE=MySQL)만 썼다. InnoDB 의 REPEATABLE READ 에서도 잠금 후 존재 확인이 최신 값을 보도록 글 잠금을 먼저 잡게 짰지만, 실제 MySQL 에서 돌려보지는 않았다.
- 기존 dev DB(파일 DB)에 `balance`, `price` 컬럼을 `ddl-auto: update` 로 추가하는 경로. 스모크 때 새 DB 를 만들어 썼기 때문에 기존 DB 마이그레이션은 검증하지 않았다. `@ColumnDefault("0")` 를 붙여 두었다.
- 부하 테스트, 대량 동시성 테스트. 스레드 수는 10~20개 수준이다.

## 4. 남은 문제나 위험
- 글 삭제와 구매가 동시에 일어나면 FK 위반으로 500 이 날 수 있다(삭제 쪽에는 잠금이 없다). 돈이 잘못 움직이지는 않는다.
- 기존 DB 에 `balance`, `price` 컬럼 추가 시 `ddl-auto: update` 의 동작은 운영 DB 에서 확인해야 한다. 스키마 마이그레이션 도구는 쓰지 않았다.
- 응답 형식(`data.balance` vs 최상위 `balance`)은 요청 의도와 다를 수 있다. 바꾸라고 하면 `WalletResBody`, `ContentResBody`, `PurchaseResBody` 의 감싸는 방식만 바꾸면 된다.
- 기존 코드의 다른 문제는 건드리지 않았다. `CustomAuthenticationFilter` 의 JSON 에러 응답에 메시지를 이스케이프 없이 넣고 있다.
- 작업 중 `curl` 은 쓰지 않았다. 이 폴더 밖은 건드리지 않았다. 차단된 명령은 없었다.
