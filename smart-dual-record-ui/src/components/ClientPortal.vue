<script setup lang="ts">
/**
 * 客户侧双录门户 v1.5 · 客户进线做双录
 *
 * 设计目标: 客户在收到理财经理 / 银行 APP 推送的链接后, 进入此页面完成双录
 *
 * 用户旅程 (6 步):
 *   1. 进入欢迎页 (产品信息 + 协议确认)
 *   2. 环境自检 (摄像头 / 麦克风 / 网络 / 浏览器)
 *   3. 身份核验 (身份证 OCR / 人脸识别 / 签字)
 *   4. 风险评估 (9 维问卷 → C1-C5)
 *   5. 双录录制 (8 节点时间轴, 大按钮, 显著合规标识)
 *   6. 完成确认 (签字 + 犹豫期 15 天提示 + 客服联系方式)
 *
 * 渠道: REMOTE_VIDEO (理财经理远程) / SELF_AI (AI 数字人自助)
 * 设计: 大字号 + 大按钮 + 圆角 + 渐变 + 进度环, 适合中老年客户
 *
 * 演示模式: 内存中模拟完整流程, 无需后端; 真实环境接入 store + API
 */
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { recordingApi, fileApi } from '@/api'

// ============================================================================
// 1. 状态
// ============================================================================

const currentStep = ref(1)
const STEPS = [
  { key: 1, title: '欢迎', desc: '产品信息 + 协议' },
  { key: 2, title: '环境检测', desc: '摄像头/麦克风' },
  { key: 3, title: '身份核验', desc: '身份证 + 人脸' },
  { key: 4, title: '风险评估', desc: '9 维问卷' },
  { key: 5, title: '双录录制', desc: '8 节点流程' },
  { key: 6, title: '完成', desc: '签字 + 犹豫期' }
]

// 业务信息 (从 URL ?businessId=BNK20260801-900001 读取, ?token=xxx 用于补录)
const businessId = ref('BNK20260801-900001')
const resumeToken = ref<string>('')
const resumeFromOrder = ref<number>(0)  // 从哪个节点开始 (1-8)
const resumeReason = ref<string>('')
const productInfo = ref({
  name: '稳健型封闭式理财',
  code: 'BNK-FIN-2026Q3-001',
  type: 'WEALTH',
  riskLevel: 'R2',
  amount: 50000,
  expectedReturn: '3.0% - 3.8%',
  duration: '180 天',
  description: '本产品为非保本浮动收益型理财, 投资于债券 + 少量股票, 适合稳健型客户。'
})
const advisor = ref({
  name: '王经理',
  branch: '北京朝阳支行',
  avatar: '👨‍💼',
  phone: '138****8001'
})

// 步骤 1: 协议
const agreed = ref(false)

// 步骤 2: 环境检测
const envCheck = ref({
  camera: false,
  mic: false,
  speaker: false,
  network: false,
  browser: false
})
const envStream = ref<MediaStream | null>(null)
const envVideoRef = ref<HTMLVideoElement | null>(null)
const envMicLevel = ref(0)
let envMicTimer: number | null = null

// 步骤 3: 身份
const idCardFront = ref<{ name: string; id: string; valid: boolean } | null>(null)
const faceMatched = ref(false)
const idSignature = ref('')
const idStep = ref<'scan' | 'face' | 'sign' | 'done'>('scan')

// 步骤 4: 风险评估 (9 维)
const riskAnswers = ref<Record<string, string>>({})
const riskScore = ref(0)
const riskLevel = ref('C3')
const currentRiskIdx = ref(0)

const RISK_QUESTIONS = [
  { key: 'liquidity', q: '您的资金可投资期限是?', options: [
    { v: 'A', s: 5, l: '短期 (< 30 天)' },
    { v: 'B', s: 15, l: '30 天 - 1 年' },
    { v: 'C', s: 25, l: '1 - 3 年' },
    { v: 'D', s: 35, l: '3 年以上' }
  ]},
  { key: 'loss_tolerance', q: '能承受的最大亏损比例是?', options: [
    { v: 'A', s: 5, l: '5% 以内' },
    { v: 'B', s: 15, l: '5% - 15%' },
    { v: 'C', s: 25, l: '15% - 30%' },
    { v: 'D', s: 35, l: '30% 以上' }
  ]},
  { key: 'experience', q: '您有多少年投资经验?', options: [
    { v: 'A', s: 5, l: '无经验' },
    { v: 'B', s: 15, l: '1 - 3 年' },
    { v: 'C', s: 25, l: '3 - 5 年' },
    { v: 'D', s: 35, l: '5 年以上' }
  ]},
  { key: 'income', q: '年收入(税后) 大约多少?', options: [
    { v: 'A', s: 5, l: '< 10 万' },
    { v: 'B', s: 15, l: '10 - 50 万' },
    { v: 'C', s: 25, l: '50 - 100 万' },
    { v: 'D', s: 35, l: '100 万以上' }
  ]},
  { key: 'asset_ratio', q: '本次投资占家庭总资产的比例?', options: [
    { v: 'A', s: 5, l: '< 5%' },
    { v: 'B', s: 15, l: '5% - 20%' },
    { v: 'C', s: 25, l: '20% - 50%' },
    { v: 'D', s: 35, l: '> 50%' }
  ]},
  { key: 'leverage', q: '是否使用杠杆(借款) 投资?', options: [
    { v: 'A', s: 5, l: '从不' },
    { v: 'B', s: 15, l: '偶尔' },
    { v: 'C', s: 25, l: '经常' }
  ]},
  { key: 'complexity', q: '您对以下产品的理解?', options: [
    { v: 'A', s: 5, l: '存款/货基' },
    { v: 'B', s: 15, l: '债基/理财' },
    { v: 'C', s: 25, l: '混合/股票' },
    { v: 'D', s: 35, l: '衍生品/PE' }
  ]},
  { key: 'goal', q: '本次投资的主要目标?', options: [
    { v: 'A', s: 5, l: '保本' },
    { v: 'B', s: 15, l: '稳定收益' },
    { v: 'C', s: 25, l: '资产增值' },
    { v: 'D', s: 35, l: '高回报' }
  ]},
  { key: 'mood', q: '面对短期亏损, 您会?', options: [
    { v: 'A', s: 5, l: '立即赎回' },
    { v: 'B', s: 15, l: '观望几天' },
    { v: 'C', s: 25, l: '坚持持有' },
    { v: 'D', s: 35, l: '加仓买入' }
  ]}
]

// 步骤 5: 录制 (复用时间轴 + 客户侧大按钮)
const recNodeIdx = ref(0)
const recCompleted = ref<Set<number>>(new Set())
const recElapsed = ref(0)
const recRecording = ref(false)
const recClientVideo = ref<HTMLVideoElement | null>(null)
const recClientStream = ref<MediaStream | null>(null)
const recMediaRecorder = ref<MediaRecorder | null>(null)
const recBlobs = ref<Blob[]>([])
const recAsrInput = ref('')
const recRecordingId = ref<string>('')
let recTimer: number | null = null

// v1.5 坐席推送文件
const pushedFiles = ref<any[]>([])
const latestPushedFile = ref<any>(null)
const showFileViewer = ref(false)
const viewingFile = ref<any>(null)
const showSignPad = ref(false)
const signingFile = ref<any>(null)
const customerSignRef = ref<HTMLCanvasElement | null>(null)
const isFileSignDrawing = ref(false)
const hasSign = ref(false)
let clientWs: WebSocket | null = null
const clientWsConnected = ref(false)

// 8 节点
const CLIENT_NODES = [
  { code: '01-IDENTITY', name: '出示身份', desc: '请出示您的身份证', mandatory: ['请坐正, 露出正脸', '请打开您的身份证件'], critical: false, dur: 30 },
  { code: '02-DISCLOSURE', name: '风险揭示', desc: '本产品存在投资风险, 详见说明书', mandatory: ['本产品为非保本浮动收益型理财', '过往业绩不代表未来表现', '投资有风险, 入市需谨慎'], critical: true, dur: 90 },
  { code: '03-PRODUCT', name: '产品详情', desc: '请仔细阅读产品要素', mandatory: ['产品名称: 稳健型封闭式理财', '业绩比较基准: 3.0% - 3.8%', '封闭期: 180 天'], critical: false, dur: 60 },
  { code: '04-RIGHTS', name: '权利义务', desc: '15 天犹豫期 + 风险自担', mandatory: ['15 天犹豫期内可无理由退保', '工本费不超过 10 元', '本金及收益风险由您承担'], critical: false, dur: 45 },
  { code: '05-TRUTH_TELL', name: '如实告知', desc: '请如实告知您的财务状况', mandatory: ['请确认: 资金来源合法', '请确认: 非借贷资金', '请确认: 风险等级匹配'], critical: false, dur: 60 },
  { code: '06-CONFIRM', name: '明确肯定', desc: '★ 关键节点: 请明确回答 "是"', mandatory: ['您是否已了解本产品风险?', '您是否已阅读产品说明书?'], critical: true, dur: 30 },
  { code: '07-SIGN', name: '电子签署', desc: '请在下方手写签名', mandatory: ['请用手指在屏幕上签名'], critical: false, dur: 45 },
  { code: '08-FOLLOWUP', name: '补充询问', desc: '您还有其他问题吗?', mandatory: ['请问您还有其他问题吗?'], critical: false, dur: 20 }
]

