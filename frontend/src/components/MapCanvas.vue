<script setup>
import { ref, shallowRef, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import HeartMarker from './HeartMarker.vue'
import BaseIcon from './BaseIcon.vue'
import { createMapEngine } from '@/services/mapEngine.js'
import { getCurrentPosition } from '@/services/geolocation.js'

/**
 * 실제 지도(카카오 지도).
 *
 * 핀은 지도 마커가 아니라 지도 위에 겹쳐 놓은 Vue 컴포넌트로 그립니다.
 * 점수 텍스트가 평범한 HTML로 남고 props/emit도 그대로 쓸 수 있어,
 * 지도 엔진이 바뀌어도 핀 디자인은 영향을 받지 않습니다.
 *
 * 카카오 SDK를 못 불러와도(키 없음, 도메인 미등록, 오프라인, 테스트 환경)
 * 타일 없는 대체 지도로 물러나 핀은 그대로 동작합니다 (services/mapEngine.js).
 */
const props = defineProps({
  places: { type: Array, default: () => [] },
  selectedId: { type: String, default: null },
  picking: { type: Boolean, default: false },
  searchedPlace: { type: Object, default: null },
  showRoute: { type: Boolean, default: false },
})

const emit = defineEmits(['select', 'pick'])

const containerRef = ref(null)
const engine = shallowRef(null)
const positions = ref({})
const searchedPosition = ref(null)
// 카카오 지도를 못 불러와 대체 지도로 물러난 상태. 핀은 그대로 동작합니다.
const mapImageMissing = ref(false)
const ready = ref(false)

// ── 내 위치 ──────────────────────────────────────────────
const myLocation = ref(null)
const myLocationPoint = ref(null)
const locating = ref(false)
const locateError = ref('')

const LOCATE_ERRORS = {
  denied: '위치 권한이 거부되어 있어요. 브라우저 주소창의 자물쇠에서 허용해주세요.',
  insecure: 'https 연결에서만 현재 위치를 쓸 수 있어요.',
  unsupported: '이 브라우저에서는 현재 위치를 쓸 수 없어요.',
  timeout: '위치를 찾는 데 너무 오래 걸려요. 잠시 후 다시 시도해주세요.',
  unavailable: '현재 위치를 가져오지 못했어요.',
}

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
  if (!engine.value) return
  const next = {}
  for (const place of positionable(props.places)) {
    next[place.id] = engine.value.containerPointOf(place)
  }
  positions.value = next
  searchedPosition.value = props.searchedPlace
    ? engine.value.containerPointOf(props.searchedPlace)
    : null
  // 내 위치 표시도 지도와 함께 따라 움직여야 합니다.
  myLocationPoint.value = myLocation.value
    ? engine.value.containerPointOf(myLocation.value)
    : null
}

/** '내 위치' — 현재 위치로 지도를 옮기고 그 자리에 표시를 남깁니다. */
async function locateMe() {
  if (locating.value) return
  locating.value = true
  locateError.value = ''
  try {
    const coordinate = await getCurrentPosition()
    myLocation.value = coordinate
    engine.value?.panTo(coordinate)
    await nextTick()
    syncPositions()
  } catch (error) {
    locateError.value = LOCATE_ERRORS[error?.code] ?? LOCATE_ERRORS.unavailable
  } finally {
    locating.value = false
  }
}

/** 저장된 장소가 모두 보이도록 지도 범위를 맞춥니다. */
function fitToPlaces() {
  if (!engine.value) return
  engine.value.fitBounds(positionable(props.places))
}

