<script setup>
// 화면 색 선택 — 고르는 즉시 바뀌고, 이 컴퓨터에만 저장된다.
// 부모님이 실수로 바꾸지 않도록 헤더가 아니라 설정 화면(마스터 관리) 아래쪽에 둔다.
import { useTheme } from '@/composables/useTheme'
import { useToast } from '@/composables/useToast'

const { theme, themes, setTheme } = useTheme()
const toast = useToast()

function pick(t) {
  if (t.key === theme.value) return
  setTheme(t.key)
  toast.success(`화면 색을 ${t.withRo} 바꿨습니다.`)
}
</script>

<template>
  <div class="card mt-3">
    <div class="card-header"><strong>화면 색</strong></div>
    <div class="card-body">
      <p class="text-muted small mb-2">
        고르는 즉시 바뀌고 이 컴퓨터에만 저장됩니다. 다른 컴퓨터의 화면은 그대로입니다.
      </p>
      <div class="d-flex flex-wrap gap-2" role="radiogroup" aria-label="화면 색">
        <template v-for="t in themes" :key="t.key">
          <input
            :id="`esti-theme-${t.key}`"
            class="btn-check"
            type="radio"
            name="esti-theme"
            :value="t.key"
            :checked="theme === t.key"
            @change="pick(t)"
          />
          <label class="btn btn-outline-secondary theme-option" :for="`esti-theme-${t.key}`">
            <span class="swatch" :style="{ background: t.swatch }" aria-hidden="true"></span>
            <span class="fw-semibold">{{ t.label }}</span>
            <span v-if="t.isDefault" class="small"> (기본)</span>
            <span class="d-block small">{{ t.desc }}</span>
          </label>
        </template>
      </div>
    </div>
  </div>
</template>

<style scoped>
.theme-option {
  min-width: 10rem;
  text-align: left;
}

/* 테마마다 고정된 미리보기 색 — 지금 테마와 상관없이 그 테마의 메인 컬러를 보여 준다 */
.swatch {
  display: inline-block;
  width: 0.875rem;
  height: 0.875rem;
  margin-right: 0.375rem;
  vertical-align: -0.125rem;
  border-radius: 50%;
  box-shadow: inset 0 0 0 1px rgba(0, 0, 0, 0.15);
}

/* 고른 칸은 어두운 채움이라, 색점이 묻히지 않게 흰 테두리를 두른다 */
.btn-check:checked + .theme-option .swatch {
  box-shadow: 0 0 0 2px #fff;
}
</style>
