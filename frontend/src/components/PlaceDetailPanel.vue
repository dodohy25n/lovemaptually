<script setup>
import { ref, computed, watch, onMounted, onBeforeUnmount } from 'vue'
import HeartRating from './HeartRating.vue'
import ScoreField from './ScoreField.vue'
import BaseIcon from './BaseIcon.vue'
import EmptyState from './EmptyState.vue'
import { COUPLE_MEMBERS } from '@/utils/users.js'
import { emptyReview } from '@/services/reviewApi.js'
import { reviewAverage } from '@/utils/heartGrade.js'

/**
 * 장소 상세 패널.
 * 장소 정보와 두 사람의 리뷰를 보여주고, 리뷰는 이 자리에서 바로 저장할 수 있습니다.
 */
const props = defineProps({
  place: { type: Object, default: null },
  open: { type: Boolean, default: false },
  saving: { type: Boolean, default: false },
})

const emit = defineEmits(['close', 'edit', 'save-review', 'add'])

const panelRef = ref(null)
const editingRole = ref(null)
const draft = ref(null)

const members = computed(() =>
  COUPLE_MEMBERS.map((member) => ({
    member,
    review: props.place?.reviews.find((r) => r.userId === member.userId) ?? null,
  })),
)

const SCORE_FIELDS = [
  { key: 'atmosphere', label: '분위기' },
  { key: 'taste', label: '맛' },
  { key: 'value', label: '가성비' },
  { key: 'service', label: '서비스' },
]

function startEdit(member, review) {
  editingRole.value = member.role
  draft.value = review ? { ...review } : emptyReview(member)
}

function cancelEdit() {
  editingRole.value = null
  draft.value = null
}

function submitReview() {
  if (!draft.value) return
  emit('save-review', { placeId: props.place.id, review: { ...draft.value } })
}

// 다른 장소를 선택하면 편집 중이던 내용은 정리합니다.
watch(() => props.place?.id, cancelEdit)
watch(() => props.saving, (isSaving, was) => {
  if (was && !isSaving) cancelEdit()
})

function onKeydown(event) {
  if (event.key === 'Escape' && props.open) emit('close')
}

onMounted(() => document.addEventListener('keydown', onKeydown))
onBeforeUnmount(() => document.removeEventListener('keydown', onKeydown))
</script>

