# s-sonnet-n-2 — AI 가 작업 후 낸 보고서 (원문)

Java에서 Kotlin으로 마이그레이션해 `kotlin migration` 커밋을 만들었고(ccd55f5), `./gradlew build`는 성공했다. HTTP 호출 105건의 응답과 DB 스키마가 원본과 같았다. 소요 시간은 약 12분이다(05:26에 시작해 05:38에 끝났다).

## 1. 바꾼 것
- **빌드 설정(`build.gradle.kts`)**
  - `java` 플러그인 자리에 Kotlin 플러그인 `jvm`, `plugin.spring`, `plugin.allopen`을 넣었다. 버전은 Boot 3.5.5가 관리하는 Kotlin 1.9.25이다.
  - `kotlin-reflect`와 `jackson-module-kotlin`을 추가했고, Lombok(`compileOnly`, `annotationProcessor`)과 `configurations` 블록을 지웠다.
  - 컴파일러 옵션에 `-java-parameters`를 넣었다. `@PathVariable`과 Spring Data 파생 쿼리에 필요하다.
  - `allOpen`에 `@Entity`, `@MappedSuperclass`, `@Embeddable`을 지정했다. Hibernate 지연 로딩 프록시 때문이다.
- **파일**: `src/main/java`의 `.java` 32개를 `src/main/kotlin`의 `.kt` 32개로 옮겼고 Java 파일은 없앴다.
- **설정**: `application*.yml`은 그대로다. Kotlin 외 의존성 버전도 그대로다.

## 2. 같다고 판단한 근거와 확인하지 못한 것
**근거**
- 원본(HEAD)의 bootJar를 먼저 빌드해 기준선으로 삼았다. 같은 Python 시나리오(105건)를 원본과 Kotlin 앱에 똑같이 실행해 상태 코드, 헤더 전체, 본문을 비교했다.
  - 시나리오는 각각 새 H2 파일 DB(dev 프로파일)로 돌렸고, localhost에서만 호출했다.
  - 날짜, UUID, JWT, JSESSIONID는 `<DATE>`, `<UUID>`, `<JWT>`, `<X>`처럼 마스킹했다.
  - 원본을 두 번 돌려 결과가 같음을 확인해, 비교 기준이 흔들리지 않음을 확인했다.
- 시나리오에 넣은 것:
  - 게시글, 댓글, 회원의 CRUD와 권한 오류(403)
  - 검증 오류(400-1)와 잘못된 JSON(400-2)
  - 헤더와 쿠키 인증, 만료되거나 깨진 accessToken의 재발급
  - 관리자 전용 경로, CORS preflight, 없는 경로와 허용되지 않는 메서드
  - 로그인 JWT를 디코딩해 claim 이름과 유효시간(1800초)까지 비교
- 결과가 일치한 항목:
  - 105건 응답이 바이트 단위로 같았다(`cmp` IDENTICAL).
  - Hibernate이 실행한 SQL 문 종류와 순서(78개)가 같았다.
  - H2 스키마 덤프(`Script -nodata`)가 `CREATE USER` 줄(솔트 값)만 빼면 같았다. 컬럼, 제약, FK, 컬럼 순서가 모두 같았다.
- 원본에서는 `/`, `/test/fetchData`, `/v3/api-docs*`, swagger-ui가 인증 때문에 401이 나서 HTTP로 확인할 수 없었다.
  - 그래서 scratchpad의 임시 복사본 두 개(원본 HEAD, Kotlin)에서만 SecurityConfig에 이 경로들의 `permitAll`을 추가해 비교했다. 저장소에는 반영하지 않았다.
  - HomeController의 HTML 두 개는 바이트 단위로 같았다.
  - OpenAPI JSON(전체, apiV1, home, swagger-config)은 같았다. 처음에는 `required`가 달라서 아래 3번처럼 고쳤다.

**확인하지 못한 것**
- 이 저장소에는 테스트가 없고, 위 비교는 내가 만든 시나리오 범위에 한정된다.
- 실제 브라우저의 CORS와 쿠키 동작은 보지 않았다. 쿠키는 `Set-Cookie` 헤더 문자열이 같은 것까지만 확인했다. Secure와 Domain=localhost 때문에 브라우저가 저장하는지는 모른다.
- `test` 프로파일(메모리 DB)로 기동하는 것은 돌리지 않았다. `dev` 프로파일 파일 DB만 썼다.
- `./gradlew build`에는 테스트 소스가 없어 실행된 테스트가 없다.
- JWT 서명 바이트를 원본 토큰과 직접 비교하지는 않았다. 클레임만 비교했다. `Date`와 `iat`가 매번 달라서 비교할 수 없다.

## 3. 판단이 필요했던 곳
- **요청 DTO 필드를 `String?`로 두었다.** 이유는 아래와 같다.
  - Java record는 필드가 빠지면 null이 되어 `@NotBlank`가 400-1을 낸다.
  - Kotlin non-null이면 jackson-module-kotlin이 먼저 실패해 400-2가 나온다. 응답 코드가 달라지는 것을 막으려는 것이다.
  - 검증 어노테이션은 `@field:`로 붙였다.
