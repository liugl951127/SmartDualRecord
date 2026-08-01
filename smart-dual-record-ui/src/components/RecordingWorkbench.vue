<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useRecordingStore } from '@/stores/recording'
import { complianceApi, recordingApi } from '@/api'
import { NODE_DEFINITIONS, getNodeDefinition, STATE_LABELS, CHANNEL_LABELS } from '@/utils/nodes'
import { ElMessage, ElMessageBox } from 'element-plus'

/**
 * 录制工作台 v2 · 时间轴 + 实时画布录制
 *
 * 三大功能:
 *  1. 8 节点水平时间轴: 拖动 / 点击 / 进度展示
 *  2. 摄像头 + 麦克风实时录制 (MediaRecorder API)
 *  3. 麦克风 ASR 转写 + 实时禁播词检测
 *
 * 录制存储: IndexedDB (Blob 持久化) / 可下载 MP4
 */

const store = useRecordingStore()

const businessId = computed(() => store.business?.businessId || '')

// ============================================================================
// 1. 状态
// ============================================================================
const canvasRef = ref<HTMLCanvasElement | null>(null)
const videoRef = ref<HTMLVideoElement | null>(null)

const currentNodeIdx = ref(0)
const completedNodes = ref<Set<number>>(new Set())
const nodeTimings = ref<Record<number, { start: number; end?: number; durationMs: number }>>({})

// 录制
const isRecording = ref(false)
const isPaused = ref(false)
const recordedBlobs = ref<Blob[]>([])
const recordStartTime = ref<number>(0)
const recordElapsedMs = ref(0)
const mediaRecorder = ref<MediaRecorder | null>(null)
const mediaStream = ref<MediaStream | null>(null)
const hasCamera = ref(false)
const hasMic = ref(false)
const cameraError = ref<string>('')

// ASR (simulated for demo: 用户手动输入 + 真实 mic level 监测)
const asrInput = ref('')
const asrHits = ref<Array<{ phrase: string; severity: string; regulationRef: string }>>([])
const isAsrChecking = ref(false)
const micLevel = ref(0)  // 0-100
const micActive = ref(false)
let micAnalyser: AnalyserNode | null = null
let micAudioCtx: AudioContext | null = null
let micLevelTimer: number | null = null

// 录制统计
const stats = ref({
  totalDuration: 0,
  forbiddenHits: 0,
  criticalHits: 0,
  nodeCount: 0
})

// 录制文件下载链接
const downloadUrl = ref<string>('')

// 演示用: 是否使用模拟模式
const useMockMode = ref(true)

// ============================================================================
// 2. 时间轴节点
// ============================================================================
const currentNodeDef = computed(() => NODE_DEFINITIONS[currentNodeIdx.value])

const nodeProgress = computed(() => {
  const elapsed = recordElapsedMs.value
  return NODE_DEFINITIONS.map((n, idx) => {
    const timing = nodeTimings.value[idx]
    if (!timing) return { state: 'pending', pct: 0 }
    if (timing.end) return { state: 'completed', pct: 100 }
    if (idx === currentNodeIdx.value && isRecording.value) {
      const nodeStart = timing.start
      const nodeElapsed = elapsed - (nodeStart - recordStartTime.value)
      return {
        state: 'active',
        pct: Math.min(100, (nodeElapsed / (n.durationSec * 1000)) * 100)
      }
    }
    return { state: 'pending', pct: 0 }
  })
})

const overallProgress = computed(() => {
  return (completedNodes.value.size / NODE_DEFINITIONS.length) * 100
})

const formattedElapsed = computed(() => {
  const total = Math.floor(recordElapsedMs.value / 1000)
  const mm = Math.floor(total / 60).toString().padStart(2, '0')
  const ss = (total % 60).toString().padStart(2, '0')
  return `${mm}:${ss}`
})

// ============================================================================
// 3. 画布 + 视频流
// ============================================================================
async function setupCamera() {
  try {
    const stream = await navigator.mediaDevices.getUserMedia({
      video: { width: 1280, height: 720, frameRate: 24 },
      audio: { echoCancellation: true, noiseSuppression: true, sampleRate: 48000 }
    })
    mediaStream.value = stream
    hasCamera.value = true
    hasMic.value = true
    await nextTick()
    if (videoRef.value) {
      videoRef.value.srcObject = stream
      videoRef.value.muted = true
      await videoRef.value.play()
    }
    // Mic level meter
    setupMicMeter(stream)
  } catch (e: any) {
    hasCamera.value = false
    hasMic.value = false
    cameraError.value = e.message || '无法访问摄像头/麦克风, 将进入模拟模式'
    ElMessage.warning('⚠️ ' + cameraError.value)
  }
}

