<script setup>
// 라우터에 없는 주소로 들어왔을 때의 화면 (F-027).
// 전에는 catch-all이 없어 헤더만 남고 본문이 통째로 비었다 —
// 로딩 중인지 주소가 틀린 건지 구분할 수 없었다.
import { useRoute, useRouter } from "vue-router";
import EmptyState from "@/components/common/EmptyState.vue";

const route = useRoute();
const router = useRouter();

function goHome() {
  router.push("/");
}
</script>

<template>
  <div class="notfound-wrap">
    <div class="container" style="max-width: 640px">
      <div class="card">
        <div class="card-body">
          <EmptyState icon="bi-signpost-2" message="찾을 수 없는 페이지입니다.">
            <template #cta>
              <!-- 어떤 주소로 들어왔는지 보여준다 — 오타인지 오래된 링크인지 가려내는 단서다 -->
              <div class="small text-muted mb-3 text-break">
                {{ decodeURIComponent(route.fullPath) }}
              </div>
              <button class="btn btn-primary btn-sm" @click="goHome">
                <i class="bi bi-house me-1"></i>홈으로
              </button>
            </template>
          </EmptyState>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.notfound-wrap {
  min-height: calc(100vh - var(--esti-header-height));
  padding: 32px 24px;
}
</style>
