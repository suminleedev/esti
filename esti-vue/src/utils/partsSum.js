// 세트가 ↔ 부속 합계 대조 판정 (P5F-5 후속 ①)
//
// 종전 배지는 "세트가 === 부속 합"을 항상 성립해야 할 등식으로 보고, 어긋나면 전부 경고를 냈다.
// 전수 조사 결과 불일치 367건 중 300건은 데이터가 아니라 판정이 틀린 것이었다
// (근거: docs/analysis-b-set-parts-mismatch.md §4~§5).
//
// 단순 합계가 성립하지 않는 구조적 사유가 네 가지 있다:
//   - 같은 슬롯의 대체 부속(국산/OEM)이 함께 딸려 온다 — 부속 관계가 price_basis를 따라가지 않는다
//   - 택1 옵션(반다리/긴다리)이 둘 다 계상된다
//   - 시트에 따라 부속 목록에 본품이 없다 — 세트가와 비교 대상이 다르다
//   - 원본에 세트가가 아예 없다(분계표)
// 이 넷은 "확인 필요"가 아니라 **비교 불가**로 표시해야 실제 오류가 묻히지 않는다.
//
// 부속 수량은 `quantity`로 온다(§8 잔여 ② 해소). 원본이 같은 부속을 두 행에 적는 경우가 있어
// 합계는 반드시 `단가 × 수량`으로 낸다 — 안 그러면 `S132E`·`L352E` 같은 세트가 실제보다 적게 잡힌다.

/** 문자열/숫자/null → 숫자. 변환 불가 시 null. */
function toNum(v) {
  if (v == null || v === '') return null
  const n = typeof v === 'number' ? v : Number(String(v).replace(/,/g, ''))
  return Number.isFinite(n) ? n : null
}

/** 부속 수량. 없거나 이상값이면 1. */
function qtyOf(part) {
  const q = toNum(part.quantity)
  return q != null && q >= 1 ? Math.trunc(q) : 1
}

/** 부속 금액 합. 단가 미상(null)은 0으로 보고, 수량을 곱한다. */
export function sumParts(parts) {
  return (parts ?? []).reduce((sum, part) => sum + (toNum(part.unitPrice) ?? 0) * qtyOf(part), 0)
}

/** 본품 성격의 부속 건수 — MAIN 슬롯 또는 몸체/도기 계열. */
function countMainBody(parts) {
  return parts.filter((part) => {
    const type = (part.relationType ?? '').toUpperCase()
    if (type === 'MAIN') return true
    const name = part.productName ?? ''
    return name.includes('몸체') || name.includes('도기')
  }).length
}

/** 같은 부속명이 2회 이상 등장하는가 — 대체 부속(국산/OEM) 동시 계상의 신호. */
function hasDuplicateName(parts) {
  const seen = new Set()
  return parts.some((part) => {
    const name = (part.productName ?? '').trim()
    if (!name) return false
    if (seen.has(name)) return true
    seen.add(name)
    return false
  })
}

/** 세트명의 "N품" 표기 → N. 없으면 null. (예: '4품 세트 AC1200(4품)' → 4) */
function declaredPieceCount(productName) {
  const m = /(\d+)\s*품/.exec(productName ?? '')
  return m ? Number(m[1]) : null
}

/**
 * 단가가 `amount`와 정확히 일치하는 부속을 찾는다. 없으면 null.
 *
 * 차액이 특정 부속 1건의 단가와 딱 맞아떨어지는 것은 우연이 아니라 신호다.
 * 부호에 따라 뜻이 반대다 — 초과(+)는 택1 옵션이 둘 다 계상된 것이고,
 * 부족(−)은 그 부속이 세트에 2개 들어가는데 관계가 1건으로 접힌 것이다.
 */
function partPricedAt(parts, amount) {
  if (amount <= 0) return null
  return parts.find((part) => (toNum(part.unitPrice) ?? 0) * qtyOf(part) === amount) ?? null
}

/**
 * 세트가와 부속 합계를 대조해 배지 한 건을 만든다.
 *
 * `mainUnitPrice`가 오면 **본품 포함 세트가**로 대조한다(G-2).
 * 공급사마다 부속 목록의 의미가 다르기 때문이다 —
 *   · A사: 세트가 = 본품 + 부속합. 부속 목록에 본품이 없다(270/270)
 *   · B사: 세트가 = 부속합.       부속 목록에 본품이 들어 있다(120 중 88)
 * 예전에는 A사가 구조적으로 `본품 미포함`(info)에 걸려 258행 전부가 판정 불가였고,
 * 실제 오류 14행이 그 안에 묻혔다.
 *
 * level 은 세 가지다:
 *   - `match`  합계가 맞는다
 *   - `info`   합계가 안 맞지만 **구조상 당연한** 것 — 확인할 필요가 없다
 *   - `error`  실제로 확인해야 하는 것
 *
 * @param {number|string|null} setPrice 세트(대표품목) 단가
 * @param {Array} parts 부속 목록 (`VendorProductPartView`)
 * @param {string} [productName] 세트 제품명 — "N품" 표기 판정에만 쓴다
 * @param {number|string|null} [mainUnitPrice] 본품 자체 단가. 있으면 `본품 + 부속합`으로 대조한다(G-2).
 *        null이면 종전대로 부속합만 본다(B사 경로, 판정 불변)
 * @returns {{level:string, code:string, label:string, detail:string, diff:number}|null}
 *          부속이 없으면 null (호출부가 "부속 없음"을 따로 표시한다)
 */