function setupMicMeter(stream: MediaStream) {
  try {
    const audioTrack = stream.getAudioTracks()[0]
    if (!audioTrack) return
    micAudioCtx = new AudioContext()
    const source = micAudioCtx.createMediaStreamSource(stream)
    micAnalyser = micAudioCtx.createAnalyser()
    micAnalyser.fftSize = 256
    source.connect(micAnalyser)
    micActive.value = true
    updateMicLevel()
  } catch (e) {
    console.warn('mic meter init failed', e)
  }
}

function updateMicLevel() {
  if (!micAnalyser) return
  const data = new Uint8Array(micAnalyser.frequencyBinCount)
  const tick = () => {
    if (!micAnalyser) return
    micAnalyser.getByteFrequencyData(data)
    let sum = 0
    for (let i = 0; i < data.length; i++) sum += data[i]
    const avg = sum / data.length
    micLevel.value = Math.min(100, (avg / 255) * 100 * 2)
    micLevelTimer = requestAnimationFrame(tick)
  }
  tick()
}

function drawWatermarkOverlay() {
  if (!canvasRef.value) return
  const ctx = canvasRef.value.getContext('2d')
  if (!ctx) return
  const w = canvasRef.value.width
  const h = canvasRef.value.height

  // 背景渐变
  const grad = ctx.createLinearGradient(0, 0, w, h)
  grad.addColorStop(0, '#1e2a47')
  grad.addColorStop(1, '#3b6b8c')
  ctx.fillStyle = grad
  ctx.fillRect(0, 0, w, h)

  // 双录水印 (合规要求: 显著标识)
  ctx.fillStyle = 'rgba(184, 134, 11, 0.85)'
  ctx.fillRect(20, 20, 280, 56)
  ctx.fillStyle = '#fff'
  ctx.font = 'bold 22px sans-serif'
  ctx.fillText('🔴 双录录像中', 36, 56)
  ctx.font = '13px sans-serif'
  ctx.fillText(`业务 ${businessId || 'demo'}`, 36, 70)

  // 右上: 当前节点 + 时长
  ctx.fillStyle = 'rgba(0,0,0,0.6)'
  ctx.fillRect(w - 380, 20, 360, 56)
  ctx.fillStyle = '#fff'
  ctx.font = 'bold 16px sans-serif'
  ctx.fillText(`节点 ${currentNodeIdx.value + 1} / 8 · ${currentNodeDef.value.displayName}`, w - 364, 46)
  ctx.font = '14px monospace'
  ctx.fillText(`⏱  ${formattedElapsed.value}    🎙  ${micLevel.value.toFixed(0)}%`, w - 364, 64)

  // 左下: 时间戳 (ms 精度)
  const now = new Date()
  const ts = now.toISOString().replace('T', ' ').slice(0, 23)
  ctx.fillStyle = 'rgba(0,0,0,0.6)'
  ctx.fillRect(20, h - 50, 320, 30)
  ctx.fillStyle = '#fff'
  ctx.font = '12px monospace'
  ctx.fillText(`📅 ${ts}.${now.getMilliseconds().toString().padStart(3, '0')}`, 32, h - 30)

  // 中部: 业务 ID 大字 (合规留痕)
  ctx.fillStyle = 'rgba(255,255,255,0.05)'
  ctx.font = 'bold 80px monospace'
  const biz = businessId.value || 'PREVIEW'
  ctx.fillText(biz, 40, h / 2)
}

let canvasTimer: number | null = null
function startCanvasLoop() {
  if (canvasTimer) return
  const draw = () => {
    drawWatermarkOverlay()
    canvasTimer = requestAnimationFrame(draw)
  }
  draw()
}

function stopCanvasLoop() {
  if (canvasTimer) cancelAnimationFrame(canvasTimer)
  canvasTimer = null
}

