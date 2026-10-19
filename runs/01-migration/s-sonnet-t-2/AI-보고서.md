# s-sonnet-t-2 — AI 가 작업 후 낸 보고서 (원문)

Java에서 Kotlin으로의 마이그레이션은 끝내고 `kotlin migration`으로 커밋했다(edf8096). 다만 비교용으로 띄운 java 프로세스 2개를 종료하지 못해 아직 떠 있다.

시작 04:43, 종료 약 04:51이라 90분 제한과는 무관하다.

## 1. 바꾼 것
- **빌드 설정** (`build.gradle.kts`)
  - Kotlin `jvm`과 `plugin.spring`을 1.9.25로 추가했다. Spring Boot 3.5.5가 관리하는 버전이다.
  - `allOpen`에 `@Entity`, `@MappedSuperclass`, `@Embeddable`을 등록했다. LAZY 프록시 때문이다.
  - `-Xjsr305=strict`를 켰다.
  - `jackson-module-kotlin`과 `kotlin-reflect`를 추가했다.
  - Lombok 의존성과 `configurations` 블록은 제거했다. `src/test`에서 Lombok을 쓰지 않는다.
  - `jacoco`, Java 21 toolchain, 나머지 의존성은 그대로다.
- **파일**
  - Java 31개를 삭제하고 `src/main/kotlin`에 Kotlin 31개를 만들었다. 패키지와 클래스 이름은 같다.
  - `src/main/resources`와 `src/test`는 건드리지 않았다.
  - `git diff HEAD~1 --stat`은 64개 파일 변경, +1659/-1730줄이다.

## 2. 테스트 결과
| | 전체 | 통과 | 실패 |
|---|---|---|---|
| 원본 | 37 | 37 | 0 |
| 변환 후 | 37 | 37 | 0 |

`./gradlew test`로 돌렸고, 변환 후 빌드에 컴파일 경고는 없었다.

## 3. 동작이 같다고 본 근거와 확인하지 못한 것
- **근거 1, 실서버 비교.** 원본(`git archive HEAD`로 따로 빌드한 jar)과 변환본 jar를 `test` 프로필로 포트 18081·18082에 띄웠다. Python으로 같은 요청 80개를 보내 상태 코드, 본문, 주요 헤더(Set-Cookie, Content-Type, Vary 등)를 비교했고 차이는 0건이었다.
  - 날짜, JWT, UUID, JSESSIONID는 비교에서 마스킹했다.
  - 요청은 글·댓글 CRUD, 권한 403/401, 검증 400-1, 깨진 JSON 400-2, 회원 가입·로그인·me·로그아웃, API 키·쿠키·토큰 조합, 관리자 API, CORS 등이다.
  - JWT 헤더와 payload 구조도 같았다.
- **근거 2, DB 스키마.** 두 앱의 Hibernate 시작 로그에서 `create table` 62줄이 동일했다.
- **확인하지 못한 것**
  - `/v3/api-docs`와 swagger-ui 응답은 비교하지 못했다. 인증 없이는 두 앱 모두 401이었고, 인증을 붙여 본문까지 비교하지는 않았다. 엔드포인트와 `@Operation`/`@Tag` 문서 메타데이터는 원본과 같게 옮겼다.
  - 홈 페이지(`/`)와 `/test/fetchData`는 두 앱 모두 401이라 본문을 실제로 비교하지 못했다. 문자열은 Java 텍스트 블록 규칙에 맞춰 손으로 옮겼다.
  - 파일 DB를 쓰는 `dev` 프로필은 실행하지 않았다. 설정 파일은 그대로다.
  - 원본의 수동 `SpringApplication` 실행 경로(`Rest1Application.main`)는 `companion object`의 `@JvmStatic main`으로 유지했고, `bootJar`로 만든 jar 실행으로 확인했다. `bootRun`은 써 보지 않았다.

## 4. 판단이 필요했던 곳
- **가시성.** `AuthTokenService.genAccessToken`과 `payloadOrNull`은 원본이 package-private이었다. Kotlin에는 그 가시성이 없고, 같은 패키지의 Java 테스트가 호출하므로 public으로 했다.
- **요청 DTO.**
  - Java record의 `@NotBlank`/`@Size`를 `@field:`로 붙였다. 파라미터에만 붙이면 검증이 적용되지 않는다.
  - 필드를 `String?`로 두었다. non-null이면 필드 누락 시 400-1 대신 400-2가 나와 원본과 달라진다. 컨트롤러에서는 검증 통과 뒤 `!!`로 쓴다.
