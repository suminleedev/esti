<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'

const file = ref(null)
const uploading = ref(false)
const progress = ref(0)
const message = ref('')
const error = ref('')
const products = ref([])
const editingProduct = ref(null) // 수정 중인 제품

function onFileChange(e) {
  error.value = ''
  const f = e.target.files?.[0]
  validateAndSet(f)
}

function validateAndSet(f) {
  if (!f) return
  const isXlsx =
    f.name.toLowerCase().endsWith('.xlsx') ||
    f.type === 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
  if (!isXlsx) {
    error.value = '엑셀(.xlsx) 파일만 업로드 가능합니다.'
    file.value = null
    return
  }
  if (f.size > 20 * 1024 * 1024) {
    error.value = '파일 용량은 20MB 이하여야 합니다.'
    file.value = null
    return
  }
  file.value = f
  message.value = ''
}

async function upload() {
  if (!file.value) {
    error.value = '업로드할 파일을 선택하세요.'
    return
  }
  error.value = ''
  message.value = ''
  uploading.value = true
  progress.value = 0
  try {
    const form = new FormData()
    form.append('file', file.value)

    const res = await axios.post('/catalog/import', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress: (e) => {
        if (e.total) {
          progress.value = Math.round((e.loaded * 100) / e.total)
        }
      },
    })
    message.value = typeof res.data === 'string' ? res.data : '엑셀 업로드 성공!'
    await loadProducts()
  } catch (e) {
    error.value = e?.response?.data ? String(e.response.data) : (e?.message || '업로드 실패')
  } finally {
    uploading.value = false
  }
}

async function loadProducts() {
  try {
    const res = await axios.get('/catalog/list')
    products.value = res.data
  } catch (e) {
    console.error('목록 조회 실패', e)
  }
}

async function deleteProduct(id) {
  if (!confirm('정말 삭제하시겠습니까?')) return
  try {
    await axios.delete(`/catalog/${id}`)
    message.value = '삭제 성공'
    await loadProducts()
  } catch (e) {
    error.value = '삭제 실패: ' + (e?.response?.data || e?.message)
  }
}

function startEdit(p) {
  editingProduct.value = { ...p } // 복사본
}

function cancelEdit() {
  editingProduct.value = null
}

async function saveEdit() {
  if (!editingProduct.value) return
  try {
    const res = await axios.put(`/catalog/${editingProduct.value.id}`, editingProduct.value)
    message.value = '수정 성공'
    editingProduct.value = null
    await loadProducts()
  } catch (e) {
    error.value = '수정 실패: ' + (e?.response?.data || e?.message)
  }
}

onMounted(() => {
  loadProducts()
})
</script>

<template>
  <div class="container my-5">
    <div class="card shadow-sm">
      <div class="card-body">
        <h2 class="card-title h4 mb-4">제품 카탈로그 엑셀 업로드</h2>

        <!-- 파일 업로드 -->
        <div class="mb-3">
          <label for="file" class="form-label">엑셀 파일 선택 (.xlsx)</label>
          <input
            id="file"
            type="file"
            class="form-control"
            accept=".xlsx"
            @change="onFileChange"
          />
          <div v-if="file" class="form-text">
            선택됨: <strong>{{ file.name }}</strong>
            ({{ (file.size/1024/1024).toFixed(2) }} MB)
          </div>
        </div>

        <!-- 진행률 -->
        <div v-if="uploading" class="mb-3">
          <div class="progress">
            <div
              class="progress-bar progress-bar-striped progress-bar-animated"
              role="progressbar"
              :style="{ width: progress + '%' }"
              :aria-valuenow="progress"
              aria-valuemin="0"
              aria-valuemax="100"
            >
              {{ progress }}%
            </div>
          </div>
        </div>

        <!-- 액션 -->
        <div class="d-flex gap-2 mb-3">
          <button
            class="btn btn-primary"
            :disabled="uploading || !file"
            @click="upload"
          >
            업로드
          </button>
          <a href="/product_catalog_sample.xlsx" class="btn btn-outline-secondary" download>
            샘플 템플릿 다운로드
          </a>
        </div>

        <!-- 메시지 -->
        <div v-if="message" class="alert alert-success" role="alert">{{ message }}</div>
        <div v-if="error" class="alert alert-danger" role="alert">{{ error }}</div>

        <!-- 목록 테이블 -->
        <div class="mt-4">
          <h5>카탈로그 목록</h5>
          <table class="table table-striped table-bordered mt-2 align-middle">
            <thead class="table-light">
            <tr>
              <th>#</th>
              <th>제품명</th>
              <th>모델명</th>
              <th>브랜드</th>
              <th>규격</th>
              <th>단가</th>
              <th>설명</th>
              <th>이미지</th>
              <th>액션</th>
            </tr>
            </thead>
            <tbody>
            <tr v-for="(p, idx) in products" :key="p.id">
              <template v-if="editingProduct && editingProduct.id === p.id">
                <!-- 수정 모드 -->
                <td>{{ idx + 1 }}</td>
                <td><input v-model="editingProduct.name" class="form-control" /></td>
                <td><input v-model="editingProduct.model" class="form-control" /></td>
                <td><input v-model="editingProduct.brand" class="form-control" /></td>
                <td><input v-model="editingProduct.specs" class="form-control" /></td>
                <td><input v-model="editingProduct.basePrice" type="number" class="form-control" /></td>
                <td><input v-model="editingProduct.description" class="form-control" /></td>
                <td><input v-model="editingProduct.imageUrl" class="form-control" /></td>
                <td class="d-flex gap-1">
                  <button class="btn btn-success btn-sm" @click="saveEdit">저장</button>
                  <button class="btn btn-secondary btn-sm" @click="cancelEdit">취소</button>
                </td>
              </template>
              <template v-else>
                <!-- 조회 모드 -->
                <td>{{ idx + 1 }}</td>
                <td>{{ p.name }}</td>
                <td>{{ p.model }}</td>
                <td>{{ p.brand }}</td>
                <td>{{ p.specs }}</td>
                <td>{{ p.basePrice }}</td>
                <td>{{ p.description }}</td>
                <td>
                  <img
                    v-if="p.imageUrl"
                    :src="p.imageUrl"
                    alt="상품 이미지"
                    style="max-width: 80px; max-height: 60px"
                  />
                </td>
                <td class="d-flex gap-1">
                  <button class="btn btn-warning btn-sm" @click="startEdit(p)">수정</button>
                  <button class="btn btn-danger btn-sm" @click="deleteProduct(p.id)">삭제</button>
                </td>
              </template>
            </tr>
            <tr v-if="products.length === 0">
              <td colspan="9" class="text-center text-muted">등록된 제품이 없습니다</td>
            </tr>
            </tbody>
          </table>
        </div>
      </div>
    </div>
  </div>
</template>
