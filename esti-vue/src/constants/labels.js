// 상태·선택지 라벨 단일 출처.
// 화면에 표시되는 용어는 반드시 이 파일에서 가져온다. (DB enum 값은 키로만 사용)

export const PROPOSAL_STATUS = {
  DRAFT: { label: '임시저장', badgeClass: 'bg-secondary' },
  SUBMITTED: { label: '작성완료', badgeClass: 'bg-primary' },
  SENT: { label: '발송완료', badgeClass: 'bg-success' },
}

export const APARTMENT_TYPES = ['59㎡', '74㎡', '84㎡']

// 건물 구분 — 견적서의 본동/부속동 섹션 분리 기준(O-5).
// 현장마다 값이 추가될 수 있어 드롭다운은 직접 입력도 허용한다. 마스터 관리는 Phase 7.
export const BUILDING_TYPES = ['본세대', '부속동', '상가']

// 단위 — 견적서 C열(O-1b). 대부분 SET이고 악세사리 일부가 EA다.
export const UNITS = ['SET', 'EA']
export const UNIT_DEFAULT = 'SET'

export const AREAS = ['욕실1', '욕실2', '욕실 공통', '주방', '세탁실', '다용도실']

export const CATEGORIES = [
  '양변기',
  '비데',
  '세면기',
  '세면기 수전',
  '욕조 수전/슬라이드바',
  '해바라기샤워수전',
  '씽크수전',
  '악세사리',
]
