<script setup>
import { ref, watch, nextTick, onBeforeUnmount } from "vue";
import { useConfirm } from "@/composables/useConfirm";

// 앱 루트에 1회 마운트되는 확인 모달 호스트. useConfirm()의 state를 소비한다.
const { state, _resolve } = useConfirm();
const confirmBtn = ref(null);

function onKeydown(e) {
  if (e.key === "Escape") _resolve(false);
}

watch(state, async (val) => {
  if (val) {
    window.addEventListener("keydown", onKeydown);
    await nextTick();
    confirmBtn.value?.focus();
  } else {
    window.removeEventListener("keydown", onKeydown);
  }
});

onBeforeUnmount(() => window.removeEventListener("keydown", onKeydown));
</script>

<template>
  <div v-if="state">
    <div class="modal-backdrop fade show"></div>
    <div class="modal fade show d-block" tabindex="-1" role="dialog">
      <div class="modal-dialog modal-dialog-centered">
        <div class="modal-content">
          <div class="modal-header">
            <h2 class="modal-title h5 mb-0">{{ state.title }}</h2>
            <button
              type="button"
              class="btn-close"
              aria-label="닫기"
              @click="_resolve(false)"
            ></button>
          </div>
          <div class="modal-body">
            <p class="mb-0 confirm-message">{{ state.message }}</p>
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-outline-secondary" @click="_resolve(false)">
              {{ state.cancelLabel }}
            </button>
            <button
              ref="confirmBtn"
              type="button"
              class="btn"
              :class="`btn-${state.variant}`"
              @click="_resolve(true)"
            >
              {{ state.confirmLabel }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* 대상 이름 등 줄바꿈(\n)을 보존해 표시 */
.confirm-message {
  white-space: pre-line;
}
</style>
