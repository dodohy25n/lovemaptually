<script setup>
/**
 * 인터페이스용 라인 아이콘 모음.
 * 장식 이미지가 아니라 UI 아이콘이므로 SVG를 인라인으로 둡니다
 * (currentColor를 따라가게 하기 위해서입니다).
 */
defineProps({
  name: { type: String, required: true },
  size: { type: [Number, String], default: 20 },
})

const PATHS = {
  search: 'M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14zm5.5 12.5L21 21',
  bell: 'M12 3a5 5 0 0 0-5 5c0 4-1.5 5.5-2 6h14c-.5-.5-2-2-2-6a5 5 0 0 0-5-5zm-2 14a2 2 0 0 0 4 0',
  user: 'M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8zm-7 8c0-3.3 3.1-6 7-6s7 2.7 7 6',
  pin: 'M12 21s7-6.3 7-11a7 7 0 1 0-14 0c0 4.7 7 11 7 11zm0-13.5a2.5 2.5 0 1 1 0 5 2.5 2.5 0 0 1 0-5z',
  cutlery: 'M7 3v8m0 0v10m0-10a3 3 0 0 0 3-3V3M7 11a3 3 0 0 1-3-3V3m13 0c-1.7 0-3 2.2-3 5s1.3 4 3 4v9',
  cup: 'M4 8h11v6a4 4 0 0 1-4 4H8a4 4 0 0 1-4-4V8zm11 1h2.5a2.5 2.5 0 0 1 0 5H15M4 21h12',
  heart: 'M12 20s-7.5-4.9-7.5-9.6A4.5 4.5 0 0 1 12 7.6a4.5 4.5 0 0 1 7.5 2.8C19.5 15.1 12 20 12 20z',
  star: 'M12 4l2.4 5 5.6.8-4 3.9 1 5.5-5-2.6-5 2.6 1-5.5-4-3.9 5.6-.8L12 4z',
  chat: 'M4 6.5A2.5 2.5 0 0 1 6.5 4h11A2.5 2.5 0 0 1 20 6.5v7a2.5 2.5 0 0 1-2.5 2.5H12l-5 4v-4H6.5A2.5 2.5 0 0 1 4 13.5v-7z',
  close: 'M6 6l12 12M18 6L6 18',
  chevron: 'M6 9l6 6 6-6',
  plus: 'M12 5v14M5 12h14',
  send: 'M4 12l16-8-6 8 6 8-16-8z',
  camera: 'M4 8h3l1.5-2h7L17 8h3v11H4V8zm8 3a3.5 3.5 0 1 0 0 7 3.5 3.5 0 0 0 0-7z',
  edit: 'M4 20h4L19 9l-4-4L4 16v4zM15 5l4 4',
  trash: 'M5 7h14M10 7V5h4v2m-6 0v12h8V7',
}
</script>

<template>
  <svg
    class="lm-icon"
    :width="size"
    :height="size"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.7"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    focusable="false"
  >
    <path :d="PATHS[name]" />
  </svg>
</template>

<style scoped>
.lm-icon { display: block; flex: none; }
</style>
