<template>
  <AttendanceDashboardView :initial-offering-id="initialOfferingId" :logged-in-user="loggedInUser" />
</template>

<script setup lang="ts">
import AttendanceDashboardView from './AttendanceDashboardView.vue'
import type { UserVO } from '../api/types'
defineProps<{ initialOfferingId?: number | null; loggedInUser?: UserVO | null }>()
</script>