export function partsSumStatus(setPrice, parts, productName, mainUnitPrice = null) {
  const list = parts ?? []
  if (list.length === 0) return null

  const set = toNum(setPrice) ?? 0
  const main = toNum(mainUnitPrice)
  // 본품 단가를 아는 공급사는 본품을 더해 대조한다 — 그래야 비교 대상이 같아진다.
  const sum = sumParts(list) + (main ?? 0)
  const diff = sum - set

  // A. 원본에 세트 합계가 없다(분계표). 파싱 실패가 아니라 값이 없는 것이라 비교 자체가 성립하지 않는다.
  if (set === 0) {
    return {
      level: 'info',
      code: 'NO_SET_PRICE',
      label: '세트가 미기재',
      detail: '원본에 세트 합계가 없어 대조할 수 없습니다 (부속 단가만 있는 시트).',
      diff,
    }
  }

  // B. 부속은 붙었는데 단가가 전부 0 — 부속 단가표와 코드가 연결되지 않았다. 진짜 버그다.
  if (sum === 0) {
    return {
      level: 'error',
      code: 'ZERO_PART_PRICE',
      label: '부속 단가 없음',
      detail: '부속 단가가 모두 0입니다. 부속 단가표 매칭에 실패했을 수 있습니다.',
      diff,
    }
  }

  if (diff === 0) {
    return { level: 'match', code: 'MATCH', label: '일치', detail: '', diff }
  }

  // D. "N품 세트"인데 부속이 그보다 많다 — 옵션 품목이 부속으로 잡힌 것이다.
  const declared = declaredPieceCount(productName)
  if (declared != null && list.length > declared) {
    return {
      level: 'error',
      code: 'PIECE_COUNT_OVER',
      label: `품수 초과 (${declared}품 / 부속 ${list.length}건)`,
      detail: '세트명이 밝힌 품수보다 부속이 많습니다. 옵션 품목이 섞였을 수 있습니다.',
      diff,
    }
  }

  // C. 같은 슬롯의 대체 부속(국산/OEM)이 함께 들어 있다. 목록은 맞고 합계 내는 방식이 다르다.
  if (hasDuplicateName(list)) {
    return {
      level: 'info',
      code: 'ALTERNATIVE_PARTS',
      label: '대체 부속 포함',
      detail: '같은 슬롯의 대체 부속(국산/OEM 등)이 함께 있어 단순 합계로는 대조할 수 없습니다.',
      diff,
    }
  }

  // F. 택1 옵션이 둘 다 계상됐다 — 초과분이 부속 1건 단가와 정확히 맞아떨어진다.
  const excessPart = partPricedAt(list, diff)
  if (excessPart) {
    return {
      level: 'info',
      code: 'OPTION_PICK_ONE',
      label: '택1 옵션 포함',
      detail: `차액이 부속 1건(${excessPart.productName})의 단가와 같습니다. 둘 중 하나만 고르는 옵션으로 보입니다.`,
      diff,
    }
  }

  // F''. 부족분이 부속 1건 금액과 정확히 맞아떨어진다 — 그 부속이 한 번 더 들어가야 하는데
  //      수량에 반영되지 않았다. §8 잔여 ② 해소로 정상 경로에서는 나오지 않아야 하고,
  //      나온다면 파싱이 반복 행을 놓쳤다는 뜻이다. 안전망으로 남긴다.
  const doubledPart = partPricedAt(list, -diff)
  if (doubledPart) {
    return {
      level: 'info',
      code: 'PART_COUNTED_ONCE',
      label: '부속 수량 미반영',
      detail: `부족분이 부속 1건(${doubledPart.productName})의 단가와 같습니다. 이 부속이 2개 들어가지만 구성 목록에는 1건으로만 잡힙니다.`,
      diff,
    }
  }

  // F'. 본품 성격 부속이 2건 이상이다(MAIN + 도기(4") 조합). 택1과 같은 성격이라 합계가 성립하지 않는다.
  const mainCount = countMainBody(list)
  if (mainCount >= 2) {
    return {
      level: 'info',
      code: 'MAIN_DUPLICATED',
      label: '본품 택1 포함',
      detail: `본품 성격 부속이 ${mainCount}건입니다. 둘 중 하나만 고르는 구성으로 보입니다.`,
      diff,
    }
  }

  // E. 부속 목록에 본품이 없다. 세트가에는 본품이 들어 있어 비교 대상이 애초에 다르다.
  //     단 "N품 세트"(악세사리)는 본품이 따로 없고 부속 합이 곧 세트가라 이 사유가 성립하지 않는다.
  //     본품 단가를 아는 경우(G-2)는 이미 더해서 비교했으므로 이 사유가 성립하지 않는다 —
  //     여기까지 왔다면 실제 불일치다. 그 경우 아래 G로 떨어뜨린다.
  if (mainCount === 0 && declared == null && main == null) {
    return {
      level: 'info',
      code: 'MAIN_EXCLUDED',
      label: '본품 미포함',
      detail: '부속 목록에 본품이 없어 세트가와 비교 대상이 다릅니다.',
      diff,
    }
  }

  // G. 어느 사유에도 해당하지 않는다 — 원본 금액이 실제로 어긋났을 수 있다.
  return {
    level: 'error',
    code: 'AMOUNT_MISMATCH',
    label: `차이 ${diff.toLocaleString('ko-KR')}`,
    detail: '알려진 사유에 해당하지 않는 차액입니다. 원본 확인이 필요합니다.',
    diff,
  }
}

/** 배지 색 — level 단일 출처. */
export const PARTS_SUM_BADGE_CLASS = {
  match: 'text-bg-success',
  info: 'text-bg-light border',
  error: 'text-bg-warning',
}
