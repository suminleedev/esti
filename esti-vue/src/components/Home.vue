<template>
  <div class="home-wrap d-flex align-items-center justify-content-center">
    <div class="container" style="max-width: 720px;">
      <div class="text-center mb-4">
        <h3 class="fw-bold">esti</h3>
        <div class="text-muted">효도를 위한 견적서 프로그램</div>
      </div>

      <div class="row g-3">
        <!-- 엑셀 업로드 -->
        <div class="col-12 col-md-6">
          <button class="menu-card w-100" type="button" @click="goExcelUpload">
            <div class="icon">📤</div>
            <div class="title">엑셀 업로드</div>
            <div class="desc">카탈로그/상품 엑셀을 업로드합니다</div>
          </button>
        </div>

        <!-- 제안서 목록 -->
        <div class="col-12 col-md-6">
          <button class="menu-card w-100" type="button" @click="goProposalList">
            <div class="icon">📄</div>
            <div class="title">제안서 목록</div>
            <div class="desc">작성한 제안서를 조회/관리합니다</div>
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { useRouter } from "vue-router";

const router = useRouter();

function goExcelUpload() {
  // 너 프로젝트 라우트에 맞게 경로만 바꾸면 됨
  router.push("/upload");
}

function goProposalList() {
  router.push("/proposal/list");
}
</script>

<style scoped>
.home-wrap {
  min-height: calc(100vh - 56px); /* 상단 navbar가 56px이면 */
  padding: 24px;
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
</style>
