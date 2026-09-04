import { afterEach, describe, expect, it, vi } from 'vitest'
import { config } from '@/services/config.js'
import {
  createReport,
  fetchGroupReports,
  generateReport,
  pollReport,
  subscribePremium,
  toMonthKey,
} from '@/services/reportApi.js'
import { STORAGE_KEYS, writeText } from '@/services/storageService.js'

const originalBaseUrl = config.apiBaseUrl

afterEach(() => {
  config.apiBaseUrl = originalBaseUrl
})

function signIn() {
  config.apiBaseUrl = 'https://api.example.test'
  writeText(STORAGE_KEYS.accessToken, 'signed-token')
}

function jsonResponse(body, { ok = true, status = 200 } = {}) {
  return { ok, status, json: async () => body }
}

const ACCEPTED = jsonResponse(
  { status: 202, message: '리포트를 쓰고 있습니다', data: { reportId: 4, groupId: 1, reportMonth: '2026-08-01', status: 'PENDING', createdAt: '2026-09-04T02:00:53Z' } },
  { status: 202 },
)

const PENDING = jsonResponse({
  status: 200,
  data: { reportId: 4, groupId: 1, reportMonth: '2026-08-01', status: 'PENDING', content: null },
})

const COMPLETED = jsonResponse({
  status: 200,
  data: {
    reportId: 4,
    groupId: 1,
    reportMonth: '2026-08-01',
    status: 'COMPLETED',
    requestedByUserId: 1,
    content: {
      title: '2026년 8월 커플 데이트 기록',
      summary: '총 10곳을 방문하였습니다.',
      highlights: [{ placeId: 49, name: '골목밥상', why: '재료가 신선하였습니다.' }],
      tasteShift: [{ tag: '맵기', direction: '순함', evidence: '순한 맛을 골랐습니다.' }],
      splitTags: [{ tag: '맵기', memberA: '순함', memberB: '매움' }],
      nextMonth: [{ placeId: 4, name: '쌍계약과점', reason: '기대됩니다.' }],
      closingLine: '다음 달도 기대합니다.',
      meta: { model: 'gpt-4o-mini', discarded: 0 },
    },
  },
})

