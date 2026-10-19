# s-sonnet-t-1b — AI 가 작업 후 낸 보고서 (원문)

Java에서 Kotlin으로의 마이그레이션을 끝내고 `kotlin migration` 메시지로 커밋했다(push 없음). `./gradlew test`는 원본과 변환 후 모두 37개 전부 통과했다. 시작은 04:17이고 04:27쯤 끝나 90분 제한 안에 마쳤다.

## 1. 바꾼 것
- **빌드 설정** (`build.gradle.kts`)
  - 플러그인 추가: `kotlin("jvm")`, `kotlin("plugin.spring")`, `kotlin("plugin.allopen")`. 버전은 Spring Boot 3.5.5가 관리하는 Kotlin과 같은 1.9.25다.
  - `allOpen`에 `@Entity`, `@MappedSuperclass`, `@Embeddable`을 등록했다. 엔티티가 open이어야 Hibernate lazy 프록시가 동작한다.
  - 의존성 추가: `kotlin-reflect`, `jackson-module-kotlin`. `javaParameters = true`도 켰다.
  - Lombok 의존성과 `annotationProcessor` 설정은 삭제했다. 토치 설정(`java { toolchain 21 }`), jacoco, 나머지 의존성은 그대로다.
- **소스**
  - `src/main/java`의 Java 33개를 삭제하고 `src/main/kotlin`에 Kotlin 32개를 만들었다. 패키지 구조는 그대로다.
  - 파일 수가 하나 줄어든 이유: Java의 `Rest1Application`(클래스와 `main`)을 Kotlin에서는 한 파일에 두었다.
  - `src/main/resources`와 `src/test`는 건드리지 않았다.

## 2. 테스트 결과
| | 전체 | 통과 | 실패 |
|---|---|---|---|
| 원본 | 37 | 37 | 0 |
| 변환 후 | 37 | 37 | 0 |

## 3. 동작이 같다고 판단한 근거
- **실행 비교**: 원본 jar와 변환 jar를 각각 띄우고(포트 18081, 18082, localhost) Python으로 같은 요청 99개를 보냈다.
  - 요청 범위: 게시글·댓글·회원·관리자 CRUD, 인증 헤더·쿠키 변형, 잘못된 JSON, 검증 실패, CORS preflight 등.
  - 비교 항목: 상태 코드, 주요 헤더, 응답 본문. 날짜·JWT·UUID는 정규화했다.
  - 결과: 본문과 상태 코드는 99개 모두 같았다. 다른 것은 매번 달라지는 `JSESSIONID` 쿠키값뿐이다.
  - `/v3/api-docs`(전체·apiV1·home)와 swagger-config 본문도 같았다.
- **DB 스키마**: 앱 로그에서 뽑은 Hibernate DDL 10줄(create table, 제약, FK)이 두 버전에서 일치했다.
- **테스트**: 기존 Java 테스트 37개가 수정 없이 통과했다. 여기에는 lazy 프록시를 확인하는 `LazyLoadingTest`도 포함된다.
- **실행 후 정리**: 띄운 두 프로세스(PID 38216, 54084)는 직접 종료했다. 두 포트가 더 이상 listen하지 않는 것도 확인했다.

**확인하지 못한 것**
- `HomeController`(`/`, `/test/fetchData`)는 라이브로 비교하지 못했다. 두 경로 모두 `/api/`가 아니라 인증 필터를 거치지 않고, 곧바로 401이 되기 때문이다(원본 문제 1). 문자열은 Java 텍스트 블록 규칙(공통 들여쓰기 제거, 줄 끝 공백 제거, 끝 개행 유지)을 스크립트로 적용해 Kotlin 리터럴을 만들었다.
- `isBlank` 차이: Java `String.isBlank`와 Kotlin `isBlank`는 NBSP 같은 일부 유니코드 공백에서 다르다. 인증 필터와 `Rq`에서 헤더나 쿠키 값이 NBSP뿐인 경우에만 달라질 수 있다.
- `Ut.jwt.toString`의 `secret.getBytes()`(플랫폼 기본 문자셋)는 `toByteArray()`(UTF-8)로 바뀌었다. JDK 21에서는 기본이 UTF-8이라 같다.
- 개발 DB 파일(`db_dev`)이 이미 있는 상태에서 `ddl-auto: update`가 어떻게 되는지는 보지 않았다. 새 DB에서 만든 DDL만 비교했다.

