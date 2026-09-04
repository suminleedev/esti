<!-- 페이지네이션 -->
<template>
<nav class="d-flex justify-content-center mt-3" aria-label="Page navigation" v-if="totalPages > 0">
  <ul class="pagination pagination-sm mb-0">
    <!-- 맨앞 -->
    <li class="page-item" :class="{ disabled: page === 0 }">
      <button class="page-link" @click="$emit('first')" :disabled="page === 0"
              title="첫 페이지" aria-label="첫 페이지">«</button>
    </li>
    <!-- 이전 블록 (10페이지 단위). 블록이 하나뿐이면 아예 내보내지 않는다 — F-021 -->
    <li v-if="hasMultipleBlocks" class="page-item" :class="{ disabled: page < blockSize }">
      <button class="page-link" @click="$emit('prevBlock')" :disabled="page < blockSize"
              :title="`이전 ${blockSize}페이지`" :aria-label="`이전 ${blockSize}페이지`"> ‹ </button>
    </li>
    <!-- 숫자 페이지 -->
    <li v-for="p in pageNumbers" :key="p" class="page-item" :class="{ active: p === page }">
      <button class="page-link" @click="$emit('go', p)"
              :aria-label="`${p + 1}페이지`" :aria-current="p === page ? 'page' : undefined">{{ p + 1 }}</button>
    </li>
    <!-- 다음 블록 (10페이지 단위) -->
    <li v-if="hasMultipleBlocks" class="page-item" :class="{ disabled: isLastBlock }">
      <button class="page-link" @click="$emit('nextBlock')" :disabled="isLastBlock"
              :title="`다음 ${blockSize}페이지`" :aria-label="`다음 ${blockSize}페이지`"> › </button>
    </li>
    <!-- 맨끝 -->
    <li class="page-item" :class="{ disabled: page >= totalPages - 1 }">
      <button class="page-link" @click="$emit('last')" :disabled="page >= totalPages - 1"
              title="마지막 페이지" aria-label="마지막 페이지">»</button>
    </li>
  </ul>
</nav>

<!-- 하단 요약 -->
<div class="text-center text-muted small mt-2" v-if="totalElements > 0">
{{ number(totalElements) }}건 중
{{ number(page * size + 1) }} -
{{ number(Math.min((page + 1) * size, totalElements)) }} 표시
</div>
</template>
<script setup>
import { computed } from "vue";
import { number } from "@/utils/format";

const props = defineProps({
  size: Number,
  page: Number,
  totalPages: Number,
  pageNumbers: Array,
  blockSize: Number,
  totalElements: Number,
})

defineEmits(["go", "first", "last", "prevBlock", "nextBlock"]);

/**
 * «‹»·«›»는 페이지가 아니라 <b>블록</b>(10페이지 묶음)을 건너뛰는 버튼이다.
 * 페이지가 한 블록 안에 다 들어가면 건너뛸 곳이 없어 늘 비활성인데,
 * 숫자 바로 옆에 붙어 있어 "다음 페이지 버튼이 고장 났다"로 읽혔다(F-021).
 * 그럴 때는 내보내지 않는다 — 죽은 버튼을 설명하는 것보다 없애는 편이 낫다.
 */
const hasMultipleBlocks = computed(() => props.totalPages > props.blockSize);

const isLastBlock = computed(
  () => Math.floor(props.page / props.blockSize) === Math.floor((props.totalPages - 1) / props.blockSize)
);
</script>

<style scoped>
</style>
