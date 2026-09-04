<script setup>
import { computed } from 'vue'
import { heartGradeInfo, toHeartGrade, formatScore } from '@/utils/heartGrade.js'

/**
 * 지도 위 하트 핀.
 *
 * 핀 모양(물방울)과 하트는 에셋/CSS로 그리고,
 * 핀 아래 숫자 점수는 이미지가 아니라 HTML 텍스트로 렌더링합니다.
 */
const props = defineProps({
  place: { type: Object, required: true },
  active: { type: Boolean, default: false },
})

const emit = defineEmits(['select'])

const grade = computed(() => heartGradeInfo(toHeartGrade(props.place.coupleScore)))
const scoreText = computed(() => formatScore(props.place.coupleScore))
// 담기만 하고 아무도 리뷰를 안 쓴 장소입니다. 아무도 좋아하지 않은 것과는 다른 상태입니다.
const pending = computed(() => props.place.reviewedCount === 0)
</script>

<template>
  <button
    type="button"
    class="pin"
    :class="{ 'pin--active': active, 'pin--pending': pending }"
    :data-testid="`map-pin-${place.id}`"
    :data-grade="grade.key"
    :aria-label="pending ? `${place.name}, 아직 기록 없음` : `${place.name}, ${grade.label}, 점수 ${scoreText}점`"
    @click.stop="emit('select', place.id)"
  >
    <span class="pin__drop">
      <img class="pin__heart" :src="grade.asset" alt="" width="26" height="26" />
    </span>
    <span class="pin__score" data-testid="pin-score">{{ pending ? '기록 전' : scoreText }}</span>
    <span class="pin__name" data-testid="pin-name">{{ place.name }}</span>
  </button>
</template>

<style scoped>
.pin {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  filter: drop-shadow(var(--lm-shadow-pin));
  transition: transform 0.14s ease;
}
.pin:hover,
.pin--active { transform: scale(1.12); }

/* 물방울 모양 핀. 아래쪽 꼭짓점만 각지게 만들어 위치를 가리킵니다. */
.pin__drop {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  background: #fff;
  border: 2px solid var(--lm-pink-line);
  border-radius: 50% 50% 50% 4px;
  transform: rotate(-45deg);
}
.pin__heart { transform: rotate(45deg); }

.pin--active .pin__drop { border-color: var(--lm-pink); }

.pin--pending .pin__drop { filter: grayscale(1); opacity: 0.72; }
.pin--pending .pin__score {
  color: #8b8b8b;
  border-color: #cfcfcf;
  border-style: dashed;
}

.pin__name {
  max-width: 92px;
  padding: 1px 7px;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  background: rgba(255, 255, 255, 0.92);
  border-radius: 6px;
  font-size: 11px;
  font-weight: 600;
  color: #6b4b45;
  line-height: 1.5;
}

.pin__score {
  margin-top: 2px;
  padding: 1px 9px;
  background: #fff;
  border: 1px solid var(--lm-pink-line);
  border-radius: 999px;
  font-size: var(--lm-text-sm);
  color: var(--lm-pink);
  font-variant-numeric: tabular-nums;
  line-height: 1.5;
}
</style>
