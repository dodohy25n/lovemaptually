<script setup>
/** 0~5 세부 점수 입력. 슬라이더와 숫자를 함께 보여줍니다. */
defineProps({
  id: { type: String, required: true },
  label: { type: String, required: true },
  modelValue: { type: [Number, String], default: 0 },
})

defineEmits(['update:modelValue'])
</script>

<template>
  <div class="score">
    <label :for="id">{{ label }}</label>
    <input
      :id="id"
      type="range"
      min="0"
      max="5"
      step="0.1"
      :value="modelValue"
      @input="$emit('update:modelValue', Number($event.target.value))"
    />
    <output :for="id" class="score__out">{{ Number(modelValue).toFixed(1) }}</output>
  </div>
</template>

<style scoped>
.score {
  display: flex;
  align-items: center;
  gap: var(--lm-space-2);
}
.score label {
  width: 48px;
  flex: none;
  font-size: var(--lm-text-xs);
  color: var(--lm-ink-soft);
}
.score input {
  flex: 1;
  accent-color: var(--lm-pink);
}
.score__out {
  width: 26px;
  text-align: right;
  font-size: var(--lm-text-xs);
  color: var(--lm-ink);
  font-variant-numeric: tabular-nums;
}
</style>
