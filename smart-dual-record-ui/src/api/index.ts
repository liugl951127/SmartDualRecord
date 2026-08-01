import axios, { type AxiosResponse } from 'axios'
import type {
  Business, StartBusinessRequest, NodeResult, QaResult,
  ScriptTemplate, ComplianceHit, OverviewData, NodeCode
} from '@/types'

const http = axios.create({
  baseURL: '/api/v1',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' }
})

// 响应拦截器：统一处理错误
http.interceptors.response.use(
  (r) => r,
  (err) => {
    const msg = err.response?.data?.message || err.message || '请求失败'
    console.error('[API Error]', msg, err.response?.data)
    return Promise.reject(new Error(msg))
  }
)

// ===== 录制主流程 =====
export const recordingApi = {
  startBusiness: (req: StartBusinessRequest) =>
    http.post<Business>('/recording/start', req).then(r => r.data),

  loadScript: (businessId: string, productId: string) =>
    http.post<ScriptTemplate>('/recording/script/load',
      null, { params: { businessId, productId } }).then(r => r.data),

  assessRisk: (businessId: string, customerIdHash: string) =>
    http.post<any>('/recording/risk/assess', null,
      { params: { businessId, customerIdHash } }).then(r => r.data),

  startRecording: (businessId: string) =>
    http.post<void>('/recording/begin', null, { params: { businessId } }),

  completeNode: (req: { businessId: string; recId: string; node: NodeCode; asrText: string }) =>
    http.post<NodeResult>('/recording/node/complete', req).then(r => r.data),

  finalize: (businessId: string, recId: string, fullAsrText: string) =>
    http.post<QaResult>('/recording/finalize', null,
      { params: { businessId, recId, fullAsrText } }).then(r => r.data),

  signAndArchive: (businessId: string) =>
    http.post<void>('/recording/sign', null, { params: { businessId } }),

  overview: (businessId: string) =>
    http.get<OverviewData>(`/recording/overview/${businessId}`).then(r => r.data),

  // ===== 新增 4 个扩展接口 (v1.1) =====
  emergencyStopAI: (operatorId: string, reason: string) =>
    http.post<void>('/recording/emergency-stop', null, { params: { operatorId, reason } }),

  linkRecordings: (primaryRecId: string, linkedRecId: string, operatorId: string) =>
    http.post<void>('/recording/link-recording', null,
      { params: { primaryRecId, linkedRecId, operatorId } }),

  auditReview: (businessId: string, auditorId: string) =>
    http.get<any>(`/recording/audit-review/${businessId}`, { params: { auditorId } }).then(r => r.data),

  realTimeCoaching: (businessId: string, asrSegment: string) =>
    http.post<{ coaching: string; latencyMs: number; degraded: boolean; modelVersion?: string; aiAppId?: string }>(
      '/recording/realtime-coaching', null, { params: { businessId, asrSegment } }).then(r => r.data),

  customerReplyWantsToCancel: (businessId: string, replyContent: string) =>
    http.post<void>('/recording/followup/wants-to-cancel', null,
      { params: { businessId, replyContent } }),

  // ===== v1.5 跨渠道补录 =====
  markOfflineFailed: (businessId: string, failedNode: string, reason: string, detail?: string) =>
    http.post<any>('/recording/offline-failed', null,
      { params: { businessId, failedNode, reason, detail } }).then(r => r.data),

  getResumeInfo: (token: string) =>
    http.get<any>('/recording/resume-info/' + token).then(r => r.data),

  completeResume: (businessId: string, token: string) =>
    http.post<any>('/recording/resume-complete', null,
      { params: { businessId, token } }).then(r => r.data)
}

// ===== 话术 =====
export const scriptApi = {
  getScript: (productId: string) =>
    http.get<ScriptTemplate>(`/script/${productId}`).then(r => r.data),

  allScripts: () =>
    http.get<Record<string, ScriptTemplate>>('/script/all').then(r => r.data),

  consistency: () =>
    http.get<string[]>('/script/consistency').then(r => r.data),

  isFrozen: (productId: string) =>
    http.get<boolean>(`/script/frozen/${productId}`).then(r => r.data)
}

// ===== 通用话术配置 =====
export const scriptConfigApi = {
  global: () =>
    http.get<any>('/script-config/global').then(r => r.data),

  product: (productId: string, productType?: string) =>
    http.get<ScriptTemplate>('/script-config/product/' + productId,
      { params: { productType } }).then(r => r.data),

  productSource: (productId: string, productType?: string) =>
    http.get<any>('/script-config/product/' + productId + '/source',
      { params: { productType } }).then(r => r.data),

  all: () =>
    http.get<Record<string, ScriptTemplate>>('/script-config/all').then(r => r.data),

  // ===== v1.4: 产品话术 CRUD (DB) =====
  listDbTemplates: () =>
    http.get<any[]>('/script-config/db-templates').then(r => r.data),

  getDbTemplate: (productId: string) =>
    http.get<any>('/script-config/db-template/' + productId).then(r => r.data),

  upsertDbTemplate: (template: any) =>
    http.post<any>('/script-config/db-template', template).then(r => r.data),

  deleteDbTemplate: (id: string) =>
    http.delete<any>('/script-config/db-template/' + id).then(r => r.data),

  submitForReview: (id: string) =>
    http.post<any>('/script-config/db-template/' + id + '/submit').then(r => r.data),

  approveTemplate: (id: string, approver: string) =>
    http.post<any>('/script-config/db-template/' + id + '/approve', null, { params: { approver } }).then(r => r.data),

  freezeTemplate: (id: string) =>
    http.post<any>('/script-config/db-template/' + id + '/freeze').then(r => r.data),

  addForbiddenPhrase: (phrase: string) =>
    http.post<any>('/script-config/forbidden-phrase', null, { params: { phrase } }).then(r => r.data),

  forbiddenPhrases: () =>
    http.get<string[]>('/script-config/forbidden-phrases').then(r => r.data)
}

