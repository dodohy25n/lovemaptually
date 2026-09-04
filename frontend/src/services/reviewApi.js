import { fetchPlace, updatePlace } from './placeApi.js'
import { config, isLocalMode } from './config.js'
import { COUPLE_MEMBERS, memberOf } from '@/utils/users.js'
import { coupleScoreFromReviews, reviewAverage, toHeartGrade } from '@/utils/heartGrade.js'
import { extractReviewTags } from './aiReadyMock.js'

/**
 * 리뷰 API 어댑터.
 *
 * 리뷰는 장소에 종속된 값이라, 지금은 장소 Repository를 통해 읽고 씁니다.
 * 백엔드가 붙으면 이 파일만 /places/:id/reviews 호출로 바꾸면 됩니다.
 * 반환 형식은 그대로 유지해야 합니다.
 *
 *   Review = { userId, userName, content,
 *              atmosphere, taste, value, service, revisitIntent, images }
 */

export class ReviewApiError extends Error {
  constructor(message, code = 'review_api_error') {
    super(message)
    this.name = 'ReviewApiError'
    this.code = code
  }
}

function authorizationFor(url) {
  if (url.hostname.endsWith('.mock.pstmn.io')) return 'Bearer mock-token'
  const token = typeof window !== 'undefined'
    ? window.localStorage.getItem('love-maptually:access-token')
    : ''
  return token ? `Bearer ${token}` : ''
}

/** API 응답을 기존 리뷰 화면 모델로 합칩니다. */
function toReviewModel(data, draft) {
  return {
    ...draft,
    reviewId: data.reviewId,
    visitedOn: data.visitedOn ?? null,
    rating: data.rating ?? reviewAverage(draft),
    content: String(data.content ?? draft.content ?? ''),
    tagStatus: ['PENDING', 'COMPLETED', 'FAILED'].includes(data.tagStatus)
      ? data.tagStatus
      : 'PENDING',
    extractedTags: Array.isArray(data.tags) ? data.tags : [],
    createdAt: data.createdAt ?? null,
  }
}

/** 화면의 네 가지 세부 점수 평균을 backend 계약인 1~5 정수 별점으로 변환합니다. */
export function toApiRating(review) {
  const rating = Math.round(reviewAverage(review))
  if (rating < 1 || rating > 5) {
    throw new ReviewApiError('리뷰 점수를 하나 이상 선택해주세요.', 'invalid_rating')
  }
  return rating
}

/** POST /api/reviews 호출. 화면 계약 유지를 위해 저장된 리뷰 한 건을 반환합니다. */
export async function createReviewFromApi(
  placeId,
  review,
  { visitedOn, fetchImpl = globalThis.fetch } = {},
) {
  if (!config.apiBaseUrl) {
    throw new ReviewApiError('API 기본 주소가 설정되지 않았습니다.', 'missing_base_url')
  }
  if (typeof fetchImpl !== 'function') {
    throw new ReviewApiError('이 환경에서는 리뷰 등록 요청을 보낼 수 없습니다.', 'fetch_unavailable')
  }

  const url = new URL('/api/reviews', config.apiBaseUrl)
  const rating = toApiRating(review)
  const authorization = authorizationFor(url)
  const isPostmanMock = url.hostname.endsWith('.mock.pstmn.io')
  const headers = {
    Accept: 'application/json',
    'Content-Type': 'application/json',
    ...(authorization && { Authorization: authorization }),
    ...(isPostmanMock && { 'x-mock-response-code': '201' }),
  }

  let response
  try {
    response = await fetchImpl(url, {
      method: 'POST',
      headers,
      body: JSON.stringify({
        placeId: Number.isNaN(Number(placeId)) ? placeId : Number(placeId),
        visitedOn,
        rating,
        content: String(review?.content ?? '').trim(),
      }),
    })
  } catch {
    throw new ReviewApiError('리뷰를 저장하지 못했습니다.', 'network_error')
  }

  const payload = await response.json().catch(() => null)
  if (!response.ok) {
    const duplicate = response.status === 409 && payload?.error?.code === 'REVIEW_DUPLICATED'
    throw new ReviewApiError(
      payload?.message || '리뷰를 저장하지 못했습니다.',
      duplicate ? 'duplicate_review' : 'http_error',
    )
  }
  if (!payload?.data || payload.data.reviewId == null) {
    throw new ReviewApiError('리뷰 등록 응답 형식이 올바르지 않습니다.', 'invalid_response')
  }

  return toReviewModel(payload.data, review)
}

/** 빈 리뷰 한 건. 폼의 초기값으로 사용합니다. */
export function emptyReview(member) {
  return {
    userId: member.userId,
    userName: member.userName,
    content: '',
    atmosphere: 0,
    taste: 0,
    value: 0,
    service: 0,
    revisitIntent: false,
    images: [],
  }
}

/** 장소의 리뷰를 사용자별로 정리해 돌려줍니다. 없는 사람은 null. */
export async function fetchReviewsByMember(placeId) {
  const place = await fetchPlace(placeId)
  const result = {}
  for (const member of COUPLE_MEMBERS) {
    result[member.role] = place?.reviews?.find((r) => r.userId === member.userId) ?? null
  }
  return result
}

/**
 * 한 사람의 리뷰를 저장(없으면 추가, 있으면 교체)합니다.
 * 저장 후 커플 통합 점수와 하트 등급은 Repository가 다시 계산합니다.
 */
export async function saveReview(placeId, review) {
  const place = await fetchPlace(placeId)
  if (!place) {
    throw new Error('리뷰를 저장할 장소를 찾을 수 없습니다.')
  }
  const member = memberOf(review.userId)

  if (!isLocalMode()) {
    const savedReview = await createReviewFromApi(placeId, {
      ...review,
      userName: review.userName || member?.userName || '',
    }, {
      visitedOn: place.visitedAt || new Date().toISOString().slice(0, 10),
    })
    const reviews = [...(place.reviews ?? []).filter((item) => item.userId !== savedReview.userId), savedReview]
    const coupleScore = coupleScoreFromReviews(reviews)
    return {
      ...place,
      visitedAt: savedReview.visitedOn || place.visitedAt,
      reviews,
      coupleScore,
      heartGrade: toHeartGrade(coupleScore),
      updatedAt: new Date().toISOString(),
    }
  }

  const extraction = await extractReviewTags(review.content)
  const next = {
    ...review,
    userName: review.userName || member?.userName || '',
    tagStatus: extraction.tagStatus,
    extractedTags: extraction.tags,
  }
  const reviews = place.reviews.filter((r) => r.userId !== next.userId)
  return updatePlace(placeId, { reviews: [...reviews, next] })
}

export async function deleteReview(placeId, userId) {
  const place = await fetchPlace(placeId)
  if (!place) return null
  return updatePlace(placeId, { reviews: place.reviews.filter((r) => r.userId !== userId) })
}

/** 리뷰 한 건의 세부 점수 평균 (화면 표시용). */
export function scoreOf(review) {
  return reviewAverage(review)
}
