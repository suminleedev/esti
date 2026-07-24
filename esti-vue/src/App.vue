<script setup>
import AppHeader from '@/components/common/AppHeader.vue'
import ToastHost from '@/components/common/ToastHost.vue'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
</script>

<template>
  <AppHeader />
  <router-view></router-view>
  <ToastHost />
  <ConfirmDialog />
</template>

<style scoped></style>
