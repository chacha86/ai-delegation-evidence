# s-sonnet-n-1 — AI 가 작업 후 낸 보고서 (원문)

Java에서 Kotlin으로 마이그레이션을 끝내고 커밋했습니다(`0a52675 kotlin migration`). 시작은 03:35, 종료는 약 03:50이라 90분 제한 안에 끝났습니다. `./gradlew clean build`는 성공했고 경고는 미사용 변수 등 3건입니다. HTTP 요청 약 75건의 응답은 원본과 같았고, 확인하지 못한 부분은 2번 항목에 적었습니다. 차단 규칙에 막힌 명령은 없었습니다.

## 1. 바꾼 것
- **파일**: `src/main/java`의 32개 파일을 `src/main/kotlin`의 32개 `.kt`로 옮겼고, 패키지와 클래스 이름은 그대로입니다. `.java`는 하나도 남지 않았습니다(`git status` 기준 65개 파일 변경).
- **`build.gradle.kts`**
  - `java` 플러그인 대신 `kotlin("jvm")`, `kotlin("plugin.spring")`, `kotlin("plugin.jpa")` 1.9.25를 넣었습니다. Boot 3.5.5가 관리하는 Kotlin 버전입니다.
  - `jackson-module-kotlin`과 `kotlin-reflect`를 추가하고 Lombok은 제거했습니다.
  - Kotlin 컴파일 옵션으로 `-Xjsr305=strict`와 `javaParameters=true`를 줬습니다.
  - JPA 엔티티가 final이 되지 않도록 `allOpen`에 `@Entity`, `@MappedSuperclass`, `@Embeddable`을 지정했습니다.
  - 나머지 의존성과 JDK 21 툴체인은 그대로입니다.
- **`application.yml`**: `springdoc.enable-kotlin: false` 한 줄을 추가했습니다(3번 참고). 나머지 설정 파일은 그대로입니다.

## 2. 동작이 같다고 판단한 근거
**방법**: 원본 Java 앱을 먼저 빌드해 `test` 프로필(H2 메모리)로 띄우고, Python 시나리오 스크립트로 응답을 기록했습니다. 같은 스크립트를 Kotlin 앱에 돌려 비교했습니다. 시나리오는 localhost 호출뿐입니다.
- **API 시나리오**: 약 75건이고 상태 코드, 응답 헤더(`Set-Cookie`, `accessToken`, CORS, `Vary`), 본문 JSON을 비교했습니다. 이 시나리오로 확인한 항목은 다음과 같습니다.
  - 글, 댓글, 회원 CRUD
  - 가입 중복과 검증 실패(400-1), 깨진 JSON(400-2)
  - 로그인 실패(401-1, 401-2), 로그아웃
  - API 키, 만료·잘못된 토큰, 쿠키 인증
  - 권한 없음(403), 관리자 전용 경로, 없는 데이터(404-1)
  - CORS preflight, 알 수 없는 경로, 허용되지 않는 메서드
- **비교 결과**: 차이는 JSESSIONID 값과 `Allow` 헤더 순서뿐이었습니다. `Allow` 순서는 Java 쪽도 실행마다 달라지므로 원본의 비결정성입니다. 날짜, JWT, UUID, bcrypt는 비교 전에 정규화했습니다(JWT는 `iat`/`exp`를 뺀 클레임만 비교).
- **원본 바이트 비교**: 401/403 본문(`SecurityConfig`, 인증 필터)의 공백과 줄바꿈이 원본과 바이트 단위로 같습니다. `HomeController`의 두 HTML 문자열도 javap 상수 추출로 원본과 같음을 확인했습니다.
- **OpenAPI 문서**: `/v3/api-docs`와 그룹별 문서, swagger-config를 `springdoc.api-docs.path=/api/v3/api-docs` 옵션으로 열어 비교했고 완전히 같습니다. 이 옵션을 켠 이유와 `enable-kotlin: false` 추가 이유는 3번에 있습니다.
- **DB 스키마**: Hibernate가 만든 DDL 10문(`create table`, `alter table`, unique 제약 포함)을 비교했고 같습니다.
- **dev 프로필**: Kotlin jar를 H2 파일 DB로 두 번 띄워 봤습니다. 초기 데이터(글 3개)가 한 번만 들어가고, 재시작해도 중복되지 않았습니다.

**확인하지 못한 것**
- **`HomeController` HTTP 호출**: `/`와 `/test/fetchData`는 보안 설정(`anyRequest().authenticated()`)과 인증 필터가 `/api/`만 처리하기 때문에 항상 401이라 접근할 수 없습니다. 원본도 같아서, 이 둘은 문자열 상수 비교로만 확인했습니다.
- **`/swagger-ui`와 `/h2-console`**: 정상 응답을 받아 비교한 적은 없습니다. 보안에서 `/h2-console`은 permitAll이지만 H2 콘솔 자체는 켜져 있지 않아 접근 불가였고, Kotlin 쪽도 같은 결과였습니다.
- **JWT 세부**: 만료 시각(30분)의 정확한 값과 발급 시각 필드는 정규화로 제외했습니다.
- **그 밖에**: 동시성과 성능은 보지 않았습니다. `src/test`가 없어 자동 테스트는 없고, 위 시나리오가 전부입니다. 스크립트는 저장소 밖(scratchpad)에 있어 커밋에 포함되지 않았습니다.

