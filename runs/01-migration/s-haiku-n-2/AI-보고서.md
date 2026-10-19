# s-haiku-n-2 — AI 가 작업 후 낸 보고서 (원문)

## Java → Kotlin 마이그레이션 보고 (C:\ctj\exp\s-haiku-n-2)

커밋: `eefdc05 kotlin migration` (main, 원격 push 안 함). 시작 05:39:36, 커밋 05:50 (90분 이내).

### 1. 바꾼 것
- **build.gradle.kts**: `java` 플러그인 제거, `kotlin("jvm")` / `plugin.spring` / `plugin.jpa` 1.9.25 추가 (Spring Boot 3.5.5가 관리하는 Kotlin 버전과 동일). `allOpen` 으로 `@Entity`·`@MappedSuperclass`·`@Embeddable` open 처리 (지연 로딩 프록시용). `-Xjsr305=strict`. Lombok 제거. `kotlin-reflect`, `jackson-module-kotlin` 추가. 나머지 의존성·버전은 그대로.
- **소스**: `src/main/java` 의 Java 파일 32개를 `src/main/kotlin` 의 Kotlin 파일 32개로 1:1 이전 (같은 패키지 경로). `Rest1Application.kt` 의 `main` 은 최상위 함수. `src/main/resources` 와 `gradlew` 는 변경 없음.
- `./gradlew build` 성공. 컴파일 경고 없음. 저장소에 테스트 코드는 없다.

### 2. 동작이 같다고 판단한 근거, 그리고 확인하지 못한 것
**확인한 것**
- 원본 jar 를 먼저 빌드해 기준선으로 삼고, 두 앱을 localhost 에서 각각 띄웠다 (포트 18080 원본, 18081 Kotlin, H2 는 `jdbc:h2:mem:` 로 커맨드라인 override). 같은 Python 스크립트로 93개 요청을 보냈다. 범위는 공개 조회, 404, 로그인·가입의 성공/검증 실패/잘못된 JSON/null 바디, 인증 variant 전부 (Bearer, Cookie, JWT, 만료 토큰 갱신, 잘못된 키), 작성자 아님 403-1/403-2, 관리자 권한, 로그아웃, CORS 이다.
- 결과: 93개 응답의 status, 헤더 (Set-Cookie 속성, accessToken 헤더 포함), 바디가 **바이트 단위로 일치**. 비교 전에 타임스탬프, JWT, UUID, JSESSIONID 만 정규화했다. 스크립트: `<임시폴더>\probe.py`. 결과 파일: 같은 scratchpad 의 `orig.json`, `kt.json`.
- 검증 어노테이션(`@NotBlank`, `@Size`)이 DTO 인스턴스 검사에 실제로 적용되는지 확인했다 (필드 누락·null·빈 값에서 원본과 같은 400-1 메시지).
- HTML 텍스트 블록 2개는 Java 텍스트 블록 규칙과 Kotlin `trimIndent()` 를 같은 소스에 적용해 비교했다. 같다.
- 테스트 후 내가 띄운 두 프로세스만 종료했다 (PID 로 확인). 빌드 산출물 `build/baseline` 은 삭제.

**확인하지 못한 것**
- Swagger/OpenAPI 문서(`/v3/api-docs`)의 내용. 인증 없이는 접근 불가라 비교하지 못했다. springdoc 어노테이션은 같은 값으로 옮겼다.
- Hibernate 가 실제로 내는 SQL 과 DDL 비교. 기동은 되고 CRUD 와 지연 로딩 결과는 같았다.
- `dev` 프로필의 파일 DB(`./db_dev`) 로 기동한 것. 테스트는 mem DB 로 했다 (URL override 외 설정은 동일).
- HTML 페이지(`/`, `/test/fetchData`)의 실제 HTTP 응답. 두 경로 모두 원본에서도 인증 필요로 401 이라 접근할 수 없다.
- 동시 요청, 성능.

