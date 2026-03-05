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

  return {
    page,
    size,
    totalPages,
    totalElements,
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
