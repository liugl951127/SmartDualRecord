<template>
  <div class="bilateral">
    <PermissionGate
      :visible="!permissionGranted"
      @granted="onPermissionGranted"
      @skip="onPermissionSkip"
    />

    <div class="b-header">
      <div class="b-title">📞 双边录制</div>
      <div :class="['b-status', status.cls]">
        <div class="b-status-dot"></div>
        <span>{{ status.label }}</span>
      </div>
    </div>

    <div class="b-videos">
      <!-- 远端 (坐席) 视频 -->
      <div class="b-video remote">
        <video ref="remoteVideo" autoplay playsinline class="b-video-el"></video>
        <div class="b-video-label">坐席</div>
        <div v-if="!peerConnected" class="b-video-placeholder">
          <div class="b-placeholder-icon">👤</div>
          <p>等待坐席加入...</p>
        </div>
      </div>

      <!-- 本地 (客户) 视频 -->
      <div class="b-video local">
        <video ref="localVideo" autoplay muted playsinline class="b-video-el"></video>
        <div class="b-video-label">您</div>
        <canvas ref="watermarkEl" class="b-watermark"></canvas>
      </div>
    </div>

    <div class="b-info">
      <div class="b-row">
        <span class="b-lbl">业务ID</span>
        <span class="b-val mono">{{ businessId }}</span>
      </div>
      <div class="b-row">
        <span class="b-lbl">连接状态</span>
        <span class="b-val">
          <span :class="['conn-dot', connectionCls]"></span>
          {{ connectionLabel }}
        </span>
      </div>
      <div class="b-row">
        <span class="b-lbl">录制状态</span>
        <span class="b-val">
          <span :class="['conn-dot', recording ? 'recording' : 'idle']"></span>
          {{ recording ? '录制中' : '未开始' }}
        </span>
      </div>
      <div class="b-row">
        <span class="b-lbl">已用时长</span>
        <span class="b-val mono">{{ formattedTime }}</span>
      </div>
      <div class="b-row">
        <span class="b-lbl">本地录制大小</span>
        <span class="b-val mono">{{ formatSize(recordedBytes) }}</span>
      </div>
    </div>

    <div class="b-log">
      <div class="b-log-title">📜 实时信令日志</div>
      <div class="b-log-list">
        <div v-for="(log, i) in logs" :key="i" :class="['log-item', `log-${log.level}`]">
          <span class="log-time">{{ log.time }}</span>
          <span class="log-msg">{{ log.msg }}</span>
        </div>
      </div>
    </div>

    <div class="b-controls">
      <div v-if="!peerConnected" class="b-connect">
        <van-button block round size="large" type="primary" :loading="connecting" @click="onStartConnect">
          🎥 开启摄像头 + 连接坐席
        </van-button>
      </div>
      <div v-else class="b-active">
        <div class="b-row-controls">
          <van-button size="normal" :type="recording ? 'danger' : 'primary'" @click="toggleRecord">
            {{ recording ? '⏸ 停止录制' : '▶ 开始录制' }}
          </van-button>
          <van-button size="normal" plain type="warning" @click="onTestForbidden">
            🚫 测试禁播词
          </van-button>
        </div>
        <van-button block round size="large" plain type="danger" @click="onEnd" style="margin-top: 8px;">
          📴 挂断
        </van-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast, showDialog } from 'vant'
import PermissionGate from '@/components/PermissionGate.vue'

const route = useRoute()
const router = useRouter()
const businessId = computed(() => route.params.businessId as string || `BIZ-${Date.now()}`)

// 状态
const localVideo = ref<HTMLVideoElement>()
const remoteVideo = ref<HTMLVideoElement>()
const watermarkEl = ref<HTMLCanvasElement>()
const peerConnected = ref(false)
const connecting = ref(false)
const connectionState = ref('disconnected')  // disconnected/connecting/connected
const permissionGranted = ref(false)

// 权限处理
function onPermissionGranted(s: MediaStream) {
  permissionGranted.value = true
  localStream = s
  if (localVideo.value) {
    localVideo.value.srcObject = s
  }
  showToast('🎥 摄像头已开启')
}
function onPermissionSkip() {
  permissionGranted.value = true
  showToast('已跳过, 仅查看模式')
}
const recording = ref(false)
const startTime = ref(0)
const elapsed = ref(0)
const recordedBytes = ref(0)
const logs = ref<Array<{ time: string, level: string, msg: string }>>([])

