<script setup>
import BaseIcon from './BaseIcon.vue'
import raccoonLovey from '@/assets/characters/raccoon-lovey.png'

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
      <img :src="raccoonLovey" alt="" width="104" height="86" />
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
  width: 300px;
  max-width: none;
  margin-right: 24px;
  padding: 20px 24px;
  background: var(--lm-card);
  border: 1px solid var(--lm-pink-line);
  border-radius: 22px;
  box-shadow: var(--lm-shadow-card);
  font-size: 17px;
  color: var(--lm-ink);
  line-height: 1.65;
}
.chatbtn__bubble strong {
  display: block;
  margin-bottom: 5px;
  font-weight: 400;
  font-size: 18px;
  color: var(--lm-pink);
}
.chatbtn__bubble::after {
  content: '';
  position: absolute;
  right: 46px;
  bottom: -12px;
  width: 22px;
  height: 22px;
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
.chatbtn__mascot img {
  width: 208px;
  height: 172px;
}

.chatbtn__badge {
  position: absolute;
  right: -8px;
  bottom: 4px;
  display: grid;
  place-items: center;
  width: 68px;
  height: 68px;
  border-radius: 50%;
  background: var(--lm-pink-btn);
  color: #fff;
  box-shadow: 0 4px 12px rgba(242, 111, 138, 0.4);
}
.chatbtn__badge :deep(.lm-icon) { width:30px;height:30px; }

@media (max-height: 850px) and (min-width: 701px) {
  .chatbtn__bubble {
    width: 230px;
    margin-right: 14px;
    padding: 14px 18px;
    border-radius: 18px;
    font-size: 13px;
  }
  .chatbtn__bubble strong { margin-bottom: 3px; font-size: 14px; }
  .chatbtn__bubble::after { right: 34px; width: 17px; height: 17px; }
  .chatbtn__mascot img { width: 145px; height: 120px; }
  .chatbtn__badge { right: -5px; width: 48px; height: 48px; }
  .chatbtn__badge :deep(.lm-icon) { width: 22px; height: 22px; }
}

@media (max-width: 700px) {
  .chatbtn__bubble { display: none; }
  .chatbtn__mascot img { width: 130px; height: 108px; }
  .chatbtn__badge { width:48px;height:48px; }
  .chatbtn__badge :deep(.lm-icon) { width:22px;height:22px; }
}
</style>