// ===== 合规 =====
export const complianceApi = {
  scan: (text: string) =>
    http.post<ComplianceHit[]>('/compliance/scan', null, { params: { text } }).then(r => r.data)
}

// ===== 风险评估 =====
export const riskApi = {
  latest: (customerIdHash: string) =>
    http.get<any>(`/risk/latest/${customerIdHash}`).then(r => r.data),

  submit: (customerIdHash: string, answers: Record<string, any>) =>
    http.post<any>('/risk/submit', answers, { params: { customerIdHash } }).then(r => r.data),

  match: (customerLevel: string, productLevel: string) =>
    http.post<any>('/risk/match', null, { params: { customerLevel, productLevel } }).then(r => r.data)
}

// ===== 状态机 =====
export const stateMachineApi = {
  transitions: () =>
    http.get<Record<string, string[]>>('/statemachine/transitions').then(r => r.data),

  canTransition: (from: string, to: string) =>
    http.get<boolean>('/statemachine/can-transition', { params: { from, to } }).then(r => r.data),

  allNodes: () =>
    http.get<any[]>('/recording/nodes').then(r => r.data)
}

// ===== 健康 =====
export const healthApi = {
  check: () => http.get<any>('/health').then(r => r.data)
}

// ===== v1.2 录像合规 =====
export const recordingComplianceApi = {
  // 32 项检查
  check: (recId: string) =>
    http.post<any>('/recording-compliance/check', null, { params: { recId } }).then(r => r.data),
  checklist: () =>
    http.get<any[]>('/recording-compliance/checklist').then(r => r.data),

  // 事件标注
  annotate: (req: { recId: string; businessId: string; type: string; nodeId?: string; timestampMs: number; note?: string; operatorId?: string }) =>
    http.post<any>('/recording-compliance/annotate', null, { params: req }).then(r => r.data),
  listAnnotations: (recId: string) =>
    http.get<any[]>(`/recording-compliance/annotations/${recId}`).then(r => r.data),

  // 回放 + DRM
  playbackToken: (recId: string, userId: string, userRole: string, ttlSec: number = 300) =>
    http.post<any>('/recording-compliance/playback-token', null,
      { params: { recId, userId, userRole, ttlSec } }).then(r => r.data),
  accessLog: (recId: string) =>
    http.get<any[]>(`/recording-compliance/access-log/${recId}`).then(r => r.data),

  // 断点续传
  uploadInit: (req: { businessId: string; channel: string; totalChunks: number; totalSizeBytes: number; chunkSize?: number }) =>
    http.post<any>('/recording-compliance/upload/init', null, { params: req }).then(r => r.data),
  uploadChunk: (sessionId: string, chunkIndex: number, data?: Blob) =>
    http.post<any>('/recording-compliance/upload/chunk', data,
      { params: { sessionId, chunkIndex }, headers: { 'Content-Type': 'application/octet-stream' } }).then(r => r.data),
  uploadStatus: (sessionId: string) =>
    http.get<any>(`/recording-compliance/upload/status/${sessionId}`).then(r => r.data),
  uploadFinalize: (sessionId: string) =>
    http.post<{ recId: string }>(`/recording-compliance/upload/finalize/${sessionId}`).then(r => r.data),

  // 证据保全
  preservationSubmit: (recId: string, requesterId: string, requesterRole: string, reason: string) =>
    http.post<any>('/recording-compliance/preservation/submit', null,
      { params: { recId, requesterId, requesterRole, reason } }).then(r => r.data),
  preservationNotarize: (preservationId: string, notaryOrg: string, notaryCertNo: string) =>
    http.post<any>(`/recording-compliance/preservation/notarize/${preservationId}`, null,
      { params: { notaryOrg, notaryCertNo } }).then(r => r.data),
  preservationVerify: (preservationId: string) =>
    http.get<any>(`/recording-compliance/preservation/verify/${preservationId}`).then(r => r.data),
  preservationList: (recId: string) =>
    http.get<any[]>(`/recording-compliance/preservation/list/${recId}`).then(r => r.data),

  // 留存
  retentionScan: () => http.post<any>('/recording-compliance/retention/scan').then(r => r.data),
  retentionArchive: (beforeDate: string) =>
    http.post<any>('/recording-compliance/retention/archive', null, { params: { beforeDate } }).then(r => r.data)
}

export default http