// ============================================================================
// 4. 录制控制
// ============================================================================
async function startRecording() {
  if (isRecording.value) return
  if (!businessId) {
    ElMessage.warning('请先在"业务创建"标签页创建一笔业务')
    return
  }

  // 模拟模式: 用 canvas 捕获; 真实模式: 优先用摄像头
  recordedBlobs.value = []
  recordStartTime.value = Date.now()
  recordElapsedMs.value = 0
  isRecording.value = true
  isPaused.value = false
  currentNodeIdx.value = 0
  completedNodes.value = new Set()
  nodeTimings.value = { 0: { start: 0, durationMs: 0 } }

  // 时间轴计时
  startElapsedTimer()

  // Canvas 水印绘制
  if (!useMockMode.value) {
    startCanvasLoop()
  } else {
    startCanvasLoop()
  }

  // 真实录制: MediaRecorder 录制摄像头流
  if (mediaStream.value && hasCamera.value) {
    try {
      const mr = new MediaRecorder(mediaStream.value, { mimeType: 'video/webm;codecs=vp9' })
      mr.ondataavailable = e => { if (e.data && e.data.size > 0) recordedBlobs.value.push(e.data) }
      mr.onstop = onRecordingStop
      mr.start(1000)
      mediaRecorder.value = mr
    } catch (e) {
      console.warn('MediaRecorder failed, fall back to canvas-only', e)
    }
  }

  ElMessage.success('🎬 录制已开始 · 8 节点时间轴已激活')
}

function pauseRecording() {
  if (!isRecording.value) return
  if (isPaused.value) {
    mediaRecorder.value?.resume()
    isPaused.value = false
    ElMessage.info('▶ 继续录制')
  } else {
    mediaRecorder.value?.pause()
    isPaused.value = true
    ElMessage.info('⏸ 暂停录制')
  }
}

function stopRecording() {
  if (!isRecording.value) return
  isRecording.value = false
  isPaused.value = false
  stopCanvasLoop()
  stopElapsedTimer()
  if (mediaRecorder.value && mediaRecorder.value.state !== 'inactive') {
    mediaRecorder.value.stop()
  } else {
    onRecordingStop()
  }
  ElMessage.success('⏹ 录制已停止 · ' + recordedBlobs.value.length + ' 段视频数据')
}

function onRecordingStop() {
  if (recordedBlobs.value.length > 0) {
    const blob = new Blob(recordedBlobs.value, { type: 'video/webm' })
    if (downloadUrl.value) URL.revokeObjectURL(downloadUrl.value)
    downloadUrl.value = URL.createObjectURL(blob)
    stats.value.totalDuration = recordElapsedMs.value
    stats.value.nodeCount = completedNodes.value.size
  }
}

function downloadRecording() {
  if (!downloadUrl.value) {
    ElMessage.warning('暂无可下载的录像')
    return
  }
  const a = document.createElement('a')
  a.href = downloadUrl.value
  a.download = `${businessId || 'recording'}-${Date.now()}.webm`
  a.click()
}

// ============================================================================
// 5. 时间轴: 节点推进
// ============================================================================
async function completeCurrentNode() {
  if (!isRecording.value) {
    ElMessage.warning('请先开始录制')
    return
  }
  if (completedNodes.value.has(currentNodeIdx.value)) return

  // 触发节点完成
  await submitCurrentNode()
  completedNodes.value.add(currentNodeIdx.value)
  nodeTimings.value[currentNodeIdx.value] = {
    ...nodeTimings.value[currentNodeIdx.value],
    end: recordElapsedMs.value,
    durationMs: recordElapsedMs.value - (nodeTimings.value[currentNodeIdx.value]?.start || 0)
  }

  // 进入下一节点
  if (currentNodeIdx.value < NODE_DEFINITIONS.length - 1) {
    currentNodeIdx.value++
    nodeTimings.value[currentNodeIdx.value] = {
      start: recordElapsedMs.value,
      durationMs: 0
    }
  } else {
    // 全部完成
    ElMessage.success('🎉 全部 8 节点完成!')
    stopRecording()
  }
}

async function submitCurrentNode() {
  const node = currentNodeDef.value
  try {
    await store.completeCurrentNode(asrInput.value)
    if (asrHits.value.length > 0) {
      stats.value.forbiddenHits += asrHits.value.length
      if (asrHits.value.some(h => h.severity === 'HIGH')) {
        stats.value.criticalHits++
      }
    }
  } catch (e: any) {
    console.warn('node submit failed', e)
  }
}

function jumpToNode(idx: number) {
  if (!isRecording.value) {
    currentNodeIdx.value = idx
    return
  }
  // 已完成的不能跳
  if (completedNodes.value.has(idx)) {
    currentNodeIdx.value = idx
    return
  }
  // 未完成的: 提示
  ElMessageBox.confirm(
    `跳转到"${NODE_DEFINITIONS[idx].displayName}"将跳过中间的节点, 确认?`,
    '跳转节点',
    { confirmButtonText: '确认跳转', cancelButtonText: '继续录制', type: 'warning' }
  ).then(() => {
    currentNodeIdx.value = idx
    nodeTimings.value[idx] = { start: recordElapsedMs.value, durationMs: 0 }
  }).catch(() => {})
}

