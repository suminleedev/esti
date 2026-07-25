<script setup>
import AppHeader from '@/components/common/AppHeader.vue'
import ToastHost from '@/components/common/ToastHost.vue'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import PromptDialog from '@/components/common/PromptDialog.vue'
</script>

<template>
  <AppHeader />
  <router-view></router-view>
  <ToastHost />
  <ConfirmDialog />
  <PromptDialog />
</template>

<style scoped></style>
