<script setup>
import { computed, ref, watch } from 'vue'
import BaseIcon from './BaseIcon.vue'
import { getCoupleTaste } from '@/services/aiReadyMock.js'
import { fetchTags } from '@/services/tagApi.js'
import { isLocalMode } from '@/services/config.js'
import { fetchGroupPreferences } from '@/services/preferenceApi.js'
import { fetchPlaces } from '@/services/placeApi.js'
import { COUPLE } from '@/utils/users.js'
const props = defineProps({
  open: { type: Boolean, default: false },
  groupId: { type: [Number, String], default: null },
})
const emit = defineEmits(['close', 'recommend'])
const taste = getCoupleTaste()
const categories = [{label:'맛집 탐방',value:90},{label:'카페 데이트',value:81},{label:'감성 여행',value:67}]
const fallbackKeywords = ['분위기맛집','사진맛집','조용한데이트','파스타','창가좌석','재방문의사']
const tags = ref([])
const tagLoading = ref(false)
const keywords = computed(() => tags.value.length ? tags.value.map((tag) => tag.name) : fallbackKeywords)

const localMode = isLocalMode()

/** 로컬 모드에는 백엔드가 없어 미리 정해둔 취향 표를 보여줍니다. */
function localPreferences() {
  const mine = COUPLE.him
  const partner = COUPLE.her
  const pair = (tagId, tagName, label, sideLabel, sides) => ({
    tagId,
    tagName,
    label,
    sideLabel,
    judgedMemberCount: sides.filter(Boolean).length,
    members: [
      { userId: mine.userId, nickname: mine.userName, sideLabel: sides[0] },
      { userId: partner.userId, nickname: partner.userName, sideLabel: sides[1] },
    ],
  })
  return [
    pair('local-quiet', '조용함', 'ALL_SAME', '조용함', ['조용함', '조용함']),
    pair('local-wait', '웨이팅', 'ALL_SAME', '짧음', ['짧음', '짧음']),
    pair('local-view', '야경', 'ONE_SIDED', '좋음', ['좋음', null]),
    pair('local-spicy', '맵기', 'SPLIT', null, ['순함', '매움']),
  ]
}

const preferences = ref(localMode ? localPreferences() : [])
const placeCount = ref(0)

/** 판정이 난 태그 중 두 사람 방향이 같은 비율입니다. 화면의 일치도가 이 값입니다. */
const compatibility = computed(() => {
  const judged = preferences.value.filter((item) => item.judgedMemberCount >= 2)
  if (!judged.length) return null
  const same = judged.filter((item) => item.label === 'ALL_SAME').length
  return Math.round((same / judged.length) * 100)
})
const judgedCount = computed(
  () => preferences.value.filter((item) => item.judgedMemberCount >= 2).length,
)
const prefLoading = ref(false)
const prefError = ref('')
const loadedGroupId = ref(null)
// 취향이 갈린 태그를 맨 위로 올립니다. 이 화면에서 가장 먼저 봐야 하는 값입니다.
const LABEL_ORDER = { SPLIT: 0, ONE_SIDED: 1, ALL_SAME: 2 }
const LABEL_TEXT = { SPLIT: '취향 갈림', ONE_SIDED: '한 사람만 판정', ALL_SAME: '취향 일치' }
const sortedPreferences = computed(() =>
  [...preferences.value].sort((a, b) => LABEL_ORDER[a.label] - LABEL_ORDER[b.label]),
)
const splitCount = computed(() => preferences.value.filter((item) => item.label === 'SPLIT').length)

async function loadPreferences() {
  if (localMode || props.groupId == null) return
  if (prefLoading.value || loadedGroupId.value === props.groupId) return
  prefLoading.value = true
  prefError.value = ''
  try {
    preferences.value = await fetchGroupPreferences(props.groupId)
    loadedGroupId.value = props.groupId
    try {
      const places = await fetchPlaces()
      placeCount.value = places.length
    } catch {
      placeCount.value = 0
    }
  } catch (error) {
    prefError.value = error?.message || '우리 취향을 불러오지 못했습니다.'
  } finally {
    prefLoading.value = false
  }
}

watch(() => props.open, async (open) => {
  if (!open) return
  loadPreferences()
  if (tags.value.length || tagLoading.value) return
  tagLoading.value = true
  try {
    tags.value = await fetchTags()
  } catch {
    // /api/tags 는 아직 백엔드에 없습니다. 조용히 기본 키워드로 넘어갑니다.
  } finally {
    tagLoading.value = false
  }
})
watch(() => props.groupId, () => { if (props.open) loadPreferences() })
</script>

