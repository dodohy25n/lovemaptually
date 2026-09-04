<script setup>
import { ref, computed, nextTick, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import CoupleSummary from '@/components/CoupleSummary.vue'
import RecentPlaces from '@/components/RecentPlaces.vue'
import MapCanvas from '@/components/MapCanvas.vue'
import CategoryFilter from '@/components/CategoryFilter.vue'
import MapSearchBar from '@/components/MapSearchBar.vue'
import PlaceFormModal from '@/components/PlaceFormModal.vue'
import EmptyState from '@/components/EmptyState.vue'
import CoupleTasteModal from '@/components/CoupleTasteModal.vue'
import RecommendationModal from '@/components/RecommendationModal.vue'
import ReviewCarouselModal from '@/components/ReviewCarouselModal.vue'
import FloatingNotebookMenu from '@/components/FloatingNotebookMenu.vue'
import { usePlacesStore } from '@/stores/places.js'
import { createGroup, fetchMyGroups } from '@/services/groupApi.js'
import heartFlourish from '../../frontend-assets/decorations/love_maptually_heart_flourish.png'
import pinkTape from '../../frontend-assets/decorations/love_maptually_pink_tape.png'

/** 메인 지도 화면. 데이터 처리는 전부 스토어가 맡고 이 화면은 조립만 합니다. */
const store = usePlacesStore()
const {
  places,
  visiblePlaces,
  recentPlaces,
  selectedId,
  activeCategory,
  totalCount,
  isEmpty,
  loading,
  error,
  storageWarning,
} = storeToRefs(store)

const mapRef = ref(null)
const formOpen = ref(false)
const picking = ref(false)
const pickedCoordinate = ref(null)
// 폼을 '새로 여는' 순간에만 증가합니다. 위치 찍기로 잠시 접었다 펴는 것은 같은 세션이라
// 이 값이 그대로여서, 사용자가 입력하던 내용이 지워지지 않습니다.
const formSession = ref(0)
const editingPlace = ref(null)
const saving = ref(false)
const formError = ref(null)
const tasteOpen = ref(false)
const recommendationOpen = ref(false)
const reviewOpen = ref(false)
const reviewRole = ref('him')
const searchedPlace = ref(null)
const groupName = ref('')
const currentGroupId = ref(null)
const groupLoaded = ref(false)
const hasGroup = ref(false)
const creatingGroup = ref(false)
const groupError = ref('')

const hasFilteredResult = computed(() => visiblePlaces.value.length > 0)

onMounted(() => {
  store.load()
  fetchMyGroups()
    .then((groups) => {
      const primary = groups.find((group) => group.type === 'COUPLE') ?? groups[0]
      currentGroupId.value = primary?.id ?? null
      hasGroup.value = groups.length > 0
      groupName.value = primary?.name ?? ''
      groupLoaded.value = true
    })
    .catch((err) => {
      if (err?.code !== 'auth_required') console.warn('그룹 목록을 불러오지 못했습니다.', err)
    })
})

async function createCoupleGroup() {
  if (creatingGroup.value) return
  groupError.value = ''
  creatingGroup.value = true
  try {
    const group = await createGroup({ groupType: 'COUPLE', name: '우리 둘' })
    hasGroup.value = true
    groupName.value = group.name
  } catch (err) {
    groupError.value = err?.message || '커플 러브맵을 만들지 못했습니다.'
  } finally {
    creatingGroup.value = false
  }
}

function selectPlace(id) {
  store.select(id)
  reviewRole.value = 'him'
  reviewOpen.value = true
  mapRef.value?.focusPlace(id)
}

async function selectSearchResult(place) {
  if (!place.saved) {
    searchedPlace.value = place
    await nextTick()
    mapRef.value?.focusSearchPlace(place)
    return
  }
  store.setCategory('all')
  await nextTick()
  selectPlace(place.id)
}

function openCreateForm() {
  editingPlace.value = null
  pickedCoordinate.value = null
  formError.value = null
  formSession.value += 1
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
      mapRef.value?.focusPlace(created.id)
    }
    closeForm()
  } catch (err) {
    formError.value = err.message ?? '저장하지 못했어요.'
  } finally {
    saving.value = false
  }
}

