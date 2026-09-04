import { reactive } from 'vue'

/**
 * 그룹 구성원 프로필.
 *
 * 'me' 는 로그인한 사용자, 'partner' 는 함께 기록하는 다른 구성원입니다.
 * 표시 이름은 닉네임에서 나오므로 성별을 전제하지 않습니다.
 *
 * 내부 식별자(userId)는 로컬 모드의 저장 데이터와 묶여 있어 그대로 둡니다.
 * api 모드에서는 로그인 후 applyMemberNames() 가 실제 닉네임으로 덮어씁니다.
 */
const MEMBERS = reactive({
  him: { userId: 'him', userName: '도현', role: 'him' },
  her: { userId: 'her', userName: '용민', role: 'her' },
})

/** 화면에 쓰는 이름표. 닉네임이 바뀌면 함께 바뀝니다. */
function decorate(member) {
  return {
    get userId() { return member.userId },
    get userName() { return member.userName },
    get role() { return member.role },
    get label() { return `${member.userName}의 기억` },
  }
}

export const COUPLE = {
  him: decorate(MEMBERS.him),
  her: decorate(MEMBERS.her),
}

export const COUPLE_MEMBERS = [COUPLE.him, COUPLE.her]

/**
 * GET /api/groups/me 의 members 를 화면 이름에 반영합니다.
 * 로그인한 사용자가 'me', 나머지 구성원이 'partner' 자리에 놓입니다.
 * currentUserId 를 모르면 합류 순서를 그대로 씁니다.
 * 구성원이 한 명뿐이면 남은 자리는 기본 이름을 유지합니다.
 */
export function applyMemberNames(members = [], currentUserId = null) {
  const list = Array.isArray(members) ? [...members] : []
  if (currentUserId != null) {
    const index = list.findIndex((member) => String(member?.userId) === String(currentUserId))
    if (index > 0) list.unshift(list.splice(index, 1)[0])
  }
  const [mine, partner] = list
  if (mine?.nickname) MEMBERS.him.userName = String(mine.nickname)
  if (partner?.nickname) MEMBERS.her.userName = String(partner.nickname)
}

export function memberOf(userId) {
  return COUPLE_MEMBERS.find((member) => member.userId === userId) ?? null
}
