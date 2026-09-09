// API·정적 자원의 기준 주소.
//
// 값이 없으면 빈 문자열이다 — 그러면 «지금 이 페이지와 같은 곳»을 부른다.
// 배포본은 프론트와 API가 한 jar에서 뜨므로 이게 맞고(D-8), 개발 서버도
// vite.config의 프록시가 /api·/uploads 같은 경로를 8080으로 넘겨 준다.
//
// 예전에는 값이 없으면 그대로 undefined였다. .env 파일은 gitignore라
// 새로 받은 저장소에는 없고, 그때 모든 요청이 "undefined/api/..."로 나갔다.
export const BASE_URL = import.meta.env.VITE_API_BASE_URL ?? ''
