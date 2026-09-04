<script setup>
import { storeToRefs } from 'pinia'
import ChatbotButton from '@/components/ChatbotButton.vue'
import ChatbotPanel from '@/components/ChatbotPanel.vue'
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useChatbotStore } from '@/stores/chatbot.js'
import { isLocalMode } from '@/services/config.js'

/** 챗봇은 모든 화면에서 쓰이므로 앱 껍데기에 둡니다. */
const chatbot = useChatbotStore()
const route = useRoute()
// 챗봇은 아직 붙은 백엔드가 없어 api 모드에서는 아예 숨깁니다.
const chatbotEnabled = isLocalMode()
const showChrome = computed(() => chatbotEnabled && !route.meta.authLayout)
const isMap = computed(() => route.name === 'home')
const { isOpen, messages, pending, error } = storeToRefs(chatbot)
</script>

<template>
  <div class="app">
    <main class="app__main" :class="{ 'app__main--map': isMap }">
      <RouterView />
    </main>

    <div v-if="showChrome" class="app__chat">
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
  height: 100%;
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
.app__main--map {
  flex: 0 0 100dvh;
  max-width: none;
  height: 100dvh;
  padding: 0;
  overflow: hidden;
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
  .app__main--map { padding: 0; }
  .app__chat { right: var(--lm-space-3); bottom: var(--lm-space-3); }
}
</style>
