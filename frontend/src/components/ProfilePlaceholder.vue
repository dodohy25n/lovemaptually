<script setup>
import { computed } from 'vue'
import BaseIcon from './BaseIcon.vue'

/** 실제 프로필 사진이 등록되기 전 공통으로 사용하는 기본 프로필입니다. */
const props = defineProps({
  size: { type: [Number, String], default: 42 },
  label: { type: String, default: '프로필 사진 없음' },
  src: { type: String, default: '' },
})

const pixelSize = computed(() => Number(props.size) || 42)
const iconSize = computed(() => Math.max(18, Math.round(pixelSize.value * 0.53)))
const style = computed(() => ({
  width: `${pixelSize.value}px`,
  height: `${pixelSize.value}px`,
}))
</script>

<template>
  <span
    class="profile-placeholder"
    :style="style"
    :aria-label="label"
    role="img"
    data-testid="profile-placeholder"
  >
    <img v-if="src" class="profile-placeholder__image" :src="src" alt="" />
    <BaseIcon v-else name="user" :size="iconSize" />
  </span>
</template>

<style scoped>
.profile-placeholder {
  display: inline-grid;
  place-items: center;
  flex: none;
  overflow: hidden;
  border-radius: 50%;
  background: var(--lm-pink-soft);
  color: #fff;
}
.profile-placeholder__image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
</style>
