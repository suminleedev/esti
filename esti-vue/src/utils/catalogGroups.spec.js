// 카탈로그 대분류 섹션 묶기 테스트 (E2)

import { describe, it, expect } from 'vitest'
import { groupByCategoryLarge, OTHER_CATEGORY } from './catalogGroups'

/** 카탈로그 항목 한 건. 이름은 합성값이다. */
function item(productName, categoryLarge) {
  return { productName, categoryLarge }
}

const names = (groups) => groups.map((g) => g.name)

describe('groupByCategoryLarge', () => {
  it('항목이 없으면 섹션도 없다', () => {
    expect(groupByCategoryLarge([])).toEqual([])
    expect(groupByCategoryLarge(null)).toEqual([])
  })

  it('대분류별로 묶고 섹션은 가나다순이다', () => {
    const groups = groupByCategoryLarge([
      item('세면기A', '세면기'),
      item('변기A', '양변기'),
      item('비데A', '비데'),
      item('변기B', '양변기'),
    ])

    expect(names(groups)).toEqual(['비데', '세면기', '양변기'])
    expect(groups[2].items.map((i) => i.productName)).toEqual(['변기A', '변기B'])
  })

  it('섹션 안에서는 입력 순서를 지킨다', () => {
    const groups = groupByCategoryLarge([
      item('나', '수전부속'),
      item('가', '수전부속'),
      item('다', '수전부속'),
    ])

    expect(groups[0].items.map((i) => i.productName)).toEqual(['나', '가', '다'])
  })

  // 대분류가 없는 품목을 빠뜨리면 검색해도 안 보이는 품목이 생긴다
  it('대분류가 비어 있으면 「기타」로 모은다', () => {
    const groups = groupByCategoryLarge([
      item('없음', null),
      item('미정의', undefined),
      item('공백', '  '),
      item('빈칸', ''),
    ])

    expect(names(groups)).toEqual([OTHER_CATEGORY])
    expect(groups[0].items).toHaveLength(4)
  })

  it('원본 대분류 「기타」와 빈 대분류는 한 섹션이고 맨 뒤다', () => {
    const groups = groupByCategoryLarge([
      item('원본기타', '기타'),
      item('빈칸', null),
      item('욕조A', '욕조'),
      item('악세A', '악세사리'),
    ])

    expect(names(groups)).toEqual(['악세사리', '욕조', OTHER_CATEGORY])
    expect(groups[2].items.map((i) => i.productName)).toEqual(['원본기타', '빈칸'])
  })

  it('대분류 앞뒤 공백은 같은 섹션으로 본다', () => {
    const groups = groupByCategoryLarge([item('A', '비데 '), item('B', '비데')])

    expect(names(groups)).toEqual(['비데'])
    expect(groups[0].items).toHaveLength(2)
  })
})
