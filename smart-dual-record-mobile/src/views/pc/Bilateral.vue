<template>
  <div class="pc-bilateral">
    <div class="p-header">
      <div class="p-title">💼 坐席双边录制</div>
      <div :class="['p-status', status.cls]">
        <div class="p-status-dot"></div>
        <span>{{ status.label }}</span>
      </div>
    </div>

    <div class="p-toolbar">
      <input v-model="businessId" placeholder="输入业务ID / 客户进线 ID" class="p-input" />
      <button :class="['p-btn', peerConnected ? 'danger' : 'primary']" @click="toggleConnect">
        {{ peerConnected ? '断开' : '加入房间' }}
      </button>
    </div>

    <div class="p-videos">
      <!-- 客户 (远端) -->
      <div class="p-video remote">
        <video ref="remoteVideo" autoplay playsinline class="p-video-el"></video>
        <div class="p-video-label">客户</div>
        <div v-if="!peerConnected" class="p-video-placeholder">
          <div class="p-placeholder-icon">📞</div>
          <p>输入业务 ID 后加入房间等待客户</p>
        </div>
      </div>

      <!-- 坐席 (本地) -->
      <div class="p-video local">
        <video ref="localVideo" autoplay muted playsinline class="p-video-el"></video>
        <div class="p-video-label">我</div>
        <canvas ref="watermarkEl" class="p-watermark"></canvas>
      </div>
    </div>

    <div class="p-info-grid">
      <div class="p-stat">
        <div class="p-stat-num">{{ stats.recording ? '●' : '○' }}</div>
        <div class="p-stat-lbl">录制</div>
      </div>
      <div class="p-stat">
        <div class="p-stat-num">{{ formattedTime }}</div>
        <div class="p-stat-lbl">时长</div>
      </div>
      <div class="p-stat">
        <div class="p-stat-num">{{ formatSize(recordedBytes) }}</div>
        <div class="p-stat-lbl">大小</div>
      </div>
      <div class="p-stat">
        <div :class="['p-stat-num', stats.forbiddenHits > 0 ? 'danger' : '']">{{ stats.forbiddenHits }}</div>
        <div class="p-stat-lbl">禁播词</div>
      </div>
    </div>

    <div class="p-panels">
      <div class="p-card">
        <h3>📋 业务信息</h3>
        <ul class="p-list">
          <li><span>业务ID</span><span class="mono">{{ businessId || '未填' }}</span></li>
          <li><span>连接状态</span><span :class="['conn', connectionCls]">{{ connectionLabel }}</span></li>
          <li><span>录制状态</span><span :class="['conn', recording ? 'rec' : 'idle']">{{ recording ? '录制中' : '未开始' }}</span></li>
          <li><span>信令消息</span><span>{{ stats.signalingMessages }}</span></li>
        </ul>
      </div>

      <div class="p-card">
        <h3>🚨 实时禁播词告警</h3>
        <div v-if="!forbiddenHits.length" class="p-empty">暂无告警</div>
        <ul v-else class="p-hit-list">
          <li v-for="(h, i) in forbiddenHits" :key="i" class="p-hit-item">
            <div class="hit-time">{{ h.time }}</div>
            <div class="hit-phrase">"{{ h.phrase }}"</div>
            <div :class="['hit-sev', `sev-${h.severity}`]">{{ h.severity }}</div>
          </li>
        </ul>
      </div>
    </div>

    <div class="p-card log-card">
      <h3>📜 信令日志</h3>
      <div class="p-log">
        <div v-for="(log, i) in logs" :key="i" :class="['log-item', `log-${log.level}`]">
          <span class="log-time">{{ log.time }}</span>
          <span class="log-msg">{{ log.msg }}</span>
        </div>
      </div>
    </div>

    <div class="p-controls">
      <div v-if="peerConnected" class="p-active">
        <button :class="['p-btn-lg', recording ? 'rec' : 'start']" @click="toggleRecord">
          {{ recording ? '⏸ 停止录制' : '▶ 开始录制' }}
        </button>
        <button class="p-btn-lg warning" @click="onSimulateASR">🗣 模拟客户发言</button>
        <button class="p-btn-lg danger" @click="onHangup">📴 挂断</button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { showToast, showDialog } from 'vant'

