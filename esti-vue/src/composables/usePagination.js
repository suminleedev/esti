import { ref, computed } from "vue";

export function usePagination(loadFn) {
  const page = ref(0); // 0부터 시작
  const size = ref(10);

  const totalPages = ref(0);
  const totalElements = ref(0);

  const blockSize = ref(10);  // 페이지 버튼에 보여줄 최대 개수

  const pageNumbers = computed(() => {
    const tp = totalPages.value;
    if (!tp) return [];

    const current = page.value;
    const block = blockSize.value;

    const start = Math.floor(current / block) * block;
    const end = Math.min(tp - 1, start + block - 1);

    const pages = [];
    for (let i = start; i <= end; i++) pages.push(i);

    return pages;
  });

  async function goToPage(p) {
    if (p < 0 || p >= totalPages.value) return;
    if (p === page.value) return;

    page.value = p;
    await Promise.resolve(loadFn());
  }

  async function firstPage() {
    await goToPage(0);
  }

  async function lastPage() {
    if (!totalPages.value) return;
    await goToPage(totalPages.value - 1);
  }

  async function prevBlock() {
    const block = blockSize.value;
    const start = Math.floor(page.value / block) * block;
    const target = start - block;
    if (target < 0) return;
    await goToPage(target);
  }

  async function nextBlock() {
    const block = blockSize.value;
    const start = Math.floor(page.value / block) * block;
    const target = start + block;
    if (target >= totalPages.value) return;
    await goToPage(target);
  }

  function resetToFirst() {
    page.value = 0;
  }

  /**
   * 서버 페이지 응답을 페이징 상태에 반영한다.
   *
   * 백엔드가 `serialization-mode=via-dto`라 메타가 `page` 아래로 모여 온다:
   *   { content: [...], page: { size, number, totalElements, totalPages } }
   *
   * 예전 평평한 모양(`{ content, totalPages, totalElements, number }`)도 함께 읽는다.
   * 이 한 곳만 보면 되도록 모아 둔 것이라, 두 모양을 아는 자리도 여기 하나여야 한다.
   *
   * @returns {Array} 목록 본문(content). 없으면 빈 배열.
   */
  function applyPage(data) {
    const meta = data?.page ?? data ?? {};
    totalPages.value = meta.totalPages ?? 0;
    totalElements.value = meta.totalElements ?? 0;
    // 서버가 보정한 현재 페이지(범위를 넘겨 요청했을 때 되돌아온다)
    page.value = meta.number ?? page.value;
    return data?.content ?? [];
  }

  /** 조회에 실패했을 때의 빈 상태. 목록을 비우는 쪽은 호출부가 맡는다. */
  function clearPage() {
    totalPages.value = 0;
    totalElements.value = 0;
  }

  return {
    page,
    size,
    totalPages,
    totalElements,
    applyPage,
    clearPage,
    blockSize,     // UI에서 blockSize가 필요하면 사용
    pageNumbers,
    goToPage,
    firstPage,
    lastPage,
    prevBlock,
    nextBlock,
    resetToFirst,  // 필터 변경 시 page=0 리셋용
  };
}