describe('월간 리포트 API', () => {
  it('reportMonth를 YYYY-MM으로 줄인다', () => {
    expect(toMonthKey('2026-08-01')).toBe('2026-08')
    expect(toMonthKey('2026-08')).toBe('2026-08')
    expect(toMonthKey('')).toBe('')
  })

  it('GET /api/groups/{id}/reports로 요금제와 목록을 읽는다', async () => {
    signIn()
    const fetchImpl = vi.fn().mockResolvedValue(jsonResponse({
      status: 200,
      data: {
        plan: 'PREMIUM',
        reports: [{ reportId: 4, reportMonth: '2026-08-01', status: 'COMPLETED', title: '8월', summary: '요약', createdAt: null, completedAt: null }],
      },
    }))

    await expect(fetchGroupReports(1, { fetchImpl })).resolves.toMatchObject({
      plan: 'PREMIUM',
      reports: [{ reportId: 4, reportMonth: '2026-08', status: 'COMPLETED' }],
    })
    expect(fetchImpl).toHaveBeenCalledWith(
      new URL('https://api.example.test/api/groups/1/reports'),
      expect.objectContaining({ method: 'GET' }),
    )
  })

  it('POST /api/groups/{id}/reports에 month를 YYYY-MM으로 보낸다', async () => {
    signIn()
    const fetchImpl = vi.fn().mockResolvedValue(ACCEPTED)

    await expect(createReport(1, '2026-08', { fetchImpl })).resolves.toMatchObject({ reportId: 4, status: 'PENDING' })
    expect(fetchImpl).toHaveBeenCalledWith(
      new URL('https://api.example.test/api/groups/1/reports'),
      expect.objectContaining({ method: 'POST', body: JSON.stringify({ month: '2026-08' }) }),
    )
  })

  it('402는 plan_required로 바꾼다', async () => {
    signIn()
    const fetchImpl = vi.fn().mockResolvedValue(jsonResponse(
      { status: 402, message: '프리미엄 구독이 필요합니다', error: { code: 'PLAN_REQUIRED', details: [] } },
      { ok: false, status: 402 },
    ))
    await expect(createReport(1, '2026-08', { fetchImpl })).rejects.toMatchObject({ code: 'plan_required' })
  })

  it('409는 report_already_exists로 바꾸고 기존 reportId를 함께 담는다', async () => {
    signIn()
    const fetchImpl = vi.fn().mockResolvedValue(jsonResponse(
      {
        status: 409,
        message: '2026-08 리포트가 이미 있습니다',
        error: { code: 'REPORT_ALREADY_EXISTS', details: [{ field: 'month', reason: 'reportId=512' }] },
      },
      { ok: false, status: 409 },
    ))
    await expect(createReport(1, '2026-08', { fetchImpl })).rejects.toMatchObject({
      code: 'report_already_exists',
      existingReportId: 512,
    })
  })

  it('422는 no_visits_in_month로 바꾼다', async () => {
    signIn()
    const fetchImpl = vi.fn().mockResolvedValue(jsonResponse(
      { status: 422, message: '방문 기록이 없습니다', error: { code: 'NO_VISITS_IN_MONTH', details: [] } },
      { ok: false, status: 422 },
    ))
    await expect(createReport(1, '2026-08', { fetchImpl })).rejects.toMatchObject({ code: 'no_visits_in_month' })
  })

  it('PENDING이 끝날 때까지 폴링하고 COMPLETED가 되면 본문을 돌려준다', async () => {
    signIn()
    const fetchImpl = vi.fn()
      .mockResolvedValueOnce(ACCEPTED)
      .mockResolvedValueOnce(PENDING)
      .mockResolvedValueOnce(PENDING)
      .mockResolvedValueOnce(COMPLETED)

    const report = await generateReport(1, '2026-08', { fetchImpl, intervalMs: 0 })

    expect(report.status).toBe('COMPLETED')
    expect(report.reportMonth).toBe('2026-08')
    expect(report.content.splitTags).toEqual([{ tag: '맵기', memberA: '순함', memberB: '매움' }])
    expect(fetchImpl).toHaveBeenCalledTimes(4)
  })

  it('409를 만나면 새로 만들지 않고 기존 리포트를 연다', async () => {
    signIn()
    const conflict = jsonResponse(
      {
        status: 409,
        message: '2026-08 리포트가 이미 있습니다',
        error: { code: 'REPORT_ALREADY_EXISTS', details: [{ field: 'month', reason: 'reportId=4' }] },
      },
      { ok: false, status: 409 },
    )
    const fetchImpl = vi.fn().mockResolvedValueOnce(conflict).mockResolvedValueOnce(COMPLETED)

    const report = await generateReport(1, '2026-08', { fetchImpl, intervalMs: 0 })

    expect(report.reportId).toBe(4)
    expect(fetchImpl).toHaveBeenNthCalledWith(
      2,
      new URL('https://api.example.test/api/reports/4'),
      expect.objectContaining({ method: 'GET' }),
    )
  })

  it('정해진 횟수 안에 끝나지 않으면 timeout으로 실패한다', async () => {
    signIn()
    const fetchImpl = vi.fn().mockResolvedValue(PENDING)

    await expect(pollReport(4, { fetchImpl, intervalMs: 0, maxAttempts: 3 })).rejects.toMatchObject({
      code: 'timeout',
      message: '리포트를 쓰는 데 시간이 오래 걸리고 있습니다. 잠시 뒤에 다시 열어주세요.',
    })
    expect(fetchImpl).toHaveBeenCalledTimes(4)
  })

  it('구독은 PREMIUM으로 요청하고, 이미 프리미엄이면 성공으로 넘긴다', async () => {
    signIn()
    const created = vi.fn().mockResolvedValue(jsonResponse(
      { status: 201, data: { subscriptionId: 7, groupId: 2, plan: 'PREMIUM', startedAt: null, paymentRef: 'demo' } },
      { status: 201 },
    ))
    await expect(subscribePremium(2, { fetchImpl: created })).resolves.toMatchObject({ plan: 'PREMIUM', alreadyPremium: false })
    expect(created).toHaveBeenCalledWith(
      new URL('https://api.example.test/api/groups/2/subscriptions'),
      expect.objectContaining({ method: 'POST', body: JSON.stringify({ plan: 'PREMIUM' }) }),
    )

    const conflict = vi.fn().mockResolvedValue(jsonResponse(
      { status: 409, message: '이미 프리미엄입니다', error: { code: 'ALREADY_PREMIUM', details: [] } },
      { ok: false, status: 409 },
    ))
    await expect(subscribePremium(2, { fetchImpl: conflict })).resolves.toMatchObject({ plan: 'PREMIUM', alreadyPremium: true })
  })

  it('토큰이 없으면 네트워크 요청 전에 auth_required로 실패한다', async () => {
    config.apiBaseUrl = 'https://api.example.test'
    const fetchImpl = vi.fn()
    await expect(fetchGroupReports(1, { fetchImpl })).rejects.toMatchObject({ code: 'auth_required' })
    expect(fetchImpl).not.toHaveBeenCalled()
  })
})