// 步骤 6: 完成
const finalSigned = ref(false)
const hesPeriodEnd = computed(() => {
  const d = new Date()
  d.setDate(d.getDate() + 15)
  return d.toLocaleDateString('zh-CN')
})

// 客户侧画板 (签名)
const signCanvasRef = ref<HTMLCanvasElement | null>(null)
const isOldSignDrawing = ref(false)
const hasOldSignature = ref(false)

// ============================================================================
// 2. 计算属性
// ============================================================================
const overallProgress = computed(() => (currentStep.value / STEPS.length) * 100)

const currentNode = computed(() => CLIENT_NODES[recNodeIdx.value])
const recProgressPct = computed(() => (recCompleted.value.size / CLIENT_NODES.length) * 100)
const recFormattedTime = computed(() => {
  const s = Math.floor(recElapsed.value / 1000)
  return `${Math.floor(s / 60).toString().padStart(2, '0')}:${(s % 60).toString().padStart(2, '0')}`
})

// 风险等级文字
const riskLevelLabel = computed(() => {
  const m: Record<string, { label: string; color: string; desc: string }> = {
    C1: { label: '保守型', color: '#94a3b8', desc: '适合存款 / 货基 / 短债' },
    C2: { label: '稳健型', color: '#3b6b8c', desc: '适合债基 / 银行理财 R1-R2' },
    C3: { label: '平衡型', color: '#b8860b', desc: '适合混合理财 R3 / 平衡型基金' },
    C4: { label: '成长型', color: '#c1453a', desc: '适合股票基金 R4' },
    C5: { label: '激进型', color: '#1e2a47', desc: '适合 PE / 海外 / 衍生品' }
  }
  return m[riskLevel.value] || m.C3
})

// 产品 vs 客户匹配
const isMatch = computed(() => {
  const m: Record<string, number> = { C1: 1, C2: 2, C3: 3, C4: 4, C5: 5 }
  const productR = parseInt(productInfo.value.riskLevel.replace(/[PR]/, ''))
  return m[riskLevel.value] >= productR
})

// ============================================================================
// 3. 步骤 2: 环境检测
// ============================================================================
async function startEnvCheck() {
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ video: true, audio: true })
    envStream.value = stream
    envCheck.value.camera = true
    envCheck.value.mic = true
    envCheck.value.speaker = true
    await nextTick()
    if (envVideoRef.value) {
      envVideoRef.value.srcObject = stream
      envVideoRef.value.muted = true
      envVideoRef.value.play()
    }
    // 麦克风音量
    const audioCtx = new AudioContext()
    const source = audioCtx.createMediaStreamSource(stream)
    const analyser = audioCtx.createAnalyser()
    analyser.fftSize = 256
    source.connect(analyser)
    const data = new Uint8Array(analyser.frequencyBinCount)
    const tick = () => {
      analyser.getByteFrequencyData(data)
      let sum = 0
      for (let i = 0; i < data.length; i++) sum += data[i]
      envMicLevel.value = Math.min(100, (sum / data.length / 255) * 100 * 2)
      envMicTimer = requestAnimationFrame(tick)
    }
    tick()
  } catch (e: any) {
    ElMessage.warning('⚠️ 无法访问摄像头/麦克风: ' + e.message + ', 将进入模拟模式')
  }
  // 网络
  envCheck.value.network = navigator.onLine
  // 浏览器
  envCheck.value.browser = !!window.MediaRecorder && !!navigator.mediaDevices
}

function speakTest() {
  // 模拟扬声器测试
  const u = new SpeechSynthesisUtterance('环境检测通过, 您的设备已就绪')
  u.lang = 'zh-CN'
  u.rate = 0.9
  speechSynthesis.speak(u)
}

const envAllOk = computed(() => Object.values(envCheck.value).every(v => v))

// ============================================================================
// 4. 步骤 3: 身份核验
// ============================================================================
function simulateIdScan() {
  setTimeout(() => {
    idCardFront.value = {
      name: '张三',
      id: '110101********1234',
      valid: true
    }
    ElMessage.success('✓ 身份证识别成功')
    idStep.value = 'face'
  }, 1500)
}

function simulateFaceMatch() {
  setTimeout(() => {
    faceMatched.value = true
    ElMessage.success('✓ 人脸识别通过 (相似度 99.2%)')
    idStep.value = 'sign'
  }, 1500)
}

// 签名画板
function startDraw(e: MouseEvent | TouchEvent) {
  isOldSignDrawing.value = true
  draw(e)
}
function endDraw() {
  isOldSignDrawing.value = false
  if (signCanvasRef.value) {
    const ctx = signCanvasRef.value.getContext('2d')
    ctx?.beginPath()
  }
}
function draw(e: MouseEvent | TouchEvent) {
  if (!isOldSignDrawing.value || !signCanvasRef.value) return
  const canvas = signCanvasRef.value
  const ctx = canvas.getContext('2d')
  if (!ctx) return
  const rect = canvas.getBoundingClientRect()
  let x, y
  if ('touches' in e) {
    x = e.touches[0].clientX - rect.left
    y = e.touches[0].clientY - rect.top
  } else {
    x = e.clientX - rect.left
    y = e.clientY - rect.top
  }
  ctx.lineWidth = 2.5
  ctx.lineCap = 'round'
  ctx.strokeStyle = '#1e2a47'
  ctx.lineTo(x, y)
  ctx.stroke()
  ctx.beginPath()
  ctx.moveTo(x, y)
  hasOldSignature.value = true
}
function clearSignature() {
  if (!signCanvasRef.value) return
  const ctx = signCanvasRef.value.getContext('2d')
  ctx?.clearRect(0, 0, signCanvasRef.value.width, signCanvasRef.value.height)
  ctx?.beginPath()
  hasOldSignature.value = false
}
function confirmIdentity() {
  if (!hasOldSignature.value) {
    ElMessage.warning('请先签名')
    return
  }
  idStep.value = 'done'
  ElMessage.success('✓ 身份核验完成')
  setTimeout(() => {
    currentStep.value = 4
  }, 800)
}

// ============================================================================
// 5. 步骤 4: 风险评估
// ============================================================================
function answerRisk(qKey: string, optScore: number) {
  riskAnswers.value[qKey] = optScore.toString()
  if (currentRiskIdx.value < RISK_QUESTIONS.length - 1) {
    setTimeout(() => currentRiskIdx.value++, 200)
  } else {
    finishRisk()
  }
}

function finishRisk() {
  const total = Object.values(riskAnswers.value).reduce((s, v) => s + Number(v), 0)
  riskScore.value = total
  // 9 题满分 305, 算比例后映射
  const ratio = total / 305
  if (ratio < 0.25) riskLevel.value = 'C1'
  else if (ratio < 0.45) riskLevel.value = 'C2'
  else if (ratio < 0.65) riskLevel.value = 'C3'
  else if (ratio < 0.85) riskLevel.value = 'C4'
  else riskLevel.value = 'C5'
  ElMessage.success(`✓ 风险评估完成: ${riskLevel.value} (${riskLevelLabel.value.label})`)
  setTimeout(() => currentStep.value = 5, 1500)
}

const currentQuestion = computed(() => RISK_QUESTIONS[currentRiskIdx.value])
const riskProgressPct = computed(() => (Object.keys(riskAnswers.value).length / RISK_QUESTIONS.length) * 100)

// ============================================================================
// 6. 步骤 5: 录制
// ============================================================================
async function startRecording() {
  recRecording.value = true
  recRecordingId.value = 'REC-CLIENT-' + Date.now()
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ video: { width: 640, height: 480 }, audio: true })
    recClientStream.value = stream
    await nextTick()
    if (recClientVideo.value) {
      recClientVideo.value.srcObject = stream
      recClientVideo.value.muted = true
      recClientVideo.value.play()
    }
    const mr = new MediaRecorder(stream, { mimeType: 'video/webm' })
    mr.ondataavailable = e => { if (e.data && e.data.size > 0) recBlobs.value.push(e.data) }
    mr.start(1000)
    recMediaRecorder.value = mr
  } catch (e) {
    console.warn('无法访问摄像头, 进入模拟模式', e)
  }
  recTimer = window.setInterval(() => recElapsed.value += 100, 100)
  ElMessage.success('🎬 录制已开始')
}

