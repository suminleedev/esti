<script setup>
import AppHeader from '@/components/common/AppHeader.vue'
import ToastHost from '@/components/common/ToastHost.vue'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import PromptDialog from '@/components/common/PromptDialog.vue'
</script>

<template>
  <AppHeader />
  <!-- 본문 랜드마크는 여기 한 곳에서 준다 — 화면마다 넣으면 있는 화면·없는 화면이 갈린다 -->
  <main>
    <router-view></router-view>
  </main>
  <ToastHost />
  <ConfirmDialog />
  <PromptDialog />
</template>

<style scoped></style>
