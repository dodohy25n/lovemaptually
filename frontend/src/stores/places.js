import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import {
  fetchPlaces,
  createPlace,
  updatePlace,
  deletePlace,
  resetPlaces,
} from '@/services/placeApi.js'
import { saveReview as saveReviewApi } from '@/services/reviewApi.js'
import { getLastStorageError } from '@/services/storageService.js'

export const CATEGORIES = [
  { key: 'all', label: '전체', icon: 'pin' },
  { key: '맛집', label: '맛집', icon: 'cutlery' },
  { key: '카페', label: '카페', icon: 'cup' },
  { key: '데이트 코스', label: '데이트 코스', icon: 'heart' },
  { key: '기타', label: '기타', icon: 'star' },
]

/**
 * 장소 스토어.
 * 저장소 구현(localStorage / HTTP)은 placeApi 뒤에 숨어 있어 이 파일은 알지 못합니다.
 */
export const usePlacesStore = defineStore('places', () => {
  const places = ref([])
  const loading = ref(false)
  const error = ref(null)
  const storageWarning = ref(null)
  const selectedId = ref(null)
  const activeCategory = ref('all')

  const selectedPlace = computed(
    () => places.value.find((place) => place.id === selectedId.value) ?? null,
  )

  const visiblePlaces = computed(() => {
    if (activeCategory.value === 'all') return places.value
    if (activeCategory.value === 'route') {
      return places.value
        .filter((place) => ['맛집', '카페'].includes(place.category))
        .sort((a, b) => String(a.visitedAt || '').localeCompare(String(b.visitedAt || '')))
    }
    if (activeCategory.value === 'favorites') {
      return places.value.filter((place) => place.favorite === true)
    }
    return places.value.filter((place) => place.category === activeCategory.value)
  })

  /** 최근 방문 장소 — 방문일 기준 내림차순. 방문일이 없으면 생성일로 대체합니다. */
  const recentPlaces = computed(() =>
    [...places.value].sort((a, b) => {
      const left = a.visitedAt || a.createdAt || ''
      const right = b.visitedAt || b.createdAt || ''
      return right.localeCompare(left)
    }),
  )

  const totalCount = computed(() => places.value.length)
  const isEmpty = computed(() => !loading.value && places.value.length === 0)

  async function load() {
    loading.value = true
    error.value = null
    try {
      places.value = await fetchPlaces()
      // 깨진 JSON을 만나도 앱은 계속 동작하되, 사용자에게는 알립니다.
      const storageError = getLastStorageError()
      storageWarning.value = storageError?.type === 'parse'
        ? '저장된 기록을 읽지 못해 기본 데이터로 시작했어요.'
        : null
    } catch (err) {
      error.value = err.message ?? '장소를 불러오지 못했습니다.'
      places.value = []
    } finally {
      loading.value = false
    }
  }

  async function add(draft, options) {
    error.value = null
    try {
      const place = await createPlace(draft, options)
      places.value = [...places.value, place]
      selectedId.value = place.id
      return place
    } catch (err) {
      error.value = err.message ?? '장소를 저장하지 못했습니다.'
      throw err
    }
  }

  async function edit(id, patch) {
    error.value = null
    try {
      const place = await updatePlace(id, patch)
      places.value = places.value.map((item) => (item.id === id ? place : item))
      return place
    } catch (err) {
      error.value = err.message ?? '장소를 수정하지 못했습니다.'
      throw err
    }
  }

  async function saveReview(placeId, review) {
    error.value = null
    try {
      const place = await saveReviewApi(placeId, review)
      places.value = places.value.map((item) => (item.id === placeId ? place : item))
      return place
    } catch (err) {
      error.value = err.message ?? '리뷰를 저장하지 못했습니다.'
      throw err
    }
  }

  async function remove(id) {
    await deletePlace(id)
    places.value = places.value.filter((place) => place.id !== id)
    if (selectedId.value === id) selectedId.value = null
  }

  function select(id) {
    selectedId.value = id
  }

  function clearSelection() {
    selectedId.value = null
  }

  function setCategory(key) {
    activeCategory.value = key
  }

  /** 개발·테스트용 초기화. */
  async function reset(options) {
    places.value = await resetPlaces(options)
    selectedId.value = null
  }

  return {
    places,
    loading,
    error,
    storageWarning,
    selectedId,
    activeCategory,
    selectedPlace,
    visiblePlaces,
    recentPlaces,
    totalCount,
    isEmpty,
    load,
    add,
    edit,
    saveReview,
    remove,
    select,
    clearSelection,
    setCategory,
    reset,
  }
})
