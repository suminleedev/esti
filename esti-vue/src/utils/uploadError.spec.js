// 카탈로그 업로드 실패 문구
//
// 지키는 것: 서버가 400으로 돌려준 안내는 **손대지 않고** 보인다.
// 머리말이 붙거나 객체가 그대로 찍히면(`[object Object]`), 사용자는 무엇을 고칠지 모른다.

import { describe, it, expect } from 'vitest'
import { uploadErrorMessage } from './uploadError'

/** axios 오류 모양. */
function axiosError(status, data, message = `Request failed with status code ${status}`) {
  return { message, response: { status, data } }
}

describe('uploadErrorMessage', () => {
  it('400 안내 문구는 그대로 보인다', () => {
    const msg = '이 파일은 B사 양식으로 보입니다 (선택한 공급사: A사). 공급사를 바꿔 다시 올려 주세요.'
    expect(uploadErrorMessage(axiosError(400, { status: 400, message: msg }))).toBe(msg)
  })

  it('400이라도 문구가 없으면 머리말과 axios 메시지로 떨어진다', () => {
    expect(uploadErrorMessage(axiosError(400, {}))).toBe(
      '공급사 엑셀 업로드/처리 중 오류가 발생했습니다: Request failed with status code 400',
    )
  })

  it('500은 머리말을 붙이고 서버 문구를 잇는다 — 객체째 찍지 않는다', () => {
    const text = uploadErrorMessage(axiosError(500, { message: '내부 오류' }))
    expect(text).toBe('공급사 엑셀 업로드/처리 중 오류가 발생했습니다: 내부 오류')
    expect(text).not.toContain('[object Object]')
  })

  it('본문이 문자열이면 그 문자열을 쓴다', () => {
    expect(uploadErrorMessage(axiosError(400, '잘못된 요청'))).toBe('잘못된 요청')
  })

  it('응답이 없는 실패(네트워크·jobId 없음)는 오류 자체의 메시지를 쓴다', () => {
    expect(uploadErrorMessage(new Error('서버에서 jobId를 받지 못했습니다.'))).toBe(
      '공급사 엑셀 업로드/처리 중 오류가 발생했습니다: 서버에서 jobId를 받지 못했습니다.',
    )
  })
})
