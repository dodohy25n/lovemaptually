<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import ReviewCard from '@/components/ReviewCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import { usePlacesStore } from '@/stores/places.js'
import { COUPLE } from '@/utils/users.js'
import { isLocalMode } from '@/services/config.js'
import { fetchGroupPlaceReviews } from '@/services/reviewApi.js'
import { fetchMyGroups } from '@/services/groupApi.js'
import { setActiveGroupId } from '@/services/placeApi.js'
import { STORAGE_KEYS, readJson } from '@/services/storageService.js'

/** 한 사람이 남긴 리뷰를 장소별 카드로 모아 보여줍니다. */
const props = defineProps({
  role: { type: String, required: true, validator: (v) => ['him', 'her'].includes(v) },
})

const store = usePlacesStore()
const { recentPlaces, loading } = storeToRefs(store)
const router = useRouter()

const member = computed(() => COUPLE[props.role])
/** 반대편 구성원의 기억 화면으로 건너가는 링크. */
const other = computed(() => (props.role === 'him'
  ? { member: COUPLE.her, to: '/reviews/partner' }
  : { member: COUPLE.him, to: '/reviews/me' }))

// api 모드에서는 장소 목록에 리뷰가 딸려 오지 않아 장소마다 따로 불러옵니다.
const apiReviews = ref({})

async function loadApiReviews() {
  if (isLocalMode() || recentPlaces.value.length === 0) return
  const groups = await fetchMyGroups().catch(() => [])
  const groupId = groups[0]?.groupId
  if (groupId == null) return
  const me = readJson(STORAGE_KEYS.authUser)
  const mineWanted = props.role === 'him'
  const loaded = {}
  await Promise.all(recentPlaces.value.map(async (place) => {
    try {
      const result = await fetchGroupPlaceReviews(groupId, place.id)
      const partner = result.otherReviews[0] ?? null
      const mine = result.myReview
      // 내 화면이면 내 리뷰, 상대 화면이면 상대 리뷰를 씁니다.
      loaded[place.id] = mineWanted ? mine : partner
      if (!mineWanted && me?.userId != null && mine && mine.userId !== me.userId) {
        loaded[place.id] = mine
      }
    } catch {
      loaded[place.id] = null
    }
  }))
  apiReviews.value = loaded
}

/** 리뷰를 쓴 장소가 먼저 오도록 정렬합니다. 리뷰가 없는 장소도 빈 카드로 보여줍니다. */
const entries = computed(() =>
  recentPlaces.value.map((place) => ({
    place,
    review: isLocalMode()
      ? (place.reviews.find((r) => r.userId === member.value.userId) ?? null)
      : (apiReviews.value[place.id] ?? null),
  })),
)

watch(recentPlaces, loadApiReviews, { immediate: false })

const written = computed(() => entries.value.filter((entry) => entry.review))

onMounted(async () => {
  // 지도 화면을 거치지 않고 바로 들어와도 장소를 불러올 수 있게 그룹을 먼저 정합니다.
  if (!isLocalMode()) {
    const groups = await fetchMyGroups().catch(() => [])
    if (groups[0]?.groupId != null) setActiveGroupId(groups[0].groupId)
  }
  await store.load()
  await loadApiReviews()
})
</script>

<template>
  <section class="reviews" :data-role="role">
    <header class="reviews__head">
      <h1 class="reviews__title">{{ member.label }}</h1>
      <p class="reviews__desc">
        {{ member.userName }}님이 남긴 리뷰 {{ written.length }}개
      </p>
      <RouterLink class="reviews__switch" :to="other.to">{{ other.member.label }}</RouterLink>
    </header>

    <p v-if="loading" class="reviews__loading" role="status">불러오는 중이에요…</p>

    <EmptyState
      v-else-if="entries.length === 0"
      title="아직 기록한 장소가 없어요"
      description="지도에서 첫 장소를 기록하면 여기에 리뷰 카드가 쌓여요."
      action-label="지도로 가기"
      @action="router.push('/map')"
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
.reviews__switch {
  display: inline-block;
  margin-top: var(--lm-space-3);
  padding: 8px 18px;
  border: 1px solid var(--lm-pink-line);
  border-radius: 999px;
  background: var(--lm-pink-bg);
  color: var(--lm-pink);
  font-size: var(--lm-text-sm);
  font-weight: 700;
  text-decoration: none;
}
.reviews[data-role='him'] .reviews__title { color: var(--lm-him); }
.reviews[data-role='her'] .reviews__title { color: var(--lm-her); }
.reviews__desc { font-size: var(--lm-text-sm); color: var(--lm-ink-soft); }
.reviews__loading { font-size: var(--lm-text-sm); color: var(--lm-ink-soft); }

.reviews__grid {
  display: grid;
  /* 카드 크기를 열 단위로 고정해 두 사람의 리뷰 카드가 항상 같은 폭이 되게 합니다. */
  grid-template-columns: repeat(auto-fill, minmax(340px, 1fr));
  gap: var(--lm-space-5);
}
</style>
