import { fetchPlace, updatePlace } from './placeApi.js'
import { COUPLE_MEMBERS, memberOf } from '@/utils/users.js'
import { reviewAverage } from '@/utils/heartGrade.js'
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
