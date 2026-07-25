<script setup>
import { ref, watch, nextTick, onBeforeUnmount } from "vue";
import { usePrompt } from "@/composables/usePrompt";

// 앱 루트에 1회 마운트되는 입력 모달 호스트. usePrompt()의 state를 소비한다.
const { state, _resolve } = usePrompt();
const inputRef = ref(null);
const value = ref("");

function onKeydown(e) {
  if (e.key === "Escape") _resolve(null);
}

// 확인: 트림한 값이 있으면 반환, 비어 있으면 취소로 처리
function submit() {
  const v = value.value.trim();
  _resolve(v ? v : null);
}

watch(state, async (val) => {
  if (val) {
    value.value = val.defaultValue;
    window.addEventListener("keydown", onKeydown);
    await nextTick();
    inputRef.value?.focus();
    inputRef.value?.select();
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
              @click="_resolve(null)"
            ></button>
          </div>
          <div class="modal-body">
            <label v-if="state.label" class="form-label">{{ state.label }}</label>
            <input
              ref="inputRef"
              v-model="value"
              type="text"
              class="form-control"
              :placeholder="state.placeholder"
              @keyup.enter="submit"
            />
          </div>
          <div class="modal-footer">
            <button type="button" class="btn btn-outline-secondary" @click="_resolve(null)">
              {{ state.cancelLabel }}
            </button>
            <button
              type="button"
              class="btn"
              :class="`btn-${state.variant}`"
              @click="submit"
            >
              {{ state.confirmLabel }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