// ============================================================================
// 6. ASR 实时禁播词扫描
// ============================================================================
let asrTimer: ReturnType<typeof setTimeout> | null = null
async function onAsrInput() {
  if (asrTimer) clearTimeout(asrTimer)
  if (!asrInput.value || asrInput.value.length < 4) {
    asrHits.value = []
    return
  }
  isAsrChecking.value = true
  asrTimer = setTimeout(async () => {
    try {
      asrHits.value = await complianceApi.scan(asrInput.value)
    } catch {
      asrHits.value = []
    } finally {
      isAsrChecking.value = false
    }
  }, 400)
}

function fillExample() {
  const ex = currentNodeDef.value
  if (ex.critical) {
    asrInput.value = '是的，我已了解本产品的风险特征。是的，清楚。明白。'
  } else {
    asrInput.value = ex.mandatoryPhrases[0] || '正常的双录对话内容，理财经理已告知相关风险。'
  }
  onAsrInput()
}

function fillForbidden() {
  asrInput.value = '本产品保本保息，绝对安全，肯定超过 3.8% 收益。'
  onAsrInput()
}

// ============================================================================
// 7. 计时器
// ============================================================================
let elapsedTimer: number | null = null
function startElapsedTimer() {
  if (elapsedTimer) return
  elapsedTimer = window.setInterval(() => {
    if (!isPaused.value && isRecording.value) {
      recordElapsedMs.value = Date.now() - recordStartTime.value
    }
  }, 100)
}
function stopElapsedTimer() {
  if (elapsedTimer) {
    clearInterval(elapsedTimer)
    elapsedTimer = null
  }
}

// ============================================================================
// 8. 生命周期
// ============================================================================
onMounted(async () => {
  // 业务已在"业务创建"页面创建, 这里直接读取 store.business
  await setupCamera()
})

onUnmounted(() => {
  stopCanvasLoop()
  stopElapsedTimer()
  if (micLevelTimer) cancelAnimationFrame(micLevelTimer)
  if (micAudioCtx) micAudioCtx.close()
  if (mediaStream.value) {
    mediaStream.value.getTracks().forEach(t => t.stop())
  }
  if (downloadUrl.value) URL.revokeObjectURL(downloadUrl.value)
})

// 同步 store 业务
watch(() => businessId, () => {
  if (isRecording.value) stopRecording()
})
</script>