</script>

<template>
  <div class="mapview">
    <img class="mapview__decor mapview__decor--heart" :src="heartFlourish" alt="" aria-hidden="true" />
    <img class="mapview__decor mapview__decor--tape" :src="pinkTape" alt="" aria-hidden="true" />
    <p v-if="storageWarning" class="mapview__warning" role="status" data-testid="storage-warning">
      {{ storageWarning }}
    </p>
    <p v-if="error" class="mapview__warning mapview__warning--error" role="alert">{{ error }}</p>
    <p v-if="groupError" class="mapview__warning mapview__warning--error" role="alert">{{ groupError }}</p>

    <div class="mapview__layout">
      <MapCanvas
        ref="mapRef"
        :places="visiblePlaces"
        :selected-id="selectedId"
        :picking="picking"
        :searched-place="searchedPlace"
        :show-route="activeCategory === 'route'"
        @select="selectPlace"
        @pick="onMapPick"
      >
        <aside class="mapview__side" aria-label="러브맵 정보">
        <CoupleSummary
          :count="totalCount"
          :group-name="groupName"
          :can-create-group="groupLoaded && !hasGroup"
          :creating-group="creatingGroup"
          @create-group="createCoupleGroup"
        />
        <RecentPlaces
          :places="recentPlaces"
          :selected-id="selectedId"
          @select="selectPlace"
          @add="openCreateForm"
        />
          <FloatingNotebookMenu
            class="mapview__notebook"
            @taste="tasteOpen = true"
            @add-place="openCreateForm"
            @recommend="recommendationOpen = true"
          />
        </aside>

          <div class="mapview__map-ui">
            <CategoryFilter :active="activeCategory" @change="store.setCategory" />
          </div>

          <MapSearchBar
            class="mapview__search"
            :places="places"
            @select="selectSearchResult"
          />

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

    </div>

    <PlaceFormModal
      :open="formOpen"
      :place="editingPlace"
      :form-session="formSession"
      :picked-coordinate="pickedCoordinate"
      :saving="saving"
      :error-message="formError"
      @submit="submitForm"
      @close="closeForm"
      @pick-request="requestPick"
    />
    <CoupleTasteModal :open="tasteOpen" @close="tasteOpen = false" @recommend="tasteOpen = false; recommendationOpen = true" />
    <RecommendationModal :open="recommendationOpen" @close="recommendationOpen = false" />
    <ReviewCarouselModal
      :open="reviewOpen"
      :initial-role="reviewRole"
      :place-id="selectedId"
      :group-id="currentGroupId"
      @close="reviewOpen = false"
    />
  </div>
</template>

<style scoped>
.mapview {
  position: relative;
  isolation: isolate;
  width: 100%;
  height: 100%;
  overflow: hidden;
  background: var(--lm-map-bg);
}
.mapview__decor {
  position: absolute;
  z-index: 2;
  pointer-events: none;
  user-select: none;
}
.mapview__decor--heart {
  left: -55px;
  bottom: -75px;
  width: 220px;
  opacity: 0.08;
  transform: rotate(-11deg);
}
.mapview__decor--tape {
  top: -70px;
  left: 305px;
  width: 190px;
  opacity: 0.22;
  transform: rotate(8deg);
}

