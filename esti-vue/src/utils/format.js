// 금액·숫자·날짜 표기 공용 유틸 (표시 계층 단일 출처)
// Phase 3-6: 화면마다 흩어진 toLocaleString/currency 인라인 호출을 이 유틸로 수렴한다.

/** 문자열/숫자/null을 숫자로 안전 변환. 변환 불가 시 null. */
function toNum(v) {
  if (v == null || v === '') return null
  const n = typeof v === 'number' ? v : Number(String(v).replace(/,/g, ''))
  return Number.isFinite(n) ? n : null
}

/** 천 단위 구분 숫자. 값 없으면 '-' (예: 1234000 → "1,234,000") */
export function number(v) {
  const n = toNum(v)
  return n == null ? '-' : n.toLocaleString('ko-KR')
}

/** 금액(원 접미). 값 없으면 '-' (예: 1234000 → "1,234,000원") */
export function won(v) {
  const n = toNum(v)
  return n == null ? '-' : n.toLocaleString('ko-KR') + '원'
}

/** 날짜 YYYY-MM-DD 통일. 값 없으면 '-' */
export function date(v) {
  if (!v) return '-'
  // 이미 YYYY-MM-DD(이상) 문자열이면 앞 10자리만
  if (typeof v === 'string' && /^\d{4}-\d{2}-\d{2}/.test(v)) return v.slice(0, 10)
  const d = new Date(v)
  if (Number.isNaN(d.getTime())) return String(v)
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}
