# 링크 프리뷰·본문 Lambda

이 패키지 ZIP 하나로 함수 두 개를 배포한다. `alink-link-preview-{env}`(진입점 `index.handler`)는 제목·썸네일·파비콘을, `alink-link-content-{env}`(진입점 `content.handler`)는 링크 본문을 수집한다. 두 함수 모두 서버의 `live` 별칭 호출만 받는다.

## 프리뷰 함수

`index.handler`는 API 서버의 비공개 `Invoke` 호출만 받는다. Lambda Function URL은 만들지 않는다. 서버는 인증과 사용자 확인을 마친 뒤 요청한다.

### 요청

```json
{"url":"https://example.com/story","mode":"PREVIEW","traceId":"request-id"}
```

`FAVICON_ONLY`에서는 `favicon`을 생략하면 페이지의 아이콘을 찾아 다운로드한다. 이미 파일 바이트가 있다면 아래처럼 Base64로 전달한다. 이 경우 페이지와 이미지 다운로드를 모두 생략한다. 입력 이미지는 4MiB 이하이며 바이트의 실제 형식으로 변환 대상을 판정한다. 동기 Lambda Invoke의 요청 본문 상한이 6MB라 Base64 입력은 더 작게 제한한다.

```json
{"url":"https://example.com/story","mode":"FAVICON_ONLY","favicon":{"base64":"..."},"traceId":"request-id"}
```

응답은 `{title, thumbnailKey, faviconKey, isVideo}`이며 URL 대신 S3 루트 객체 키를 반환한다. URL 호스트는 API 서버가 조립한다. Lambda는 `APP_ENV=dev|prod`와 `PREVIEW_BUCKET=alink-previews-{env}`를 배포 환경에서 받는다.

`PREVIEW` 모드에서 YouTube 영상 URL(`youtube.com`·`www.youtube.com`·`m.youtube.com`의 `/watch?v=`, `/shorts/`, `/live/`, `/embed/`와 `youtu.be/`, 영상 id 11자)은 페이지보다 먼저 YouTube oEmbed(`https://www.youtube.com/oembed`, 상한 100,000바이트)에서 제목과 `i.ytimg.com` 썸네일을 받는다. 데이터센터 IP에는 og 태그가 없는 페이지가 오기 때문이다. oEmbed나 썸네일이 실패하면 페이지, `hqdefault.jpg`, 파비콘 순서로 대체하며 `isVideo`는 true다. 판별과 응답 해석은 `src/youtube.mjs`가 맡는다.

## 본문 함수

서버의 본문 수집 워커가 링크 하나마다 동기 `Invoke`로 호출한다. 사용자 컨텍스트 없이 저장된 URL만 요청한다.

```json
{"url":"https://example.com/story","traceId":"request-id","deadlineMs":20000}
```

`deadlineMs`는 그대로 믿지 않는다. 실제 마감은 `min(deadlineMs, 남은 실행 시간 − 5초, 20초)`이며 DNS·모든 리다이렉트 홉·본문 수신 전체에 걸린다. 타임아웃은 마감 20초 < Lambda 30초 < 서버 호출 45초 순서로 둔다.

```json
{"outcome":"COLLECTED","code":null,"upstreamStatus":200,"retryAfterSeconds":null,
 "finalUrl":"https://example.com/story","canonicalUrl":null,"title":"제목","siteName":null,"author":null,
 "publishedAt":"2026-03-01T00:00:00.000Z","language":"ko","text":"정제 본문","truncation":"NONE","durationMs":1234}
```

- `outcome`: `COLLECTED`, `FAILED`, `BLOCKED`. 실패면 `text`와 메타데이터는 null이다(`finalUrl`은 받은 경우만).
- `code`: `FETCH_TIMEOUT`, `TOO_MANY_REDIRECTS`, `TARGET_DENIED`, `UPSTREAM_NOT_FOUND`(404·410, 없는 호스트), `UPSTREAM_SERVER_ERROR`(5xx), `UPSTREAM_RATE_LIMITED`(429), `UPSTREAM_OTHER_STATUS`, `NOT_HTML`, `CONTENT_TOO_LARGE`(예약; 2MB를 넘는 HTML은 잘라서 처리하므로 지금은 보내지 않음), `CONTENT_EMPTY`, `NOT_CONTENT`, `EXTRACTION_FAILED`, `LOGIN_REQUIRED`(401, 짧은 본문 + 비밀번호 칸), `PAYWALL`(JSON-LD `isAccessibleForFree: false`), `BOT_WALL`, `ACCESS_DENIED`(403), `ROBOTS_NOINDEX`(robots·googlebot 메타 또는 `X-Robots-Tag`의 `noindex`·`noarchive`; 헤더는 크롤러 접두어가 없거나 `googlebot:`인 지시어만 본다), `INVALID_EVENT`. 차단 코드(`LOGIN_REQUIRED`, `PAYWALL`, `BOT_WALL`, `ACCESS_DENIED`, `ROBOTS_NOINDEX`)는 `BLOCKED`, 나머지는 `FAILED`다. 재시도 여부는 서버가 `code`로만 정한다. 연결 재설정·거부 같은 일시적 네트워크 오류는 `FETCH_TIMEOUT`으로 보낸다.
- `upstreamStatus`·`retryAfterSeconds`는 로그와 호스트 쿨다운용이다. `Retry-After`는 초 또는 HTTP 날짜를 초로 바꿔 그대로 넘기고, 서버가 0~900초로 자른다.
- `truncation`: HTML이 2MB에서 잘렸으면 `HTML`, 본문이 100,000자에서 잘렸으면 `TEXT`(surrogate 쌍은 나누지 않음), 아니면 `NONE`.