.mapview__warning {
  position: absolute;
  z-index: calc(var(--lm-z-map-ui) + 3);
  top: 18px;
  left: 50%;
  transform: translateX(-50%);
  padding: var(--lm-space-2) var(--lm-space-4);
  border-radius: var(--lm-radius-sm);
  background: var(--lm-pink-bg);
  border: 1px solid var(--lm-pink-line);
  font-size: var(--lm-text-sm);
  color: var(--lm-ink);
}
.mapview__warning--error { background: #fbe9e5; border-color: #e8bdb2; color: var(--lm-danger); }

.mapview__layout {
  position: absolute;
  inset: 0;
}
.mapview__layout > :first-child { width:100%;height:100%;min-height:0;border:0;border-radius:0;box-shadow:none; }

.mapview__side {
  position: absolute;
  z-index: calc(var(--lm-z-map-ui) + 1);
  top: 22px;
  left: 22px;
  display: flex;
  flex-direction: column;
  gap: 14px;
  width: 320px;
  height: calc(100% - 44px);
  overflow-y: auto;
  scrollbar-width: none;
}
.mapview__side::-webkit-scrollbar { display:none; }
.mapview__side :deep(.summary),.mapview__side :deep(.recent){flex:1 1 0;min-height:210px;background:rgba(255,251,246,.94);backdrop-filter:blur(12px);box-shadow:0 10px 30px rgba(86,57,49,.16)}
.mapview__side :deep(.summary__title),.mapview__side :deep(.lm-card__title){font-size:18px}
.mapview__side :deep(.summary__avatar){width:136px!important;height:136px!important}
.mapview__side :deep(.summary__label),.mapview__side :deep(.recent__name){font-size:15px}
.mapview__side :deep(.recent__thumb){width:54px;height:54px}

.mapview__map-ui {
  position: absolute;
  left: 50%;
  bottom: 24px;
  transform: translateX(-50%);
  z-index: calc(var(--lm-z-map-ui) + 1);
}
.mapview__search{position:absolute;z-index:calc(var(--lm-z-map-ui) + 3);top:24px;left:50%;transform:translateX(-50%)}
.mapview__map-ui :deep(.filter){gap:16px;padding:20px 28px;border-radius:28px;background:rgba(255,251,246,.95);backdrop-filter:blur(12px);box-shadow:0 10px 30px rgba(86,57,49,.18)}
.mapview__map-ui :deep(.filter__item){gap:8px;min-width:104px;padding:10px 14px;border-radius:16px}
.mapview__map-ui :deep(.filter__item .lm-icon){width:40px;height:40px}
.mapview__map-ui :deep(.filter__label){font-size:18px}
.mapview__notebook{position:relative;display:flex;flex:1.45 1 0;flex-direction:column;width:100%;min-height:300px;z-index:calc(var(--lm-z-map-ui) + 2)}
.mapview__notebook :deep(.notebook__grid){flex:1}
.mapview__notebook :deep(.notebook__item){min-height:0}
.mapview__notebook :deep(.notebook__title){font-size:26px}
.mapview__notebook :deep(.notebook__icon){width:48px;height:48px}
.mapview__notebook :deep(.notebook__item){font-size:14px}
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

@media (max-width: 900px) {
  .mapview__side{top:14px;left:14px;width:280px;height:calc(100% - 28px)}
  .mapview__map-ui{bottom:14px;max-width:calc(100% - 28px);overflow-x:auto}
  .mapview__map-ui :deep(.filter){gap:8px;padding:14px 18px}
  .mapview__map-ui :deep(.filter__item){min-width:78px;padding:7px 10px}
  .mapview__map-ui :deep(.filter__item .lm-icon){width:32px;height:32px}
  .mapview__map-ui :deep(.filter__label){font-size:14px}
  .mapview__search{top:14px;left:auto;right:14px;transform:none;width:min(410px,calc(100% - 322px))}
}
@media (max-width: 560px) {
  .mapview__side{top:12px;left:12px;width:220px;height:calc(100% - 100px)}
  .mapview__search{top:12px;right:12px;width:calc(100% - 256px);min-width:180px}
  .mapview__side :deep(.summary),.mapview__side :deep(.recent){min-height:190px}
  .mapview__notebook{min-height:300px}
  .mapview__map-ui :deep(.filter){gap:4px;padding:10px}
  .mapview__map-ui :deep(.filter__item){min-width:62px;padding:5px 7px}
  .mapview__map-ui :deep(.filter__item .lm-icon){width:27px;height:27px}
  .mapview__map-ui :deep(.filter__label){font-size:12px}
}
</style>
