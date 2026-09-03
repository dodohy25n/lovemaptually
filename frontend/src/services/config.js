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
  /** 'mock' | 'api' — 챗봇 응답 출처 */
  aiMode: env.VITE_AI_MODE || 'mock',
  /** 백엔드 base URL (dataMode==='api' 일 때만 사용) */
  apiBaseUrl: env.VITE_API_BASE_URL || '',
  /**
   * 카카오 장소 검색용 JavaScript 키. 아직 검색 UI가 없어 읽어두기만 합니다.
   * 반드시 JavaScript 키여야 하며(REST 키는 서버 전용), 카카오 콘솔에서
   * 도메인 제한을 걸어야 합니다. 비어 있으면 수기 입력만 동작합니다.
   */
  kakaoJsKey: env.VITE_KAKAO_JS_KEY || '',
}

/** 카카오 장소 검색을 쓸 수 있는 상태인지. 키가 없으면 수기 입력으로만 동작합니다. */
export const canSearchPlaces = () => Boolean(config.kakaoJsKey)

export const isLocalMode = () => config.dataMode !== 'api'
export const isMockAi = () => config.aiMode !== 'api'
