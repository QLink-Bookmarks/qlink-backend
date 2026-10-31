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

Node.js 24에서 `npm ci`, `npm test`, `npm audit --omit=dev --audit-level=high`, `npm run package` 순서로 실행한다. 직접 버전을 변경할 때는 이 디렉터리에서 `npm install --save-exact 패키지@버전`을 실행하고 `package.json`과 `package-lock.json`을 함께 커밋한다. Node 런타임 메이저 버전은 `terraform/lambda/module`의 `runtime`과 CI·Lambda 배포 워크플로의 `setup-node`에서 지정한다.

Lambda는 앱·기존 인프라와 분리된 `terraform/lambda/dev` 및 `terraform/lambda/prod` 상태에서 배포한다. Lambda 소스·의존성·패키징 스크립트 또는 해당 환경의 Lambda Terraform 설정이 바뀐 PR을 `main`(dev) 또는 `release`(prod)에 병합할 때만 Lambda 배포 작업이 실행된다. prod에는 기존 승인 단계가 있다. 배포 작업은 테스트·취약점 검사 후 ZIP을 다시 만들고 Terraform plan/apply를 실행한다. ZIP의 `source_code_hash`로 코드 변경을 추적하고, 발행 버전과 `live` 별칭을 갱신한다. Git SHA는 함수 설명과 태그에 기록된다.

## 외부 IAM 준비

실행 역할과 정책 연결, 서버의 Lambda 호출 권한은 Terraform에서 만들거나 수정하지 않는다. AWS 계정 `650177546654`에는 관리형 정책 `arn:aws:iam::650177546654:policy/alinkPreviewLambda`가 만들어져 있다. 정책 내용은 `references/iam/alinkPreviewLambda.json`에 기록했다. 이 파일을 변경해도 AWS 정책은 자동으로 갱신되지 않으므로 정책 버전은 IAM에서 직접 관리한다. 이 정책을 Lambda 서비스가 수임할 수 있는 각 환경의 실행 역할에 연결하고 역할 태그 `alinkEnvironment=dev` 또는 `alinkEnvironment=prod`를 지정해야 한다. 이 태그로 접근 가능한 버킷·객체 접두어·로그 그룹이 해당 환경으로 제한된다.

GitHub 저장소 변수는 필요 없다. Terraform은 배포 계정 ID를 조회하고 외부 실행 역할 이름 `alinkPreviewLambda-dev` 또는 `alinkPreviewLambda-prod`로 ARN을 조립해 Lambda에 연결한다. 이 이름의 역할을 해당 계정에 만들고 `alinkPreviewLambda` 정책을 연결해야 배포할 수 있다. 서버의 기존 ECS 태스크 역할에는 해당 환경의 `alink-link-preview-{env}:live` 별칭에 대한 `lambda:InvokeFunction` 권한을 외부에서 부여해야 한다.

이미지 객체는 원본 URL의 scheme·host만 소문자화해 만든 해시 또는 파비콘의 호스트 해시를 키로 사용한다. S3 `LastModified`가 7일 미만이면 재사용하고, 이후 요청에서 조건부로 갱신한다. `Cache-Control`은 1일이다.
