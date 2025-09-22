<style scoped src="../assets/css/estimatePage.css"></style>

<template>
  <div class="wrap">
    <div class="topbar">
      <h1>아파트 화장실 위생도기 견적서 작성</h1>
      <div class="small muted">품목 사진 · 규격 확인 → 수량·단가 입력 → 견적서에 추가</div>
    </div>

    <div class="grid">
      <!-- Left: Catalog + Detail -->
      <div class="card">
        <div class="catalog-header">
          <strong>제품 카탈로그</strong>
          <input v-model="search" placeholder="검색 (예: 변기, 세면대)" />
        </div>

        <div class="catalog">
          <div v-for="item in filteredItems" :key="item.id" class="item" @click="selectItem(item)">
            <img :src="item.image" class="thumb" alt="thumb" />
            <div class="meta">
              <div class="title">{{ item.name }}</div>
              <div class="sub">{{ item.model }} · {{ item.brand }}</div>
            </div>
            <div class="small muted">{{ formatCurrency(item.price) }}</div>
          </div>
        </div>
      </div>

      <!-- Middle: Detail + Controls -->
      <div class="card">
        <div class="detail-preview">
          <img :src="selected.image" class="detail-img" alt="detail" />
          <div class="specs">
            <h3>{{ selected.name }} <span class="small muted">({{ selected.model }})</span></h3>
            <dl>
              <dt>브랜드</dt>
              <dd>{{ selected.brand }}</dd>
              <dt>규격</dt>
              <dd>{{ selected.specs }}</dd>
              <dt>기본 단가</dt>
              <dd>{{ formatCurrency(selected.price) }} (권장 단가)</dd>
              <dt>설명</dt>
              <dd class="small muted">{{ selected.desc }}</dd>
            </dl>
          </div>
        </div>

        <div class="controls">
          <div class="controls-header">
            <strong>견적 항목 입력</strong>
            <div class="small muted">
              선택한 품목: <span v-if="selected.id">{{ selected.name }}</span><span v-else>없음</span>
            </div>
          </div>

          <div class="row">
            <input type="number" v-model.number="form.qty" min="1" placeholder="수량" />
            <input type="number" v-model.number="form.unitPrice" min="0" placeholder="단가 (원)" />
          </div>

          <div class="row">
            <input type="text" v-model="form.note" placeholder="비고 (선택)" />
          </div>

          <div class="row buttons">
            <button @click="addToEstimate" :disabled="!selected.id || form.qty<1">추가</button>
            <button class="secondary" @click="clearForm">초기화</button>
            <button class="ghost" @click="prefillSelected">선택 단가 불러오기</button>
          </div>

          <div class="small muted">총액: <span class="total">{{ formatCurrency(computedLineTotal) }}</span></div>
        </div>
      </div>

      <!-- Right: Estimate List -->
      <div class="card">
        <div class="estimate-header">
          <strong>견적 내역</strong>
          <div class="small muted">총 항목: {{ estimate.length }}</div>
        </div>

        <div v-if="estimate.length === 0" class="empty card">
          아직 추가된 항목이 없습니다. 좌측에서 품목을 선택하고 추가하세요.
        </div>

        <div v-else class="estimate-list">
          <table>
            <thead>
            <tr>
              <th>품목</th>
              <th>규격 / 모델</th>
              <th>수량</th>
              <th class="right">단가</th>
              <th class="right">금액</th>
              <th></th>
            </tr>
            </thead>
            <tbody>
            <tr v-for="(row, idx) in estimate" :key="row.uid">
              <td>{{ row.name }} <div class="small muted">{{ row.brand }}</div></td>
              <td>{{ row.model }}</td>
              <td><input class="qty-input" type="number" v-model.number="row.qty" @input="recalc" min="1" /></td>
              <td class="right">{{ formatCurrency(row.unitPrice) }}</td>
              <td class="right">{{ formatCurrency((row.qty * row.unitPrice)) }}</td>
              <td class="right"><button class="ghost" @click="removeRow(idx)">삭제</button></td>
            </tr>
            </tbody>
            <tfoot>
            <tr>
              <td colspan="4" class="right">합계</td>
              <td class="right">{{ formatCurrency(subtotal) }}</td>
              <td></td>
            </tr>
            </tfoot>
          </table>
        </div>

        <div class="estimate-footer">
          <div class="buttons">
            <button @click="exportJSON" class="secondary">JSON 내보내기</button>
            <button @click="clearEstimate" class="ghost">비우기</button>
          </div>
          <div>
            <div class="small muted">부가세 포함(10%):</div>
            <div class="total">{{ formatCurrency(taxIncluded) }}</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
