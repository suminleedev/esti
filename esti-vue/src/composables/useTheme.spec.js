// 화면 색 테마 훅 테스트
//
// 이 파일이 지키는 것은 세 가지다.
//   ① 저장값이 없거나 이상하거나 읽을 수 없으면 청록(기본)으로 떨어진다
//   ② 청록은 속성을 지우고 나머지는 data-esti-theme을 단다 — esti-theme.css가 그 규칙으로 쓰였다
//   ③ 테마 키 목록 — index.html 인라인 스크립트가 같은 키를 직접 적고 있어서, 여기가 바뀌면 거기도 바꿔야 한다
import { describe, expect, it } from 'vitest'
import { DEFAULT_THEME, STORAGE_KEY, THEMES, applyTheme, readSaved } from './useTheme'

const storageWith = (value) => ({ getItem: (k) => (k === STORAGE_KEY ? value : null) })

function fakeRoot() {
  const attrs = {}
  return {
    attrs,
    setAttribute: (k, v) => { attrs[k] = v },
    removeAttribute: (k) => { delete attrs[k] },
  }
}

describe('readSaved', () => {
  it('저장된 테마를 그대로 돌려준다', () => {
    expect(readSaved(storageWith('indigo'))).toBe('indigo')
  })
  it('모르는 값이면 기본(청록)', () => {
    expect(readSaved(storageWith('purple'))).toBe(DEFAULT_THEME)
  })
  it('저장값이 없으면 기본(청록)', () => {
    expect(readSaved(storageWith(null))).toBe(DEFAULT_THEME)
  })
  it('저장소를 읽다 예외가 나도 기본(청록)', () => {
    const broken = { getItem: () => { throw new Error('denied') } }
    expect(readSaved(broken)).toBe(DEFAULT_THEME)
  })
})

describe('applyTheme', () => {
  it('청록은 data-esti-theme을 지운다', () => {
    const root = fakeRoot()
    applyTheme('clay', root)
    applyTheme('teal', root)
    expect(root.attrs).toEqual({})
  })
  it('쪽빛·클레이는 data-esti-theme을 단다', () => {
    const root = fakeRoot()
    applyTheme('indigo', root)
    expect(root.attrs['data-esti-theme']).toBe('indigo')
    applyTheme('clay', root)
    expect(root.attrs['data-esti-theme']).toBe('clay')
  })
})

describe('THEMES', () => {
  it('키 목록이 index.html 인라인 스크립트와 같다', () => {
    expect(THEMES.map((t) => t.key)).toEqual(['teal', 'indigo', 'clay'])
    expect(THEMES.filter((t) => t.isDefault).map((t) => t.key)).toEqual([DEFAULT_THEME])
  })
})
