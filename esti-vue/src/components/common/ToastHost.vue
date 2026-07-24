<script setup>
import { useToast } from "@/composables/useToast";

// 앱 루트에 1회 마운트되는 토스트 렌더 호스트.
const { toasts, remove } = useToast();

const variantClass = {
  success: "text-bg-success",
  error: "text-bg-danger",
  info: "text-bg-primary",
};
</script>

<template>
  <div class="toast-container position-fixed top-0 end-0 p-3">
    <div
      v-for="t in toasts"
      :key="t.id"
      class="toast show align-items-center border-0"
      :class="variantClass[t.type] || 'text-bg-secondary'"
      role="alert"
      aria-live="assertive"
      aria-atomic="true"
    >
      <div class="d-flex">
        <div class="toast-body">{{ t.message }}</div>
        <button
          type="button"
          class="btn-close btn-close-white me-2 m-auto"
          aria-label="닫기"
          @click="remove(t.id)"
        ></button>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 모달(1055)보다 위에 표시해 확인 후 결과 토스트가 가려지지 않게 한다. */
.toast-container {
  z-index: 1090;
}
</style>
