<script setup>
import raccoonLovey from '@/assets/characters/raccoon-lovey.png'

defineProps({
  title: { type: String, required: true },
  description: { type: String, default: '' },
  actionLabel: { type: String, default: '' },
  compact: { type: Boolean, default: false },
})

defineEmits(['action'])
</script>

<template>
  <div class="empty" :class="{ 'empty--compact': compact }" data-testid="empty-state">
    <img
      class="empty__mascot"
      :src="raccoonLovey"
      alt=""
      width="72"
      height="59"
    />
    <p class="empty__title">{{ title }}</p>
    <p v-if="description" class="empty__desc">{{ description }}</p>
    <button
      v-if="actionLabel"
      type="button"
      class="lm-btn lm-btn--primary"
      @click="$emit('action')"
    >
      {{ actionLabel }}
    </button>
  </div>
</template>

<style scoped>
.empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--lm-space-2);
  padding: var(--lm-space-6) var(--lm-space-4);
  text-align: center;
}
.empty--compact { padding: var(--lm-space-4) var(--lm-space-3); }
.empty__mascot { opacity: 0.8; }
.empty--compact .empty__mascot { width: 48px; height: 40px; }
.empty__title {
  font-size: var(--lm-text-md);
  color: var(--lm-ink);
}
.empty__desc {
  font-size: var(--lm-text-sm);
  color: var(--lm-ink-soft);
  line-height: 1.6;
  max-width: 30ch;
}
.empty > .lm-btn { margin-top: var(--lm-space-2); }
</style>