### 3. 판단이 필요했던 곳 (원본과 다르게 쓴 것 포함)
- **DTO 필드를 `String?` 으로 둠 + `@field:NotBlank` 등 사용.** 이렇게 해야 누락 필드가 Jackson 역직렬화 오류 (400-2) 가 아니라 검증 오류 (400-1) 로 처리된다. non-null 로 두면 응답 코드가 바뀐다. `@param:` 이 아니라 `@field:` 를 쓴 이유도 같다 (Bean Validation 은 필드를 검사한다). probe 로 확인했다.
- **엔티티**: id 와 날짜는 `Long?` / `LocalDateTime?` (protected set). 필수 연관·문자열은 `lateinit var`. `Post.findCommentById` 등은 `Optional` 반환을 그대로 둬서 `.get()` 의 NoSuchElementException → 404-1 경로를 유지했다.
- **`isBlank`**: Kotlin `isBlank()` 는 NBSP 를 공백으로 보고 Java 는 보지 않는다. Java 와 맞추는 헬퍼 `String.isBlankJava()` 를 `Ut.kt` 에 두었다.
- **`Map.of` → `mapOf`**: `Map.of` 는 null 을 거부하고 순서가 무작위다. 현재 값은 모두 non-null 이라 JWT 내용은 같다.
- `@Value` 는 `AuthTokenService` 생성자 파라미터로 옮겼다. 문자열 `${...}` 는 Kotlin 템플릿과 충돌하므로 `\$` 로 이스케이프했다.
- **Kotlin 으로 옮기며 제거한 것** (HTTP 동작 영향 없음): Lombok 이 만든 미사용 `Comment(content, post, author)` 생성자, 미사용 주입 필드 (`Rq.memberService`, `ApiV1MemberController.authTokenService`, 컨트롤러들의 `memberService`), `CustomAuthenticationFilter` 의 "잡아서 다시 던지기" 중복 catch. 미사용 DTO `CommentModifyReqBody` 와 `BaseInitData` 의 `member3` 는 원본대로 남겼다.
- `RsData` 는 `data class` 가 아닌 일반 class 로 두고 `data` 기본값을 null 로 했다 (2-인자 생성자 대체).

### 4. 발견했지만 고치지 않은 원본 문제
1. **로그아웃이 `accessToken` 쿠키를 지우지 않는다.** `ApiV1MemberController.logout` 이 `apiKey` 만 `deleteCookie` 한다.
2. **JWT 가 유효하면 `apiKey` 를 검사하지 않는다.** `Bearer badkey <유효한 토큰>` 이 200 을 반환한다 (probe 로 확인).
3. **홈 페이지와 `test/fetchData` 가 도달 불가.** `anyRequest().authenticated()` 이고 필터는 `/api/` 경로만 인증하므로 항상 401 이다. `HomeController` 의 HTML 은 사실상 죽은 코드다.
4. **보안 설정 노출**: `application.yml` 에 JWT secret 이 평문으로 있고, `BaseInitData` 가 `1234` 비밀번호와 `user1`, `admin` 같은 예측 가능한 API 키로 계정을 만든다. admin 권한은 username 문자열 `"admin"` 으로 판정한다.
5. `GlobalExceptionHandler` 가 **모든** `NoSuchElementException` 을 404-1 로 바꾼다. 의도하지 않은 곳에서 나는 예외도 404 가 된다.
6. 로그인 비밀번호 불일치가 `401-2` 를 쓴다. 같은 코드가 Bearer 형식 오류에도 쓰여 구분이 안 된다.
7. `CustomAuthenticationFilter` 가 에러 JSON 을 문자열 포맷으로 만든다 (이스케이프 없음). 현재 메시지에는 특수문자가 없어 당장은 문제가 없다.
8. `Ut.jwt.toString` 은 `getBytes()` (기본 charset), `isValid` 는 UTF-8 을 쓴다. JDK 21 기본이 UTF-8 이라 실해는 없다.

참고: git 설정 `core.autocrlf=true` 때문에 `git add` 시 LF→CRLF 경고가 나온다. 저장소 동작에는 영향 없다.
