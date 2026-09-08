// 세트가 ↔ 부속 합계 대조 판정 테스트
//
// 이 파일이 지키는 것은 두 가지다.
//   ① 분기 11개가 각각 제 조건에서만 나온다
//   ② **분기 순서**가 계약이다 — 한 입력이 여러 사유에 걸릴 때 어느 것이 이기는지가 정해져 있다.
//      순서가 바뀌면 «확인 필요»로 떠야 할 것이 «구조상 당연»에 묻힌다. 실제로 한 번 그랬다
//      (본품 미포함이 A사 258행을 전부 삼켜 실제 오류 14행이 묻혔던 것 — G-2).
//
// 금액은 전부 합성값이다. 실단가를 쓰지 않는다 (CLAUDE.md 「공급사 정보는 저장소에 두지 않는다」).

import { describe, it, expect } from 'vitest'
import { sumParts, partsSumStatus, PARTS_SUM_BADGE_CLASS } from './partsSum'

/** 부속 한 건. 이름은 합성값이고, 본품 판정에 걸리는 «몸체»·«도기»는 필요할 때만 쓴다. */
function part(productName, unitPrice, extra = {}) {
  return { productName, unitPrice, ...extra }
}

describe('sumParts — 부속 금액 합', () => {
  it('부속이 없으면 0이다', () => {
    expect(sumParts([])).toBe(0)
    expect(sumParts(null)).toBe(0)
    expect(sumParts(undefined)).toBe(0)
  })

  it('수량이 없으면 1개로 본다', () => {
    expect(sumParts([part('개스킷', 1000)])).toBe(1000)
  })

  // 원본이 같은 부속을 두 행에 적는 경우가 있어 합계는 반드시 «단가 × 수량»으로 낸다.
  // 이걸 빠뜨리면 세트가 실제보다 적게 잡힌다.
  it('수량을 곱한다', () => {
    expect(sumParts([part('개스킷', 1000, { quantity: 2 })])).toBe(2000)
    expect(sumParts([part('개스킷', 1000, { quantity: 3 }), part('볼트', 500)])).toBe(3500)
  })

  it('수량이 이상값이면 1개로 본다', () => {
    expect(sumParts([part('개스킷', 1000, { quantity: 0 })])).toBe(1000)
    expect(sumParts([part('개스킷', 1000, { quantity: -3 })])).toBe(1000)
    expect(sumParts([part('개스킷', 1000, { quantity: null })])).toBe(1000)
  })

  it('소수 수량은 버린다', () => {
    expect(sumParts([part('개스킷', 1000, { quantity: 2.9 })])).toBe(2000)
  })

  it('천 단위 구분이 든 문자열 단가를 읽는다', () => {
    expect(sumParts([part('개스킷', '1500')])).toBe(1500)
    expect(sumParts([part('개스킷', '2' + ',' + '500')])).toBe(2500)
  })

  it('단가 미상은 0으로 본다', () => {
    expect(sumParts([part('개스킷', null), part('볼트', 1000)])).toBe(1000)
    expect(sumParts([part('개스킷', ''), part('볼트', 1000)])).toBe(1000)
    expect(sumParts([part('개스킷', '해당없음'), part('볼트', 1000)])).toBe(1000)
  })
})

