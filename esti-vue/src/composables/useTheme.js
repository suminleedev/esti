import { readonly, ref } from 'vue'

// 화면 색 테마 — 청록(기본) / 쪽빛 / 클레이. 색 값은 assets/esti-theme.css에 있다.
// 선택은 이 브라우저의 localStorage에만 저장한다(컴퓨터마다 따로). 적용은 <html data-esti-theme>.
// 첫 화면이 그려지기 전 적용은 index.html 인라인 스크립트가 같은 키로 먼저 한다 —
// 테마를 늘리거나 키를 바꾸면 그 스크립트도 같이 고친다(useTheme.spec.js가 키 목록을 지킨다).

export const STORAGE_KEY = 'esti-theme'
export const DEFAULT_THEME = 'teal'

export const THEMES = [
  { key: 'teal', label: '청록', withRo: '청록으로', desc: '물빛과 백자', swatch: '#137a8d', isDefault: true },
  { key: 'indigo', label: '쪽빛', withRo: '쪽빛으로', desc: '먹빛 남색과 한지', swatch: '#3e56a5' },
  { key: 'clay', label: '클레이', withRo: '클레이로', desc: '구운 흙과 아이보리', swatch: '#b3532e' },
]

const isKnown = (key) => THEMES.some((t) => t.key === key)

function safeStorage() {
  try {
    return typeof window === 'undefined' ? null : window.localStorage
  } catch {
    return null // 사생활 보호 모드 등에서 접근 자체가 막히는 경우
  }
}

/** 저장된 테마 키. 없거나 모르는 값이거나 읽을 수 없으면 기본(청록). */
export function readSaved(storage = safeStorage()) {
  try {
    const saved = storage?.getItem(STORAGE_KEY)
    return isKnown(saved) ? saved : DEFAULT_THEME
  } catch {
    return DEFAULT_THEME
  }
}

/** 청록은 속성을 지우고(:root가 청록), 나머지는 data-esti-theme을 단다. */
export function applyTheme(key, root) {
  if (key === DEFAULT_THEME) root.removeAttribute('data-esti-theme')
  else root.setAttribute('data-esti-theme', key)
}

// 앱 전체가 하나를 공유한다(useToast와 같은 싱글턴)
const current = ref(readSaved())

export function useTheme() {
  function setTheme(key) {
    if (!isKnown(key)) return
    current.value = key
    if (typeof document !== 'undefined') applyTheme(key, document.documentElement)
    try {
      safeStorage()?.setItem(STORAGE_KEY, key)
    } catch {
      // 저장할 수 없어도 이번 화면에는 적용된다
    }
  }
  return { theme: readonly(current), themes: THEMES, setTheme }
}