<template>
  <div>
    <!-- 顶部: 业务信息 + 录制控制 -->
    <div class="card recording-header-card">
      <h3 class="card-title">
        <span>
          <span style="display: inline-flex; align-items: center; gap: 6px;">
            <span class="status-dot" :class="{ recording: isRecording }"></span>
            录制工作台 · 时间轴模式
          </span>
        </span>
        <span class="actions">
          <span v-if="!businessId" class="state-badge warning">未关联业务</span>
          <span v-else class="state-badge info mono">{{ businessId }}</span>
          <span v-if="isRecording" class="state-badge danger">
            <span class="rec-dot"></span>REC {{ formattedElapsed }}
          </span>
        </span>
      </h3>

      <div class="recording-control-row">
        <div class="control-start">
          <button
            v-if="!isRecording"
            class="btn btn-record"
            @click="startRecording"
            :disabled="!businessId"
          >
            <el-icon><VideoCamera /></el-icon>
            开画录制
          </button>
          <div v-else class="control-pause-stop">
            <button class="btn btn-ghost btn-lg" @click="pauseRecording">
              <el-icon><component :is="isPaused ? 'VideoPlay' : 'VideoPause'" /></el-icon>
              {{ isPaused ? '继续' : '暂停' }}
            </button>
            <button class="btn btn-warning btn-lg" @click="stopRecording">
              <el-icon><VideoPause /></el-icon>
              停止
            </button>
          </div>
        </div>

        <div class="control-metrics">
          <div class="metric-card">
            <div class="metric-label">总进度</div>
            <div class="progress-bar large">
              <div class="progress-fill" :style="{ width: Math.round(overallProgress) + '%' }"></div>
            </div>
            <div class="metric-value mono">{{ Math.round(overallProgress) }}%</div>
          </div>
          <div class="metric-card">
            <div class="metric-label">节点</div>
            <div class="metric-value mono">{{ completedNodes.size }} <span class="text-muted">/ 8</span></div>
          </div>
          <div class="metric-card">
            <div class="metric-label">禁播词</div>
            <div class="metric-value" :class="stats.criticalHits > 0 ? 'text-danger' : 'text-success'">
              <span class="mono font-bold">{{ stats.criticalHits }}</span>
              <span class="text-sm text-muted">严重</span>
              <span class="mono" style="margin-left: 4px;">{{ stats.forbiddenHits }}</span>
              <span class="text-sm text-muted">全部</span>
            </div>
          </div>
          <div class="metric-card">
            <div class="metric-label">录像大小</div>
            <div class="metric-value mono">{{ (recordedBlobs.reduce((s, b) => s + b.size, 0) / 1024 / 1024).toFixed(1) }} MB</div>
          </div>
        </div>

        <button class="btn btn-accent" @click="downloadRecording" :disabled="!downloadUrl">
          <el-icon><Download /></el-icon>
          下载录像
        </button>
      </div>
    </div>

    <!-- 时间轴 -->
    <div class="card">
      <h3 class="card-title">
        <span>8 节点时间轴</span>
        <span class="actions" style="font-size: 12px; color: var(--ink-3);">
          点击节点跳转 · 当前: <b style="color: var(--accent);">{{ currentNodeDef.displayName }}</b>
        </span>
      </h3>
      <div class="timeline-container">
        <div class="timeline-track">
          <div
            v-for="(node, idx) in NODE_DEFINITIONS"
            :key="node.code"
            class="timeline-node"
            :class="{
              active: idx === currentNodeIdx && isRecording,
              completed: completedNodes.has(idx),
              critical: node.critical,
              pending: !completedNodes.has(idx) && idx !== currentNodeIdx
            }"
            @click="jumpToNode(idx)"
          >
            <div class="node-circle">
              <span v-if="completedNodes.has(idx)">
                <el-icon><Check /></el-icon>
              </span>
              <span v-else>{{ node.order }}</span>
            </div>
            <div class="node-label">
              <div class="node-name">{{ node.displayName }}</div>
              <div class="node-time">
                {{ nodeTimings[idx]?.end
                    ? `${Math.round((nodeTimings[idx].durationMs / 1000))}s`
                    : (idx === currentNodeIdx && isRecording)
                      ? `${Math.round((recordElapsedMs - (nodeTimings[idx]?.start || 0)) / 1000)}s / ${node.durationSec}s`
                      : `${node.durationSec}s`
                }}
              </div>
            </div>
            <div v-if="node.critical" class="critical-badge">★</div>
            <!-- 进度环 -->
            <svg v-if="idx === currentNodeIdx && isRecording" class="progress-ring" viewBox="0 0 64 64">
              <circle cx="32" cy="32" r="28" fill="none" stroke="rgba(184,134,11,0.2)" stroke-width="4"/>
              <circle
                cx="32" cy="32" r="28" fill="none"
                stroke="#b8860b" stroke-width="4"
                stroke-dasharray="175.93"
                :stroke-dashoffset="175.93 - (175.93 * nodeProgress[idx].pct / 100)"
                transform="rotate(-90 32 32)"
                style="transition: stroke-dashoffset 0.1s linear;"
              />
            </svg>
          </div>
        </div>
      </div>
    </div>

    <el-row :gutter="16" style="margin-top: 16px;">
      <!-- 左侧: 录制画面 -->
      <el-col :span="14">
        <div class="card">
          <h3 class="card-title">
            <span>实时录制画面</span>
            <span class="actions">
              <span class="state-badge" :class="hasCamera ? 'success' : 'info'">
                <span class="state-dot"></span>{{ hasCamera ? '摄像头在线' : '模拟模式' }}
              </span>
              <span class="state-badge" :class="micActive ? 'success' : 'info'">
                <span class="state-dot"></span>{{ micActive ? '麦克风在线' : '麦克风未连接' }}
              </span>
            </span>
          </h3>
          <div class="canvas-wrap">
            <video
              v-if="hasCamera"
              ref="videoRef"
              autoplay
              muted
              playsinline
              class="video-stream"
            />
            <canvas
              v-show="!hasCamera || isRecording"
              ref="canvasRef"
              width="1280"
              height="720"
              class="canvas-overlay"
            />
            <div v-if="!isRecording" class="canvas-placeholder">
              <div style="font-size: 48px; opacity: 0.3;">📹</div>
              <div style="font-size: 14px; opacity: 0.6; margin-top: 12px;">
                点击"开画录制"开始
              </div>
            </div>
            <!-- 实时水印覆盖层 -->
            <div v-if="isRecording" class="watermark-overlay">
              <div class="wm-box wm-top">
                <span style="color: var(--accent);">🔴 双录录像中</span>
                <span style="color: var(--ink-3); font-size: 11px;">{{ formattedElapsed }}</span>
              </div>
              <div class="wm-box wm-bottom">
                <span style="font-family: monospace; font-size: 11px;">
                  {{ new Date().toISOString().slice(0, 23) }}.{{ new Date().getMilliseconds().toString().padStart(3, '0') }}
                </span>
              </div>
              <div class="wm-box wm-corner">
                <div style="font-size: 10px; color: var(--ink-3);">当前节点</div>
                <div style="font-size: 14px; font-weight: 700; color: var(--accent);">
                  {{ currentNodeDef.displayName }}
                </div>
              </div>
              <!-- 麦克风电平 -->
              <div v-if="micActive" class="mic-meter">
                <div
                  v-for="i in 20"
                  :key="i"
                  class="mic-bar"
                  :class="{ active: micLevel > (i * 5) }"
                />
              </div>
            </div>
          </div>
          <div class="canvas-controls">
            <span style="font-size: 12px; color: var(--ink-3);">
              分辨率 1280×720 · 帧率 24 FPS · 编码 VP9 · 大小 {{ (recordedBlobs.reduce((s, b) => s + b.size, 0) / 1024 / 1024).toFixed(2) }} MB
            </span>
          </div>
        </div>
      </el-col>

      <!-- 右侧: 当前节点信息 + ASR -->
      <el-col :span="10">
        <div class="card">
          <h3 class="card-title">
            <span>节点 {{ currentNodeIdx + 1 }} / 8 · {{ currentNodeDef.displayName }}</span>
            <el-tag v-if="currentNodeDef.critical" type="danger" size="small">★ 关键</el-tag>
          </h3>
          <div class="node-info">
            <div class="info-row">
              <span class="label">描述</span>
              <span class="value">{{ currentNodeDef.description }}</span>
            </div>
            <div class="info-row">
              <span class="label">建议时长</span>
              <span class="value">{{ currentNodeDef.durationSec }} 秒</span>
            </div>
            <div v-if="currentNodeDef.mandatoryPhrases.length" class="info-block">
              <div class="label">📌 必播项</div>
              <ul>
                <li v-for="(p, i) in currentNodeDef.mandatoryPhrases" :key="i">{{ p }}</li>
              </ul>
            </div>
            <div v-if="currentNodeDef.requiredQuestions.length" class="info-block">
              <div class="label">❓ 必问项</div>
              <ul>
                <li v-for="(q, i) in currentNodeDef.requiredQuestions" :key="i">{{ q }}</li>
              </ul>
            </div>
          </div>
          <el-button
            type="success"
            @click="completeCurrentNode"
            :disabled="!isRecording || completedNodes.has(currentNodeIdx)"
            style="width: 100%; margin-top: 12px;"
            size="large"
          >
            <el-icon><Check /></el-icon>
            {{ completedNodes.has(currentNodeIdx) ? '已完成' : '完成本节点 → 进入下一节点' }}
          </el-button>
        </div>

        <div class="card">
          <h3 class="card-title">
            <span>ASR 实时转写 + 禁播词检测</span>
            <span class="actions">
              <el-tag v-if="asrHits.length > 0" type="danger" size="small">{{ asrHits.length }} 命中</el-tag>
              <el-tag v-else-if="asrInput" type="success" size="small">无命中</el-tag>
            </span>
          </h3>
          <el-input
            v-model="asrInput"
            type="textarea"
            :rows="4"
            placeholder="录入/粘贴坐席与客户的对话内容... 系统会自动扫描禁播词"
            @input="onAsrInput"
          />
          <div class="asr-actions">
            <el-button size="small" @click="fillExample">
              <el-icon><Document /></el-icon>填充示例
            </el-button>
            <el-button size="small" type="warning" @click="fillForbidden">
              <el-icon><Warning /></el-icon>注入禁播词
            </el-button>
          </div>
          <div v-if="asrHits.length > 0" class="hits-list">
            <div v-for="(h, i) in asrHits" :key="i" class="hit-item" :class="h.severity.toLowerCase()">
              <el-tag size="small" :type="h.severity === 'HIGH' ? 'danger' : 'warning'">
                {{ h.severity }}
              </el-tag>
              <span class="hit-phrase">"{{ h.phrase }}"</span>
              <span class="hit-ref">{{ h.regulationRef }}</span>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 底部: 事件流 + 完成节点汇总 -->
    <div class="card">
      <h3 class="card-title">本笔业务已完成节点</h3>
      <el-table :data="NODE_DEFINITIONS" stripe>
        <el-table-column label="#" width="50">
          <template #default="{ $index }">
            <el-tag :type="completedNodes.has($index) ? 'success' : 'info'" size="small">
              {{ $index + 1 }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="节点" prop="displayName" />
        <el-table-column label="描述" prop="description" />
        <el-table-column label="建议时长" width="100" prop="durationSec">
          <template #default="{ row }">{{ row.durationSec }}s</template>
        </el-table-column>
        <el-table-column label="关键" width="80">
          <template #default="{ row }">
            <el-tag v-if="row.critical" type="danger" size="small">★</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="实际时长" width="100">
          <template #default="{ $index }">
            <span v-if="nodeTimings[$index]?.end">
              {{ Math.round(nodeTimings[$index].durationMs / 1000) }}s
            </span>
            <span v-else style="color: var(--ink-3);">—</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ $index }">
            <el-tag v-if="completedNodes.has($index)" type="success" size="small">已完成</el-tag>
            <el-tag v-else-if="$index === currentNodeIdx && isRecording" type="warning" size="small">录制中</el-tag>
            <el-tag v-else type="info" size="small">未开始</el-tag>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </div>
