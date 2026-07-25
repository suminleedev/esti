import { ref } from "vue";

// 텍스트 입력 확인 모달 전역 싱글턴 — window.prompt 대체.
// 호출부는 `const value = await promptInput({ title, label, defaultValue })` 형태로 사용한다.
// 확인 시 입력 문자열(트림)을 반환하고, 취소/빈 값이면 null을 반환한다.
const state = ref(null); // 열려 있을 때만 { title, label, placeholder, defaultValue, confirmLabel, cancelLabel, variant }
let resolver = null;

export function usePrompt() {
  function promptInput(options) {
    const opts = typeof options === "string" ? { label: options } : options || {};
    state.value = {
      title: opts.title ?? "입력",
      label: opts.label ?? "",
      placeholder: opts.placeholder ?? "",
      defaultValue: opts.defaultValue ?? "",
      confirmLabel: opts.confirmLabel ?? "저장",
      cancelLabel: opts.cancelLabel ?? "취소",
      variant: opts.variant ?? "primary",
    };
    return new Promise((resolve) => {
      resolver = resolve;
    });
  }

  // PromptDialog(호스트)가 확인/취소 시 호출. 확인=문자열, 취소=null
  function _resolve(result) {
    state.value = null;
    if (resolver) {
      resolver(result);
      resolver = null;
    }
  }

  return { state, promptInput, _resolve };
}
