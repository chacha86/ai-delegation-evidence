# s-haiku-n-1 — AI 가 작업 후 낸 보고서 (원문)

## 작업 결과 요약
- 프로젝트: `C:\ctj\exp\s-haiku-n-1`
- 커밋: `4758c0d kotlin migration` (로컬만, push 안 함). 커밋 메시지에 Co-Authored-By / Claude-Session 트레일러를 붙였다.
- 시작 03:51, 종료 약 04:06 (90분 이내).
- 프로젝트 폴더 밖은 건드리지 않았다. 검증용 원본 빌드와 비교 스크립트는 scratchpad(`<임시폴더>`)에만 두었다. 테스트 서버는 모두 종료했다.

## 1. 바꾼 것
- 빌드 설정 (`build.gradle.kts`): `java` 플러그인 → `kotlin("jvm") 1.9.25`. 1.9.25는 Spring Boot 3.5.5 BOM이 관리하는 `kotlin.version`과 같다. `plugin.spring`, `plugin.jpa` 추가. `allOpen`(Entity, MappedSuperclass, Embeddable, ControllerAdvice), `noArg`(invokeInitializers=true), `-Xjsr305=strict`. `jackson-module-kotlin`과 `kotlin-reflect` 추가(BOM 관리 버전). Lombok 의존성과 annotationProcessor 설정 제거.
- 소스: `src/main/java` 32개 `.java` 삭제, `src/main/kotlin` 32개 `.kt` 신규 생성. 패키지와 파일 구조는 그대로다.
- `application*.yml`, gradle wrapper는 변경 없음.
- `./gradlew build` 성공 (테스트 코드 없음).

## 2. 동작이 원본과 같다고 판단한 근거와 확인하지 못한 것
근거:
- 원본(HEAD)을 임시 폴더에 풀어 bootJar를 만들었다. 원본과 신규를 각각 localhost 포트 18101, 18102에 띄우고 빈 H2 DB에서 같은 73개 요청 시나리오를 보냈다. 시나리오는 가입, 중복(409-1), 검증 실패(400-1 메시지), 잘못된 JSON(400-2), 로그인 실패(401-1/2), 쿠키·Bearer·apiKey 인증, 토큰 폴백과 재발급, 401-1/2/3, 작성·수정·삭제 권한(403-1/2), 404-1, 관리자 경로, 로그아웃을 포함한다. 상태 코드, 응답 헤더(Set-Cookie 포함), 응답 본문을 비교했고 73/73 일치했다. 토큰, UUID, 타임스탬프, JSESSIONID는 정규화했다.
- H2 스키마 DDL 덤프를 두 DB에서 떠서 비교했다. 동일했다.
- Java text block 규칙을 파이썬으로 재현해 HomeController의 두 HTML 문자열이 Kotlin raw string과 같은지 확인했다.
- 오류 응답 본문(401, 403)은 바이트 단위로 같다. 비교 중 403 들여쓰기 차이를 찾아 고쳤다.

확인하지 못한 것:
- `/v3/api-docs`(OpenAPI) 내용 비교. 두 버전 모두 익명 접근에 401이라 확인할 수 없었다. 반환 타입은 원본 선언을 그대로 유지했다.
- `/`, `/test/fetchData` HTML 응답. 두 버전 모두 401이라 HTTP로 비교하지 못했다. 텍스트만 재현으로 확인했다.
- 존재하지 않는 경로, 405, 415, 경로 변수 타입 오류의 Spring 기본 에러 응답. 두 버전 모두 401이라 실질적으로 비교되지 않았다(아래 4번 참고).
- Swagger UI 렌더링, CORS 실제 동작, 동시성, 성능, 로그 출력.

