import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import ReviewCarouselModal from '@/components/ReviewCarouselModal.vue'
import { config } from '@/services/config.js'
import { STORAGE_KEYS, writeText } from '@/services/storageService.js'

// 장소 목록은 이 화면의 관심사가 아니라 저장소 계층을 통째로 대신합니다.
vi.mock('@/services/placeApi.js', () => ({
  fetchPlaces: vi.fn(async () => [PLACE]),
  fetchPlace: vi.fn(async () => PLACE),
  createPlace: vi.fn(),
  updatePlace: vi.fn(),
  deletePlace: vi.fn(),
  resetPlaces: vi.fn(async () => []),
  setActiveGroupId: vi.fn(),
  getPlaceRepository: vi.fn(),
  setPlaceRepository: vi.fn(),
}))

const PLACE = {
  id: '412',
  name: '연남동 오후 세시',
  address: '서울 마포구 연남로 27',
  category: '맛집',
  latitude: 37.56,
  longitude: 126.92,
  visitedAt: '',
  coupleScore: 0,
  heartGrade: 'bad',
  reviewedCount: 0,
  groupLabel: null,
  images: [],
  tags: [],
  reviews: [],
  memo: '',
  createdAt: '2026-09-01T00:00:00.000Z',
  updatedAt: '2026-09-01T00:00:00.000Z',
}

const GROUP_ID = 7001

const originalMode = config.dataMode
const originalBaseUrl = config.apiBaseUrl
const originalFetch = globalThis.fetch

/** 화면이 부르는 두 요청만 가려 응답합니다. 나머지는 실패로 둡니다. */
function stubFetch({ groupReviews, createResponse }) {
  const fetchMock = vi.fn(async (url, init = {}) => {
    const href = String(url)
    if (init.method === 'POST' && href.endsWith('/api/reviews')) {
      return createResponse
    }
    if (href.endsWith(`/api/groups/${GROUP_ID}/places/${PLACE.id}/reviews`)) {
      return {
        ok: true,
        status: 200,
        json: async () => ({ status: 200, message: '조회했습니다', data: groupReviews }),
      }
    }
    return { ok: false, status: 404, json: async () => ({ message: '없습니다' }) }
  })
  globalThis.fetch = fetchMock
  return fetchMock
}

const EMPTY_GROUP_REVIEWS = {
  myReview: null,
  otherReviews: [],
  otherReviewsLocked: false,
  lockedReason: null,
}

function createdResponse(data) {
  return {
    ok: true,
    status: 201,
    json: async () => ({ status: 201, message: '리뷰를 저장했습니다', data }),
  }
}

function errorResponse(status, code, message) {
  return {
    ok: false,
    status,
    json: async () => ({ status, message, error: { code, details: [] } }),
  }
}

async function mountModal(props = {}) {
  const wrapper = mount(ReviewCarouselModal, {
    props: { open: true, initialRole: 'him', placeId: PLACE.id, groupId: GROUP_ID, ...props },
    attachTo: document.body,
  })
  await flushPromises()
  await flushPromises()
  return wrapper
}

/** 별점을 고르고 문장을 적은 뒤 저장을 누릅니다. */
async function fillAndSubmit(wrapper, { rating = 4, content = '조용해서 얘기하기 좋았어요' } = {}) {
  await wrapper.find('[data-testid="review-rating-4"]').trigger('click')
  if (rating !== 4) await wrapper.find(`[data-testid="review-rating-${rating}"]`).trigger('click')
  await wrapper.find('[data-testid="review-content"]').setValue(content)
  await wrapper.find('[data-testid="review-write-form"]').trigger('submit')
  await flushPromises()
  await flushPromises()
}

beforeEach(() => {
  setActivePinia(createPinia())
  config.dataMode = 'api'
  config.apiBaseUrl = 'https://api.example.test'
  writeText(STORAGE_KEYS.accessToken, 'signed-token')
})

afterEach(() => {
  config.dataMode = originalMode
  config.apiBaseUrl = originalBaseUrl
  globalThis.fetch = originalFetch
  vi.restoreAllMocks()
})

