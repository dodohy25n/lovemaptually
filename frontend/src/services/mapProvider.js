import { config } from './config.js'

/**
 * 지도 타일 공급자 설정.
 *
 * 두 공급자 모두 클라이언트 키가 필요 없는 무료 타일입니다.
 * 만약 키가 필요한 공급자(카카오/네이버/구글 등)로 바꾼다면:
 *   - 브라우저에 노출되는 "공개용 클라이언트 키"만 VITE_MAP_CLIENT_KEY로 주입하세요.
 *   - 서버 전용 REST/Secret 키는 절대 프론트엔드 코드나 Git에 넣지 마세요.
 *   - 공개용 키라도 공급자 콘솔에서 반드시 도메인(Referer) 제한을 걸어야 합니다.
 *     제한이 없으면 번들에서 키를 그대로 복사해 도용할 수 있습니다.
 */
const PROVIDERS = {
  osm: {
    key: 'osm',
    url: 'https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',
    attribution: '© OpenStreetMap contributors',
    maxZoom: 19,
  },
  carto: {
    key: 'carto',
    url: 'https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png',
    attribution: '© OpenStreetMap contributors © CARTO',
    maxZoom: 20,
  },
}

export function getTileProvider() {
  return PROVIDERS[config.mapProvider] ?? PROVIDERS.osm
}

/** 서울 시청 부근. 저장된 장소가 없을 때의 초기 중심점. */
export const DEFAULT_CENTER = { latitude: 37.5563, longitude: 126.9723 }
export const DEFAULT_ZOOM = 12
