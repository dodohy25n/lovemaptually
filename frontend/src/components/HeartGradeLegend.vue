<script setup>
import { HEART_GRADE_LEGEND } from '@/utils/heartGrade.js'

/** '점수에 따른 하트 등급' 안내 카드. 등급 경계는 heartGrade.js 한 곳에서만 정의합니다. */
function rangeText(grade) {
  if (grade.key === 'good') return '(4.0 ~ 5.0)'
  if (grade.key === 'normal') return '(2.0 ~ 3.9)'
  return '(0 ~ 1.9)'
}
</script>

<template>
  <section class="legend lm-card" aria-labelledby="legend-title">
    <span class="lm-tape lm-tape--br"></span>

    <h2 id="legend-title" class="lm-card__title">점수에 따른 하트 등급</h2>

    <ul class="legend__list">
      <li v-for="grade in HEART_GRADE_LEGEND" :key="grade.key" class="legend__item">
        <img :src="grade.asset" :alt="`${grade.label} 하트`" width="26" height="26" />
        <span class="legend__text">
          {{ grade.label }}{{ grade.key === 'good' ? '!' : '' }} {{ rangeText(grade) }}
        </span>
      </li>
    </ul>
  </section>
</template>

<style scoped>
.legend {
  padding: var(--lm-space-4);
  display: flex;
  flex-direction: column;
  gap: var(--lm-space-3);
}
.legend__list {
  display: flex;
  flex-direction: column;
  gap: var(--lm-space-3);
}
.legend__item {
  display: flex;
  align-items: center;
  gap: var(--lm-space-3);
}
.legend__text {
  font-size: var(--lm-text-sm);
  color: var(--lm-ink);
}
</style>
