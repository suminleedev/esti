// 서버 페이징 상태 훅 테스트
//
// 이 파일이 지키는 것은 세 가지다.
//   ① applyPage()가 페이지 응답의 «두 모양»을 다 읽는다
//   ② 이동 함수들이 갈 곳이 없을 때 loadFn을 부르지 않는다
//   ③ pageNumbers가 블록 경계에서 잘린다
//
// ①이 이 훅의 존재 이유다. 백엔드가 serialization-mode=via-dto로 바뀌면서
// 페이지 메타가 평평한 자리에서 `page` 아래로 내려갔다. 프론트가 그 모양을 아는 자리를
// 여기 하나로 모아 뒀으니, 두 모양을 아는 것도 여기 하나여야 한다.
// 여기가 조용히 깨지면 목록 화면이 «0건»으로 보이고 페이지 버튼이 사라진다.
//
// ②는 «안 하는 일»이라 눈에 띄지 않는다. 경계에서 loadFn이 새면 같은 조회가 두 번 나간다.

import { describe, it, expect, vi } from 'vitest'
import { usePagination } from './usePagination'

/** loadFn 호출 횟수를 셀 수 있는 훅 한 벌. */
function setup() {
  const loadFn = vi.fn()
  return { loadFn, ...usePagination(loadFn) }
}

/** via-dto(중첩) 모양 — 현재 백엔드가 주는 형태. */
function nested(content, { size = 10, number = 0, totalElements = 0, totalPages = 0 } = {}) {
  return { content, page: { size, number, totalElements, totalPages } }
}

/** 예전 평평한 모양 — PageImpl을 그대로 직렬화하던 시절. */
function flat(content, meta = {}) {
  return { content, ...meta }
}

describe('applyPage — 페이지 응답 반영', () => {
  it('중첩 모양(via-dto)을 읽는다', () => {
    const p = setup()
    const rows = p.applyPage(nested([{ id: 1 }, { id: 2 }], { number: 2, totalElements: 25, totalPages: 3 }))

    expect(rows).toEqual([{ id: 1 }, { id: 2 }])
    expect(p.page.value).toBe(2)
    expect(p.totalPages.value).toBe(3)
    expect(p.totalElements.value).toBe(25)
  })

  it('평평한 모양도 함께 읽는다', () => {
    const p = setup()
    const rows = p.applyPage(flat([{ id: 1 }], { number: 1, totalElements: 5, totalPages: 2 }))

    expect(rows).toEqual([{ id: 1 }])
    expect(p.page.value).toBe(1)
    expect(p.totalPages.value).toBe(2)
    expect(p.totalElements.value).toBe(5)
  })

  // 범위를 넘겨 요청하면 서버가 현재 페이지를 되돌려 준다. 그 값을 받아야 화면과 서버가 어긋나지 않는다.
  it('서버가 보정한 페이지 번호를 받는다', () => {
    const p = setup()
    p.page.value = 99
    p.applyPage(nested([], { number: 4, totalPages: 5 }))

    expect(p.page.value).toBe(4)
  })

  // number가 빠져 오면 현재 페이지를 유지한다 — 0으로 되돌리면 보고 있던 자리를 잃는다.
  it('number가 없으면 현재 페이지를 유지한다', () => {
    const p = setup()
    p.page.value = 3
    p.applyPage({ content: [], page: { totalPages: 5 } })

    expect(p.page.value).toBe(3)
  })

  it('응답이 비어 있어도 터지지 않는다', () => {
    const p = setup()

    expect(p.applyPage(null)).toEqual([])
    expect(p.applyPage(undefined)).toEqual([])
    expect(p.applyPage({})).toEqual([])
    expect(p.totalPages.value).toBe(0)
    expect(p.totalElements.value).toBe(0)
  })
})

describe('clearPage — 조회 실패 시 빈 상태', () => {
  it('총계를 0으로 되돌린다', () => {
    const p = setup()
    p.applyPage(nested([{ id: 1 }], { totalElements: 25, totalPages: 3 }))
    p.clearPage()

    expect(p.totalPages.value).toBe(0)
    expect(p.totalElements.value).toBe(0)
  })
})

