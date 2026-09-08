// 금액·숫자·날짜 표기 유틸 테스트
//
// 이 파일이 지키는 것은 세 가지다.
//   ① «값이 없다»와 «0»을 가른다 — 둘 다 '-'가 되면 «0원»짜리 항목이 화면에서 사라진다
//   ② 서버가 문자열로 준 숫자(쉼표 포함)도 읽는다
//   ③ date()는 읽을 수 없는 값을 버리지 않고 원본을 그대로 돌려준다
//
// 금액은 전부 합성값이다. 실단가를 쓰지 않는다 (CLAUDE.md 「공급사 정보는 저장소에 두지 않는다」).

import { describe, it, expect } from 'vitest'
import { number, won, date } from './format'

describe('number — 천 단위 구분', () => {
  it('숫자를 쉼표로 끊는다', () => {
    expect(number(1234000)).toBe('1,234,000')
    expect(number(999)).toBe('999')
  })

  // 이 유틸의 «없음»은 null·undefined·빈 문자열 셋뿐이다.
  it('값이 없으면 -', () => {
    expect(number(null)).toBe('-')
    expect(number(undefined)).toBe('-')
    expect(number('')).toBe('-')
  })

  // 0은 «없음»이 아니라 «0»이다. 여기가 무너지면 0원 항목이 화면에서 통째로 사라진다.
  it('0은 -가 아니라 0이다', () => {
    expect(number(0)).toBe('0')
    expect(number('0')).toBe('0')
  })

  // 서버가 이미 포맷한 문자열을 그대로 넘기는 자리가 있어 쉼표를 걷어내고 읽는다.
  it('쉼표가 섞인 문자열도 읽는다', () => {
    expect(number('1,234,000')).toBe('1,234,000')
    expect(number('1234')).toBe('1,234')
  })

  it('숫자로 볼 수 없으면 -', () => {
    expect(number('없음')).toBe('-')
    expect(number({})).toBe('-')
    expect(number(NaN)).toBe('-')
    expect(number(Infinity)).toBe('-')
  })

  it('음수와 소수도 그대로 표기한다', () => {
    expect(number(-1234)).toBe('-1,234')
    expect(number(1234.5)).toBe('1,234.5')
  })
})

describe('won — 금액(원 접미)', () => {
  it('숫자 뒤에 원을 붙인다', () => {
    expect(won(1234000)).toBe('1,234,000원')
    expect(won(0)).toBe('0원')
  })

  // 값이 없을 때는 «-원»이 아니라 «-»다.
  it('값이 없으면 원을 붙이지 않는다', () => {
    expect(won(null)).toBe('-')
    expect(won('')).toBe('-')
    expect(won('없음')).toBe('-')
  })
})

describe('date — YYYY-MM-DD 통일', () => {
  // 서버가 LocalDateTime을 주면 뒤에 시각이 붙어 온다. 앞 10자리만 쓴다.
  it('이미 YYYY-MM-DD면 앞 10자리만 남긴다', () => {
    expect(date('2026-09-08')).toBe('2026-09-08')
    expect(date('2026-09-08T13:45:00')).toBe('2026-09-08')
    expect(date('2026-09-08T13:45:00.123456')).toBe('2026-09-08')
  })

  it('Date 객체를 포맷한다', () => {
    // 로컬 기준으로 만들어야 실행 환경 시간대에 흔들리지 않는다.
    expect(date(new Date(2026, 8, 8))).toBe('2026-09-08')
    expect(date(new Date(2026, 0, 1))).toBe('2026-01-01')
  })

  it('한 자리 월·일을 0으로 채운다', () => {
    expect(date(new Date(2026, 2, 5))).toBe('2026-03-05')
  })

  it('값이 없으면 -', () => {
    expect(date(null)).toBe('-')
    expect(date(undefined)).toBe('-')
    expect(date('')).toBe('-')
  })

  // 읽을 수 없다고 '-'로 지우지 않는다. 원본을 보여줘야 무엇이 잘못됐는지 알 수 있다.
  it('날짜로 읽을 수 없으면 원본을 그대로 돌려준다', () => {
    expect(date('언젠가')).toBe('언젠가')
    expect(date('2026-13-99')).toBe('2026-13-99')
  })
})
