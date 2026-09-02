<script setup>
import { useRouter } from 'vue-router'
import EmptyState from '@/components/EmptyState.vue'

const router = useRouter()
</script>

<template>
  <EmptyState
    title="길을 잃었어요"
    description="찾으시는 페이지가 없어요. 러브맵으로 돌아갈까요?"
    action-label="지도로 가기"
    @action="router.push('/')"
  />
</template>
