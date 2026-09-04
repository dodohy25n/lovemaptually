<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import BaseIcon from '@/components/BaseIcon.vue'
import CoupleReportCard from '@/components/CoupleReportCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import { usePlacesStore } from '@/stores/places.js'
import bookshelfAsset from '../../frontend-assets/decorations/empty-memory-bookshelf.png'
import bookPink from '../../frontend-assets/decorations/memory-book-pink-beach.png'
import bookIvory from '../../frontend-assets/decorations/memory-book-ivory-flower.png'
import bookLavender from '../../frontend-assets/decorations/memory-book-lavender-cafe.png'
import bookSage from '../../frontend-assets/decorations/memory-book-sage-spring.png'
import bookBlue from '../../frontend-assets/decorations/memory-book-blue-seaside.png'
import heartFlourish from '../../frontend-assets/decorations/love_maptually_heart_flourish.png'
import pinkTape from '../../frontend-assets/decorations/love_maptually_pink_tape.png'

const store = usePlacesStore()
const { recentPlaces, loading } = storeToRefs(store)
const selectedMonthKey = ref('')
const selectedIndex = ref(0)
const bookAssets = [bookPink, bookIvory, bookLavender, bookSage, bookBlue, bookPink]

const monthGroups = computed(() => {
  const groups = new Map()
  for (const place of recentPlaces.value) {
    const month = /^\d{4}-\d{2}/.exec(place.visitedAt)?.[0] ?? '날짜 미정'
    if (!groups.has(month)) groups.set(month, [])
    groups.get(month).push(place)
  }
  return [...groups.entries()]
    .map(([key, places]) => ({ key, label: key.replace('-', '.'), places }))
    .sort((a, b) => a.key.localeCompare(b.key))
})
const diaries = computed(() => monthGroups.value.slice(0, bookAssets.length).map((group, index) => ({ ...group, image: bookAssets[index] })))
const selectedMonth = computed(() => monthGroups.value.find((group) => group.key === selectedMonthKey.value) ?? null)
const selectedPlaces = computed(() => selectedMonth.value?.places ?? [])
const reportPageCount = 5
const isDetailOpen = computed(() => Boolean(selectedMonth.value))

function openDiary(monthKey) { selectedMonthKey.value = monthKey; selectedIndex.value = 0 }
function closeDetail() { selectedMonthKey.value = ''; selectedIndex.value = 0 }
function moveDetail(step) {
  selectedIndex.value = (selectedIndex.value + step + reportPageCount) % reportPageCount
}
function onKeydown(event) {
  if (!isDetailOpen.value) return
  if (event.key === 'Escape') closeDetail()
  if (event.key === 'ArrowLeft') moveDetail(-1)
  if (event.key === 'ArrowRight') moveDetail(1)
}

watch(isDetailOpen, (open) => { document.body.style.overflow = open ? 'hidden' : '' })
onMounted(() => {
  document.addEventListener('keydown', onKeydown)
  if (!recentPlaces.value.length) store.load()
})
onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown)
  document.body.style.overflow = ''
})
</script>

