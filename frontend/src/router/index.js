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
    path: '/reviews/him',
    name: 'reviews-him',
    component: () => import('@/views/ReviewsView.vue'),
    props: { role: 'him' },
    meta: { title: '그의 기억' },
  },
  {
    path: '/reviews/her',
    name: 'reviews-her',
    component: () => import('@/views/ReviewsView.vue'),
    props: { role: 'her' },
    meta: { title: '그녀의 기억' },
  },
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
