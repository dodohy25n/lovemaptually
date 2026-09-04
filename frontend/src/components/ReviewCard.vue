<script setup>
import { computed } from 'vue'
import HeartRating from './HeartRating.vue'
import ProfilePlaceholder from './ProfilePlaceholder.vue'
import { reviewAverage, formatScore } from '@/utils/heartGrade.js'
import { memberOf } from '@/utils/users.js'
import photoPlaceholder from '@/assets/photo-placeholder.svg'

/**
 * 구성원 한 사람의 리뷰를 담는 종이 카드.
 *
 * 두 카드는 구조와 크기가 완전히 같고 강조 색만 다릅니다(role 값으로 전환).
 * 장식(테이프)은 pointer-events가 없고 카드 바깥 여백에만 놓여 본문을 가리지 않습니다.
 */
const props = defineProps({
  place: { type: Object, required: true },
  review: { type: Object, default: null },
  role: { type: String, default: 'him', validator: (v) => ['him', 'her'].includes(v) },
  photoSlots: { type: Number, default: 6 },
})

const member = computed(() => memberOf(props.role === 'her' ? 'her' : 'him'))

const average = computed(() => reviewAverage(props.review))

const SCORE_FIELDS = [
  { key: 'atmosphere', label: '분위기' },
  { key: 'taste', label: '맛' },
  { key: 'value', label: '가성비' },
  { key: 'service', label: '서비스' },
]

const scores = computed(() =>
  SCORE_FIELDS.map((field) => ({
    ...field,
    value: Number(props.review?.[field.key] ?? 0),
  })),
)

/** 사진은 항상 photoSlots개 칸을 유지합니다. 없으면 placeholder가 자리를 지킵니다. */
// 세부 점수 네 개는 로컬 모드의 옛 모델입니다. 백엔드 리뷰에는 별점 하나뿐이라
// 모드가 아니라 값이 실제로 있는지로 가릅니다.
const reviewTags = computed(() => (Array.isArray(props.review?.tags) ? props.review.tags : []))
const hasDetailScores = computed(() =>
  ['atmosphere', 'taste', 'value', 'service'].some((key) => Number.isFinite(Number(props.review?.[key]))))
const hasRevisit = computed(() => props.review != null && 'revisitIntent' in props.review)

const photos = computed(() => {
  const source = props.review?.images?.length ? props.review.images : props.place.images
  return Array.from({ length: props.photoSlots }, (_, index) => source?.[index] ?? null)
})
</script>

<template>
  <article class="review" :data-role="role" :data-testid="`review-card-${role}`">
    <span class="lm-tape lm-tape--tl"></span>

    <header class="review__head">
      <ProfilePlaceholder
        class="review__avatar"
        :size="42"
        :label="`${member.userName} 프로필 사진 없음`"
      />
      <div class="review__who">
        <h3 class="review__name">{{ member.label }}</h3>
        <p class="review__author">{{ member.userName }}</p>
      </div>
      <HeartRating :score="average" :size="22" />
    </header>

    <div class="review__meta">
      <p class="review__place">{{ place.name }}</p>
      <p class="review__sub">
        <span>{{ place.category }}</span>
        <span v-if="place.visitedAt" aria-hidden="true">·</span>
        <span v-if="place.visitedAt">{{ place.visitedAt }} 방문</span>
      </p>
      <ul v-if="place.tags.length" class="review__tags">
        <li v-for="tag in place.tags" :key="tag" class="review__tag">#{{ tag }}</li>
      </ul>
    </div>

    <ul class="review__photos" :aria-label="`${member.label} 사진 ${photoSlots}칸`">
      <li v-for="(photo, index) in photos" :key="index" class="review__photo">
        <img
          v-if="photo"
          :src="photo"
          :alt="`${place.name} 사진 ${index + 1}`"
          loading="lazy"
        />
        <span v-else class="review__photo-empty">
          <img :src="photoPlaceholder" alt="" width="34" height="34" />
          <span class="lm-sr-only">사진 없음</span>
        </span>
      </li>
    </ul>

    <p v-if="review?.content" class="review__body">{{ review.content }}</p>
    <p v-else class="review__body review__body--empty">아직 리뷰를 작성하지 않았어요.</p>

    <section v-if="reviewTags.length" class="review__ai" data-testid="review-ai-tags">
      <h4 class="review__ai-title">이 문장에서 뽑은 태그</h4>
      <ul class="review__ai-list">
        <li v-for="tag in reviewTags" :key="tag.tag" class="review__ai-tag">
          <strong class="review__ai-name">{{ tag.tag }}</strong>
          <span v-if="tag.fact" class="review__ai-slot">
            <span class="review__ai-key">가게</span>
            <b>{{ tag.fact }}</b>
          </span>
          <span v-if="tag.want" class="review__ai-slot review__ai-slot--want">
            <span class="review__ai-key">원함</span>
            <b>{{ tag.want }}</b>
          </span>
        </li>
      </ul>
    </section>

    <dl v-if="hasDetailScores" class="review__scores">
      <div v-for="score in scores" :key="score.key" class="review__score">
        <dt>{{ score.label }}</dt>
        <dd>
          <span class="review__bar" aria-hidden="true">
            <span class="review__bar-fill" :style="{ width: `${(score.value / 5) * 100}%` }"></span>
          </span>
          <span class="review__score-num">{{ formatScore(score.value) }}</span>
        </dd>
      </div>
    </dl>

    <p v-if="hasRevisit" class="review__revisit">
      재방문 의사
      <strong>{{ review?.revisitIntent ? '있어요' : '없어요' }}</strong>
    </p>

  </article>
