# s-haiku-g-2 — AI 가 작업 후 낸 보고서 (원문)

## Java → Kotlin 마이그레이션 보고서 (C:\ctj\exp\s-haiku-g-2)

작업 시각: 12:22 시작, 12:37 커밋 완료 (90분 한도 내). 커밋: `e0966a5 docs: migration decisions`, `29bc226 kotlin migration`. 작업 트리 깨끗함. push 없음.

### 1. 0단계 결정 (ADR 제목)
사용자가 응답할 수 없어 grilling 라운드의 추천 답을 수락된 것으로 보고 진행했다 (C:\ctj\exp\s-haiku-g-2\docs\grilling.md 에 15개 질문과 추천 답 기록).
- ADR 0001 Kotlin 버전은 Spring Boot 3.5.5 가 관리하는 1.9.25 로 맞춘다 (`spring-boot-dependencies-3.5.5.pom` 의 kotlin.version 확인)
- ADR 0002 Optional 반환과 `.get()` 을 유지해 NoSuchElementException → 404-1 을 보존한다 (`!!` 금지)
- ADR 0003 엔티티는 일반 class, 널 허용 타입 유지, DDL 불변 (data class 금지)
- ADR 0004 (마이그레이션 중 추가) 요청 본문 DTO 필드는 `String?` + `@field:` 검증 어노테이션
- 결정 외 추가: GLOSSARY.md 생성 (회원, API 키, 액세스 토큰, 관리자, 글, 댓글, 결과 코드, 응답 데이터, 서비스 예외)

결정과 다르게 한 것 / 결정 밖에서 정한 것:
- `kotlin("plugin.allopen")` 을 추가하고 `jakarta.persistence.Entity/MappedSuperclass/Embeddable` 을 allOpen 대상으로 지정했다. LAZY ManyToOne 프록시가 동작하려면 필요하며, grilling 에는 없던 항목이다.
- `@PathVariable` 에 이름을 명시했다 (Kotlin 이 파라미터 이름을 Java 처럼 내보내지 않으므로).
- `join`, `login`, `me` 의 반환 타입은 원본 Java 의 선언 타입 `RsData<MemberDto>` 를 유지하려고 `as RsData<MemberDto>` 비검사 캐스트를 썼다 (springdoc 이 선언 타입을 읽음). 실제 body 는 `JoinResBody` 등이다.
- `String.isBlank()` 는 Kotlin 과 Java 의 공백 판정이 달라(NBSP) `isBlankJava()` 확장 함수(Ut.kt)로 Java 판정을 그대로 옮겼다.
- `AuthTokenService` 는 `java.util.Map.of` 를 유지해 null 거부 동작을 보존했다.
- `Rq`, `SecurityConfig`, `CustomAuthenticationFilter` 의 JSON 본문 text block 은 줄 끝 공백 제거 규칙까지 맞춰 명시적 문자열로 옮겼다.
- 죽은 코드: Comment 의 AllArgsConstructor(미사용)는 제거, `catch (Exception) { throw e }` 는 제거(동작 동일), `CommentModifyReqBody` 와 `memberService` 미사용 주입은 원본대로 남겼다.

### 2. 바꾼 것
- build.gradle.kts: Lombok 제거(의존성, annotationProcessor, compileOnly extendsFrom), Kotlin 플러그인 1.9.25 (jvm, plugin.spring, plugin.jpa, plugin.allopen), kotlin-reflect, jackson-module-kotlin 추가, `-Xjsr305=strict`. Boot 3.5.5, JDK 21 툴체인, springdoc 2.8.13 등 나머지는 그대로.
- src/main/java 의 Java 파일 32개 삭제, src/main/kotlin 에 Kotlin 파일 32개를 같은 패키지 경로로 생성.
- docs: GLOSSARY.md, docs/grilling.md, docs/adr/0001~0004.
- 설정 파일(application*.yml) 변경 없음.

