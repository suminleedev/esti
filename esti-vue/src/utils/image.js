// 제품 이미지 경로 유틸 (표시 계층 단일 출처)
// BASE_URL 조합과 "브라우저가 못 그리는 포맷" 판정을 한곳으로 모은다.

import { BASE_URL } from '@/config/api'
import noImg from '@/assets/no-image.svg'

// 엑셀 임베디드 이미지에서 넘어온 벡터 포맷. 브라우저가 <img>로 렌더링하지 못해
// 어차피 onerror → 대체 이미지로 떨어지므로, 요청 자체를 하지 않는다.
// (현재 카탈로그 기준 EMF 16개가 11.6MB로 전체 이미지 용량의 절반을 차지한다.)
const UNRENDERABLE = /\.(emf|wmf)$/i

/** 상품 imageUrl → 실제 <img src>. 값이 없거나 렌더링 불가 포맷이면 대체 이미지. */
export function productImage(url) {
  if (!url || UNRENDERABLE.test(url)) return noImg
  return `${BASE_URL}${url}`
}
