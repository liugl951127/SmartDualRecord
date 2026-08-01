<template>
  <div class="record-flow">
    <!-- 权限申请卡 -->
    <PermissionGate
      :visible="!permissionGranted"
      @granted="onPermissionGranted"
      @skip="onPermissionSkip"
    />

    <div class="record-header">
      <div class="biz-id">{{ businessId }}</div>
      <div class="timer">{{ formattedTime }}</div>
    </div>

    <div class="video-wrap">
      <video ref="videoEl" autoplay muted playsinline class="video"></video>
      <canvas ref="canvasEl" class="watermark"></canvas>
      <div class="rec-badge" v-if="recording">
        <div class="rec-dot"></div>
        <span>REC</span>
      </div>
      <div class="node-progress">{{ currentNode + 1 }} / {{ nodes.length }}</div>
      <div v-if="state" class="state-badge">状态: {{ stateLabel(state) }}</div>
    </div>

    <div class="timeline-wrap">
      <div class="timeline">
        <div
          v-for="(n, i) in nodes"
          :key="n.id"
          :class="['timeline-item', getNodeCls(i)]"
        >
          <div class="timeline-dot">
            <span v-if="i < currentNode">✓</span>
            <span v-else-if="i === currentNode">{{ i + 1 }}</span>
            <span v-else>{{ i + 1 }}</span>
          </div>
          <div class="timeline-content">
            <div class="node-name">{{ n.name }}</div>
            <div class="node-desc">{{ n.desc }}</div>
          </div>
        </div>
      </div>
    </div>

    <div class="script-area" v-if="script">
      <div class="script-title">📋 当前话术</div>
      <div class="script-content">
        <div class="script-section">
          <div class="ss-label">必播项</div>
          <ul>
            <li v-for="(d, i) in script.mandatoryDisclosure" :key="i">
              <span class="line-num">{{ i + 1 }}.</span>{{ d }}
            </li>
          </ul>
        </div>
        <div class="script-section">
          <div class="ss-label">必问题</div>
          <ul>
            <li v-for="(q, i) in script.requiredQuestions" :key="i">
              <span class="line-num">{{ i + 1 }}.</span>{{ q }}
            </li>
          </ul>
        </div>
        <div class="script-section">
          <div class="ss-label">禁播词 (触发即失败)</div>
          <div class="phrases">
            <span v-for="p in script.forbiddenPhrases" :key="p" class="phrase">{{ p }}</span>
          </div>
        </div>
      </div>
    </div>

    <div class="control-bar">
      <div class="ctrl-row">
        <div :class="['ctrl-btn', currentNode === 0 && 'disabled']" @click="prev">◀ 上一节点</div>
        <div :class="['ctrl-btn', recording ? 'recording' : 'start']" @click="toggleRecord">
          {{ recording ? '⏸ 暂停' : '▶ 开始录制' }}
        </div>
        <div :class="['ctrl-btn', currentNode === nodes.length - 1 && 'disabled']" @click="next">
          下一节点 ▶
        </div>
      </div>
      <van-button
        v-if="currentNode === nodes.length - 1"
        block
        round
        size="large"
        type="primary"
        @click="onComplete"
      >
        完成双录 · 提交签字
      </van-button>
    </div>

    <van-dialog
      v-model:show="showSign"
      title="电子签名"
      show-cancel-button
      confirm-button-text="确认提交"
      @confirm="onSign"
    >
      <div class="sign-area">
        <p class="sign-tip">请在下方手写您的签名</p>
        <canvas
          ref="signCanvasEl"
          class="sign-canvas"
          width="320"
          height="160"
          @touchstart="onSignStart"
          @touchmove="onSignMove"
          @touchend="onSignEnd"
        ></canvas>
        <div class="sign-actions">
          <van-button size="small" plain @click="clearSign">清空</van-button>
        </div>
      </div>
    </van-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { recordingApi, scriptApi } from '@/api'
import PermissionGate from '@/components/PermissionGate.vue'

