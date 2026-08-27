<template>
  <div class="home-wrap">
    <div class="container" style="max-width: 820px;">
      <div class="text-center mb-4">
        <h3 class="fw-bold">esti</h3>
        <div class="text-muted">효도를 위한 견적서 프로그램</div>
      </div>

      <div class="row g-3">
        <!-- 새 제안서 작성 -->
        <div class="col-12 col-md-4">
          <button class="menu-card w-100" type="button" @click="goProposalNew">
            <div class="icon"><i class="bi bi-pencil-square"></i></div>
            <div class="title">새 제안서 작성</div>
            <div class="desc">현장 정보와 품목으로 제안서를 만듭니다</div>
          </button>
        </div>

        <!-- 카탈로그 관리 -->
        <div class="col-12 col-md-4">
          <button class="menu-card w-100" type="button" @click="goExcelUpload">
            <div class="icon"><i class="bi bi-upload"></i></div>
            <div class="title">카탈로그 관리</div>
            <div class="desc">카탈로그/상품 엑셀을 업로드합니다</div>
          </button>
        </div>

        <!-- 제안서 목록 -->
        <div class="col-12 col-md-4">
          <button class="menu-card w-100" type="button" @click="goProposalList">
            <div class="icon"><i class="bi bi-file-earmark-text"></i></div>
            <div class="title">제안서 목록</div>
            <div class="desc">작성한 제안서를 조회/관리합니다</div>
          </button>
        </div>
      </div>

      <!-- 최근 제안서 (L-5) -->
      <div class="card mt-4">
        <div class="card-header d-flex justify-content-between align-items-center">
          <span class="fw-semibold">최근 제안서</span>
          <router-link to="/proposal/list" class="small">전체 보기</router-link>
        </div>
        <div class="card-body p-0">
          <EmptyState
            v-if="loading || recent.length === 0"
            :loading="loading"
            icon="bi-clipboard-x"
            message="아직 작성한 제안서가 없습니다."
          >
            <template #cta>
              <button class="btn btn-primary btn-sm" @click="goProposalNew">
                <i class="bi bi-pencil-square me-1"></i>새 제안서 작성
              </button>
            </template>
          </EmptyState>

          <ul v-else class="list-group list-group-flush">
            <li
              v-for="p in recent"
              :key="p.id"
              class="list-group-item d-flex align-items-center gap-3"
              role="button"
              tabindex="0"
              :aria-label="`제안서 #${p.id} ${p.projectName} ${p.status === 'DRAFT' ? '이어서 작성' : '상세 열기'}`"
              @click="goDetail(p.id)"
              @keydown.enter="goDetail(p.id)"
              @keydown.space.prevent="goDetail(p.id)"
            >
              <div class="flex-grow-1 text-truncate">
                <div class="fw-semibold text-truncate">{{ p.projectName }}</div>
                <div class="small text-muted">
                  {{ p.apartmentType || '-' }} · {{ p.households ?? '-' }}세대 · {{ date(p.date) }}
                </div>
              </div>
              <span v-if="p.status === 'DRAFT'" class="small text-primary flex-shrink-0">
                <i class="bi bi-pencil me-1"></i>이어서 작성
              </span>
              <StatusBadge :status="p.status" class="flex-shrink-0" />
            </li>
          </ul>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from "vue";
import { useRouter } from "vue-router";
import axios from "axios";
import { date } from "@/utils/format";
import StatusBadge from "@/components/common/StatusBadge.vue";
import EmptyState from "@/components/common/EmptyState.vue";

const router = useRouter();

const recent = ref([]);
const loading = ref(false);

async function loadRecent() {
  loading.value = true;
  try {
    const { data } = await axios.get("/api/proposals/page", {
      params: { page: 0, size: 5 },
    });
    recent.value = data.content ?? [];
  } catch {
    recent.value = [];
  } finally {
    loading.value = false;
  }
}

onMounted(loadRecent);

function goExcelUpload() {
  router.push("/upload");
}

function goProposalList() {
  router.push("/proposal/list");
}

function goProposalNew() {
  router.push({ name: "proposal-new" });
}

function goDetail(id) {
  router.push(`/proposal/${id}`);
}
</script>

<style scoped>
.home-wrap {
  min-height: calc(100vh - var(--esti-header-height));
  padding: 32px 24px;
}

.menu-card {
  border: 2px solid #e9ecef;
  border-radius: 18px;
  background: white;
  padding: 22px 18px;
  text-align: left;
  transition: transform 0.08s ease, box-shadow 0.08s ease, border-color 0.08s ease;
}

.menu-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 24px rgba(0, 0, 0, 0.08);
  border-color: #ced4da;
}

.icon {
  font-size: 34px;
  line-height: 1;
  margin-bottom: 10px;
}

.title {
  font-size: 18px;
  font-weight: 700;
  margin-bottom: 4px;
}

.desc {
  font-size: 13px;
  color: #6c757d;
}

/* 최근 제안서 행 키보드 포커스 가시화 (H-9 접근성) */
.list-group-item[role="button"] {
  cursor: pointer;
}
.list-group-item[role="button"]:focus-visible {
  outline: 2px solid var(--bs-primary);
  outline-offset: -2px;
  z-index: 1;
}
</style>