처리 순서는 `src/content-handler.mjs`와 `src/content-extract.mjs`가 맡는다. 받기는 프리뷰와 같은 `fetchSafe`(대상 검증, 리다이렉트마다 재검증, 2MB 절단, 압축 응답 거절)를 쓰며, 본문 함수만 `accept`·`accept-language` 요청 헤더와 마감 신호를 켠다. 오류 응답의 본문은 읽지 않는다. 인코딩은 BOM → `Content-Type` charset → 앞 2KB의 `<meta>` charset → UTF-8 순서로 정하고, `cp949`·`ms949`·`ks_c_5601-1987`·`ks_c_5601-1989`·`x-windows-949`는 `euc-kr`로 읽는다. `linkedom`으로 한 번 파싱해(스크립트 미실행) 요소(element, 텍스트 노드 제외)가 200,000개를 넘으면 `EXTRACTION_FAILED`다. 스크립트·입력칸·버튼·선택 상자·내비게이션·푸터·사이드 영역 등을 지우고 `<form>`은 안의 글만 남긴 복사본에서(ASP.NET WebForms처럼 본문 전체를 감싼 폼 대비) `@mozilla/readability`(`charThreshold: 1`)로 본문을 고르고, 실패하면 `<article>`·`<main>`을 쓴다. 요소가 100,000개를 넘으면 Readability를 건너뛴다(linkedom에서는 Readability 자체 상한이 동작하지 않는다). 블록 요소 경계는 빈 줄, `br`은 줄바꿈이며, 제어·zero-width 문자 제거, 줄 안 공백 1칸, 빈 줄 1개, 세 번 이상 반복된 같은 줄 1회로 정규화한다. 본문 최소 길이는 없다. 비어 있으면 `CONTENT_EMPTY`, 200자 미만이면서 비밀번호 칸이 있으면 `LOGIN_REQUIRED`, 봇 표식(소스의 `cf-chl`·`challenge-form`·`cf-browser-verification`, `<title>`의 "Just a moment", 보이는 본문의 "captcha")이 있으면 `BOT_WALL`, 200자 미만이면서 제목과 같거나 모든 줄이 오류·로딩·쿠키 동의·지역 제한 안내 문구면 `NOT_CONTENT`다. 문구 목록은 `src/content-extract.mjs`의 상수다. `robots.txt`는 조회하지 않는다.

로그는 `LINK_CONTENT_EXTERNAL`(host·status·ms)과 `LINK_CONTENT_RESULT`(traceId·outcome·code·upstreamStatus·textLength·truncation·durationMs)뿐이다. 본문·헤더·경로·쿼리스트링은 남기지 않는다. 함수는 VPC에 연결하지 않는다.

## 개발과 배포

Node.js 24에서 `npm ci`, `npm test`, `npm audit --omit=dev --audit-level=high`, `npm run package` 순서로 실행한다. `링크 프리뷰 Lambda CI (npm)`은 Lambda 파일이 바뀐 PR에서 이 npm 검증만 수행한다. Terraform fmt·validate·plan은 환경별 Lambda CD에서 수행한다. 직접 버전을 변경할 때는 이 디렉터리에서 `npm install --save-exact 패키지@버전`을 실행하고 `package.json`과 `package-lock.json`을 함께 커밋한다. Node 런타임 메이저 버전은 `terraform/lambda/module`의 `runtime`과 CI·Lambda 배포 워크플로의 `setup-node`에서 지정한다.

Lambda는 앱·기존 인프라와 분리된 `terraform/lambda/dev` 및 `terraform/lambda/prod` 상태에서 배포한다. Lambda 소스·의존성·패키징 스크립트 또는 해당 환경의 Lambda Terraform 설정이 바뀐 PR을 `main`(dev) 또는 `release`(prod)에 병합할 때만 Lambda 배포 작업이 실행된다. prod에는 기존 승인 단계가 있다. 배포 작업은 테스트·취약점 검사 후 ZIP을 다시 만들고 Terraform plan/apply를 실행한다. ZIP의 `source_code_hash`로 코드 변경을 추적하고, 발행 버전과 `live` 별칭을 갱신한다. Git SHA는 함수 설명과 태그에 기록된다.

## 외부 IAM 준비

실행 역할과 정책 연결, 서버의 Lambda 호출 권한은 Terraform에서 관리하지 않는다. 본문 함수에는 로그 기록 권한만 가진 별도의 실행 역할이 필요하다. 배포 주체는 해당 실행 역할을 전달할 수 있어야 하며, API 서버의 태스크 역할에는 각 함수의 `live` 별칭을 호출할 권한이 필요하다. 실제 IAM 정책과 적용 내역은 AWS에서 별도로 관리한다.

프리뷰 함수의 이미지 객체는 원본 URL의 scheme·host만 소문자화해 만든 해시 또는 파비콘의 호스트 해시를 키로 사용한다. S3 `LastModified`가 7일 미만이면 재사용하고, 이후 요청에서 조건부로 갱신한다. `Cache-Control`은 1일이다.
