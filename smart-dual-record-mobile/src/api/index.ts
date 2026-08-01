import axios, { type AxiosInstance, type AxiosResponse } from 'axios'
import { showToast } from 'vant'

const http: AxiosInstance = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || '/api',
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
    } else {
      showToast(msg)
    }
    return Promise.reject(err)
  }
)

// ==================== 业务 API ====================
export const businessApi = {
  create: (data: any) => http.post('/business', data),
  get: (id: string) => http.get(`/business/${id}`),
  list: (params?: any) => http.get('/business', { params }),
  archive: (id: string) => http.post(`/business/${id}/archive`),
  // 跨渠道补录
  markOfflineFailed: (data: any) => http.post('/recording/offline-failed', data),
  getResumeInfo: (token: string) => http.get(`/recording/resume-info/${token}`),
  completeOnlineResume: (data: any) => http.post('/recording/resume-complete', data)
}

// ==================== 录像 API ====================
export const recordingApi = {
  start: (businessId: string, data: any) => http.post(`/recording/${businessId}/start`, data),
  completeNode: (businessId: string, data: any) => http.post(`/recording/${businessId}/node/complete`, data),
  complete: (businessId: string, data: any) => http.post(`/recording/${businessId}/complete`, data),
  detail: (recId: string) => http.get(`/recording/${recId}`),
  // 8 节点进度
  progress: (businessId: string) => http.get(`/recording/${businessId}/progress`),
  // AI 质检
  aiCheck: (recId: string) => http.get(`/recording/${recId}/ai-check`)
}

// ==================== 话术 API ====================
export const scriptApi = {
  getTemplate: (productId: string, channel: string) => http.get('/script/template', { params: { productId, channel } }),
  listTemplates: () => http.get('/script/templates'),
  validate: (text: string) => http.post('/script/validate', { text })
}

export const scriptConfigApi = {
  globalConfig: () => http.get('/script-config/global'),
  byProduct: (productId: string) => http.get(`/script-config/by-product/${productId}`),
  listDb: (params?: any) => http.get('/script-config/db', { params }),
  upsert: (data: any) => http.post('/script-config/db', data),
  delete: (id: string) => http.delete(`/script-config/db/${id}`),
  submit: (id: string) => http.post(`/script-config/db/${id}/submit`),
  approve: (id: string) => http.post(`/script-config/db/${id}/approve`),
  freeze: (id: string) => http.post(`/script-config/db/${id}/freeze`)
}

// ==================== 风险评估 API ====================
export const riskApi = {
  submit: (data: any) => http.post('/risk-assessment', data),
  get: (assessmentId: string) => http.get(`/risk-assessment/${assessmentId}`),
  listByCustomer: (customerId: string) => http.get(`/risk-assessment/customer/${customerId}`),
  // 9 维问卷
  questions: () => http.get('/risk-assessment/questions'),
  // 风险匹配
  match: (riskLevel: string, productId: string) => http.get('/risk-assessment/match', { params: { riskLevel, productId } })
}

// ==================== 禁播词 API ====================
export const forbiddenApi = {
  list: (params?: any) => http.get('/forbidden-phrase', { params }),
  scan: (text: string) => http.post('/forbidden-phrase/scan', { text })
}

// ==================== 录像合规 API ====================
export const complianceApi = {
  check32Items: (recId: string) => http.get(`/recording-compliance/${recId}/32-items`),
  report: (recId: string) => http.get(`/recording-compliance/${recId}/report`),
  annotations: (recId: string) => http.get(`/recording-compliance/${recId}/annotations`)
}

export const recordingComplianceApi = {
  check: (recId: string) => http.get(`/recording-compliance/${recId}/check`),
  annotations: (recId: string) => http.get(`/recording-compliance/${recId}/annotations`),
  report: (recId: string) => http.get(`/recording-compliance/${recId}/report`)
}

// ==================== 文件推送 API (PC 坐席用) ====================
export const fileApi = {
  push: (data: any) => http.post('/file-push', data),
  list: (businessId: string) => http.get(`/file-push/business/${businessId}`),
  detail: (fileId: string) => http.get(`/file-push/${fileId}`),
  markViewed: (fileId: string) => http.post(`/file-push/${fileId}/viewed`),
  sign: (fileId: string, data: any) => http.post(`/file-push/${fileId}/sign`, data),
  templates: () => http.get('/file-push/templates')
}

// ==================== 理财经理 API ====================
export const advisorApi = {
  listAvailable: () => http.get('/advisor/available'),
  requestTransfer: (data: any) => http.post('/advisor/transfer', data),
  accept: (sessionId: string) => http.post(`/advisor/session/${sessionId}/accept`),
  decline: (sessionId: string, reason?: string) => http.post(`/advisor/session/${sessionId}/decline`, { reason }),
  end: (sessionId: string) => http.post(`/advisor/session/${sessionId}/end`),
  pendingList: () => http.get('/advisor/pending'),
  activeList: () => http.get('/advisor/active'),
  myActive: () => http.get('/advisor/active/me')
}

// ==================== 状态机 API ====================
export const stateMachineApi = {
  states: () => http.get('/state-machine/states'),
  transitions: (from: string) => http.get(`/state-machine/transitions/${from}`),
  // 8 节点
  nodes: () => http.get('/state-machine/nodes')
}

// ==================== 健康检查 ====================
export const healthApi = {
  check: () => http.get('/health'),
  ready: () => http.get('/health/ready')
}

export default http