function completeCurrentNode() {
  if (recCompleted.value.has(recNodeIdx.value)) return
  recCompleted.value.add(recNodeIdx.value)
  if (recNodeIdx.value < CLIENT_NODES.length - 1) {
    setTimeout(() => recNodeIdx.value++, 600)
  } else {
    stopRecording()
    // 如果是补录, 调用后端完成接口
    if (resumeToken.value) {
      recordingApi.completeResume(businessId.value, resumeToken.value).then(() => {
        ElMessage.success('✓ 线上补录完成, 业务已恢复正常')
      }).catch(e => {
        ElMessage.warning('补录完成回调失败: ' + e.message)
      })
    }
    setTimeout(() => currentStep.value = 6, 1500)
  }
}

function stopRecording() {
  recRecording.value = false
  if (recTimer) clearInterval(recTimer)
  recTimer = null
  if (recMediaRecorder.value && recMediaRecorder.value.state !== 'inactive') {
    recMediaRecorder.value.stop()
  }
  if (recClientStream.value) {
    recClientStream.value.getTracks().forEach(t => t.stop())
  }
}

// ============================================================================
// 7. 步骤导航
// ============================================================================
function nextStep() {
  // 校验
  if (currentStep.value === 1 && !agreed.value) {
    ElMessage.warning('请先同意服务协议')
    return
  }
  if (currentStep.value === 2 && !envAllOk.value) {
    ElMessage.warning('请先完成环境检测')
    return
  }
  if (currentStep.value === 3 && idStep.value !== 'done') {
    ElMessage.warning('请先完成身份核验')
    return
  }
  if (currentStep.value === 4 && Object.keys(riskAnswers.value).length < RISK_QUESTIONS.length) {
    ElMessage.warning('请先完成所有风险评估问题')
    return
  }
  if (currentStep.value === 5 && recCompleted.value.size < CLIENT_NODES.length) {
    ElMessage.warning(`还有 ${CLIENT_NODES.length - recCompleted.value.size} 个节点未完成`)
    return
  }
  if (currentStep.value === 5) {
    stopRecording()
  }
  if (currentStep.value < STEPS.length) currentStep.value++
}

function prevStep() {
  if (currentStep.value > 1) currentStep.value--
}

// ============================================================================
// 8. 生命周期
// ============================================================================
onMounted(async () => {
  // 解析 URL 参数
  const params = new URLSearchParams(window.location.search)
  if (params.get('businessId')) businessId.value = params.get('businessId')!
  if (params.get('advisor')) advisor.value.name = params.get('advisor')!
  // v1.5 跨渠道补录: ?token=xxx
  if (params.get('token')) {
    resumeToken.value = params.get('token')!
    try {
      const info = await recordingApi.getResumeInfo(resumeToken.value)
      if (info.businessId) businessId.value = info.businessId
      if (info.productId) productInfo.value.code = info.productId
      if (info.amount) productInfo.value.amount = info.amount
      if (info.productRiskLevel) productInfo.value.riskLevel = info.productRiskLevel
      if (info.customerRiskLevel) riskLevel.value = info.customerRiskLevel
      if (info.resumeFromNodeOrder) resumeFromOrder.value = info.resumeFromNodeOrder
      if (info.failedReason) resumeReason.value = info.failedReason
      ElMessage.warning(`检测到补录请求, 从节点 ${resumeFromOrder.value} 继续`)
    } catch (e: any) {
      ElMessage.error('补录 token 无效: ' + e.message)
    }
  }
  // 自动启动环境检测
  startEnvCheck()
  // v1.5: 加载已推送的文件 + WebSocket
  await loadPushedFiles()
  connectClientWs()
})

onUnmounted(() => {
  if (envStream.value) envStream.value.getTracks().forEach(t => t.stop())
  if (envMicTimer) cancelAnimationFrame(envMicTimer)
  stopRecording()
  if (clientWs) clientWs.close()
})

// ============================================================================
// v1.5 文件推送 (客户侧)
// ============================================================================
async function loadPushedFiles() {
  try {
    const list = await fileApi.list(businessId.value)
    pushedFiles.value = list
    // 找最近未签署/未查看的
    const pending = list.find(f => f.status === 'PUSHED' || (f.status === 'VIEWED' && !f.signedAt && f.fileCategory === 'CONTRACT'))
    if (pending) latestPushedFile.value = pending
  } catch (e) { pushedFiles.value = [] }
}

function connectClientWs() {
  const proto = window.location.protocol === 'https:' ? 'wss' : 'ws'
  const host = (window as any).location?.host || 'localhost:8080'
  const url = `${proto}://${host}/ws/recording/${businessId.value}`
  try {
    clientWs = new WebSocket(url)
    clientWs.onopen = () => { clientWsConnected.value = true }
    clientWs.onclose = () => {
      clientWsConnected.value = false
      setTimeout(connectClientWs, 3000)
    }
    clientWs.onerror = () => { clientWsConnected.value = false }
    clientWs.onmessage = (e) => {
      try {
        const msg = JSON.parse(e.data)
        if (msg.type === 'FILE_PUSHED') {
          ElMessage.warning(`📤 坐席推送给您: ${msg.fileName}`)
          loadPushedFiles()
        }
      } catch (err) { /* ignore */ }
    }
  } catch (e) { /* ignore */ }
}

function openFileViewer(file: any) {
  viewingFile.value = file
  showFileViewer.value = true
  // 自动标记已查看
  if (!file.viewedAt) {
    fileApi.markViewed(file.fileId).then(() => loadPushedFiles())
  }
}

async function markFileViewed(file: any) {
  try {
    await fileApi.markViewed(file.fileId)
    ElMessage.success('✓ 已记录您查看过此文件')
    await loadPushedFiles()
  } catch (e: any) {
    ElMessage.error('标记失败: ' + e.message)
  }
}

function openSignPad(file: any) {
  signingFile.value = file
  showSignPad.value = true
  hasSign.value = false
  // 等 DOM 更新后清空 canvas
  setTimeout(() => {
    if (customerSignRef.value) {
      const ctx = customerSignRef.value.getContext('2d')
      ctx?.clearRect(0, 0, customerSignRef.value.width, customerSignRef.value.height)
      ctx?.beginPath()
    }
  }, 100)
}

function startSignDraw(e: MouseEvent | TouchEvent) {
  isFileSignDrawing.value = true
  drawSign(e)
}
function endSignDraw() {
  isFileSignDrawing.value = false
  if (customerSignRef.value) {
    const ctx = customerSignRef.value.getContext('2d')
    ctx?.beginPath()
  }
}
function drawSign(e: MouseEvent | TouchEvent) {
  if (!isFileSignDrawing.value || !customerSignRef.value) return
  const canvas = customerSignRef.value
  const ctx = canvas.getContext('2d')
  if (!ctx) return
  const rect = canvas.getBoundingClientRect()
  let x, y
  if ('touches' in e) {
    x = e.touches[0].clientX - rect.left
    y = e.touches[0].clientY - rect.top
  } else {
    x = e.clientX - rect.left
    y = e.clientY - rect.top
  }
  ctx.lineWidth = 2.5
  ctx.lineCap = 'round'
  ctx.strokeStyle = '#1e2a47'
  ctx.lineTo(x, y)
  ctx.stroke()
  ctx.beginPath()
  ctx.moveTo(x, y)
  hasSign.value = true
}
function clearSign() {
  if (!customerSignRef.value) return
  const ctx = customerSignRef.value.getContext('2d')
  ctx?.clearRect(0, 0, customerSignRef.value.width, customerSignRef.value.height)
  ctx?.beginPath()
  hasSign.value = false
}
async function confirmSign() {
  if (!hasSign.value || !signingFile.value) {
    ElMessage.warning('请先签名')
    return
  }
  // 提取签名 base64
  const dataUrl = customerSignRef.value?.toDataURL('image/png') || ''
  try {
    await fileApi.signFile(signingFile.value.fileId, { signatureData: dataUrl, rejected: false })
    ElMessage.success('✓ 已签署')
    showSignPad.value = false
    await loadPushedFiles()
  } catch (e: any) {
    ElMessage.error('签署失败: ' + e.message)
  }
}
async function rejectFile() {
  if (!signingFile.value) return
  try {
    await fileApi.signFile(signingFile.value.fileId, { rejected: true, rejectReason: '客户拒绝签署' })
    ElMessage.warning('已拒签')
    showSignPad.value = false
    await loadPushedFiles()
  } catch (e: any) {
    ElMessage.error('拒签失败: ' + e.message)
  }
}

// 风险等级变化时提示
watch(riskLevel, (v) => {
  if (v === 'C1' && productInfo.value.riskLevel !== 'R1') {
    setTimeout(() => {
      ElMessageBox.confirm(
        '您的风险等级是 C1 (保守型), 但产品是 R2 (稳健型). 监管要求客户主动申请, 是否继续?',
        '风险等级不匹配',
        { confirmButtonText: '我主动申请', cancelButtonText: '换产品', type: 'warning' }
      ).catch(() => {
        currentStep.value = 1
      })
    }, 500)
  }
})

