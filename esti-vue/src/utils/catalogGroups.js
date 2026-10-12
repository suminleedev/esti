// 제안서 작성 화면 카탈로그의 대분류 섹션 (E2)
//
// 카탈로그 항목에 `categoryLarge`가 이미 실려 오므로 목록을 따로 받지 않고 프론트에서 묶는다.
// 대분류가 비어 있는 품목은 버리지 않고 「기타」로 모은다 — 원본에 대분류 「기타」가 따로 있어
// 같은 이름이면 한 섹션으로 합쳐진다.

export const OTHER_CATEGORY = '기타'

// 공급사마다 표기가 달라 같은 대분류가 두 섹션으로 갈리는 것을 화면에서만 합친다.
// 저장된 대분류는 가격행 기준·재적재 매칭에 쓰여서 고치지 않는다.
const SECTION_ALIASES = {
  악세사리: '액세서리',
  악세서리: '액세서리',
  액세사리: '액세서리',
}

/**
 * 항목을 대분류별로 묶는다. 섹션 안의 순서는 입력 순서를 그대로 지킨다.
 *
 * 섹션 순서는 대분류 이름 가나다순이고 「기타」만 맨 뒤다.
 * 표기만 다른 대분류(악세사리·액세서리)는 「액세서리」 한 섹션으로 합친다.
 *
 * @param {Array<{categoryLarge?: string|null}>} items 카탈로그 항목 (검색 필터를 거친 것)
 * @returns {Array<{name: string, items: Array}>}
 */
export function groupByCategoryLarge(items) {
  const groups = new Map()
  for (const item of items ?? []) {
    const raw = (item.categoryLarge ?? '').trim() || OTHER_CATEGORY
    const name = SECTION_ALIASES[raw] ?? raw
    if (!groups.has(name)) groups.set(name, [])
    groups.get(name).push(item)
  }

  return [...groups.entries()]
    .sort(([a], [b]) => {
      if (a === OTHER_CATEGORY) return 1
      if (b === OTHER_CATEGORY) return -1
      return a.localeCompare(b, 'ko')
    })
    .map(([name, list]) => ({ name, items: list }))
}