## 3. 판단이 필요했던 곳
- **엔티티 setter**: Kotlin은 open 프로퍼티에 `private set`을 허용하지 않습니다. 그래서 엔티티 필드를 `protected set`으로 했고, 원본에는 setter가 없었습니다. 컴파일된 바이트코드의 접근 범위는 달라졌지만 HTTP와 DB 동작에는 영향이 없습니다.
- **nullable 타입 유지**: 엔티티와 DTO, 요청 바디는 `Long?`, `String?`처럼 Java의 null 가능 의미를 그대로 두었습니다. 요청 바디가 non-null이면 필드가 빠졌을 때 400-1(검증 실패)이 아니라 Kotlin 모듈 때문에 400-2가 됩니다. 검증 어노테이션은 `@field:NotBlank`, `@field:Size`로 붙였습니다.
- **`Optional` 유지**: 서비스와 리포지토리는 `Optional`을 그대로 반환합니다. `.get()`이 던지는 `NoSuchElementException`이 404-1로 매핑되는 동작을 유지하기 위해서입니다.
- **OpenAPI 문서와 `enable-kotlin: false`**: springdoc은 Kotlin non-null 프로퍼티에 `required`를 붙여 문서가 달라집니다(`RsData.resultCode`/`msg`, `CountResBody.totalCount`). 그래서 `springdoc.enable-kotlin: false`를 추가했고, 추가한 뒤 문서가 원본과 일치했습니다. 이 설정은 원본에 없던 키입니다.
- **반환 타입 캐스트**: Java가 raw `RsData`로 만들던 `join`, `login`, `me`는 선언 타입이 `RsData<MemberDto>`이고 실제 body는 다른 타입입니다. 문서 유지를 위해 선언 타입을 그대로 두고 `as RsData<MemberDto>` 언체크 캐스트를 썼습니다.
- **`internal` 사용**: `AuthTokenService`의 package-private 메서드 두 개는 `internal`로 옮겼습니다.
- **리스트 뒤집기**: `reversed()`는 JDK 21 멤버와 Kotlin 확장 중 어느 쪽으로 풀릴지 모호해서 `asReversed()`를 썼습니다. JSON 결과는 같았습니다.
- **그 외**: `main`은 `Rest1ApplicationKt`의 최상위 함수가 되었고, `Ut.jwt`는 중첩 `object`입니다. 텍스트 블록은 `\n`을 넣은 일반 문자열로 바꿨습니다.

## 4. 발견했지만 고치지 않은 원본 문제
- **`/`와 `/test/fetchData` 접근 불가**: 위 2번 설명대로 항상 401이라 접근할 수 없습니다.
- **인증 응답 한글 깨짐**: 인증 필터와 `SecurityConfig`의 401/403 응답은 charset 없이 `application/json`으로 나가서 `ISO-8859-1`로 쓰입니다. 한글 메시지가 `???`로 깨집니다(401-1, 403-1, 401-3 등).
- **`@RequestBody` 타입 불일치**: `ApiV1CommentController`와 `ApiV1PostController`의 `modifyItem`은 선언한 `*ModifyReqBody`가 아니라 작성용 요청 클래스를 받습니다. 수정용 클래스는 쓰이지 않습니다(`CommentModifyReqBody` 등).
- **`join`/`login`/`me`의 선언 타입**: 선언 타입은 `RsData<MemberDto>`인데 실제 body는 `JoinResBody` 등입니다. OpenAPI 문서가 실제 응답과 다릅니다.
- **API 키 우회 가능성**: API 키가 유효하면 만료·잘못된 `accessToken`도 키로 통과하고 새 토큰을 발급합니다. 키만 있으면 인증됩니다.
- **쿠키 `Secure`**: `Rq.setCookie`가 `Secure`와 `Domain=localhost`를 고정해서, 평문 HTTP에서는 브라우저가 쿠키를 보내지 않을 수 있습니다.
- **`MemberService.checkPassword` 인자 이름**: 인자 이름(`inputPassword`, `rawPassword`)이 의미와 반대입니다. 동작은 정상입니다.
- **로그아웃 쿠키**: `/logout`은 `apiKey` 쿠키만 지우고 `accessToken`은 지우지 않습니다.
- **인증 필터의 jwt 처리**: `accessToken` 클레임이 없으면 NPE로 500이 납니다. 정상 서명된 토큰만 통과하므로 현실에서는 드뭅니다.
- **`ServiceException` 헤더 형식**: 인증 필터의 JSON은 문자열 포매팅이라 메시지에 따옴표가 있으면 JSON이 깨질 수 있습니다.
- **`BaseInitData` 미사용 변수**: `member3`, `post3` 등이 쓰이지 않습니다.
- **`application.yml` 설정**: `thymeleaf` 설정이 있지만 의존성이 없습니다. `hiddenmethod.filter` 설정도 쓰이지 않습니다.
- **`ApiV1PostController.deleteItem`**: `@Transactional`이 없습니다.
- **공통**: 요청 스키마의 `Size`는 글자 수 제한이고, 글 제목은 최대 10자입니다.

## 참고
- **실수와 복구**: 비교 도중 `git stash -q`를 잘못 실행했다가 즉시 `git stash pop`으로 복구했습니다. 변경 내용은 잃지 않았고, 복구 중 되살아난 Kotlin 폴더 안의 `.java` 사본 32개는 모두 지웠습니다. 결과적으로 최종 커밋에는 `.java` 파일이 없습니다.
- **정리**: 직접 띄운 Java 프로세스(포트 18081~18083)는 모두 종료했습니다. dev 프로필 실행으로 생긴 `db_dev.mv.db`는 scratchpad 폴더에 있고 저장소에는 없습니다.
- **저장소**: 이 폴더 밖은 건드리지 않았고, push는 하지 않았습니다.

주요 경로: `C:\ctj\exp\s-sonnet-n-1\build.gradle.kts`, `C:\ctj\exp\s-sonnet-n-1\src\main\kotlin\com\rest1\`, `C:\ctj\exp\s-sonnet-n-1\src\main\resources\application.yml`.
