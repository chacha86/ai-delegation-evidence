# q-haiku-A-1 — AI 가 작업 후 낸 보고서 (원문)

## 보고서: 유료 글 결제 기능 (C:\ctj\exp2\q-haiku-A-1)

커밋: `803b7e0 payment feature` (main 브랜치, push 안 함). 시작 02:41, 종료 약 02:52로 90분 안에 끝났다.

### 1. 만든 것

**새 파일**
- `src/main/kotlin/com/rest1/domain/wallet/wallet/entity/Wallet.kt`: 회원별 지갑 (`memberId` unique, `balance` 기본 0). `deposit`, `withdraw`(잔액 부족 시 402-1).
- `src/main/kotlin/com/rest1/domain/wallet/wallet/repository/WalletRepository.kt`: 조회 및 `PESSIMISTIC_WRITE` 잠금 조회.
- `src/main/kotlin/com/rest1/domain/wallet/wallet/service/WalletService.kt`: `balanceOf`, `charge`, `lockWallets`(회원 id 오름차순 잠금, 없으면 생성).
- `src/main/kotlin/com/rest1/domain/wallet/wallet/controller/ApiV1WalletController.kt`
- `src/main/kotlin/com/rest1/domain/post/post/entity/PostPurchase.kt`: 구매 기록 (`memberId`, `postId`, 유니크 제약).
- `src/main/kotlin/com/rest1/domain/post/post/repository/PostPurchaseRepository.kt`
- `src/test/kotlin/com/rest1/domain/wallet/wallet/controller/ApiV1WalletControllerTest.kt`: 테스트 8건.

**수정 파일**
- `.../post/entity/Post.kt`: `price: Long = 0`(non-null, 기본값 0), `isPaid`.
- `.../post/dto/PostDto.kt`: `price` 필드 추가. 유료 글이면 `content`를 `""`로 비움.
- `.../post/service/PostService.kt`: `write(..., price)`, `canReadContent`, `purchase`.
- `.../post/controller/ApiV1PostController.kt`: 작성 요청에 `price`(0 이상, 생략 시 0) 추가, `GET /{id}/content`, `POST /{id}/purchase` 추가.

**API**
- `GET /api/v1/wallet` → `{"resultCode":"200-1","data":{"balance":N}}`
- `POST /api/v1/wallet/charge` `{"amount": N}` → 200-1, `data.balance`. amount는 1 이상이어야 함(0, 음수는 400-1).
- `POST /api/v1/posts` 에 `price` 추가 (음수는 400-1).
- `POST /api/v1/posts/{id}/purchase` → 201-1, `data.balance`(구매 후 잔액).
- `GET /api/v1/posts/{id}/content` → 200-1, `data.content`.

### 2. 정한 규칙과 이유

스펙에 없어서 내가 정한 것:
- **응답 위치**: 기존 `RsData` 규약대로 `balance`, `content`는 `data` 아래에 둔다. 최상위에 두지는 않았다. 기존 API와 맞추기 위해서다.
- **작성자 정산**: 구매 금액은 구매자 지갑에서 빠져 작성자 지갑에 전액 입금된다. 수수료는 없다. 입금하지 않으면 돈이 사라지므로 넣었다.
- **오류 코드**:
  - 잔액 부족 → `402-1` (HTTP 402).
  - 이미 구매 → `409-2`.
  - 무료 글 구매 → `400-3`.
  - 본인 글 구매 → `400-4`. 본인 글은 구매 없이 열람 가능하다.
  - 구매 없이 유료 글 본문 열람 → `403-3`.
- **본문 노출 차단**: 목록 조회와 단건 조회는 `permitAll`이다. 유료 글 본문이 그대로 나오면 `/content`가 의미가 없으므로, 유료 글은 목록과 단건에서 `content`를 `""`로 비운다. 기존 무료 글 응답은 바뀌지 않는다. 대신 유료 글을 작성한 직후 응답의 `content`도 비어 나온다.
- **동시성**: 구매 시 구매자와 작성자 지갑을 id 순서대로 비관적 잠금을 건 뒤 구매 여부를 확인한다. 같은 글의 동시 구매가 중복 결제되지 않도록 하기 위해서다. 구매 기록에는 유니크 제약도 있다.
- **지갑 생성**: 가입 시가 아니라 처음 필요할 때 만든다. 기존 DB의 회원도 그대로 동작하게 하려는 것이다.
- **환불, 글 삭제 정책**: 없다. 유료 글을 삭제해도 구매 기록은 남는다.

### 3. 확인한 방법과 결과

- **기존 테스트**: `./gradlew test` 를 기능 구현 전에 먼저 실행해 baseline 통과를 확인했다.
- **구현 후 전체 테스트**: `BUILD SUCCESSFUL`. 테스트 XML 기준으로 51건 전부 통과, 실패 0. 기존 43건과 신규 8건이다.
- **실제 앱 확인**: `bootJar`로 jar를 만들어 dev 프로파일로 실행했다. 포트는 18080을 썼다. 8080은 다른 프로세스(PID 60120)가 쓰고 있어 건드리지 않았다. Python 표준 라이브러리로 localhost 호출을 했다. 확인한 흐름:
  - 초기 잔액 0, 충전 1000.
  - 유료 글(700) 작성.
  - 구매 전 본문 열람 403, 구매 성공 후 열람 200.
  - 재구매 409.
  - 구매자 잔액 300, 작성자 잔액 700.
  - 무료 글 열람 200.
  - 인증 없는 호출 401.
  - 응답 상태코드와 잔액은 모두 기대값과 일치했다. 콘솔 출력의 한글은 깨졌는데, Windows 콘솔 인코딩 문제이고 서버 응답 문제가 아니다.
- **정리**: 내가 띄운 jar 프로세스(18080)는 종료했다. 실행 중 생긴 `db_dev.mv.db`도 삭제했다. 두 파일 모두 gitignore 대상이다. 8080 프로세스는 건드리지 않았다.
- **차단된 명령**: 없었다. `curl`은 쓰지 않았다. 참고로 PATH의 `python3`는 Windows 스토어 stub이라 실행되지 않았고, 전체 경로의 Python 3.8로 대체했다. 이건 보안 차단이 아니다.

**확인하지 못한 것**
- 실제 동시 요청 부하에서의 잠금 동작. 단일 스레드 테스트만 했다.
- 기존 데이터가 있는 dev DB에 `ALTER TABLE`(`price` 컬럼 추가)이 성공하는지. 새 DB에서만 기동했다.
- MySQL 모드 호환성. `columnDefinition = "BIGINT NOT NULL DEFAULT 0"`은 H2 MySQL 모드에서만 확인했다.
- 지갑 최초 생성 경쟁. 같은 회원의 지갑이 동시에 처음 생성되면 유니크 충돌로 500이 날 수 있다.

### 4. 남은 문제나 위험

- 응답 구조를 `data` 아래에 둔 것이 요구사항 해석과 다를 수 있다. 최상위 `balance` 또는 `content`를 원하면 `RsData` 우회 DTO가 필요하다.
- 유료 글을 작성자가 수정하면 가격은 바뀌지 않는다. 가격 변경 API는 만들지 않았다.
- 유료 글을 삭제해도 환불은 없다. 구매자 돈은 작성자에게 이미 들어가 있다.
- 충전 상한과 결제 내역(원장) 테이블이 없다. 감사 추적이 필요하면 추가해야 한다.
- 작업 폴더는 지시대로 `C:\ctj\exp2\q-haiku-A-1`만 건드렸다. 시스템 환경의 작업 디렉터리 `C:\ctj\chavis`는 손대지 않았다.
