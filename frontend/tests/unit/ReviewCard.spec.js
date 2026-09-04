import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import ReviewCard from '@/components/ReviewCard.vue'
import HeartRating from '@/components/HeartRating.vue'

const PLACE = {
  id: 'p1',
  name: '연남동 오후 세시',
  address: '서울 마포구 연남로 27',
  category: '카페',
  visitedAt: '2026-02-14',
  coupleScore: 4.6,
  heartGrade: 'good',
  images: [],
  tags: ['첫 데이트', '조용한'],
  reviews: [],
}

const REVIEW = {
  userId: 'him',
  userName: '도현',
  content: '창가 자리에서 두 시간 넘게 이야기했다.',
  atmosphere: 5,
  taste: 4.5,
  value: 4,
  service: 5,
  revisitIntent: true,
  images: [],
}

describe('리뷰 카드 렌더링', () => {
  it('장소·방문일·태그·본문을 표시한다', () => {
    const wrapper = mount(ReviewCard, { props: { place: PLACE, review: REVIEW, role: 'him' } })
    const text = wrapper.text()

    expect(text).toContain('연남동 오후 세시')
    expect(text).toContain('2026-02-14')
    expect(text).toContain('#첫 데이트')
    expect(text).toContain('창가 자리에서')
  })

  it('분위기·맛·가성비·서비스 점수를 모두 보여준다', () => {
    const wrapper = mount(ReviewCard, { props: { place: PLACE, review: REVIEW, role: 'him' } })
    const text = wrapper.text()

    for (const label of ['분위기', '맛', '가성비', '서비스']) {
      expect(text).toContain(label)
    }
    // 세부 점수 평균 4.6이 하트 옆 숫자로 표시된다
    expect(wrapper.findComponent(HeartRating).props('score')).toBe(4.6)
  })

  it('사진이 없어도 6칸 레이아웃을 유지한다', () => {
    const wrapper = mount(ReviewCard, { props: { place: PLACE, review: REVIEW, role: 'him' } })
    expect(wrapper.findAll('.review__photo')).toHaveLength(6)
    expect(wrapper.findAll('.review__photo-empty')).toHaveLength(6)
  })

  it('사진이 있으면 앞 칸부터 채우고 나머지는 placeholder로 남는다', () => {
    const wrapper = mount(ReviewCard, {
      props: {
        place: PLACE,
        review: { ...REVIEW, images: ['/a.png', '/b.png'] },
        role: 'him',
      },
    })
    expect(wrapper.findAll('.review__photo')).toHaveLength(6)
    expect(wrapper.findAll('.review__photo img[alt^="연남동"]')).toHaveLength(2)
  })

  it('리뷰가 없으면 안내 문구를 보여준다', () => {
    const wrapper = mount(ReviewCard, { props: { place: PLACE, review: null, role: 'her' } })
    expect(wrapper.text()).toContain('아직 리뷰를 작성하지 않았어요.')
  })

  it('두 사람의 리뷰는 구조가 같고 role만 다르다', () => {
    const him = mount(ReviewCard, { props: { place: PLACE, review: REVIEW, role: 'him' } })
    const her = mount(ReviewCard, {
      props: { place: PLACE, review: { ...REVIEW, userId: 'her' }, role: 'her' },
    })

    expect(him.attributes('data-role')).toBe('him')
    expect(her.attributes('data-role')).toBe('her')
    // 같은 구성 요소 개수 = 같은 레이아웃
    expect(him.findAll('.review__photo').length).toBe(her.findAll('.review__photo').length)
    expect(him.findAll('.review__score').length).toBe(her.findAll('.review__score').length)
  })
})