const route = useRoute()
const router = useRouter()
const businessId = computed(() => route.params.businessId as string)

const nodes = [
  { id: 'NODE_01_IDENTITY', name: '身份核验', desc: '出示身份证 + 联网核查' },
  { id: 'NODE_02_DISCLOSURE', name: '风险揭示', desc: '产品基本信息 / 风险等级' },
  { id: 'NODE_03_PRODUCT', name: '产品展示', desc: '产品特点 / 收益 / 费用' },
  { id: 'NODE_04_RIGHTS', name: '权利义务', desc: '犹豫期 / 赎回 / 投诉' },
  { id: 'NODE_05_TRUTH_TELL', name: '如实告知', desc: '客户风险承受能力确认' },
  { id: 'NODE_06_CONFIRM', name: '明确肯定', desc: '客户明确购买意愿' },
  { id: 'NODE_07_SIGN', name: '签署', desc: '合同 + 风险揭示书签字' },
  { id: 'NODE_08_FOLLOWUP', name: '补充询问', desc: '15 天 3 次回访' }
]

const currentNode = ref(0)
const recording = ref(false)
const nodeStart = ref(Date.now())
const nodeElapsed = ref(0)
const state = ref<string>('')
const script = ref<any>(null)
const permissionGranted = ref(false)  // 权限已批准

const videoEl = ref<HTMLVideoElement>()
const canvasEl = ref<HTMLCanvasElement>()
const signCanvasEl = ref<HTMLCanvasElement>()
const showSign = ref(false)

let stream: MediaStream | null = null
let mediaRecorder: MediaRecorder | null = null
let chunks: Blob[] = []
let watermarkTimer: any = null
let signContext: CanvasRenderingContext2D | null = null
let isDrawing = false
let lastPos = { x: 0, y: 0 }

// ============ 权限处理 ============
function onPermissionGranted(s: MediaStream) {
  permissionGranted.value = true
  stream = s
  if (videoEl.value) {
    videoEl.value.srcObject = s
  }
  showToast('🎥 摄像头已开启')
}
function onPermissionSkip() {
  permissionGranted.value = true
  showToast('已跳过, 仅查看模式 (无法录制)')
}

const formattedTime = computed(() => {
  const total = (Date.now() - nodeStart.value) / 1000
  const m = Math.floor(total / 60)
  const s = Math.floor(total % 60)
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
})

function getNodeCls(i: number) {
  if (i < currentNode.value) return 'completed'
  if (i === currentNode.value) return 'active'
  return ''
}

function stateLabel(s: string) {
  const map: any = {
    INIT: '待开始', IDENTITY_VERIFIED: '已核身', RISK_ASSESSED: '已评估',
    SCRIPT_LOADED: '已加载话术', RECORDING: '录制中', RECORDED: '已录制',
    AI_QA: 'AI 质检', AI_QA_PASSED: 'AI 通过', AI_QA_FLAGGED: 'AI 标记',
    HUMAN_REVIEW: '人工复核', HUMAN_REVIEWED: '复核完成', SIGNED: '已签署',
    ARCHIVED: '已归档', FAILED: '已失败', OFFLINE_FAILED: '线下失败', ROLLED_BACK: '已回滚'
  }
  return map[s] || s
}

onMounted(async () => {
  await loadOverview()
  await loadScript()
  // 不再自动 startCamera, 改由 PermissionGate 处理
  // PermissionGate 弹窗, 用户授权后 onPermissionGranted 会接管
  nextTick(() => {
    if (videoEl.value) {
      // 监听 stream 变化, 渲染到 video
      const observer = new MutationObserver(() => {})
      observer.disconnect()
    }
  })
})

onUnmounted(() => {
  stopAll()
  if (watermarkTimer) clearInterval(watermarkTimer)
})

async function loadOverview() {
  try {
    const res: any = await recordingApi.overview(businessId.value)
    if (res?.business) {
      state.value = res.business.state
    }
  } catch (e) {
    // 业务可能尚未创建, 忽略
  }
}