describe('partsSumStatus — 분기 11개', () => {
  it('부속이 없으면 판정하지 않는다 (호출부가 «부속 없음»을 따로 표시한다)', () => {
    expect(partsSumStatus(1000, [])).toBeNull()
    expect(partsSumStatus(1000, null)).toBeNull()
  })

  it('A. 세트가가 없으면 비교 자체가 성립하지 않는다', () => {
    expect(partsSumStatus(0, [part('개스킷', 1000)])).toMatchObject({
      level: 'info',
      code: 'NO_SET_PRICE',
    })
    expect(partsSumStatus(null, [part('개스킷', 1000)])).toMatchObject({ code: 'NO_SET_PRICE' })
  })

  it('B. 부속 단가가 전부 0이면 오류다 — 단가표 매칭 실패 신호', () => {
    expect(partsSumStatus(5000, [part('개스킷', 0), part('볼트', null)])).toMatchObject({
      level: 'error',
      code: 'ZERO_PART_PRICE',
    })
  })

  it('합계가 맞으면 일치다', () => {
    expect(partsSumStatus(3000, [part('개스킷', 1000), part('볼트', 2000)])).toMatchObject({
      level: 'match',
      code: 'MATCH',
      diff: 0,
    })
  })

  it('D. «N품 세트»인데 부속이 그보다 많으면 오류다', () => {
    const parts = [
      part('부속1', 1000),
      part('부속2', 1000),
      part('부속3', 1000),
      part('부속4', 1000),
      part('부속5', 1000),
    ]
    const status = partsSumStatus(4500, parts, '악세사리 4품 세트')
    expect(status).toMatchObject({ level: 'error', code: 'PIECE_COUNT_OVER' })
    expect(status.label).toContain('4품')
    expect(status.label).toContain('5건')
  })

  it('C. 같은 이름이 두 번 나오면 대체 부속으로 본다', () => {
    const parts = [part('배수구', 1000), part('배수구', 1200)]
    expect(partsSumStatus(1500, parts)).toMatchObject({
      level: 'info',
      code: 'ALTERNATIVE_PARTS',
    })
  })

  it('F. 초과분이 부속 1건과 딱 맞으면 택1 옵션으로 본다', () => {
    const parts = [part('A형 다리', 2000), part('B형 다리', 2500)]
    // 합 4500 − 세트가 2500 = 2000 → A형 다리 1건과 같다
    const status = partsSumStatus(2500, parts)
    expect(status).toMatchObject({ level: 'info', code: 'OPTION_PICK_ONE', diff: 2000 })
    expect(status.detail).toContain('A형 다리')
  })

  it("F''. 부족분이 부속 1건과 딱 맞으면 수량 미반영으로 본다", () => {
    const parts = [part('개스킷', 1000), part('볼트', 3000)]
    // 합 4000 − 세트가 5000 = −1000 → 개스킷이 2개 들어가는데 1건으로 잡힌 것
    const status = partsSumStatus(5000, parts)
    expect(status).toMatchObject({ level: 'info', code: 'PART_COUNTED_ONCE', diff: -1000 })
    expect(status.detail).toContain('개스킷')
  })

  it("F'. 본품 성격 부속이 2건이면 본품 택1로 본다", () => {
    const parts = [part('양변기 도기', 3000), part('양변기 몸체', 3200), part('시트', 1000)]
    const status = partsSumStatus(5000, parts)
    expect(status).toMatchObject({ level: 'info', code: 'MAIN_DUPLICATED' })
    expect(status.detail).toContain('2건')
  })

  it("F'. relationType이 MAIN인 부속 2건도 본품 택1이다", () => {
    const parts = [
      part('구성품 갑', 3000, { relationType: 'MAIN' }),
      part('구성품 을', 3200, { relationType: 'main' }),
      part('시트', 1000),
    ]
    expect(partsSumStatus(5000, parts)).toMatchObject({ code: 'MAIN_DUPLICATED' })
  })

  it('E. 부속 목록에 본품이 없으면 비교 대상이 다르다', () => {
    const parts = [part('시트', 1000), part('배수부속', 1500)]
    expect(partsSumStatus(4200, parts)).toMatchObject({
      level: 'info',
      code: 'MAIN_EXCLUDED',
    })
  })

  it('G. 알려진 사유가 없으면 실제 불일치로 본다', () => {
    const parts = [part('시트', 1000), part('배수부속', 1500)]
    // 본품 단가를 알면 E(본품 미포함)가 성립하지 않는다 → 여기까지 오면 진짜 어긋난 것이다
    const status = partsSumStatus(5200, parts, undefined, 1000)
    expect(status).toMatchObject({ level: 'error', code: 'AMOUNT_MISMATCH', diff: -1700 })
  })

  it('배지 색은 level 세 가지를 모두 덮는다', () => {
    expect(Object.keys(PARTS_SUM_BADGE_CLASS).sort()).toEqual(['error', 'info', 'match'])
  })
})

