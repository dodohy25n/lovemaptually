import { config } from './config.js'
import { readText, STORAGE_KEYS } from './storageService.js'

/**
 * 월간 리포트 API 어댑터.
 *
 * 리포트 생성도 비동기라 추천과 같은 구조입니다.
 *   1. POST /api/groups/{groupId}/reports → 202 {reportId, status:'PENDING'}
 *   2. GET  /api/reports/{reportId} → status 가 PENDING 을 벗어날 때까지 폴링
 *
 * 실패는 코드로 구분해 화면이 분기할 수 있게 합니다.
 *   plan_required            프리미엄 구독이 필요합니다 (402)
 *   report_already_exists    이미 만든 리포트가 있습니다 (409, existingReportId 를 함께 담습니다)
 *   no_visits_in_month       그 달에 방문 기록이 없습니다 (422)
 */

export const POLL_INTERVAL_MS = 500
export const POLL_MAX_ATTEMPTS = 20

export class ReportApiError extends Error {
  constructor(message, code = 'report_api_error', { existingReportId = null } = {}) {
    super(message)
    this.name = 'ReportApiError'
    this.code = code
    this.existingReportId = existingReportId
  }
}

function requireApiContext(fetchImpl) {
  if (!config.apiBaseUrl) {
    throw new ReportApiError('API 기본 주소가 설정되지 않았습니다.', 'missing_base_url')
  }
  const token = readText(STORAGE_KEYS.accessToken)
  if (!token) {
    throw new ReportApiError('로그인이 필요합니다.', 'auth_required')
  }
  if (typeof fetchImpl !== 'function') {
    throw new ReportApiError('이 환경에서는 리포트 요청을 보낼 수 없습니다.', 'fetch_unavailable')
  }
  return { token }
}

/** 백엔드는 reportMonth 를 '2026-08-01' 로 돌려주므로 화면이 쓰는 'YYYY-MM' 으로 줄입니다. */
export function toMonthKey(value) {
  const matched = /^(\d{4})-(\d{2})/.exec(String(value ?? ''))
  return matched ? `${matched[1]}-${matched[2]}` : ''
}

/** 409 응답의 details[0].reason 은 'reportId=512' 형식입니다. */
function existingReportIdFrom(payload) {
  for (const detail of payload?.error?.details ?? []) {
    const matched = /reportId=(\d+)/.exec(String(detail?.reason ?? ''))
    if (matched) return Number(matched[1])
  }
  return null
}

function normalizeSummary(report) {
  if (report?.reportId == null) return null
  return {
    reportId: report.reportId,
    reportMonth: toMonthKey(report.reportMonth),
    status: String(report.status ?? 'PENDING').toUpperCase(),
    title: report.title ?? '',
    summary: report.summary ?? '',
    createdAt: report.createdAt ?? null,
    completedAt: report.completedAt ?? null,
  }
}

function normalizeContent(content) {
  if (!content) return null
  const list = (value) => (Array.isArray(value) ? value : [])
  return {
    title: String(content.title ?? ''),
    summary: String(content.summary ?? ''),
    highlights: list(content.highlights),
    tasteShift: list(content.tasteShift),
    splitTags: list(content.splitTags),
    nextMonth: list(content.nextMonth),
    closingLine: String(content.closingLine ?? ''),
    meta: content.meta ?? null,
  }
}

function normalizeReport(data) {
  return {
    reportId: data?.reportId ?? null,
    groupId: data?.groupId ?? null,
    reportMonth: toMonthKey(data?.reportMonth),
    status: String(data?.status ?? 'PENDING').toUpperCase(),
    requestedByUserId: data?.requestedByUserId ?? null,
    createdAt: data?.createdAt ?? null,
    completedAt: data?.completedAt ?? null,
    content: normalizeContent(data?.content),
  }
}

