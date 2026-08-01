<template>
  <transition name="fade">
    <div v-if="visible" class="pg-overlay">
      <div class="pg-modal">
        <div class="pg-icon" :class="state">
          <span v-if="state === 'granted'">✓</span>
          <span v-else-if="state === 'denied'">🚫</span>
          <span v-else-if="state === 'unsupported'">⚠️</span>
          <span v-else>🔐</span>
        </div>
        <h3 class="pg-title">
          <span v-if="state === 'requesting'">正在申请设备权限</span>
          <span v-else-if="state === 'granted'">权限已就绪</span>
          <span v-else-if="state === 'denied'">权限被拒绝</span>
          <span v-else-if="state === 'unsupported'">浏览器不支持</span>
          <span v-else>需要设备权限</span>
        </h3>
        <p class="pg-desc">
          <span v-if="state === 'idle'">为了开始双录, 我们需要访问您的<strong>摄像头</strong>和<strong>麦克风</strong>。<br/>请在浏览器弹窗中点击"允许"。</span>
          <span v-else-if="state === 'requesting'">正在打开浏览器权限申请弹窗...</span>
          <span v-else-if="state === 'granted'">✓ 摄像头 + 麦克风已就绪, 可以开始录制</span>
          <span v-else-if="state === 'denied'">您拒绝了权限, 无法继续录制。<br/>请到浏览器设置中开启摄像头和麦克风权限, 然后刷新页面。</span>
          <span v-else>当前浏览器不支持 getUserMedia API, 请使用 Chrome 70+、Safari 11+、微信内置浏览器等。</span>
        </p>

        <!-- 设备列表 -->
        <div class="dev-list" v-if="state === 'idle' || state === 'requesting'">
          <div v-if="!isLocalhost" class="url-warning">
            <span class="uw-icon">⚠️</span>
            <div class="uw-text">
              <div class="uw-title">当前不是 localhost 访问</div>
              <div class="uw-desc">摄像头权限要求 HTTPS 或 localhost。<br/>请改用: <code class="uw-code">{{ accessUrl }}</code></div>
            </div>
          </div>
          <div class="dev-item">
            <span class="dev-icon">📷</span>
            <div class="dev-info">
              <div class="dev-name">摄像头</div>
              <div class="dev-desc">用于录制您的画面 (1280x720)</div>
            </div>
            <span class="dev-state" :class="cameraOk && 'ok'">{{ cameraOk ? '✓' : '待授权' }}</span>
          </div>
          <div class="dev-item">
            <span class="dev-icon">🎤</span>
            <div class="dev-info">
              <div class="dev-name">麦克风</div>
              <div class="dev-desc">用于录制您的声音 + ASR 转写</div>
            </div>
            <span class="dev-state" :class="micOk && 'ok'">{{ micOk ? '✓' : '待授权' }}</span>
          </div>
          <div class="dev-item" v-if="checkSecureCtx">
            <span class="dev-icon">🔒</span>
            <div class="dev-info">
              <div class="dev-name">HTTPS 安全连接</div>
              <div class="dev-desc">浏览器要求 HTTPS 才能访问摄像头</div>
            </div>
            <span class="dev-state" :class="isSecure && 'ok'">{{ isSecure ? '✓' : '⚠' }}</span>
          </div>
        </div>

        <!-- 失败时的操作 -->
        <div v-if="state === 'denied'" class="pg-actions">
          <button class="btn btn-ghost" @click="onRetry">↻ 重新申请</button>
          <button class="btn btn-ghost" @click="onHelp">查看帮助</button>
          <button class="btn btn-accent" @click="onContinue">仅查看 (无录制)</button>
        </div>
        <div v-else-if="state === 'unsupported'" class="pg-actions">
          <button class="btn btn-ghost" @click="onContinue">返回</button>
        </div>
        <div v-else-if="state === 'idle'" class="pg-actions">
          <button class="btn btn-ghost" @click="onSkip">跳过 (无录制)</button>
          <button class="btn btn-accent btn-lg" @click="onRequest">
            🎬 开启摄像头和麦克风
          </button>
        </div>
        <div v-else-if="state === 'granted'" class="pg-actions">
          <button class="btn btn-accent btn-lg" @click="onContinue">
            进入录制 →
          </button>
        </div>
      </div>
    </div>
  </transition>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'

