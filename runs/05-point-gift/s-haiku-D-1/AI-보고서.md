# s-haiku-D-1 — AI 가 작업 후 낸 보고서 (원문)

## 포인트 선물하기 구현 보고서

작업 경로: `C:\ctj\exp3\s-haiku-D-1`. 시작 06:15:56, 끝 약 06:40, 90분 이내. 커밋 `6895450 point gift` (push 안 함). 원격 저장소 없음.

### 1. 만든 것
- 수정 `C:\ctj\exp3\s-haiku-D-1\src\main\kotlin\com\rest1\domain\member\member\entity\Member.kt`: `point`(Long, 기본 0, `BIGINT DEFAULT 0 NOT NULL`), `addPoint`, `subtractPoint`.
- 수정 `...\member\repository\MemberRepository.kt`: `findByIdForUpdate`(PESSIMISTIC_WRITE), `findIdByUsername`.
- 신규 `...\src\main\kotlin\com\rest1\domain\point\point\`
  - `controller\ApiV1PointController.kt`
  - `service\PointService.kt`
  - `policy\PointGiftPolicy.kt`(수수료, 한도, KST 날짜)
  - `entity\PointGift.kt`(성공한 선물 기록: senderId, receiverId, amount, fee, giftDate)
  - `repository\PointGiftRepository.kt`
- 신규 테스트 `C:\ctj\exp3\s-haiku-D-1\src\test\kotlin\com\rest1\domain\point\point\policy\PointGiftFeeTest.kt`(수수료 경계 단위 테스트 2개)
- `PointGiftPolicyTest.kt`는 수정하지 않았다.

API
- `POST /api/v1/points/charge` `{"amount": N}` → 200-1, data `{balance}`
- `GET /api/v1/points` → 200-1, data `{balance}`
- `POST /api/v1/points/transfer` `{"toUsername", "amount"}` → 201-1, data `{giftId, toUsername, amount, fee, totalPaid, balance}`
- 실패 코드: 400-1(검증), 400-3(1,000P 미만), 400-4(자기 자신), 400-5(잔액 부족), 400-6(1일 한도 초과), 404-2(없는 아이디). 인증은 기존 필터 그대로(`/api/*/**` authenticated).

### 2. 해석
- 수수료: 1만 이하 500, 10만 이하 1,000, 10만 초과는 `금액/1000`을 최대 5,000으로 제한. 결과는 10 단위로 내림. 경계값은 보강 사항 1번을 따랐다.
- 보내는 사람은 금액+수수료 차감, 받는 사람은 금액 전액 가산. 잔액 부족 판단은 금액+수수료 기준.
- 1일 한도: 그날 보낸 선물 금액 합계(수수료 제외) + 이번 금액이 1,000만 이하면 허용.
- 스스로 정한 것 1: "그날"의 기준은 Asia/Seoul(KST) 날짜다. 기획서에 시간대가 없어서 정했다.
- 스스로 정한 것 2: 동시성은 보내는 사람 행을 `SELECT ... FOR UPDATE`로 잠가 같은 사람의 선물을 직렬화한다. 받는 사람도 잠그며, 잠금은 id 오름차순으로 걸어 교착을 피한다. 실패는 예외로 롤백된다. 검증은 모두 잔액 변경 전에 끝난다.
- 스스로 정한 것 3: 존재하지 않는 아이디는 404-2, 자기 자신은 400-4로 처리했다. 기획서에는 코드가 없다.
- 스스로 정한 것 4: 충전은 테스트용이라 상한을 두지 않고 1 이상만 받는다. 포인트 변동 이력(원장) API는 만들지 않았다.

### 3. 확인 방법과 결과
- 기준선: 수정 전 `./gradlew test` 44개 중 PointGiftPolicyTest 7개만 실패. 기존 37개는 통과.
- 구현 후 `./gradlew test`: 46개 전부 통과(기존 37 + 정책 7 + 수수료 단위 2). 실패 0, 스킵 0.
- 정책 테스트를 `--rerun`으로 3회 반복 실행해 모두 통과(동시성 테스트 포함).
- 개발 중 발견하고 고친 버그: 받는 사람을 `findByUsername`으로 먼저 엔티티로 올리면 이후 잠금 조회가 잠금 전 잔액을 그대로 돌려줘서, 동시 선물 시 받는 사람 잔액이 유실됐다(5,000P만 반영). id만 조회하도록 바꿔 해결했다.
- 확인 대상 HTTP는 테스트가 랜덤 포트의 실제 서버에 보낸 것이다. curl과 외부 주소는 쓰지 않았다.

**확인하지 못한 것**
- 개발 DB(`db_dev`, 파일 H2)에 기존 데이터가 있는 상태에서 `point` 컬럼 추가(ddl-auto=update)가 되는지. 테스트는 메모리 DB만 썼다.
- 실제 MySQL에서 `FOR UPDATE` 동작. 테스트는 H2(MySQL 모드)만 돌렸다.
- 앱을 직접 띄워 수동 호출한 확인. 테스트로만 확인했다.
- 여러 서버 인스턴스에서의 동시성. 잠금은 DB 행 잠금에 의존하므로 DB가 하나면 성립한다.

### 4. 남은 문제와 위험
- 정책 불연속: 기획 보강 1번을 그대로 적용하면 100,000P 선물의 수수료는 1,000P, 100,001P는 100P가 된다. 10만 초과 구간에 최소 1,000P를 두는지 기획 확인이 필요하다. 현재 코드는 문서화된 대로 동작한다(`PointGiftFeeTest`에 명시).
- 1일 한도 날짜 기준(KST)은 스스로 정한 값이다. 기획 확인을 권한다.
- 기존 운영 DB에 `point` 컬럼을 추가하면 기본값 0으로 채워지지만, 실 DB에서는 확인하지 못했다.
- 포인트 이력 테이블이 없어 충전 내역은 추적되지 않는다.
- git 경고(LF→CRLF)는 .gitattributes 설정에 따른 것으로 기능과 무관하다.
- 실행한 Gradle 데몬은 `./gradlew --stop`으로 정리했다. Docker나 다른 프로세스는 건드리지 않았다.
