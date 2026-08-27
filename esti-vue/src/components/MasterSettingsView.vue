<template>
  <main class="container-fluid px-3 py-3">
    <div class="d-flex align-items-center justify-content-between mb-3">
      <div>
        <h4 class="mb-1">마스터 관리</h4>
        <p class="text-muted small mb-0">
          제안서 화면의 선택지를 직접 관리합니다. 여기서 바꾼 값은 <strong>앞으로 만드는 제안서</strong>에 적용되고,
          이미 저장된 제안서의 값은 그대로 유지됩니다.
        </p>
      </div>
    </div>

    <ul class="nav nav-tabs mb-3">
      <li class="nav-item" v-for="t in MASTER_TYPES" :key="t.key">
        <button
          class="nav-link"
          :class="{ active: activeType === t.key }"
          type="button"
          @click="selectType(t.key)"
        >
          {{ t.label }}
          <span class="badge rounded-pill bg-light text-muted ms-1">{{ activeCount(t.key) }}</span>
        </button>
      </li>
    </ul>

    <div class="card">
      <div class="card-header d-flex justify-content-between align-items-center">
        <strong>{{ currentType.label }}</strong>
        <small class="text-muted">{{ currentType.hint }}</small>
      </div>

      <div class="card-body">
        <!-- 추가 -->
        <form class="row g-2 align-items-center mb-3" @submit.prevent="addItem">
          <div class="col-md-5">
            <label class="visually-hidden" :for="`new-${activeType}`">새 {{ currentType.label }}</label>
            <input
              :id="`new-${activeType}`"
              v-model.trim="newLabel"
              class="form-control form-control-sm"
              :placeholder="`새 ${currentType.label} 이름`"
              maxlength="100"
            />
          </div>
          <div class="col-auto">
            <button class="btn btn-primary btn-sm" type="submit" :disabled="!newLabel || busy">
              <i class="bi bi-plus-lg" aria-hidden="true"></i> 추가
            </button>
          </div>
        </form>

        <EmptyState
          v-if="loading || !items.length"
          :loading="loading"
          message="등록된 값이 없습니다. 위에서 추가하세요."
        />

        <div v-else class="table-responsive">
          <table class="table table-sm align-middle mb-0">
            <thead>
              <tr>
                <th style="width: 90px">순서</th>
                <th>이름</th>
                <th style="width: 100px">상태</th>
                <th style="width: 170px" class="text-end">관리</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(item, i) in items" :key="item.id" :class="{ 'opacity-50': !item.active }">
                <td>
                  <div class="btn-group btn-group-sm" role="group" aria-label="순서 변경">
                    <button
                      class="btn btn-outline-secondary"
                      type="button"
                      :disabled="i === 0 || busy"
                      :aria-label="`${item.label} 위로`"
                      @click="move(i, -1)"
                    >
                      <i class="bi bi-arrow-up" aria-hidden="true"></i>
                    </button>
                    <button
                      class="btn btn-outline-secondary"
                      type="button"
                      :disabled="i === items.length - 1 || busy"
                      :aria-label="`${item.label} 아래로`"
                      @click="move(i, 1)"
                    >
                      <i class="bi bi-arrow-down" aria-hidden="true"></i>
                    </button>
                  </div>
                </td>
                <td>{{ item.label }}</td>
                <td>
                  <span class="badge" :class="item.active ? 'bg-success' : 'bg-secondary'">
                    {{ item.active ? '사용' : '숨김' }}
                  </span>
                </td>
                <td class="text-end">
                  <button class="btn btn-outline-secondary btn-sm me-1" type="button" :disabled="busy" @click="rename(item)">
                    이름 변경
                  </button>
                  <button
                    v-if="item.active"
                    class="btn btn-outline-danger btn-sm"
                    type="button"
                    :disabled="busy"
                    @click="hide(item)"
                  >
                    숨김
                  </button>
                  <button v-else class="btn btn-outline-primary btn-sm" type="button" :disabled="busy" @click="restore(item)">
                    복원
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <p class="form-text mt-3 mb-0">
          삭제 대신 <strong>숨김</strong>입니다 — 그 값을 이미 쓰고 있는 제안서가 깨지지 않도록,
          선택지에서만 빼고 기록은 남깁니다. 언제든 복원할 수 있습니다.
        </p>
      </div>
    </div>
  </main>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import axios from 'axios'
import EmptyState from '@/components/common/EmptyState.vue'
import { MASTER_TYPES } from '@/constants/labels'
import { useToast } from '@/composables/useToast'
import { useConfirm } from '@/composables/useConfirm'
import { usePrompt } from '@/composables/usePrompt'
import { useMasterCodes } from '@/composables/useMasterCodes'