const props = defineProps<{
  visible: boolean
}>()

const emit = defineEmits<{
  granted: [stream: MediaStream]
  skip: []
  retry: []
  help: []
}>()

// ============ 状态 ============
const state = ref<'idle' | 'requesting' | 'granted' | 'denied' | 'unsupported'>('idle')
const cameraOk = ref(false)
const micOk = ref(false)
const stream = ref<MediaStream | null>(null)
const errorMessage = ref('')

// ============ 检查 ============
const isSecure = computed(() => {
  if (typeof location === 'undefined') return false
  return location.protocol === 'https:' || location.hostname === 'localhost' || location.hostname === '127.0.0.1'
})
const isLocalhost = computed(() => {
  if (typeof location === 'undefined') return false
  return location.hostname === 'localhost' || location.hostname === '127.0.0.1'
})
const checkSecureCtx = computed(() => !isSecure.value)
const accessUrl = computed(() => {
  if (typeof location === 'undefined') return ''
  return `http://localhost:${location.port || '5174'}`
})

const hasMediaDevices = computed(() => {
  return typeof navigator !== 'undefined' &&
    navigator.mediaDevices &&
    typeof navigator.mediaDevices.getUserMedia === 'function'
})

// ============ 申请权限 ============
async function onRequest() {
  if (!hasMediaDevices.value) {
    state.value = 'unsupported'
    return
  }
  state.value = 'requesting'
  errorMessage.value = ''
  try {
    const s = await navigator.mediaDevices.getUserMedia({
      video: {
        width: { ideal: 1280 },
        height: { ideal: 720 },
        facingMode: 'user'
      },
      audio: {
        echoCancellation: true,
        noiseSuppression: true,
        autoGainControl: true
      }
    })
    stream.value = s
    // 检查 track 状态
    s.getVideoTracks().forEach(t => {
      if (t.enabled) cameraOk.value = true
    })
    s.getAudioTracks().forEach(t => {
      if (t.enabled) micOk.value = true
    })
    if (cameraOk.value || micOk.value) {
      state.value = 'granted'
      setTimeout(() => {
        emit('granted', s)
      }, 600)
    } else {
      state.value = 'denied'
    }
  } catch (e: any) {
    console.warn('getUserMedia error', e)
    if (e.name === 'NotAllowedError' || e.name === 'PermissionDeniedError') {
      state.value = 'denied'
    } else if (e.name === 'NotFoundError') {
      state.value = 'denied'
      errorMessage.value = '未检测到摄像头/麦克风设备'
    } else if (e.name === 'NotReadableError') {
      state.value = 'denied'
      errorMessage.value = '设备被其他程序占用'
    } else {
      state.value = 'denied'
      errorMessage.value = e.message || '未知错误'
    }
  }
}

function onRetry() {
  state.value = 'idle'
  cameraOk.value = false
  micOk.value = false
  setTimeout(onRequest, 100)
}

function onSkip() {
  emit('skip')
}

function onContinue() {
  if (stream.value) {
    emit('granted', stream.value)
  } else {
    emit('skip')
  }
}

function onHelp() {
  emit('help')
}

onMounted(() => {
  // 检查浏览器支持
  if (!hasMediaDevices.value) {
    state.value = 'unsupported'
  }
})
</script>

