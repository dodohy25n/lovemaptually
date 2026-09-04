import { createRouter, createWebHistory } from 'vue-router'
import MapView from '@/views/MapView.vue'

/**
 * 라우트는 요구사항 화면을 기준으로 구성했습니다.
 * (장소 상세와 챗봇은 별도 페이지가 아니라 지도 화면 위의 패널입니다.)
 */
const routes = [
  { path: '/', redirect: '/login' },
  { path: '/map', name: 'home', component: MapView, meta: { title: '홈' } },
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/LoginView.vue'),
    meta: { title: '로그인', authLayout: true },
  },
  {
    path: '/signup',
    name: 'signup',
    component: () => import('@/views/SignupView.vue'),
    meta: { title: '회원가입', authLayout: true },
  },
  {
    path: '/reviews/me',
    name: 'reviews-me',
    component: () => import('@/views/ReviewsView.vue'),
    props: { role: 'him' },
    meta: { title: '나의 기억' },
  },
  {
    path: '/reviews/partner',
    name: 'reviews-partner',
    component: () => import('@/views/ReviewsView.vue'),
    props: { role: 'her' },
    meta: { title: '상대의 기억' },
  },
  // 옛 주소는 새 주소로 넘겨 북마크와 기존 링크가 깨지지 않게 합니다.
  { path: '/reviews/him', redirect: '/reviews/me' },
  { path: '/reviews/her', redirect: '/reviews/partner' },
  {
    path: '/memories',
    name: 'memories',
    component: () => import('@/views/MemoriesView.vue'),
    meta: { title: '추억 저장소' },
  },
  { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('@/views/NotFoundView.vue') },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

router.afterEach((to) => {
  const suffix = to.meta?.title ? ` · ${to.meta.title}` : ''
  document.title = `Love Maptually${suffix}`
})

export default router