describe('본품 단가를 아는 공급사 (G-2)', () => {
  // A사: 세트가 = 본품 + 부속합, 부속 목록에 본품이 없다
  it('본품을 더해서 대조한다', () => {
    const parts = [part('시트', 1000), part('배수부속', 1000)]
    expect(partsSumStatus(5000, parts, undefined, 3000)).toMatchObject({
      level: 'match',
      code: 'MATCH',
      diff: 0,
    })
  })

  it('본품 단가가 없으면 종전대로 부속합만 본다', () => {
    const parts = [part('시트', 1000), part('배수부속', 1000)]
    // 같은 입력인데 본품 단가가 없으면 2000 vs 5000이라 일치할 수 없다
    expect(partsSumStatus(5000, parts, undefined, null)).not.toMatchObject({ code: 'MATCH' })
  })

  // 이 한 건이 G-2의 핵심이다. 예전에는 A사가 구조적으로 «본품 미포함»(info)에 걸려
  // 판정 불가로 떨어졌고, 실제 오류가 그 안에 묻혔다.
  it('본품 단가를 알면 실제 불일치가 «본품 미포함»에 묻히지 않는다', () => {
    const parts = [part('시트', 1000), part('배수부속', 1500)]

    const 묻힘 = partsSumStatus(4200, parts, undefined, null)
    expect(묻힘).toMatchObject({ level: 'info', code: 'MAIN_EXCLUDED' })

    const 드러남 = partsSumStatus(5200, parts, undefined, 1000)
    expect(드러남).toMatchObject({ level: 'error', code: 'AMOUNT_MISMATCH' })
  })
})

describe('분기 순서 — 한 입력이 여러 사유에 걸릴 때 무엇이 이기는가', () => {
  it('품수 초과가 대체 부속을 이긴다', () => {
    // 이름 중복(C)과 품수 초과(D)에 동시에 걸린다. D가 먼저다 —
    // 대체 부속은 «구조상 당연»(info)이라, 순서가 뒤집히면 오류가 묻힌다.
    const parts = [
      part('배수구', 1000),
      part('배수구', 1000),
      part('부속3', 1000),
      part('부속4', 1000),
      part('부속5', 1000),
    ]
    expect(partsSumStatus(4500, parts, '악세사리 4품 세트')).toMatchObject({
      level: 'error',
      code: 'PIECE_COUNT_OVER',
    })
  })

  it('대체 부속이 택1 옵션을 이긴다', () => {
    // 이름이 중복(C)이면서 초과분이 부속 1건과도 맞는다(F). C가 먼저다.
    const parts = [part('배수구', 1000), part('배수구', 1200)]
    // 합 2200 − 세트가 1200 = 1000 → 배수구 1건과 같지만, 이름 중복이 먼저 잡힌다
    expect(partsSumStatus(1200, parts)).toMatchObject({ code: 'ALTERNATIVE_PARTS' })
  })

  it('택1 옵션이 본품 택1을 이긴다', () => {
    // 본품 성격 2건(F')이면서 초과분이 부속 1건과 맞는다(F). F가 먼저다.
    const parts = [part('양변기 도기', 2000), part('양변기 몸체', 2000)]
    // 합 4000 − 세트가 2000 = 2000 → 부속 1건과 같다
    expect(partsSumStatus(2000, parts)).toMatchObject({ code: 'OPTION_PICK_ONE' })
  })

  it('세트가 없음이 부속 단가 없음을 이긴다', () => {
    // 세트가도 0이고 부속 단가도 0이다. 값이 없는 것(A)이 매칭 실패(B)보다 먼저다.
    expect(partsSumStatus(0, [part('개스킷', 0)])).toMatchObject({ code: 'NO_SET_PRICE' })
  })
})

describe('sumParts — 선택 옵션 제외', () => {
  // B사 도기 시트는 기본 구성을 왼쪽에, 고를 수 있는 것을 오른쪽(N~P)에 나눠 적는다.
  // 세트가(計)에는 왼쪽만 들어가므로, 옵션을 더하면 멀쩡한 세트가 전부 «합계 불일치»가 된다.
  it('OPTION은 합계에서 뺀다', () => {
    const parts = [
      part('도기', 100),
      part('시트', 50),
      { ...part('탱크뚜껑', 9999), relationType: 'OPTION' },
    ]
    expect(sumParts(parts)).toBe(150)
  })

  it('대소문자를 가리지 않는다', () => {
    expect(sumParts([part('도기', 100), { ...part('옵션', 500), relationType: 'option' }])).toBe(100)
  })

  it('relationType이 없으면 종전대로 센다', () => {
    expect(sumParts([part('도기', 100), part('시트', 50)])).toBe(150)
  })
})
