<script setup>
import { computed, ref } from 'vue'
import HeartRating from './HeartRating.vue'
import EmptyState from './EmptyState.vue'
import BaseIcon from './BaseIcon.vue'

/** '최근 방문 장소' 목록 카드. 기본 3개만 보여주고 '더 보기'로 펼칩니다. */
const props = defineProps({
  places: { type: Array, default: () => [] },
  selectedId: { type: String, default: null },
  collapsedCount: { type: Number, default: 3 },
})

const emit = defineEmits(['select', 'add'])

const expanded = ref(false)

const visible = computed(() =>
  expanded.value ? props.places : props.places.slice(0, props.collapsedCount),
)
const hasMore = computed(() => props.places.length > props.collapsedCount)

/** '카페 / 강남구' 형태의 보조 텍스트. 주소에서 구 단위만 뽑습니다. */
function subtitleOf(place) {
  const district = place.address?.split(' ').find((token) => /[구군시]$/.test(token))
  return district ? `${place.category} / ${district}` : place.category
}
</script>

<template>
  <section class="recent lm-card" aria-labelledby="recent-title">
    <h2 id="recent-title" class="lm-card__title">최근 방문 장소</h2>

    <EmptyState
      v-if="places.length === 0"
      compact
      title="아직 기록한 장소가 없어요"
      description="지도를 눌러 두 사람의 첫 장소를 남겨보세요."
      action-label="장소 기록하기"
      @action="emit('add')"
    />

    <template v-else>
      <ul class="recent__list">
        <li v-for="place in visible" :key="place.id">
          <button
            type="button"
            class="recent__item"
            :class="{ 'recent__item--active': place.id === selectedId }"
            :aria-current="place.id === selectedId ? 'true' : undefined"
            @click="emit('select', place.id)"
          >
            <img
              class="recent__thumb"
              :src="place.images[0] || '/assets/photo-placeholder.svg'"
              :alt="place.images.length ? `${place.name} 사진` : ''"
              width="42"
              height="42"
            />
            <span class="recent__body">
              <span class="recent__name">{{ place.name }}</span>
              <span class="recent__sub">{{ subtitleOf(place) }}</span>
            </span>
            <HeartRating :score="place.coupleScore" :size="18" />
          </button>
        </li>
      </ul>

      <button
        v-if="hasMore"
        type="button"
        class="recent__more"
        :aria-expanded="expanded"
        @click="expanded = !expanded"
      >
        {{ expanded ? '접기' : '더 보기' }}
        <BaseIcon name="chevron" :size="16" :class="{ 'recent__chevron--up': expanded }" />
      </button>
    </template>
  </section>
</template>

<style scoped>
.recent {
  padding: var(--lm-space-4);
  display: flex;
  flex-direction: column;
  gap: var(--lm-space-3);
}
.recent__list {
  display: flex;
  flex-direction: column;
}
.recent__list > li + li { border-top: 1px solid var(--lm-card-edge-soft); }

.recent__item {
  display: flex;
  align-items: center;
  gap: var(--lm-space-3);
  width: 100%;
  padding: var(--lm-space-3) var(--lm-space-1);
  text-align: left;
  border-radius: var(--lm-radius-sm);
}
.recent__item:hover { background: var(--lm-pink-bg); }
.recent__item--active { background: var(--lm-pink-bg-2); }

.recent__thumb {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  object-fit: cover;
  background: var(--lm-paper-shade);
  flex: none;
}
.recent__body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
  flex: 1;
}
.recent__name {
  font-size: var(--lm-text-md);
  color: var(--lm-ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.recent__sub {
  font-size: var(--lm-text-xs);
  color: var(--lm-ink-faint);
}

.recent__more {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--lm-space-2);
  padding: 9px;
  border: 1px solid var(--lm-card-edge);
  border-radius: var(--lm-radius-sm);
  background: var(--lm-header-bg);
  font-size: var(--lm-text-sm);
  color: var(--lm-ink-soft);
}
.recent__more:hover { border-color: var(--lm-pink-line); color: var(--lm-pink); }
.recent__chevron--up { transform: rotate(180deg); }
</style>