/** GET /api/groups/{groupId}/reports — 요금제와 지금까지 만든 리포트 목록. */
export async function fetchGroupReports(groupId, { fetchImpl = globalThis.fetch } = {}) {
  const { token } = requireApiContext(fetchImpl)
  if (groupId == null) {
    throw new ReportApiError('그룹을 먼저 선택해주세요.', 'group_required')
  }

  const url = new URL(`/api/groups/${groupId}/reports`, config.apiBaseUrl)
  let response
  try {
    response = await fetchImpl(url, {
      method: 'GET',
      headers: { Accept: 'application/json', Authorization: `Bearer ${token}` },
    })
  } catch {
    throw new ReportApiError('리포트 목록을 불러오지 못했습니다.', 'network_error')
  }

  const payload = await response.json().catch(() => null)
  if (!response.ok) {
    throw new ReportApiError(
      payload?.message || '리포트 목록을 불러오지 못했습니다.',
      payload?.error?.code || (response.status === 401 ? 'auth_required' : 'http_error'),
    )
  }
  if (!Array.isArray(payload?.data?.reports)) {
    throw new ReportApiError('리포트 목록 응답 형식이 올바르지 않습니다.', 'invalid_response')
  }

  return {
    plan: String(payload.data.plan ?? '').toUpperCase(),
    reports: payload.data.reports.map(normalizeSummary).filter(Boolean),
  }
}

/** POST /api/groups/{groupId}/reports — 그 달 리포트 생성을 요청합니다. */
export async function createReport(groupId, month, { fetchImpl = globalThis.fetch } = {}) {
  const { token } = requireApiContext(fetchImpl)
  if (groupId == null) {
    throw new ReportApiError('그룹을 먼저 선택해주세요.', 'group_required')
  }
  const monthKey = toMonthKey(month)
  if (!monthKey) {
    throw new ReportApiError('리포트를 만들 달을 골라주세요.', 'invalid_month')
  }

  const url = new URL(`/api/groups/${groupId}/reports`, config.apiBaseUrl)
  let response
  try {
    response = await fetchImpl(url, {
      method: 'POST',
      headers: {
        Accept: 'application/json',
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ month: monthKey }),
    })
  } catch {
    throw new ReportApiError('리포트 서버에 연결하지 못했습니다.', 'network_error')
  }

  const payload = await response.json().catch(() => null)
  if (!response.ok) {
    const backendCode = payload?.error?.code
    if (response.status === 402 || backendCode === 'PLAN_REQUIRED') {
      throw new ReportApiError(
        payload?.message || '월간 리포트는 프리미엄 구독이 필요합니다.',
        'plan_required',
      )
    }
    if (response.status === 409 || backendCode === 'REPORT_ALREADY_EXISTS') {
      throw new ReportApiError(
        payload?.message || '이미 만들어진 리포트가 있습니다.',
        'report_already_exists',
        { existingReportId: existingReportIdFrom(payload) },
      )
    }
    if (response.status === 422 || backendCode === 'NO_VISITS_IN_MONTH') {
      throw new ReportApiError(
        payload?.message || '그 달에는 방문 기록이 없습니다.',
        'no_visits_in_month',
      )
    }
    throw new ReportApiError(
      payload?.message || '리포트를 만들지 못했습니다.',
      backendCode || (response.status === 401 ? 'auth_required' : 'http_error'),
    )
  }
  if (payload?.data?.reportId == null) {
    throw new ReportApiError('리포트 생성 응답 형식이 올바르지 않습니다.', 'invalid_response')
  }

  return {
    reportId: payload.data.reportId,
    groupId: payload.data.groupId ?? groupId,
    reportMonth: toMonthKey(payload.data.reportMonth) || monthKey,
    status: String(payload.data.status ?? 'PENDING').toUpperCase(),
    createdAt: payload.data.createdAt ?? null,
  }
}

