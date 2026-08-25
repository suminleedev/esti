// 제품 이미지 경로 유틸 (표시 계층 단일 출처)
// BASE_URL 조합과 "브라우저가 못 그리는 포맷" 판정을 한곳으로 모은다.

import { BASE_URL } from '@/config/api'
import noImg from '@/assets/no-image.svg'

// 엑셀 임베디드 이미지에서 넘어온 벡터 포맷. 브라우저가 <img>로 렌더링하지 못한다.
// 적재 시점에 서버가 PNG로 변환하므로(P0) 정상 경로에서는 더 이상 나오지 않는다.
// 변환 실패분·구버전 잔존분에 대한 방어로 남겨 둔다 — 어차피 onerror → 대체 이미지이므로 요청 자체를 하지 않는다.
const UNRENDERABLE = /\.(emf|wmf)$/i

/** 상품 imageUrl → 실제 <img src>. 값이 없거나 렌더링 불가 포맷이면 대체 이미지. */
export function productImage(url) {
  if (!url || UNRENDERABLE.test(url)) return noImg
  return `${BASE_URL}${url}`
}