</template>

<style scoped>
.review {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: var(--lm-space-3);
  /* 두 카드의 크기를 강제로 맞춥니다 (두 사람의 리뷰가 나란히 놓입니다). */
  min-height: 560px;
  padding: var(--lm-space-5);
  background: var(--lm-card);
  border: 1px solid var(--accent-line);
  border-radius: var(--lm-radius-lg);
  box-shadow: var(--lm-shadow-card);
}
.review[data-role='him'] { --accent: var(--lm-him); --accent-bg: var(--lm-him-bg); --accent-line: var(--lm-him-line); }
.review[data-role='her'] { --accent: var(--lm-her); --accent-bg: var(--lm-her-bg); --accent-line: var(--lm-her-line); }

.review__head {
  display: flex;
  align-items: center;
  gap: var(--lm-space-3);
}
.review__avatar {
  flex: none;
}
.review__who { flex: 1; min-width: 0; }
.review__name { font-size: var(--lm-text-lg); color: var(--accent); }
.review__author { font-size: var(--lm-text-xs); color: var(--lm-ink-faint); }

.review__meta { display: flex; flex-direction: column; gap: 4px; }
.review__place { font-size: var(--lm-text-md); color: var(--lm-ink); }
.review__sub {
  display: flex;
  gap: 5px;
  font-size: var(--lm-text-xs);
  color: var(--lm-ink-soft);
}
.review__tags { display: flex; flex-wrap: wrap; gap: 5px; margin-top: 2px; }
.review__tag {
  padding: 2px 9px;
  border-radius: 999px;
  background: var(--accent-bg);
  color: var(--accent);
  font-size: var(--lm-text-xs);
}

.review__photos {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--lm-space-2);
}
.review__photo {
  aspect-ratio: 1;
  border-radius: var(--lm-radius-sm);
  overflow: hidden;
  border: 1px solid var(--lm-card-edge);
  background: var(--lm-header-bg);
}
.review__photo img { width: 100%; height: 100%; object-fit: cover; }
.review__photo-empty {
  display: grid;
  place-items: center;
  width: 100%;
  height: 100%;
  opacity: 0.5;
}

.review__body {
  flex: 1;
  font-size: var(--lm-text-sm);
  line-height: 1.75;
  color: var(--lm-ink);
  padding: var(--lm-space-3);
  background: var(--lm-header-bg);
  border-radius: var(--lm-radius-sm);
  border: 1px solid var(--lm-card-edge-soft);
  white-space: pre-wrap;
}
.review__body--empty { color: var(--lm-ink-faint); }

.review__scores {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--lm-space-2) var(--lm-space-4);
}
.review__score { display: flex; align-items: center; gap: var(--lm-space-2); }
.review__score dt {
  width: 44px;
  flex: none;
  font-size: var(--lm-text-xs);
  color: var(--lm-ink-soft);
}
.review__score dd {
  display: flex;
  align-items: center;
  gap: var(--lm-space-2);
  flex: 1;
  margin: 0;
}
.review__bar {
  flex: 1;
  height: 6px;
  border-radius: 999px;
  background: var(--lm-paper-shade);
  overflow: hidden;
}
.review__bar-fill { display: block; height: 100%; background: var(--accent); }
.review__score-num {
  font-size: var(--lm-text-xs);
  color: var(--lm-ink);
  font-variant-numeric: tabular-nums;
  width: 24px;
  text-align: right;
}

/* AI가 뽑은 태그. 이 카드의 핵심이라 각주가 아니라 하나의 블록으로 세웁니다. */
.review__ai {
  padding: var(--lm-space-3) var(--lm-space-4);
  border: 1px solid var(--lm-pink-line);
  border-radius: var(--lm-radius);
  background: var(--lm-pink-bg-2);
}
.review__ai-title {
  margin-bottom: var(--lm-space-2);
  font-size: var(--lm-text-sm);
  color: var(--lm-pink);
}
.review__ai-list { display: flex; flex-wrap: wrap; gap: var(--lm-space-2); }
.review__ai-tag {
  display: flex;
  align-items: center;
  gap: var(--lm-space-2);
  padding: 7px 13px;
  border: 1px solid var(--lm-pink-line);
  border-radius: 999px;
  background: var(--lm-card);
  box-shadow: var(--lm-shadow-card);
}
.review__ai-name {
  font-size: var(--lm-text-lg);
  color: var(--lm-pink);
}
.review__ai-slot {
  display: inline-flex;
  align-items: baseline;
  gap: 4px;
  font-size: var(--lm-text-xs);
  color: var(--lm-ink-faint);
}
.review__ai-slot b { font-size: var(--lm-text-md); font-weight: 400; color: var(--lm-ink); }
.review__ai-slot--want b { color: var(--lm-pink-btn); }
.review__ai-key { letter-spacing: -0.02em; }

.review__revisit {
  font-size: var(--lm-text-sm);
  color: var(--lm-ink-soft);
}
.review__revisit strong { font-weight: 400; color: var(--accent); }

@media (max-width: 700px) {
  .review { min-height: 0; }
}
</style>
