<script setup>
import { ref, shallowRef, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'
import HeartMarker from './HeartMarker.vue'
import { getTileProvider, DEFAULT_CENTER, DEFAULT_ZOOM } from '@/services/mapProvider.js'

/**
 * 실제 지도(Leaflet + OSM 타일).
 *
 * 핀은 Leaflet 마커 대신 지도 위에 겹쳐 놓은 Vue 컴포넌트로 그립니다.
 * 이렇게 하면 점수 텍스트가 평범한 HTML로 남고, props/emit도 그대로 쓸 수 있습니다.
 *
 * 타일 요청이 실패해도(오프라인, 차단, 테스트 환경) 지도 컨테이너와 핀은
 * 그대로 동작하며 fallback 배경과 안내만 추가로 보여줍니다.
 */
const props = defineProps({
  places: { type: Array, default: () => [] },
  selectedId: { type: String, default: null },
  picking: { type: Boolean, default: false },
})

const emit = defineEmits(['select', 'pick'])

const containerRef = ref(null)
const map = shallowRef(null)
const positions = ref({})
const tileFailed = ref(false)
const ready = ref(false)

let tileErrorCount = 0
let resizeObserver = null
// 장소는 마운트 직후가 아니라 스토어 로딩이 끝난 뒤 도착합니다.
// 목록이 처음 채워지는 순간 한 번만 지도 범위를 맞춥니다.
let hasFitted = false

/** 화면에 그릴 수 있는(좌표가 유효한) 장소만 남깁니다. */
function positionable(list) {
  return list.filter(
    (place) => Number.isFinite(place.latitude) && Number.isFinite(place.longitude),
  )
}

/** 각 장소의 화면 좌표(px)를 다시 계산합니다. */
function syncPositions() {
  if (!map.value) return
  const next = {}
  for (const place of positionable(props.places)) {
    const point = map.value.latLngToContainerPoint([place.latitude, place.longitude])
    next[place.id] = { x: point.x, y: point.y }
  }
  positions.value = next
}

/** 저장된 장소가 모두 보이도록 지도 범위를 맞춥니다. */
function fitToPlaces() {
  if (!map.value) return
  const list = positionable(props.places)
  if (list.length === 0) {
    map.value.setView([DEFAULT_CENTER.latitude, DEFAULT_CENTER.longitude], DEFAULT_ZOOM)
    return
  }
  const bounds = L.latLngBounds(list.map((place) => [place.latitude, place.longitude]))
  // 지도 위에 겹쳐 놓은 UI(상단 기록 버튼, 하단 카테고리 필터) 뒤로 핀이 숨지 않도록
  // 위아래 여백을 다르게 줍니다.
  map.value.fitBounds(bounds, {
    paddingTopLeft: [70, 90],
    paddingBottomRight: [70, 150],
    maxZoom: 15,
  })
}

onMounted(() => {
  const provider = getTileProvider()

  map.value = L.map(containerRef.value, {
    center: [DEFAULT_CENTER.latitude, DEFAULT_CENTER.longitude],
    zoom: DEFAULT_ZOOM,
    zoomControl: true,
    attributionControl: true,
  })

  const tiles = L.tileLayer(provider.url, {
    attribution: provider.attribution,
    maxZoom: provider.maxZoom,
  })

  // 타일이 몇 장 연속으로 실패하면 지도 서비스 장애로 보고 fallback을 켭니다.
  tiles.on('tileerror', () => {
    tileErrorCount += 1
    if (tileErrorCount >= 3) tileFailed.value = true
  })
  tiles.on('tileload', () => {
    tileErrorCount = 0
    tileFailed.value = false
  })
  tiles.addTo(map.value)

  map.value.on('move zoom resize zoomend moveend', syncPositions)
  map.value.on('click', (event) => {
    if (!props.picking) return
    emit('pick', { latitude: event.latlng.lat, longitude: event.latlng.lng })
  })

  // 컨테이너 크기가 바뀌면 Leaflet에 알려야 타일과 핀 위치가 어긋나지 않습니다.
  if (typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver(() => {
      map.value?.invalidateSize()
      syncPositions()
    })
    resizeObserver.observe(containerRef.value)
  }

  if (positionable(props.places).length > 0) hasFitted = true
  fitToPlaces()
  nextTick(() => {
    syncPositions()
    ready.value = true
  })
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  map.value?.remove()
  map.value = null
})

watch(
  () => props.places.map((place) => `${place.id}:${place.latitude}:${place.longitude}`).join('|'),
  async () => {
    if (!hasFitted && positionable(props.places).length > 0) {
      hasFitted = true
      fitToPlaces()
    }
    await nextTick()
    syncPositions()
  },
)

/** 특정 장소로 지도를 이동시킵니다. 부모(상세 패널 열기 등)에서 호출합니다. */
function focusPlace(placeId) {
  const place = props.places.find((item) => item.id === placeId)
  if (!place || !map.value) return
  map.value.panTo([place.latitude, place.longitude])
}

defineExpose({ focusPlace, fitToPlaces })
</script>

<template>
  <div class="map" :class="{ 'map--picking': picking }">
    <!-- 타일이 안 뜰 때 보이는 종이 지도 느낌의 배경 -->
    <div class="map__fallback-bg" aria-hidden="true"></div>

    <div ref="containerRef" class="map__leaflet" data-testid="map-canvas"></div>

    <!-- 실제 지도 타일을 디자인의 아이보리·핑크 톤으로 덮는 보정 레이어.
         클릭을 가로채지 않고, 핀 레이어보다 아래에 놓입니다. -->
    <div class="map__tint" aria-hidden="true"></div>

    <!-- 핀 레이어: 지도 위에 겹쳐 그리고, 핀 자체만 클릭을 받습니다. -->
    <div class="map__pins" data-testid="map-pins">
      <div
        v-for="place in places"
        v-show="ready && positions[place.id]"
        :key="place.id"
        class="map__pin"
        :style="{
          left: `${positions[place.id]?.x ?? 0}px`,
          top: `${positions[place.id]?.y ?? 0}px`,
        }"
      >
        <HeartMarker
          :place="place"
          :active="place.id === selectedId"
          @select="emit('select', $event)"
        />
      </div>
    </div>

    <p v-if="picking" class="map__hint" role="status">
      지도를 클릭해 장소의 위치를 찍어주세요.
    </p>

    <p v-if="tileFailed" class="map__notice" role="status" data-testid="map-fallback">
      지도 이미지를 불러오지 못했어요. 기록한 장소와 핀은 그대로 사용할 수 있어요.
    </p>

    <slot />
  </div>
