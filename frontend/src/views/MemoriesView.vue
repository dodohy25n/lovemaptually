<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import BaseIcon from '@/components/BaseIcon.vue'
import CoupleReportCard from '@/components/CoupleReportCard.vue'
import EmptyState from '@/components/EmptyState.vue'
import { usePlacesStore } from '@/stores/places.js'
import { isLocalMode } from '@/services/config.js'
import { fetchMyGroups } from '@/services/groupApi.js'
import { fetchGroupReports, generateReport, pollReport, subscribePremium } from '@/services/reportApi.js'
import { setActiveGroupId } from '@/services/placeApi.js'
import { applyMemberNames } from '@/utils/users.js'
import { readJson, STORAGE_KEYS } from '@/services/storageService.js'
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

/* ── 월간 리포트 ───────────────────────────────────────────── */
const localMode = isLocalMode()
const reportGroupId = ref(null)
const reportMonth = ref('2026-08')
const reportPlan = ref('')
const reportList = ref([])
const report = ref(null)
const reportLoading = ref(false)
const reportError = ref('')
const planRequired = ref(false)
const upgrading = ref(false)

const reportMembers = ref([])

const hasReportContent = computed(() => report.value?.status === 'COMPLETED' && report.value?.content)

/**
 * 리포트 본문의 구성원은 A와 B로 익명화되어 옵니다 (LLM에 닉네임을 보내지 않기 때문입니다).
 * 서버가 A와 B를 매긴 순서가 그룹 구성원의 합류 순서와 같아, 그 순서로 닉네임을 되붙입니다.
 * 구성원이 한 명뿐이거나 매핑할 수 없으면 원래 값을 그대로 씁니다.
 */
function memberName(slot, fallback) {
  const member = reportMembers.value[slot]
  return member?.nickname || fallback
}

const splitTagRows = computed(() =>
  (report.value?.content?.splitTags ?? []).map((item) => ({
    tag: item.tag,
    memberA: memberName(0, item.memberA),
    memberB: memberName(1, item.memberB),
    sideA: item.memberA,
    sideB: item.memberB,
  })),
)

async function loadReportContext() {
  if (localMode) return
  try {
    const groups = await fetchMyGroups()
    const primary = groups.find((group) => group.type === 'COUPLE') ?? groups[0]
    reportGroupId.value = primary?.groupId ?? null
    reportMembers.value = primary?.members ?? []
    applyMemberNames(reportMembers.value, readJson(STORAGE_KEYS.authUser)?.userId)
    if (reportGroupId.value == null) return
    // 지도와 같은 그룹 마커를 써야 다이어리에 그 달 장소가 놓입니다.
    setActiveGroupId(reportGroupId.value)
    await store.load()
    const listed = await fetchGroupReports(reportGroupId.value)
    reportPlan.value = listed.plan
    reportList.value = listed.reports
  } catch (error) {
    if (error?.code !== 'auth_required') reportError.value = error?.message || '리포트 정보를 불러오지 못했습니다.'
  }
}

/** 이미 만들어 둔 달이면 바로 열고, 없으면 생성을 요청한 뒤 완성될 때까지 기다립니다. */
async function openMonthlyReport() {
  if (reportLoading.value || localMode) return
  reportError.value = ''
  planRequired.value = false
  reportLoading.value = true
  try {
    const existing = reportList.value.find((item) => item.reportMonth === reportMonth.value)
    report.value = existing
      ? await pollReport(existing.reportId)
      : await generateReport(reportGroupId.value, reportMonth.value)
    if (!reportList.value.some((item) => item.reportId === report.value.reportId)) {
      reportList.value = [...reportList.value, {
        reportId: report.value.reportId,
        reportMonth: report.value.reportMonth,
        status: report.value.status,
      }]
    }
  } catch (error) {
    report.value = null
    if (error?.code === 'plan_required') {
      planRequired.value = true
      return
    }
    if (error?.code === 'no_visits_in_month') {
      reportError.value = '그 달에는 함께 다녀온 기록이 없어 리포트를 만들 수 없습니다.'
      return
    }
    reportError.value = error?.message || '리포트를 열지 못했습니다.'
  } finally {
    reportLoading.value = false
  }
}