const businessId = ref<string>('')
const localVideo = ref<HTMLVideoElement>()
const remoteVideo = ref<HTMLVideoElement>()
const watermarkEl = ref<HTMLCanvasElement>()

const peerConnected = ref(false)
const connectionState = ref('disconnected')
const recording = ref(false)
const startTime = ref(0)
const elapsed = ref(0)
const recordedBytes = ref(0)

const logs = ref<Array<{ time: string, level: string, msg: string }>>([])
const forbiddenHits = ref<Array<{ time: string, phrase: string, severity: string }>>([])

const stats = ref({
  signalingMessages: 0,
  forbiddenHits: 0,
  recording: false
})

let pc: RTCPeerConnection | null = null
let localStream: MediaStream | null = null
let mediaRecorder: MediaRecorder | null = null
let chunks: Blob[] = []
let timer: any = null
let watermarkTimer: any = null
let ws: WebSocket | null = null

const status = computed(() => {
  if (connectionState.value === 'connected' && recording.value) {
    stats.value.recording = true
    return { cls: 'live', label: 'LIVE' }
  }
  stats.value.recording = false
  if (connectionState.value === 'connected') return { cls: 'ready', label: '已连接' }
  if (connectionState.value === 'connecting') return { cls: 'connecting', label: '加入中...' }
  return { cls: 'idle', label: '待加入' }
})

const connectionCls = computed(() => {
  if (connectionState.value === 'connected') return 'ok'
  if (connectionState.value === 'connecting') return 'pending'
  return 'idle'
})

const connectionLabel = computed(() => {
  return ({ disconnected: '未连接', connecting: '连接中...', connected: '已连接' } as any)[connectionState.value] || '未知'
})

const formattedTime = computed(() => {
  const total = elapsed.value / 1000
  const m = Math.floor(total / 60)
  const s = Math.floor(total % 60)
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
})

onMounted(() => addLog('info', '坐席双边录制台就绪'))
onUnmounted(() => cleanup())

function addLog(level: string, msg: string) {
  const t = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
  logs.value.unshift({ time: t, level, msg })
  if (logs.value.length > 50) logs.value = logs.value.slice(0, 50)
  console.log(`[${level}] ${msg}`)
}

function formatSize(b: number) {
  if (b < 1024) return b + ' B'
  if (b < 1024 * 1024) return (b / 1024).toFixed(1) + ' KB'
  return (b / 1024 / 1024).toFixed(2) + ' MB'
}

async function toggleConnect() {
  if (peerConnected.value) {
    cleanup()
  } else {
    await joinRoom()
  }
}

async function joinRoom() {
  if (!businessId.value) {
    showToast('请输入业务ID')
    return
  }
  connectionState.value = 'connecting'
  try {
    // 1. 摄像头
    addLog('info', '申请坐席摄像头...')
    localStream = await navigator.mediaDevices.getUserMedia({
      video: { width: 1280, height: 720 },
      audio: { echoCancellation: true, noiseSuppression: true }
    })
    if (localVideo.value) localVideo.value.srcObject = localStream
    nextTick(() => startWatermark())
    addLog('ok', '坐席摄像头已开启')

    // 2. PeerConnection
    pc = new RTCPeerConnection({ iceServers: [{ urls: 'stun:stun.l.google.com:19302' }] })
    localStream.getTracks().forEach(t => pc!.addTrack(t, localStream!))
    pc.ontrack = (e) => {
      addLog('ok', '收到客户视频流')
      if (remoteVideo.value) remoteVideo.value.srcObject = e.streams[0]
    }
    pc.onicecandidate = (e) => {
      if (e.candidate) sendSignaling({ type: 'ICE_CANDIDATE', candidate: e.candidate })
    }
    pc.onconnectionstatechange = () => {
      addLog('info', `PC 状态: ${pc?.connectionState}`)
      if (pc?.connectionState === 'connected') {
        peerConnected.value = true
        connectionState.value = 'connected'
        addLog('ok', 'P2P 连接已建立')
      }
    }
    addLog('ok', 'PeerConnection 创建')

    // 3. 信令
    connectSignaling()
  } catch (e: any) {
    addLog('error', `加入房间失败: ${e.message}`)
    showToast('摄像头权限被拒绝')
    connectionState.value = 'disconnected'
  }
}

