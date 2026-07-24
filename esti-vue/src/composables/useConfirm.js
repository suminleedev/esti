import { ref } from "vue";

// 파괴적 액션 확인 모달 전역 싱글턴 — window.confirm 대체.
// 호출부는 `const ok = await confirm({ message: '...' })` 형태로 사용한다.
const state = ref(null); // 열려 있을 때만 { title, message, confirmLabel, cancelLabel, variant }
let resolver = null;

export function useConfirm() {
  // options: 문자열이면 message로 취급. 대상 이름을 message에 포함해 표시한다.
  function confirm(options) {
    const opts = typeof options === "string" ? { message: options } : options || {};
    state.value = {
      title: opts.title ?? "확인",
      message: opts.message ?? "",
      confirmLabel: opts.confirmLabel ?? "확인",
      cancelLabel: opts.cancelLabel ?? "취소",
      variant: opts.variant ?? "danger", // 파괴 기본 danger
    };
    return new Promise((resolve) => {
      resolver = resolve;
    });
  }

  // ConfirmDialog(호스트)가 확인/취소 시 호출
  function _resolve(result) {
    state.value = null;
    if (resolver) {
      resolver(result);
      resolver = null;
    }
  }

  return { state, confirm, _resolve };
}
