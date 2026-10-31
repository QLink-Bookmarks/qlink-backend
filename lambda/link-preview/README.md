# 링크 프리뷰 Lambda

`index.handler`는 API 서버의 비공개 `Invoke` 호출만 받는다. Lambda Function URL은 만들지 않는다. 서버는 인증과 사용자 확인을 마친 뒤 요청한다.

## 요청

```json
{"url":"https://example.com/story","mode":"PREVIEW","traceId":"request-id"}
```

`FAVICON_ONLY`에서는 `favicon`을 생략하면 페이지의 아이콘을 찾아 다운로드한다. 이미 파일 바이트가 있다면 아래처럼 Base64로 전달한다. 이 경우 페이지와 이미지 다운로드를 모두 생략한다. 입력 이미지는 4MiB 이하이며 바이트의 실제 형식으로 변환 대상을 판정한다. 동기 Lambda Invoke의 요청 본문 상한이 6MB라 Base64 입력은 더 작게 제한한다.

```json
{"url":"https://example.com/story","mode":"FAVICON_ONLY","favicon":{"base64":"..."},"traceId":"request-id"}
```

응답은 `{title, thumbnailKey, faviconKey, isVideo}`이며 URL 대신 S3 루트 객체 키를 반환한다. URL 호스트는 API 서버가 조립한다. Lambda는 `APP_ENV=dev|prod`와 `PREVIEW_BUCKET=alink-previews-{env}`를 배포 환경에서 받는다.

## 개발과 배포

Node.js 24에서 `npm ci`, `npm test`, `npm audit --omit=dev --audit-level=high`, `npm run package` 순서로 실행한다. 직접 버전을 변경할 때는 이 디렉터리에서 `npm install --save-exact 패키지@버전`을 실행하고 `package.json`과 `package-lock.json`을 함께 커밋한다. Node 런타임 메이저 버전은 Terraform의 `runtime`과 CI·배포 워크플로의 `setup-node`에서 지정한다. 생성된 `build/link-preview.zip`은 Terraform이 `source_code_hash`로 추적하고 버전으로 발행하며 `live` 별칭이 최신 발행 버전을 가리킨다. CI와 dev/prod 배포가 같은 검증 및 패키징 절차를 수행한다. Git SHA는 함수 설명과 태그에 기록된다.

이미지 객체는 원본 URL의 scheme·host만 소문자화해 만든 해시 또는 파비콘의 호스트 해시를 키로 사용한다. S3 `LastModified`가 7일 미만이면 재사용하고, 이후 요청에서 조건부로 갱신한다. `Cache-Control`은 1일이다. Lambda 실행 역할은 해당 환경 버킷의 이미지 객체와 로그 그룹에만 접근하며 API 서버 역할에는 `live` 별칭 호출 권한만 있다.
