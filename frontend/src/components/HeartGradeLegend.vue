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
  <RouterLink to="/memories" class="legend lm-card" aria-labelledby="legend-title">
    <span class="lm-tape lm-tape--br"></span>

    <h2 id="legend-title" class="lm-card__title">추억 저장소</h2>

    <ul class="legend__list">
      <li v-for="grade in HEART_GRADE_LEGEND" :key="grade.key" class="legend__item">
        <img :src="grade.asset" :alt="`${grade.label} 하트`" width="26" height="26" />
        <span class="legend__text">
          {{ grade.label }}{{ grade.key === 'good' ? '!' : '' }} {{ rangeText(grade) }}
        </span>
      </li>
    </ul>
    <span class="legend__link">우리의 기록 모아보기 <span aria-hidden="true">→</span></span>
  </RouterLink>
</template>

<style scoped>
.legend {
  padding: var(--lm-space-4);
  display: flex;
  flex-direction: column;
  gap: var(--lm-space-3);
  text-decoration: none;
  transition: transform .16s ease, box-shadow .16s ease;
}
.legend:hover { transform:translateY(-2px);box-shadow:var(--lm-shadow-lift); }
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
.legend__link { color:var(--lm-pink);font-size:var(--lm-text-xs);font-weight:700;text-align:right; }
</style>
