// 상태·선택지 라벨 단일 출처.
// 화면에 표시되는 용어는 반드시 이 파일에서 가져온다. (DB enum 값은 키로만 사용)

export const PROPOSAL_STATUS = {
  DRAFT: { label: '임시저장', badgeClass: 'bg-secondary' },
  SUBMITTED: { label: '작성완료', badgeClass: 'bg-primary' },
  SENT: { label: '발송완료', badgeClass: 'bg-success' },
}

export const APARTMENT_TYPES = ['24평', '32평', '40평', '48평', '59㎡', '74㎡', '84㎡']

export const AREAS = ['욕실1', '욕실2', '주방', '세탁실', '다용도실']

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
