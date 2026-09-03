<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { createMapEngine } from '@/services/mapEngine.js'

const props = defineProps({ places: { type: Array, default: () => [] } })
const container = ref(null)
const fallback = ref(false)
let engine = null

onMounted(async () => {
  const valid = props.places.filter((place) => Number.isFinite(place.latitude) && Number.isFinite(place.longitude))
  if (!container.value || !valid.length) return
  engine = await createMapEngine(container.value)
  fallback.value = engine.kind !== 'kakao'
  engine.relayout()
  engine.drawRoute(valid)
  engine.fitBounds(valid, { top: 34, right: 34, bottom: 34, left: 34 })
})

onBeforeUnmount(() => { engine?.destroy(); engine = null })
</script>

<template>
  <div class="report-map">
    <div ref="container" class="report-map__canvas" data-testid="report-route-map"></div>
    <p v-if="fallback" class="report-map__fallback">지도 키 연결 전 미리보기</p>
  </div>
</template>

<style scoped>
.report-map{position:relative;height:250px;overflow:hidden;border:1px solid #e8caca;background:#f4ede5}.report-map__canvas{position:absolute;inset:0}.report-map__fallback{position:absolute;right:9px;bottom:8px;padding:5px 9px;border-radius:99px;background:rgba(255,249,241,.92);color:#9c7c75;font-size:9px;box-shadow:0 2px 8px rgba(70,45,39,.12)}
:global(.lm-route-marker){display:grid;place-items:center;width:28px;height:28px;border:3px solid #ec7489;border-radius:50%;background:#fff;color:#d75e77;font:800 12px/1 sans-serif;box-shadow:0 3px 9px rgba(98,54,62,.25)}
</style>