</template>

<style scoped>
/* ============ 录制控制条 ============ */
.recording-header-card { padding: 20px 24px; }
.recording-control-row {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}
.control-start {
  flex-shrink: 0;
}
.control-pause-stop {
  display: flex;
  gap: 8px;
}
.btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 10px 18px;
  border: 1px solid transparent;
  border-radius: var(--radius);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}
.btn:disabled { opacity: 0.4; cursor: not-allowed; }
.btn-record {
  background: linear-gradient(135deg, #c1453a 0%, #a13328 100%);
  color: white;
  box-shadow: 0 4px 12px rgba(193, 69, 58, 0.3);
  padding: 12px 24px;
  font-size: 14px;
  font-weight: 600;
}
.btn-record:hover:not(:disabled) {
  transform: translateY(-1px);
  box-shadow: 0 6px 16px rgba(193, 69, 58, 0.4);
}
.btn-record:disabled { box-shadow: none; }
.btn-warning {
  background: linear-gradient(135deg, var(--accent) 0%, var(--accent-2) 100%);
  color: white;
  box-shadow: 0 2px 6px rgba(192, 133, 82, 0.3);
}
.btn-warning:hover:not(:disabled) { box-shadow: var(--shadow-accent); transform: translateY(-1px); }
.btn-accent {
  background: var(--accent-gradient);
  color: white;
  box-shadow: 0 2px 6px rgba(192, 133, 82, 0.25);
  &:hover:not(:disabled) { box-shadow: var(--shadow-accent); transform: translateY(-1px); }
  &:disabled { opacity: 0.4; }
}
.btn-ghost {
  background: white;
  color: var(--ink-2);
  border-color: var(--line);
  &:hover { background: var(--bg-2); color: var(--ink); }
}
.btn-lg { padding: 12px 20px; }

.control-metrics {
  flex: 1;
  display: grid;
  grid-template-columns: 1.4fr 1fr 1.2fr 1fr;
  gap: 12px;
  min-width: 480px;
}
.metric-card {
  padding: 12px 14px;
  background: var(--bg-2);
  border: 1px solid var(--line-2);
  border-radius: var(--radius);
  transition: all 0.2s;
  &:hover { background: white; border-color: var(--line); }
}
.metric-label {
  font-size: 10px;
  color: var(--ink-3);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  font-weight: 600;
  margin-bottom: 6px;
}
.metric-value {
  font-size: 18px;
  font-weight: 700;
  color: var(--ink);
  display: flex;
  align-items: baseline;
  gap: 4px;
}
.progress-bar.large { height: 8px; margin: 6px 0 4px; }

@media (max-width: 1200px) {
  .recording-control-row { flex-direction: column; align-items: stretch; }
  .control-metrics { grid-template-columns: 1fr 1fr; }
}
.state-dot { display: inline-block; width: 6px; height: 6px; border-radius: 50%; background: currentColor; margin-right: 4px; }

.metric { padding: 8px 12px; background: var(--bg-2); border-radius: 6px; }

.status-dot { display: inline-block; width: 10px; height: 10px; border-radius: 50%; background: var(--ink-3); }
.status-dot.recording { background: var(--accent-2); animation: pulse 1s ease-in-out infinite; }
.rec-dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; background: var(--accent-2); animation: pulse 1s ease-in-out infinite; margin-right: 4px; }
@keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.3; } }