function connectSignaling() {
  const wsBase = import.meta.env.VITE_WS_BASE || `ws://${location.host}`
  const url = `${wsBase}/ws/bilateral/${businessId.value}/AGENT`
  addLog('info', `连接信令: ${url}`)
  ws = new WebSocket(url)
  ws.onopen = () => {
    addLog('ok', '坐席信令已连接')
    sendSignaling({ type: 'READY', role: 'AGENT' })
  }
  ws.onmessage = (evt) => {
    try {
      handleSignaling(JSON.parse(evt.data))
    } catch {}
  }
  ws.onerror = () => addLog('error', '信令错误')
  ws.onclose = () => {
    addLog('warn', '信令关闭')
    connectionState.value = 'disconnected'
  }
}

async function handleSignaling(msg: any) {
  stats.value.signalingMessages++
  addLog('info', `收到: ${msg.type}`)
  switch (msg.type) {
    case 'OFFER':
      if (pc && msg.sdp) {
        await pc.setRemoteDescription({ type: 'offer', sdp: msg.sdp })
        const answer = await pc.createAnswer()
        await pc.setLocalDescription(answer)
        sendSignaling({ type: 'ANSWER', sdp: answer.sdp })
        addLog('ok', 'OFFER → ANSWER')
      }
      break
    case 'ICE_CANDIDATE':
      if (pc && msg.candidate) {
        try {
          await pc.addIceCandidate(msg.candidate)
        } catch (e) {
          addLog('error', `ICE 失败: ${e}`)
        }
      }
      break
    case 'PEER_JOINED':
      addLog('ok', `对端 ${msg.role} 加入房间`)
      break
    case 'PEER_LEFT':
      addLog('warn', `对端 ${msg.role} 离开`)
      break
    case 'ASR_CHUNK':
      // 客户 ASR 转写, 坐席端可以显示
      addLog('info', `客户发言: "${msg.text}"`)
      // 这里可以加禁播词扫描, 但后端已经做了
      break
    case 'FORBIDDEN_PHRASE_HIT':
      const phrase = msg.phrase || (msg.hits?.[0]?.phrase) || '禁播词'
      forbiddenHits.value.unshift({
        time: new Date().toLocaleTimeString('zh-CN'),
        phrase,
        severity: msg.severity || 'HIGH'
      })
      stats.value.forbiddenHits++
      addLog('error', `🚫 禁播词: ${phrase}`)
      showDialog({ title: '🚫 禁播词警告', message: `客户触发禁播词: "${phrase}"\n请立即纠正!` })
      break
    case 'NODE_TICK':
      addLog('info', `节点: ${msg.node}`)
      break
  }
}

function sendSignaling(msg: any) {
  if (ws?.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify(msg))
  }
}

function startWatermark() {
  if (!watermarkEl.value) return
  const canvas = watermarkEl.value
  const ctx = canvas.getContext('2d')!
  canvas.width = 1280
  canvas.height = 720
  const draw = () => {
    ctx.clearRect(0, 0, canvas.width, canvas.height)
    ctx.fillStyle = 'rgba(255,255,255,0.7)'
    ctx.font = '18px monospace'
    const ts = new Date().toISOString().slice(0, 19).replace('T', ' ')
    ctx.fillText(`AGENT · ${businessId.value}`, 20, 30)
    ctx.fillText(ts, 20, 55)
    if (recording.value) {
      ctx.fillStyle = 'rgba(238,10,36,0.9)'
      ctx.fillText('● REC', 20, 80)
    }
  }
  draw()
  watermarkTimer = setInterval(draw, 1000)
}

function toggleRecord() {
  if (!localStream) return
  if (recording.value) {
    stopRecord()
  } else {
    startRecord()
  }
}

