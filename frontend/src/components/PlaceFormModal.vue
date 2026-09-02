<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount, nextTick } from 'vue'
import BaseIcon from './BaseIcon.vue'
import HeartRating from './HeartRating.vue'
import { CATEGORIES } from '@/stores/places.js'
import { isValidLatitude, isValidLongitude } from '@/utils/coords.js'

/**
 * 장소 등록 / 수정 모달.
 *
 * 좌표는 지도에서 찍은 값이 채워지지만 직접 입력·수정도 가능합니다.
 * 커플 점수는 리뷰가 없을 때만 직접 입력하며, 리뷰가 있으면 세부 점수 평균으로 자동 계산됩니다.
 */
const props = defineProps({
  open: { type: Boolean, default: false },
  place: { type: Object, default: null },
  pickedCoordinate: { type: Object, default: null },
  saving: { type: Boolean, default: false },
  errorMessage: { type: String, default: null },
})

const emit = defineEmits(['submit', 'close', 'pick-request'])

const selectableCategories = CATEGORIES.filter((category) => category.key !== 'all')

function blankForm() {
  return {
    name: '',
    address: '',
    category: '카페',
    visitedAt: new Date().toISOString().slice(0, 10),
    latitude: '',
    longitude: '',
    coupleScore: 4,
    tagText: '',
  }
}

const form = ref(blankForm())
const touched = ref(false)
const firstFieldRef = ref(null)
const dialogRef = ref(null)

const isEditing = computed(() => Boolean(props.place))

const hasReviews = computed(() => (props.place?.reviews?.length ?? 0) > 0)

const errors = computed(() => ({
  name: form.value.name.trim() ? null : '장소명을 입력해주세요.',
  latitude: isValidLatitude(form.value.latitude) ? null : '위도는 -90 ~ 90 사이의 숫자여야 해요.',
  longitude: isValidLongitude(form.value.longitude)
    ? null
    : '경도는 -180 ~ 180 사이의 숫자여야 해요.',
}))

const isValid = computed(() => Object.values(errors.value).every((message) => message === null))

/** 열릴 때 폼을 채웁니다 — 수정이면 기존 값, 신규면 빈 값 + 찍은 좌표. */
watch(
  () => [props.open, props.place?.id],
  async ([open]) => {
    if (!open) return
    touched.value = false
    form.value = props.place
      ? {
          name: props.place.name,
          address: props.place.address,
          category: props.place.category,
          visitedAt: props.place.visitedAt,
          latitude: String(props.place.latitude),
          longitude: String(props.place.longitude),
          coupleScore: props.place.coupleScore,
          tagText: props.place.tags.join(', '),
        }
      : blankForm()
    await nextTick()
    firstFieldRef.value?.focus()
  },
  { immediate: true },
)

/** 지도에서 좌표를 찍으면 폼에 반영합니다. */
watch(
  () => props.pickedCoordinate,
  (coordinate) => {
    if (!coordinate || props.place) return
    form.value.latitude = coordinate.latitude.toFixed(6)
    form.value.longitude = coordinate.longitude.toFixed(6)
  },
)

function submit() {
  touched.value = true
  if (!isValid.value) return
  emit('submit', {
    name: form.value.name.trim(),
    address: form.value.address.trim(),
    category: form.value.category,
    visitedAt: form.value.visitedAt,
    latitude: Number(form.value.latitude),
    longitude: Number(form.value.longitude),
    coupleScore: Number(form.value.coupleScore),
    tags: form.value.tagText
      .split(',')
      .map((tag) => tag.trim())
      .filter(Boolean),
  })
}

function onKeydown(event) {
  if (!props.open) return
  if (event.key === 'Escape') {
    emit('close')
    return
  }
  // 포커스가 모달 밖으로 나가지 않도록 Tab을 가둡니다.
  if (event.key !== 'Tab' || !dialogRef.value) return
  const focusables = dialogRef.value.querySelectorAll(
    'button, input, select, textarea, [href], [tabindex]:not([tabindex="-1"])',
  )
  if (focusables.length === 0) return
  const first = focusables[0]
  const last = focusables[focusables.length - 1]
  if (event.shiftKey && document.activeElement === first) {
    event.preventDefault()
    last.focus()
  } else if (!event.shiftKey && document.activeElement === last) {
    event.preventDefault()
    first.focus()
  }
}

onMounted(() => document.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => document.removeEventListener('keydown', onKeydown))
</script>