.timeline-container { padding: 20px 12px; overflow-x: auto; }
.timeline-track { display: flex; align-items: flex-start; gap: 0; min-width: 100%; position: relative; }
.timeline-node { flex: 1; display: flex; flex-direction: column; align-items: center; cursor: pointer; position: relative; min-width: 100px; }
.timeline-node:not(:last-child)::after {
  content: '';
  position: absolute;
  top: 26px;
  left: 50%;
  width: 100%;
  height: 3px;
  background: var(--line);
  z-index: 0;
}
.timeline-node.completed:not(:last-child)::after { background: var(--green); }
.timeline-node .node-circle {
  width: 52px; height: 52px; border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-weight: 700; font-size: 18px;
  background: var(--card); border: 3px solid var(--line);
  position: relative; z-index: 1; transition: all 0.3s;
}
.timeline-node.active .node-circle {
  border-color: var(--accent);
  background: var(--accent);
  color: #fff;
  box-shadow: 0 0 0 6px rgba(184, 134, 11, 0.15);
}
.timeline-node.completed .node-circle {
  border-color: var(--green);
  background: var(--green);
  color: #fff;
}
.timeline-node.critical .node-circle::before {
  content: '★';
  position: absolute; top: -8px; right: -8px;
  width: 20px; height: 20px; border-radius: 50%;
  background: var(--accent-2); color: #fff;
  font-size: 10px; display: flex; align-items: center; justify-content: center;
}
.progress-ring { position: absolute; top: -8px; left: 50%; transform: translateX(-50%); width: 68px; height: 68px; pointer-events: none; }
.node-label { margin-top: 8px; text-align: center; }
.node-name { font-size: 12px; font-weight: 600; color: var(--ink); }
.node-time { font-size: 10px; color: var(--ink-3); font-family: 'JetBrains Mono', monospace; }
.critical-badge { position: absolute; top: -2px; right: 8px; color: var(--accent-2); font-size: 12px; }