async function loadScript() {
  try {
    // 1. 先获取话术 (用于显示)
    const overview: any = await recordingApi.overview(businessId.value)
    const productId = overview?.business?.productId
    if (productId) {
      const res: any = await scriptApi.getDbTemplate(productId)
      // 解析 JSON
      if (res.mandatoryDisclosure && typeof res.mandatoryDisclosure === 'string') {
        try { res.mandatoryDisclosure = JSON.parse(res.mandatoryDisclosure) } catch {}
      }
      if (res.requiredQuestions && typeof res.requiredQuestions === 'string') {
        try { res.requiredQuestions = JSON.parse(res.requiredQuestions) } catch {}
      }
      if (res.forbiddenPhrases && typeof res.forbiddenPhrases === 'string') {
        try { res.forbiddenPhrases = JSON.parse(res.forbiddenPhrases) } catch {}
      }
      script.value = res
    }
    // 2. 调用后端 script/load 让后端状态机进入 SCRIPT_LOADED
    if (productId && state.value === 'RISK_ASSESSED') {
      try {
        await recordingApi.loadScript(businessId.value, productId)
        state.value = 'SCRIPT_LOADED'
      } catch {}
    }
    // 3. 如果还在 INIT/未开始, 尝试进入开始录制
    if (state.value === 'SCRIPT_LOADED' || state.value === 'RISK_ASSESSED' || state.value === 'IDENTITY_VERIFIED') {
      try {
        await recordingApi.beginRecording(businessId.value)
        state.value = 'RECORDING'
      } catch {}
    }
  } catch (e) {
    console.warn('Load script failed', e)
  }
}

async function startCamera() {
  try {
    stream = await navigator.mediaDevices.getUserMedia({
      video: { width: 1280, height: 720, facingMode: 'user' },
      audio: { echoCancellation: true, noiseSuppression: true }
    })
    if (videoEl.value) videoEl.value.srcObject = stream
  } catch (e: any) {
    showToast('请允许访问摄像头和麦克风')
  }
}

function setupWatermark() {
  if (!canvasEl.value) return
  const canvas = canvasEl.value
  const ctx = canvas.getContext('2d')!
  canvas.width = 1280
  canvas.height = 720
  const draw = () => {
    ctx.clearRect(0, 0, canvas.width, canvas.height)
    ctx.fillStyle = 'rgba(255,255,255,0.7)'
    ctx.font = '20px monospace'
    const ts = new Date().toISOString().slice(0, 19).replace('T', ' ')
    ctx.fillText(`${businessId.value} | ${ts}`, 20, 40)
    ctx.fillText(`Node: ${nodes[currentNode.value].id}`, 20, 70)
  }
  draw()
  watermarkTimer = setInterval(draw, 1000)
}

function toggleRecord() {
  recording.value ? pauseRecord() : startRecord()
}

function startRecord() {
  if (!stream) { showToast('摄像头未就绪'); return }
  try {
    const mime = MediaRecorder.isTypeSupported('video/webm;codecs=vp9') ? 'video/webm;codecs=vp9' : 'video/webm'
    mediaRecorder = new MediaRecorder(stream, { mimeType: mime })
    mediaRecorder.ondataavailable = (e) => { if (e.data.size > 0) chunks.push(e.data) }
    mediaRecorder.start(1000)
    recording.value = true
  } catch (e: any) {
    showToast('录制启动失败')
  }
}

function pauseRecord() {
  if (mediaRecorder && mediaRecorder.state === 'recording') mediaRecorder.pause()
  recording.value = false
}

async function next() {
  if (currentNode.value < nodes.length - 1) {
    // 真实 API: 通知后端完成节点
    try {
      const res: any = await recordingApi.completeNode(businessId.value, {
        recId: 'rec-mobile-' + businessId.value,
        node: nodes[currentNode.value].id,
        asrText: '客户在节点 ' + nodes[currentNode.value].id + ' 完成确认'
      })
      if (res?.state) state.value = res.state
    } catch (e) {
      // 允许继续
    }
    currentNode.value++
    nodeStart.value = Date.now()
    chunks = []
  }
}

