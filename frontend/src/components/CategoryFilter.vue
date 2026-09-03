<script setup>
import { computed } from 'vue'
import BaseIcon from './BaseIcon.vue'
import { CATEGORIES } from '@/stores/places.js'

/** 지도 하단의 카테고리 필터 바. */
defineProps({
  active: { type: String, default: 'all' },
})

defineEmits(['change'])

const filterCategories = computed(() => CATEGORIES.map((category) =>
  category.key === '데이트 코스'
    ? { ...category, key: 'route', label: '맛집 탐방기', icon: 'pin' }
    : category.key === '기타'
      ? { ...category, key: 'favorites', label: '즐겨찾기', icon: 'star' }
    : category,
))
</script>

<template>
  <div class="filter" role="group" aria-label="카테고리 필터">
    <button
      v-for="category in filterCategories"
      :key="category.key"
      type="button"
      class="filter__item"
      :class="{ 'filter__item--active': category.key === active }"
      :aria-pressed="category.key === active"
      :data-testid="`category-${category.key}`"
      @click="$emit('change', category.key)"
    >
      <BaseIcon :name="category.icon" :size="22" />
      <span class="filter__label">{{ category.label }}</span>
    </button>
  </div>
</template>

<style scoped>
.filter {
  display: flex;
  align-items: center;
  gap: var(--lm-space-2);
  padding: var(--lm-space-3) var(--lm-space-4);
  background: var(--lm-card);
  border: 1px solid var(--lm-card-edge);
  border-radius: var(--lm-radius-lg);
  box-shadow: var(--lm-shadow-card);
}
.filter__item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  min-width: 64px;
  padding: 4px 8px;
  border-radius: var(--lm-radius-sm);
  color: var(--lm-ink-soft);
}
.filter__item:hover { background: var(--lm-pink-bg); color: var(--lm-pink); }
/* 활성 항목은 색상뿐 아니라 배경과 aria-pressed로도 구분됩니다. */
.filter__item--active {
  color: var(--lm-pink);
  background: var(--lm-pink-bg-2);
}
.filter__label {
  font-size: var(--lm-text-xs);
  white-space: nowrap;
}
</style>
