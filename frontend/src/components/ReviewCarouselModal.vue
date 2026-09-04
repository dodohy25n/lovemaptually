<script setup>
import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import BaseIcon from './BaseIcon.vue'
import ReviewCard from './ReviewCard.vue'
import ReviewWriteForm from './ReviewWriteForm.vue'
import { usePlacesStore } from '@/stores/places.js'
import { COUPLE } from '@/utils/users.js'
import { isLocalMode } from '@/services/config.js'
import { createReviewFromApi, fetchGroupPlaceReviews } from '@/services/reviewApi.js'
import { fetchMyGroups } from '@/services/groupApi.js'

const props = defineProps({
  open: { type: Boolean, default: false },
  initialRole: { type: String, default: 'him' },
  placeId: { type: String, default: '' },
  groupId: { type: [String, Number], default: null },
})
const emit = defineEmits(['close', 'saved'])
const store = usePlacesStore()
const { recentPlaces } = storeToRefs(store)
const role = ref(props.initialRole)
const index = ref(0)

// api 모드에서는 장소 목록에 리뷰가 딸려 오지 않아 장소마다 따로 불러옵니다.
const apiMode = computed(() => !isLocalMode())
const activeGroupId = ref(props.groupId ?? null)
const EMPTY_REVIEWS = { myReview: null, otherReviews: [], locked: false, lockedReason: '' }
const groupReviews = ref({ ...EMPTY_REVIEWS })
const reviewsLoading = ref(false)
const saving = ref(false)
const saveError = ref('')
const tagFailed = ref(false)

const SAVE_ERRORS = {
  duplicate_review: '그 날짜에 남긴 리뷰가 이미 있습니다. 다른 날짜를 골라 주세요.',
  rating_out_of_range: '별점은 1점부터 5점까지만 저장할 수 있습니다.',
}
const SAVE_ERROR_FALLBACK = '리뷰를 저장하지 못했습니다. 잠시 후 다시 시도해 주세요.'
const LOCK_FALLBACK = '내 리뷰를 남기면 다른 구성원의 리뷰가 함께 공개됩니다.'

watch(() => props.initialRole, (next) => { role.value = next; index.value = 0 })
watch(() => props.open, (open) => { if (open) index.value = 0 })
watch(() => props.groupId, (next) => { if (next != null) activeGroupId.value = next })

const member = computed(() => COUPLE[role.value])
const entries = computed(() => recentPlaces.value
  .filter((place) => !props.placeId || place.id === props.placeId)
  .map((place) => ({
  place,
  review: place.reviews.find((item) => item.userId === member.value.userId) ?? null,
})))
const current = computed(() => entries.value[index.value] ?? null)
const currentPlaceId = computed(() => current.value?.place?.id ?? '')

/** 'him' 자리가 로그인한 사용자, 'her' 자리가 함께 기록하는 구성원입니다. */
const isMine = computed(() => role.value === 'him')
const currentReview = computed(() => {
  if (!current.value) return null
  if (!apiMode.value) return current.value.review
  return isMine.value
    ? groupReviews.value.myReview
    : (groupReviews.value.otherReviews[0] ?? null)
})
/** 상대 리뷰 잠금은 오류가 아니라 상태입니다. 내가 먼저 쓰면 풀립니다. */
const showLock = computed(() =>
  apiMode.value && !isMine.value && groupReviews.value.locked && !currentReview.value)
const showForm = computed(() =>
  apiMode.value && isMine.value && !reviewsLoading.value && !currentReview.value && Boolean(current.value))

async function resolveGroupId() {
  if (activeGroupId.value != null) return activeGroupId.value
  const groups = await fetchMyGroups().catch(() => [])
  const primary = groups.find((group) => group.type === 'COUPLE') ?? groups[0]
  activeGroupId.value = primary?.groupId ?? null
  return activeGroupId.value
}

async function loadReviews() {
  if (!apiMode.value || !props.open || !currentPlaceId.value) return
  const groupId = await resolveGroupId()
  if (groupId == null) return
  reviewsLoading.value = true
  try {
    groupReviews.value = await fetchGroupPlaceReviews(groupId, currentPlaceId.value)
  } catch {
    groupReviews.value = { ...EMPTY_REVIEWS }
  } finally {
    reviewsLoading.value = false
  }
}

