import { config, isMockAi } from './config.js'

/**
 * 챗봇('러비') API 어댑터.
 *
 * 지금은 정해진 mock 응답만 돌려줍니다.
 * VITE_AI_MODE=api 로 바꾸고 sendToAi()에 fetch를 채우면
 * ChatbotPanel 컴포넌트는 그대로 두고 실제 AI로 교체됩니다.
 *
 * 반환 형식 (고정 계약):
 *   ChatMessage = { id, role: 'user' | 'bot', text, createdAt }
 */

let sequence = 0

function makeMessage(role, text) {
  sequence += 1
  return {
    id: `msg_${Date.now()}_${sequence}`,
    role,
    text,
    createdAt: new Date().toISOString(),
  }
}

export function userMessage(text) {
  return makeMessage('user', text)
}

export function botMessage(text) {
  return makeMessage('bot', text)
}

/** 패널을 처음 열었을 때 러비가 먼저 건네는 인사. */
export function greeting() {
  return botMessage('안녕! 나는 러비야 🦝 우리 러브맵에 대해 궁금한 게 있으면 무엇이든 물어봐줘!')
}

/** 키워드 → 정해진 답변. 실제 AI로 교체되기 전까지 쓰는 규칙입니다. */
const MOCK_RULES = [
  {
    match: /추천|어디\s*(가|갈)|갈까|데이트\s*코스/,
    reply: '이번 주말엔 점수 4점 이상인 곳부터 다시 가보는 건 어때? 지도에서 꽉 찬 하트만 골라보면 금방 찾을 수 있어!',
  },
  {
    match: /점수|평점|하트|등급/,
    reply: '점수는 두 사람 리뷰의 평균이야. 4.0 이상이면 꽉 찬 하트, 2.0~3.9는 반쪽 하트, 그 아래는 깨진 하트로 표시돼!',
  },
  {
    match: /등록|추가|기록|저장/,
    reply: '지도를 클릭해서 위치를 고르고 장소 정보를 채우면 바로 기록돼. 새로고침해도 그대로 남아 있으니 안심해!',
  },
  {
    match: /리뷰|후기|한줄/,
    reply: '장소를 누르면 두 사람의 리뷰를 각각 볼 수 있어. 분위기·맛·가성비·서비스 네 가지로 나눠서 적을 수 있지!',
  },
  {
    match: /안녕|하이|반가|누구/,
    reply: '반가워! 나는 러브맵 안내를 도와주는 너구리 러비야 🦝',
  },
]

const FALLBACK_REPLY =
  '아직 내가 배우는 중이라 그건 잘 모르겠어! 지도에 기록한 장소, 점수, 리뷰에 대해서는 무엇이든 물어봐줘 💕'

/** mock 응답 규칙. 테스트에서 기대값을 맞추기 위해 export 합니다. */
export function mockReplyFor(text) {
  const input = String(text ?? '')
  const rule = MOCK_RULES.find((r) => r.match.test(input))
  return rule ? rule.reply : FALLBACK_REPLY
}

/**
 * 메시지를 보내고 답변 하나를 받습니다.
 * mock 모드에서는 외부 네트워크를 전혀 타지 않으므로 테스트가 네트워크에 의존하지 않습니다.
 */
export async function sendMessage(text, { delay = 0 } = {}) {
  if (isMockAi()) {
    if (delay > 0) {
      await new Promise((resolve) => setTimeout(resolve, delay))
    }
    return botMessage(mockReplyFor(text))
  }
  return sendToAi(text)
}

/** 실제 AI 백엔드 연결 지점. 아직 구현하지 않습니다. */
async function sendToAi(_text) {
  throw new Error(
    `챗봇 API는 백엔드 구현 후 연결됩니다. (예정 엔드포인트: ${config.apiBaseUrl || '/api'}/chat)`,
  )
}