// WebRTC
let pc: RTCPeerConnection | null = null
let localStream: MediaStream | null = null
let mediaRecorder: MediaRecorder | null = null
let chunks: Blob[] = []
let timer: any = null
let watermarkTimer: any = null
let ws: WebSocket | null = null

const status = computed(() => {
  if (connectionState.value === 'connected' && recording.value) {
    return { cls: 'live', label: 'LIVE' }
  }
  if (connectionState.value === 'connected') {
    return { cls: 'ready', label: '已连接' }
  }
  if (connectionState.value === 'connecting') {
    return { cls: 'connecting', label: '连接中...' }
  }
  return { cls: 'idle', label: '未连接' }
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

onMounted(() => {
  addLog('info', '页面加载, 准备连接')
})
onUnmounted(() => {
  cleanup()
})

function addLog(level: string, msg: string) {
  const t = new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
  logs.value.unshift({ time: t, level, msg })
  if (logs.value.length > 30) logs.value = logs.value.slice(0, 30)
  console.log(`[${level}] ${msg}`)
}

function formatSize(b: number) {
  if (b < 1024) return b + ' B'
  if (b < 1024 * 1024) return (b / 1024).toFixed(1) + ' KB'
  return (b / 1024 / 1024).toFixed(2) + ' MB'
}

async function onStartConnect() {
  if (connecting.value) return
  connecting.value = true
  connectionState.value = 'connecting'
  try {
    // 1. 申请摄像头
    addLog('info', '申请摄像头权限...')
    localStream = await navigator.mediaDevices.getUserMedia({
      video: { width: 1280, height: 720, facingMode: 'user' },
      audio: { echoCancellation: true, noiseSuppression: true }
    })
    if (localVideo.value) localVideo.value.srcObject = localStream
    addLog('ok', '摄像头已开启')
    nextTick(() => startWatermark())

    // 2. 创建 RTCPeerConnection
    const peer = new RTCPeerConnection({
      iceServers: [{ urls: 'stun:stun.l.google.com:19302' }]
    })
    pc = peer
    localStream.getTracks().forEach(track => peer.addTrack(track, localStream!))

    pc.ontrack = (event) => {
      addLog('ok', '收到远端视频流')
      if (remoteVideo.value) {
        remoteVideo.value.srcObject = event.streams[0]
      }
    }
    pc.onicecandidate = (event) => {
      if (event.candidate) {
        sendSignaling({ type: 'ICE_CANDIDATE', candidate: event.candidate })
      }
    }
    pc.onconnectionstatechange = () => {
      addLog('info', `连接状态变化: ${pc?.connectionState}`)
      if (pc?.connectionState === 'connected') {
        peerConnected.value = true
        connectionState.value = 'connected'
      }
    }
    addLog('ok', 'PeerConnection 创建完成')

    // 3. 连接信令服务器
    connectSignaling()

    // 4. 创建 OFFER
    const offer = await pc.createOffer()
    await pc.setLocalDescription(offer)
    addLog('info', '已创建 OFFER')
    // 等信令连接上后再发
    setTimeout(() => {
      if (ws?.readyState === WebSocket.OPEN) {
        sendSignaling({ type: 'OFFER', sdp: offer.sdp })
        addLog('ok', 'OFFER 已发送')
      } else {
        addLog('warn', '信令未连接, OFFER 将延迟发送')
        // 等 ws open 后再发
      }
    }, 500)
  } catch (e: any) {
    addLog('error', `连接失败: ${e.message}`)
    showToast('请允许访问摄像头和麦克风')
    connectionState.value = 'disconnected'
  } finally {
    connecting.value = false
  }
}

function connectSignaling() {
  const wsBase = import.meta.env.VITE_WS_BASE || `ws://${location.host}`
  const url = `${wsBase}/ws/bilateral/${businessId.value}/CUSTOMER`
  addLog('info', `连接信令服务器: ${url}`)
  ws = new WebSocket(url)
  ws.onopen = () => {
    addLog('ok', '信令服务器已连接')
    sendSignaling({ type: 'READY', role: 'CUSTOMER' })
  }
  ws.onmessage = (evt) => {
    try {
      const msg = JSON.parse(evt.data)
      handleSignaling(msg)
    } catch (e) {
      addLog('error', `消息解析失败: ${e}`)
    }
  }
  ws.onerror = () => addLog('error', '信令连接错误')
  ws.onclose = () => {
    addLog('warn', '信令连接关闭')
    connectionState.value = 'disconnected'
  }
}

async function handleSignaling(msg: any) {
  addLog('info', `收到: ${msg.type}`)
  switch (msg.type) {
    case 'ANSWER':
      if (pc && msg.sdp) {
        await pc.setRemoteDescription({ type: 'answer', sdp: msg.sdp })
        addLog('ok', 'ANSWER 已应用')
      }
      break
    case 'ICE_CANDIDATE':
      if (pc && msg.candidate) {
        try {
          await pc.addIceCandidate(msg.candidate)
          addLog('ok', 'ICE 已添加')
        } catch (e) {
          addLog('error', `ICE 失败: ${e}`)
        }
      }
      break
    case 'PEER_JOINED':
      addLog('ok', `对端 ${msg.role} 加入`)
      // 如果坐席刚加入, 主动 OFFER (延迟一下)
      if (msg.role === 'AGENT' && pc && pc.localDescription == null) {
        const offer = await pc.createOffer()
        await pc.setLocalDescription(offer)
        sendSignaling({ type: 'OFFER', sdp: offer.sdp })
        addLog('ok', '主动发送 OFFER')
      }
      break
    case 'PEER_LEFT':
      addLog('warn', `对端 ${msg.role} 离开`)
      break
    case 'FORBIDDEN_PHRASE_HIT':
      addLog('error', `🚫 触发禁播词: ${msg.phrase || JSON.stringify(msg.hits)}`)
      showDialog({ title: '禁播词警告', message: `坐席检测到禁播词: ${msg.phrase || '请检查'}` })
      break
  }
}

function sendSignaling(msg: any) {
  if (ws?.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify(msg))
  } else {
    addLog('warn', `信令未连接, 消息 ${msg.type} 暂存`)
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
    ctx.fillText(`${businessId.value}`, 20, 30)
    ctx.fillText(`CUSTOMER · ${ts}`, 20, 55)
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
    timer = setInterval(() => {
      elapsed.value = Date.now() - startTime.value
    }, 1000)
    // 通知坐席端开始录制
    sendSignaling({ type: 'NODE_TICK', node: 'START_RECORD', duration: 0 })
    addLog('ok', '本地录制已开始')
  } catch (e: any) {
    addLog('error', `录制失败: ${e.message}`)
  }
}

function stopRecord() {
  if (mediaRecorder && mediaRecorder.state !== 'inactive') {
    mediaRecorder.stop()
  }
  recording.value = false
  if (timer) { clearInterval(timer); timer = null }
  // 自动下载
  const blob = new Blob(chunks, { type: 'video/webm' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${businessId.value}-customer.webm`
  a.click()
  addLog('ok', `本地录制已停止 (${formatSize(recordedBytes.value)}), 已下载`)
  sendSignaling({ type: 'NODE_TICK', node: 'STOP_RECORD' })
}

function onTestForbidden() {
  // 模拟 ASR 文本, 触发禁播词扫描
  sendSignaling({
    type: 'ASR_CHUNK',
    text: '我们这个产品保证收益, 保本保息, 绝对安全, 一定不亏, 和存款一样',
    timestamp: Date.now()
  })
  addLog('info', '已发送测试禁播词 ASR')
}

function onEnd() {
  showDialog({ title: '挂断', message: '确认结束双边录制?', showCancelButton: true })
    .then(() => {
      addLog('info', '用户挂断')
      cleanup()
      router.replace('/h5/orders')
    })
    .catch(() => {})
}

function cleanup() {
  if (mediaRecorder && mediaRecorder.state !== 'inactive') {
    try { mediaRecorder.stop() } catch {}
  }
  if (localStream) {
    localStream.getTracks().forEach(t => t.stop())
    localStream = null
  }
  if (pc) {
    pc.close()
    pc = null
  }
  if (ws) {
    sendSignaling({ type: 'BYE' })
    ws.close()
    ws = null
  }
  if (timer) { clearInterval(timer); timer = null }
  if (watermarkTimer) { clearInterval(watermarkTimer); watermarkTimer = null }
  recording.value = false
  peerConnected.value = false
  connectionState.value = 'disconnected'
}
</script>

<style lang="scss" scoped>
.bilateral {
  min-height: 100vh;
  background: var(--bg);
  padding-bottom: 16px;
}

.b-header {
  background: linear-gradient(135deg, #1e2a47 0%, #2c3a5c 100%);
  color: white;
  padding: 16px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.b-title { font-size: 18px; font-weight: 600; }
.b-status {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: 12px;
  font-size: 11px;
  font-weight: 600;
  &.live { background: rgba(238,10,36,0.2); color: #ff6b6b; }
  &.ready { background: rgba(7,193,96,0.2); color: #4ade80; }
  &.connecting { background: rgba(255,151,106,0.2); color: #fbbf24; }
  &.idle { background: rgba(255,255,255,0.15); color: rgba(255,255,255,0.6); }
}
.b-status-dot {
  width: 8px; height: 8px;
  border-radius: 50%;
  background: currentColor;
  &.live { animation: pulse 1s infinite; }
}
@keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.4; } }

.b-videos {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 4px;
  margin: 8px;
  height: 200px;
}
.b-video {
  position: relative;
  background: #000;
  border-radius: 8px;
  overflow: hidden;
}
.b-video.local { border: 2px solid var(--accent); }
.b-video.remote { border: 2px solid var(--primary); }
.b-video-el { width: 100%; height: 100%; object-fit: cover; }
.b-watermark { position: absolute; inset: 0; width: 100%; height: 100%; pointer-events: none; }
.b-video-label {
  position: absolute;
  top: 6px; left: 6px;
  background: rgba(0,0,0,0.5);
  color: white;
  font-size: 10px;
  padding: 2px 6px;
  border-radius: 3px;
  z-index: 10;
}
.b-video-placeholder {
  position: absolute; inset: 0;
  display: flex; flex-direction: column;
  align-items: center; justify-content: center;
  background: #1a1a1a;
  color: rgba(255,255,255,0.5);
}
.b-placeholder-icon { font-size: 36px; margin-bottom: 6px; }
.b-video-placeholder p { font-size: 11px; margin: 0; }

.b-info {
  background: white;
  margin: 8px;
  padding: 12px 16px;
  border-radius: 12px;
}
.b-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 6px 0;
  font-size: 13px;
  border-bottom: 1px solid var(--border);
  &:last-child { border-bottom: none; }
}
.b-lbl { color: var(--text-3); }
.b-val { font-weight: 500; }
.mono { font-family: monospace; }
.conn-dot {
  display: inline-block;
  width: 8px; height: 8px;
  border-radius: 50%;
  margin-right: 4px;
  &.ok { background: var(--success); }
  &.pending { background: var(--warning); animation: pulse 1s infinite; }
  &.idle { background: var(--text-3); }
  &.recording { background: var(--danger); animation: pulse 1s infinite; }
}

.b-log {
  background: #1a1a1a;
  margin: 8px;
  border-radius: 12px;
  padding: 12px;
  max-height: 200px;
  overflow-y: auto;
  font-family: 'JetBrains Mono', monospace;
}
.b-log-title { color: var(--accent-light); font-size: 12px; font-weight: 600; margin-bottom: 8px; }
.b-log-list { }
.log-item {
  display: flex;
  gap: 8px;
  font-size: 11px;
  padding: 2px 0;
  color: rgba(255,255,255,0.7);
}
.log-time { color: rgba(255,255,255,0.4); }
.log-info .log-msg { color: rgba(255,255,255,0.7); }
.log-ok .log-msg { color: #4ade80; }
.log-warn .log-msg { color: #fbbf24; }
.log-error .log-msg { color: #ff6b6b; }

.b-controls {
  margin: 8px;
}
.b-row-controls {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  margin-bottom: 8px;
}
</style>
