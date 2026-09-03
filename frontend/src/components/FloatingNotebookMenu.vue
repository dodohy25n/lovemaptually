<script setup>
import BaseIcon from './BaseIcon.vue'
import coupleProfile from '@/assets/couple-profile.jpeg'

defineEmits(['taste', 'add-place', 'recommend'])
</script>

<template>
  <nav class="notebook" aria-label="러브맵 바로가기">
    <span class="notebook__rings" aria-hidden="true"></span>
    <p class="notebook__title">Love Menu <span aria-hidden="true">♡</span></p>
    <div class="notebook__grid">
      <RouterLink to="/memories" class="notebook__item">
        <span class="notebook__icon"><BaseIcon name="heart" :size="22" /></span>
        <span>추억 저장소</span>
      </RouterLink>
      <button class="notebook__item" type="button" data-testid="open-taste" @click="$emit('taste')">
        <span class="notebook__icon"><BaseIcon name="star" :size="22" /></span>
        <span>우리 취향 보기</span>
      </button>
      <button class="notebook__item" type="button" data-testid="add-place" @click="$emit('add-place')">
        <span class="notebook__icon"><BaseIcon name="plus" :size="22" /></span>
        <span>장소 기록하기</span>
      </button>
      <RouterLink to="/login" class="notebook__item" aria-label="로그아웃">
        <span class="notebook__icon notebook__icon--profile">
          <img :src="coupleProfile" alt="" />
        </span>
        <span>로그아웃</span>
      </RouterLink>
      <button
        class="notebook__item notebook__item--recommend"
        type="button"
        data-testid="open-recommendation"
        @click="$emit('recommend')"
      >
        <span class="notebook__icon"><BaseIcon name="chat" :size="22" /></span>
        <span>러비에게 추천받기</span>
      </button>
    </div>
  </nav>
</template>

<style scoped>
.notebook {
  position: relative;
  width: 286px;
  padding: 31px 16px 17px;
  overflow: visible;
  border: 1px solid rgba(224, 185, 176, .86);
  border-radius: 16px 16px 20px 20px;
  background:
    linear-gradient(rgba(255, 251, 246, .91), rgba(255, 251, 246, .91)),
    url('../../frontend-assets/decorations/love_maptually_paper_texture.png') center / 420px;
  box-shadow: 0 13px 34px rgba(77, 50, 43, .2);
  color: var(--lm-ink);
}
.notebook::after {
  content: '';
  position: absolute;
  inset: 8px;
  border: 1px dashed rgba(237, 126, 145, .2);
  border-radius: 11px;
  pointer-events: none;
}
.notebook__rings {
  position: absolute;
  z-index: 2;
  top: -9px;
  left: 13px;
  right: 13px;
  height: 27px;
  background:
    radial-gradient(circle at 12px 18px, #9f6d65 0 4px, #f9e6dd 4.5px 6px, transparent 6.5px),
    linear-gradient(90deg, transparent 9px, #bd7e74 9px 12px, transparent 12px) 0 0 / 30px 22px;
  background-size: 30px 26px;
  background-repeat: repeat-x;
  pointer-events: none;
}
.notebook__title {
  position: relative;
  z-index: 1;
  margin-bottom: 12px;
  text-align: center;
  color: var(--lm-pink);
  font-family: var(--lm-font-logo);
  font-size: 22px;
}
.notebook__grid {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 9px;
}
.notebook__item {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 7px;
  min-height: 82px;
  padding: 10px 7px;
  border: 1px solid rgba(231, 202, 194, .9);
  border-radius: 12px;
  background: rgba(255, 255, 255, .68);
  text-decoration: none;
  color: var(--lm-ink-soft);
  font-size: 12px;
  font-weight: 700;
  box-shadow: 0 3px 9px rgba(102, 72, 63, .06);
  transition: transform .15s ease, border-color .15s ease, color .15s ease, background .15s ease;
}
.notebook__item:hover {
  transform: translateY(-2px);
  border-color: var(--lm-pink-line);
  background: var(--lm-pink-bg);
  color: var(--lm-pink);
}
.notebook__item--recommend {
  grid-column: 1 / -1;
  min-height: 60px;
  flex-direction: row;
  border-color: var(--lm-pink-line);
  background: var(--lm-pink-bg);
  color: var(--lm-pink);
  font-size: 13px;
}
.notebook__item--recommend .notebook__icon { width:34px;height:34px;background:#fff; }
.notebook__icon {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: 50%;
  background: var(--lm-pink-bg-2);
  color: var(--lm-pink);
}
.notebook__icon--profile {
  overflow: hidden;
  border: 2px solid #fff;
  box-shadow: 0 0 0 1px var(--lm-pink-line);
}
.notebook__icon--profile img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
@media(max-width:620px){.notebook{width:218px;padding:27px 11px 12px}.notebook__title{font-size:18px;margin-bottom:8px}.notebook__grid{gap:6px}.notebook__item{min-height:60px;font-size:10px}.notebook__item--recommend{min-height:46px}.notebook__icon{width:31px;height:31px}}
</style>
