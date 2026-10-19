# s-haiku-g-1 — AI 가 작업 후 낸 보고서 (원문)

## Spring Boot Java to Kotlin 마이그레이션 보고

대상: `C:\ctj\exp\s-haiku-g-1` (원격 없음, push 안 함). 커밋 2개.
- `a4c3064` docs: migration decisions
- `c924dd8` kotlin migration
- 작업 트리 깨끗함. 시작 12:04, 종료 약 12:35 (90분 이내).

### 1. 0단계 결정 (ADR 제목 수준)

사용자가 응답할 수 없어서 각 라운드의 추천 답을 수락된 것으로 처리했다. 질문 19개를 3라운드로 나눴고 전부 `docs/grilling.md` 에 기록했다.

ADR (`docs/adr/`):
- 0001 엔티티 필드의 널 허용성은 원본과 같게 둔다 (DDL 보존)
- 0002 요청 DTO는 널 허용 타입으로 두고 검증 어노테이션은 `@field:` 로 붙인다 (400-1 응답 보존)
- 0003 Repository/Service의 `Optional` 반환을 유지한다 (404-1 흐름 보존)

용어집: `GLOSSARY.md` (회원, 아이디, 닉네임, 관리자, API 키, 액세스 토큰, 글, 댓글, 작성자, 결과 코드, 메시지. 구현 세부 없음).

결정과 다르게 한 것:
- Kotlin 버전: 처음엔 2.1.21로 잡았다. Boot 3.5.5 BOM이 `kotlin-bom` 을 1.9.25로 관리하고, 2.1.21을 쓰면 `kotlin-reflect` 가 1.9.25 요청 → 2.1.21 로 어긋났다. 그래서 전부 1.9.25 (Boot 관리 버전)로 맞췄다.
- Q16: `plugin.spring` 과 `plugin.jpa` 만으로는 엔티티가 열리지 않는다. 지연 로딩 프록시가 동작하려면 `plugin.allopen` 으로 `@Entity`, `@MappedSuperclass`, `@Embeddable` 을 열어야 해서 추가했다.
- 엔티티 setter: 원본에는 setter가 없다. Kotlin은 open 프로퍼티에 `private set` 을 허용하지 않아서 `protected set` 을 썼다.
- 응답 제네릭: 원본은 `RsData<MemberDto>` 를 선언하고 실제로는 다른 타입을 넣는다. OpenAPI 스키마를 그대로 두려고 `rsDataOf()` 헬퍼(unchecked cast)를 만들었다.
- `standard/ut/StringExt.kt` 를 추가했다. Kotlin의 `isBlank()` 는 NBSP 같은 유니코드 공백을 빈 값으로 보는데 Java `String.isBlank()` 는 아니어서, Java 판정과 같은 `isBlankJava()` 를 만들었다. 결정 문서에는 없던 항목이다.
- 기타 (동작 영향 없음): `main` 은 `companion object` 에 두어 시작 클래스를 `Rest1Application` 그대로 유지. `@PathVariable` 에 이름을 명시. `Rq.getActor()` 같은 메서드 이름은 유지. `SecurityConfig` 미사용 람다 파라미터는 `_` 로 바꿈.

### 2. 바꾼 것

- `build.gradle.kts`: `java` 를 `kotlin("jvm") 1.9.25` 로 교체. `plugin.spring`, `plugin.jpa`, `plugin.allopen` 1.9.25 추가. Lombok 의존성, `annotationProcessor`, `compileOnly` 블록 제거. `jackson-module-kotlin`, `kotlin-reflect` 추가. `kotlin { jvmToolchain(21); -Xjsr305=strict }`. 나머지 의존성과 Boot/DM 플러그인 버전은 그대로.
- Java 파일 32개 삭제 (`git rm`), Kotlin 파일 33개 추가 (`src/main/kotlin/com/rest1/...`, 패키지와 클래스명 동일. `StringExt.kt` 1개 추가).
- `application*.yml`, gradle wrapper, `settings.gradle.kts` 는 변경 없음. Lombok 참조 0.

### 3. 동작이 원본과 같다고 판단한 근거 / 확인하지 못한 것

