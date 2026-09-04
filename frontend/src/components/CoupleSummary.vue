<script setup>
import ProfilePlaceholder from './ProfilePlaceholder.vue'
import coupleProfile from '@/assets/couple-profile.jpeg'

/** '우리의 러브맵' 요약 카드 — 커플 일러스트와 함께한 장소 수. */
defineProps({
  count: { type: Number, default: 0 },
  groupName: { type: String, default: '' },
  canCreateGroup: { type: Boolean, default: false },
  creatingGroup: { type: Boolean, default: false },
})

defineEmits(['create-group'])
</script>

<template>
  <section class="summary lm-card" aria-labelledby="summary-title">
    <h2 id="summary-title" class="summary__title">
      <span aria-hidden="true">♡</span> {{ groupName ? `${groupName} 러브맵` : '우리의 러브맵' }} <span aria-hidden="true">♡</span>
    </h2>

    <ProfilePlaceholder
      class="summary__avatar"
      :size="110"
      :src="coupleProfile"
      label="우리 커플 프로필 사진"
    />

    <p class="summary__label">우리가 함께한 장소</p>
    <p class="summary__count">
      <strong data-testid="place-count">{{ count }}</strong>
      <span class="summary__unit">곳</span>
    </p>
    <button
      v-if="canCreateGroup"
      type="button"
      class="lm-btn lm-btn--primary summary__create"
      :disabled="creatingGroup"
      @click="$emit('create-group')"
    >
      {{ creatingGroup ? '만드는 중…' : '커플 러브맵 만들기' }}
    </button>
  </section>
</template>

<style scoped>
.summary {
  position: relative;
  isolation: isolate;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--lm-space-2);
  padding: 40px var(--lm-space-4) var(--lm-space-4);
  text-align: center;
  border-color: rgba(224, 185, 176, .86);
  border-radius: 16px 16px 20px 20px;
  background:
    linear-gradient(rgba(255, 251, 246, .91), rgba(255, 251, 246, .91)),
    url('../../frontend-assets/decorations/love_maptually_paper_texture.png') center / 420px;
}
.summary::before {
  content: '';
  position: absolute;
  z-index: 2;
  top: -8px;
  left: 12px;
  right: 12px;
  height: 28px;
  background:
    radial-gradient(circle at 12px 18px, #9f6d65 0 4px, #f9e6dd 4.5px 6px, transparent 6.5px),
    linear-gradient(90deg, transparent 9px, #bd7e74 9px 12px, transparent 12px) 0 0 / 30px 22px;
  background-size: 30px 26px;
  background-repeat: repeat-x;
}
.summary::after {
  content: '';
  position: absolute;
  z-index: -1;
  inset: 8px;
  border: 1px dashed rgba(237, 126, 145, .2);
  border-radius: 11px;
  pointer-events: none;
}
.summary__title {
  font-size: var(--lm-text-md);
  color: var(--lm-pink);
}
.summary__avatar {
  margin-top: var(--lm-space-2);
}
.summary__label {
  margin-top: var(--lm-space-2);
  font-size: var(--lm-text-sm);
  color: var(--lm-ink-soft);
}
.summary__count {
  display: flex;
  align-items: baseline;
  gap: 6px;
}
.summary__count strong {
  font-size: var(--lm-text-3xl);
  font-weight: 400;
  color: var(--lm-pink);
  line-height: 1;
}
.summary__unit {
  font-size: var(--lm-text-md);
  color: var(--lm-ink-soft);
}
.summary__create { margin-top: var(--lm-space-2); font-size: var(--lm-text-xs); }
</style>