/** GET /api/reports/{reportId} — 리포트 한 건. PENDING 이면 content 는 null 입니다. */
export async function fetchReport(reportId, { fetchImpl = globalThis.fetch } = {}) {
  const { token } = requireApiContext(fetchImpl)

  const url = new URL(`/api/reports/${reportId}`, config.apiBaseUrl)
  let response
  try {
    response = await fetchImpl(url, {
      method: 'GET',
      headers: { Accept: 'application/json', Authorization: `Bearer ${token}` },
    })
  } catch {
    throw new ReportApiError('리포트를 불러오지 못했습니다.', 'network_error')
  }

  const payload = await response.json().catch(() => null)
  if (!response.ok) {
    throw new ReportApiError(
      payload?.message || '리포트를 불러오지 못했습니다.',
      payload?.error?.code || (response.status === 401 ? 'auth_required' : 'http_error'),
    )
  }
  if (!payload?.data) {
    throw new ReportApiError('리포트 응답 형식이 올바르지 않습니다.', 'invalid_response')
  }

  return normalizeReport(payload.data)
}

const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

/** 리포트 한 건이 PENDING 을 벗어날 때까지 폴링합니다. */
export async function pollReport(
  reportId,
  { fetchImpl = globalThis.fetch, intervalMs = POLL_INTERVAL_MS, maxAttempts = POLL_MAX_ATTEMPTS } = {},
) {
  const first = await fetchReport(reportId, { fetchImpl })
  if (first.status !== 'PENDING') return first

  for (let attempt = 0; attempt < maxAttempts; attempt += 1) {
    await wait(intervalMs)
    const report = await fetchReport(reportId, { fetchImpl })
    if (report.status !== 'PENDING') return report
  }

  throw new ReportApiError(
    '리포트를 쓰는 데 시간이 오래 걸리고 있습니다. 잠시 뒤에 다시 열어주세요.',
    'timeout',
  )
}

/**
 * 리포트를 만들고 완성될 때까지 기다립니다.
 * 이미 그 달 리포트가 있으면(409) 새로 만들지 않고 기존 리포트를 그대로 엽니다.
 */
export async function generateReport(
  groupId,
  month,
  { fetchImpl = globalThis.fetch, intervalMs = POLL_INTERVAL_MS, maxAttempts = POLL_MAX_ATTEMPTS, onPending } = {},
) {
  let created
  try {
    created = await createReport(groupId, month, { fetchImpl })
  } catch (error) {
    if (error?.code === 'report_already_exists' && error.existingReportId != null) {
      return pollReport(error.existingReportId, { fetchImpl, intervalMs, maxAttempts })
    }
    throw error
  }
  if (typeof onPending === 'function') onPending(created)
  return pollReport(created.reportId, { fetchImpl, intervalMs, maxAttempts })
}

/** POST /api/groups/{groupId}/subscriptions — 그룹을 프리미엄으로 올립니다. */
export async function subscribePremium(groupId, { fetchImpl = globalThis.fetch } = {}) {
  const { token } = requireApiContext(fetchImpl)
  if (groupId == null) {
    throw new ReportApiError('그룹을 먼저 선택해주세요.', 'group_required')
  }

  const url = new URL(`/api/groups/${groupId}/subscriptions`, config.apiBaseUrl)
  let response
  try {
    response = await fetchImpl(url, {
      method: 'POST',
      headers: {
        Accept: 'application/json',
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ plan: 'PREMIUM' }),
    })
  } catch {
    throw new ReportApiError('구독 서버에 연결하지 못했습니다.', 'network_error')
  }

  const payload = await response.json().catch(() => null)
  if (!response.ok) {
    // 이미 프리미엄이면 화면 입장에서는 성공과 같으므로 그대로 통과시킵니다.
    if (response.status === 409 || payload?.error?.code === 'ALREADY_PREMIUM') {
      return { groupId, plan: 'PREMIUM', alreadyPremium: true }
    }
    throw new ReportApiError(
      payload?.message || '프리미엄으로 전환하지 못했습니다.',
      payload?.error?.code || 'http_error',
    )
  }
  if (!payload?.data) {
    throw new ReportApiError('구독 응답 형식이 올바르지 않습니다.', 'invalid_response')
  }

  return {
    subscriptionId: payload.data.subscriptionId ?? null,
    groupId: payload.data.groupId ?? groupId,
    plan: String(payload.data.plan ?? 'PREMIUM').toUpperCase(),
    startedAt: payload.data.startedAt ?? null,
    paymentRef: payload.data.paymentRef ?? null,
    alreadyPremium: false,
  }
}
