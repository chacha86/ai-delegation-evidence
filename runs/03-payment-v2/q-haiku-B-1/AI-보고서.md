# q-haiku-B-1 — AI 가 작업 후 낸 보고서 (원문)

## 결과 요약
유료 글 결제 기능을 구현하고 `C:\ctj\exp2\q-haiku-B-1` 에 커밋했다 (`a8d8a71 payment feature`, 브랜치 main, push 없음). 시작 02:46, 커밋 약 02:57 이라 90분 안에 끝났다. 기존 테스트 전부 통과, 신규 테스트 18개 추가 후에도 전부 통과.

## 1. 만든 것
**지갑 (`src/main/kotlin/com/rest1/domain/wallet/wallet/`)**
- `entity/Wallet.kt`: memberId(unique), balance(Long). `withdraw` 는 잔액 부족이면 400-8.
- `repository/WalletRepository.kt`: `findAllLockedByMemberIdIn` (PESSIMISTIC_WRITE, memberId 오름차순 잠금).
- `service/WalletService.kt`: `charge`, `balanceOf`, `lockWallets`, `create`.
- `controller/ApiV1WalletController.kt`: `POST /api/v1/wallet/charge`, `GET /api/v1/wallet`.
- 회원가입(`MemberService.join`) 시 지갑 생성 (잔액 0).

**글/구매 (`src/main/kotlin/com/rest1/domain/post/`)**
- `post/entity/Post.kt`: `price` 컬럼 (기본 0, not null). `Post.validatePrice` 로 검증.
- `post/dto/PostDto.kt`: `price` 추가, `content` 는 nullable. 본문 노출 여부를 생성자 인자로 받는다.
- `post/controller/ApiV1PostController.kt`:
  - 작성 `POST /api/v1/posts` 에 `price` 추가 (생략 시 0).
  - 목록/단건 조회: 열람 권한이 없는 유료 글은 `content: null`.
  - `POST /api/v1/posts/{id}/purchase` (201)
  - `GET /api/v1/posts/{id}/content` (유료 글은 작성자·구매자만, 나머지 403-3, 비로그인 401)
- `purchase/` (entity, repository, service): 구매 로직, 열람 권한 판정.
- `post/service/PostService.kt`: 구매자가 있는 글 삭제 거부 (400-11).
- `global/rq/Rq.kt`: 비로그인에서도 쓰는 `actorOrNull` 추가.

**테스트 (`src/test/kotlin/...`)**
- `ApiV1WalletControllerTest` (5개), `ApiV1PostPurchaseControllerTest` (10개), `PaymentConcurrencyTest` (3개).
- 세 클래스는 `payment_test` 라는 별도 H2 DB를 써서 기존 테스트의 post id 의존성에 영향을 주지 않게 했다.

## 2. 정한 규칙과 이유
- 응답 형식: 기존 RsData 규약을 따라 `$.data.balance`, `$.data.content` 에 담았다. 최상위에 `balance` 를 두지 않았다. 스펙의 "응답 JSON 의 balance 필드"를 어떻게 해석할지는 확인이 필요하다.
- 에러 코드: 충전 1 미만 400-3, 소수 400-4, 10만 초과 400-5, 가격 오류 400-6, 무료 글 구매 400-7, 잔액 부족 400-8, 중복 구매 400-9, 자기 글 구매 400-10, 구매자 있는 글 삭제 400-11, 열람 권한 없음 403-3. 모두 4xx 이다.
- 잔액 음수 방지와 동시성: 구매/충전은 트랜잭션 안에서 지갑 행을 `SELECT ... FOR UPDATE` 로 잠근다. 잠금은 memberId 오름차순으로 잡아 교착을 피한다. 구매 실패 시 전체 롤백된다. 중복 구매는 잠금 안에서 검사하고, `(member_id, post_id)` unique 제약을 마지막 방어선으로 둔다.
- 작성자 지급: `price / 10 * 9`. 가격이 100원 단위이므로 정수로 정확히 나뉜다.
- 가격 검증: null 이면 0, 음수와 100원 단위가 아니면 거부, `longValueExact` 로 오버플로 방지.
- 충전 금액: BigDecimal 로 받아 정수 여부를 검사한다. 소수 입력을 조용히 잘라서 충전하지 않는다.
- 스스로 정한 것:
  - 무료 글 구매는 거부한다 (400-7).
  - 자기 글 구매는 거부한다 (400-10).
  - 글 수정(PUT)은 가격을 바꾸지 않는다.
  - 구매자가 있는 유료 글은 삭제할 수 없다 (400-11). 삭제하면 구매자가 돈을 내고 본문을 잃기 때문이다.
  - 무료 글 본문은 로그인 필요 (시큐리티 설정 그대로).
- 기존 동작 보존: 무료 글의 목록/단건 응답은 이전과 같다.

## 3. 확인한 방법과 결과
- `./gradlew test` (JDK 21, H2): 시작 전 베이스라인 통과. 구현 후 전체 10개 클래스 통과, 실패 0.
- 실제 앱 구동 후 localhost HTTP 로 Python 스크립트(`scratchpad\e2e_check.py`) 실행: 24개 항목 전부 PASS. 충전 거부, 잔액 불변, 가격 검증, 목록/단건 본문 숨김, 401/403/200 본문 접근, 구매 성공 후 7500/2250 잔액, 중복·자기 구매 실패까지 확인했다.
- 동시성 테스트 (실제 커밋되는 다중 스레드):
  - 같은 글 10번 동시 구매 → 1번만 성공, 1회 차감.
  - 100원 20번 동시 충전 → 정확히 2000원.
  - 잔액 2500원으로 1000원짜리 5개 동시 구매 → 2개만 성공, 잔액 500, 음수 없음.
- 앱 정리: 내가 띄운 bootRun 프로세스(8080)를 종료했고 포트가 비었음을 확인. `db_dev.mv.db`(앱이 만든 파일)도 삭제했다. curl 은 쓰지 않았다.

## 확인하지 못한 것
- 기존 dev DB(`db_dev`)에 데이터가 있는 상태에서의 스키마 마이그레이션 (`ddl-auto: update` 로 `post.price` 컬럼 추가). 이번 실행에서 DB를 새로 만들었기 때문에 검증하지 못했다.
- MySQL 에서의 동작. 테스트와 실행 모두 H2(MySQL 모드)다. `FOR UPDATE` 와 unique 제약은 MySQL 에서도 같은 방식으로 동작할 것으로 보지만 실제로 확인하지는 않았다.
- 응답 형식 해석 (`$.data.balance` vs 최상위 `balance`)은 요청자 확인이 필요하다.

## 4. 남은 문제나 위험
- 기존 회원 중 지갑이 없는 경우는 충전/구매 시점에 지갑을 만든다. 이 경로는 동시에 처음 접근하면 한쪽이 unique 위반으로 실패할 수 있다. 가입으로 만든 회원에게는 해당하지 않는다.
- 삭제 거부(400-11)는 스펙에 없던 내 판단이다. 요구와 다르면 되돌려야 한다.
- 구매 가능한 가격 상한을 두지 않았다 (Long 범위까지만 허용).
- 구매 기록이 post 테이블과 FK 로 묶여 있지 않다. 현재는 삭제를 막아 문제를 피하지만, 나중에 삭제 정책을 바꾸면 고아 구매 기록을 신경 써야 한다.
