<script setup>
import { computed } from 'vue'
import { heartGradeInfo, toHeartGrade, formatScore } from '@/utils/heartGrade.js'

/**
 * 하트 아이콘 + 숫자 점수.
 * 숫자는 절대 이미지에 포함하지 않고 항상 HTML 텍스트로 렌더링합니다.
 * 색상만으로 등급을 구분하지 않도록 등급 라벨을 접근성 텍스트로 함께 제공합니다.
 */
const props = defineProps({
  score: { type: [Number, String], default: 0 },
  size: { type: Number, default: 20 },
  showScore: { type: Boolean, default: true },
  showLabel: { type: Boolean, default: false },
})

const grade = computed(() => heartGradeInfo(toHeartGrade(props.score)))
const scoreText = computed(() => formatScore(props.score))
</script>

<template>
  <span class="rating" :data-grade="grade.key">
    <img
      class="rating__heart"
      :src="grade.asset"
      :alt="`${grade.label} 하트`"
      :width="size"
      :height="size"
    />
    <span v-if="showScore" class="rating__score" data-testid="score-text">{{ scoreText }}</span>
    <span v-if="showLabel" class="rating__label">{{ grade.label }}</span>
    <span v-else class="lm-sr-only">{{ grade.label }}</span>
  </span>
</template>

<style scoped>
.rating {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.rating__heart { display: block; flex: none; }
.rating__score {
  font-size: var(--lm-text-md);
  color: var(--lm-ink);
  font-variant-numeric: tabular-nums;
}
.rating__label {
  font-size: var(--lm-text-sm);
  color: var(--lm-ink-soft);
}
</style>