watch([() => props.open, currentPlaceId], () => {
  saveError.value = ''
  tagFailed.value = false
  groupReviews.value = { ...EMPTY_REVIEWS }
  loadReviews()
}, { immediate: true })

async function submitReview(draft) {
  if (saving.value || !current.value) return
  saving.value = true
  saveError.value = ''
  tagFailed.value = false
  const placeId = current.value.place.id
  try {
    const saved = await createReviewFromApi(placeId, {
      userId: COUPLE.him.userId,
      userName: COUPLE.him.userName,
      rating: draft.rating,
      content: draft.content,
    }, {
      visitedOn: draft.visitedOn,
      withGroupId: activeGroupId.value,
    })
    groupReviews.value = {
      ...groupReviews.value,
      myReview: {
        userId: saved.userId,
        nickname: COUPLE.him.userName,
        rating: saved.rating,
        content: saved.content,
        text: saved.content,
        visitedOn: saved.visitedOn ?? draft.visitedOn,
        tags: saved.extractedTags,
        images: [],
      },
    }
    tagFailed.value = saved.tagStatus === 'FAILED'
    // 핀 라벨이 바뀌었으므로 지도가 쓰는 장소 목록을 다시 읽습니다.
    await store.load()
    emit('saved', { placeId })
  } catch (error) {
    saveError.value = SAVE_ERRORS[error?.code] ?? SAVE_ERROR_FALLBACK
  } finally {
    saving.value = false
  }
}

function move(step) {
  const count = entries.value.length
  if (!count) return
  index.value = (index.value + step + count) % count
}
function selectRole(next) { role.value = next; index.value = 0 }
function onKeydown(event) {
  if (!props.open) return
  if (event.key === 'Escape') emit('close')
  // 폼에 글자를 쓰는 중에는 방향키가 카드를 넘기면 안 됩니다.
  const tag = event.target?.tagName
  if (tag === 'INPUT' || tag === 'TEXTAREA') return
  if (event.key === 'ArrowLeft') move(-1)
  if (event.key === 'ArrowRight') move(1)
}
onMounted(() => { document.addEventListener('keydown', onKeydown); if (!recentPlaces.value.length) store.load() })
onBeforeUnmount(() => document.removeEventListener('keydown', onKeydown))
</script>

<template>
  <div v-if="open" class="overlay" @click.self="emit('close')">
    <section class="viewer" role="dialog" aria-modal="true" :aria-label="`${member.label} 게시물`" data-testid="review-carousel">
      <button class="close" type="button" aria-label="기억 팝업 닫기" @click="emit('close')"><BaseIcon name="close" :size="20" /></button>
      <header class="viewer__head">
        <div class="tabs" role="tablist" aria-label="기억 작성자">
          <button role="tab" :aria-selected="role==='him'" :class="{active:role==='him'}" @click="selectRole('him')">{{ COUPLE.him.label }}</button>
          <button role="tab" :aria-selected="role==='her'" :class="{active:role==='her'}" @click="selectRole('her')">{{ COUPLE.her.label }}</button>
        </div>
        <h2>{{ member.label }} ♡</h2>
        <p>{{ member.userName }}의 시선으로 바라본 우리의 데이트</p>
      </header>

      <div v-if="current" class="stage">
        <button class="arrow arrow--left" type="button" aria-label="이전 게시물" @click="move(-1)">‹</button>

        <ReviewWriteForm
          v-if="showForm"
          :key="`form-${current.place.id}`"
          :place="current.place"
          :role="role"
          :saving="saving"
          :error-message="saveError"
          @submit="submitReview"
        />

        <p v-else-if="showLock" class="locked" role="status" data-testid="review-locked">
          {{ groupReviews.lockedReason || LOCK_FALLBACK }}
        </p>

        <Transition v-else name="slide" mode="out-in">
          <ReviewCard :key="`${role}-${current.place.id}`" :place="current.place" :review="currentReview" :role="role" />
        </Transition>

        <button class="arrow arrow--right" type="button" aria-label="다음 게시물" @click="move(1)">›</button>
      </div>
      <p v-else class="empty">아직 작성된 리뷰가 없어요.</p>

      <p v-if="tagFailed" class="tag-failed" role="status" data-testid="review-tag-failed">
        태그를 뽑지 못했지만 리뷰는 저장되었습니다.
      </p>

      <footer v-if="entries.length" class="viewer__foot">
        <span>{{ index + 1 }} / {{ entries.length }}</span>
        <div class="dots" aria-label="게시물 위치">
          <button v-for="(_, dotIndex) in entries" :key="dotIndex" :class="{active:index===dotIndex}" :aria-label="`${dotIndex+1}번째 게시물`" @click="index=dotIndex"></button>
        </div>
        <small>← → 방향키로도 넘길 수 있어요</small>
      </footer>
    </section>
  </div>
