import { COUPLE } from '@/utils/users.js'

/**
 * 저장된 데이터가 하나도 없을 때 사용하는 seed 데이터.
 *
 * 장소명·카테고리·지역·점수는 Figma 와이어프레임의 '최근 방문 장소' 예시를 그대로 따랐습니다.
 * 세부 점수는 커플 통합 점수가 각각 4.5 / 3.2 / 1.8 이 되도록 맞춰,
 * 세 가지 하트 등급(좋아요·보통이에요·아쉬워요)이 처음부터 모두 보이게 했습니다.
 *
 * 백엔드가 붙으면 이 파일은 더 이상 쓰이지 않습니다 (LocalPlaceRepository 전용).
 */
export function createSeedPlaces() {
  const now = '2026-03-01T09:00:00.000Z'
  return [
    {
      id: 'place_seed_dear_moment',
      name: '디어 모먼트',
      address: '서울 강남구 테헤란로 123',
      category: '카페',
      latitude: 37.4979,
      longitude: 127.0276,
      visitedAt: '2026-02-14',
      images: [],
      tags: ['첫 데이트', '창가석'],
      reviews: [
        {
          userId: COUPLE.him.userId,
          userName: COUPLE.him.userName,
          content: '창가 자리에서 두 시간 넘게 이야기했다. 커피는 산미가 강했지만 분위기가 다 덮어줬다.',
          atmosphere: 5,
          taste: 4.5,
          value: 4,
          service: 4.5,
          revisitIntent: true,
        },
        {
          userId: COUPLE.her.userId,
          userName: COUPLE.her.userName,
          content: '햇빛 들어오는 자리 최고. 디저트가 조금 달았지만 사진이 예쁘게 나와서 만족!',
          atmosphere: 4.5,
          taste: 4.5,
          value: 4.5,
          service: 4.5,
          revisitIntent: true,
        },
      ],
      createdAt: now,
      updatedAt: now,
    },
    {
      id: 'place_seed_romantic_garden',
      name: '로맨틱 가든',
      address: '서울 서초구 서초대로 77',
      category: '맛집',
      latitude: 37.4837,
      longitude: 127.0324,
      visitedAt: '2026-01-20',
      images: [],
      tags: ['기념일', '파스타'],
      reviews: [
        {
          userId: COUPLE.him.userId,
          userName: COUPLE.him.userName,
          content: '대기 40분은 길었지만 라구는 인정. 다만 가격대는 조금 부담스러웠다.',
          atmosphere: 3.5,
          taste: 4,
          value: 2.5,
          service: 3,
          revisitIntent: true,
        },
        {
          userId: COUPLE.her.userId,
          userName: COUPLE.her.userName,
          content: '소스는 훌륭한데 자리 간격이 좁아서 옆 테이블 소리가 다 들렸어요.',
          atmosphere: 3,
          taste: 3.5,
          value: 2.5,
          service: 3.5,
          revisitIntent: false,
        },
      ],
      createdAt: now,
      updatedAt: now,
    },
    {
      id: 'place_seed_moonlight_ramen',
      name: '달빛 라멘',
      address: '서울 마포구 양화로 45',
      category: '맛집',
      latitude: 37.5563,
      longitude: 126.9236,
      visitedAt: '2026-02-28',
      images: [],
      tags: ['야식', '웨이팅'],
      reviews: [
        {
          userId: COUPLE.him.userId,
          userName: COUPLE.him.userName,
          content: '국물이 미지근했다. 웨이팅 한 시간을 생각하면 아쉬움이 크다.',
          atmosphere: 2,
          taste: 1.5,
          value: 2,
          service: 1.5,
          revisitIntent: false,
        },
        {
          userId: COUPLE.her.userId,
          userName: COUPLE.her.userName,
          content: '면은 괜찮았는데 자리가 너무 좁고 시끄러웠어요. 다음엔 포장으로!',
          atmosphere: 2,
          taste: 1.5,
          value: 2,
          service: 1.5,
          revisitIntent: false,
        },
      ],
      createdAt: now,
      updatedAt: now,
    },
  ]
}
