<script setup>
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import ReviewCard from '@/components/ReviewCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import { usePlacesStore } from '@/stores/places.js'
import { COUPLE } from '@/utils/users.js'

/** '그의 리뷰' / '그녀의 리뷰' — 한 사람이 남긴 리뷰를 장소별 카드로 모아 보여줍니다. */
const props = defineProps({
  role: { type: String, required: true, validator: (v) => ['him', 'her'].includes(v) },
})

const store = usePlacesStore()
const { recentPlaces, loading } = storeToRefs(store)
const router = useRouter()

const member = computed(() => COUPLE[props.role])

/** 리뷰를 쓴 장소가 먼저 오도록 정렬합니다. 리뷰가 없는 장소도 빈 카드로 보여줍니다. */
const entries = computed(() =>
  recentPlaces.value.map((place) => ({
    place,
    review: place.reviews.find((r) => r.userId === member.value.userId) ?? null,
  })),
)

const written = computed(() => entries.value.filter((entry) => entry.review))

onMounted(() => {
  if (recentPlaces.value.length === 0) store.load()
})
</script>

<template>
  <section class="reviews" :data-role="role">
    <header class="reviews__head">
      <h1 class="reviews__title">{{ member.label }}</h1>
      <p class="reviews__desc">
        {{ member.userName }}님이 남긴 리뷰 {{ written.length }}개
      </p>
    </header>

    <p v-if="loading" class="reviews__loading" role="status">불러오는 중이에요…</p>

    <EmptyState
      v-else-if="entries.length === 0"
      title="아직 기록한 장소가 없어요"
      description="지도에서 첫 장소를 기록하면 여기에 리뷰 카드가 쌓여요."
      action-label="지도로 가기"
      @action="router.push('/')"
    />

    <ul v-else class="reviews__grid">
      <li v-for="entry in entries" :key="entry.place.id">
        <ReviewCard :place="entry.place" :review="entry.review" :role="role" />
      </li>
    </ul>
  </section>
</template>

<style scoped>
.reviews { display: flex; flex-direction: column; gap: var(--lm-space-5); }
.reviews__head { display: flex; flex-direction: column; gap: 4px; }
.reviews__title { font-size: var(--lm-text-2xl); }
.reviews[data-role='him'] .reviews__title { color: var(--lm-him); }
.reviews[data-role='her'] .reviews__title { color: var(--lm-her); }
.reviews__desc { font-size: var(--lm-text-sm); color: var(--lm-ink-soft); }
.reviews__loading { font-size: var(--lm-text-sm); color: var(--lm-ink-soft); }

.reviews__grid {
  display: grid;
  /* 카드 크기를 열 단위로 고정해 그의/그녀의 리뷰 카드가 항상 같은 폭이 되게 합니다. */
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: var(--lm-space-5);
}
</style>