describe('goToPage — 경계에서 조회하지 않는다', () => {
  it('정상 이동은 조회한다', async () => {
    const p = setup()
    p.applyPage(nested([], { totalPages: 5 }))
    await p.goToPage(3)

    expect(p.page.value).toBe(3)
    expect(p.loadFn).toHaveBeenCalledTimes(1)
  })

  it('음수로는 가지 않는다', async () => {
    const p = setup()
    p.applyPage(nested([], { totalPages: 5 }))
    await p.goToPage(-1)

    expect(p.page.value).toBe(0)
    expect(p.loadFn).not.toHaveBeenCalled()
  })

  it('마지막 페이지를 넘어가지 않는다', async () => {
    const p = setup()
    p.applyPage(nested([], { totalPages: 5 }))
    await p.goToPage(5)

    expect(p.page.value).toBe(0)
    expect(p.loadFn).not.toHaveBeenCalled()
  })

  // 같은 페이지를 다시 누르면 조회가 한 번 더 나간다. 그게 새면 목록이 두 번 깜빡인다.
  it('같은 페이지면 조회하지 않는다', async () => {
    const p = setup()
    p.applyPage(nested([], { number: 2, totalPages: 5 }))
    await p.goToPage(2)

    expect(p.loadFn).not.toHaveBeenCalled()
  })
})

describe('firstPage · lastPage', () => {
  it('첫 페이지로 간다', async () => {
    const p = setup()
    p.applyPage(nested([], { number: 3, totalPages: 5 }))
    await p.firstPage()

    expect(p.page.value).toBe(0)
    expect(p.loadFn).toHaveBeenCalledTimes(1)
  })

  it('마지막 페이지로 간다', async () => {
    const p = setup()
    p.applyPage(nested([], { totalPages: 5 }))
    await p.lastPage()

    expect(p.page.value).toBe(4)
  })

  // 목록이 비면 totalPages가 0이다. 이때 lastPage()는 -1로 가려 들면 안 된다.
  it('목록이 비어 있으면 마지막 페이지로 가지 않는다', async () => {
    const p = setup()
    await p.lastPage()

    expect(p.page.value).toBe(0)
    expect(p.loadFn).not.toHaveBeenCalled()
  })
})

describe('prevBlock · nextBlock — 10칸 단위 이동', () => {
  it('다음 블록의 첫 페이지로 간다', async () => {
    const p = setup()
    p.applyPage(nested([], { number: 3, totalPages: 25 }))
    await p.nextBlock()

    expect(p.page.value).toBe(10)
  })

  it('이전 블록의 첫 페이지로 간다', async () => {
    const p = setup()
    p.applyPage(nested([], { number: 14, totalPages: 25 }))
    await p.prevBlock()

    expect(p.page.value).toBe(0)
  })

  it('첫 블록에서는 이전으로 가지 않는다', async () => {
    const p = setup()
    p.applyPage(nested([], { number: 3, totalPages: 25 }))
    await p.prevBlock()

    expect(p.page.value).toBe(3)
    expect(p.loadFn).not.toHaveBeenCalled()
  })

  it('마지막 블록에서는 다음으로 가지 않는다', async () => {
    const p = setup()
    p.applyPage(nested([], { number: 22, totalPages: 25 }))
    await p.nextBlock()

    expect(p.page.value).toBe(22)
    expect(p.loadFn).not.toHaveBeenCalled()
  })
})

describe('pageNumbers — 버튼에 뿌릴 번호', () => {
  it('목록이 비면 빈 배열이다', () => {
    const p = setup()

    expect(p.pageNumbers.value).toEqual([])
  })

  it('현재 페이지가 속한 블록을 낸다', () => {
    const p = setup()
    p.applyPage(nested([], { number: 12, totalPages: 25 }))

    expect(p.pageNumbers.value).toEqual([10, 11, 12, 13, 14, 15, 16, 17, 18, 19])
  })

  // 마지막 블록은 블록 크기가 아니라 totalPages에서 잘린다.
  it('마지막 블록은 총 페이지에서 잘린다', () => {
    const p = setup()
    p.applyPage(nested([], { number: 22, totalPages: 25 }))

    expect(p.pageNumbers.value).toEqual([20, 21, 22, 23, 24])
  })

  it('한 블록에 못 미치면 있는 만큼만 낸다', () => {
    const p = setup()
    p.applyPage(nested([], { totalPages: 3 }))

    expect(p.pageNumbers.value).toEqual([0, 1, 2])
  })
})

describe('resetToFirst — 필터 변경 시', () => {
  // 조회는 호출부가 맡는다. 여기서 loadFn을 부르면 필터 적용 조회와 겹쳐 두 번 나간다.
  it('페이지만 0으로 되돌리고 조회하지 않는다', () => {
    const p = setup()
    p.applyPage(nested([], { number: 7, totalPages: 25 }))
    p.resetToFirst()

    expect(p.page.value).toBe(0)
    expect(p.loadFn).not.toHaveBeenCalled()
  })
})
