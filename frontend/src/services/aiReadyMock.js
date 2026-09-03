/**
 * PRD v4.0의 AI 경계를 흉내 내는 프론트 전용 구현입니다.
 * 백엔드가 준비되면 이 모듈의 세 함수만 HTTP 어댑터로 교체합니다.
 */

const RULES = [
  { tag: '조용함', high: '조용함', low: '시끄러움', highWords: ['조용', '차분'], lowWords: ['시끄럽', '소음'] },
  { tag: '대화', high: '대화하기 좋음', low: '시끄러워 어려움', highWords: ['이야기하기 좋', '대화하기 좋'], lowWords: ['대화가 어렵'] },
  { tag: '웨이팅', high: '김', low: '짧음', highWords: ['웨이팅', '기다렸', '대기'], lowWords: ['바로 들어', '기다리지 않'] },
  { tag: '맵기', high: '매움', low: '순함', highWords: ['매워', '매운'], lowWords: ['안 매워', '순한'] },
  { tag: '사진', high: '잘 나옴', low: '안 나옴', highWords: ['사진이 예쁘', '사진이 잘'], lowWords: ['사진이 안'] },
  { tag: '가성비', high: '좋음', low: '나쁨', highWords: ['가성비가 좋', '가격도 좋'], lowWords: ['비싸', '부담스러'] },
]

function evidenceOf(text, words) {
  const word = words.find((candidate) => text.includes(candidate))
  if (!word) return null
  const index = text.indexOf(word)
  return text.slice(Math.max(0, index - 6), Math.min(text.length, index + word.length + 12)).trim()
}

export async function extractReviewTags(content) {
  const text = String(content ?? '').trim()
  const tags = []
  for (const rule of RULES) {
    const lowEvidence = evidenceOf(text, rule.lowWords)
    const highEvidence = evidenceOf(text, rule.highWords)
    const evidence = lowEvidence || highEvidence
    if (!evidence) continue
    const fact = lowEvidence ? rule.low : rule.high
    let want = fact
    if ((rule.tag === '웨이팅' && fact === '김') || (rule.tag === '가성비' && fact === '나쁨')) want = rule.low === '짧음' ? '짧음' : '좋음'
    if (rule.tag === '맵기' && fact === '매움' && /힘들|너무/.test(text)) want = '순함'
    tags.push({ tag: rule.tag, fact, want, evidence })
    if (tags.length === 5) break
  }
  return { tagStatus: 'COMPLETED', tags }
}

export function getCoupleTaste() {
  return {
    compatibility: 86,
    reviewedCount: 12,
    labels: [
      { tag: '조용함', label: 'ALL_SAME', side: '조용함', members: 2, detail: '두 사람 모두 조용한 공간을 선호해요.' },
      { tag: '웨이팅', label: 'ALL_SAME', side: '짧음', members: 2, detail: '두 사람 모두 짧은 대기를 원해요.' },
      { tag: '야경', label: 'ONE_SIDED', side: '좋음', members: 1, detail: '지민님에게서 2회 확인됐어요.' },
      { tag: '맵기', label: 'SPLIT', side: '도현→순함 · 지민→매움', members: 2, detail: '서로 원하는 쪽이 달라 추천 이유에 함께 표시해요.' },
    ],
  }
}

const CANDIDATES = [
  { placeId: 412, name: '라 비앙 로즈', category: '이탈리안', region: '인사동', basis: 'OTHERS', score: 4.8, matchedTags: ['조용함', '대화'], reason: '차분하게 이야기하기 좋은 공간으로 두 분의 공통 취향과 잘 맞아요.' },
  { placeId: 587, name: '디어 모먼트', category: '브런치 카페', region: '인사동', basis: 'OWN', score: 4.7, matchedTags: ['사진', '플레이팅'], reason: '사진이 잘 나오고 플레이팅이 예뻐 기록을 남기기 좋아요.' },
  { placeId: 633, name: '로즈 가든', category: '스테이크', region: '인사동', basis: 'OTHERS', score: 4.6, matchedTags: ['기념일', '조용함'], reason: '기념일에 어울리는 분위기이고 비교적 조용하다는 기록이 많아요.' },
]

export async function recommendPlaces(query) {
  const text = String(query ?? '').trim()
  const region = text.match(/(인사동|강남|홍대|성수|을지로)/)?.[1] ?? null
  const count = Math.min(5, Math.max(1, Number(text.match(/(\d+)\s*곳/)?.[1] ?? 3)))
  if (!region) return { status: 'NEEDS_REGION', message: '어느 동네인지 알려주세요.', places: [] }
  const places = CANDIDATES.filter((place) => place.region === region).slice(0, count)
  if (!places.length) return { status: 'EMPTY', message: '이 지역엔 아직 기록된 곳이 없습니다. 먼저 등록해보세요.', places: [] }
  return { status: 'COMPLETED', region, count, places }
}
