<script setup>
import BaseIcon from './BaseIcon.vue'
import { getCoupleTaste } from '@/services/aiReadyMock.js'
defineProps({ open: { type: Boolean, default: false } })
const emit = defineEmits(['close', 'recommend'])
const taste = getCoupleTaste()
const categories = [{label:'맛집 탐방',value:90},{label:'카페 데이트',value:81},{label:'감성 여행',value:67}]
const keywords = ['분위기맛집','사진맛집','조용한데이트','파스타','창가좌석','재방문의사']
</script>

<template>
  <div v-if="open" class="overlay" @click.self="emit('close')">
    <section class="popup" role="dialog" aria-modal="true" aria-labelledby="taste-title" data-testid="taste-modal">
      <button class="close" aria-label="우리 취향 닫기" @click="emit('close')"><BaseIcon name="close" :size="18"/></button>
      <div class="ribbon">♥ 우리 취향 보기</div>
      <h2 id="taste-title">두 사람이 함께 만든 취향을 분석했어요</h2>
      <p class="subtitle">저장된 방문·평점·리뷰 데이터를 기준으로 정리한 결과예요.</p>
      <section class="match">
        <span class="match__heart">💞</span><div><h3>우리 커플의 취향 일치도</h3><strong>{{taste.compatibility}}%</strong></div>
        <div class="match__track"><span :style="{width:`${taste.compatibility}%`}"></span></div><p>서로 다른 취향도 데이트 선택의 폭을 넓혀줘요.</p>
        <div class="match__count"><small>함께 기록한 장소</small><strong>{{taste.reviewedCount}}곳</strong></div>
      </section>
      <h3 class="section-title">♥ 카테고리별 선호</h3>
      <div class="analysis-grid">
        <section class="paper-card"><h4>자주 선택한 데이트</h4><ul class="bars"><li v-for="item in categories" :key="item.label"><span>{{item.label}}</span><i><b :style="{width:`${item.value}%`}"></b></i><strong>{{item.value}}%</strong></li></ul></section>
        <section class="paper-card"><h4>공통 취향 키워드</h4><ul class="chips"><li v-for="keyword in keywords" :key="keyword">#{{keyword}}</li></ul><p class="split"><strong>취향 갈림 · 맵기</strong><span>도현→순함 · 지민→매움 · 2명 판정</span></p></section>
      </div>
      <section class="lovey"><span>✨</span><div><h3>러비의 한 줄 분석</h3><p>두 분은 조용한 분위기에서 맛있는 음식과 사진을 함께 즐기는 데이트를 가장 좋아해요.</p><small>다음 추천에서는 분위기 좋은 파스타·브런치 장소를 먼저 보여드릴게요. ♥</small></div></section>
      <button class="action" @click="emit('recommend')">우리 취향에 맞는 장소 보기</button>
    </section>
  </div>
</template>

<style scoped>
.overlay{position:fixed;inset:0;z-index:var(--lm-z-modal);display:grid;place-items:center;padding:20px;background:rgba(82,58,52,.4);backdrop-filter:blur(4px)}.popup{position:relative;width:min(1040px,96vw);max-height:94vh;overflow:auto;padding:72px 68px 42px;background:linear-gradient(rgba(255,249,241,.94),rgba(255,249,241,.94)),url('@/assets/decorations/paper-texture.jpg') center/cover;border:7px solid #f5c6c2;border-radius:28px;box-shadow:0 24px 70px rgba(84,61,56,.32);color:#543d38}.popup:before{content:'';position:absolute;inset:14px;border:1px solid #f2d5cc;border-radius:18px;pointer-events:none}.close{position:absolute;z-index:2;right:25px;top:22px;width:40px;height:40px;display:grid;place-items:center;border-radius:50%;background:#f17a8e;color:#fff}.ribbon{position:absolute;top:28px;left:50%;transform:translateX(-50%);padding:11px 34px;background:#facfd0;color:#c94f63;font-weight:800;clip-path:polygon(7% 0,93% 0,100% 50%,93% 100%,7% 100%,0 50%)}h2{text-align:center;margin:31px 0 8px;font-size:31px}.subtitle{text-align:center;color:#876d65}.match{display:grid;grid-template-columns:55px 180px 1fr 140px;align-items:center;gap:14px;margin-top:26px;padding:18px 28px;background:#fff1ed;border:1px solid #f4d1ca;border-radius:16px}.match__heart{font-size:30px}.match h3{font-size:15px}.match strong{display:block;color:#f05f7b;font-size:30px}.match__track{height:11px;background:#efdcd8;border-radius:99px;overflow:hidden}.match__track span{display:block;height:100%;background:linear-gradient(90deg,#f792a4,#ef5f7b)}.match>p{grid-column:3;font-size:12px}.match__count{grid-column:4;grid-row:1/3;text-align:center}.match__count small{display:block;color:#9b7770}.match__count strong{font-size:20px}.section-title{margin:22px 0 10px;font-size:18px}.analysis-grid{display:grid;grid-template-columns:1fr 1fr;gap:18px}.paper-card{padding:20px 22px;border:1px solid #ead7ca;border-radius:14px;background:rgba(255,255,255,.62)}.paper-card h4{margin-bottom:15px}.bars{display:flex;flex-direction:column;gap:14px}.bars li{display:grid;grid-template-columns:90px 1fr 36px;align-items:center;gap:9px;font-size:12px}.bars i{height:8px;background:#f0dcd8;border-radius:99px;overflow:hidden}.bars b{display:block;height:100%;background:#f47d94}.bars strong{color:#e95d76}.chips{display:flex;flex-wrap:wrap;gap:9px}.chips li{padding:8px 13px;border:1px solid #f4cbd0;border-radius:99px;background:#fff5f3;color:#b65e6d;font-size:11px}.split{display:flex;flex-direction:column;gap:5px;margin-top:14px;padding:10px;border-radius:10px;background:#fff0f1;font-size:11px}.split strong{color:#d9546e}.lovey{display:flex;gap:18px;align-items:center;margin-top:18px;padding:16px 25px;border:1px dashed #ef9aaa;border-radius:14px;background:#fff4f1}.lovey>span{font-size:26px}.lovey h3{font-size:15px}.lovey p{margin:6px 0;font-size:13px}.lovey small{color:#e25b74}.action{display:block;width:520px;max-width:100%;margin:19px auto 0;padding:14px;border-radius:999px;background:linear-gradient(90deg,#f78398,#ed5f7b);color:#fff;font-weight:800}@media(max-width:720px){.popup{padding:68px 20px 28px}.match{grid-template-columns:45px 1fr}.match__track,.match>p,.match__count{grid-column:1/3;grid-row:auto}.analysis-grid{grid-template-columns:1fr}h2{font-size:24px}}
.popup{background:linear-gradient(rgba(255,249,241,.87),rgba(255,249,241,.87)),url('../../frontend-assets/decorations/love_maptually_paper_texture.png') center/560px}
</style>
