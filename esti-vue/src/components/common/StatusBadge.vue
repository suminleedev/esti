<script setup>
import { computed } from 'vue'
import { PROPOSAL_STATUS } from '@/constants/labels'

const props = defineProps({
  status: { type: String, required: true },
})

const info = computed(
  () => PROPOSAL_STATUS[props.status] ?? { label: props.status, badgeClass: 'bg-secondary' },
)
</script>

<template>
  <span class="badge" :class="info.badgeClass">{{ info.label }}</span>
</template>
