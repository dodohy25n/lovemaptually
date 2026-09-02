<script setup>
import { storeToRefs } from 'pinia'
import AppHeader from '@/components/AppHeader.vue'
import ChatbotButton from '@/components/ChatbotButton.vue'
import ChatbotPanel from '@/components/ChatbotPanel.vue'
import { useChatbotStore } from '@/stores/chatbot.js'

/** 챗봇은 모든 화면에서 쓰이므로 앱 껍데기에 둡니다. */
const chatbot = useChatbotStore()
const { isOpen, messages, pending, error } = storeToRefs(chatbot)
</script>

<template>
  <div class="app">
    <AppHeader />

    <main class="app__main">
      <RouterView />
    </main>

    <div class="app__chat">
      <ChatbotPanel
        :open="isOpen"
        :messages="messages"
        :pending="pending"
        :error="error"
        @send="chatbot.send"
        @close="chatbot.close"
      />
      <ChatbotButton :open="isOpen" @toggle="chatbot.toggle" />
    </div>
  </div>
</template>

<style scoped>
.app {
  min-height: 100%;
  display: flex;
  flex-direction: column;
}
.app__main {
  flex: 1;
  width: 100%;
  max-width: var(--lm-frame-max);
  margin: 0 auto;
  /* 아래 여백은 우하단 고정 챗봇 위젯이 콘텐츠를 가리지 않도록 넉넉히 둡니다. */
  padding: var(--lm-space-6) var(--lm-space-6) 130px;
}

/* 챗봇은 화면 우하단에 고정. 패널이 열리면 마스코트 위로 쌓입니다. */
.app__chat {
  position: fixed;
  right: var(--lm-space-5);
  bottom: var(--lm-space-5);
  z-index: var(--lm-z-chat);
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: var(--lm-space-3);
}

@media (max-width: 700px) {
  .app__main { padding: var(--lm-space-4) var(--lm-space-4) 110px; }
  .app__chat { right: var(--lm-space-3); bottom: var(--lm-space-3); }
}
</style>
