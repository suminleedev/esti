<script setup>
// 목록의 로딩/빈 상태 표준 컴포넌트.
// loading=true면 스피너+문구, 아니면 아이콘+메시지(+CTA 슬롯).
defineProps({
  loading: { type: Boolean, default: false },
  icon: { type: String, default: "bi-inbox" },
  message: { type: String, default: "데이터가 없습니다." },
  loadingText: { type: String, default: "불러오는 중..." },
});
</script>

<template>
  <div class="text-center text-muted py-4">
    <template v-if="loading">
      <span
        class="spinner-border spinner-border-sm me-2"
        role="status"
        aria-hidden="true"
      ></span>
      {{ loadingText }}
    </template>
    <template v-else>
      <i class="bi d-block mb-2 empty-icon" :class="icon"></i>
      <div class="small">{{ message }}</div>
      <div v-if="$slots.cta" class="mt-3">
        <slot name="cta" />
      </div>
    </template>
  </div>
</template>

<style scoped>
.empty-icon {
  font-size: 1.8rem;
  opacity: 0.45;
}
</style>
