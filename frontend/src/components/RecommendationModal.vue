<script setup>
import { computed, ref } from 'vue'
import BaseIcon from './BaseIcon.vue'
import { isLocalMode } from '@/services/config.js'
import { requestRecommendation } from '@/services/recommendationApi.js'
import { recommendPlaces } from '@/services/aiReadyMock.js'

const props = defineProps({
  open: { type: Boolean, default: false },
  groupId: { type: [Number, String], default: null },
})
const emit = defineEmits(['close'])

const localMode = isLocalMode()
const query = ref('오늘 인사동 갈 건데 조용한 카페 3곳 추천해줘')
const loading = ref(false)
const result = ref(null)
const errorMessage = ref('')
// 질문에서 동네를 못 읽었을 때(422 REGION_NOT_FOUND)는 오류가 아니라 되묻기로 다룹니다.
const regionPrompt = ref('')
const skeletons = [1, 2, 3]

const places = computed(() => result.value?.recommendations ?? [])
/** priceBand 는 1~4 단계 숫자로 옵니다. 그대로 두면 '카페 2' 처럼 읽히지 않습니다. */
function priceLabel(band) {
  const level = Number(band)
  return Number.isFinite(level) && level > 0 ? '₩'.repeat(Math.min(4, level)) : ''
}

/** 로컬 모드는 백엔드 없이 정해진 후보 안에서만 답합니다. */
async function localRecommendation(text) {
  const mock = await recommendPlaces(text)
  if (mock.status !== 'COMPLETED') {
    regionPrompt.value = mock.message
    return null
  }
  return {
    candidateCount: mock.places.length,
    cfWeight: null,
    recommendations: mock.places.map((place, index) => ({
      recommendationId: place.placeId ?? index,
      placeId: place.placeId ?? index,
      name: place.name,
      category: place.category,
      priceBand: null,
      matchedTags: place.matchedTags ?? [],
      reason: place.reason ?? '',
      basis: place.basis ?? null,
    })),
  }
}

async function submit() {
  if (loading.value) return
  errorMessage.value = ''
  regionPrompt.value = ''
  loading.value = true
  try {
    result.value = localMode
      ? await localRecommendation(query.value)
      : await requestRecommendation(props.groupId, query.value)
  } catch (error) {
    result.value = null
    if (error?.code === 'REGION_NOT_FOUND') {
      regionPrompt.value = '어느 동네에서 찾아드릴까요? 예를 들어 "인사동", "성수동"처럼 알려주시면 바로 찾아보겠습니다.'
      return
    }
    errorMessage.value = error?.message || '추천을 받지 못했습니다.'
  } finally {
    loading.value = false
  }
}
</script>

<template>
 <div v-if="open" class="overlay" @click.self="emit('close')"><section class="popup" role="dialog" aria-modal="true" aria-labelledby="recommend-title" data-testid="recommendation-modal">
  <button class="close" aria-label="추천 닫기" @click="emit('close')"><BaseIcon name="close" :size="18"/></button><div class="ribbon">♡ 러비의 추천!</div>
  <h2 id="recommend-title">취향이 비슷한 다른 커플이<br><em>선호하는 장소를 추천해요!</em></h2>
  <p class="subtitle">가고 싶은 동네와 분위기를 문장으로 적어주시면 러비가 찾아드립니다 ♡</p>

  <form class="query" @submit.prevent="submit">
    <input v-model="query" aria-label="추천 질문" data-testid="recommendation-query" placeholder="예) 오늘 인사동 갈 건데 조용한 카페 3곳 추천해줘">
    <button :disabled="loading" data-testid="recommendation-submit">{{loading?'찾는 중…':'추천받기'}}</button>
  </form>

  <p v-if="regionPrompt" class="reask" role="status" data-testid="recommendation-reask">{{ regionPrompt }}</p>
  <p v-else-if="errorMessage" class="notice notice--error" role="alert" data-testid="recommendation-error">{{ errorMessage }}</p>

  <ol v-if="loading" class="cards" data-testid="recommendation-skeleton">
    <li v-for="key in skeletons" :key="key" class="card card--skeleton"><span class="sk sk--img"></span><div><span class="sk sk--line"></span><span class="sk sk--line sk--short"></span><span class="sk sk--line"></span></div></li>
  </ol>

  <template v-else-if="places.length">
   <p class="meta-line" data-testid="recommendation-meta">
     후보 {{ result.candidateCount }}곳에서 골랐고, 협업 필터 가중치는 {{ result.cfWeight ?? '-' }}입니다.
   </p>
   <h3>♥ 러비가 고른 {{ places.length }}곳</h3>
   <ol class="cards">
    <li v-for="(place,index) in places" :key="place.recommendationId" class="card" :data-testid="`recommendation-${place.placeId}`">
      <span class="rank">{{index+1}}</span>
      <div>
        <h4>{{place.name}}</h4>
        <p class="meta">{{place.category}}<template v-if="priceLabel(place.priceBand)"> / {{priceLabel(place.priceBand)}}</template></p>
        <p class="reason">{{place.reason}}</p>
        <ul><li v-for="tag in place.matchedTags" :key="tag">#{{tag}}</li></ul>
        <small>{{place.basis==='OWN'?'우리 기록에서':'취향이 비슷한 커플의 기록에서'}}</small>
      </div>
    </li>
   </ol>
  </template>

  <button class="confirm" @click="emit('close')">확인했어요!</button>
 </section></div>