const toast = useToast()
const { confirm } = useConfirm()
const { promptInput } = usePrompt()
const { invalidate, load } = useMasterCodes()

const activeType = ref(MASTER_TYPES[0].key)
const items = ref([])
const counts = ref({}) // 탭 배지 — 종류별 '사용' 개수
const newLabel = ref('')
const loading = ref(false)
const busy = ref(false)

const currentType = computed(
  () => MASTER_TYPES.find((t) => t.key === activeType.value) ?? MASTER_TYPES[0],
)

function activeCount(key) {
  return counts.value[key] ?? 0
}

function errorMessage(e, fallback) {
  return e?.response?.data?.message || fallback
}

/** 목록을 바꾼 뒤엔 항상 여길 지난다 — 배지와 다른 화면의 선택지 캐시를 함께 맞춘다. */
function afterChange() {
  counts.value = { ...counts.value, [activeType.value]: items.value.filter((i) => i.active).length }
  invalidate()
  load({ force: true })
}

async function fetchItems() {
  loading.value = true
  try {
    const res = await axios.get('/api/master-codes', { params: { type: activeType.value } })
    items.value = res.data
    counts.value = { ...counts.value, [activeType.value]: items.value.filter((i) => i.active).length }
  } catch (e) {
    toast.error(errorMessage(e, '목록을 불러오지 못했습니다.'))
  } finally {
    loading.value = false
  }
}

async function fetchCounts() {
  // 탭 배지용 — 진입 시 세 종류의 '사용' 개수를 한 번에 받는다
  try {
    const res = await axios.get('/api/master-codes/options')
    counts.value = Object.fromEntries(MASTER_TYPES.map((t) => [t.key, (res.data?.[t.key] ?? []).length]))
  } catch {
    // 배지는 없어도 화면이 동작한다 — 조용히 넘어간다
  }
}

function selectType(key) {
  if (activeType.value === key) return
  activeType.value = key
  newLabel.value = ''
  fetchItems()
}

async function addItem() {
  if (!newLabel.value || busy.value) return
  busy.value = true
  try {
    await axios.post('/api/master-codes', { type: activeType.value, label: newLabel.value })
    toast.success(`추가했습니다: ${newLabel.value}`)
    newLabel.value = ''
    await fetchItems()
    afterChange()
  } catch (e) {
    toast.error(errorMessage(e, '추가하지 못했습니다.'))
  } finally {
    busy.value = false
  }
}

async function rename(item) {
  const next = await promptInput({
    title: '이름 변경',
    label: `${currentType.value.label} 이름`,
    defaultValue: item.label,
  })
  if (!next || next === item.label) return

  busy.value = true
  try {
    await axios.put(`/api/master-codes/${item.id}`, { label: next })
    toast.success(`이름을 바꿨습니다: ${next}`)
    await fetchItems()
    afterChange()
  } catch (e) {
    toast.error(errorMessage(e, '이름을 바꾸지 못했습니다.'))
  } finally {
    busy.value = false
  }
}

async function hide(item) {
  const ok = await confirm({
    title: '선택지에서 숨기기',
    message: `'${item.label}'을(를) 선택지에서 숨깁니다. 이미 이 값을 쓰는 제안서는 그대로 유지됩니다.`,
    confirmLabel: '숨기기',
  })
  if (!ok) return

  busy.value = true
  try {
    await axios.delete(`/api/master-codes/${item.id}`)
    toast.success(`숨겼습니다: ${item.label}`)
    await fetchItems()
    afterChange()
  } catch (e) {
    toast.error(errorMessage(e, '숨기지 못했습니다.'))
  } finally {
    busy.value = false
  }
}

async function restore(item) {
  busy.value = true
  try {
    await axios.put(`/api/master-codes/${item.id}`, { active: true })
    toast.success(`복원했습니다: ${item.label}`)
    await fetchItems()
    afterChange()
  } catch (e) {
    toast.error(errorMessage(e, '복원하지 못했습니다.'))
  } finally {
    busy.value = false
  }
}

async function move(index, delta) {
  const target = index + delta
  if (target < 0 || target >= items.value.length) return

  const reordered = [...items.value]
  const [moved] = reordered.splice(index, 1)
  reordered.splice(target, 0, moved)

  // 낙관적 반영 — 서버가 거절하면 fetchItems()로 되돌아온다
  items.value = reordered
  busy.value = true
  try {
    const res = await axios.put('/api/master-codes/reorder', {
      type: activeType.value,
      ids: reordered.map((i) => i.id),
    })
    items.value = res.data
    afterChange()
  } catch (e) {
    toast.error(errorMessage(e, '순서를 저장하지 못했습니다.'))
    await fetchItems()
  } finally {
    busy.value = false
  }
}

onMounted(() => {
  fetchCounts()
  fetchItems()
})
</script>
