/**
 * 지도 기본값.
 *
 * 지도는 카카오 지도 SDK로 그립니다(`services/mapEngine.js`).
 * 카카오를 못 불러오면 타일 없는 대체 지도로 물러나며, 그때 쓰는 값도 여기 있습니다.
 *
 * ⚠️ 카카오 지도는 사업자 약관에 따라 **로고와 저작권 표시를 가리거나 지도 이미지를
 *    변형하면 안 됩니다.** 예전에 OSM 타일에 걸어 두었던 sepia/핑크 보정은 그래서 뺐습니다.
 */

/** 서울 시청 부근. 저장된 장소가 없을 때의 초기 중심점. */
export const DEFAULT_CENTER = { latitude: 37.5563, longitude: 126.9723 }

/**
 * 카카오 지도의 확대 수준.
 * Leaflet의 zoom과 반대로 **숫자가 작을수록 확대**되며 1~14 범위입니다.
 * 7이면 서울과 주변 수도권이 한눈에 들어옵니다.
 */
export const DEFAULT_LEVEL = 7

/** 대체 지도(Leaflet)의 zoom. 이쪽은 숫자가 클수록 확대됩니다. */
export const DEFAULT_ZOOM = 12