</template>

<style scoped>
.overlay{position:fixed;inset:0;z-index:var(--lm-z-modal);display:grid;place-items:center;padding:20px;background:rgba(82,58,52,.42);backdrop-filter:blur(4px)}.popup{position:relative;width:min(820px,96vw);max-height:95vh;overflow:auto;padding:62px 58px 34px;background:linear-gradient(rgba(255,249,241,.95),rgba(255,249,241,.95)),url('@/assets/decorations/paper-texture.jpg') center/cover;border:7px solid #f4bbb9;border-radius:28px;box-shadow:0 24px 70px rgba(84,61,56,.32);color:#4e342e}.popup:before{content:'';position:absolute;inset:13px;border:1px solid #f4d1ca;border-radius:18px;pointer-events:none}.close{position:absolute;z-index:2;right:22px;top:18px;width:40px;height:40px;display:grid;place-items:center;border-radius:50%;background:#f17a8e;color:#fff}.ribbon{position:absolute;top:24px;left:50%;transform:translateX(-50%);padding:9px 28px;background:#facfd0;color:#c34558;font-weight:800;clip-path:polygon(6% 0,94% 0,100% 50%,94% 100%,6% 100%,0 50%)}h2{text-align:center;margin:28px 0 7px;font-size:25px;line-height:1.45}h2 em{font-style:normal;font-size:31px;color:#ed667d}.subtitle{text-align:center}.query{display:flex;gap:8px;margin:17px auto;max-width:620px}.query input{flex:1;padding:10px 15px;border:1px solid #ead4cb;border-radius:99px}.query button,.confirm{padding:11px 22px;border-radius:99px;background:#ed667d;color:#fff;font-weight:800}.query button:disabled{opacity:.55}.notice{padding:13px;text-align:center;background:#fff0ed;border-radius:12px}.notice--error{background:#fbe9e5;color:var(--lm-danger)}.popup>h3{margin:17px 0 10px;font-size:16px}
.reask{margin:4px auto 0;max-width:620px;padding:15px 18px;border:1px dashed #ef9aaa;border-radius:14px;background:#fff4f1;color:#b6485f;font-size:14px;line-height:1.6;text-align:center}
.meta-line{margin-top:6px;text-align:center;color:#9b8178;font-size:11px}
.cards{display:grid;grid-template-columns:repeat(3,1fr);gap:13px}.card{position:relative;overflow:hidden;background:#fffaf4;border:1px solid #e8d2c7;border-radius:14px;box-shadow:0 5px 12px #5a372e1f}.rank{position:absolute;z-index:1;top:0;left:12px;display:grid;place-items:center;width:34px;height:45px;background:#ef6680;color:#fff;font-size:20px;font-weight:800;clip-path:polygon(0 0,100% 0,100% 100%,50% 82%,0 100%)}.card>div{padding:26px 14px 14px}.card h4{font-size:16px;padding-left:38px;min-height:22px}.card p{font-size:11.5px;line-height:1.6}.meta{margin:4px 0 10px;color:#80645c}.reason{color:#5c433c}.card div ul{display:flex;flex-wrap:wrap;gap:5px;margin:10px 0 8px}.card div ul li{padding:3px 8px;border-radius:99px;background:#fff0f1;font-size:9.5px;color:#d95f74}.card div>small{color:#9b8178;font-size:10px}
.card--skeleton{min-height:190px}.sk{display:block;border-radius:8px;background:linear-gradient(90deg,#f0e2dc,#faf1ec,#f0e2dc);background-size:200% 100%;animation:sk 1.1s ease-in-out infinite}.sk--img{height:70px;border-radius:0}.sk--line{height:12px;margin:10px 14px}.sk--short{width:55%}@keyframes sk{0%{background-position:0 0}100%{background-position:-200% 0}}
.confirm{display:block;width:330px;max-width:100%;margin:22px auto 0}@media(max-width:700px){.popup{padding:58px 18px 28px}h2 em{font-size:24px}.query{flex-direction:column}.cards{grid-template-columns:1fr}}
.popup{background:linear-gradient(rgba(255,249,241,.87),rgba(255,249,241,.87)),url('../../frontend-assets/decorations/love_maptually_paper_texture.png') center/560px}
</style>
