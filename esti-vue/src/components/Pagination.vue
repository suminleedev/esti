<!-- 페이지네이션 -->
<template>
<nav class="d-flex justify-content-center mt-3" aria-label="Page navigation" v-if="totalPages > 0">
  <ul class="pagination pagination-sm mb-0">
    <!-- 맨앞 -->
    <li class="page-item" :class="{ disabled: page === 0 }">
      <button class="page-link" @click="$emit('first')" :disabled="page === 0">«</button>
    </li>
    <!-- 10개 이전 블록 -->
    <li class="page-item" :class="{ disabled: page < blockSize }">
      <button class="page-link" @click="$emit('prevBlock')" :disabled="page < blockSize"> ‹ </button>
    </li>
    <!-- 숫자 페이지 -->
    <li v-for="p in pageNumbers" :key="p" class="page-item" :class="{ active: p === page }">
      <button class="page-link" @click="$emit('go', p)">{{ p + 1 }}</button>
    </li>
    <!-- 10개 다음 블록 -->
    <li class="page-item" :class="{ disabled: Math.floor(page / blockSize) === Math.floor((totalPages - 1) / blockSize) }">
      <button class="page-link" @click="$emit('nextBlock')"
              :disabled="Math.floor(page / blockSize) === Math.floor((totalPages - 1) / blockSize)"> › </button>
    </li>
    <!-- 맨끝 -->
    <li class="page-item" :class="{ disabled: page >= totalPages - 1 }">
      <button class="page-link" @click="$emit('last')" :disabled="page >= totalPages - 1">»</button>
    </li>
  </ul>
</nav>

<!-- 하단 요약 -->
<div class="text-center text-muted small mt-2" v-if="totalElements > 0">
{{ totalElements.toLocaleString() }}건 중
{{ (page * size + 1).toLocaleString() }} -
{{ Math.min((page + 1) * size, totalElements).toLocaleString() }} 표시
</div>
</template>
<script setup lang="ts">
defineProps({
  size: Number,
  page: Number,
  totalPages: Number,
  pageNumbers: Array,
  blockSize: Number,
  totalElements: Number,
})

defineEmits(["go", "first", "last", "prevBlock", "nextBlock"]);
</script>
