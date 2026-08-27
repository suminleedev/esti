// 상태·선택지 라벨 단일 출처.
// 화면에 표시되는 용어는 반드시 이 파일에서 가져온다. (DB enum 값은 키로만 사용)

export const PROPOSAL_STATUS = {
  DRAFT: { label: '임시저장', badgeClass: 'bg-secondary' },
  SUBMITTED: { label: '작성완료', badgeClass: 'bg-primary' },
  SENT: { label: '발송완료', badgeClass: 'bg-success' },
}

export const APARTMENT_TYPES = ['59㎡', '74㎡', '84㎡']

// 단위 — 견적서 C열(O-1b). 대부분 SET이고 악세사리 일부가 EA다.
export const UNITS = ['SET', 'EA']
export const UNIT_DEFAULT = 'SET'

// 건물구분·적용부위·적용카테고리는 더 이상 여기 없다 —
// 사용자가 화면(/settings/master)에서 직접 관리하는 DB 마스터로 옮겼다(Phase 7).
// 선택지는 useMasterCodes()로 받고, 아래는 그 세 종류의 화면 표기(설정 탭 제목 등)다.
export const MASTER_TYPES = [
  { key: 'BUILDING_TYPE', label: '건물 구분', hint: '견적서의 본동·부속동 섹션을 나누는 기준입니다.' },
  { key: 'AREA', label: '적용 부위', hint: '제안서 STEP 2의 적용 부위 선택지입니다.' },
  { key: 'CATEGORY', label: '적용 카테고리', hint: '제안서의 필수 위생기구 유형 선택지입니다.' },
]
