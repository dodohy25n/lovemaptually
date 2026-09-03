<script setup>
import { ref, watch, nextTick } from 'vue'
import BaseIcon from './BaseIcon.vue'
import raccoonLovey from '@/assets/characters/raccoon-lovey.png'

/**
 * 챗봇 대화 패널.
 * 응답 생성은 chatbotApi(현재 mock)가 담당하고, 이 컴포넌트는 표시만 합니다.
 */
const props = defineProps({
  open: { type: Boolean, default: false },
  messages: { type: Array, default: () => [] },
  pending: { type: Boolean, default: false },
  error: { type: String, default: null },
})

const emit = defineEmits(['send', 'close'])

const draft = ref('')
const inputRef = ref(null)
const logRef = ref(null)

function submit() {
  const text = draft.value.trim()
  if (!text || props.pending) return
  emit('send', text)
  draft.value = ''
}

/** 열릴 때 입력창으로 포커스를 옮겨 키보드만으로 대화할 수 있게 합니다. */
watch(
  () => props.open,
  async (isOpen) => {
    if (!isOpen) return
    await nextTick()
    inputRef.value?.focus()
  },
)

/** 새 메시지가 오면 가장 아래로 스크롤합니다. */
watch(
  () => props.messages.length,
  async () => {
    await nextTick()
    if (logRef.value) logRef.value.scrollTop = logRef.value.scrollHeight
  },
)
</script>

<template>
  <section
    v-if="open"
    id="chatbot-panel"
    class="chat"
    role="dialog"
    aria-modal="false"
    aria-labelledby="chatbot-title"
    data-testid="chatbot-panel"
    @keydown.esc="emit('close')"
  >
    <header class="chat__head">
      <img :src="raccoonLovey" alt="" width="44" height="36" />
      <h2 id="chatbot-title" class="chat__title">러비에게 물어보기</h2>
      <button
        type="button"
        class="chat__close"
        aria-label="챗봇 닫기"
        data-testid="chatbot-close"
        @click="emit('close')"
      >
        <BaseIcon name="close" :size="18" />
      </button>
    </header>

    <div ref="logRef" class="chat__log" role="log" aria-live="polite" data-testid="chatbot-log">
      <p
        v-for="message in messages"
        :key="message.id"
        class="chat__msg"
        :class="`chat__msg--${message.role}`"
        :data-role="message.role"
      >
        {{ message.text }}
      </p>
      <p v-if="pending" class="chat__msg chat__msg--bot chat__msg--pending">러비가 생각하는 중…</p>
      <p v-if="error" class="chat__error" role="alert">{{ error }}</p>
    </div>

    <form class="chat__form" @submit.prevent="submit">
      <label for="chatbot-input" class="lm-sr-only">러비에게 보낼 메시지</label>
      <input
        id="chatbot-input"
        ref="inputRef"
        v-model="draft"
        type="text"
        autocomplete="off"
        placeholder="러비에게 물어보세요"
        data-testid="chatbot-input"
      />
      <button
        type="submit"
        class="chat__send"
        :disabled="pending || draft.trim().length === 0"
        aria-label="메시지 보내기"
        data-testid="chatbot-send"
      >
        <BaseIcon name="send" :size="21" />
      </button>
    </form>
  </section>
</template>

<style scoped>
.chat {
  display: flex;
  flex-direction: column;
  width: 520px;
  height: min(700px, calc(100vh - 170px));
  min-height: 520px;
  max-height: 700px;
  background: var(--lm-card);
  border: 1px solid var(--lm-card-edge);
  border-radius: var(--lm-radius-lg);
  box-shadow: var(--lm-shadow-lift);
  overflow: hidden;
}

.chat__head {
  display: flex;
  align-items: center;
  gap: var(--lm-space-2);
  padding: 18px 22px;
  background: var(--lm-pink-bg);
  border-bottom: 1px solid var(--lm-card-edge);
}
.chat__title {
  flex: 1;
  font-size: 20px;
  color: var(--lm-pink);
}
.chat__close {
  display: grid;
  place-items: center;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  color: var(--lm-ink-soft);
}
.chat__close:hover { background: #fff; color: var(--lm-pink); }

.chat__log {
  flex: 1;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: var(--lm-space-2);
  gap: 13px;
  padding: 24px;
  min-height: 380px;
}
.chat__msg {
  max-width: 82%;
  padding: 13px 17px;
  border-radius: var(--lm-radius);
  font-size: 15px;
  line-height: 1.7;
}
.chat__msg--bot {
  align-self: flex-start;
  background: var(--lm-pink-bg);
  color: var(--lm-ink);
  border-bottom-left-radius: 5px;
}
.chat__msg--user {
  align-self: flex-end;
  background: var(--lm-pink);
  color: #fff;
  border-bottom-right-radius: 5px;
}
.chat__msg--pending { opacity: 0.7; }
.chat__error {
  font-size: 13px;
  color: var(--lm-danger);
}

.chat__form {
  display: flex;
  gap: var(--lm-space-2);
  padding: 18px;
  border-top: 1px solid var(--lm-card-edge);
  background: var(--lm-header-bg);
}
.chat__form input {
  flex: 1;
  font: inherit;
  font-size: 15px;
  padding: 14px 17px;
  border: 1px solid var(--lm-card-edge);
  border-radius: 999px;
  background: #fff;
  color: var(--lm-ink);
}
.chat__send {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: var(--lm-pink-btn);
  color: #fff;
  flex: none;
}
.chat__send:disabled { opacity: 0.45; cursor: not-allowed; }

@media (max-width: 700px) {
  .chat {
    width: min(460px, calc(100vw - 24px));
    height: min(620px, calc(100vh - 125px));
    min-height: 400px;
  }
  .chat__log { min-height: 220px; padding: 16px; }
}
</style>
