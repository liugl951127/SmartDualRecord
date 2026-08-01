import axios, { type AxiosInstance, type AxiosResponse } from 'axios'
import { showToast } from 'vant'

const http: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api/v1',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' }
})

// 请求拦截
http.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截
http.interceptors.response.use(
  (res: AxiosResponse) => res.data,
  (err) => {
    const msg = err.response?.data?.message || err.message || '请求失败'
    if (err.response?.status === 401) {
      localStorage.removeItem('token')
      showToast('登录已过期')
      setTimeout(() => location.href = '/#/login', 500)
    } else if (err.response?.status >= 500) {
      showToast(`服务异常: ${msg}`)
    }
    return Promise.reject(err)
  }
)

// ==================== 业务/双录 API ====================
export const recordingApi = {
  // 创建业务并启动双录
  startBusiness: (data: {
    businessType: string
    productId: string
    customerIdHash: string
    sellerIdHash?: string
    channel: string
    sellerType?: string
    amount?: number
  }) => http.post('/recording/start', data),

  // 加载话术
  loadScript: (businessId: string, productId: string) =>
    http.post('/recording/script/load', null, { params: { businessId, productId } }),

  // 风险评估
  assessRisk: (businessId: string, params: {
    customerIdHash: string
    answers: Record<string, any>
  }) => http.post('/recording/risk/assess', null, {
    params: { businessId, customerIdHash: params.customerIdHash }
  }),

  // 开始录制
  beginRecording: (businessId: string) => http.post('/recording/begin', null, { params: { businessId } }),

  // 完成一个节点
  completeNode: (businessId: string, data: {
    recId: string
    node: string
    asrText: string
  }) => http.post('/recording/node/complete', { businessId, ...data }),

  // 完成双录 (签字后)
  finalize: (businessId: string, recId: string, fullAsrText: string) =>
    http.post('/recording/finalize', null, { params: { businessId, recId, fullAsrText } }),

  // 签合同
  sign: (businessId: string) => http.post('/recording/sign', null, { params: { businessId } }),

  // 业务总览
  overview: (businessId: string) => http.get(`/recording/overview/${businessId}`),

  // 8 节点定义
  nodes: () => http.get('/recording/nodes'),

  // 跨渠道补录
  markOfflineFailed: (data: any) => http.post('/recording/offline-failed', data),
  getResumeInfo: (token: string) => http.get(`/recording/resume-info/${token}`),
  completeOnlineResume: (data: any) => http.post('/recording/resume-complete', data)
}

// ==================== 话术 API ====================
export const scriptApi = {
  // 全部话术 (按产品)
  getTemplate: (productId: string) => http.get(`/script/${productId}`),
  // DB 话术
  getDbTemplate: (productId: string) => http.get(`/script-config/db-template/${productId}`),
  // 全部 DB 话术
  listDbTemplates: () => http.get('/script-config/db-templates'),
  // 全部话术
  listAll: () => http.get('/script/all'),
  // 一致性检查
  consistency: () => http.get('/script/consistency'),
  // 冻结状态
  frozenStatus: (productId: string) => http.get(`/script/frozen/${productId}`),
  // 禁播词
  forbiddenPhrases: () => http.get('/script-config/forbidden-phrases')
}

// ==================== 话术配置 API (CRUD) ====================
export const scriptConfigApi = {
  globalConfig: () => http.get('/script-config/global'),
  byProduct: (productId: string) => http.get(`/script-config/product/${productId}`),
  productSource: (productId: string) => http.get(`/script-config/product/${productId}/source`),
  all: () => http.get('/script-config/all'),
  upsertDb: (data: any) => http.post('/script-config/db-template', data),
  deleteDb: (id: string) => http.delete(`/script-config/db-template/${id}`),
  submit: (id: string) => http.post(`/script-config/db-template/${id}/submit`),
  approve: (id: string) => http.post(`/script-config/db-template/${id}/approve`),
  freeze: (id: string) => http.post(`/script-config/db-template/${id}/freeze`)
}

// ==================== 风险评估 API ====================
export const riskApi = {
  // 提交评估
  submit: (data: {
    customerIdHash: string
    answers: Record<string, any>
  }) => http.post('/risk/submit', data),
  // 客户最新评估
  latest: (customerIdHash: string) => http.get(`/risk/latest/${customerIdHash}`),
  // 风险匹配
  match: (customerLevel: string, productRisk: string) =>
    http.get('/risk/match', { params: { customerLevel, productRisk } })
}

// ==================== 录像合规 API ====================
export const complianceApi = {
  scan: (data: { text: string }) => http.post('/compliance/scan', data),
  refresh: () => http.post('/compliance/refresh'),
  // 32 项检查
  check: (data: { businessId: string; recId?: string }) =>
    http.post('/recording-compliance/check', data),
  checklist: (recId: string) => http.get(`/recording-compliance/checklist`, { params: { recId } })
}

// ==================== 文件推送 API (PC 坐席用) ====================
export const fileApi = {
  push: (data: {
    businessId: string
    fileName: string
    fileUrl?: string
    fileType?: string
    note?: string
  }) => http.post('/file/push', data),
  list: (businessId: string) => http.get(`/file/list/${businessId}`),
  detail: (fileId: string) => http.get(`/file/${fileId}`),
  markViewed: (fileId: string) => http.post(`/file/${fileId}/view`),
  sign: (fileId: string, data: { signature: string }) =>
    http.post(`/file/${fileId}/sign`, data),
  templates: () => http.get('/file/templates')
}

// ==================== 理财经理 API ====================
export const advisorApi = {
  listAvailable: () => http.get('/advisor/list'),
  pending: (advisorId: string) => http.get(`/advisor/pending/${advisorId}`),
  active: (advisorId: string) => http.get(`/advisor/advisor/active/${advisorId}`),
  activeByBusiness: (businessId: string) => http.get(`/advisor/active/${businessId}`),
  request: (data: { businessId: string; reason: string; description?: string; preferredAdvisorId?: string }) =>
    http.post('/advisor/request', data),
  accept: (sessionId: string) => http.post(`/advisor/${sessionId}/accept`),
  decline: (sessionId: string, reason?: string) =>
    http.post(`/advisor/${sessionId}/decline`, null, { params: { reason } }),
  end: (sessionId: string) => http.post(`/advisor/${sessionId}/end`)
}

// ==================== 状态机 API ====================
export const stateMachineApi = {
  transitions: () => http.get('/statemachine/transitions'),
  canTransition: (from: string, to: string) =>
    http.get('/statemachine/can-transition', { params: { from, to } }),
  // 8 节点
  nodes: () => http.get('/recording/nodes')
}

// ==================== 健康检查 ====================
export const healthApi = {
  check: () => http.get('/health')
}

export default http
