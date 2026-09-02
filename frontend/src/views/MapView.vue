<script setup>
import { ref, computed, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import CoupleSummary from '@/components/CoupleSummary.vue'
import HeartGradeLegend from '@/components/HeartGradeLegend.vue'
import RecentPlaces from '@/components/RecentPlaces.vue'
import MapCanvas from '@/components/MapCanvas.vue'
import CategoryFilter from '@/components/CategoryFilter.vue'
import PlaceDetailPanel from '@/components/PlaceDetailPanel.vue'
import PlaceFormModal from '@/components/PlaceFormModal.vue'
import EmptyState from '@/components/EmptyState.vue'
import BaseIcon from '@/components/BaseIcon.vue'
import { usePlacesStore } from '@/stores/places.js'

/** 메인 지도 화면. 데이터 처리는 전부 스토어가 맡고 이 화면은 조립만 합니다. */
const store = usePlacesStore()
const {
  places,
  visiblePlaces,
  recentPlaces,
  selectedPlace,
  selectedId,
  activeCategory,
  totalCount,
  isEmpty,
  loading,
  error,
  storageWarning,
} = storeToRefs(store)

const mapRef = ref(null)
const detailOpen = ref(false)
const formOpen = ref(false)
const picking = ref(false)
const pickedCoordinate = ref(null)
const editingPlace = ref(null)
const saving = ref(false)
const formError = ref(null)

const hasFilteredResult = computed(() => visiblePlaces.value.length > 0)

onMounted(() => store.load())

function selectPlace(id) {
  store.select(id)
  detailOpen.value = true
  mapRef.value?.focusPlace(id)
}

function closeDetail() {
  detailOpen.value = false
  store.clearSelection()
}

function openCreateForm() {
  editingPlace.value = null
  pickedCoordinate.value = null
  formError.value = null
  formOpen.value = true
}

function openEditForm(placeId) {
  editingPlace.value = places.value.find((place) => place.id === placeId) ?? null
  formError.value = null
  formOpen.value = true
}

function closeForm() {
  formOpen.value = false
  picking.value = false
  editingPlace.value = null
}

/** '지도에서 위치 찍기' — 모달을 잠시 접고 지도 클릭을 기다립니다. */
function requestPick() {
  picking.value = true
  formOpen.value = false
}

function onMapPick(coordinate) {
  pickedCoordinate.value = coordinate
  picking.value = false
  formOpen.value = true
}

async function submitForm(draft) {
  saving.value = true
  formError.value = null
  try {
    if (editingPlace.value) {
      await store.edit(editingPlace.value.id, draft)
    } else {
      const created = await store.add(draft)
      detailOpen.value = true
      mapRef.value?.focusPlace(created.id)
    }
    closeForm()
  } catch (err) {
    formError.value = err.message ?? '저장하지 못했어요.'
  } finally {
    saving.value = false
  }
}

async function saveReview({ placeId, review }) {
  saving.value = true
  try {
    await store.saveReview(placeId, review)
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <div class="mapview">
    <p v-if="storageWarning" class="mapview__warning" role="status" data-testid="storage-warning">
      {{ storageWarning }}
    </p>
    <p v-if="error" class="mapview__warning mapview__warning--error" role="alert">{{ error }}</p>

    <div class="mapview__layout">
      <aside class="mapview__side">
        <CoupleSummary :count="totalCount" />
        <HeartGradeLegend />
        <RecentPlaces
          :places="recentPlaces"
          :selected-id="selectedId"
          @select="selectPlace"
          @add="openCreateForm"
        />
      </aside>

      <div class="mapview__main">
        <MapCanvas
          ref="mapRef"
          :places="visiblePlaces"
          :selected-id="selectedId"
          :picking="picking"
          @select="selectPlace"
          @pick="onMapPick"
        >
          <div class="mapview__map-ui">
            <CategoryFilter :active="activeCategory" @change="store.setCategory" />
          </div>

          <button
            type="button"
            class="lm-btn lm-btn--primary mapview__add"
            data-testid="add-place"
            @click="openCreateForm"
          >
            <BaseIcon name="plus" :size="16" />
            장소 기록하기
          </button>

          <div v-if="isEmpty" class="mapview__empty">
            <EmptyState
              title="아직 우리의 러브맵이 비어 있어요"
              description="함께 다녀온 첫 장소를 지도에 남겨보세요."
              action-label="첫 장소 기록하기"
              @action="openCreateForm"
            />
          </div>

          <p
            v-else-if="!hasFilteredResult && !loading"
            class="mapview__no-result"
            role="status"
            data-testid="no-filter-result"
          >
            이 카테고리에는 아직 기록한 장소가 없어요.
          </p>
        </MapCanvas>

        <PlaceDetailPanel
          class="mapview__detail"
          :place="selectedPlace"
          :open="detailOpen"
          :saving="saving"
          @close="closeDetail"
          @edit="openEditForm"
          @save-review="saveReview"
          @add="openCreateForm"
        />
      </div>
    </div>

    <PlaceFormModal
      :open="formOpen"
      :place="editingPlace"
      :picked-coordinate="pickedCoordinate"
      :saving="saving"
      :error-message="formError"
      @submit="submitForm"
      @close="closeForm"
      @pick-request="requestPick"
    />
  </div>
</template>

<style scoped>
.mapview { display: flex; flex-direction: column; gap: var(--lm-space-3); }

.mapview__warning {
  padding: var(--lm-space-2) var(--lm-space-4);
  border-radius: var(--lm-radius-sm);
  background: var(--lm-pink-bg);
  border: 1px solid var(--lm-pink-line);
  font-size: var(--lm-text-sm);
  color: var(--lm-ink);
}
.mapview__warning--error { background: #fbe9e5; border-color: #e8bdb2; color: var(--lm-danger); }

.mapview__layout {
  display: grid;
  grid-template-columns: var(--lm-sidebar-w) minmax(0, 1fr);
  gap: var(--lm-gutter);
  align-items: start;
}

.mapview__side {
  display: flex;
  flex-direction: column;
  gap: var(--lm-space-4);
}

.mapview__main {
  position: relative;
  display: flex;
  gap: var(--lm-space-4);
  align-items: stretch;
  /* 와이어프레임의 지도 비율(프레임 1112 중 약 740)에 맞춘 높이 */
  min-height: 680px;
}
.mapview__main > :first-child { flex: 1; min-width: 0; }

/* 지도 위에 겹치는 UI. 지도 자체의 드래그를 막지 않도록 필요한 곳만 클릭을 받습니다. */
.mapview__map-ui {
  position: absolute;
  left: 50%;
  bottom: var(--lm-space-4);
  transform: translateX(-50%);
  z-index: var(--lm-z-map-ui);
}
.mapview__add {
  position: absolute;
  top: var(--lm-space-4);
  right: var(--lm-space-4);
  z-index: var(--lm-z-map-ui);
}
.mapview__empty,
.mapview__no-result {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  z-index: var(--lm-z-map-ui);
  background: var(--lm-card);
  border: 1px solid var(--lm-card-edge);
  border-radius: var(--lm-radius-lg);
  box-shadow: var(--lm-shadow-card);
}
.mapview__no-result {
  padding: var(--lm-space-3) var(--lm-space-5);
  font-size: var(--lm-text-sm);
  color: var(--lm-ink-soft);
}

.mapview__detail { flex: none; }

@media (max-width: 1100px) {
  .mapview__layout { grid-template-columns: 1fr; }
  .mapview__side {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  }
}
@media (max-width: 900px) {
  .mapview__main { flex-direction: column; }
  .mapview__detail { max-height: 60vh; }
}
</style>
