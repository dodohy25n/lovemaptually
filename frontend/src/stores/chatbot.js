import { defineStore } from 'pinia'
import { ref } from 'vue'
import { greeting, sendMessage, userMessage } from '@/services/chatbotApi.js'

/**
 * 챗봇 '러비' 패널 상태.
 * 실제 AI 연결 여부는 chatbotApi 뒤에 숨어 있어 이 파일은 알지 못합니다.
 */
export const useChatbotStore = defineStore('chatbot', () => {
  const isOpen = ref(false)
  const messages = ref([])
  const pending = ref(false)
  const error = ref(null)

  function open() {
    isOpen.value = true
    // 처음 열 때만 러비가 먼저 인사합니다.
    if (messages.value.length === 0) {
      messages.value = [greeting()]
    }
  }

  function close() {
    isOpen.value = false
  }

  function toggle() {
    if (isOpen.value) close()
    else open()
  }

  async function send(text) {
    const trimmed = String(text ?? '').trim()
    if (!trimmed || pending.value) return null

    error.value = null
    messages.value = [...messages.value, userMessage(trimmed)]
    pending.value = true
    try {
      const reply = await sendMessage(trimmed)
      messages.value = [...messages.value, reply]
      return reply
    } catch (err) {
      error.value = err.message ?? '답장을 받지 못했어요.'
      return null
    } finally {
      pending.value = false
    }
  }

  function clear() {
    messages.value = []
    error.value = null
  }

  return { isOpen, messages, pending, error, open, close, toggle, send, clear }
})