<template>
  <aside
    v-if="open"
    ref="panelRef"
    class="detail"
    role="dialog"
    aria-modal="false"
    aria-labelledby="detail-title"
    data-testid="place-detail"
  >
    <header class="detail__head">
      <h2 id="detail-title" class="detail__title">
        {{ place ? place.name : '장소 상세' }}
      </h2>
      <button
        type="button"
        class="detail__close"
        aria-label="장소 상세 닫기"
        data-testid="detail-close"
        @click="emit('close')"
      >
        <BaseIcon name="close" :size="18" />
      </button>
    </header>

    <EmptyState
      v-if="!place"
      title="선택한 장소가 없어요"
      description="지도의 하트 핀을 누르거나 새 장소를 기록해보세요."
      action-label="장소 기록하기"
      @action="emit('add')"
    />

    <div v-else class="detail__body">
      <dl class="detail__meta">
        <div class="detail__row">
          <dt>주소</dt>
          <dd data-testid="detail-address">{{ place.address || '주소 미입력' }}</dd>
        </div>
        <div class="detail__row">
          <dt>카테고리</dt>
          <dd>{{ place.category }}</dd>
        </div>
        <div class="detail__row">
          <dt>방문일</dt>
          <dd data-testid="detail-visited-at">{{ place.visitedAt || '방문일 미입력' }}</dd>
        </div>
        <div class="detail__row">
          <dt>좌표</dt>
          <dd class="detail__coord">
            {{ place.latitude.toFixed(5) }}, {{ place.longitude.toFixed(5) }}
          </dd>
        </div>
      </dl>

      <div class="detail__score">
        <span class="detail__score-label">커플 통합 점수</span>
        <HeartRating :score="place.coupleScore" :size="28" show-label />
      </div>

      <ul v-if="place.tags.length" class="detail__tags">
        <li v-for="tag in place.tags" :key="tag">#{{ tag }}</li>
      </ul>

      <section class="detail__photos">
        <h3 class="detail__subtitle">사진</h3>
        <ul v-if="place.images.length" class="detail__photo-list">
          <li v-for="(image, index) in place.images" :key="index">
            <img :src="image" :alt="`${place.name} 사진 ${index + 1}`" loading="lazy" />
          </li>
        </ul>
        <p v-else class="detail__no-photo">아직 등록된 사진이 없어요.</p>
      </section>

      <section class="detail__reviews">
        <h3 class="detail__subtitle">두 사람의 리뷰</h3>

        <article
          v-for="entry in members"
          :key="entry.member.userId"
          class="detail__review"
          :data-role="entry.member.role"
          :data-testid="`detail-review-${entry.member.role}`"
        >
          <header class="detail__review-head">
            <span class="detail__badge">{{ entry.member.label }}</span>
            <HeartRating
              v-if="entry.review"
              :score="reviewAverage(entry.review)"
              :size="18"
            />
            <button
              v-if="editingRole !== entry.member.role"
              type="button"
              class="detail__edit"
              :data-testid="`review-edit-${entry.member.role}`"
              @click="startEdit(entry.member, entry.review)"
            >
              <BaseIcon :name="entry.review ? 'edit' : 'plus'" :size="15" />
              {{ entry.review ? '수정' : '작성' }}
            </button>
          </header>

          <p v-if="editingRole !== entry.member.role" class="detail__review-body">
            {{ entry.review?.content || '아직 리뷰를 작성하지 않았어요.' }}
          </p>

          <form
            v-else
            class="detail__form"
            :data-testid="`review-form-${entry.member.role}`"
            @submit.prevent="submitReview"
          >
            <div class="lm-field">
              <label :for="`review-content-${entry.member.role}`">리뷰 본문</label>
              <textarea
                :id="`review-content-${entry.member.role}`"
                v-model="draft.content"
                :data-testid="`review-content-${entry.member.role}`"
                placeholder="이 곳은 어땠나요?"
              ></textarea>
            </div>

            <ScoreField
              v-for="field in SCORE_FIELDS"
              :key="field.key"
              :id="`review-${field.key}-${entry.member.role}`"
              :label="field.label"
              v-model="draft[field.key]"
            />

            <label class="detail__check">
              <input v-model="draft.revisitIntent" type="checkbox" />
              또 가고 싶어요
            </label>

            <div class="detail__actions">
              <button type="button" class="lm-btn lm-btn--quiet" @click="cancelEdit">취소</button>
              <button
                type="submit"
                class="lm-btn lm-btn--primary"
                :disabled="saving"
                :data-testid="`review-save-${entry.member.role}`"
              >
                {{ saving ? '저장 중…' : '리뷰 저장' }}
              </button>
            </div>
          </form>
        </article>
      </section>

      <footer class="detail__foot">
        <button
          type="button"
          class="lm-btn lm-btn--ghost"
          data-testid="detail-edit-place"
          @click="emit('edit', place.id)"
        >
          <BaseIcon name="edit" :size="16" />
          장소 정보 수정
        </button>
      </footer>
    </div>
  </aside>
</template>

<style scoped>
.detail {
  display: flex;
  flex-direction: column;
  width: 340px;
  max-height: 100%;
  background: var(--lm-card);
  border: 1px solid var(--lm-card-edge);
  border-radius: var(--lm-radius-lg);
  box-shadow: var(--lm-shadow-lift);
  overflow: hidden;
}

