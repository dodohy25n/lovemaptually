/**
 * 환경 변수 해석. import.meta.env를 직접 읽는 곳은 여기 한 곳뿐입니다.
 *
 * ⚠️ VITE_ 로 시작하는 값은 전부 브라우저 번들에 그대로 들어가 사용자에게 공개됩니다.
 *    서버 전용 비밀 키는 절대 이 경로로 다루지 마세요. (.env.example 참고)
 */
const env = typeof import.meta !== 'undefined' && import.meta.env ? import.meta.env : {}

export const config = {
  /** 'local' | 'api' — 장소 데이터의 출처 */
  dataMode: env.VITE_DATA_MODE || 'local',
  /** 'osm' | 'carto' — 지도 타일 공급자 */
  mapProvider: env.VITE_MAP_PROVIDER || 'osm',
  /** 'mock' | 'api' — 챗봇 응답 출처 */
  aiMode: env.VITE_AI_MODE || 'mock',
  /** 백엔드 base URL (dataMode==='api' 일 때만 사용) */
  apiBaseUrl: env.VITE_API_BASE_URL || '',
}

export const isLocalMode = () => config.dataMode !== 'api'
export const isMockAi = () => config.aiMode !== 'api'
