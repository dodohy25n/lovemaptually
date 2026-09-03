<script setup>
import { computed, ref } from 'vue'
import BaseIcon from './BaseIcon.vue'
import { isSearchAvailable, searchPlaces } from '@/services/placeSearchApi.js'

const props = defineProps({
  places: { type: Array, default: () => [] },
})

const emit = defineEmits(['select'])
const query = ref('')
const focused = ref(false)
const remoteResults = ref([])
const searching = ref(false)
const notice = ref('')

const normalizedQuery = computed(() => query.value.trim().toLocaleLowerCase('ko-KR'))
const localMatches = computed(() => {
  if (!normalizedQuery.value) return []
  return props.places.filter((place) =>
    [place.name, place.address, place.category, ...(place.tags ?? [])]
      .filter(Boolean)
      .some((value) => String(value).toLocaleLowerCase('ko-KR').includes(normalizedQuery.value)),
  ).slice(0, 5).map((place) => ({ ...place, saved: true }))
})
const matches = computed(() => remoteResults.value.length ? remoteResults.value : localMatches.value)
const showResults = computed(() => focused.value && Boolean(normalizedQuery.value))

function select(place) {
  query.value = place.name
  focused.value = false
  emit('select', place)
}

async function submit() {
  const keyword = query.value.trim()
  if (!keyword || searching.value) return
  focused.value = true
  notice.value = ''
  remoteResults.value = []
  if (!isSearchAvailable()) {
    notice.value = localMatches.value.length ? '' : '지도 검색 키가 없어 러브맵 기록에서만 찾고 있어요.'
    if (localMatches.value[0]) select(localMatches.value[0])
    return
  }
  searching.value = true
  try {
    remoteResults.value = (await searchPlaces(keyword, { size: 5 })).map((place) => ({ ...place, saved: false }))
    if (!remoteResults.value.length) notice.value = '검색 결과가 없어요.'
  } catch {
    notice.value = '지도 검색을 사용할 수 없어 러브맵 기록에서 찾았어요.'
    if (localMatches.value[0]) select(localMatches.value[0])
  } finally {
    searching.value = false
  }
}
</script>

<template>
  <div class="map-search">
    <form class="map-search__form" role="search" @submit.prevent="submit">
      <label class="lm-sr-only" for="map-place-search">지도 장소 검색</label>
      <input
        id="map-place-search"
        v-model="query"
        type="search"
        autocomplete="off"
        placeholder="장소를 검색해보세요"
        data-testid="map-search-input"
        @focus="focused = true"
        @keydown.esc="focused = false"
      />
      <span class="map-search__divider" aria-hidden="true"></span>
      <button type="submit" aria-label="지도 검색" data-testid="map-search-submit">
        <BaseIcon name="search" :size="31" />
      </button>
    </form>

    <div v-if="showResults" class="map-search__results">
      <button
        v-for="place in matches"
        :key="place.id"
        type="button"
        @mousedown.prevent="select(place)"
      >
        <span class="map-search__result-copy">
          <strong>{{ place.name }} <small>{{ place.saved ? '우리 기록' : '지도 검색' }}</small></strong>
          <span>{{ place.categoryName || place.category }}<template v-if="place.address"> · {{ place.address }}</template></span>
        </span>
        <span class="map-search__favorite" title="즐겨찾기 기능 준비 중" aria-hidden="true">
          <BaseIcon name="star" :size="23" />
        </span>
      </button>
      <p v-if="searching" role="status">가게를 검색하고 있어요…</p>
      <p v-else-if="notice" role="status">{{ notice }}</p>
      <p v-else-if="matches.length === 0" role="status">가게 이름을 입력하고 검색 버튼을 눌러주세요.</p>
    </div>
  </div>
</template>

<style scoped>
.map-search { position:relative;width:min(460px,calc(100vw - 40px)); }
.map-search__form { display:flex;align-items:center;height:64px;padding:0 13px 0 28px;border:1px solid rgba(247,199,202,.8);border-radius:999px;background:rgba(255,249,249,.96);box-shadow:0 8px 18px rgba(161,94,108,.2); }
.map-search input { flex:1;min-width:0;border:0;outline:0;background:transparent;color:var(--lm-ink);font:inherit;font-size:18px; }
.map-search input::placeholder { color:#d7b3b7; }
.map-search input::-webkit-search-cancel-button { display:none; }
.map-search__divider { width:2px;height:38px;margin:0 9px 0 14px;border-radius:99px;background:#efc6c9; }
.map-search button[type='submit'] { display:grid;place-items:center;width:46px;height:46px;border-radius:50%;color:#e3adb4;transition:background .15s,color .15s; }
.map-search button[type='submit']:hover { background:var(--lm-pink-bg);color:var(--lm-pink); }
.map-search__results { position:absolute;top:72px;left:12px;right:12px;overflow:hidden;padding:8px;border:1px solid var(--lm-pink-line);border-radius:18px;background:rgba(255,251,248,.98);box-shadow:0 14px 30px rgba(92,57,50,.18); }
.map-search__results button { display:flex;width:100%;align-items:center;gap:10px;padding:11px 14px;border-radius:11px;text-align:left; }
.map-search__results button:hover { background:var(--lm-pink-bg); }
.map-search__result-copy { display:flex;min-width:0;flex:1;flex-direction:column;gap:3px; }
.map-search__favorite { display:grid;place-items:center;flex:none;width:34px;height:34px;border-radius:50%;color:#e7a0ad;background:#fff4f3; }
.map-search__results strong { color:var(--lm-ink);font-size:14px; }
.map-search__results small { margin-left:5px;color:var(--lm-pink);font-size:9px;font-weight:700; }
.map-search__results span,.map-search__results p { color:var(--lm-ink-faint);font-size:11px; }
.map-search__results p { padding:14px;text-align:center; }
@media(max-width:700px){.map-search__form{height:52px;padding-left:20px}.map-search input{font-size:15px}.map-search__divider{height:30px}.map-search__results{top:60px}}
</style>