- **엔티티 필드도 전부 nullable로 두었다.** Java에서 null이 가능했던 값(예: `Member(id, username, nickname)`의 password와 apiKey)을 그대로 두기 위해서다.
  - 엔티티 setter는 `protected set`이다. open 프로퍼티는 `private set`을 쓸 수 없다.
  - no-arg 생성자는 kotlin-jpa 플러그인 없이 직접 정의했다. 그래서 프로퍼티 초기화(`comments = ArrayList()`)가 Java처럼 실행된다.
- **join, login, me의 반환 타입.**
  - 원본은 raw type `RsData<MemberDto>`를 선언하고 실제 data는 `JoinResBody`, `LoginResBody`, `MeResBody`였다.
  - OpenAPI 문서의 선언 타입까지 같게 하려고 `as RsData<MemberDto>` 비검사 캐스트로 유지했다.
- **OpenAPI `required` 차이를 `@Schema`로 막았다.**
  - springdoc이 Kotlin non-null 타입을 `required`로 문서화했다.
  - `RsData.resultCode`, `RsData.msg`, `CountResBody.totalCount`에 `@field:Schema(requiredMode = NOT_REQUIRED)`를 붙여 원본과 같게 맞췄다.
- **`Ut.isBlank` 헬퍼를 추가했다.** Kotlin의 `isBlank()`는 NBSP 같은 문자를 공백으로 보지만 Java `String.isBlank()`는 그렇지 않다. Rq와 인증 필터에서 Java 규칙을 쓰려고 `Character.isWhitespace` 기준으로 만들었다.
- **텍스트 블록은 Kotlin 문자열로 바꿨다.**
  - Java 텍스트 블록과 같은 줄바꿈과 들여쓰기가 되도록 맞췄다.
  - 보안 핸들러 JSON은 들여쓰기 4칸까지 같다.
  - HomeController는 `trimMargin()` 뒤에 `"\n"`을 붙였다.
- **작은 차이들.**
  - `AuthTokenService`의 `@Value`는 필드 주입에서 생성자 주입으로 바꿨다.
  - 원본의 package-private 메서드(`genAccessToken`, `payloadOrNull`)는 public이 되었다.
  - `Rq`가 주입받던 `MemberService`는 쓰이지 않아 뺐다.
  - 쓰이지 않던 Lombok `@AllArgsConstructor`(Comment)는 만들지 않았다.
  - `CustomAuthenticationFilter`의 `catch (Exception e) { throw e; }`는 아무 효과가 없어 뺐다.
  - 컨트롤러에서 `Optional.get()`을 그대로 써서 `NoSuchElementException`이 404-1이 되는 동작은 유지했다.
- **빌드 경고가 2개 남아 있다.** `BaseInitData`의 `member3`, `post3`는 원본에서도 쓰이지 않던 변수다. 그대로 두었다.

## 4. 발견했지만 고치지 않은 원본 문제
- **`/`, `/test/fetchData`, swagger-ui, `/v3/api-docs`가 인증 없이는 401이다.** 인증 필터는 `/api/` 밖의 경로를 건너뛰고, `anyRequest().authenticated()`가 막기 때문에 로그인으로도 열 수 없다. 홈 화면과 API 문서를 쓸 수 없다.
- **보안 핸들러의 한글 응답이 깨진다.**
  - 401-1("로그인 후 이용해주세요.")과 403-1("권한이 없습니다.")이 `Content-Type: application/json;charset=ISO-8859-1`로 나가 한글이 `?`로 바뀐다.
  - 필터의 ServiceException 응답도 같은 방식(`setContentType`과 `getWriter`)으로 나가므로 같은 문제가 있을 가능성이 크다. 이 경로는 시나리오에서 한글 응답을 확인하지 않았다.
  - 이 응답은 들여쓰기 공백이 들어간 JSON 문자열이기도 하다.
- **`PUT /api/v1/posts/{id}/comments/{id}`가 `CommentModifyReqBody`가 아니라 `CommentWriteReqBody`를 받는다.** `CommentModifyReqBody`는 정의만 있고 쓰이지 않는다.
- **`ApiV1PostController.deleteItem`에 `@Transactional`이 없다.** 같은 컨트롤러의 다른 쓰기 API는 모두 붙어 있다.
- **쿠키가 `Domain=localhost; Secure`로 고정되어 있다.** `localhost` 이외 환경에서는 브라우저가 거부할 수 있다.
- **logout이 `apiKey` 쿠키만 지운다.** `accessToken` 쿠키는 남는다.
- **application.yml에 `thymeleaf` 설정이 있지만 Thymeleaf 의존성은 없다.**
- **인증용 JWT 비밀키가 application.yml에 평문으로 있다.**
- **일반 회원 가입 때 비밀번호 길이 상한 30자와 BCrypt의 72바이트 제한은 따로 확인하지 않았다.** (확인하지 못한 항목이며 버그로 확정한 것은 아니다.)
- **`BaseInitData`의 `member3`, `post3`는 쓰이지 않는 변수다.**

산출물 위치는 `C:\ctj\exp\s-sonnet-n-2`이다. 검증 스크립트와 결과 JSON은 scratchpad `<임시폴더>`의 `scen.py`, `run.sh`, `out-*.json`에 있고, 저장소에는 넣지 않았다. 직접 띄운 앱 프로세스는 모두 종료했다.
