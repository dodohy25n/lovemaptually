/** 충돌 가능성이 낮은 로컬 ID 생성기. crypto.randomUUID가 없으면 폴백을 씁니다. */
export function createId(prefix = 'place') {
  const uuid =
    typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
      ? crypto.randomUUID()
      : `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`
  return `${prefix}_${uuid}`
}