- **엔티티.** 필수 필드는 `lateinit var`, 원래 null일 수 있는 `password`와 `apiKey`는 `String?`로 했다. setter는 `protected set`이다. open 프로퍼티는 private setter가 안 되기 때문이다.
- **JSON 구조 유지.** 컨트롤러가 `RsData<MemberDto>`를 선언하고 실제로는 `JoinResBody` 등을 담던 raw 타입 우회는 `RsData<Any>`를 거친 unchecked 캐스트로 같은 구조를 유지했다.
- **공백 판정.** Kotlin의 `isBlank()`는 NBSP 같은 문자도 공백으로 봐서 Java와 다르다. 그래서 `Ut.kt`에 `isBlankJava()`(Java와 같은 `Character.isWhitespace` 기준)를 추가해 Rq와 필터에서 썼다.
- **text block.** Java 텍스트 블록의 들여쓰기 제거 결과를 Python으로 계산해서 같은 문자열로 옮겼다. 줄바꿈은 `\n`이고 끝에도 줄바꿈이 있다.
- **Ut.jwt.** `object Ut { object jwt { @JvmStatic ... } }` 구조라서 Java 테스트의 `Ut.jwt.toString(...)`, `Ut.jwt.isValid(...)` 호출이 그대로 컴파일된다.
- **미사용 요소.** 컨트롤러의 미사용 생성자 인자(`memberService`, `authTokenService`, `Rq.memberService`)와 미사용 `CommentModifyReqBody`는 원본 구조를 지키려고 남겼다.
- **프로세스 정리 실패.** 비교용으로 띄운 java 프로세스 PID 42832(포트 18081, 원본)와 40724(포트 18082, 변환본)가 아직 살아 있다.
  - `taskkill //PID <pid> //F`는 종료 코드 0에 출력 없이 끝났는데 프로세스는 계속 떠 있었다. 차단 메시지가 없어서 막힌 것인지 알 수 없었다.
  - 규칙상 다른 도구로 우회하지 않고 멈췄다. 직접 종료해 주기 바란다.
  - 둘 다 `--spring.profiles.active=test`(인메모리 H2)라서 디스크에 데이터는 남지 않는다. 원본 빌드본은 스크래치 폴더 `<임시폴더>\orig`(저장소 밖)에 있다.

## 5. 발견했지만 고치지 않은 원본 문제
- **로그인 필요 401과 권한 없음 403 응답의 한글이 깨진다.** `SecurityConfig`의 entry point와 access-denied 핸들러는 charset 없이 `application/json`으로 쓴다. 응답이 `application/json;charset=ISO-8859-1`이라 한글이 `???`로 나온다. `CustomAuthenticationFilter`가 직접 쓰는 401-2/401-3 메시지도 같다. 원본과 변환본이 똑같이 깨졌다.
- **홈(`/`), `/test/fetchData`, `/v3/api-docs`, swagger-ui가 인증 없이는 모두 401이다.** `anyRequest().authenticated()` 때문이다. 홈 페이지의 "API 문서로 이동" 링크가 로그인 없이는 쓸모없다.
- **로그아웃이 `apiKey` 쿠키만 지우고 `accessToken` 쿠키는 남긴다.**
- **`Authorization: Bearer <apiKey> <무효토큰>`을 보내면 새 `accessToken`이 발급된다.** 이때 apiKey만 맞으면 통과한다. 의도일 수 있지만 토큰 검증이 사실상 우회된다.
- **글·댓글 작성자가 아니어도 존재 확인 전에 권한을 검사하는 순서가 일관되지 않는다.** 예를 들어 없는 댓글에 PUT하면 403 대신 404다. 큰 문제는 아니다.
- **`ApiV1CommentController.modifyItem`이 `CommentModifyReqBody` 대신 `CommentWriteReqBody`를 받는다.** `CommentModifyReqBody`는 미사용이다.
- **`ApiV1MemberController.login`이 응답 `memberDto` 외에 `apiKey`와 `accessToken`을 본문에도 노출한다.** 쿠키가 HttpOnly여도 본문에는 평문이다.
- **쿠키가 `Secure`, `Domain=localhost`로 고정이다.** `localhost` 외의 도메인에서는 쿠키가 동작하지 않는다.
- **`createDate`/`modifyDate`가 JSON에서 타임존 없는 `LocalDateTime`이다.**
- **`BaseInitData.work2`가 `user3`을 조회하고 쓰지 않으며 `post3`도 변수만 선언한다.**
- **`MemberService.join`이 아이디 중복을 조회로 확인하고 저장한다.** 동시 요청에서는 유니크 제약 예외가 500으로 나올 수 있다.
- **`Ut.jwt.toString`이 `secret.getBytes()`에 charset을 지정하지 않는다.** JDK 18 이상에서는 UTF-8이 기본이라 지금은 문제없다.
- **`ApiV1CommentController.getItems`는 `@Transactional` 없이 지연 로딩에 의존한다.** open-in-view에 기대고 있다.

주요 경로는 `C:\ctj\exp\s-sonnet-t-2\build.gradle.kts`와 `C:\ctj\exp\s-sonnet-t-2\src\main\kotlin\com\rest1\`이다.
