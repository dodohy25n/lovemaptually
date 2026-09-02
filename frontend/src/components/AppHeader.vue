<script setup>
import BaseIcon from './BaseIcon.vue'

/**
 * 공통 헤더 — 스프링 제본 장식이 달린 종이 노트 상단.
 * 메뉴는 요구사항 화면(메인 지도 / 리뷰 / 기억)에 맞춰 구성했습니다.
 */
const NAV_ITEMS = [
  { label: '홈', to: '/' },
  { label: '그의 리뷰', to: '/reviews/him' },
  { label: '그녀의 리뷰', to: '/reviews/her' },
  { label: '우리의 기억', to: '/memories' },
]
</script>

<template>
  <header class="header">
    <!-- 스프링 제본 구멍. 순수 장식이라 스크린리더에서 숨깁니다. -->
    <div class="header__spring" aria-hidden="true"></div>

    <div class="header__inner">
      <RouterLink to="/" class="brand" aria-label="Love Maptually 홈으로">
        <span class="brand__name">Love Maptually</span>
        <span class="brand__sub">러브 맵츄얼리</span>
      </RouterLink>

      <nav class="nav" aria-label="주요 메뉴">
        <RouterLink
          v-for="item in NAV_ITEMS"
          :key="item.to"
          :to="item.to"
          class="nav__link"
        >
          {{ item.label }}
        </RouterLink>
      </nav>

      <div class="actions">
        <button type="button" class="actions__icon" aria-label="장소 검색">
          <BaseIcon name="search" :size="21" />
        </button>
        <button type="button" class="actions__icon" aria-label="알림 보기">
          <BaseIcon name="bell" :size="21" />
        </button>
        <button type="button" class="actions__avatar" aria-label="내 프로필">
          <BaseIcon name="user" :size="20" />
        </button>
      </div>
    </div>
  </header>
</template>

<style scoped>
.header {
  position: relative;
  background: var(--lm-header-bg);
  border-bottom: 1px solid var(--lm-card-edge);
  border-radius: 0 0 var(--lm-radius-lg) var(--lm-radius-lg);
  box-shadow: var(--lm-shadow-card);
}

/* 스프링 구멍: 이미지 대신 CSS로 그려 폭에 상관없이 균일하게 반복됩니다. */
.header__spring {
  height: 20px;
  background-image: radial-gradient(
    circle at 14px 10px,
    var(--lm-spring-dot) 0 5px,
    transparent 5.5px
  );
  background-size: 34px 20px;
  background-repeat: repeat-x;
  background-position: center top;
}

.header__inner {
  display: flex;
  align-items: center;
  gap: var(--lm-space-5);
  height: calc(var(--lm-header-h) - 20px);
  padding: 0 var(--lm-space-6);
  max-width: var(--lm-frame-max);
  margin: 0 auto;
}

.brand {
  display: flex;
  flex-direction: column;
  gap: 1px;
  text-decoration: none;
  flex: none;
}
.brand__name {
  font-family: var(--lm-font-logo);
  font-size: 30px;
  line-height: 1.1;
  color: var(--lm-pink);
}
.brand__sub {
  font-size: var(--lm-text-xs);
  color: var(--lm-pink-soft);
  letter-spacing: 0.06em;
  padding-left: 2px;
}

.nav {
  display: flex;
  align-items: center;
  gap: var(--lm-space-6);
  margin: 0 auto;
}
.nav__link {
  position: relative;
  padding: 6px 2px;
  text-decoration: none;
  font-size: var(--lm-text-md);
  color: var(--lm-ink-soft);
  white-space: nowrap;
}
.nav__link:hover { color: var(--lm-ink); }
/* 활성 메뉴는 색상만이 아니라 밑줄로도 구분합니다. */
.nav__link.router-link-active { color: var(--lm-pink); }
.nav__link.router-link-active::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: -2px;
  height: 2px;
  background: var(--lm-pink);
  border-radius: 2px;
}

.actions {
  display: flex;
  align-items: center;
  gap: var(--lm-space-4);
  flex: none;
}
.actions__icon {
  color: var(--lm-ink-soft);
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 50%;
}
.actions__icon:hover { color: var(--lm-pink); background: var(--lm-pink-bg); }
.actions__avatar {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  border-radius: 50%;
  background: var(--lm-pink-soft);
  color: #fff;
}
.actions__avatar:hover { background: var(--lm-pink); }

@media (max-width: 900px) {
  .header__inner {
    flex-wrap: wrap;
    height: auto;
    padding: var(--lm-space-3) var(--lm-space-4);
    gap: var(--lm-space-3);
  }
  .nav {
    order: 3;
    width: 100%;
    margin: 0;
    gap: var(--lm-space-4);
    overflow-x: auto;
  }
  .actions { margin-left: auto; }
}
</style>