## 4. 판단이 필요했던 곳
- **`AuthTokenService.genAccessToken`, `payloadOrNull` 가시성**: 원본은 package-private이었다. Kotlin에는 package-private이 없고, `internal`로 하면 같은 패키지의 Java 테스트가 호출하는 메서드 이름이 바뀌어 실패한다. 그래서 `public`으로 열었다. 외부 HTTP 동작에는 영향이 없다.
- **요청 DTO를 nullable로 선언**: `JoinReqBody` 같은 요청 body는 `String?` 필드에 `@field:NotBlank`, `@field:Size`를 붙였다. 필드를 non-null로 두면 필드가 빠진 요청이 400-1(검증 오류) 대신 400-2(형식 오류)로 바뀌기 때문이다. 서비스 호출 직전에는 `!!`로 풀었는데, 검증이 먼저 막아서 null이 올 수 없다.
- **엔티티 필드는 전부 `var ...?`와 `protected set`**: 필드가 non-null이거나 primitive(Long 등)면 DB 컬럼 NOT NULL로 스키마가 바뀐다. 그래서 Java처럼 nullable로 두었다. `private set` 대신 `protected set`을 쓴 것은 open 프로퍼티에는 private setter를 둘 수 없어서다.
- **`RsData<MemberDto>` 반환 타입 유지**: 원본은 raw type으로 `join`, `login`, `me`의 선언 타입 `RsData<MemberDto>`에 `JoinResBody`, `LoginResBody`, `MeResBody`를 담았다. 선언 타입을 올바르게 고치면 OpenAPI 문서가 바뀐다. 그래서 `RsData<Any>(...) as RsData<MemberDto>`로 원본과 같게 유지했고, 주석을 남겼다.
- **`main` 진입점**: Java의 `Rest1Application.main`을 Kotlin 최상위 `main`(`Rest1ApplicationKt`)으로 바꿨다. 클래스 이름 `com.rest1.Rest1Application`은 그대로다.
- **요청 DTO의 접근 수준**: 원본은 package-private record였는데 Kotlin에서는 public data class로 두었다.
- **스크래치패드에 기존 파일이 있었다**: 스크래치패드 폴더에 다른 작업의 파일이 많이 있었고, 내가 `orig.jar`를 그 폴더에 복사해 같은 이름의 기존 파일을 덮어썼을 수 있다. 이후 작업은 하위 폴더 `m1b`로 옮겼다. 저장소 밖 임시 파일이라 프로젝트에는 영향이 없다.
- **`taskkill`이 출력 없이 프로세스를 못 끝냈다**: 내가 띄운 PID 두 개는 PowerShell `Stop-Process`로 정리했다. 다른 프로세스는 건드리지 않았다.

## 5. 발견했지만 고치지 않은 원본 문제
1. **`/`, `/test/fetchData`는 항상 401이다**: `/api/**` 밖의 경로는 `anyRequest().authenticated()`인데 인증 필터는 `/api/`로 시작하는 요청만 처리한다. 홈 페이지에 로그인한 사용자가 접근할 방법이 없다. 실제로 둘 다 401이 나왔다.
2. **`logout`은 `apiKey` 쿠키만 지우고 `accessToken` 쿠키는 남긴다.** 로그아웃 후에도 `accessToken` 쿠키가 만료 전까지 유효하다.
3. **`GET /api/v1/posts/{id}/comments` 계열 조회 권한 문제**: 인증 없이 조회할 수 있는 경로 중 `/api/*/posts/{postId}/comments`는 허용되지만, 조회와 무관한 `POST`는 인증을 요구한다. 읽기용 permitAll이 의도한 범위인지는 불분명하다. 동작은 원본과 같게 유지했다.
4. **`CommentModifyReqBody`가 선언만 있고 쓰이지 않는다.** `modifyItem`은 `CommentWriteReqBody`를 받는다.
5. **응답 타입과 선언 타입 불일치**: `join`, `login`, `me`는 `RsData<MemberDto>`로 선언되어 있지만 실제 `data`는 `JoinResBody` 등이다. OpenAPI 문서가 실제 응답과 다르다. 위 판단 항목처럼 그대로 유지했다.
6. **`CustomAuthenticationFilter`가 JSON을 문자열 포맷으로 만든다**: `msg`를 이스케이프하지 않고 `"%s"`에 넣는다. 지금 메시지에는 따옴표가 없어 문제가 없지만, 다른 메시지가 추가되면 깨진다.
7. **`BaseInitData`의 시드 `apiKey`가 `"admin"`, `"user1"` 같은 추측 가능한 값이다.** 개발용 데이터로 보이나 운영에서는 위험하다.
8. **`Rq.setCookie`는 `domain=localhost`, `secure=true`로 고정되어 있다.** 로컬 http 환경이나 다른 도메인에서는 쿠키가 동작하지 않는다.
9. **`application.yml`의 `spring.thymeleaf.*` 설정은 의존성이 없어 효과가 없다.**
10. **`MemberService.checkPassword`의 인자 이름이 반대다.** `(inputPassword, rawPassword)`인데 실제로는 입력과 저장된(암호화된) 값 순서다. 동작에는 문제가 없다.

주요 파일: `C:\ctj\exp\s-sonnet-t-1b\build.gradle.kts`, `C:\ctj\exp\s-sonnet-t-1b\src\main\kotlin\com\rest1\`. 비교 스크립트와 결과는 `<임시폴더>\m1b\`에 있다(`scen.py`, `o.json`, `k.json`).
