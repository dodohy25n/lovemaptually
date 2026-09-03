<script setup>
import { ref, shallowRef, onMounted, onBeforeUnmount, watch, nextTick } from 'vue'
import HeartMarker from './HeartMarker.vue'
import { createMapEngine } from '@/services/mapEngine.js'

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
})

const emit = defineEmits(['select', 'pick'])

const containerRef = ref(null)
const engine = shallowRef(null)
const positions = ref({})
// 카카오 지도를 못 불러와 대체 지도로 물러난 상태. 핀은 그대로 동작합니다.
const mapImageMissing = ref(false)
const ready = ref(false)

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

/** 특정 장소로 지도를 이동시킵니다. 부모(상세 패널 열기 등)에서 호출합니다. */
function focusPlace(placeId) {
  const place = props.places.find((item) => item.id === placeId)
  if (!place || !engine.value) return
  engine.value.panTo(place)
}

defineExpose({ focusPlace, fitToPlaces })
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

/* 대체 지도(Leaflet)의 기본 UI만 디자인 톤에 맞춥니다.
   카카오 지도의 로고·저작권은 가리면 안 되므로 손대지 않습니다. */
.map :deep(.leaflet-bar a) {
  background: var(--lm-card);
  color: var(--lm-ink);
  border-bottom-color: var(--lm-card-edge);
}
</style>