<style lang="scss" scoped>
.pg-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.6);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  z-index: 9999;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}
.pg-modal {
  background: white;
  border-radius: 16px;
  padding: 28px 24px;
  max-width: 420px;
  width: 100%;
  text-align: center;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  animation: modal-in 0.3s ease-out;
}
@keyframes modal-in {
  from { opacity: 0; transform: translateY(20px) scale(0.95); }
  to { opacity: 1; transform: translateY(0) scale(1); }
}
.pg-icon {
  width: 80px;
  height: 80px;
  margin: 0 auto 16px;
  border-radius: 50%;
  background: linear-gradient(135deg, #1e2a47 0%, #2c3a5c 100%);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 36px;
  font-weight: 700;
  box-shadow: 0 8px 24px rgba(30, 42, 71, 0.3);
  &.granted { background: linear-gradient(135deg, #10b981 0%, #047857 100%); box-shadow: 0 8px 24px rgba(16, 185, 129, 0.3); }
  &.denied, &.unsupported { background: linear-gradient(135deg, #ef4444 0%, #b91c1c 100%); box-shadow: 0 8px 24px rgba(239, 68, 68, 0.3); }
  &.requesting {
    animation: pulse-rotate 1.5s ease-in-out infinite;
  }
}
@keyframes pulse-rotate {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.08); }
}
.pg-title {
  font-size: 18px;
  font-weight: 700;
  color: #1a1f36;
  margin: 0 0 8px;
}
.pg-desc {
  font-size: 13px;
  color: #5a6079;
  line-height: 1.6;
  margin: 0 0 20px;
  strong { color: #c08552; font-weight: 600; }
}

.dev-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 20px;
  text-align: left;
}
.url-warning {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px 14px;
  background: linear-gradient(135deg, #fef3c7 0%, #fed7aa 100%);
  border: 1px solid #fbbf24;
  border-radius: 10px;
  margin-bottom: 4px;
  text-align: left;
}
.uw-icon { font-size: 20px; flex-shrink: 0; }
.uw-title {
  font-size: 12px;
  font-weight: 700;
  color: #b45309;
  margin-bottom: 4px;
}
.uw-desc {
  font-size: 11px;
  color: #92400e;
  line-height: 1.5;
}
.uw-code {
  background: white;
  padding: 2px 6px;
  border-radius: 4px;
  font-family: 'JetBrains Mono', monospace;
  font-size: 11px;
  color: #b45309;
  border: 1px solid #fbbf24;
  user-select: all;
}
.dev-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 14px;
  background: #faf8f4;
  border: 1px solid #f0ede6;
  border-radius: 10px;
}
.dev-icon {
  font-size: 24px;
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: white;
  border-radius: 8px;
  flex-shrink: 0;
}
.dev-info { flex: 1; min-width: 0; }
.dev-name {
  font-size: 13px;
  font-weight: 600;
  color: #1a1f36;
}
.dev-desc {
  font-size: 11px;
  color: #5a6079;
  margin-top: 2px;
}
.dev-state {
  font-size: 11px;
  font-weight: 700;
  color: #98a0b3;
  padding: 4px 10px;
  background: #f0ede6;
  border-radius: 999px;
  &.ok {
    color: #047857;
    background: #d1fae5;
  }
}

.pg-actions {
  display: flex;
  gap: 8px;
  justify-content: center;
  flex-wrap: wrap;
  & > * { flex: 1; min-width: 100px; }
}

.btn {
  padding: 10px 18px;
  border: 1px solid transparent;
  border-radius: 8px;
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s;
}
.btn-ghost {
  background: white;
  color: #5a6079;
  border-color: #e8e4dc;
  &:hover { background: #f0ede6; color: #1a1f36; }
}
.btn-accent {
  background: linear-gradient(135deg, #c08552 0%, #d9a47a 100%);
  color: white;
  box-shadow: 0 4px 12px rgba(192, 133, 82, 0.3);
  &:hover { transform: translateY(-1px); box-shadow: 0 6px 16px rgba(192, 133, 82, 0.4); }
}
.btn-lg { padding: 14px 24px; font-size: 15px; font-weight: 600; }

.fade-enter-active, .fade-leave-active { transition: opacity 0.25s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>
