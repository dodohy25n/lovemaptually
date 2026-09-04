<script setup>
import { COUPLE } from '@/utils/users.js'
import { computed } from 'vue'
import HeartRating from './HeartRating.vue'
import ReportRouteMap from './ReportRouteMap.vue'
import { reviewAverage } from '@/utils/heartGrade.js'
import heartBurst from '../../frontend-assets/decorations/crayon_heart_burst.png'
import pinkCamera from '../../frontend-assets/decorations/pink_camera_hearts_sketch.png'
import blueCamera from '../../frontend-assets/decorations/blue_camera_sketch.png'
import confetti from '../../frontend-assets/decorations/color_sparkle_confetti.png'
import loveStamp from '../../frontend-assets/decorations/love_stamp_red.png'
import thumbsUp from '../../frontend-assets/decorations/thumbs_up_good.png'
import pinkTape from '../../frontend-assets/decorations/love_maptually_pink_tape.png'
import lovey from '../../frontend-assets/mascots/rubia_raccoon_waving.png'

const props = defineProps({
  places: { type: Array, default: () => [] },
  month: { type: String, default: '' },
  page: { type: Number, default: 0 },
})

const regionCounts = computed(() => {
  const counts = new Map()
  props.places.forEach((place) => {
    const region = place.address?.split(' ').find((part) => /[구군시]$/.test(part)) ?? '지역 미정'
    counts.set(region, (counts.get(region) ?? 0) + 1)
  })
  return [...counts.entries()].sort((a, b) => b[1] - a[1])
})
const favoriteRegion = computed(() => regionCounts.value[0]?.[0] ?? '아직 분석 전')
const averageScore = computed(() => props.places.length
  ? props.places.reduce((sum, place) => sum + Number(place.coupleScore ?? 0), 0) / props.places.length
  : 0)
const routePoints = computed(() => {
  const valid = props.places.filter((place) => Number.isFinite(place.latitude) && Number.isFinite(place.longitude))
  if (!valid.length) return []
  const lats = valid.map((place) => place.latitude)
  const lngs = valid.map((place) => place.longitude)
  const minLat = Math.min(...lats); const maxLat = Math.max(...lats)
  const minLng = Math.min(...lngs); const maxLng = Math.max(...lngs)
  return valid.map((place) => ({
    place,
    x: 38 + ((place.longitude - minLng) / (maxLng - minLng || 1)) * 324,
    y: 198 - ((place.latitude - minLat) / (maxLat - minLat || 1)) * 152,
  }))
})
const summaries = computed(() => props.places.map((place) => {
  const reviews = place.reviews ?? []
  const positive = reviews.filter((review) => review.revisitIntent).length
  const excerpts = reviews.map((review) => review.content).filter(Boolean)
  return {
    place,
    text: excerpts.length
      ? excerpts.map((text) => text.replace(/[.!?].*$/, '').trim()).join(' · ')
      : '작성된 리뷰가 없어 요약을 준비하지 못했어요.',
    verdict: positive >= Math.ceil(reviews.length / 2) ? '추천' : '비추천',
    reason: positive >= Math.ceil(reviews.length / 2)
      ? `두 사람의 재방문 의사와 ${Number(place.coupleScore ?? 0).toFixed(1)}점의 평가를 반영했어요.`
      : `재방문 의사가 낮고 평균 점수가 ${Number(place.coupleScore ?? 0).toFixed(1)}점이에요.`,
  }
}))

const pageDecor = computed(() => [heartBurst, loveStamp, pinkCamera, blueCamera, thumbsUp][props.page] ?? heartBurst)
</script>