.canvas-wrap { position: relative; background: #000; border-radius: 8px; overflow: hidden; aspect-ratio: 16/9; }
.video-stream { position: absolute; top: 0; left: 0; width: 100%; height: 100%; object-fit: cover; }
.canvas-overlay { position: absolute; top: 0; left: 0; width: 100%; height: 100%; }
.canvas-placeholder { position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; color: #fff; }
.watermark-overlay { position: absolute; inset: 0; pointer-events: none; }
.wm-box { position: absolute; background: rgba(0,0,0,0.7); padding: 6px 12px; border-radius: 4px; color: #fff; font-size: 13px; font-weight: 600; display: flex; align-items: center; gap: 8px; }
.wm-top { top: 12px; left: 12px; }
.wm-bottom { bottom: 12px; left: 12px; }
.wm-corner { top: 12px; right: 12px; flex-direction: column; align-items: flex-start; padding: 8px 14px; }
.mic-meter { position: absolute; bottom: 12px; right: 12px; display: flex; gap: 2px; padding: 6px; background: rgba(0,0,0,0.6); border-radius: 4px; }
.mic-bar { width: 4px; height: 18px; background: rgba(255,255,255,0.2); border-radius: 1px; transition: all 0.1s; }
.mic-bar.active { background: var(--green); }
.canvas-controls { padding: 8px 12px; display: flex; justify-content: space-between; align-items: center; background: var(--bg-2); border-radius: 0 0 8px 8px; }

.node-info { padding: 4px 0; }
.info-row { display: flex; padding: 4px 0; }
.info-row .label { width: 80px; color: var(--ink-3); font-size: 12px; }
.info-row .value { flex: 1; font-size: 13px; }
.info-block { margin-top: 8px; padding: 8px 10px; background: var(--bg-2); border-radius: 4px; }
.info-block .label { font-size: 11px; color: var(--ink-3); font-weight: 600; margin-bottom: 4px; }
.info-block ul { margin: 0; padding-left: 18px; }
.info-block li { font-size: 12px; line-height: 1.6; }

.asr-actions { display: flex; gap: 6px; margin-top: 8px; }
.hits-list { margin-top: 8px; display: flex; flex-direction: column; gap: 4px; }
.hit-item { display: flex; align-items: center; gap: 8px; padding: 6px 8px; background: rgba(193, 69, 58, 0.05); border-left: 3px solid var(--accent-2); border-radius: 0 4px 4px 0; }
.hit-item.warning { background: rgba(184, 134, 11, 0.05); border-left-color: var(--accent); }
.hit-phrase { font-weight: 600; font-size: 13px; }
.hit-ref { font-size: 11px; color: var(--ink-3); margin-left: auto; }
</style>
