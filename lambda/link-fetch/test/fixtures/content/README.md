# 본문 추출 fixture

`test/content.test.mjs`가 쓰는 실제 페이지 표본이다. 2026-11-07에 본문 함수와 같은 요청 헤더로 받은 HTML에서 스크립트·스타일 태그·링크 태그·주석과 이미지 주소를 지우고, 본문 앞부분(산문 문단 몇 개)까지만 남겼다. 페이지를 감싼 원래 요소 구조, `style` 속성(숨김 요소), 각주 표시는 그대로 둔다.

| 파일 | 원본 | 라이선스 |
|---|---|---|
| `en-wikipedia-redirect.html` | https://en.wikipedia.org/wiki/Bookmark_(digital) | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/), Wikipedia contributors, 줄임 |
| `en-wikipedia-hatnote.html` | https://en.wikipedia.org/wiki/Percentage | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/), Wikipedia contributors, 줄임 |
| `en-wikipedia-figure.html` | https://en.wikipedia.org/wiki/Typography | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/), Wikipedia contributors, 줄임 |
| `en-wikipedia-infobox.html` | https://en.wikipedia.org/wiki/Axolotl | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/), Wikipedia contributors, 줄임 |
| `en-wikipedia-stub.html` | https://en.wikipedia.org/wiki/Alzoniella_edmundi | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/), Wikipedia contributors, 줄임 |
| `ko-wikipedia-infobox.html` | https://ko.wikipedia.org/wiki/김치 | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/), 위키백과 기여자, 줄임 |
| `ko-wikipedia-hatnote.html` | https://ko.wikipedia.org/wiki/비빔밥 | [CC BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/), 위키백과 기여자, 줄임 |
| `python-docs-source-link.html` | https://docs.python.org/3/library/csv.html | PSF License, Python Software Foundation |
| `blog-prose.html` | https://go.dev/blog/defer-panic-and-recover | [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/), The Go Authors, 줄임 |
| `soft-404.html` | https://www.yes24.com/ 의 없는 주소(HTTP 200 안내 페이지) | 예스24 오류 안내 화면 |
