<template>
  <div class="record-flow">
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

    <div class="script-area">
      <div class="script-title">📋 当前话术 ({{ nodes[currentNode].name }})</div>
      <div class="script-content">
        <div v-for="(line, i) in currentScriptLines" :key="i" class="script-line">
          <span class="line-num">{{ i + 1 }}.</span>
          <span>{{ line }}</span>
        </div>
      </div>
    </div>

    <div class="control-bar">
      <div class="ctrl-row">
        <div :class="['ctrl-btn', currentNode === 0 && 'disabled']" @click="prev">
          ◀ 上一节点
        </div>
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

    <!-- 签字弹窗 -->
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
import { ref, computed, onMounted, onUnmounted, nextTick, reactive } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast, showDialog } from 'vant'
import { useRecordingStore, NODES } from '@/stores/recording'
import { recordingApi, businessApi, scriptApi } from '@/api'

const route = useRoute()
const router = useRouter()
const store = useRecordingStore()

const businessId = computed(() => route.params.businessId as string || `BIZ-${Date.now()}`)
const nodes = NODES
const currentNode = ref(0)
const recording = ref(false)
const nodeStart = ref(Date.now())
const nodeElapsed = ref(0)
const errorMsg = ref('')

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

const formattedTime = computed(() => {
  const total = (Date.now() - nodeStart.value) / 1000
  const m = Math.floor(total / 60)
  const s = Math.floor(total % 60)
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
})

// 当前节点话术
const currentScriptLines = computed(() => {
  const lines: Record<string, string[]> = {
    '01-IDENTITY': ['请出示您的身份证原件', '客户: [出示身份证]', '请将身份证正面对准摄像头', '客户: [对准摄像头]'],
    '02-DISCLOSURE': ['本产品属于 R2 级中低风险', '预期年化收益 3.6%, 不保证', '投资期限 180 天', '产品代码: BNK-FIN-2026Q3-001'],
    '03-PRODUCT': ['本产品为固收类理财', '主要投资于国债/政策性金融债', '适合 C2 及以上风险等级客户', '管理费 0.5%/年, 托管费 0.05%/年'],
    '04-RIGHTS': ['本产品有 24 小时冷静期', '冷静期内可无条件撤单', '持有到期可赎回', '如需投诉请拨打 95588'],
    '05-TRUTH_TELL': ['请问您的投资经验?', '客户: [如实回答]', '请问您的收入来源?', '客户: [如实回答]', '请确认以上信息真实有效'],
    '06-AFFIRMATIVE': ['您是否已了解产品风险?', '客户: 了解', '您是否自愿购买本产品?', '客户: 自愿购买', '您是否同意相关协议?', '客户: 同意'],
    '07-SIGN': ['请在下方手写电子签名', '客户: [签名]', '签名提交后本次双录完成'],
    '08-FOLLOWUP': ['双录已完成', '产品有 15 天犹豫期', '犹豫期内可申请撤单', '15 天内会有 3 次回访确认']
  }
  return lines[nodes[currentNode.value].id] || []
})

function getNodeCls(i: number) {
  if (i < currentNode.value) return 'completed'
  if (i === currentNode.value) return 'active'
  return ''
}

onMounted(async () => {
  await startCamera()
  await loadScript()
  nextTick(() => setupWatermark())
})

onUnmounted(() => {
  stopAll()
  if (watermarkTimer) clearInterval(watermarkTimer)
})

async function startCamera() {
  try {
    stream = await navigator.mediaDevices.getUserMedia({
      video: { width: 1280, height: 720, facingMode: 'user' },
      audio: { echoCancellation: true, noiseSuppression: true }
    })
    if (videoEl.value) {
      videoEl.value.srcObject = stream
    }
  } catch (e: any) {
    errorMsg.value = `摄像头访问失败: ${e.message}`
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
  if (recording.value) {
    pauseRecord()
  } else {
    startRecord()
  }
}

function startRecord() {
  if (!stream) {
    showToast('摄像头未就绪')
    return
  }
  try {
    const mime = MediaRecorder.isTypeSupported('video/webm;codecs=vp9') ? 'video/webm;codecs=vp9' : 'video/webm'
    mediaRecorder = new MediaRecorder(stream, { mimeType: mime })
    mediaRecorder.ondataavailable = (e) => { if (e.data.size > 0) chunks.push(e.data) }
    mediaRecorder.start(1000)
    recording.value = true
  } catch (e: any) {
    errorMsg.value = e.message
  }
}

function pauseRecord() {
  if (mediaRecorder && mediaRecorder.state === 'recording') {
    mediaRecorder.pause()
  }
  recording.value = false
}

function next() {
  if (currentNode.value < nodes.length - 1) {
    // 上传当前节点录像
    uploadNode()
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

async function uploadNode() {
  if (chunks.length === 0) return
  try {
    const blob = new Blob(chunks, { type: 'video/webm' })
    await recordingApi.completeNode(businessId.value, {
      nodeId: nodes[currentNode.value].id,
      durationMs: Date.now() - nodeStart.value,
      fileSize: blob.size
    })
  } catch {}
}

async function loadScript() {
  await scriptApi.getTemplate('BNK-FIN-2026Q3-001', 'INTERNET_TEXT')
}

async function onComplete() {
  await uploadNode()
  showSign.value = true
  await nextTick()
  setupSignCanvas()
}

function setupSignCanvas() {
  if (!signCanvasEl.value) return
  const canvas = signCanvasEl.value
  // 适配 retina
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
function onSignEnd() {
  isDrawing = false
}
function clearSign() {
  if (!signContext || !signCanvasEl.value) return
  signContext.clearRect(0, 0, signCanvasEl.value.width, signCanvasEl.value.height)
}

async function onSign() {
  // 上传签名 + 完成双录
  try {
    const dataUrl = signCanvasEl.value?.toDataURL('image/png')
    await recordingApi.complete(businessId.value, {
      signature: dataUrl,
      totalNodes: nodes.length
    })
    showToast('双录已完成')
    setTimeout(() => router.replace('/h5/orders'), 800)
  } catch {
    showToast('提交失败, 请重试')
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
.video {
  width: 100%; height: 100%;
  object-fit: cover;
}
.watermark {
  position: absolute; inset: 0;
  width: 100%; height: 100%;
  pointer-events: none;
}
.rec-badge {
  position: absolute;
  top: 12px; left: 12px;
  background: rgba(238,10,36,0.85);
  color: white;
  padding: 4px 10px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 700;
  display: flex;
  align-items: center;
  gap: 4px;
}
.rec-dot {
  width: 8px; height: 8px;
  background: white;
  border-radius: 50%;
  animation: blink 1s infinite;
}
@keyframes blink { 0%, 100% { opacity: 1; } 50% { opacity: 0.3; } }

.node-progress {
  position: absolute;
  top: 12px; right: 12px;
  background: rgba(0,0,0,0.5);
  color: white;
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-family: monospace;
}

.timeline-wrap {
  background: white;
  padding: 12px 16px;
  margin: 12px;
  border-radius: 12px;
}
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
}
.script-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-2);
  margin-bottom: 8px;
}
.script-content {
  max-height: 180px;
  overflow-y: auto;
}
.script-line {
  display: flex;
  gap: 8px;
  padding: 6px 0;
  font-size: 13px;
  line-height: 1.5;
  border-bottom: 1px dashed var(--border);
  &:last-child { border-bottom: none; }
}
.line-num {
  color: var(--accent);
  font-weight: 600;
  flex-shrink: 0;
  font-family: monospace;
}

.control-bar {
  position: fixed;
  bottom: 0; left: 0; right: 0;
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