onMounted(async () => {
  // SDK를 받아오는 동안이라 await 합니다. 실패하면 대체 지도가 돌아옵니다.
  const created = await createMapEngine(containerRef.value)
  // 기다리는 사이에 화면을 벗어났다면 정리만 하고 끝냅니다.
  if (!containerRef.value) {
    created.destroy()
    return
  }

  engine.value = created
  mapImageMissing.value = created.kind !== 'kakao'

  created.onViewChange(syncPositions)
  created.onClick((coordinate) => {
    if (!props.picking) return
    emit('pick', coordinate)
  })

  // 컨테이너 크기가 바뀌면 지도에 알려야 타일과 핀 위치가 어긋나지 않습니다.
  if (typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver(() => {
      engine.value?.relayout()
      syncPositions()
    })
    resizeObserver.observe(containerRef.value)
  }

  if (positionable(props.places).length > 0) hasFitted = true
  fitToPlaces()
  await nextTick()
  syncPositions()
  ready.value = true
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  resizeObserver = null
  engine.value?.destroy()
  engine.value = null
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

watch(
  [() => props.showRoute, () => props.places.map((place) => place.id).join('|')],
  () => {
    if (!engine.value) return
    const routePlaces = props.showRoute ? positionable(props.places) : []
    engine.value.drawRoute(routePlaces)
    if (routePlaces.length) {
      const wide = (containerRef.value?.clientWidth ?? 0) > 900
      engine.value.fitBounds(routePlaces, wide
        ? { top: 100, right: 250, bottom: 180, left: 380 }
        : { top: 80, right: 35, bottom: 150, left: 240 })
    }
  },
)

/** 특정 장소로 지도를 이동시킵니다. 부모(상세 패널 열기 등)에서 호출합니다. */
function focusPlace(placeId) {
  const place = props.places.find((item) => item.id === placeId)
  if (!place || !engine.value) return
  engine.value.panTo(place)
}

function focusSearchPlace(place) {
  if (!place || !engine.value) return
  engine.value.panTo(place)
  nextTick(syncPositions)
}

watch(() => props.searchedPlace, async () => { await nextTick(); syncPositions() })

defineExpose({ focusPlace, focusSearchPlace, fitToPlaces })
</script>

<template>
  <div class="map" :class="{ 'map--picking': picking }">
    <!-- 타일이 안 뜰 때 보이는 종이 지도 느낌의 배경 -->
    <div class="map__fallback-bg" aria-hidden="true"></div>

    <div ref="containerRef" class="map__surface" data-testid="map-canvas"></div>

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

    <div
      v-if="ready && searchedPlace && searchedPosition"
      class="map__searched-place"
      :style="{ left: `${searchedPosition.x}px`, top: `${searchedPosition.y}px` }"
      data-testid="map-searched-place"
    >
      <span><BaseIcon name="pin" :size="24" /></span>
      <strong>{{ searchedPlace.name }}</strong>
    </div>

    <!-- 내 위치 표시. 지도와 함께 움직이도록 핀과 같은 방식으로 좌표를 계산합니다. -->
    <div
      v-if="ready && myLocationPoint"
      class="map__me"
      data-testid="map-my-location"
      aria-hidden="true"
      :style="{ left: `${myLocationPoint.x}px`, top: `${myLocationPoint.y}px` }"
    >
      <span class="map__me-dot"></span>
    </div>

    <button
      type="button"
      class="map__locate"
      :aria-label="locating ? '현재 위치를 찾는 중' : '내 위치로 이동'"
      :aria-busy="locating"
      :disabled="locating"
      data-testid="map-locate"
      @click="locateMe"
    >
      <BaseIcon name="locate" :size="20" />
    </button>

    <p v-if="locateError" class="map__notice map__notice--error" role="status" data-testid="map-locate-error">
      {{ locateError }}
    </p>

    <p v-if="picking" class="map__hint" role="status">
      지도를 클릭해 장소의 위치를 찍어주세요.
    </p>

    <p v-if="mapImageMissing" class="map__notice" role="status" data-testid="map-fallback">
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

 /* 지도 이미지에는 어떤 보정도 걸지 않습니다.
    카카오 지도는 약관상 지도 이미지 변형과 로고·저작권 표시 가림이 금지됩니다.
    (예전 OSM 타일에 걸어 두었던 sepia/핑크 보정은 그래서 제거했습니다.) */
.map__surface {
  position: absolute;
  inset: 0;
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
.map__searched-place {
  position: absolute;
  z-index: calc(var(--lm-z-map-ui) + 2);
  display: flex;
  align-items: center;
  gap: 8px;
  transform: translate(-22px, -100%);
  padding: 7px 12px 7px 7px;
  border: 2px solid var(--lm-pink);
  border-radius: 999px;
  background: #fff;
  color: var(--lm-ink);
  box-shadow: var(--lm-shadow-pin);
  white-space: nowrap;
  pointer-events: none;
}
.map__searched-place span { display:grid;place-items:center;width:34px;height:34px;border-radius:50%;background:var(--lm-pink);color:#fff; }
.map__searched-place strong { font-size:12px; }
:global(.lm-route-marker){display:grid;place-items:center;width:28px;height:28px;border:3px solid #ec7489;border-radius:50%;background:#fff;color:#d75e77;font:800 12px/1 sans-serif;box-shadow:0 3px 9px rgba(98,54,62,.25)}

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

/* 내 위치 버튼 — 카카오 로고(좌하단)와 카테고리 필터(하단 중앙)를 피해 놓습니다. */
.map__locate {
  position: absolute;
  left: var(--lm-space-3);
  bottom: 44px;
  z-index: var(--lm-z-map-ui);
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: 50%;
  background: var(--lm-card);
  border: 1px solid var(--lm-card-edge);
  color: var(--lm-ink);
  box-shadow: var(--lm-shadow-card);
  transition: color 0.16s ease, transform 0.16s ease;
}
.map__locate:hover { color: var(--lm-pink); transform: translateY(-1px); }
.map__locate:disabled { opacity: 0.6; cursor: progress; transform: none; }

.map__me {
  position: absolute;
  transform: translate(-50%, -50%);
  pointer-events: none;
  z-index: var(--lm-z-map-ui);
}
.map__me-dot {
  display: block;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: var(--lm-pink-btn);
  border: 3px solid #fff;
  box-shadow: 0 0 0 4px rgba(242, 111, 138, 0.28);
}

.map__notice--error {
  max-width: min(88%, 460px);
  text-align: center;
  line-height: 1.5;
}

/* 대체 지도(Leaflet)의 기본 UI만 디자인 톤에 맞춥니다.
   카카오 지도의 로고·저작권은 가리면 안 되므로 손대지 않습니다. */
.map :deep(.leaflet-bar a) {
  background: var(--lm-card);
  color: var(--lm-ink);
  border-bottom-color: var(--lm-card-edge);
}
</style>
