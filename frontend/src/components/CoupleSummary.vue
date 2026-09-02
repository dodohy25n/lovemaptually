<script setup>
/** '우리의 러브맵' 요약 카드 — 커플 일러스트와 함께한 장소 수. */
defineProps({
  count: { type: Number, default: 0 },
})
</script>

<template>
  <section class="summary lm-card" aria-labelledby="summary-title">
    <span class="lm-tape lm-tape--tl"></span>

    <h2 id="summary-title" class="summary__title">
      <span aria-hidden="true">♡</span> 우리의 러브맵 <span aria-hidden="true">♡</span>
    </h2>

    <img
      class="summary__avatar"
      src="/assets/couple-avatar.svg"
      alt="커플 프로필 일러스트"
      width="110"
      height="110"
    />

    <p class="summary__label">우리가 함께한 장소</p>
    <p class="summary__count">
      <strong data-testid="place-count">{{ count }}</strong>
      <span class="summary__unit">곳</span>
    </p>
  </section>
</template>

<style scoped>
.summary {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--lm-space-2);
  padding: var(--lm-space-5) var(--lm-space-4) var(--lm-space-4);
  text-align: center;
}
.summary__title {
  font-size: var(--lm-text-md);
  color: var(--lm-pink);
}
.summary__avatar {
  margin-top: var(--lm-space-2);
  border-radius: 50%;
  border: 2px solid var(--lm-pink-line);
  background: var(--lm-pink-bg);
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
</style>