<template>
  <div v-if="open" class="overlay" @click.self="emit('close')">
    <section class="popup" role="dialog" aria-modal="true" aria-labelledby="taste-title" data-testid="taste-modal">
      <button class="close" aria-label="우리 취향 닫기" @click="emit('close')"><BaseIcon name="close" :size="18"/></button>
      <div class="ribbon">♥ 우리 취향 보기</div>
      <h2 id="taste-title">두 사람이 함께 만든 취향을 분석했어요</h2>
      <p class="subtitle">저장된 방문·평점·리뷰 데이터를 기준으로 정리한 결과예요.</p>
      <section class="match">
        <span class="match__heart">💞</span><div><h3>우리 커플의 취향 일치도</h3><strong>{{compatibility === null ? '판정 전' : compatibility + '%'}}</strong></div>
        <div class="match__track"><span :style="{width:`${compatibility ?? 0}%`}"></span></div><p>서로 다른 취향도 데이트 선택의 폭을 넓혀줘요.</p>
        <div class="match__count"><small>우리 지도에 담은 곳</small><strong>{{placeCount}}곳</strong></div>
      </section>
      <h3 class="section-title">♥ 태그별 취향</h3>
      <p v-if="prefLoading" class="pref-state" role="status">우리 취향을 불러오는 중이에요…</p>
      <p v-else-if="prefError" class="pref-error" role="status">{{ prefError }}</p>
      <template v-else-if="sortedPreferences.length">
        <p class="pref-lead">
          지금까지 쌓인 리뷰에서 태그 {{ sortedPreferences.length }}개를 읽었고,
          그중 {{ splitCount }}개는 두 사람 취향이 갈렸습니다.
        </p>
        <ul class="prefs" data-testid="preference-list">
          <li
            v-for="preference in sortedPreferences"
            :key="preference.tagId"
            class="pref"
            :class="`pref--${preference.label.toLowerCase()}`"
            :data-testid="`preference-${preference.tagId}`"
            :data-label="preference.label"
          >
            <div class="pref__head">
              <strong>{{ preference.tagName }}</strong>
              <span class="pref__badge">{{ LABEL_TEXT[preference.label] }}</span>
              <small>{{ preference.judgedMemberCount }}명 판정</small>
            </div>
            <p v-if="preference.sideLabel" class="pref__side">함께 기울어진 쪽 {{ preference.sideLabel }}</p>
            <ul class="pref__members">
              <li v-for="member in preference.members" :key="member.userId">
                <span>{{ member.nickname }}</span>
                <b>{{ member.sideLabel || '아직 판정 없음' }}</b>
              </li>
            </ul>
          </li>
        </ul>
      </template>
      <p v-else class="pref-state" role="status">아직 판정된 취향 태그가 없습니다.</p>

      <h3 class="section-title">♥ 카테고리별 선호</h3>
      <div class="analysis-grid">
        <section class="paper-card"><h4>자주 선택한 데이트</h4><ul class="bars"><li v-for="item in categories" :key="item.label"><span>{{item.label}}</span><i><b :style="{width:`${item.value}%`}"></b></i><strong>{{item.value}}%</strong></li></ul></section>
        <section class="paper-card"><h4>공통 취향 키워드</h4><p v-if="tagLoading" class="tag-state" role="status">태그를 불러오는 중이에요…</p><ul v-else class="chips"><li v-for="keyword in keywords" :key="keyword">#{{keyword}}</li></ul></section>
      </div>
      <section class="lovey"><span>✨</span><div><h3>러비의 한 줄 분석</h3><p>두 분은 조용한 분위기에서 맛있는 음식과 사진을 함께 즐기는 데이트를 가장 좋아해요.</p><small>다음 추천에서는 분위기 좋은 파스타·브런치 장소를 먼저 보여드릴게요. ♥</small></div></section>
      <button class="action" @click="emit('recommend')">우리 취향에 맞는 장소 보기</button>
    </section>
  </div>
</template>