<template>
  <section class="memories">
    <img class="memories__decor memories__decor--heart" :src="heartFlourish" alt="" aria-hidden="true" />
    <img class="memories__decor memories__decor--tape" :src="pinkTape" alt="" aria-hidden="true" />
    <header class="memories__head">
      <div>
        <p class="memories__eyebrow">Love Maptually Archive</p>
        <h1 class="memories__title">추억 저장소</h1>
        <p class="memories__desc">함께 다녀온 장소를 담은 우리만의 다이어리</p>
      </div>
    </header>

    <p v-if="loading" class="memories__loading" role="status">추억을 불러오는 중이에요…</p>
    <EmptyState v-else-if="!recentPlaces.length" title="아직 쌓인 기억이 없어요" description="지도에 장소를 기록하면 두 사람의 다이어리가 이곳에 놓여요." />

    <div v-else class="shelf" data-testid="memory-bookshelf">
      <img class="shelf__base" :src="bookshelfAsset" alt="꽃과 화분으로 장식된 나무 책장" />
      <ul class="shelf__books">
        <li v-for="({ key, label, image }) in diaries" :key="key" class="shelf__slot">
          <button type="button" class="diary" :data-testid="`memory-diary-${key}`" :aria-label="`${label} 추억 다이어리 열기`" @click="openDiary(key)">
            <img :src="image" alt="" />
            <span class="diary__label">{{ label }}</span>
          </button>
        </li>
      </ul>
    </div>

    <Teleport to="body">
      <div v-if="isDetailOpen" class="detail-overlay" data-testid="memory-detail-modal" @click.self="closeDetail">
        <section class="detail" role="dialog" aria-modal="true" aria-labelledby="memory-detail-title">
          <button type="button" class="detail__close" aria-label="우리의 기억 닫기" @click="closeDetail"><BaseIcon name="close" :size="21" /></button>
          <header class="detail__head">
            <p>Our Memory Diary</p>
            <h2 id="memory-detail-title">우리의 기억 ♡</h2>
            <span>{{ selectedMonth.label }} · REPORT {{ selectedIndex + 1 }} / {{ reportPageCount }}</span>
          </header>
          <div class="detail__stage">
            <button type="button" class="detail__arrow detail__arrow--left" aria-label="이전 기억" @click="moveDetail(-1)">‹</button>
            <Transition name="memory-slide" mode="out-in">
              <CoupleReportCard
                :key="`${selectedMonth.key}-${selectedIndex}`"
                :places="selectedPlaces"
                :month="selectedMonth.label"
                :page="selectedIndex"
              />
            </Transition>
            <button type="button" class="detail__arrow detail__arrow--right" aria-label="다음 기억" @click="moveDetail(1)">›</button>
          </div>
          <footer class="detail__foot">
            <span>{{ selectedIndex + 1 }} / {{ reportPageCount }}</span>
            <div class="detail__dots" aria-label="추억 게시물 위치">
              <button v-for="index in reportPageCount" :key="index" type="button" :class="{ active: selectedIndex === index - 1 }" :aria-label="`${index}번째 리포트 보기`" @click="selectedIndex = index - 1"></button>
            </div>
            <small>← → 방향키로도 넘길 수 있어요</small>
          </footer>
        </section>
      </div>
    </Teleport>
  </section>
</template>

