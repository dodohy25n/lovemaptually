<script setup>
import { computed, onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import BaseIcon from './BaseIcon.vue'
import ReviewCard from './ReviewCard.vue'
import { usePlacesStore } from '@/stores/places.js'
import { COUPLE } from '@/utils/users.js'

const props = defineProps({
  open: { type: Boolean, default: false },
  initialRole: { type: String, default: 'him' },
  placeId: { type: String, default: '' },
})
const emit = defineEmits(['close'])
const store = usePlacesStore()
const { recentPlaces } = storeToRefs(store)
const role = ref(props.initialRole)
const index = ref(0)

watch(() => props.initialRole, (next) => { role.value = next; index.value = 0 })
watch(() => props.open, (open) => { if (open) index.value = 0 })

const member = computed(() => COUPLE[role.value])
const entries = computed(() => recentPlaces.value
  .filter((place) => !props.placeId || place.id === props.placeId)
  .map((place) => ({
  place,
  review: place.reviews.find((item) => item.userId === member.value.userId) ?? null,
})))
const current = computed(() => entries.value[index.value] ?? null)

function move(step) {
  const count = entries.value.length
  if (!count) return
  index.value = (index.value + step + count) % count
}
function selectRole(next) { role.value = next; index.value = 0 }
function onKeydown(event) {
  if (!props.open) return
  if (event.key === 'Escape') emit('close')
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
          <button role="tab" :aria-selected="role==='him'" :class="{active:role==='him'}" @click="selectRole('him')">그의 기억</button>
          <button role="tab" :aria-selected="role==='her'" :class="{active:role==='her'}" @click="selectRole('her')">그녀의 기억</button>
        </div>
        <h2>{{ member.label }} ♡</h2>
        <p>{{ member.userName }}의 시선으로 바라본 우리의 데이트</p>
      </header>

      <div v-if="current" class="stage">
        <button class="arrow arrow--left" type="button" aria-label="이전 게시물" @click="move(-1)">‹</button>
        <Transition name="slide" mode="out-in">
          <ReviewCard :key="`${role}-${current.place.id}`" :place="current.place" :review="current.review" :role="role" />
        </Transition>
        <button class="arrow arrow--right" type="button" aria-label="다음 게시물" @click="move(1)">›</button>
      </div>
      <p v-else class="empty">아직 작성된 리뷰가 없어요.</p>

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
.overlay{position:fixed;inset:0;z-index:var(--lm-z-modal);display:grid;place-items:center;padding:18px;background:rgba(70,48,43,.54);backdrop-filter:blur(5px)}.viewer{position:relative;width:min(680px,96vw);max-height:96vh;overflow:auto;padding:38px 54px 24px;background:linear-gradient(rgba(255,249,241,.94),rgba(255,249,241,.94)),url('@/assets/decorations/paper-texture.jpg') center/cover;border:6px solid #f1beb9;border-radius:28px;box-shadow:0 28px 80px rgba(45,28,24,.4)}.viewer:before{content:'';position:absolute;left:20px;right:20px;top:0;height:23px;background:radial-gradient(circle at 11px 8px,#aa756d 0 4px,transparent 4.5px);background-size:28px 22px}.close{position:absolute;z-index:3;right:17px;top:22px;display:grid;place-items:center;width:38px;height:38px;border-radius:50%;background:#ef7188;color:#fff}.viewer__head{text-align:center;margin-bottom:18px}.viewer__head h2{margin-top:12px;color:var(--lm-pink);font-size:30px}.viewer__head p{margin-top:5px;color:var(--lm-ink-soft);font-size:12px}.tabs{display:inline-flex;padding:4px;border:1px solid var(--lm-pink-line);border-radius:999px;background:#fff}.tabs button{padding:7px 18px;border-radius:999px;font-size:12px}.tabs button.active{background:var(--lm-pink);color:#fff}.stage{position:relative;width:min(480px,100%);margin:auto}.stage :deep(.review){width:100%;min-height:560px}.arrow{position:absolute;z-index:2;top:50%;width:42px;height:42px;border-radius:50%;background:#fff;color:var(--lm-pink);border:1px solid var(--lm-pink-line);box-shadow:var(--lm-shadow-card);font-size:34px;line-height:1}.arrow--left{left:-50px}.arrow--right{right:-50px}.viewer__foot{display:grid;grid-template-columns:80px 1fr 170px;align-items:center;margin-top:15px;color:var(--lm-ink-soft);font-size:11px}.dots{display:flex;justify-content:center;gap:7px}.dots button{width:7px;height:7px;border-radius:50%;background:#e7d6d0}.dots button.active{width:19px;border-radius:99px;background:var(--lm-pink)}.viewer__foot small{text-align:right}.empty{text-align:center;padding:80px}.slide-enter-active,.slide-leave-active{transition:opacity .16s ease,transform .16s ease}.slide-enter-from{opacity:0;transform:translateX(18px)}.slide-leave-to{opacity:0;transform:translateX(-18px)}@media(max-width:620px){.viewer{padding:55px 14px 20px}.stage{width:calc(100% - 44px)}.arrow{width:34px;height:34px}.arrow--left{left:-38px}.arrow--right{right:-38px}.viewer__foot{grid-template-columns:50px 1fr}.viewer__foot small{display:none}.stage :deep(.review){min-height:0}}
</style>