<style scoped>
.overlay{position:fixed;inset:0;z-index:var(--lm-z-modal);display:grid;place-items:center;padding:20px;background:rgba(82,58,52,.4);backdrop-filter:blur(4px)}.popup{position:relative;width:min(1040px,96vw);max-height:94vh;overflow:auto;padding:72px 68px 42px;background:linear-gradient(rgba(255,249,241,.94),rgba(255,249,241,.94)),url('@/assets/decorations/paper-texture.jpg') center/cover;border:7px solid #f5c6c2;border-radius:28px;box-shadow:0 24px 70px rgba(84,61,56,.32);color:#543d38}.popup:before{content:'';position:absolute;inset:14px;border:1px solid #f2d5cc;border-radius:18px;pointer-events:none}.close{position:absolute;z-index:2;right:25px;top:22px;width:40px;height:40px;display:grid;place-items:center;border-radius:50%;background:#f17a8e;color:#fff}.ribbon{position:absolute;top:28px;left:50%;transform:translateX(-50%);padding:11px 34px;background:#facfd0;color:#c94f63;font-weight:800;clip-path:polygon(7% 0,93% 0,100% 50%,93% 100%,7% 100%,0 50%)}h2{text-align:center;margin:31px 0 8px;font-size:31px}.subtitle{text-align:center;color:#876d65}.match{display:grid;grid-template-columns:55px 180px 1fr 140px;align-items:center;gap:14px;margin-top:26px;padding:18px 28px;background:#fff1ed;border:1px solid #f4d1ca;border-radius:16px}.match__heart{font-size:30px}.match h3{font-size:15px}.match strong{display:block;color:#f05f7b;font-size:30px}.match__track{height:11px;background:#efdcd8;border-radius:99px;overflow:hidden}.match__track span{display:block;height:100%;background:linear-gradient(90deg,#f792a4,#ef5f7b)}.match>p{grid-column:3;font-size:12px}.match__count{grid-column:4;grid-row:1/3;text-align:center}.match__count small{display:block;color:#9b7770}.match__count strong{font-size:20px}.section-title{margin:22px 0 10px;font-size:18px}.analysis-grid{display:grid;grid-template-columns:1fr 1fr;gap:18px}.paper-card{padding:20px 22px;border:1px solid #ead7ca;border-radius:14px;background:rgba(255,255,255,.62)}.paper-card h4{margin-bottom:15px}.bars{display:flex;flex-direction:column;gap:14px}.bars li{display:grid;grid-template-columns:90px 1fr 36px;align-items:center;gap:9px;font-size:12px}.bars i{height:8px;background:#f0dcd8;border-radius:99px;overflow:hidden}.bars b{display:block;height:100%;background:#f47d94}.bars strong{color:#e95d76}.chips{display:flex;flex-wrap:wrap;gap:9px}.chips li{padding:8px 13px;border:1px solid #f4cbd0;border-radius:99px;background:#fff5f3;color:#b65e6d;font-size:11px}.split{display:flex;flex-direction:column;gap:5px;margin-top:14px;padding:10px;border-radius:10px;background:#fff0f1;font-size:11px}.split strong{color:#d9546e}.lovey{display:flex;gap:18px;align-items:center;margin-top:18px;padding:16px 25px;border:1px dashed #ef9aaa;border-radius:14px;background:#fff4f1}.lovey>span{font-size:26px}.lovey h3{font-size:15px}.lovey p{margin:6px 0;font-size:13px}.lovey small{color:#e25b74}.action{display:block;width:520px;max-width:100%;margin:19px auto 0;padding:14px;border-radius:999px;background:linear-gradient(90deg,#f78398,#ed5f7b);color:#fff;font-weight:800}@media(max-width:720px){.popup{padding:68px 20px 28px}.match{grid-template-columns:45px 1fr}.match__track,.match>p,.match__count{grid-column:1/3;grid-row:auto}.analysis-grid{grid-template-columns:1fr}h2{font-size:24px}}
.popup{background:linear-gradient(rgba(255,249,241,.87),rgba(255,249,241,.87)),url('../../frontend-assets/decorations/love_maptually_paper_texture.png') center/560px}
.tag-state,.tag-error{font-size:11px;color:var(--lm-ink-soft)}.tag-error{margin-top:8px;color:var(--lm-danger)}
.pref-state{padding:16px;border:1px dashed #ecd0cb;border-radius:12px;background:rgba(255,255,255,.55);color:#8a6f68;font-size:13px}
.pref-error{padding:16px;border:1px solid #e8bdb2;border-radius:12px;background:#fbe9e5;color:var(--lm-danger);font-size:13px}
.pref-lead{margin-bottom:12px;color:#876d65;font-size:13px}
.prefs{display:grid;grid-template-columns:repeat(auto-fill,minmax(230px,1fr));gap:12px}
.pref{padding:14px 16px;border:1px solid #ead7ca;border-radius:14px;background:rgba(255,255,255,.62)}
.pref__head{display:flex;align-items:center;gap:8px;flex-wrap:wrap}
.pref__head strong{font-size:16px}
.pref__badge{padding:3px 10px;border-radius:99px;background:#efe3dd;color:#7d6058;font-size:10px;font-weight:800}
.pref__head small{margin-left:auto;color:#9b8178;font-size:10px}
.pref__side{margin-top:7px;color:#876d65;font-size:11px}
.pref__members{display:flex;flex-direction:column;gap:6px;margin-top:11px}
.pref__members li{display:flex;justify-content:space-between;gap:10px;padding:6px 10px;border-radius:8px;background:#fbf3ef;font-size:12px}
.pref__members b{color:#a5776d}
.pref--split{border-color:#ef6680;border-width:2px;background:#fff0f1;box-shadow:0 6px 16px rgba(233,93,118,.18)}
.pref--split .pref__badge{background:#ef6680;color:#fff}
.pref--split .pref__members li{background:#ffe4e7}
.pref--split .pref__members b{color:#d9546e;font-weight:800}
.pref--one_sided{border-style:dashed}
</style>
