# s-haiku-t-1 — AI 가 작업 후 낸 보고서 (원문)

## Java → Kotlin 마이그레이션 보고 (C:\ctj\exp\s-haiku-t-1)

시작 04:29:52, 완료 약 04:43 (90분 기한 내). 커밋 `9ebe691 kotlin migration` (push 없음, 원본 커밋 b268b37 위에 생성).

### 1. 바꾼 것
- **빌드 설정** `build.gradle.kts`: `java` 플러그인 → `kotlin("jvm") 2.1.21`, `plugin.spring` / `plugin.jpa` 2.1.21 추가. Lombok 의존성과 annotationProcessor 제거. `allOpen`으로 `@Entity`, `@MappedSuperclass`, `@Embeddable` 을 open 처리(Hibernate LAZY 프록시용). `kotlin-reflect`, `jackson-module-kotlin` 추가(Kotlin 데이터 클래스 역직렬화용). 컴파일러 옵션 `-Xjsr305=strict`, `-java-parameters`. Kotlin stdlib 해석 버전이 2.1.21인 것을 `dependencyInsight`로 확인했습니다.
- **소스**: `src/main/java` 의 Java 파일 32개를 삭제하고 `src/main/kotlin/com/rest1/...` 에 Kotlin 파일 32개를 같은 패키지/디렉터리 구조로 작성했습니다.
- `src/main/resources/*.yml`, `src/test/**` 는 변경 없음 (`git diff b268b37 HEAD -- src/test` 결과 0줄).

### 2. 테스트 결과
- 원본: `./gradlew test` → 전체 37, 통과 37, 실패 0, 에러 0, 스킵 0.
- 변환 후: 전체 37, 통과 37, 실패 0, 에러 0, 스킵 0.

### 3. 동작 동일성 근거와 미확인 사항
근거 (모두 실제 실행으로 확인):
- **HTTP 비교**: 원본 jar와 변환 jar를 각각 test 프로필(인메모리 H2)로 띄우고 같은 시나리오 53건을 Python으로 호출했습니다. 시나리오는 회원가입·중복·검증 오류(400-1 메시지 포함)·잘못된 JSON(400-2)·로그인·쿠키/Bearer 인증·401-1/2/3·403-1/2·글/댓글 CRUD·관리자 API·CORS 프리플라이트를 포함합니다. 상태 코드, Content-Type, X-Frame-Options, Set-Cookie, accessToken 헤더, 본문 원문을 비교했고 **53건 모두 동일**합니다(익명 요청의 무작위 JSESSIONID만 정규화). 그 과정에서 Jackson 필드 순서도 같음을 확인했습니다.
- **DB 스키마**: 기동 로그의 Hibernate DDL 블록 10개를 비교했습니다. 테이블, 컬럼, FK 제약명(FKiq6lerqnw4jde34k91gugrx9 등)이 동일합니다.
- **텍스트 블록**: 401/403 본문과 인증 필터 오류 본문은 HTTP 응답으로 바이트 단위 일치를 확인했습니다. HomeController의 두 HTML 텍스트 블록은 Java 텍스트 블록 규칙을 Python으로 재현해 Kotlin raw string과 대조해 일치를 확인했습니다.

확인하지 못한 것:
- `/`, `/test/fetchData`, `/swagger-ui/**`, `/v3/api-docs` 는 원본과 변환본 모두 익명 요청에 401을 반환하므로(아래 5번) 실제 HTML/OpenAPI 응답은 비교하지 못했습니다. OpenAPI 스키마 이름이 변하지 않았는지는 코드상으로만 확인했습니다(중첩 클래스 이름 유지).
- `dev` 프로필(파일 DB `./db_dev`) 기동은 확인하지 않았습니다. `test` 프로필로만 기동 비교했습니다.
- 원본에 없던 실패 경로(예: Post의 comment id가 null인 상태에서의 조회)는 NPE 동작을 맞추려 `!!`를 썼으나 실제 비교는 하지 않았습니다.
- 서버는 두 개 모두 비교 후 종료했습니다(직접 띄운 java 프로세스만 종료).

