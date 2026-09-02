<script setup>
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import MemoryCard from '@/components/MemoryCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import { usePlacesStore } from '@/stores/places.js'
import { formatScore } from '@/utils/heartGrade.js'

/** '우리의 기억' — 두 사람의 방문 기록을 장소별로 통합해 보여줍니다. */
const store = usePlacesStore()
const { recentPlaces, loading, totalCount } = storeToRefs(store)
const router = useRouter()

const averageScore = computed(() => {
  if (recentPlaces.value.length === 0) return 0
  const sum = recentPlaces.value.reduce((acc, place) => acc + place.coupleScore, 0)
  return sum / recentPlaces.value.length
})

function openOnMap(placeId) {
  store.select(placeId)
  router.push('/')
}

onMounted(() => {
  if (recentPlaces.value.length === 0) store.load()
})
</script>

<template>
  <section class="memories">
    <header class="memories__head">
      <div>
        <h1 class="memories__title">우리의 기억</h1>
        <p class="memories__desc">두 사람이 함께 남긴 {{ totalCount }}개의 기록</p>
      </div>
      <p class="memories__average">
        평균 점수
        <strong data-testid="memories-average">{{ formatScore(averageScore) }}</strong>
      </p>
    </header>

    <p v-if="loading" class="memories__loading" role="status">불러오는 중이에요…</p>

    <EmptyState
      v-else-if="recentPlaces.length === 0"
      title="아직 쌓인 기억이 없어요"
      description="지도에 장소를 기록하면 두 사람의 기억이 여기에 모여요."
      action-label="지도로 가기"
      @action="router.push('/')"
    />

    <ul v-else class="memories__grid">
      <li v-for="place in recentPlaces" :key="place.id">
        <MemoryCard :place="place" @open="openOnMap" />
      </li>
    </ul>
  </section>
</template>

<style scoped>
.memories { display: flex; flex-direction: column; gap: var(--lm-space-5); }
.memories__head {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: var(--lm-space-4);
  flex-wrap: wrap;
}
.memories__title { font-size: var(--lm-text-2xl); color: var(--lm-pink); }
.memories__desc { margin-top: 4px; font-size: var(--lm-text-sm); color: var(--lm-ink-soft); }
.memories__average {
  display: flex;
  align-items: baseline;
  gap: var(--lm-space-2);
  font-size: var(--lm-text-sm);
  color: var(--lm-ink-soft);
}
.memories__average strong {
  font-size: var(--lm-text-xl);
  font-weight: 400;
  color: var(--lm-pink);
  font-variant-numeric: tabular-nums;
}
.memories__loading { font-size: var(--lm-text-sm); color: var(--lm-ink-soft); }

.memories__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: var(--lm-space-5);
}
</style>