// v1.5 补录: 加载到 token 后直接跳到第 5 步 (录制) 并从对应节点开始
watch(resumeFromOrder, (o) => {
  if (o > 0) {
    currentStep.value = 5
    // 把 1 ~ (o-1) 标记为已完成
    const completed = new Set<number>()
    for (let i = 0; i < o - 1; i++) completed.add(i)
    recCompleted.value = completed
    recNodeIdx.value = o - 1
    // 风评也跳过
    if (Object.keys(riskAnswers.value).length === 0) {
      // 模拟已回答 (用 C3 兜底)
      for (let i = 0; i < RISK_QUESTIONS.length; i++) {
        riskAnswers.value[RISK_QUESTIONS[i].key] = '20'
      }
      riskScore.value = 180
      riskLevel.value = 'C3'
    }
    currentRiskIdx.value = RISK_QUESTIONS.length
  }
})
</script>

<template>
  <div class="client-portal">
    <!-- 顶部: 业务信息 + 进度 -->
    <header class="cp-header">
      <div class="cp-header-inner">
        <div class="cp-brand">
          <div class="cp-logo">🛡️</div>
          <div>
            <div class="cp-brand-name">智能双录 · 客户门户</div>
            <div class="cp-brand-sub">合规 · 安全 · 高效</div>
          </div>
        </div>
        <div class="cp-progress">
          <el-steps :active="currentStep - 1" finish-status="success" align-center simple>
            <el-step v-for="s in STEPS" :key="s.key" :title="s.title" />
          </el-steps>
        </div>
        <div class="cp-business">
          <div class="cp-biz-label">业务编号</div>
          <div class="cp-biz-id mono">{{ businessId }}</div>
        </div>
      </div>
      <div class="cp-progress-bar">
        <div class="cp-progress-fill" :style="{ width: overallProgress + '%' }"></div>
      </div>
    </header>

    <main class="cp-main">
      <!-- v1.5 跨渠道补录提示横幅 -->
      <el-alert
        v-if="resumeFromOrder > 0"
        type="warning"
        :closable="false"
        show-icon
        style="margin-bottom: 16px;"
        :title="`⚡ 跨渠道补录: 线下双录节点 ${resumeFromOrder} 失败 (${resumeReason}), 已自动跳转到此节点继续`"
        description="完成剩余节点后, 业务将恢复正常状态"
      />
      <!-- ============================== -->
      <!-- 步骤 1: 欢迎 + 协议            -->
      <!-- ============================== -->
      <section v-show="currentStep === 1" class="cp-section welcome">
        <div class="welcome-hero">
          <div class="welcome-badge">📞 您正在与 {{ advisor.name }} 进行双录</div>
          <h1 class="welcome-title">欢迎办理 <span class="accent">{{ productInfo.name }}</span></h1>
          <p class="welcome-sub">本流程约需 5-8 分钟, 请您准备身份证件并保持网络畅通</p>
        </div>

        <el-row :gutter="16">
          <el-col :span="14">
            <div class="card">
              <h3 class="card-title">📋 产品信息</h3>
              <el-descriptions :column="2" border>
                <el-descriptions-item label="产品名称">{{ productInfo.name }}</el-descriptions-item>
                <el-descriptions-item label="产品代码">
                  <span class="mono">{{ productInfo.code }}</span>
                </el-descriptions-item>
                <el-descriptions-item label="产品类型">
                  <el-tag>{{ productInfo.type }}</el-tag>
                </el-descriptions-item>
                <el-descriptions-item label="风险等级">
                  <span class="risk-pill" :class="productInfo.riskLevel">{{ productInfo.riskLevel }}</span>
                </el-descriptions-item>
                <el-descriptions-item label="投资金额">¥ {{ productInfo.amount.toLocaleString() }}</el-descriptions-item>
                <el-descriptions-item label="业绩基准">{{ productInfo.expectedReturn }}</el-descriptions-item>
                <el-descriptions-item label="封闭期">{{ productInfo.duration }}</el-descriptions-item>
                <el-descriptions-item label="15 天犹豫期">✓ 享此权益</el-descriptions-item>
              </el-descriptions>
              <div class="product-desc">{{ productInfo.description }}</div>
            </div>
          </el-col>
          <el-col :span="10">
            <div class="card">
              <h3 class="card-title">👨‍💼 您的理财经理</h3>
              <div class="advisor-card">
                <div class="advisor-avatar">{{ advisor.avatar }}</div>
                <div>
                  <div class="advisor-name">{{ advisor.name }}</div>
                  <div class="advisor-meta">{{ advisor.branch }}</div>
                  <div class="advisor-meta">📱 {{ advisor.phone }}</div>
                </div>
              </div>
              <el-alert type="info" :closable="false" show-icon
                title="本通话全程录音录像"
                description="依据《金发〔2026〕8号》, 银行/保险/基金销售过程必须双录, 用于保护您的权益。"
              />
            </div>
          </el-col>
        </el-row>

        <div class="card">
          <h3 class="card-title">📜 服务协议</h3>
          <el-scrollbar height="200px">
            <div class="agreement-text">
              <h4>双录服务协议</h4>
              <p>1. 您理解并同意, 本次业务办理过程将同步录音录像 (以下简称"双录")。</p>
              <p>2. 双录内容将加密存储于我行/保险公司/基金公司专用服务器, 保管期限不少于 10 年。</p>
              <p>3. 您的身份证号、手机号等敏感信息已脱敏存储, 仅用于业务办理与监管报送。</p>
              <p>4. 您享有 15 天犹豫期 (自合同生效起), 期间可无理由退保, 工本费不超过 10 元。</p>
              <p>5. 您有权随时调阅本次双录录像 (需本人携身份证至网点办理)。</p>
              <p>6. 您理解 AI 数字人/远程视频可能涉及您的肖像, 同意按"显著标识"原则使用。</p>
              <p>7. 监管机构 (金管局/证监会/银保监) 有权依法调阅本次双录。</p>
              <p>8. 如有任何疑问, 请联系您的理财经理或拨打客服热线 955XX。</p>
              <p>9. 点击"我已阅读并同意", 即视为您接受以上全部条款。</p>
            </div>
          </el-scrollbar>
          <div class="agreement-checkbox">
            <el-checkbox v-model="agreed" size="large">
              <strong>我已阅读并同意《双录服务协议》全部条款</strong>
            </el-checkbox>
          </div>
        </div>

        <div class="cp-action-bar">
          <el-button type="primary" size="large" :disabled="!agreed" @click="nextStep" style="min-width: 200px;">
            下一步 · 环境检测
            <el-icon><ArrowRight /></el-icon>
          </el-button>
        </div>
      </section>

      <!-- ============================== -->
      <!-- 步骤 2: 环境检测                -->
      <!-- ============================== -->
      <section v-show="currentStep === 2" class="cp-section">
        <h2 class="section-title">📡 设备环境自检</h2>
        <p class="section-sub">请确保您的设备满足以下条件, 保障双录质量</p>

        <el-row :gutter="16">
          <el-col :span="14">
            <div class="card">
              <h3 class="card-title">摄像头预览</h3>
              <div class="video-frame">
                <video ref="envVideoRef" autoplay muted playsinline class="cam-video" />
                <div v-if="!envCheck.camera" class="cam-placeholder">
                  <div class="cam-icon">📷</div>
                  <div>摄像头未启动</div>
                </div>
                <div v-if="envCheck.camera" class="cam-overlay">
                  <div class="rec-badge">● 摄像头就绪</div>
                </div>
              </div>
              <div class="mic-section">
                <div class="mic-label">🎙 麦克风音量</div>
                <div class="mic-bar">
                  <div
                    v-for="i in 20"
                    :key="i"
                    class="mic-cell"
                    :class="{ active: envMicLevel > (i * 5) }"
                  />
                </div>
                <el-button size="small" @click="speakTest">🔊 扬声器测试</el-button>
              </div>
            </div>
          </el-col>
          <el-col :span="10">
            <div class="card">
              <h3 class="card-title">检测项</h3>
              <div class="env-list">
                <div class="env-item" :class="{ ok: envCheck.camera }">
                  <el-icon class="env-icon"><VideoCamera /></el-icon>
                  <div class="env-text">摄像头</div>
                  <el-tag :type="envCheck.camera ? 'success' : 'danger'" size="small">
                    {{ envCheck.camera ? '✓ 正常' : '✗ 未授权' }}
                  </el-tag>
                </div>
                <div class="env-item" :class="{ ok: envCheck.mic }">
                  <el-icon class="env-icon"><Microphone /></el-icon>
                  <div class="env-text">麦克风</div>
                  <el-tag :type="envCheck.mic ? 'success' : 'danger'" size="small">
                    {{ envCheck.mic ? '✓ 正常' : '✗ 未授权' }}
                  </el-tag>
                </div>
                <div class="env-item" :class="{ ok: envCheck.speaker }">
                  <el-icon class="env-icon"><Headset /></el-icon>
                  <div class="env-text">扬声器</div>
                  <el-tag :type="envCheck.speaker ? 'success' : 'warning'" size="small">
                    {{ envCheck.speaker ? '✓ 就绪' : '未测试' }}
                  </el-tag>
                </div>
                <div class="env-item" :class="{ ok: envCheck.network }">
                  <el-icon class="env-icon"><Connection /></el-icon>
                  <div class="env-text">网络连接</div>
                  <el-tag :type="envCheck.network ? 'success' : 'danger'" size="small">
                    {{ envCheck.network ? '✓ 在线' : '✗ 离线' }}
                  </el-tag>
                </div>
                <div class="env-item" :class="{ ok: envCheck.browser }">
                  <el-icon class="env-icon"><ChromeFilled /></el-icon>
                  <div class="env-text">浏览器</div>
                  <el-tag :type="envCheck.browser ? 'success' : 'warning'" size="small">
                    {{ envCheck.browser ? '✓ 支持' : '可能不兼容' }}
                  </el-tag>
                </div>
              </div>
              <el-button type="primary" size="large" @click="startEnvCheck" style="width: 100%;">
                <el-icon><Refresh /></el-icon>重新检测
              </el-button>
            </div>
          </el-col>
        </el-row>

        <div class="cp-action-bar">
          <el-button size="large" @click="prevStep">
            <el-icon><ArrowLeft /></el-icon>上一步
          </el-button>
          <el-button type="primary" size="large" :disabled="!envAllOk" @click="nextStep" style="min-width: 200px;">
            下一步 · 身份核验
            <el-icon><ArrowRight /></el-icon>
          </el-button>
        </div>
      </section>

      <!-- ============================== -->
      <!-- 步骤 3: 身份核验                -->
      <!-- ============================== -->
      <section v-show="currentStep === 3" class="cp-section">
        <h2 class="section-title">🆔 身份核验</h2>
        <p class="section-sub">请完成 3 步身份验证, 全程加密, 监管认可</p>

        <el-steps :active="['scan', 'face', 'sign', 'done'].indexOf(idStep)" finish-status="success" align-center>
          <el-step title="① 扫描身份证" />
          <el-step title="② 人脸识别" />
          <el-step title="③ 手写签名" />
        </el-steps>

        <div class="id-flow">
          <!-- 步骤 3.1: 扫身份证 -->
          <div v-if="idStep === 'scan'" class="id-card-area">
            <div class="id-card-mock">
              <div class="id-card-header">中华人民共和国 居民身份证</div>
              <div class="id-card-photo">👤</div>
              <div class="id-card-info">
                <div class="id-row"><span>姓名</span><strong>张 三</strong></div>
                <div class="id-row"><span>性别</span><strong>男</strong></div>
                <div class="id-row"><span>民族</span><strong>汉</strong></div>
                <div class="id-row"><span>出生</span><strong>1985-05-20</strong></div>
                <div class="id-row"><span>住址</span><strong>北京市朝阳区****</strong></div>
                <div class="id-row"><span>公民身份号码</span><strong>11010119850520****</strong></div>
              </div>
            </div>
            <el-button type="primary" size="large" @click="simulateIdScan" style="min-width: 240px;">
              <el-icon><Camera /></el-icon>
              点击开始扫描
            </el-button>
            <div class="hint">将身份证正面置于摄像头前方, 保持 5 秒</div>
          </div>

          <!-- 步骤 3.2: 人脸 -->
          <div v-else-if="idStep === 'face'" class="id-face-area">
            <div class="face-frame">
              <div class="face-emoji">😊</div>
              <div class="face-circle"></div>
              <div class="face-text">请正对摄像头, 眨眼 / 缓慢转头</div>
            </div>
            <el-button type="primary" size="large" @click="simulateFaceMatch" style="min-width: 240px;">
              开始人脸识别
            </el-button>
          </div>

          <!-- 步骤 3.3: 签名 -->
          <div v-else-if="idStep === 'sign'" class="id-sign-area">
            <h3 style="text-align: center; margin: 16px 0;">✍️ 请在下方手写签名</h3>
            <div class="sign-frame">
              <canvas
                ref="signCanvasRef"
                width="500"
                height="180"
                class="sign-canvas"
                @mousedown="startDraw"
                @mousemove="draw"
                @mouseup="endDraw"
                @mouseleave="endDraw"
                @touchstart.prevent="startDraw"
                @touchmove.prevent="draw"
                @touchend="endDraw"
              />
              <div v-if="!hasOldSignature" class="sign-placeholder">请用鼠标 / 手指在方框内签名</div>
            </div>
            <div class="sign-actions">
              <el-button @click="clearSignature">
                <el-icon><RefreshLeft /></el-icon>清除
              </el-button>
              <el-button type="primary" size="large" @click="confirmIdentity" :disabled="!hasOldSignature" style="min-width: 200px;">
                确认提交
              </el-button>
            </div>
          </div>

          <!-- 完成 -->
          <div v-else class="id-done">
            <div class="big-check">✅</div>
            <h3>身份核验完成</h3>
            <p>正在进入风险评估...</p>
          </div>
        </div>

        <div class="cp-action-bar">
          <el-button size="large" @click="prevStep" :disabled="idStep === 'done'">
            <el-icon><ArrowLeft /></el-icon>上一步
          </el-button>
        </div>
      </section>

      <!-- ============================== -->
      <!-- 步骤 4: 风险评估                -->
      <!-- ============================== -->
      <section v-show="currentStep === 4" class="cp-section">
        <h2 class="section-title">📊 投资者风险评估</h2>
        <p class="section-sub">监管要求, 销售前必须完成风险评估问卷</p>

        <el-progress :percentage="Math.round(riskProgressPct)" :stroke-width="10" />

        <div v-if="Object.keys(riskAnswers).length < RISK_QUESTIONS.length" class="risk-quiz">
          <div class="quiz-counter">
            第 {{ currentRiskIdx + 1 }} / {{ RISK_QUESTIONS.length }} 题
          </div>
          <h2 class="quiz-q">{{ currentQuestion.q }}</h2>
          <div class="quiz-options">
            <button
              v-for="opt in currentQuestion.options"
              :key="opt.v"
              class="quiz-option"
              :class="{ selected: riskAnswers[currentQuestion.key] === opt.s.toString() }"
              @click="answerRisk(currentQuestion.key, opt.s)"
            >
              <span class="opt-v">{{ opt.v }}</span>
              <span class="opt-l">{{ opt.l }}</span>
            </button>
          </div>
        </div>

        <div v-else class="risk-result">
          <div class="result-ring" :style="{ '--c': riskLevelLabel.color }">
            <div class="result-score">{{ riskScore }}</div>
            <div class="result-level">{{ riskLevel }}</div>
            <div class="result-label">{{ riskLevelLabel.label }}</div>
          </div>
          <div class="result-desc">
            <h3>{{ riskLevelLabel.desc }}</h3>
            <p>产品风险等级: <span class="risk-pill" :class="productInfo.riskLevel">{{ productInfo.riskLevel }}</span></p>
            <p>
              <el-tag v-if="isMatch" type="success" size="large">✓ 风险等级匹配, 可继续</el-tag>
              <el-tag v-else type="warning" size="large">⚠️ 风险等级不匹配, 需主动申请</el-tag>
            </p>
          </div>
        </div>
      </section>

      <!-- ============================== -->
      <!-- 步骤 5: 录制 (8 节点时间轴)     -->
      <!-- ============================== -->
      <section v-show="currentStep === 5" class="cp-section recording">
        <h2 class="section-title">🎬 双录录制中</h2>

        <!-- v1.5 坐席推送文件提示 -->
        <el-alert
          v-if="latestPushedFile"
          type="info"
          :closable="false"
          show-icon
          style="margin-bottom: 12px;"
          :title="`📤 坐席推送给您: ${latestPushedFile.fileName}`"
        >
          <template #default>
            <div style="display: flex; align-items: center; gap: 8px; flex-wrap: wrap;">
              <el-tag size="small">{{ latestPushedFile.fileType }}</el-tag>
              <span v-if="latestPushedFile.remark" style="color: var(--ink-3); font-size: 12px;">备注: {{ latestPushedFile.remark }}</span>
              <el-button size="small" type="primary" @click="openFileViewer(latestPushedFile)">
                <el-icon><View /></el-icon>查看文件
              </el-button>
              <el-button v-if="!latestPushedFile.viewedAt" size="small" @click="markFileViewed(latestPushedFile)">
                <el-icon><Check /></el-icon>我已查看
              </el-button>
              <el-button v-if="latestPushedFile.fileCategory === 'CONTRACT' && !latestPushedFile.signedAt" size="small" type="success" @click="openSignPad(latestPushedFile)">
                <el-icon><EditPen /></el-icon>签署
              </el-button>
            </div>
          </template>
        </el-alert>

        <!-- 时间轴 -->
        <div class="rec-timeline">
          <div
            v-for="(n, idx) in CLIENT_NODES"
            :key="n.code"
            class="rec-node"
            :class="{
              active: idx === recNodeIdx && recRecording,
              completed: recCompleted.has(idx),
              critical: n.critical
            }"
          >
            <div class="rec-dot">
              <span v-if="recCompleted.has(idx)">✓</span>
              <span v-else>{{ idx + 1 }}</span>
            </div>
            <div class="rec-name">{{ n.name }}</div>
            <div v-if="n.critical" class="rec-star">★</div>
          </div>
        </div>

        <el-row :gutter="16">
          <el-col :span="14">
            <div class="card">
              <h3 class="card-title">
                <span>📹 我的画面</span>
                <el-tag v-if="recRecording" type="danger" size="small">
                  <span class="rec-dot"></span>REC {{ recFormattedTime }}
                </el-tag>
              </h3>
              <div class="cam-window">
                <video ref="recClientVideo" autoplay muted playsinline class="cam-video" />
                <div v-if="!recRecording" class="cam-overlay-big">
                  <div class="big-icon">📹</div>
                  <el-button type="primary" size="large" @click="startRecording" style="margin-top: 16px;">
                    <el-icon><VideoCamera /></el-icon>开始录制
                  </el-button>
                </div>
                <div v-else class="rec-watermark">
                  <div class="wm-top">🔴 双录录制中 · {{ recFormattedTime }}</div>
                  <div class="wm-bot">业务 {{ businessId }} · 节点 {{ recNodeIdx + 1 }}</div>
                </div>
              </div>
              <el-progress
                :percentage="Math.round(recProgressPct)"
                :stroke-width="8"
                :color="recProgressPct === 100 ? '#2f6f5e' : '#b8860b'"
              />
            </div>
          </el-col>

          <el-col :span="10">
            <div class="card">
              <h3 class="card-title">
                <span>📋 当前节点</span>
                <el-tag v-if="currentNode.critical" type="danger" size="small">★ 关键</el-tag>
              </h3>
              <div class="node-card">
                <div class="node-name">{{ currentNode.name }}</div>
                <div class="node-desc">{{ currentNode.desc }}</div>
                <div class="node-duration">建议时长 {{ currentNode.dur }} 秒</div>
              </div>
              <h4 style="margin: 12px 0 8px; font-size: 13px;">必读 / 必问内容</h4>
              <div class="mandatory-list">
                <div v-for="(m, i) in currentNode.mandatory" :key="i" class="mandatory-item">
                  <span class="m-num">{{ i + 1 }}</span>
                  <span class="m-text">{{ m }}</span>
                  <el-button v-if="!recCompleted.has(recNodeIdx)" size="small" plain @click="recAsrInput = m">
                    📋
                  </el-button>
                </div>
              </div>
              <el-button
                v-if="!recRecording && recCompleted.size === 0"
                type="primary" size="large" @click="startRecording" style="width: 100%; margin-top: 12px;"
              >
                <el-icon><VideoCamera /></el-icon>开始录制
              </el-button>
              <el-button
                v-if="recRecording && !recCompleted.has(recNodeIdx)"
                type="success" size="large" @click="completeCurrentNode" style="width: 100%; margin-top: 12px;"
              >
                ✓ {{ currentNode.critical ? '我已确认 · 进入下一节点' : '本节点完成' }}
              </el-button>
            </div>

            <div class="card">
              <h3 class="card-title">📝 文本输入 (备选)</h3>
              <el-input
                v-model="recAsrInput"
                type="textarea" :rows="3"
                placeholder="如不便使用语音, 可在此输入对话文本"
              />
            </div>
          </el-col>
        </el-row>

        <div class="cp-action-bar">
          <el-button size="large" @click="stopRecording" v-if="recRecording" type="warning">
            <el-icon><VideoPause /></el-icon>暂停 / 终止
          </el-button>
          <el-button
            v-if="recCompleted.size === CLIENT_NODES.length"
            type="primary" size="large" @click="nextStep" style="min-width: 200px;"
          >
            下一步 · 签字完成
            <el-icon><ArrowRight /></el-icon>
          </el-button>
        </div>
      </section>

      <!-- ============================== -->
      <!-- 步骤 6: 完成                    -->
      <!-- ============================== -->
      <section v-show="currentStep === 6" class="cp-section done">
        <div class="done-hero">
          <div class="done-check">✅</div>
          <h1>双录已完成!</h1>
          <p class="done-sub">业务 {{ businessId }} 归档成功, 合同已生效</p>
        </div>

        <el-row :gutter="16">
          <el-col :span="14">
            <div class="card">
              <h3 class="card-title">📋 合同摘要</h3>
              <el-descriptions :column="2" border>
                <el-descriptions-item label="产品">{{ productInfo.name }}</el-descriptions-item>
                <el-descriptions-item label="金额">¥ {{ productInfo.amount.toLocaleString() }}</el-descriptions-item>
                <el-descriptions-item label="风险等级">
                  <span class="risk-pill" :class="productInfo.riskLevel">{{ productInfo.riskLevel }}</span>
                </el-descriptions-item>
                <el-descriptions-item label="客户风险">
                  <span class="risk-pill" :class="riskLevel">{{ riskLevel }}</span>
                </el-descriptions-item>
                <el-descriptions-item label="犹豫期至">{{ hesPeriodEnd }}</el-descriptions-item>
                <el-descriptions-item label="录像 ID">
                  <span class="mono">{{ recRecordingId || '已生成' }}</span>
                </el-descriptions-item>
              </el-descriptions>
            </div>

            <div class="card warning">
              <h3 class="card-title">⏰ 15 天犹豫期 (重要)</h3>
              <el-alert type="warning" :closable="false" show-icon
                title="您有 15 天犹豫期"
                :description="`自合同生效起 15 天内 (至 ${hesPeriodEnd}), 您可无理由退保, 工本费不超过 10 元。`"
              />
              <ul class="hes-list">
                <li><strong>D+1 天</strong>: 系统将推送保单摘要 + 答疑入口 (微信 + 短信)</li>
                <li><strong>D+7 天</strong>: 系统将询问您是否有疑问 (微信推送)</li>
                <li><strong>D+14 天</strong>: 系统将提醒您犹豫期即将结束 (短信)</li>
                <li>如需退保, 回复 1 或拨打 955XX 客服热线</li>
              </ul>
            </div>
          </el-col>

          <el-col :span="10">
            <div class="card">
              <h3 class="card-title">👨‍💼 您的理财经理</h3>
              <div class="advisor-card big">
                <div class="advisor-avatar">{{ advisor.avatar }}</div>
                <div>
                  <div class="advisor-name">{{ advisor.name }}</div>
                  <div class="advisor-meta">{{ advisor.branch }}</div>
                  <div class="advisor-meta">📱 {{ advisor.phone }}</div>
                </div>
              </div>
            </div>

            <div class="card primary">
              <h3 class="card-title">📞 客服与申诉</h3>
              <div class="contact-list">
                <div class="contact-item">
                  <span class="contact-icon">📞</span>
                  <div>
                    <div class="contact-label">客服热线</div>
                    <div class="contact-value mono">955XX (24h)</div>
                  </div>
                </div>
                <div class="contact-item">
                  <span class="contact-icon">💬</span>
                  <div>
                    <div class="contact-label">在线客服</div>
                    <div class="contact-value">APP / 微信公众号</div>
                  </div>
                </div>
                <div class="contact-item">
                  <span class="contact-icon">⚖️</span>
                  <div>
                    <div class="contact-label">金融消费者权益</div>
                    <div class="contact-value">12363</div>
                  </div>
                </div>
                <div class="contact-item">
                  <span class="contact-icon">📺</span>
                  <div>
                    <div class="contact-label">调阅本次录像</div>
                    <div class="contact-value">携身份证至网点</div>
                  </div>
                </div>
              </div>
            </div>
          </el-col>
        </el-row>
      </section>
    </main>

    <!-- v1.5 文件查看器 -->
    <el-dialog v-model="showFileViewer" :title="viewingFile?.fileName || '文件查看'" width="700px">
      <div v-if="viewingFile" class="file-viewer">
        <div class="file-info">
          <el-tag size="small">{{ viewingFile.fileType }}</el-tag>
          <el-tag size="small">{{ viewingFile.fileCategory }}</el-tag>
          <span class="file-time">推送时间: {{ viewingFile.pushedAt ? new Date(viewingFile.pushedAt).toLocaleString('zh-CN') : '—' }}</span>
        </div>
        <div v-if="viewingFile.remark" class="file-remark">
          <strong>坐席备注:</strong> {{ viewingFile.remark }}
        </div>
        <div class="file-preview">
          <div class="preview-placeholder">
            <div class="big-icon">📄</div>
            <div>{{ viewingFile.fileName }}</div>
            <div style="font-size: 11px; color: var(--ink-3); margin-top: 4px;">
              (PDF/图片实际预览接 CDN, 此处模拟)
            </div>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="showFileViewer = false">关闭</el-button>
        <el-button
          v-if="viewingFile && viewingFile.fileCategory === 'CONTRACT' && !viewingFile.signedAt"
          type="success"
          @click="openSignPad(viewingFile); showFileViewer = false"
        >
          <el-icon><EditPen /></el-icon>签署合同
        </el-button>
      </template>
    </el-dialog>

    <!-- v1.5 客户签署弹窗 -->
    <el-dialog v-model="showSignPad" title="电子签署" width="500px">
      <p v-if="signingFile" style="margin: 0 0 12px;">
        请在下方手写签名以确认: <strong>{{ signingFile.fileName }}</strong>
      </p>
      <div class="sign-frame">
        <canvas
          ref="customerSignRef"
          width="450"
          height="160"
          class="sign-canvas"
          @mousedown="startSignDraw"
          @mousemove="drawSign"
          @mouseup="endSignDraw"
          @mouseleave="endSignDraw"
          @touchstart.prevent="startSignDraw"
          @touchmove.prevent="drawSign"
          @touchend="endSignDraw"
        />
        <div v-if="!hasSign" class="sign-placeholder">请用鼠标 / 手指在方框内签名</div>
      </div>
      <template #footer>
        <el-button @click="showSignPad = false">取消</el-button>
        <el-button @click="clearSign">
          <el-icon><RefreshLeft /></el-icon>清除
        </el-button>
        <el-button type="danger" plain @click="rejectFile" v-if="signingFile">
          <el-icon><Close /></el-icon>拒签
        </el-button>
        <el-button type="success" :disabled="!hasSign" @click="confirmSign">
          <el-icon><Check /></el-icon>确认签署
        </el-button>
      </template>
    </el-dialog>

    <!-- 底部固定栏 -->
    <footer class="cp-footer">
      <div class="cp-footer-inner">
        <div class="cp-footer-left">
          <span class="cp-footer-brand">智能双录系统 v1.5</span>
          <span class="cp-footer-sep">|</span>
          <span>业务 {{ businessId }}</span>
          <span class="cp-footer-sep">|</span>
          <span>当前节点 {{ currentStep }} / {{ STEPS.length }}</span>
        </div>
        <div class="cp-footer-right">
          <span class="cp-footer-secure">🔒 SSL 加密 · 全程留痕</span>
        </div>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.client-portal { min-height: 100vh; background: linear-gradient(180deg, #f5f3ec 0%, #e8e3d4 100%); display: flex; flex-direction: column; }

/* Header */
.cp-header { background: linear-gradient(135deg, #1e2a47 0%, #2a3a5c 100%); color: #fff; padding: 12px 24px; box-shadow: 0 2px 12px rgba(0,0,0,0.1); position: sticky; top: 0; z-index: 100; }
.cp-header-inner { display: flex; align-items: center; gap: 24px; max-width: 1280px; margin: 0 auto; }
.cp-brand { display: flex; align-items: center; gap: 10px; min-width: 220px; }
.cp-logo { font-size: 32px; }
.cp-brand-name { font-size: 16px; font-weight: 700; }
.cp-brand-sub { font-size: 11px; color: rgba(255,255,255,0.6); }
.cp-progress { flex: 1; }
.cp-business { text-align: right; }
.cp-biz-label { font-size: 10px; color: rgba(255,255,255,0.6); text-transform: uppercase; letter-spacing: 1px; }
.cp-biz-id { font-size: 13px; font-weight: 600; }
.cp-progress-bar { height: 3px; background: rgba(255,255,255,0.1); margin-top: 12px; }
.cp-progress-fill { height: 100%; background: linear-gradient(90deg, #b8860b 0%, #d4a52d 100%); transition: width 0.5s; }

/* Main */
.cp-main { flex: 1; padding: 24px; max-width: 1280px; margin: 0 auto; width: 100%; }
.cp-section { animation: fadeIn 0.4s; }

@keyframes fadeIn { from { opacity: 0; transform: translateY(12px); } to { opacity: 1; transform: translateY(0); } }

.section-title { font-size: 24px; font-weight: 700; color: var(--primary); margin: 0 0 4px; }
.section-sub { font-size: 14px; color: var(--ink-3); margin: 0 0 24px; }

/* Welcome */
.welcome-hero { text-align: center; padding: 32px 0; }
.welcome-badge { display: inline-block; padding: 6px 16px; background: rgba(184, 134, 11, 0.1); color: var(--accent); border-radius: 20px; font-size: 13px; margin-bottom: 16px; font-weight: 600; }
.welcome-title { font-size: 36px; margin: 0 0 8px; color: var(--primary); }
.welcome-title .accent { color: var(--accent); }
.welcome-sub { font-size: 15px; color: var(--ink-3); }
.product-desc { padding: 12px; background: var(--bg-2); border-radius: 6px; margin-top: 12px; font-size: 13px; line-height: 1.6; }

.advisor-card { display: flex; align-items: center; gap: 12px; padding: 12px; background: var(--bg-2); border-radius: 8px; margin-bottom: 12px; }
.advisor-card.big { padding: 16px; }
.advisor-avatar { font-size: 48px; }
.advisor-name { font-size: 18px; font-weight: 700; }
.advisor-meta { font-size: 12px; color: var(--ink-3); margin-top: 2px; }

.agreement-text { padding: 16px; font-size: 13px; line-height: 1.8; }
.agreement-text h4 { color: var(--primary); }
.agreement-text p { margin: 4px 0; }
.agreement-checkbox { padding: 12px 16px; background: var(--bg-2); border-radius: 0 0 8px 8px; }

/* Env */
.video-frame { position: relative; background: #000; border-radius: 8px; overflow: hidden; aspect-ratio: 4/3; }
.cam-video { width: 100%; height: 100%; object-fit: cover; transform: scaleX(-1); }
.cam-placeholder { position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; color: #fff; }
.cam-icon { font-size: 48px; opacity: 0.5; }
.cam-overlay { position: absolute; top: 12px; left: 12px; }
.rec-badge { background: var(--accent-2); color: #fff; padding: 4px 10px; border-radius: 4px; font-size: 11px; font-weight: 600; }
.mic-section { display: flex; align-items: center; gap: 12px; padding: 12px; background: var(--bg-2); border-radius: 0 0 8px 8px; }
.mic-label { font-size: 13px; font-weight: 600; }
.mic-bar { display: flex; gap: 2px; flex: 1; }
.mic-cell { flex: 1; height: 18px; background: rgba(0,0,0,0.1); border-radius: 2px; transition: all 0.1s; }
.mic-cell.active { background: var(--green); }

.env-list { display: flex; flex-direction: column; gap: 8px; margin-bottom: 16px; }
.env-item { display: flex; align-items: center; gap: 12px; padding: 12px; background: var(--bg-2); border-radius: 6px; }
.env-item.ok { background: rgba(47, 111, 94, 0.08); }
.env-icon { font-size: 20px; color: var(--ink-3); }
.env-item.ok .env-icon { color: var(--green); }
.env-text { flex: 1; font-size: 14px; font-weight: 500; }

/* ID Flow */
.id-flow { background: var(--card); border-radius: 12px; padding: 32px; margin-top: 16px; min-height: 400px; display: flex; flex-direction: column; align-items: center; justify-content: center; }
.id-card-mock { background: linear-gradient(135deg, #e8f0ff 0%, #d4e2ff 100%); border: 2px solid #3b6b8c; border-radius: 12px; padding: 16px; width: 360px; box-shadow: 0 4px 12px rgba(0,0,0,0.1); margin-bottom: 16px; }
.id-card-header { background: var(--accent-2); color: #fff; padding: 6px 12px; border-radius: 4px; text-align: center; font-size: 13px; font-weight: 700; margin-bottom: 12px; }
.id-card-photo { font-size: 80px; text-align: center; padding: 16px 0; }
.id-card-info { padding: 0 8px; }
.id-row { display: flex; justify-content: space-between; padding: 4px 0; font-size: 12px; }
.id-row span { color: var(--ink-3); }
.id-row strong { color: var(--ink); }

.face-frame { position: relative; width: 280px; height: 280px; display: flex; align-items: center; justify-content: center; margin-bottom: 16px; }
.face-emoji { font-size: 100px; z-index: 2; }
.face-circle { position: absolute; inset: 0; border: 4px solid var(--accent); border-radius: 50%; animation: pulse-circle 1.5s ease-in-out infinite; }
.face-text { position: absolute; bottom: -32px; font-size: 13px; color: var(--ink-3); white-space: nowrap; }
@keyframes pulse-circle { 0%, 100% { transform: scale(1); opacity: 1; } 50% { transform: scale(1.05); opacity: 0.6; } }

.sign-frame { position: relative; width: 500px; max-width: 100%; background: #fff; border: 2px dashed var(--ink-3); border-radius: 8px; margin: 16px auto; }
.sign-canvas { display: block; width: 100%; height: 180px; cursor: crosshair; touch-action: none; }
.sign-placeholder { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; color: var(--ink-3); pointer-events: none; font-style: italic; }
.sign-actions { display: flex; gap: 8px; justify-content: center; }

.id-done { text-align: center; }
.big-check { font-size: 80px; margin-bottom: 16px; }
.hint { font-size: 12px; color: var(--ink-3); margin-top: 8px; }

/* Risk Quiz */
.risk-quiz { background: var(--card); border-radius: 12px; padding: 32px; margin-top: 16px; }
.quiz-counter { text-align: center; color: var(--ink-3); font-size: 14px; margin-bottom: 16px; }
.quiz-q { font-size: 28px; text-align: center; margin: 0 0 32px; color: var(--primary); }
.quiz-options { display: flex; flex-direction: column; gap: 12px; max-width: 600px; margin: 0 auto; }
.quiz-option { display: flex; align-items: center; gap: 16px; padding: 16px 24px; background: var(--bg-2); border: 2px solid transparent; border-radius: 12px; cursor: pointer; transition: all 0.2s; text-align: left; }
.quiz-option:hover { background: rgba(184, 134, 11, 0.08); border-color: var(--accent); transform: translateX(4px); }
.quiz-option.selected { background: rgba(47, 111, 94, 0.08); border-color: var(--green); }
.opt-v { font-size: 24px; font-weight: 700; color: var(--accent); width: 40px; }
.opt-l { font-size: 16px; }

.risk-result { background: var(--card); border-radius: 12px; padding: 48px; margin-top: 16px; display: flex; align-items: center; justify-content: center; gap: 48px; }
.result-ring { width: 200px; height: 200px; border-radius: 50%; background: conic-gradient(var(--c) 360deg, var(--bg-2) 0deg); display: flex; flex-direction: column; align-items: center; justify-content: center; position: relative; }
.result-ring::before { content: ''; position: absolute; inset: 12px; background: var(--card); border-radius: 50%; }
.result-score { font-size: 48px; font-weight: 700; color: var(--c); position: relative; z-index: 1; }
.result-level { font-size: 24px; font-weight: 700; color: var(--c); position: relative; z-index: 1; }
.result-label { font-size: 12px; color: var(--ink-3); position: relative; z-index: 1; }
.result-desc h3 { margin: 0 0 12px; color: var(--primary); }
.result-desc p { margin: 6px 0; }

/* Recording */
.recording { background: var(--card); border-radius: 12px; padding: 24px; }
.rec-timeline { display: flex; gap: 4px; margin-bottom: 24px; padding: 16px 0; overflow-x: auto; }
.rec-node { flex: 1; display: flex; flex-direction: column; align-items: center; gap: 4px; min-width: 90px; position: relative; }
.rec-node:not(:last-child)::after { content: ''; position: absolute; top: 18px; left: 50%; width: 100%; height: 2px; background: var(--line); }
.rec-node.completed:not(:last-child)::after { background: var(--green); }
.rec-dot { width: 36px; height: 36px; border-radius: 50%; background: var(--card); border: 2px solid var(--line); display: flex; align-items: center; justify-content: center; font-weight: 700; font-size: 14px; z-index: 1; }
.rec-node.active .rec-dot { background: var(--accent); color: #fff; border-color: var(--accent); box-shadow: 0 0 0 6px rgba(184, 134, 11, 0.15); }
.rec-node.completed .rec-dot { background: var(--green); color: #fff; border-color: var(--green); }
.rec-name { font-size: 11px; font-weight: 600; text-align: center; }
.rec-star { color: var(--accent-2); font-size: 12px; }

.cam-window { position: relative; background: #000; border-radius: 8px; overflow: hidden; aspect-ratio: 4/3; }
.cam-overlay-big { position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; color: #fff; }
.big-icon { font-size: 64px; opacity: 0.3; }
.rec-watermark { position: absolute; inset: 0; pointer-events: none; }
.wm-top { position: absolute; top: 12px; left: 12px; background: var(--accent-2); color: #fff; padding: 6px 12px; border-radius: 4px; font-size: 12px; font-weight: 600; }
.wm-bot { position: absolute; bottom: 12px; left: 12px; background: rgba(0,0,0,0.6); color: #fff; padding: 4px 10px; border-radius: 4px; font-size: 11px; font-family: monospace; }

.node-card { padding: 16px; background: var(--bg-2); border-radius: 8px; margin-bottom: 12px; }
.node-name { font-size: 18px; font-weight: 700; color: var(--primary); }
.node-desc { font-size: 13px; color: var(--ink-3); margin-top: 4px; }
.node-duration { font-size: 12px; color: var(--ink-3); margin-top: 8px; font-family: 'JetBrains Mono', monospace; }

.mandatory-list { display: flex; flex-direction: column; gap: 6px; }
.mandatory-item { display: flex; align-items: center; gap: 8px; padding: 8px; background: var(--bg-2); border-radius: 4px; }
.m-num { width: 20px; height: 20px; border-radius: 50%; background: var(--accent); color: #fff; display: flex; align-items: center; justify-content: center; font-size: 11px; font-weight: 700; }
.m-text { flex: 1; font-size: 13px; }

/* Done */
.done { text-align: center; }
.done-hero { padding: 48px 0; }
.done-check { font-size: 100px; }
.done-hero h1 { font-size: 48px; margin: 16px 0 8px; color: var(--green); }
.done-sub { font-size: 18px; color: var(--ink-3); }

.hes-list { padding-left: 20px; line-height: 1.8; }
.hes-list li { margin: 4px 0; }
.contact-list { display: flex; flex-direction: column; gap: 12px; }
.contact-item { display: flex; align-items: center; gap: 12px; padding: 10px; background: var(--bg-2); border-radius: 6px; }
.contact-icon { font-size: 24px; }
.contact-label { font-size: 11px; color: var(--ink-3); }
.contact-value { font-size: 14px; font-weight: 600; }

.cp-action-bar { display: flex; justify-content: center; gap: 12px; margin-top: 24px; padding-top: 24px; border-top: 1px solid var(--line); }

.file-viewer { padding: 8px 0; }
.file-info { display: flex; gap: 8px; align-items: center; margin-bottom: 12px; }
.file-time { font-size: 11px; color: var(--ink-3); margin-left: auto; font-family: 'JetBrains Mono', monospace; }
.file-remark { padding: 8px 12px; background: rgba(184, 134, 11, 0.05); border-left: 3px solid var(--accent); border-radius: 0 4px 4px 0; font-size: 13px; margin-bottom: 12px; }
.file-preview { background: var(--bg-2); border-radius: 8px; padding: 40px; text-align: center; min-height: 300px; display: flex; flex-direction: column; align-items: center; justify-content: center; }
.preview-placeholder .big-icon { font-size: 64px; opacity: 0.3; }

.sign-frame { position: relative; width: 100%; background: #fff; border: 2px dashed var(--ink-3); border-radius: 8px; margin: 8px 0; }
.sign-canvas { display: block; width: 100%; height: 160px; cursor: crosshair; touch-action: none; }
.sign-placeholder { position: absolute; inset: 0; display: flex; align-items: center; justify-content: center; color: var(--ink-3); pointer-events: none; font-style: italic; }

/* Footer */
.cp-footer { background: var(--primary); color: #fff; padding: 12px 24px; }
.cp-footer-inner { display: flex; justify-content: space-between; max-width: 1280px; margin: 0 auto; font-size: 12px; }
.cp-footer-brand { font-weight: 700; }
.cp-footer-sep { color: rgba(255,255,255,0.3); margin: 0 8px; }
.cp-footer-secure { color: var(--accent); font-weight: 600; }

/* Risk pill (复用) */
.risk-pill { display: inline-block; padding: 2px 8px; border-radius: 4px; font-size: 11px; font-weight: 700; font-family: 'JetBrains Mono', monospace; }
.risk-pill.R1, .risk-pill.P1 { background: var(--ink-3); color: #fff; }
.risk-pill.R2, .risk-pill.P2 { background: var(--blue); color: #fff; }
.risk-pill.R3, .risk-pill.P3 { background: var(--accent); color: #fff; }
.risk-pill.R4, .risk-pill.P4 { background: var(--accent-2); color: #fff; }
.risk-pill.R5, .risk-pill.P5 { background: var(--primary); color: #fff; }
.risk-pill.C1 { background: var(--ink-3); color: #fff; }
.risk-pill.C2 { background: var(--blue); color: #fff; }
.risk-pill.C3 { background: var(--accent); color: #fff; }
.risk-pill.C4 { background: var(--accent-2); color: #fff; }
.risk-pill.C5 { background: var(--primary); color: #fff; }

.card { background: var(--card); border: 1px solid var(--line); border-radius: 12px; padding: 16px; }
.card-title { font-size: 15px; font-weight: 600; margin: 0 0 12px; color: var(--ink); display: flex; justify-content: space-between; align-items: center; }
.rec-dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; background: #fff; animation: pulse 1s infinite; }
@keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.3; } }
</style>