<style scoped>
.memories{position:relative;isolation:isolate;display:flex;flex-direction:column;align-items:center;gap:24px;padding:30px 24px 48px;overflow:hidden;border:1px solid rgba(232,198,194,.72);border-radius:28px;background:linear-gradient(rgba(255,249,242,.76),rgba(255,249,242,.76)),url('../../frontend-assets/decorations/love_maptually_paper_texture.png') center/540px;box-shadow:0 18px 45px rgba(118,76,57,.08)}.memories:before{content:'';position:absolute;z-index:-1;inset:18px;border:1px dashed rgba(237,143,157,.22);border-radius:20px;pointer-events:none}.memories__decor{position:absolute;z-index:-1;pointer-events:none;user-select:none}.memories__decor--heart{top:-82px;right:-70px;width:260px;opacity:.13;transform:rotate(9deg)}.memories__decor--tape{top:82px;left:-82px;width:230px;opacity:.38;transform:rotate(-13deg)}.memories__head{position:relative;z-index:1;width:min(900px,100%);display:flex;align-items:flex-end;justify-content:space-between;gap:24px;padding:8px 28px 0}.memories__eyebrow{font-family:Georgia,serif;font-size:12px;font-style:italic;color:var(--lm-pink)}.memories__title{margin-top:5px;font-size:clamp(30px,4vw,44px);color:var(--lm-pink);letter-spacing:-.04em}.memories__desc{margin-top:7px;font-size:14px;color:var(--lm-ink-soft)}.memories__summary{display:flex;gap:12px;flex-wrap:wrap;justify-content:flex-end;font-size:12px;color:var(--lm-ink-soft)}.memories__summary span{padding:8px 13px;border:1px solid var(--lm-card-edge);border-radius:999px;background:rgba(255,249,241,.82)}.memories__summary strong{color:var(--lm-pink);font-size:15px}.memories__loading{padding:80px;color:var(--lm-ink-soft)}
.shelf{position:relative;width:min(760px,94vw);aspect-ratio:1122/1402;filter:drop-shadow(0 18px 24px rgba(126,83,54,.14))}.shelf__base{position:absolute;inset:0;width:100%;height:100%;object-fit:contain;pointer-events:none}.shelf__books{position:absolute;inset:19.5% 12% 8.2%;display:grid;grid-template-columns:repeat(2,1fr);grid-template-rows:repeat(3,1fr);align-items:end;column-gap:18%;row-gap:2.5%}.shelf__slot{display:flex;justify-content:center;align-items:flex-end;height:100%}.diary{position:relative;width:73%;height:92%;border-radius:12px;filter:drop-shadow(0 7px 5px rgba(99,62,42,.18));transition:transform .18s ease,filter .18s ease}.diary:hover,.diary:focus-visible{transform:translateY(-9px) rotate(-1deg);filter:drop-shadow(0 14px 9px rgba(99,62,42,.23))}.diary:focus-visible{outline:3px solid var(--lm-pink);outline-offset:5px}.diary img{width:100%;height:100%;object-fit:contain}.diary__month{position:absolute;z-index:2;left:18%;right:9%;top:11%;display:grid;place-items:center;height:32%;border-radius:8px;background:rgba(255,249,241,.88);color:#5f4338;font-family:Georgia,serif;font-size:clamp(14px,2.2vw,22px);font-weight:700;letter-spacing:.03em;box-shadow:0 2px 8px rgba(85,57,43,.06)}.diary__label{position:absolute;z-index:2;left:14%;right:8%;bottom:4%;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;padding:4px 5px;border:1px solid #dab38c;border-radius:7px;background:#fff9ed;color:#704f40;font-size:clamp(8px,1.3vw,12px);box-shadow:0 2px 4px rgba(80,48,31,.12)}
.detail-overlay{position:fixed;inset:0;z-index:var(--lm-z-modal);display:grid;place-items:center;padding:20px;background:rgba(64,42,37,.58);backdrop-filter:blur(6px)}.detail{position:relative;width:min(720px,96vw);max-height:96vh;overflow:auto;padding:40px 72px 25px;background:linear-gradient(rgba(255,249,241,.95),rgba(255,249,241,.95)),url('@/assets/decorations/paper-texture.jpg') center/cover;border:6px solid #efb8b1;border-radius:28px;box-shadow:0 28px 80px rgba(45,28,24,.4)}.detail:before{content:'';position:absolute;left:24px;right:24px;top:0;height:23px;background:radial-gradient(circle at 10px 8px,#ad766d 0 4px,transparent 4.5px);background-size:28px 22px}.detail__close{position:absolute;z-index:3;right:18px;top:20px;display:grid;place-items:center;width:40px;height:40px;border-radius:50%;background:var(--lm-pink);color:#fff;box-shadow:0 5px 12px rgba(205,90,112,.28)}.detail__head{text-align:center;margin-bottom:17px}.detail__head p{font-family:Georgia,serif;font-size:11px;font-style:italic;color:var(--lm-pink)}.detail__head h2{margin:5px 0 3px;font-size:30px;color:var(--lm-pink)}.detail__head span{font-size:11px;color:var(--lm-ink-faint)}.detail__stage{position:relative;width:min(520px,100%);margin:auto}.detail__stage :deep(.memory){min-height:420px}.detail__arrow{position:absolute;z-index:4;top:50%;display:grid;place-items:center;width:42px;height:42px;border:1px solid var(--lm-pink-line);border-radius:50%;background:#fff;color:var(--lm-pink);font-size:34px;line-height:1;box-shadow:var(--lm-shadow-card)}.detail__arrow--left{left:-55px}.detail__arrow--right{right:-55px}.detail__foot{display:grid;grid-template-columns:70px 1fr 185px;align-items:center;margin-top:16px;color:var(--lm-ink-soft);font-size:11px}.detail__dots{display:flex;justify-content:center;gap:7px}.detail__dots button{width:7px;height:7px;border-radius:50%;background:#e5d2cd;transition:width .15s ease,background .15s ease}.detail__dots button.active{width:20px;border-radius:99px;background:var(--lm-pink)}.detail__foot small{text-align:right}.memory-slide-enter-active,.memory-slide-leave-active{transition:opacity .2s ease,transform .2s ease}.memory-slide-enter-from{opacity:0;transform:translateX(24px)}.memory-slide-leave-to{opacity:0;transform:translateX(-24px)}
@media(max-height:850px) and (min-width:701px){.memories{height:100%;min-height:0;gap:10px;padding:18px 24px 22px}.memories__head{width:min(620px,100%);padding-top:0}.memories__eyebrow{font-size:10px}.memories__title{margin-top:2px;font-size:32px}.memories__desc{margin-top:3px;font-size:12px}.shelf{width:min(450px,86vw,calc(80vh - 152px))}}
@media(max-width:700px){.memories__head{align-items:flex-start;flex-direction:column;padding-inline:5px}.memories__summary{justify-content:flex-start}.shelf{width:100%}.shelf__books{inset:19.5% 10% 8%;column-gap:12%}.diary{width:82%;height:90%}.detail{padding:50px 15px 22px}.detail__stage{width:calc(100% - 48px)}.detail__arrow{width:34px;height:34px}.detail__arrow--left{left:-40px}.detail__arrow--right{right:-40px}.detail__foot{grid-template-columns:45px 1fr}.detail__foot small{display:none}}
.memories__decor{z-index:0}.shelf{z-index:1}
</style>