<template>
  <div v-if="open" class="modal" data-testid="place-form">
    <div class="modal__backdrop" @click="emit('close')"></div>

    <div
      ref="dialogRef"
      class="modal__dialog"
      role="dialog"
      aria-modal="true"
      aria-labelledby="place-form-title"
    >
      <span class="lm-tape lm-tape--tl"></span>

      <header class="modal__head">
        <h2 id="place-form-title" class="modal__title">
          {{ isEditing ? '장소 정보 수정' : '우리가 함께한 장소 기록하기' }}
        </h2>
        <button
          type="button"
          class="modal__close"
          aria-label="닫기"
          data-testid="place-form-close"
          @click="emit('close')"
        >
          <BaseIcon name="close" :size="18" />
        </button>
      </header>

      <form class="modal__form" @submit.prevent="submit">
        <div class="lm-field">
          <label for="place-name">장소명</label>
          <input
            id="place-name"
            ref="firstFieldRef"
            v-model="form.name"
            type="text"
            required
            data-testid="field-name"
            placeholder="예) 연남동 오후 세시"
          />
          <p v-if="touched && errors.name" class="lm-field__error">{{ errors.name }}</p>
        </div>

        <div class="lm-field">
          <label for="place-address">주소</label>
          <input
            id="place-address"
            v-model="form.address"
            type="text"
            data-testid="field-address"
            placeholder="예) 서울 마포구 연남로 27"
          />
        </div>

        <div class="modal__grid">
          <div class="lm-field">
            <label for="place-category">카테고리</label>
            <select id="place-category" v-model="form.category" data-testid="field-category">
              <option v-for="category in selectableCategories" :key="category.key" :value="category.key">
                {{ category.label }}
              </option>
            </select>
          </div>
          <div class="lm-field">
            <label for="place-visited">방문일</label>
            <input
              id="place-visited"
              v-model="form.visitedAt"
              type="date"
              data-testid="field-visited-at"
            />
          </div>
        </div>

        <div class="modal__grid">
          <div class="lm-field">
            <label for="place-lat">위도</label>
            <input
              id="place-lat"
              v-model="form.latitude"
              type="text"
              inputmode="decimal"
              data-testid="field-latitude"
              placeholder="37.5626"
            />
            <p v-if="touched && errors.latitude" class="lm-field__error">{{ errors.latitude }}</p>
          </div>
          <div class="lm-field">
            <label for="place-lng">경도</label>
            <input
              id="place-lng"
              v-model="form.longitude"
              type="text"
              inputmode="decimal"
              data-testid="field-longitude"
              placeholder="126.9256"
            />
            <p v-if="touched && errors.longitude" class="lm-field__error">{{ errors.longitude }}</p>
          </div>
        </div>

        <button
          v-if="!isEditing"
          type="button"
          class="lm-btn lm-btn--quiet modal__pick"
          data-testid="pick-on-map"
          @click="emit('pick-request')"
        >
          <BaseIcon name="pin" :size="16" />
          지도에서 위치 찍기
        </button>

        <div class="lm-field">
          <label for="place-tags">태그 (쉼표로 구분)</label>
          <input
            id="place-tags"
            v-model="form.tagText"
            type="text"
            data-testid="field-tags"
            placeholder="첫 데이트, 조용한"
          />
        </div>

        <div class="lm-field">
          <label for="place-score">우리의 점수</label>
          <div class="modal__score">
            <input
              id="place-score"
              v-model.number="form.coupleScore"
              type="number"
              min="0"
              max="5"
              step="0.1"
              :disabled="hasReviews"
              data-testid="field-score"
            />
            <HeartRating :score="form.coupleScore" :size="24" :show-score="false" show-label />
          </div>
          <p class="modal__hint">
            {{
              hasReviews
                ? '리뷰가 있으면 두 사람의 세부 점수 평균으로 자동 계산돼요.'
                : '리뷰를 작성하면 세부 점수 평균으로 다시 계산돼요.'
            }}
          </p>
        </div>

        <p v-if="errorMessage" class="modal__error" role="alert" data-testid="place-form-error">
          {{ errorMessage }}
        </p>

        <div class="modal__actions">
          <button type="button" class="lm-btn lm-btn--quiet" @click="emit('close')">취소</button>
          <button
            type="submit"
            class="lm-btn lm-btn--primary"
            :disabled="saving"
            data-testid="place-form-submit"
          >
            {{ saving ? '저장 중…' : '저장하기' }}
          </button>
        </div>
      </form>
    </div>
  </div>
</template>

<style scoped>
.modal {
  position: fixed;
  inset: 0;
  z-index: var(--lm-z-modal);
  display: grid;
  place-items: center;
  padding: var(--lm-space-4);
}
.modal__backdrop {
  position: absolute;
  inset: 0;
  background: rgba(112, 81, 73, 0.34);
}

.modal__dialog {
  position: relative;
  width: min(460px, 100%);
  max-height: 90vh;
  display: flex;
  flex-direction: column;
  background: var(--lm-card);
  border: 1px solid var(--lm-card-edge);
  border-radius: var(--lm-radius-lg);
  box-shadow: var(--lm-shadow-lift);
}

.modal__head {
  display: flex;
  align-items: center;
  gap: var(--lm-space-2);
  padding: var(--lm-space-4);
  border-bottom: 1px solid var(--lm-card-edge);
  background: var(--lm-pink-bg);
  border-radius: var(--lm-radius-lg) var(--lm-radius-lg) 0 0;
}
.modal__title { flex: 1; font-size: var(--lm-text-lg); color: var(--lm-ink); }
.modal__close {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  color: var(--lm-ink-soft);
}
.modal__close:hover { background: #fff; color: var(--lm-pink); }

.modal__form {
  overflow-y: auto;
  padding: var(--lm-space-4);
  display: flex;
  flex-direction: column;
  gap: var(--lm-space-3);
}
.modal__grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--lm-space-3);
}
.modal__pick { align-self: flex-start; }
.modal__score { display: flex; align-items: center; gap: var(--lm-space-3); }
.modal__score input { width: 96px; }
.modal__hint { font-size: var(--lm-text-xs); color: var(--lm-ink-faint); }
.modal__error {
  padding: var(--lm-space-2) var(--lm-space-3);
  border-radius: var(--lm-radius-sm);
  background: #fbe9e5;
  color: var(--lm-danger);
  font-size: var(--lm-text-sm);
}
.modal__actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--lm-space-2);
  padding-top: var(--lm-space-2);
}

@media (max-width: 520px) {
  .modal__grid { grid-template-columns: 1fr; }
}
</style>
