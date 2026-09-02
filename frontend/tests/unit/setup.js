import { beforeEach } from 'vitest'
import { clearAll } from '@/services/storageService.js'

/**
 * Node 26 + jsdom 조합에서는 window.localStorage가 노출되지 않습니다
 * (Node가 자체 실험적 localStorage 전역을 갖고 있어 jsdom 것이 가려집니다).
 * 앱 코드는 저장소가 없으면 메모리로 폴백하지만, 테스트에서는
 * "저장 → 새 인스턴스에서 복원" 을 실제로 검증해야 하므로 최소 구현을 채워 넣습니다.
 */
if (typeof window !== 'undefined' && !window.localStorage) {
  const store = new Map()
  Object.defineProperty(window, 'localStorage', {
    configurable: true,
    value: {
      getItem: (key) => (store.has(String(key)) ? store.get(String(key)) : null),
      setItem: (key, value) => store.set(String(key), String(value)),
      removeItem: (key) => store.delete(String(key)),
      clear: () => store.clear(),
      key: (index) => [...store.keys()][index] ?? null,
      get length() {
        return store.size
      },
    },
  })
}

// 테스트마다 저장소를 비워 케이스 간 데이터가 새지 않게 합니다.
beforeEach(() => {
  window.localStorage.clear()
  clearAll()
})
