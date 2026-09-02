import { describe, it, expect } from 'vitest'
import { mount } from '@vue/test-utils'
import HeartRating from '@/components/HeartRating.vue'

describe('하트 + 점수 표시', () => {
  it('점수는 이미지가 아니라 텍스트로 렌더링된다', () => {
    const wrapper = mount(HeartRating, { props: { score: 4.8 } })
    expect(wrapper.get('[data-testid="score-text"]').text()).toBe('4.8')
  })

  it('점수 구간에 따라 하트 이미지가 바뀐다', () => {
    const good = mount(HeartRating, { props: { score: 4.8 } })
    const normal = mount(HeartRating, { props: { score: 3.2 } })
    const bad = mount(HeartRating, { props: { score: 1.5 } })

    expect(good.get('img').attributes('src')).toContain('heart-good')
    expect(normal.get('img').attributes('src')).toContain('heart-normal')
    expect(bad.get('img').attributes('src')).toContain('heart-bad')
  })

  it('색상 외에 등급 라벨을 항상 함께 제공한다', () => {
    const wrapper = mount(HeartRating, { props: { score: 1.5 } })
    expect(wrapper.text()).toContain('아쉬워요')
    expect(wrapper.get('img').attributes('alt')).toBe('아쉬워요 하트')
  })
})
