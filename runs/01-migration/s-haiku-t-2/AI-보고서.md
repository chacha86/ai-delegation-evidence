# s-haiku-t-2 — AI 가 작업 후 낸 보고서 (원문)

## Kotlin 마이그레이션 보고 (`C:\ctj\exp\s-haiku-t-2`)

시작 05:07:04, 완료 약 05:25 (90분 이내). 커밋 `686c08d kotlin migration` (로컬, push 안 함, 커밋 메시지에 요청된 Co-Authored-By / Claude-Session 트레일러 포함).

### 1. 바꾼 것
- **빌드 설정** `C:\ctj\exp\s-haiku-t-2\build.gradle.kts`
  - Kotlin 1.9.25 (Spring Boot 3.5.5 가 관리하는 `kotlin.version` 과 동일). 플러그인: `kotlin("jvm")`, `plugin.spring`, `plugin.jpa`, `plugin.allopen` (`@Entity`, `@MappedSuperclass`, `@Embeddable` 을 open 처리, 지연 로딩 프록시용).
  - Lombok 의존성 및 `configurations` 블록 제거. `kotlin-reflect`, `jackson-module-kotlin` 추가. `-Xjsr305=strict`, `jvmToolchain(21)`.
- **소스**: `src/main/java` 32개 파일 삭제, `src/main/kotlin` 32개 파일 생성 (패키지·클래스명 동일). `Rest1Application` 의 main 은 companion `@JvmStatic` 으로 유지.
- `src/main/resources`, `src/test` 는 변경 없음 (diff 0 줄 확인).

### 2. 테스트
- 원본: 37개 실행, 통과 37, 실패 0, 스킵 0.
- 변환 후: 37개 실행, 통과 37, 실패 0, 스킵 0 (클래스별 개수 동일). 컴파일 warning 0.

### 3. 동작이 같다고 본 근거와 미확인 사항
근거:
- **HTTP 비교**: 원본 jar 과 변환 jar 을 각각 새 H2 DB 로 기동(dev 프로파일)하고, 같은 56개 시나리오를 두 서버에 보내 status, Content-Type, Set-Cookie 속성, 본문을 비교했다 (타임스탬프, JWT, UUID 만 정규화). 차이 0. 시나리오: 글/댓글 조회 및 404, 400-1 검증 메시지(정렬 포함), 400-2 잘못된 JSON, 401-1/2/3, 403-1/2, 409-1, 로그인/가입/로그아웃, 쿠키 및 Bearer 인증, 글/댓글 CRUD 와 권한 오류, 관리자 API. (스크립트: `<임시폴더>\compare.py`)
- **DB 스키마**: 두 앱의 H2 DDL (테이블, 컬럼, PK, FK, identity) 을 덤프해 비교 → 동일.
- 기동 로그의 WARN 목록 동일.

미확인:
- `/` (홈 HTML), `/test/fetchData`, `/v3/api-docs`, swagger-ui 는 **두 앱 모두 401** 이었다 (필터가 `/api/` 외 경로를 인증하지 않고 `anyRequest().authenticated()` 가 걸려 있음). 그래서 본문 비교가 불가능했다. HTML 텍스트는 원본 text block 의 바이트에 맞춰 작성했지만 실측은 못 했다. OpenAPI 스키마 동일성도 미확인이다.
- CORS preflight(OPTIONS) 미테스트. 에러/SQL 로그 내용 미비교.

### 4. 판단이 필요했던 곳 (원본과 다르게 한 것)
- `AuthTokenService.genAccessToken`, `payloadOrNull`: 원본은 package-private 이었으나 Kotlin 에는 해당 가시성이 없다. `internal` 은 이름이 mangling 되어 Java 테스트 호출이 깨지므로 **public** 으로 바꿨다 (가시성 확대, 동작 영향 없음).
- 엔티티 setter: all-open 때문에 `private set` 이 허용되지 않아 **`protected set`** 으로 했다 (원본에는 setter 가 없었다).
- Bean Validation 은 생성자 파라미터가 아니라 **`@field:`** 에 붙였다 (파라미터에 붙으면 검증이 누락됨).
- 요청 DTO 필드는 **nullable (`String?`)**. 필드 누락 시 Jackson 이 400-2 로 던지는 대신 원본처럼 검증 메시지 400-1 이 나오도록 했다.
- 원본 raw type `new RsData(...)` 응답은 선언 타입 (`RsData<MemberDto>` 등) 을 유지하고 unchecked cast 를 썼다. JSON 은 동일하다.
- `Map.of` 의 null → NPE 동작은 `java.util.Map.of` 로 보존. `secret.getBytes()` 는 `Charset.defaultCharset()` 로 옮겼다.
- `isBlank`: Kotlin `isBlank()` 는 NBSP 등 space separator 를 공백으로 보지만 Java 는 아니다. 그래서 Java 와 같은 판정을 하는 `isJavaBlank()` (`standard/ut/Ut.kt`) 를 만들어 6곳에 썼다.
- text block 으로 된 401/403 본문, 홈 HTML 은 원본과 같은 바이트가 나오도록 명시 문자열로 작성했다.
- 그 밖: `catch (Exception e) { throw e; }` 는 no-op 이라 제거. `BaseInitData.work2` 의 미사용 `member3` 조회는 `get()` 호출을 남겨 NoSuchElement 동작을 보존. `Ut` 는 `object Ut { object jwt }` + `@JvmStatic` 으로 Java 테스트의 `Ut.jwt.x()` 호출 형태를 유지.

### 5. 발견했지만 고치지 않은 원본 문제
- **`/`, `/test/fetchData`, `/swagger-ui`, `/v3/api-docs` 가 인증 없이 접근 불가** (HTTP 401 로 확인). `anyRequest().authenticated()` 와 `/api/` 에만 적용되는 필터 때문으로 보인다. `HomeController` 의 "Welcome" 페이지 의도와 어긋난다.
- **CORS 설정이 적용되지 않을 가능성 (추정, 미검증)**: `corsConfigurationSource` 빈은 있으나 `filterChain` 에 `http.cors(...)` 호출이 없다.
- `application.yml` 의 `custom.jwt.secretPattern` 이 설정 파일에 하드코딩되어 있다 (보안 관점에서 확인 필요).
- `GlobalExceptionHandler` 가 모든 `NoSuchElementException` 을 404-1 로 변환한다. 실제 버그가 404 로 가려질 수 있다.
- `Rq.setCookie` 에서 도메인 `localhost` 와 `Secure` 가 하드코딩되어 있다.

### 정리해야 할 것 (사용자 확인 필요)
- 실수로 H2 를 `/c/...` 경로로 열어 **`C:\c` 디렉터리 트리가 생성되었다** (`C:\c\<임시폴더 경로>` 아래에 파일 2개). 삭제 명령이 안전 검사에 의해 차단되어 제가 지울 수 없었습니다. 프로젝트 밖이므로 직접 삭제해 주시기 바랍니다 (`C:\c`).
- 빌드 산출물 `build/`, `.gradle/` 는 gitignore 대상이다. 임시 서버는 모두 종료했고, 프로젝트 루트의 H2 파일은 삭제했다.