.detail__head {
  display: flex;
  align-items: center;
  gap: var(--lm-space-2);
  padding: var(--lm-space-4);
  background: var(--lm-pink-bg);
  border-bottom: 1px solid var(--lm-card-edge);
}
.detail__title {
  flex: 1;
  font-size: var(--lm-text-lg);
  color: var(--lm-ink);
}
.detail__close {
  display: grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  color: var(--lm-ink-soft);
  flex: none;
}
.detail__close:hover { background: #fff; color: var(--lm-pink); }

.detail__body {
  overflow-y: auto;
  padding: var(--lm-space-4);
  /* 우하단에 고정된 챗봇 마스코트가 마지막 버튼을 가리지 않도록 여유를 둡니다. */
  padding-bottom: 130px;
  display: flex;
  flex-direction: column;
  gap: var(--lm-space-4);
}

.detail__meta { display: flex; flex-direction: column; gap: var(--lm-space-2); }
.detail__row { display: flex; gap: var(--lm-space-3); font-size: var(--lm-text-sm); }
.detail__row dt { width: 56px; flex: none; color: var(--lm-ink-faint); }
.detail__row dd { margin: 0; color: var(--lm-ink); }
.detail__coord { font-variant-numeric: tabular-nums; }

.detail__score {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--lm-space-3);
  padding: var(--lm-space-3);
  background: var(--lm-pink-bg);
  border-radius: var(--lm-radius-sm);
}
.detail__score-label { font-size: var(--lm-text-sm); color: var(--lm-ink-soft); }

.detail__tags { display: flex; flex-wrap: wrap; gap: 5px; }
.detail__tags li {
  padding: 2px 9px;
  border-radius: 999px;
  background: var(--lm-header-bg);
  border: 1px solid var(--lm-card-edge);
  font-size: var(--lm-text-xs);
  color: var(--lm-ink-soft);
}

.detail__subtitle {
  font-size: var(--lm-text-sm);
  color: var(--lm-ink-soft);
  margin-bottom: var(--lm-space-2);
}
.detail__photo-list {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: var(--lm-space-2);
}
.detail__photo-list img {
  width: 100%;
  aspect-ratio: 1;
  object-fit: cover;
  border-radius: var(--lm-radius-sm);
  border: 1px solid var(--lm-card-edge);
}
.detail__no-photo { font-size: var(--lm-text-xs); color: var(--lm-ink-faint); }

.detail__reviews { display: flex; flex-direction: column; gap: var(--lm-space-3); }
.detail__review {
  padding: var(--lm-space-3);
  border: 1px solid var(--lm-card-edge);
  border-radius: var(--lm-radius-sm);
  background: var(--lm-header-bg);
}
.detail__review-head {
  display: flex;
  align-items: center;
  gap: var(--lm-space-2);
  margin-bottom: var(--lm-space-2);
}
.detail__badge {
  flex: 1;
  font-size: var(--lm-text-sm);
}
.detail__review[data-role='him'] .detail__badge { color: var(--lm-him); }
.detail__review[data-role='her'] .detail__badge { color: var(--lm-her); }
.detail__edit {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 9px;
  border: 1px solid var(--lm-card-edge);
  border-radius: 999px;
  font-size: var(--lm-text-xs);
  color: var(--lm-ink-soft);
  background: var(--lm-card);
}
.detail__edit:hover { border-color: var(--lm-pink-line); color: var(--lm-pink); }
.detail__review-body {
  font-size: var(--lm-text-sm);
  line-height: 1.7;
  color: var(--lm-ink);
  white-space: pre-wrap;
}

.detail__form { display: flex; flex-direction: column; gap: var(--lm-space-2); }
.detail__check {
  display: flex;
  align-items: center;
  gap: var(--lm-space-2);
  font-size: var(--lm-text-sm);
  color: var(--lm-ink-soft);
}
.detail__check input { accent-color: var(--lm-pink); }
.detail__actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--lm-space-2);
  margin-top: var(--lm-space-1);
}

.detail__foot {
  padding-top: var(--lm-space-3);
  border-top: 1px solid var(--lm-card-edge-soft);
}
.detail__foot .lm-btn { width: 100%; }

@media (max-width: 900px) {
  .detail { width: 100%; }
}
</style>