function prev() {
  if (currentNode.value > 0) {
    currentNode.value--
    nodeStart.value = Date.now()
  }
}

async function onComplete() {
  // 完成最后一个节点
  try {
    await recordingApi.completeNode(businessId.value, {
      recId: 'rec-mobile-' + businessId.value,
      node: nodes[currentNode.value].id,
      asrText: '客户在节点 ' + nodes[currentNode.value].id + ' 完成确认'
    })
  } catch {}
  showSign.value = true
  await nextTick()
  setupSignCanvas()
}

function setupSignCanvas() {
  if (!signCanvasEl.value) return
  const canvas = signCanvasEl.value
  const ratio = window.devicePixelRatio || 1
  canvas.width = 320 * ratio
  canvas.height = 160 * ratio
  canvas.style.width = '100%'
  canvas.style.height = '160px'
  signContext = canvas.getContext('2d')!
  signContext.scale(ratio, ratio)
  signContext.strokeStyle = '#000'
  signContext.lineWidth = 2
  signContext.lineCap = 'round'
}

function onSignStart(e: TouchEvent) {
  e.preventDefault()
  isDrawing = true
  const rect = signCanvasEl.value!.getBoundingClientRect()
  const t = e.touches[0]
  lastPos = { x: t.clientX - rect.left, y: t.clientY - rect.top }
}
function onSignMove(e: TouchEvent) {
  if (!isDrawing || !signContext) return
  e.preventDefault()
  const rect = signCanvasEl.value!.getBoundingClientRect()
  const t = e.touches[0]
  const x = t.clientX - rect.left
  const y = t.clientY - rect.top
  signContext.beginPath()
  signContext.moveTo(lastPos.x, lastPos.y)
  signContext.lineTo(x, y)
  signContext.stroke()
  lastPos = { x, y }
}
function onSignEnd() { isDrawing = false }
function clearSign() {
  if (signContext && signCanvasEl.value) {
    signContext.clearRect(0, 0, signCanvasEl.value.width, signCanvasEl.value.height)
  }
}

async function onSign() {
  try {
    // 1. 签字 (后端返回 204)
    await recordingApi.sign(businessId.value)
    state.value = 'SIGNED'
    // 2. 完成 (AI 终检)
    try {
      const final: any = await recordingApi.finalize(businessId.value, 'rec-mobile-' + businessId.value, '客户已签字, 双录完成')
      state.value = final?.result || 'AI_QA'
    } catch {}
    showToast('双录已完成')
    setTimeout(() => router.replace('/h5/orders'), 800)
  } catch (e: any) {
    showToast('提交失败: ' + (e?.message || '未知错误'))
  }
}

function stopAll() {
  if (mediaRecorder && mediaRecorder.state !== 'inactive') {
    try { mediaRecorder.stop() } catch {}
  }
  if (stream) {
    stream.getTracks().forEach(t => t.stop())
    stream = null
  }
}
</script>

<style lang="scss" scoped>
.record-flow {
  min-height: 100vh;
  background: var(--bg);
  display: flex;
  flex-direction: column;
}

.record-header {
  background: var(--primary);
  color: white;
  padding: 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-family: monospace;
  font-size: 13px;
}
.biz-id { color: var(--accent-light); }
.timer { font-size: 20px; font-weight: 700; }