/** 402 잠금 화면의 '프리미엄으로 열기'. 구독을 올린 뒤 곧바로 다시 시도합니다. */
async function upgradeAndRetry() {
  if (upgrading.value) return
  upgrading.value = true
  reportError.value = ''
  try {
    const subscription = await subscribePremium(reportGroupId.value)
    reportPlan.value = subscription.plan
    planRequired.value = false
    await openMonthlyReport()
  } catch (error) {
    reportError.value = error?.message || '프리미엄으로 전환하지 못했습니다.'
  } finally {
    upgrading.value = false
  }
}

watch(isDetailOpen, (open) => { document.body.style.overflow = open ? 'hidden' : '' })
onMounted(() => {
  document.addEventListener('keydown', onKeydown)
  loadReportContext().finally(() => {
    if (!recentPlaces.value.length) store.load()
  })
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

    <section class="report" aria-labelledby="monthly-report-title" data-testid="monthly-report">
      <header class="report__head">
        <h2 id="monthly-report-title">월간 리포트</h2>
        <p>한 달 동안 함께 다녀온 기록을 러비가 한 장으로 정리해 드립니다.</p>
      </header>

      <p v-if="localMode" class="report__state" role="status">백엔드 연결이 필요합니다.</p>
      <form v-else class="report__form" @submit.prevent="openMonthlyReport">
        <label for="report-month">리포트를 볼 달</label>
        <input id="report-month" v-model="reportMonth" type="month" data-testid="report-month" />
        <button type="submit" :disabled="reportLoading" data-testid="report-generate">
          {{ reportLoading ? '쓰는 중…' : '리포트 열기' }}
        </button>
        <small v-if="reportPlan">현재 요금제 {{ reportPlan }}</small>
      </form>

      <div v-if="reportLoading" class="report__skeleton" data-testid="report-skeleton">
        <span class="sk sk--title"></span><span class="sk"></span><span class="sk"></span><span class="sk sk--short"></span>
      </div>

      <div v-else-if="planRequired" class="report__lock" data-testid="report-lock">
        <span aria-hidden="true">🔒</span>
        <div>
          <strong>월간 리포트는 프리미엄 기능입니다.</strong>
          <p>프리미엄으로 올리면 이 달 리포트를 바로 열어 드립니다.</p>
        </div>
        <button type="button" :disabled="upgrading" data-testid="report-upgrade" @click="upgradeAndRetry">
          {{ upgrading ? '전환 중…' : '프리미엄으로 열기' }}
        </button>
      </div>

      <p v-else-if="reportError" class="report__error" role="alert" data-testid="report-error">{{ reportError }}</p>

      <article v-else-if="hasReportContent" class="report__body" data-testid="report-content">
        <h3>{{ report.content.title }}</h3>
        <p class="report__summary">{{ report.content.summary }}</p>

        <section v-if="report.content.highlights.length">
          <h4>이 달의 장면</h4>
          <ul>
            <li v-for="item in report.content.highlights" :key="`h-${item.placeId}-${item.name}`">
              <strong>{{ item.name }}</strong><span>{{ item.why }}</span>
            </li>
          </ul>
        </section>

        <section v-if="report.content.tasteShift.length">
          <h4>취향의 변화</h4>
          <ul>
            <li v-for="(item, index) in report.content.tasteShift" :key="`t-${index}`">
              <strong>{{ item.tag }} → {{ item.direction }}</strong><span>{{ item.evidence }}</span>
            </li>
          </ul>
        </section>

        <section v-if="splitTagRows.length">
          <h4>취향이 갈린 지점</h4>
          <ul class="report__split" data-testid="report-split-tags">
            <li v-for="(item, index) in splitTagRows" :key="`s-${index}`">
              <strong>{{ item.tag }}</strong>
              <span>{{ item.memberA }} → {{ item.sideA }} / {{ item.memberB }} → {{ item.sideB }}</span>
            </li>
          </ul>
        </section>

        <section v-if="report.content.nextMonth.length">
          <h4>다음 달에 가볼 곳</h4>
          <ul>
            <li v-for="item in report.content.nextMonth" :key="`n-${item.placeId}-${item.name}`">
              <strong>{{ item.name }}</strong><span>{{ item.reason }}</span>
            </li>
          </ul>
        </section>

        <p class="report__closing">{{ report.content.closingLine }}</p>
      </article>
    </section>

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
@media(max-width:700px){.memories__head{align-items:flex-start;flex-direction:column;padding-inline:5px}.memories__summary{justify-content:flex-start}.shelf{width:100%}.shelf__books{inset:19.5% 10% 8%;column-gap:12%}.diary{width:82%;height:90%}.detail{padding:50px 15px 22px}.detail__stage{width:calc(100% - 48px)}.detail__arrow{width:34px;height:34px}.detail__arrow--left{left:-40px}.detail__arrow--right{right:-40px}.detail__foot{grid-template-columns:45px 1fr}.detail__foot small{display:none}}
.memories__decor{z-index:0}.shelf{z-index:1}
.report{position:relative;z-index:1;width:min(900px,100%);padding:24px 28px;border:1px solid rgba(232,198,194,.8);border-radius:20px;background:rgba(255,255,255,.66);box-shadow:0 8px 24px rgba(118,76,57,.07)}
.report__head h2{font-size:22px;color:var(--lm-pink)}
.report__head p{margin-top:5px;color:var(--lm-ink-soft);font-size:13px}
.report__form{display:flex;align-items:center;flex-wrap:wrap;gap:10px;margin-top:16px}
.report__form label{font-size:13px;color:var(--lm-ink-soft)}
.report__form input{padding:8px 13px;border:1px solid #ead4cb;border-radius:99px;background:#fff;font:inherit}
.report__form button{padding:9px 20px;border-radius:99px;background:#ed667d;color:#fff;font-weight:800;font-size:13px}
.report__form button:disabled{opacity:.55}
.report__form small{margin-left:auto;color:#9b8178;font-size:11px}
.report__state{margin-top:16px;padding:16px;border:1px dashed #ecd0cb;border-radius:12px;color:#8a6f68;font-size:13px}
.report__error{margin-top:16px;padding:14px 16px;border:1px solid #e8bdb2;border-radius:12px;background:#fbe9e5;color:var(--lm-danger);font-size:13px}
.report__skeleton{display:flex;flex-direction:column;gap:11px;margin-top:18px}
.report__skeleton .sk{height:14px;border-radius:7px;background:linear-gradient(90deg,#f0e2dc,#faf1ec,#f0e2dc);background-size:200% 100%;animation:reportsk 1.1s ease-in-out infinite}
.report__skeleton .sk--title{height:24px;width:45%}.report__skeleton .sk--short{width:60%}
@keyframes reportsk{0%{background-position:0 0}100%{background-position:-200% 0}}
.report__lock{display:flex;align-items:center;gap:16px;margin-top:18px;padding:18px 22px;border:1px dashed #ef9aaa;border-radius:14px;background:#fff4f1}
.report__lock>span{font-size:26px}.report__lock div{flex:1}
.report__lock strong{font-size:15px;color:#b6485f}.report__lock p{margin-top:5px;font-size:12px;color:#8a6f68}
.report__lock button{padding:11px 22px;border-radius:99px;background:#ed667d;color:#fff;font-weight:800;font-size:13px}
.report__lock button:disabled{opacity:.55}
.report__body{margin-top:20px;padding-top:18px;border-top:1px dashed #ecd0cb}
.report__body h3{font-size:20px;color:var(--lm-pink)}
.report__summary{margin-top:8px;color:var(--lm-ink);font-size:14px;line-height:1.75}
.report__body section{margin-top:18px}
.report__body h4{margin-bottom:9px;font-size:14px;color:#a5776d}
.report__body ul{display:flex;flex-direction:column;gap:8px}
.report__body li{display:flex;flex-direction:column;gap:3px;padding:11px 14px;border-radius:11px;background:#fbf3ef;font-size:13px}
.report__body li strong{color:#5c433c}.report__body li span{color:#8a6f68;font-size:12px;line-height:1.6}
.report__split li{background:#ffe4e7}.report__split li strong{color:#d9546e}
.report__closing{margin-top:20px;padding-top:14px;border-top:1px dashed #ecd0cb;text-align:center;color:#b6485f;font-size:14px}
@media(max-width:700px){.report{padding:20px 16px}.report__form small{margin-left:0;width:100%}.report__lock{flex-direction:column;align-items:flex-start}}
</style>
