import { ref } from "vue";

// 비차단 알림(Toast) 전역 싱글턴 — 앱 전체가 하나의 큐를 공유한다.
// 성공은 자동 소멸(기본 3초), 실패는 수동 닫기(delay=0)로 남긴다.
const toasts = ref([]);
let seq = 0;

const SUCCESS_DELAY = 3000;

function push(type, message, { delay } = {}) {
  const id = ++seq;
  toasts.value.push({ id, type, message });

  // delay 미지정 시: 성공=자동 소멸, 그 외=수동 닫기
  const ms = delay !== undefined ? delay : type === "success" ? SUCCESS_DELAY : 0;
  if (ms > 0) {
    setTimeout(() => remove(id), ms);
  }
  return id;
}

function remove(id) {
  toasts.value = toasts.value.filter((t) => t.id !== id);
}

export function useToast() {
  return {
    toasts, // ToastHost가 렌더에 사용
    success: (message, opts) => push("success", message, opts),
    error: (message, opts) => push("error", message, opts),
    info: (message, opts) => push("info", message, opts),
    remove,
  };
}