//import '../css/assets/EstimatePage.css'
import { reactive, ref, computed } from 'vue'

const search = ref('')
const items = reactive([
  { id:1, name:'양변기 (일체형)', model:'TZ-101', brand:'CleanBath', specs:'W:360 x D:700 x H:780mm', price:220000, desc:'저수량 절수형 일체형 변기', image:'https://images.unsplash.com/photo-1598300059196-9b2f6f6f6f6f?auto=format&fit=crop&w=600&q=60' },
  { id:2, name:'세면대 (탑볼)', model:'WB-202', brand:'AquaForm', specs:'W:500 x D:420 x H:185mm', price:95000, desc:'상판 설치형 탑볼 세면대', image:'https://images.unsplash.com/photo-1616596871742-3e3d2b1b1b1b?auto=format&fit=crop&w=600&q=60' },
  { id:3, name:'비데 일체형 시트', model:'BD-300', brand:'HygienePro', specs:'표준형, 자동세척', price:180000, desc:'온수·좌욕·자동세척 기능', image:'https://images.unsplash.com/photo-1586201375761-83865001e86a?auto=format&fit=crop&w=600&q=60' },
  { id:4, name:'샤워기 세트', model:'SH-77', brand:'RainFlow', specs:'스테인리스, 3단수압', price:85000, desc:'절수형 샤워헤드+호스 세트', image:'https://images.unsplash.com/photo-1556228720-1f5c1b7e3f3f?auto=format&fit=crop&w=600&q=60' },
  { id:5, name:'양변기 뚜껑', model:'CT-55', brand:'SpareParts', specs:'폴리프로필렌, 백색', price:28000, desc:'표준형 변기용 뚜껑', image:'https://images.unsplash.com/photo-1542314831-068cd1dbfeeb?auto=format&fit=crop&w=600&q=60' }
])

const selected = reactive({ id:null, name:'샘플을 선택하세요', model:'', brand:'', specs:'', price:0, desc:'좌측 목록에서 품목을 선택하면 사진과 규격을 볼 수 있습니다.', image:'https://via.placeholder.com/600x400?text=선택된+품목+이미지' })
const form = reactive({ qty:1, unitPrice:0, note:'' })
const estimate = reactive([])

const filteredItems = computed(()=>{
  const q = search.value.trim().toLowerCase()
  if(!q) return items
  return items.filter(i => (i.name + ' ' + i.model + ' ' + i.brand).toLowerCase().includes(q))
})

function selectItem(item){
  Object.assign(selected, item)
  form.unitPrice = item.price
  form.qty = 1
  form.note = ''
}

function prefillSelected(){ if(selected.id) form.unitPrice = selected.price }

function addToEstimate(){
  if(!selected.id) return
  const uid = Date.now() + Math.floor(Math.random()*999)
  estimate.push({ uid, id:selected.id, name:selected.name, model:selected.model, brand:selected.brand, qty: form.qty || 1, unitPrice: form.unitPrice || 0, note: form.note })
  clearForm()
}

function clearForm(){ form.qty = 1; form.unitPrice = selected.price || 0; form.note = '' }
function removeRow(idx){ estimate.splice(idx,1) }
function recalc(){ /* reactive handles totals */ }
function clearEstimate(){ if(confirm('견적서를 비우시겠습니까?')) estimate.splice(0, estimate.length) }

const subtotal = computed(()=> estimate.reduce((s,r)=> s + (r.qty * r.unitPrice), 0))
const taxIncluded = computed(()=> Math.round(subtotal.value * 1.1))
const computedLineTotal = computed(()=> (form.qty || 0) * (form.unitPrice || 0))

function exportJSON(){
  const payload = { createdAt: new Date().toISOString(), items: estimate.slice(), subtotal: subtotal.value, total: taxIncluded.value }
  const dataStr = 'data:text/json;charset=utf-8,' + encodeURIComponent(JSON.stringify(payload, null, 2))
  const a = document.createElement('a')
  a.href = dataStr
  a.download = 'estimate.json'
  a.click()
}

function formatCurrency(value) {
  if (value == null) return '-';
  return value.toLocaleString('ko-KR');
}

</script>