.video-wrap {
  position: relative;
  background: #000;
  width: 100%;
  aspect-ratio: 16 / 9;
  overflow: hidden;
}
.video { width: 100%; height: 100%; object-fit: cover; }
.watermark { position: absolute; inset: 0; width: 100%; height: 100%; pointer-events: none; }
.rec-badge {
  position: absolute; top: 12px; left: 12px;
  background: rgba(238,10,36,0.85);
  color: white;
  padding: 4px 10px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 700;
  display: flex; align-items: center; gap: 4px;
}
.rec-dot {
  width: 8px; height: 8px;
  background: white;
  border-radius: 50%;
  animation: blink 1s infinite;
}
@keyframes blink { 0%, 100% { opacity: 1; } 50% { opacity: 0.3; } }
.node-progress {
  position: absolute; top: 12px; right: 12px;
  background: rgba(0,0,0,0.5);
  color: white;
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-family: monospace;
}
.state-badge {
  position: absolute; bottom: 12px; left: 12px;
  background: rgba(184,134,11,0.85);
  color: white;
  padding: 4px 10px;
  border-radius: 4px;
  font-size: 11px;
  font-family: monospace;
}

.timeline-wrap { background: white; padding: 12px 16px; margin: 12px; border-radius: 12px; }
.timeline { position: relative; padding-left: 32px; }
.timeline::before {
  content: '';
  position: absolute;
  left: 11px; top: 8px; bottom: 8px;
  width: 2px;
  background: var(--border);
}
.timeline-item {
  position: relative;
  padding: 6px 0;
  &.completed .timeline-dot { background: var(--success); border-color: var(--success); color: white; }
  &.active .timeline-dot { background: var(--accent); border-color: var(--accent); color: white; animation: pulse 2s infinite; }
}
@keyframes pulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(184,134,11,0.7); }
  50% { box-shadow: 0 0 0 8px rgba(184,134,11,0); }
}
.timeline-dot {
  position: absolute;
  left: -27px; top: 6px;
  width: 24px; height: 24px;
  border-radius: 50%;
  background: white;
  border: 2px solid var(--border);
  display: flex; align-items: center; justify-content: center;
  font-size: 11px; font-weight: 600;
  color: var(--text-3);
}
.node-name { font-size: 13px; font-weight: 600; }
.node-desc { font-size: 11px; color: var(--text-3); margin-top: 2px; }

.script-area {
  background: white;
  margin: 0 12px 12px;
  padding: 12px 16px;
  border-radius: 12px;
  flex: 1;
  max-height: 280px;
  overflow-y: auto;
}
.script-title { font-size: 13px; font-weight: 600; color: var(--text-2); margin-bottom: 8px; }
.script-content { }
.script-section { margin-bottom: 12px; }
.script-section:last-child { margin-bottom: 0; }
.ss-label { font-size: 11px; color: var(--text-3); margin-bottom: 4px; }
.script-section ul { list-style: none; padding: 0; margin: 0; }
.script-section li {
  display: flex; gap: 6px;
  padding: 3px 0;
  font-size: 12px;
  line-height: 1.5;
}
.line-num { color: var(--accent); font-weight: 600; font-family: monospace; }
.phrases { display: flex; flex-wrap: wrap; gap: 4px; }
.phrase {
  background: rgba(238,10,36,0.1);
  color: var(--danger);
  padding: 2px 6px;
  border-radius: 3px;
  font-size: 11px;
}

.control-bar {
  position: fixed;
  bottom: calc(60px + env(safe-area-inset-bottom, 0px)); left: 0; right: 0;
  background: white;
  padding: 12px 16px 16px;
  border-top: 1px solid var(--border);
  box-shadow: 0 -2px 8px rgba(0,0,0,0.05);
}
.ctrl-row {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 8px;
  margin-bottom: 8px;
}
.ctrl-btn {
  text-align: center;
  padding: 12px;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  background: var(--bg);
  color: var(--text-2);
  cursor: pointer;
  &.disabled { opacity: 0.4; cursor: not-allowed; }
  &.start { background: var(--accent); color: white; }
  &.recording { background: var(--danger); color: white; }
}

.sign-area { padding: 16px; }
.sign-tip { font-size: 13px; color: var(--text-2); margin: 0 0 12px; }
.sign-canvas {
  display: block;
  width: 100%;
  height: 160px;
  border: 2px dashed var(--border);
  border-radius: 8px;
  background: white;
  touch-action: none;
}
.sign-actions { margin-top: 12px; text-align: right; }
</style>
