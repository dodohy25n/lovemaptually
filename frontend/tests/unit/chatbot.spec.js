import { describe, it, expect, beforeEach } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { mockReplyFor } from '@/services/chatbotApi.js'
import { useChatbotStore } from '@/stores/chatbot.js'

describe('챗봇 mock 응답', () => {
  it('키워드에 맞는 답을 돌려준다', () => {
    expect(mockReplyFor('점수는 어떻게 계산돼?')).toContain('평균')
    expect(mockReplyFor('어디 갈까?')).toContain('주말')
    expect(mockReplyFor('안녕')).toContain('러비')
  })

  it('모르는 질문에는 폴백 답을 돌려준다', () => {
    expect(mockReplyFor('오늘 코스피 지수 알려줘')).toContain('배우는 중')
  })
})

describe('챗봇 패널 열기와 닫기', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('처음 열면 러비가 인사한다', () => {
    const chatbot = useChatbotStore()
    expect(chatbot.isOpen).toBe(false)

    chatbot.open()
    expect(chatbot.isOpen).toBe(true)
    expect(chatbot.messages).toHaveLength(1)
    expect(chatbot.messages[0].role).toBe('bot')
  })

  it('닫았다 다시 열어도 인사를 반복하지 않는다', () => {
    const chatbot = useChatbotStore()
    chatbot.open()
    chatbot.close()
    chatbot.open()
    expect(chatbot.messages).toHaveLength(1)
  })

  it('toggle로 열고 닫을 수 있다', () => {
    const chatbot = useChatbotStore()
    chatbot.toggle()
    expect(chatbot.isOpen).toBe(true)
    chatbot.toggle()
    expect(chatbot.isOpen).toBe(false)
  })

  it('메시지를 보내면 사용자 메시지와 답장이 쌓인다', async () => {
    const chatbot = useChatbotStore()
    chatbot.open()

    await chatbot.send('점수는 어떻게 계산돼?')

    expect(chatbot.messages).toHaveLength(3)
    expect(chatbot.messages[1].role).toBe('user')
    expect(chatbot.messages[2].role).toBe('bot')
    expect(chatbot.pending).toBe(false)
  })

  it('빈 메시지는 보내지 않는다', async () => {
    const chatbot = useChatbotStore()
    chatbot.open()
    await chatbot.send('   ')
    expect(chatbot.messages).toHaveLength(1)
  })
})
