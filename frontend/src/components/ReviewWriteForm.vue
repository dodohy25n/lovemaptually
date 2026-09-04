<script setup>
import { computed, ref, watch } from 'vue'
import { HEART_GRADES } from '@/utils/heartGrade.js'

/**
 * 리뷰 작성 폼.
 *
 * 카드와 같은 종이 위에 올라가고 크기도 같아서, 저장하는 순간 폼 자리에
 * 그대로 리뷰 카드가 들어섭니다.
 * 저장 요청은 부모가 보냅니다. 이 컴포넌트는 입력값만 모아 올려보냅니다.
 */
const props = defineProps({
  place: { type: Object, required: true },
  role: { type: String, default: 'him', validator: (v) => ['him', 'her'].includes(v) },
  saving: { type: Boolean, default: false },
  errorMessage: { type: String, default: '' },
})

const emit = defineEmits(['submit'])

const RATINGS = [1, 2, 3, 4, 5]
const heartAsset = HEART_GRADES.good.asset

/** 오늘 날짜를 YYYY-MM-DD 로. UTC로 변환하면 하루가 밀리므로 로컬 시각을 씁니다. */
function today() {
  const now = new Date()
  const offset = now.getTimezoneOffset() * 60000
  return new Date(now.getTime() - offset).toISOString().slice(0, 10)
}

const rating = ref(0)
const visitedOn = ref(today())
const content = ref('')
const localError = ref('')

// 장소를 바꿔 가며 여러 곳에 쓸 수 있어야 하므로 대상이 바뀌면 입력을 비웁니다.
watch(() => props.place?.id, () => {
  rating.value = 0
  visitedOn.value = today()
  content.value = ''
  localError.value = ''
})

const notice = computed(() => localError.value || props.errorMessage)

function submit() {
  if (props.saving) return
  if (!rating.value) {
    localError.value = '별점을 먼저 골라 주세요.'
    return
  }
  if (!content.value.trim()) {
    localError.value = '한 문장이라도 남겨 주세요.'
    return
  }
  localError.value = ''
  emit('submit', {
    rating: rating.value,
    visitedOn: visitedOn.value,
    content: content.value.trim(),
  })
}

function pick(value) {
  rating.value = value
  localError.value = ''
}
</script>

<template>
  <form class="write" :data-role="role" data-testid="review-write-form" @submit.prevent="submit">
    <span class="lm-tape lm-tape--tl"></span>

    <header class="write__head">
      <h3 class="write__title">오늘의 기억 남기기</h3>
      <p class="write__place">{{ place.name }}</p>
    </header>

    <div class="write__field">
      <span id="review-rating-label" class="write__label">별점</span>
      <div class="write__hearts" role="radiogroup" aria-labelledby="review-rating-label">
        <button
          v-for="value in RATINGS"
          :key="value"
          type="button"
          role="radio"
          class="write__heart"
          :class="{ 'write__heart--on': value <= rating }"
          :aria-checked="value === rating"
          :aria-label="`${value}점`"
          :data-testid="`review-rating-${value}`"
          @click="pick(value)"
        >
          <img :src="heartAsset" alt="" width="30" height="30" />
        </button>
        <span class="write__hearts-num" data-testid="review-rating-value">
          {{ rating ? `${rating}점` : '고르지 않음' }}
        </span>
      </div>
    </div>

    <div class="lm-field write__field">
      <label for="review-visited-on" class="write__label">방문 날짜</label>
      <input
        id="review-visited-on"
        v-model="visitedOn"
        type="date"
        data-testid="review-visited-on"
        required
      />
    </div>

    <div class="lm-field write__field write__field--grow">
      <label for="review-content" class="write__label">문장</label>
      <textarea
        id="review-content"
        v-model="content"
        rows="5"
        maxlength="500"
        data-testid="review-content"
        placeholder="그날 어땠는지 한 문장으로 남겨 주세요"
      ></textarea>
      <p class="write__help">
        솔직하게 적을수록 태그가 정확해집니다. 태그는 다음 추천에 그대로 쓰입니다.
      </p>
    </div>

    <p v-if="notice" class="write__error" role="alert" data-testid="review-form-error">
      {{ notice }}
    </p>

    <button
      type="submit"
      class="lm-btn lm-btn--primary write__submit"
      :disabled="saving"
      data-testid="review-submit"
    >
      {{ saving ? '저장하는 중입니다…' : '리뷰 저장하기' }}
    </button>
  </form>
</template>

<style scoped>
.write {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: var(--lm-space-4);
  min-height: 560px;
  padding: var(--lm-space-5);
  background: var(--lm-card);
  border: 1px solid var(--accent-line);
  border-radius: var(--lm-radius-lg);
  box-shadow: var(--lm-shadow-card);
}
.write[data-role='him'] { --accent: var(--lm-him); --accent-line: var(--lm-him-line); }
.write[data-role='her'] { --accent: var(--lm-her); --accent-line: var(--lm-her-line); }

.write__head { display: flex; flex-direction: column; gap: 4px; }
.write__title { font-size: var(--lm-text-xl); color: var(--accent); }
.write__place { font-size: var(--lm-text-sm); color: var(--lm-ink-soft); }

.write__field { display: flex; flex-direction: column; gap: 6px; }
.write__field--grow { flex: 1; }
.write__label {
  font-size: var(--lm-text-xs);
  font-weight: 700;
  color: var(--lm-ink-soft);
}

.write__hearts { display: flex; align-items: center; gap: 6px; }
.write__heart {
  display: grid;
  place-items: center;
  padding: 2px;
  border-radius: 50%;
  line-height: 0;
  filter: grayscale(1);
  opacity: 0.35;
  transition: opacity 0.12s ease, transform 0.12s ease, filter 0.12s ease;
}
.write__heart--on { filter: none; opacity: 1; transform: scale(1.08); }
.write__heart:hover { opacity: 0.85; }
.write__hearts-num {
  margin-left: var(--lm-space-2);
  font-size: var(--lm-text-sm);
  color: var(--lm-ink);
}

.write__help { font-size: var(--lm-text-xs); color: var(--lm-ink-faint); }
.write__error {
  padding: 9px 12px;
  border: 1px solid #e8bdb2;
  border-radius: var(--lm-radius-sm);
  background: #fbe9e5;
  font-size: var(--lm-text-sm);
  color: var(--lm-danger);
}
.write__submit { width: 100%; padding: 12px 18px; font-size: var(--lm-text-md); }

@media (max-width: 700px) {
  .write { min-height: 0; }
}
</style>