describe('리뷰 작성 화면', () => {
  it('내 탭에 아직 리뷰가 없으면 작성 폼을 보여준다', async () => {
    stubFetch({ groupReviews: EMPTY_GROUP_REVIEWS })
    const wrapper = await mountModal()

    expect(wrapper.find('[data-testid="review-write-form"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="review-content"]').attributes('placeholder'))
      .toBe('그날 어땠는지 한 문장으로 남겨 주세요')
    expect(wrapper.text()).not.toContain('아직 리뷰를 작성하지 않았어요.')
  })

  it('저장에 성공하면 폼 대신 추출된 태그가 붙은 카드를 보여준다', async () => {
    const fetchMock = stubFetch({
      groupReviews: EMPTY_GROUP_REVIEWS,
      createResponse: createdResponse({
        reviewId: 501,
        placeId: 412,
        visitedOn: '2026-09-04',
        rating: 4,
        content: '조용해서 얘기하기 좋았어요',
        tagStatus: 'COMPLETED',
        tags: [
          { tag: '웨이팅', fact: '김', want: '짧음', evidence: '웨이팅이 40분' },
          { tag: '맵기', fact: '순함', want: '순함', evidence: '안 매워서' },
        ],
        placeLabel: { label: 'BOTH_LIKED', reviewedCount: 1, likedCount: 1 },
      }),
    })
    const wrapper = await mountModal()
    await fillAndSubmit(wrapper)

    expect(wrapper.find('[data-testid="review-write-form"]').exists()).toBe(false)
    const tags = wrapper.find('[data-testid="review-ai-tags"]')
    expect(tags.exists()).toBe(true)
    expect(tags.text()).toContain('웨이팅')
    expect(tags.text()).toContain('김')
    expect(tags.text()).toContain('짧음')
    expect(tags.text()).toContain('맵기')
    expect(tags.text()).toContain('순함')
    expect(wrapper.emitted('saved')?.[0]).toEqual([{ placeId: PLACE.id }])

    // 핀 라벨을 바꾸려면 그룹을 함께 보내야 합니다.
    const post = fetchMock.mock.calls.find(([, init]) => init?.method === 'POST')
    expect(JSON.parse(post[1].body)).toEqual({
      placeId: 412,
      withGroupId: GROUP_ID,
      visitedOn: expect.any(String),
      rating: 4,
      content: '조용해서 얘기하기 좋았어요',
    })
  })

  it('태그 추출에 실패해도 리뷰는 저장되었다고 알린다', async () => {
    stubFetch({
      groupReviews: EMPTY_GROUP_REVIEWS,
      createResponse: createdResponse({
        reviewId: 502,
        visitedOn: '2026-09-04',
        rating: 4,
        content: '조용해서 얘기하기 좋았어요',
        tagStatus: 'FAILED',
        tags: [],
      }),
    })
    const wrapper = await mountModal()
    await fillAndSubmit(wrapper)

    expect(wrapper.find('[data-testid="review-tag-failed"]').text())
      .toContain('태그를 뽑지 못했지만 리뷰는 저장되었습니다.')
    expect(wrapper.find('[data-testid="review-write-form"]').exists()).toBe(false)
  })

  it('같은 날짜에 이미 리뷰가 있으면 폼 옆에 안내를 남긴다', async () => {
    stubFetch({
      groupReviews: EMPTY_GROUP_REVIEWS,
      createResponse: errorResponse(409, 'REVIEW_DUPLICATED', '같은 날 같은 장소에 이미 리뷰를 남겼습니다'),
    })
    const wrapper = await mountModal()
    await fillAndSubmit(wrapper)

    expect(wrapper.find('[data-testid="review-form-error"]').text())
      .toBe('그 날짜에 남긴 리뷰가 이미 있습니다. 다른 날짜를 골라 주세요.')
    expect(wrapper.find('[data-testid="review-write-form"]').exists()).toBe(true)
  })

  it('별점 범위를 벗어나면 별점 안내를 남긴다', async () => {
    stubFetch({
      groupReviews: EMPTY_GROUP_REVIEWS,
      createResponse: errorResponse(422, 'RATING_OUT_OF_RANGE', '별점은 1점부터 5점까지입니다'),
    })
    const wrapper = await mountModal()
    await fillAndSubmit(wrapper)

    expect(wrapper.find('[data-testid="review-form-error"]').text())
      .toBe('별점은 1점부터 5점까지만 저장할 수 있습니다.')
  })

  it('상대 탭이 잠겨 있으면 잠금 사유를 보여준다', async () => {
    stubFetch({
      groupReviews: {
        myReview: null,
        otherReviews: [],
        otherReviewsLocked: true,
        lockedReason: '내 리뷰를 남기면 다른 구성원의 리뷰가 함께 공개됩니다',
      },
    })
    const wrapper = await mountModal({ initialRole: 'her' })

    expect(wrapper.find('[data-testid="review-locked"]').text())
      .toContain('내 리뷰를 남기면 다른 구성원의 리뷰가 함께 공개됩니다')
    expect(wrapper.find('[data-testid="review-write-form"]').exists()).toBe(false)
  })

  it('local 모드에서는 작성 폼 없이 기존 안내 문구를 그대로 쓴다', async () => {
    config.dataMode = 'local'
    stubFetch({ groupReviews: EMPTY_GROUP_REVIEWS })
    const wrapper = await mountModal()

    expect(wrapper.find('[data-testid="review-write-form"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('아직 리뷰를 작성하지 않았어요.')
  })
})
