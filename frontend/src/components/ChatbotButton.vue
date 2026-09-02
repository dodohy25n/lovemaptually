<script setup>
import BaseIcon from './BaseIcon.vue'

/**
 * 우하단 고정 챗봇 버튼 — 마스코트 '러비'와 말풍선.
 * 마스코트를 눌러도, 아래 원형 버튼을 눌러도 같은 동작을 합니다.
 */
defineProps({
  open: { type: Boolean, default: false },
  showBubble: { type: Boolean, default: true },
})

defineEmits(['toggle'])
</script>

<template>
  <div class="chatbtn">
    <p v-if="showBubble && !open" class="chatbtn__bubble" aria-hidden="true">
      <strong>안녕! 나는 러비야!</strong>
      궁금한 게 있으면<br />무엇이든 물어봐줘! <span class="chatbtn__heart">♡</span>
    </p>

    <button
      type="button"
      class="chatbtn__mascot"
      :aria-label="open ? '러비 챗봇 닫기' : '러비 챗봇 열기'"
      :aria-expanded="open"
      aria-controls="chatbot-panel"
      data-testid="chatbot-button"
      @click="$emit('toggle')"
    >
      <img src="/assets/raccoon-lovey.svg" alt="" width="104" height="96" />
      <span class="chatbtn__badge">
        <BaseIcon :name="open ? 'close' : 'chat'" :size="18" />
      </span>
    </button>
  </div>
</template>

<style scoped>
.chatbtn {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: var(--lm-space-2);
}

.chatbtn__bubble {
  position: relative;
  max-width: 190px;
  padding: var(--lm-space-3) var(--lm-space-4);
  background: var(--lm-card);
  border: 1px solid var(--lm-pink-line);
  border-radius: var(--lm-radius);
  box-shadow: var(--lm-shadow-card);
  font-size: var(--lm-text-sm);
  color: var(--lm-ink);
  line-height: 1.55;
}
.chatbtn__bubble strong {
  display: block;
  margin-bottom: 2px;
  font-weight: 400;
  color: var(--lm-pink);
}
.chatbtn__bubble::after {
  content: '';
  position: absolute;
  right: 34px;
  bottom: -8px;
  width: 14px;
  height: 14px;
  background: var(--lm-card);
  border-right: 1px solid var(--lm-pink-line);
  border-bottom: 1px solid var(--lm-pink-line);
  transform: rotate(45deg);
}
.chatbtn__heart { color: var(--lm-pink); }

.chatbtn__mascot {
  position: relative;
  display: block;
  line-height: 0;
  transition: transform 0.16s ease;
}
.chatbtn__mascot:hover { transform: translateY(-3px); }

.chatbtn__badge {
  position: absolute;
  right: -6px;
  bottom: 2px;
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: 50%;
  background: var(--lm-pink-btn);
  color: #fff;
  box-shadow: 0 4px 12px rgba(242, 111, 138, 0.4);
}

@media (max-width: 700px) {
  .chatbtn__bubble { display: none; }
  .chatbtn__mascot img { width: 76px; height: 70px; }
}
</style>