</template>

<style scoped>
.overlay{position:fixed;inset:0;z-index:var(--lm-z-modal);display:grid;place-items:center;padding:18px;background:rgba(70,48,43,.54);backdrop-filter:blur(5px)}.viewer{position:relative;width:min(680px,96vw);max-height:96vh;overflow:auto;padding:38px 54px 24px;background:linear-gradient(rgba(255,249,241,.94),rgba(255,249,241,.94)),url('@/assets/decorations/paper-texture.jpg') center/cover;border:6px solid #f1beb9;border-radius:28px;box-shadow:0 28px 80px rgba(45,28,24,.4)}.viewer:before{content:'';position:absolute;left:20px;right:20px;top:0;height:23px;background:radial-gradient(circle at 11px 8px,#aa756d 0 4px,transparent 4.5px);background-size:28px 22px}.close{position:absolute;z-index:3;right:17px;top:22px;display:grid;place-items:center;width:38px;height:38px;border-radius:50%;background:#ef7188;color:#fff}.viewer__head{text-align:center;margin-bottom:18px}.viewer__head h2{margin-top:12px;color:var(--lm-pink);font-size:30px}.viewer__head p{margin-top:5px;color:var(--lm-ink-soft);font-size:12px}.tabs{display:inline-flex;padding:4px;border:1px solid var(--lm-pink-line);border-radius:999px;background:#fff}.tabs button{padding:7px 18px;border-radius:999px;font-size:12px}.tabs button.active{background:var(--lm-pink);color:#fff}.stage{position:relative;width:min(480px,100%);margin:auto}.stage :deep(.review){width:100%;min-height:560px}.arrow{position:absolute;z-index:2;top:50%;width:42px;height:42px;border-radius:50%;background:#fff;color:var(--lm-pink);border:1px solid var(--lm-pink-line);box-shadow:var(--lm-shadow-card);font-size:34px;line-height:1}.arrow--left{left:-50px}.arrow--right{right:-50px}.viewer__foot{display:grid;grid-template-columns:80px 1fr 170px;align-items:center;margin-top:15px;color:var(--lm-ink-soft);font-size:11px}.dots{display:flex;justify-content:center;gap:7px}.dots button{width:7px;height:7px;border-radius:50%;background:#e7d6d0}.dots button.active{width:19px;border-radius:99px;background:var(--lm-pink)}.viewer__foot small{text-align:right}.empty{text-align:center;padding:80px}
.locked{display:grid;place-items:center;min-height:560px;padding:var(--lm-space-6);border:1px dashed var(--lm-pink-line);border-radius:var(--lm-radius-lg);background:var(--lm-card);color:var(--lm-ink-soft);font-size:var(--lm-text-md);line-height:1.7;text-align:center}
.tag-failed{margin-top:12px;text-align:center;color:var(--lm-ink-faint);font-size:var(--lm-text-xs)}
.stage :deep(.write){width:100%}.slide-enter-active,.slide-leave-active{transition:opacity .16s ease,transform .16s ease}.slide-enter-from{opacity:0;transform:translateX(18px)}.slide-leave-to{opacity:0;transform:translateX(-18px)}@media(max-width:620px){.viewer{padding:55px 14px 20px}.stage{width:calc(100% - 44px)}.arrow{width:34px;height:34px}.arrow--left{left:-38px}.arrow--right{right:-38px}.viewer__foot{grid-template-columns:50px 1fr}.viewer__foot small{display:none}.stage :deep(.review),.locked{min-height:0}}
</style>
