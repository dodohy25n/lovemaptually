/** 커플 프로필. 백엔드 연동 시 인증 정보로 대체될 자리입니다. */
export const COUPLE = {
  him: { userId: 'him', userName: '도현', role: 'him', label: '그의 리뷰', emoji: '🧢' },
  her: { userId: 'her', userName: '지우', role: 'her', label: '그녀의 리뷰', emoji: '🎀' },
}

export const COUPLE_MEMBERS = [COUPLE.him, COUPLE.her]

export function memberOf(userId) {
  return COUPLE_MEMBERS.find((member) => member.userId === userId) ?? null
}
