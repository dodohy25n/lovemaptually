<script setup>
import { computed } from 'vue'
import HeartRating from './HeartRating.vue'
import { COUPLE_MEMBERS } from '@/utils/users.js'
import { reviewAverage } from '@/utils/heartGrade.js'

/**
 * '우리의 기억' 카드 — 두 사람의 방문 기록을 한 장으로 합친 것.
 * 리뷰 카드와 같은 폭·같은 최소 높이를 유지합니다.
 */
const props = defineProps({
  place: { type: Object, required: true },
  photoSlots: { type: Number, default: 3 },
})

defineEmits(['open'])

const photos = computed(() =>
  Array.from({ length: props.photoSlots }, (_, index) => props.place.images?.[index] ?? null),
)

/** 각 사람의 리뷰를 (없으면 null로) 순서대로 정리합니다. */
const memberReviews = computed(() =>
  COUPLE_MEMBERS.map((member) => ({
    member,
    review: props.place.reviews.find((r) => r.userId === member.userId) ?? null,
  })),
)

/** 두 사람 모두 재방문 의사가 있을 때만 '또 가고 싶어요'. */
const revisit = computed(() => {
  const reviews = props.place.reviews
  if (reviews.length === 0) return '아직 정하지 않았어요'
  const all = reviews.every((review) => review.revisitIntent)
  const some = reviews.some((review) => review.revisitIntent)
  if (all) return '둘 다 또 가고 싶어요'
  if (some) return '한 사람만 또 가고 싶어요'
  return '다음엔 다른 곳으로'
})

/** 한줄평: 두 리뷰 중 먼저 작성된 본문의 첫 문장을 씁니다. */
const oneLiner = computed(() => {
  const content = props.place.memo || props.place.reviews.find((r) => r.content)?.content || ''
  const first = content.split(/(?<=[.!?])\s|\n/)[0]
  return first || ''
})
</script>

<template>
  <article class="memory" :data-testid="`memory-card-${place.id}`">
    <span class="lm-tape lm-tape--tl"></span>

    <header class="memory__head">
      <div class="memory__title">
        <h3 class="memory__name">{{ place.name }}</h3>
        <p class="memory__sub">
          <span>{{ place.category }}</span>
          <span v-if="place.visitedAt" aria-hidden="true">·</span>
          <span v-if="place.visitedAt">{{ place.visitedAt }}</span>
        </p>
      </div>
      <HeartRating :score="place.coupleScore" :size="24" show-label />
    </header>

    <ul class="memory__photos" :aria-label="`${place.name} 사진`">
      <li v-for="(photo, index) in photos" :key="index" class="memory__photo">
        <img v-if="photo" :src="photo" :alt="`${place.name} 사진 ${index + 1}`" loading="lazy" />
        <span v-else class="memory__photo-empty">
          <img src="/assets/photo-placeholder.svg" alt="" width="30" height="30" />
          <span class="lm-sr-only">사진 없음</span>
        </span>
      </li>
    </ul>

    <p v-if="oneLiner" class="memory__oneliner">“{{ oneLiner }}”</p>
    <p v-else class="memory__oneliner memory__oneliner--empty">아직 남긴 한줄평이 없어요.</p>

    <ul class="memory__reviews">
      <li v-for="entry in memberReviews" :key="entry.member.userId" class="memory__review">
        <span class="memory__who" :data-role="entry.member.role">
          {{ entry.member.userName }}
        </span>
        <span v-if="entry.review" class="memory__excerpt">{{ entry.review.content || '리뷰 본문 없음' }}</span>
        <span v-else class="memory__excerpt memory__excerpt--empty">아직 리뷰 없음</span>
        <HeartRating v-if="entry.review" :score="reviewAverage(entry.review)" :size="16" />
      </li>
    </ul>

    <footer class="memory__foot">
      <p class="memory__revisit">재방문 의사 · <strong>{{ revisit }}</strong></p>
      <button type="button" class="lm-btn lm-btn--ghost" @click="$emit('open', place.id)">
        지도에서 보기
      </button>
    </footer>
  </article>
</template>

<style scoped>
.memory {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: var(--lm-space-3);
  min-height: 420px;
  padding: var(--lm-space-5);
  background: var(--lm-card);
  border: 1px solid var(--lm-card-edge);
  border-radius: var(--lm-radius-lg);
  box-shadow: var(--lm-shadow-card);
}

.memory__head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--lm-space-3);
}
.memory__name { font-size: var(--lm-text-lg); color: var(--lm-ink); }
.memory__sub {
  display: flex;
  gap: 5px;
  margin-top: 3px;
  font-size: var(--lm-text-xs);
  color: var(--lm-ink-soft);
}

.memory__photos {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--lm-space-2);
}
.memory__photo {
  aspect-ratio: 4 / 3;
  border-radius: var(--lm-radius-sm);
  overflow: hidden;
  border: 1px solid var(--lm-card-edge);
  background: var(--lm-header-bg);
}
.memory__photo img { width: 100%; height: 100%; object-fit: cover; }
.memory__photo-empty {
  display: grid;
  place-items: center;
  width: 100%;
  height: 100%;
  opacity: 0.5;
}

.memory__oneliner {
  padding: var(--lm-space-3);
  background: var(--lm-pink-bg);
  border-radius: var(--lm-radius-sm);
  font-size: var(--lm-text-sm);
  line-height: 1.7;
  color: var(--lm-ink);
}
.memory__oneliner--empty { background: var(--lm-header-bg); color: var(--lm-ink-faint); }

.memory__reviews {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: var(--lm-space-2);
}
.memory__review {
  display: flex;
  align-items: center;
  gap: var(--lm-space-2);
  font-size: var(--lm-text-xs);
}
.memory__who {
  flex: none;
  padding: 2px 9px;
  border-radius: 999px;
  color: #fff;
}
.memory__who[data-role='him'] { background: var(--lm-him); }
.memory__who[data-role='her'] { background: var(--lm-her); }
.memory__excerpt {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--lm-ink-soft);
}
.memory__excerpt--empty { color: var(--lm-ink-faint); }

.memory__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lm-space-3);
  padding-top: var(--lm-space-3);
  border-top: 1px solid var(--lm-card-edge-soft);
}
.memory__revisit { font-size: var(--lm-text-sm); color: var(--lm-ink-soft); }
.memory__revisit strong { font-weight: 400; color: var(--lm-pink); }
</style>