<template>
  <article class="report" :data-page="page" data-testid="couple-report-card">
    <span class="report__rings" aria-hidden="true"></span>
    <img class="report__tape" :src="pinkTape" alt="" aria-hidden="true" />
    <img class="report__decor" :src="pageDecor" alt="" aria-hidden="true" />
    <img v-if="page === 0" class="report__confetti" :src="confetti" alt="" aria-hidden="true" />
    <img v-if="page === 0" class="report__lovey" :src="lovey" alt="" aria-hidden="true" />
    <header class="report__mast"><b>LOVE MAPTUALLY REPORT</b><span>{{ month }}</span></header>

    <section v-if="page === 0" class="page page--cover">
      <p class="eyebrow">OUR DATE ARCHIVE</p>
      <h3>{{ month }}<br /><em>우리의 맛집 리포트</em></h3>
      <div class="cover-stats">
        <span><strong>{{ places.length }}</strong>곳의 추억</span>
        <span><strong>{{ averageScore.toFixed(1) }}</strong>커플 평균</span>
        <span><strong>{{ favoriteRegion }}</strong>최애 지역</span>
      </div>
      <p class="cover-copy">함께 걷고, 먹고, 이야기했던 기록을<br />한 권의 카드뉴스로 모았어요 ♡</p>
    </section>

    <section v-else-if="page === 1" class="page">
      <p class="eyebrow">DATE ROUTE</p><h3>우리가 함께 걸어온 맛집 동선</h3>
      <ReportRouteMap :places="places" />
      <ol class="route-list"><li v-for="(point,index) in routePoints" :key="point.place.id"><b>{{ index + 1 }}</b><span>{{ point.place.name }}</span></li></ol>
      <p class="insight">가장 자주 머문 곳은 <strong>{{ favoriteRegion }}</strong>이에요. 이 지역에서 주로 식사와 카페 데이트를 즐겼어요.</p>
    </section>

    <section v-else-if="page === 2" class="page">
      <p class="eyebrow">PLACE SCORE</p><h3>사진과 점수로 다시 보는 맛집</h3>
      <div class="place-grid">
        <article v-for="place in places" :key="place.id">
          <img :src="place.images?.[0] || '/favicon.svg'" :alt="`${place.name} 사진`" />
          <div><h4>{{ place.name }}</h4><p>{{ place.category }} · {{ place.address }}</p><HeartRating :score="place.coupleScore" :size="20" show-label /></div>
        </article>
      </div>
    </section>

    <section v-else-if="page === 3" class="page">
      <p class="eyebrow">AI REVIEW SUMMARY</p><h3>두 사람의 리뷰를 한눈에</h3>
      <div class="summary-list">
        <article v-for="item in summaries" :key="item.place.id"><h4>{{ item.place.name }}</h4><p>“{{ item.text }}”</p><ul><li v-for="tag in item.place.tags" :key="tag">#{{ tag }}</li></ul></article>
      </div>
    </section>

    <section v-else class="page">
      <p class="eyebrow">VISIT & AI VERDICT</p><h3>방문 기록과 다시 갈 이유</h3>
      <div class="visit-list">
        <article v-for="item in summaries" :key="item.place.id">
          <div class="visit-head"><h4>{{ item.place.name }}</h4><b :class="{ no:item.verdict==='비추천' }">{{ item.verdict }}</b></div>
          <p class="visit-date"><strong>1회 방문</strong><span>{{ item.place.visitedAt || '날짜 미정' }}</span></p>
          <p>{{ item.reason }}</p>
          <p class="score-pair">{{ COUPLE.him.userName }} 점수 {{ reviewAverage(item.place.reviews?.[0]).toFixed(1) }} / {{ COUPLE.her.userName }} 점수 {{ reviewAverage(item.place.reviews?.[1]).toFixed(1) }}</p>
        </article>
      </div>
    </section>
  </article>
</template>