### 3. 동작 동일성 근거와 확인하지 못한 것
확인한 것:
- `./gradlew build` 성공 (경고 0개, SecurityUser 의 redundant projection 경고는 수정).
- 원본 jar 과 Kotlin jar 을 각각 localhost 의 인메모리 H2(`--spring.datasource.url=jdbc:h2:mem:...`)로 띄우고 같은 시나리오 73개를 Python 으로 호출했다 (회원 가입/로그인/인증 변형 12종, 글과 댓글 CRUD, 권한 403, 404, 400-1/400-2, 관리자 API, 로그아웃, 쿠키 인증, 액세스 토큰 갱신, 홈과 test/fetchData). 상태 코드, Content-Type, body, Set-Cookie, accessToken 헤더를 비교했다. 시각, JWT, UUID, JSESSIONID 만 정규화했다. 결과: 73개 중 차이 0개.
- Hibernate schema-generation 으로 뽑은 DDL 이 두 빌드에서 동일 (줄바꿈 정규화 후 diff 무차이).
- 시작 로그의 WARN 줄도 두 버전이 동일 (open-in-view 기본값 경고).
- 테스트 서버는 모두 정리됨 (18081~18084 리스닝 없음). curl 사용 안 함.

확인하지 못한 것:
- `/v3/api-docs/*` 와 swagger-ui: 원본과 Kotlin 모두 401 이라 실제 OpenAPI 내용은 비교하지 못했다 (보안 설정상 /api 가 아닌 경로는 인증이 필요해 접근 불가). springdoc 스키마 동등성은 미검증이다.
- dev 프로필의 파일 DB(`jdbc:h2:./db_dev`) 기동은 시험하지 않았다. 인메모리 override 로만 검증했다.
- NBSP 만 있는 Authorization 헤더 같은 공백 엣지 케이스는 시나리오에 없다 (코드상 Java 판정과 동일하게 구현).
- 동시성과 부하, 브라우저에서의 쿠키(Secure, localhost 도메인) 동작은 검증하지 않았다.

### 4. 판단이 필요했던 곳
- 요청 DTO 를 non-null 로 두면 필드 누락 시 Jackson 단계에서 400-2 가 되어 원본의 400-1 검증 메시지가 깨진다. 그래서 `String?` 를 택했고 ADR 0004 로 기록했다.
- Kotlin 생성자 파라미터의 검증 어노테이션은 필드에 붙지 않는다. `@field:` 를 명시하지 않으면 검증이 조용히 빠진다.
- `Post.findCommentById` 의 `c.id!! == commentId` 는 원본 `c.getId().equals(...)` 의 NPE 를 보존하려는 것이다.
- `Member.getName()`, `isAdmin()`, `getAuthorities()` 는 Java 메서드 이름 그대로 `fun` 으로 두었다 (Q12).

### 5. 발견했지만 고치지 않은 원본 문제
1. 보안: BaseInitData 가 admin 계정의 비밀번호를 "admin", 시드 회원들의 API 키를 아이디와 같은 값("admin", "user1" 등)으로 넣는다. 예측 가능한 자격 증명이다.
2. `Member.isAdmin()` 는 아이디 문자열 "admin" 만 보고 ROLE_ADMIN 을 준다. 권한 판단이 식별자에 묶여 있다.
3. `Rq.setCookie` 가 도메인을 "localhost" 로 하드코딩한다 (배포 환경에서 쿠키가 맞지 않을 수 있음).
4. `CustomAuthenticationFilter` 가 401 JSON 을 문자열 포맷으로 만든다 (메시지를 이스케이프하지 않음). 현재는 상수 메시지라 안전하다.
5. `Post`/`Comment` 의 checkActor* 는 author 가 null 이면 NPE, `Post.findCommentById` 는 id 가 null 인 댓글이 있으면 NPE (flush 전 호출, 현재 흐름에서는 도달하지 않음).
6. `AuthTokenService.genAccessToken` 의 `Map.of` 는 username 이나 nickname 이 null 이면 NPE (검증으로 현재는 도달하지 않음).
7. `ApiV1MemberController.join/login/me` 의 선언 반환 타입 `RsData<MemberDto>` 가 실제 body 와 다르다 (원본의 raw type 사용 흔적). 정리하면 OpenAPI 스키마가 바뀐다.
8. `ApiV1CommentController.getItems` 는 @Transactional 없이 지연 로딩을 쓰며 open-in-view 에 의존한다 (시작 경고와 동일).
9. `CommentModifyReqBody` 는 원본에서도 미사용 (수정 API 는 `CommentWriteReqBody` 를 씀).
