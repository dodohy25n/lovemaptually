/**
 * 지도 엔진.
 *
 * 화면(MapCanvas)은 이 인터페이스만 알고, 뒤가 카카오인지 대체 엔진인지 모릅니다.
 *
 *   createMapEngine()
 *    ├─ 카카오 SDK를 불러오면        → 카카오 지도
 *    └─ 못 불러오면(키 없음·도메인 미등록·오프라인) → 타일 없는 대체 지도(Leaflet)
 *
 * 대체 엔진은 지도 그림 없이 종이 배경만 깔지만 좌표 변환·확대·이동이 그대로 되므로,
 * **기록한 장소와 하트 핀은 어떤 경우에도 계속 동작합니다.**
 *
 * 공통 인터페이스:
 *   kind                     'kakao' | 'fallback'
 *   containerPointOf(place)  좌표 → 컨테이너 픽셀 {x, y}
 *   coordsAt(point)          컨테이너 픽셀 → 좌표 {latitude, longitude}
 *   panTo(place)             해당 좌표로 부드럽게 이동
 *   fitBounds(places)        전부 보이도록 범위 맞춤 (없으면 기본 위치)
 *   relayout()               컨테이너 크기 변경 반영
 *   onViewChange(cb) / onClick(cb) / destroy()
 */
import { loadKakaoMaps } from './kakaoSdk.js'
import { DEFAULT_CENTER, DEFAULT_ZOOM, DEFAULT_LEVEL } from './mapProvider.js'

/** 지도 위에 겹쳐 놓은 UI(상단 기록 버튼, 하단 카테고리 필터) 뒤로 핀이 숨지 않도록 하는 여백. */
const FIT_PADDING = { top: 90, right: 70, bottom: 150, left: 70 }

/**
 * 지도를 만듭니다. 카카오를 먼저 시도하고, 실패하면 대체 엔진으로 물러납니다.
 * 어느 쪽으로 갔는지는 `engine.kind` 로 알 수 있습니다.
 */
export async function createMapEngine(container) {
  try {
    const maps = await loadKakaoMaps()
    return createKakaoEngine(maps, container)
  } catch {
    // 지도 그림만 포기하고 핀은 살립니다. 사용자에게는 화면에서 따로 안내합니다.
    return createFallbackEngine(container)
  }
}

/** 카카오 지도. */
function createKakaoEngine(maps, container) {
  const map = new maps.Map(container, {
    center: new maps.LatLng(DEFAULT_CENTER.latitude, DEFAULT_CENTER.longitude),
    level: DEFAULT_LEVEL,
  })

  // Leaflet과 달리 카카오 지도는 확대/축소 버튼을 기본으로 그리지 않습니다.
  // 대체 지도와 조작 위치를 맞추기 위해 왼쪽 위에 붙입니다.
  map.addControl(new maps.ZoomControl(), maps.ControlPosition.TOPLEFT)

  const listeners = []
  const routeOverlays = []
  function on(target, type, handler) {
    maps.event.addListener(target, type, handler)
    listeners.push([target, type, handler])
  }

  return {
    kind: 'kakao',

    containerPointOf(place) {
      const point = map
        .getProjection()
        .containerPointFromCoords(new maps.LatLng(place.latitude, place.longitude))
      return { x: point.x, y: point.y }
    },

    coordsAt({ x, y }) {
      const coords = map.getProjection().coordsFromContainerPoint(new maps.Point(x, y))
      return { latitude: coords.getLat(), longitude: coords.getLng() }
    },

    panTo(place) {
      map.panTo(new maps.LatLng(place.latitude, place.longitude))
    },

    fitBounds(places, padding = FIT_PADDING) {
      if (places.length === 0) {
        map.setCenter(new maps.LatLng(DEFAULT_CENTER.latitude, DEFAULT_CENTER.longitude))
        map.setLevel(DEFAULT_LEVEL)
        return
      }
      const bounds = new maps.LatLngBounds()
      for (const place of places) {
        bounds.extend(new maps.LatLng(place.latitude, place.longitude))
      }
      map.setBounds(bounds, padding.top, padding.right, padding.bottom, padding.left)
      // 한 곳뿐이면 setBounds가 최대 배율까지 당겨버려 주변이 안 보입니다.
      if (places.length === 1) map.setLevel(Math.max(map.getLevel(), 4))
    },

    drawRoute(places) {
      routeOverlays.splice(0).forEach((overlay) => overlay.setMap(null))
      const path = places.map((place) => new maps.LatLng(place.latitude, place.longitude))
      if (path.length > 1) {
        const line = new maps.Polyline({ map, path, strokeWeight: 5, strokeColor: '#ec7489', strokeOpacity: 0.9, strokeStyle: 'shortdash' })
        routeOverlays.push(line)
      }
      path.forEach((position, index) => {
        const marker = new maps.CustomOverlay({
          map,
          position,
          content: `<span class="lm-route-marker">${index + 1}</span>`,
          yAnchor: 0.5,
          xAnchor: 0.5,
        })
        routeOverlays.push(marker)
      })
    },

    relayout() {
      map.relayout()
    },

    onViewChange(callback) {
      // 확대·이동이 진행되는 동안에도 핀이 따라붙어야 해서 center_changed까지 듣습니다.
      for (const type of ['center_changed', 'zoom_changed', 'bounds_changed', 'idle']) {
        on(map, type, callback)
      }
    },

    onClick(callback) {
      on(map, 'click', (event) => {
        callback({ latitude: event.latLng.getLat(), longitude: event.latLng.getLng() })
      })
    },

    destroy() {
      routeOverlays.splice(0).forEach((overlay) => overlay.setMap(null))
      for (const [target, type, handler] of listeners) {
        maps.event.removeListener(target, type, handler)
      }
      listeners.length = 0
      container.innerHTML = ''
    },
  }
}