확인 방법: 원본 커밋(`cfa5918`)과 신규 빌드 jar를 각각 `localhost:8081`, `8082` 에 빈 H2 DB로 띄우고 같은 시나리오 80개를 Python `urllib` 으로 호출했다. 상태 코드, 본문 원문, Set-Cookie, `accessToken` 헤더를 비교했다. 정규화는 시각, JWT, UUID, IP, JSESSIONID 값만 했다.
- 차이 0건 (80/80).
- 시나리오 범위: 공개 조회, 회원가입, 로그인, 검증 실패 (400-1, 400-2), 인증 실패 (401-1, 401-2, 401-3), 쿠키/Bearer/JWT (유효, 위조, 만료 전 재발급 경로), 권한 (403-1, 403-2), 404-1, 409-1, 관리자 API, 글/댓글 CRUD, CORS (허용/거부), `/v3/api-docs`, 로그아웃.
- DDL: Hibernate 스키마 생성 스크립트 비교, 138줄 완전 일치.
- `./gradlew build` 성공, 컴파일 경고 0. 양쪽 서버 로그 ERROR 0.
- 인증 여부와 상관없이 `/`, `/test/fetchData` 는 원본과 신규 모두 401 (바이트 동일).

확인하지 못한 것:
- 테스트 코드가 없어서 위 시나리오 밖의 경로는 확인하지 못했다. 예: 만료된 JWT, 기존 `db_dev.mv.db` 가 있는 상태에서의 기동, `test` 프로필 기동.
- 쿠키 `Secure`/`SameSite` 가 실제 브라우저에서 동작하는지는 확인하지 않았다 (HTTP 헤더만 비교).
- 기본 charset이 UTF-8이 아닌 환경, 다른 로케일의 `String.format` 결과.
- 동시성, 부하, 기동 시간.

### 4. 판단이 필요했던 곳

- 요청 DTO의 널 허용: 원본처럼 필드가 빠진 요청이 `400-1` 이 되려면 non-null 로 두면 안 된다. `String?` + `@field:` 로 맞추고, 서비스 호출 시 `!!` 를 썼다. 검증을 통과한 뒤에만 도달하므로 NPE는 나지 않는다.
- `Map.of` 의 null 거부 동작은 `java.util.Map.of` 를 그대로 호출해서 보존했다.
- `Comment` 의 미사용 Lombok 생성자는 제거했다. 미사용 `CommentModifyReqBody` 는 남겼다.
- `Ut.jwt.toString` 의 `secret.getBytes()` 는 `Charset.defaultCharset()` 로 옮겼다 (원본과 같은 플랫폼 기본 charset). 바꾸면 원본과 다른 키가 된다.
- `BaseInitData` 의 자기 자신 주입은 `@field:Lazy @field:Autowired lateinit var self` 로 옮겨 `@Transactional` 프록시 호출을 유지했다.
- `CustomAuthenticationFilter` 의 `member` 가변 변수는 Kotlin 1.9 smart cast 문제로 `tokenMember ?: findByApiKey(...)` 로 바꿨다. 조회 조건과 순서는 같다.

### 5. 발견했지만 고치지 않은 원본 문제

- `Ut.jwt.toString` 은 `secret.getBytes()` (플랫폼 기본 charset) 로 서명 키를 만들고, 검증은 UTF-8 로 키를 만든다. 비ASCII 비밀키이면 Windows(cp949)에서 발급한 토큰이 검증에 실패한다. 현재 키는 ASCII라 발현하지 않는다.
- `/` (HomeController) 와 `/test/fetchData` 는 사실상 죽은 페이지다. `CustomAuthenticationFilter` 가 `/api/` 가 아닌 경로에는 인증 정보를 세팅하지 않고, `SecurityConfig` 가 `anyRequest().authenticated()` 로 막아서 항상 401이 된다. 원본 동작 그대로 두었다.
- `HomeController.home()` 은 서버의 내부 IP와 호스트명을 HTML로 내보낸다. 현재는 위 이유로 도달할 수 없다.
- `Rq.setCookie` 는 `domain` 을 `localhost` 로 고정하고 `Secure` 를 켠다. `localhost` 가 아닌 도메인에서는 쿠키가 전달되지 않을 수 있다.
- 액세스 토큰이 유효하면 API 키 쿠키/헤더는 검사하지 않는다 (시나리오 `me-valid-token-with-apikey-cookie` 로 확인). 의도된 설계인지는 불명확하다.
- 미사용 코드: `CommentModifyReqBody`, `Rq` 의 `memberService`, `BaseInitData.work2` 의 `user3` 조회 결과. 동작 영향은 없다.

확인에 쓴 스크립트와 결과: `<임시폴더>\compare\scenario.py`, `...\compare\baseline.json`, `...\compare\new.json`. 테스트 서버 두 개는 종료했다.