</template>

<style scoped>
.map {
  position: relative;
  border-radius: var(--lm-radius-lg);
  border: 1px solid var(--lm-card-edge);
  background: var(--lm-map-bg);
  overflow: hidden;
  box-shadow: var(--lm-shadow-card);
  /* height:100%는 부모 높이가 auto일 때 stretch를 무력화시키므로 쓰지 않습니다.
     높이는 부모 flex 라인이 늘려줍니다. */
  min-height: 420px;
}
.map--picking { cursor: crosshair; }

/* 타일 실패 시에도 빈 회색 박스가 아니라 지도처럼 보이도록 하는 바탕 */
.map__fallback-bg {
  position: absolute;
  inset: 0;
  background-color: var(--lm-map-bg);
  background-image:
    linear-gradient(var(--lm-map-road) 3px, transparent 3px),
    linear-gradient(90deg, var(--lm-map-road) 3px, transparent 3px),
    linear-gradient(var(--lm-map-block) 1px, transparent 1px),
    linear-gradient(90deg, var(--lm-map-block) 1px, transparent 1px);
  background-size: 160px 160px, 160px 160px, 40px 40px, 40px 40px;
}

.map__leaflet {
  position: absolute;
  inset: 0;
  /* 1단계: 채도를 낮추고 종이 느낌으로 밝힙니다. */
  filter: sepia(0.5) saturate(0.55) hue-rotate(-8deg) brightness(1.14) contrast(0.86);
}

/* 2단계: 남은 초록·파랑 기운을 핑크 계열로 물들입니다. */
.map__tint {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: 1;
  background: var(--lm-pink-bg);
  mix-blend-mode: color;
  opacity: 0.5;
}

.map__pins {
  position: absolute;
  inset: 0;
  pointer-events: none;
  z-index: var(--lm-z-map-ui);
}
.map__pin {
  position: absolute;
  /* 핀 끝이 실제 좌표를 가리키도록 아래쪽 기준으로 정렬합니다. */
  transform: translate(-50%, -100%);
  pointer-events: auto;
}

.map__hint,
.map__notice {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  padding: 8px 16px;
  border-radius: 999px;
  font-size: var(--lm-text-sm);
  z-index: var(--lm-z-map-ui);
  box-shadow: var(--lm-shadow-card);
}
.map__hint {
  top: var(--lm-space-4);
  background: var(--lm-pink);
  color: #fff;
}
.map__notice {
  bottom: var(--lm-space-4);
  background: var(--lm-card);
  border: 1px solid var(--lm-card-edge);
  color: var(--lm-ink);
}

/* Leaflet 기본 UI를 디자인 톤에 맞춤 */
.map :deep(.leaflet-control-attribution) {
  background: rgba(255, 249, 241, 0.86);
  font-size: 10px;
  color: var(--lm-ink-faint);
}
.map :deep(.leaflet-bar a) {
  background: var(--lm-card);
  color: var(--lm-ink);
  border-bottom-color: var(--lm-card-edge);
}
</style>
