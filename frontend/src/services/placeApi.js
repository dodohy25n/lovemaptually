import { config, isLocalMode } from './config.js'
import { LocalPlaceRepository, ApiPlaceRepository } from './placeRepository.js'

/**
 * 장소 API 어댑터.
 *
 * 화면과 스토어는 이 모듈의 함수만 호출합니다.
 * 지금은 LocalPlaceRepository(localStorage)를 쓰지만,
 * VITE_DATA_MODE=api 로 바꾸면 ApiPlaceRepository로 교체되고
 * 호출부는 아무것도 바뀌지 않습니다.
 *
 * 반환 형식 (고정 계약):
 *   Place = {
 *     id, name, address, category, latitude, longitude, visitedAt,
 *     coupleScore, heartGrade, images, tags, memo,
 *     reviews: [{ userId, userName, content,
 *                 atmosphere, taste, value, service, revisitIntent, images }],
 *     createdAt, updatedAt
 *   }
 */

let repository = null

/** 현재 모드에 맞는 Repository를 돌려줍니다. */
export function getPlaceRepository() {
  if (!repository) {
    repository = isLocalMode()
      ? new LocalPlaceRepository()
      : new ApiPlaceRepository({ baseUrl: config.apiBaseUrl })
  }
  return repository
}

/** 테스트에서 다른 Repository를 주입하기 위한 훅. */
export function setPlaceRepository(next) {
  repository = next
}

export function fetchPlaces(options) {
  return getPlaceRepository().list(options)
}

export function fetchPlace(id) {
  return getPlaceRepository().get(id)
}

export function createPlace(draft) {
  return getPlaceRepository().create(draft)
}

export function updatePlace(id, patch) {
  return getPlaceRepository().update(id, patch)
}

export function deletePlace(id) {
  return getPlaceRepository().remove(id)
}

export function resetPlaces(options) {
  return getPlaceRepository().reset(options)
}