### 4. 판단이 필요했던 곳 (원본과 다르게 할 수밖에 없었던 것)
- **가시성**: Kotlin에는 Java의 package-private가 없어 다음을 `public`으로 했습니다. `AuthTokenService.genAccessToken/payloadOrNull` (테스트가 같은 패키지에서 직접 호출하므로 `internal`은 이름 맹글링 때문에 불가), `BaseInitData.initDataRunner`, `SecurityConfig.filterChain`, `corsConfigurationSource` (빈 이름 유지). 외부 HTTP 동작에는 영향 없습니다.
- **요청 DTO 필드를 nullable(`String?`)로 선언하고 `!!`로 사용**: non-null로 두면 필드 누락 시 Jackson이 400-2로 먼저 실패해 원본의 400-1(검증 메시지)과 달라지기 때문입니다. `@Valid`가 null을 먼저 걸러 `!!`는 도달하지 않습니다. `join-missing`, `join-null` 케이스로 확인했습니다.
- **`@field:` 사용-지점 지정**: Kotlin의 기본 annotation target은 Bean Validation이 읽지 않는 파라미터이므로 `@field:NotBlank` 등으로 명시했습니다.
- **제네릭 캐스트**: 원본 컨트롤러는 `RsData<MemberDto>` 를 반환 타입으로 선언하고 raw `new RsData(...)` 로 다른 본문을 담았습니다. springdoc 스키마를 그대로 두기 위해 `RsData<Any?>(...) as RsData<MemberDto>` 형태(unchecked 캐스트)로 유지했습니다.
- **Ut.jwt.toString**: 원본은 `secret.getBytes()`(플랫폼 기본 charset)를, 다른 곳은 UTF-8을 씁니다. Kotlin에서는 `toByteArray()`(UTF-8)로 바꿨는데, JDK 21 기본 charset이 UTF-8이므로 이 환경에서는 동일합니다.
- **`String.isBlank()`**: Kotlin `isBlank()`는 NBSP를 공백으로 보므로 Java와 판정이 다를 수 있어, `Character.isWhitespace` 기반 헬퍼 `isBlankLikeJava()`를 만들었습니다.
- **버리거나 옮긴 것**: 사용되지 않던 `CommentModifyReqBody` record와 `Comment` 의 Lombok `@AllArgsConstructor`(미사용)는 제거했습니다. 기존 `CommentController.modifyItem` 은 원래도 `CommentWriteReqBody` 를 썼습니다. `Rest1Application.main` 은 `companion object` 의 `@JvmStatic` 으로 두어 메인 클래스 이름을 유지했습니다. 파일별 함수명(`getActor`, `getHeader` 등)은 Java 메서드명을 그대로 유지했습니다.
- **엔티티 설계**: no-arg 생성자를 명시하고 필드 초기화가 실행되도록 했습니다(`comments = ArrayList()`). `BaseEntity.createDate/modifyDate` 는 원본(setter 없음)과 같도록 `protected set` 으로 두었습니다(`private set` 은 allOpen 과 충돌).
- **Java `Map.of`**: null 값에서 NPE가 나는 원본 동작을 맞추려 Kotlin의 `mapOf` 대신 `java.util.Map.of` 를 썼습니다.

### 5. 발견했지만 고치지 않은 원본 문제
1. **홈/테스트 페이지/Swagger가 모두 401**: `CustomAuthenticationFilter` 는 `/api/` 가 아닌 경로에서 인증을 설정하지 않는데, `SecurityConfig` 는 `anyRequest().authenticated()` 입니다. 그래서 `/`, `/test/fetchData`, `/swagger-ui/index.html`, `/v3/api-docs` 가 익명으로 접근 불가능합니다(원본에서 이미 401 확인). `HomeController` 와 Swagger UI가 사실상 죽어 있습니다.
2. **기본 자격증명과 시크릿이 설정 파일에 하드코딩**: `BaseInitData` 가 `admin/admin`, `user1~3/1234` 계정과 API 키를 `system`, `admin`, `user1` 같은 값으로 만듭니다. `application.yml` 의 `custom.jwt.secretPattern` 도 고정 문자열입니다. 운영 환경에 그대로 올라가면 위험합니다.
3. **`GlobalExceptionHandler` 가 모든 `NoSuchElementException` 을 404-1로 변환**: `Optional.get()` 에서 나는 예외까지 404로 가려져 서버 내부 버그가 구분되지 않습니다.
4. **`Ut.jwt.toString` 과 나머지의 charset 불일치**: `getBytes()` (플랫폼 기본) 와 `UTF_8` 이 섞여 있습니다. 현재 JDK 21에서는 같지만 일관성이 없습니다.
5. **`Member/Comment/Post` 의 권한 검사가 `Long.equals` 에 의존**: 비영속 객체의 id가 null이면 NPE가 나며 403이 아닌 500이 됩니다. (예: `Post.findCommentById`의 `c.getId().equals(commentId)`.)
6. **`ApiV1CommentController.getItems` 에 `@Transactional(readOnly=true)` 없음**: 지연 로딩이 open-in-view에 기대고 있습니다. 동작은 하지만 서비스 계층 밖에서 로딩되는 구조입니다.
7. **쿠키 `domain("localhost")` 와 `Secure` 고정**: 다른 호스트 배포나 평문 HTTP 환경에서 쿠키 동작이 제한됩니다(dev 전용으로 보입니다).
8. **`CustomAuthenticationFilter` 가 `Authorization` 헤더 이외의 경로에서도 `SecurityContext` 를 비우지 않음**: 인증 상태 관리가 요청 간에 명확하지 않습니다(단, Spring 기본 동작에 기대므로 직접 장애는 확인하지 못했습니다).

참고: 작업 중 `git` 이 Windows 줄바꿈(CRLF) 경고를 출력했는데 `.gitattributes` 설정에 따른 것이며 커밋 내용에는 영향이 없습니다.