<style scoped>
.report{position:relative;min-height:650px;padding:54px 42px 34px;overflow:hidden;border:1px solid #ead6d0;border-radius:8px 8px 24px 24px;background:#fff3f3;color:#4f403d;box-shadow:7px 8px 0 rgba(98,79,72,.12)}.report:after{content:'';position:absolute;inset:14px;border:1px solid rgba(207,174,168,.42);pointer-events:none}.report__rings{position:absolute;z-index:2;top:-9px;left:20px;right:20px;height:32px;background:radial-gradient(circle at 12px 19px,#6e6965 0 4px,#e8e5df 4.5px 7px,transparent 7.5px),linear-gradient(90deg,transparent 10px,#aaa4a0 10px 13px,transparent 13px) 0 0/31px 26px;background-repeat:repeat-x}.report__mast{position:relative;z-index:1;display:flex;justify-content:space-between;padding-bottom:9px;border-bottom:1px solid #9e8580;font-size:10px;letter-spacing:.12em}.report__mast span{color:#a77b7b}.page{position:relative;z-index:1}.eyebrow{margin-top:24px;color:#d66f83;font-size:10px;font-weight:800;letter-spacing:.18em}.page h3{margin:10px 0 20px;font-size:26px;line-height:1.35}.page h3 em{color:#e66d84;font-style:normal}.page--cover{text-align:center;padding-top:56px}.page--cover h3{font-size:40px}.cover-stats{display:grid;grid-template-columns:repeat(3,1fr);gap:9px;margin:48px 0 38px}.cover-stats span{padding:18px 7px;border:1px solid #e9c8c8;background:rgba(255,255,255,.56);font-size:11px}.cover-stats strong{display:block;margin-bottom:7px;color:#df7185;font-size:23px}.cover-copy{font-size:14px;line-height:1.9}.route-map{border:1px solid #e8caca;background:#fffafa}.route-map svg{display:block;width:100%;height:250px}.route-map path{fill:none;stroke:#eadedd;stroke-width:1}.route-map polyline{fill:none;stroke:#ec7c91;stroke-width:4;stroke-linecap:round;stroke-linejoin:round;stroke-dasharray:8 7}.route-map circle{fill:#fff;stroke:#e96f88;stroke-width:4}.route-map text{text-anchor:middle;fill:#d65d77;font-size:11px;font-weight:800}.route-list{display:flex;flex-wrap:wrap;gap:8px;margin:12px 0}.route-list li{display:flex;align-items:center;gap:6px;padding:6px 10px;border-radius:99px;background:#fff}.route-list b{color:#e46981}.insight{padding:13px 15px;border-left:4px solid #ef8296;background:#fff;font-size:12px;line-height:1.6}.place-grid,.summary-list,.visit-list{display:flex;flex-direction:column;gap:12px}.place-grid article{display:grid;grid-template-columns:120px 1fr;gap:14px;padding:10px;border:1px solid #ead4d0;background:#fff}.place-grid img{width:120px;height:94px;object-fit:cover}.place-grid h4,.summary-list h4,.visit-list h4{margin-bottom:5px;font-size:16px}.place-grid p{margin-bottom:9px;color:#907973;font-size:11px}.summary-list article,.visit-list article{padding:15px 17px;border:1px solid #ead4d0;background:rgba(255,255,255,.7)}.summary-list p,.visit-list p{font-size:12px;line-height:1.65}.summary-list ul{display:flex;gap:6px;margin-top:8px}.summary-list li{color:#d7627a;font-size:10px}.visit-head{display:flex;justify-content:space-between}.visit-head b{padding:4px 10px;border-radius:99px;background:#e77a8e;color:#fff;font-size:11px}.visit-head b.no{background:#9b8b87}.visit-date{display:flex;gap:12px;margin:6px 0}.visit-date strong{color:#df6c82}.score-pair{margin-top:6px;color:#967b75}.report[data-page='4'] .page{max-height:545px;overflow:auto;padding-right:4px}@media(max-width:620px){.report{min-height:600px;padding:48px 24px 28px}.page--cover{padding-top:30px}.page--cover h3{font-size:31px}.cover-stats{margin:30px 0;grid-template-columns:1fr}.place-grid article{grid-template-columns:90px 1fr}.place-grid img{width:90px;height:82px}}
.report{background:linear-gradient(rgba(255,243,243,.9),rgba(255,247,240,.9)),url('../../frontend-assets/decorations/love_maptually_paper_texture.png') center/460px}
.report__tape,.report__decor,.report__confetti,.report__lovey{position:absolute;z-index:1;pointer-events:none;user-select:none}
.report__tape{top:-64px;left:50%;width:190px;opacity:.52;transform:translateX(-50%) rotate(-5deg)}
.report__decor{right:-34px;bottom:-28px;width:180px;opacity:.2;object-fit:contain}
.report__confetti{left:-26px;top:72px;width:150px;opacity:.3;transform:rotate(-12deg)}
.report__lovey{right:28px;bottom:20px;width:122px;filter:drop-shadow(0 7px 8px rgba(91,58,50,.16))}
.report[data-page='0'] .report__decor{right:-50px;top:20px;bottom:auto;width:210px;opacity:.18}
.report[data-page='1'] .report__decor{right:-25px;bottom:-20px;width:150px;opacity:.17;transform:rotate(12deg)}
.report[data-page='2'] .report__decor{right:-20px;bottom:-4px;width:145px;opacity:.16;transform:rotate(-10deg)}
.report[data-page='3'] .report__decor{right:-26px;top:78px;bottom:auto;width:150px;opacity:.13;transform:rotate(8deg)}
.report[data-page='4'] .report__decor{right:-24px;bottom:4px;width:170px;opacity:.22;transform:rotate(-7deg)}
.cover-stats span,.place-grid article,.summary-list article,.visit-list article{backdrop-filter:blur(2px);box-shadow:0 5px 12px rgba(109,73,65,.07)}
.place-grid article:nth-child(odd),.summary-list article:nth-child(odd){transform:rotate(-.35deg)}
.place-grid article:nth-child(even),.summary-list article:nth-child(even){transform:rotate(.35deg)}
.route-map,.report-map{box-shadow:0 6px 15px rgba(100,68,62,.08)}
@media(max-width:620px){.report__decor{width:115px}.report__lovey{width:84px;right:14px}.report__confetti{width:100px}.report__tape{width:140px}}
</style>