function startRecord() {
  if (!localStream) return
  try {
    const mime = MediaRecorder.isTypeSupported('video/webm;codecs=vp9') ? 'video/webm;codecs=vp9' : 'video/webm'
    mediaRecorder = new MediaRecorder(localStream, { mimeType: mime })
    mediaRecorder.ondataavailable = (e) => {
      if (e.data.size > 0) {
        chunks.push(e.data)
        recordedBytes.value += e.data.size
      }
    }
    mediaRecorder.start(1000)
    recording.value = true
    startTime.value = Date.now()
    timer = setInterval(() => { elapsed.value = Date.now() - startTime.value }, 1000)
    addLog('ok', '坐席开始录制')
    sendSignaling({ type: 'NODE_TICK', node: 'AGENT_START_RECORD' })
  } catch (e: any) {
    addLog('error', `录制失败: ${e.message}`)
  }
}

function stopRecord() {
  if (mediaRecorder && mediaRecorder.state !== 'inactive') mediaRecorder.stop()
  recording.value = false
  if (timer) { clearInterval(timer); timer = null }
  const blob = new Blob(chunks, { type: 'video/webm' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${businessId.value}-agent.webm`
  a.click()
  addLog('ok', `坐席录制停止 (${formatSize(recordedBytes.value)}), 已下载`)
  sendSignaling({ type: 'NODE_TICK', node: 'AGENT_STOP_RECORD' })
}

function onSimulateASR() {
  // 模拟客户触发禁播词的 ASR (后端会扫描)
  sendSignaling({
    type: 'ASR_CHUNK',
    text: '本产品保证收益, 保本保息, 绝对安全',
    timestamp: Date.now()
  })
  addLog('info', '发送模拟禁播词 ASR')
}

function onHangup() {
  showDialog({ title: '挂断', message: '确认挂断?', showCancelButton: true })
    .then(() => cleanup())
    .catch(() => {})
}

function cleanup() {
  if (mediaRecorder && mediaRecorder.state !== 'inactive') {
    try { mediaRecorder.stop() } catch {}
  }
  if (localStream) { localStream.getTracks().forEach(t => t.stop()); localStream = null }
  if (pc) { pc.close(); pc = null }
  if (ws) { try { sendSignaling({ type: 'BYE' }) } catch {}; ws.close(); ws = null }
  if (timer) { clearInterval(timer); timer = null }
  if (watermarkTimer) { clearInterval(watermarkTimer); watermarkTimer = null }
  recording.value = false
  peerConnected.value = false
  connectionState.value = 'disconnected'
  addLog('info', '已断开')
}
</script>

<style lang="scss" scoped>
.pc-bilateral { min-height: 100vh; background: var(--bg); padding-bottom: 16px; }

.p-header {
  background: linear-gradient(135deg, #1e2a47 0%, #2c3a5c 100%);
  color: white;
  padding: 16px 24px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.p-title { font-size: 18px; font-weight: 600; }
.p-status {
  display: flex; align-items: center; gap: 4px;
  padding: 4px 10px; border-radius: 12px;
  font-size: 11px; font-weight: 600;
  &.live { background: rgba(238,10,36,0.2); color: #ff6b6b; }
  &.ready { background: rgba(7,193,96,0.2); color: #4ade80; }
  &.connecting { background: rgba(255,151,106,0.2); color: #fbbf24; }
  &.idle { background: rgba(255,255,255,0.15); color: rgba(255,255,255,0.6); }
}
.p-status-dot {
  width: 8px; height: 8px; border-radius: 50%; background: currentColor;
  &.live { animation: pulse 1s infinite; }
}
@keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.4; } }

.p-toolbar {
  display: flex; gap: 8px; padding: 12px 16px;
  background: white;
  border-bottom: 1px solid var(--border);
}
.p-input {
  flex: 1;
  padding: 8px 12px;
  border: 1px solid var(--border);
  border-radius: 6px;
  font-size: 14px;
  font-family: monospace;
  &:focus { outline: none; border-color: var(--accent); }
}
.p-btn {
  padding: 8px 20px;
  border: none;
  border-radius: 6px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  color: white;
  &.primary { background: var(--primary); }
  &.danger { background: var(--danger); }
}

.p-videos {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 8px;
  padding: 12px;
  height: 280px;
}
.p-video {
  position: relative;
  background: #000;
  border-radius: 8px;
  overflow: hidden;
}
.p-video.remote { border: 2px solid var(--accent); }
.p-video.local { border: 2px solid var(--primary); }
.p-video-el { width: 100%; height: 100%; object-fit: cover; }
.p-watermark { position: absolute; inset: 0; width: 100%; height: 100%; pointer-events: none; }
.p-video-label {
  position: absolute; top: 8px; left: 8px;
  background: rgba(0,0,0,0.5); color: white;
  font-size: 11px; padding: 2px 8px; border-radius: 3px;
  z-index: 10;
}
.p-video-placeholder {
  position: absolute; inset: 0;
  display: flex; flex-direction: column;
  align-items: center; justify-content: center;
  background: #1a1a1a;
  color: rgba(255,255,255,0.5);
}
.p-placeholder-icon { font-size: 48px; margin-bottom: 8px; opacity: 0.6; }
.p-video-placeholder p { font-size: 12px; margin: 0; }

.p-info-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  padding: 0 16px;
}
.p-stat {
  background: white;
  border-radius: 8px;
  padding: 12px;
  text-align: center;
}
.p-stat-num {
  font-size: 18px;
  font-weight: 700;
  color: var(--primary);
  font-family: 'JetBrains Mono', monospace;
  &.danger { color: var(--danger); }
}
.p-stat-lbl { font-size: 11px; color: var(--text-3); margin-top: 2px; }

.p-panels {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  padding: 12px 16px;
}
.p-card {
  background: white;
  border-radius: 8px;
  padding: 12px 16px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.05);
  h3 { font-size: 13px; font-weight: 600; margin: 0 0 8px; }
}
.log-card { margin: 0 16px 12px; }
.p-list { list-style: none; padding: 0; margin: 0; }
.p-list li {
  display: flex; justify-content: space-between;
  padding: 4px 0;
  font-size: 12px;
  border-bottom: 1px solid var(--border);
  &:last-child { border-bottom: none; }
}
.mono { font-family: monospace; }
.conn { font-weight: 500; }
.conn.ok { color: var(--success); }
.conn.pending { color: var(--warning); }
.conn.idle { color: var(--text-3); }
.conn.rec { color: var(--danger); }

.p-empty { text-align: center; padding: 16px; color: var(--text-3); font-size: 12px; }
.p-hit-list { list-style: none; padding: 0; margin: 0; max-height: 200px; overflow-y: auto; }
.p-hit-item {
  padding: 6px 8px;
  background: rgba(238,10,36,0.05);
  border-left: 3px solid var(--danger);
  border-radius: 4px;
  margin-bottom: 4px;
  font-size: 11px;
}
.hit-time { color: var(--text-3); font-family: monospace; }
.hit-phrase { font-weight: 600; color: var(--danger); margin: 2px 0; }
.hit-sev {
  display: inline-block;
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 3px;
  &.sev-HIGH { background: var(--danger); color: white; }
  &.sev-MEDIUM { background: var(--warning); color: white; }
  &.sev-LOW { background: var(--text-3); color: white; }
}

.p-log {
  max-height: 180px;
  overflow-y: auto;
  background: #1a1a1a;
  border-radius: 6px;
  padding: 8px;
  font-family: 'JetBrains Mono', monospace;
}
.log-item {
  display: flex; gap: 8px;
  font-size: 11px;
  padding: 2px 0;
  color: rgba(255,255,255,0.7);
}
.log-time { color: rgba(255,255,255,0.4); }
.log-ok .log-msg { color: #4ade80; }
.log-warn .log-msg { color: #fbbf24; }
.log-error .log-msg { color: #ff6b6b; }

.p-controls {
  padding: 12px 16px;
}
.p-active {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 8px;
}
.p-btn-lg {
  padding: 14px;
  border: none;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  color: white;
  &.start { background: var(--accent); }
  &.rec { background: var(--danger); }
  &.warning { background: var(--warning); }
  &.danger { background: rgba(238,10,36,0.1); color: var(--danger); border: 1px solid var(--danger); }
}
</style>