/**
 * 대체 지도 — 타일 없이 좌표 변환만 담당합니다.
 *
 * Leaflet은 이미 번들에 있고 타일 레이어 없이도 확대·이동·좌표 변환이 모두 동작하므로,
 * 카카오를 못 쓸 때 핀을 살리는 데 그대로 씁니다. 지도 그림 자리에는 종이 배경이 깔립니다.
 */
async function createFallbackEngine(container) {
  const { default: L } = await import('leaflet')
  await import('leaflet/dist/leaflet.css')

  const map = L.map(container, {
    center: [DEFAULT_CENTER.latitude, DEFAULT_CENTER.longitude],
    zoom: DEFAULT_ZOOM,
    zoomControl: true,
    // Leaflet의 zoom transition 타이머는 화면 전환 뒤에도 남을 수 있습니다.
    // 타일이 없는 대체 지도에서는 애니메이션이 필요하지 않으므로 비활성화해
    // 제거된 map pane을 지연 콜백이 다시 참조하지 않게 합니다.
    zoomAnimation: false,
    attributionControl: false, // 표시할 지도 출처가 없습니다.
  })
  let routeLayer = null

  return {
    kind: 'fallback',

    containerPointOf(place) {
      const point = map.latLngToContainerPoint([place.latitude, place.longitude])
      return { x: point.x, y: point.y }
    },

    coordsAt({ x, y }) {
      const coords = map.containerPointToLatLng([x, y])
      return { latitude: coords.lat, longitude: coords.lng }
    },

    panTo(place) {
      map.panTo([place.latitude, place.longitude])
    },

    fitBounds(places, padding = FIT_PADDING) {
      if (places.length === 0) {
        map.setView([DEFAULT_CENTER.latitude, DEFAULT_CENTER.longitude], DEFAULT_ZOOM)
        return
      }
      map.fitBounds(
        L.latLngBounds(places.map((place) => [place.latitude, place.longitude])),
        {
          paddingTopLeft: [padding.left, padding.top],
          paddingBottomRight: [padding.right, padding.bottom],
          maxZoom: 15,
        },
      )
    },

    drawRoute(places) {
      if (routeLayer) map.removeLayer(routeLayer)
      const layers = []
      const coords = places.map((place) => [place.latitude, place.longitude])
      if (coords.length > 1) layers.push(L.polyline(coords, { color: '#ec7489', weight: 5, opacity: 0.9, dashArray: '8 7' }))
      coords.forEach((coordinate, index) => layers.push(L.marker(coordinate, {
        icon: L.divIcon({ className: '', html: `<span class="lm-route-marker">${index + 1}</span>`, iconSize: [28, 28], iconAnchor: [14, 14] }),
      })))
      routeLayer = L.layerGroup(layers).addTo(map)
    },

    relayout() {
      map.invalidateSize()
    },

    onViewChange(callback) {
      map.on('move zoom resize zoomend moveend', callback)
    },

    onClick(callback) {
      map.on('click', (event) => {
        callback({ latitude: event.latlng.lat, longitude: event.latlng.lng })
      })
    },

    destroy() {
      routeLayer = null
      map.remove()
    },
  }
}
