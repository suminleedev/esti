/**
 * 카탈로그 업로드 실패를 화면에 띄울 문구로 바꾼다 (V-6).
 *
 * 서버가 400으로 거부했으면 그 문구는 «사용자에게 하는 안내»다 — 무엇이 잘못됐고 어떻게 하라는지가
 * 들어 있으므로(예: 「이 파일은 B사 양식으로 보입니다 …」) 앞에 아무것도 붙이지 않고 그대로 보여준다.
 * 그 밖의 실패(500·네트워크 등)는 어디서 났는지 알 수 있게 머리말을 붙인다.
 *
 * 전에는 응답 본문을 객체째 문자열에 이어 붙여, 서버 문구가 있어도 `[object Object]`로 나왔다.
 */
export function uploadErrorMessage(err) {
  const status = err?.response?.status
  const data = err?.response?.data
  const serverMessage = typeof data === 'string' ? data : data?.message

  if (status === 400 && serverMessage) return serverMessage
  return '공급사 엑셀 업로드/처리 중 오류가 발생했습니다: ' + (serverMessage || err?.message || '')
}
