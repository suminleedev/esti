// 제품 이미지 경로 유틸 테스트
//
// 이 파일이 지키는 것은 두 가지다.
//   ① «브라우저가 못 그리는 포맷»은 요청조차 하지 않고 대체 이미지로 간다
//   ② 그 외에는 BASE_URL + 상대경로 조합이 유일한 규칙이다
//
// ①은 방어 코드라 평소에는 안 걸린다. 적재 시점에 서버가 PNG로 바꾸기 때문이다(P0).
// 걸리는 건 변환 실패분·구버전 잔존분인데, 그게 바로 «있는지 모르고 지나가기 쉬운» 경로다.
//
// BASE_URL은 값을 단정하지 않고 함께 읽어 조합만 검증한다 — 환경변수라 실행 환경마다 다르다.

import { describe, it, expect } from 'vitest'
import { productImage } from './image'
import { BASE_URL } from '@/config/api'
import noImg from '@/assets/no-image.svg'

describe('productImage — 값이 없을 때', () => {
  it('null·undefined·빈 문자열이면 대체 이미지다', () => {
    expect(productImage(null)).toBe(noImg)
    expect(productImage(undefined)).toBe(noImg)
    expect(productImage('')).toBe(noImg)
  })
})

describe('productImage — 렌더링 불가 포맷', () => {
  // 엑셀 임베디드 벡터 포맷. <img>로 못 그리므로 요청 자체를 하지 않는다.
  it('.emf·.wmf는 대체 이미지다', () => {
    expect(productImage('/uploads/product-images/a.emf')).toBe(noImg)
    expect(productImage('/uploads/product-images/a.wmf')).toBe(noImg)
  })

  it('대소문자를 가리지 않는다', () => {
    expect(productImage('/uploads/product-images/a.EMF')).toBe(noImg)
    expect(productImage('/uploads/product-images/a.Wmf')).toBe(noImg)
  })

  // 판정은 «끝»에서만 한다. 경로 중간에 섞인 글자까지 막으면 멀쩡한 이미지가 사라진다.
  it('확장자 자리가 아니면 막지 않는다', () => {
    expect(productImage('/uploads/a.emf/b.png')).toBe(`${BASE_URL}/uploads/a.emf/b.png`)
    expect(productImage('/uploads/a.emfx')).toBe(`${BASE_URL}/uploads/a.emfx`)
    expect(productImage('/uploads/emf.png')).toBe(`${BASE_URL}/uploads/emf.png`)
  })
})

describe('productImage — 정상 경로', () => {
  it('BASE_URL을 앞에 붙인다', () => {
    expect(productImage('/uploads/product-images/x.png')).toBe(`${BASE_URL}/uploads/product-images/x.png`)
    expect(productImage('/uploads/product-images/x.jpg')).toBe(`${BASE_URL}/uploads/product-images/x.jpg`)
  })

  // 크롤러가 Content-Type으로 확장자를 정하므로(I-6) 대문자 확장자도 들어올 수 있다.
  it('그 외 확장자는 그대로 통과시킨다', () => {
    expect(productImage('/uploads/x.PNG')).toBe(`${BASE_URL}/uploads/x.PNG`)
    expect(productImage('/uploads/x.gif')).toBe(`${BASE_URL}/uploads/x.gif`)
  })
})