## 3. 판단이 필요했던 곳 (원본과 다르게 했거나 다르게 할 수밖에 없었던 것)
- 요청 DTO 필드는 `String?`로 두고 `@field:NotBlank`, `@field:Size`를 유지했다. 컨트롤러에서는 검증 통과 후 `!!`를 쓴다. non-null로 두면 누락 또는 null 입력이 400-1 대신 400-2로 바뀌기 때문이다.
- 원본은 `RsData<MemberDto>`로 선언하고 실제로는 raw type으로 `JoinResBody`, `LoginResBody`, `MeResBody`를 담는다. 선언 타입을 유지하려고 unchecked cast 헬퍼 `castData`를 썼다. JSON과 OpenAPI 선언 모두 원본과 같다.
- JSON 본문 문자열(401/403, 필터의 에러 본문, HomeController HTML)은 text block 대신 명시적 문자열로 옮겨 바이트 단위로 맞췄다.
- `String.isBlank()` 대신 Java와 같은 기준의 `isBlankJava()`를 `standard/ut/Ut.kt`에 두었다. Kotlin의 isBlank는 NBSP를 공백으로 보기 때문이다.
- `@PathVariable`에 이름을 명시했다(파라미터 이름 디스커버리에 의존하지 않도록).
- 엔티티 setter는 `protected set`으로 했다. allOpen 때문에 `private set`이 허용되지 않는다. Hibernate는 필드 접근이라 영향이 없다.
- `Post.comments`의 초기화 동작은 noArg `invokeInitializers=true`로 원본과 맞췄다.
- 접근 제한: Java package-private 메서드(예: `AuthTokenService.genAccessToken`, `@Bean` 메서드)는 Kotlin에 대응 개념이 없어 public으로 바꿨다. HTTP 동작에는 영향이 없다.
- 사용되지 않던 코드를 제거했다: `CommentModifyReqBody`, `Comment`의 미사용 `@AllArgsConstructor`, 미사용 주입 필드(`Rq.memberService`, 컨트롤러들의 `memberService`, `authTokenService`), 필터의 no-op `catch (Exception e) { throw e; }`.
- Kotlin 1.9.25를 선택했다. Spring Boot 3.5.5 BOM의 `kotlin.version`에 맞췄고, Kotlin 2.x는 쓰지 않았다.

## 4. 발견했지만 고치지 않은 원본 문제
1. 익명 요청으로 존재하지 않는 경로, 405, 415, `/api/v1/posts/abc` 같은 경로 타입 오류를 보내면 404, 405, 415, 400 대신 401이 나온다. `anyRequest().authenticated()`에 에러 디스패치(`/error`)가 걸리기 때문이다. 실측: `GET /nonexistent`, `GET /api/v1/nothing`, `PATCH /api/v1/posts`, `POST /api/v1/posts`(text/plain), `GET /api/v1/posts/abc` 모두 401.
2. `GET /`(홈), `/test/fetchData`, `/swagger-ui/**`, `/v3/api-docs` 등이 익명 접근 시 401이다. 홈 화면에 Swagger 링크가 있어도 문서에 접근할 수 없다. 의도된 동작인지 확인이 필요하다.
3. `ApiV1MemberController`의 `join`, `login`, `me`는 선언 반환 타입 `RsData<MemberDto>`와 실제 data 타입이 다르다. 그래서 OpenAPI 스키마가 틀리다.
4. `DELETE /api/v1/members/logout`은 `apiKey` 쿠키만 지우고 `accessToken` 쿠키는 지우지 않는다. 실측 응답의 Set-Cookie도 apiKey만 있다.
5. `corsConfigurationSource` 빈은 정의만 되어 있고 `http.cors()`가 호출되지 않아 CORS 설정이 적용되지 않는다.
6. 초기 데이터의 apiKey가 username과 같은 고정값("admin", "user1" 등)이다. `Member.isAdmin()`은 username이 "admin"인지로 관리자를 판단한다. 개발용이라도 권한이 이름으로 결정되는 것은 위험하다.
7. `ApiV1CommentController.getItems()`는 `@Transactional` 없이 lazy 컬렉션을 읽는다. 현재는 Spring Boot 기본 OSIV에 의존해 동작한다.
8. `application.yml`에 `hibernate.ddl-auto: update`와 TRACE 로깅이 켜져 있다. 개발용 설정으로 보이지만 운영에 쓰면 안 된다.
