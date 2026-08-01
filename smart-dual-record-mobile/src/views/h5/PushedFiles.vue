<template>
  <div class="files-page">
    <div class="page-header">
      <h1 class="page-title">待签文件</h1>
      <p class="page-subtitle">坐席推送的文件，请及时查看与签署</p>
    </div>

    <div v-if="files.length === 0" class="empty">
      <div class="empty-icon">📭</div>
      <p>暂无待签文件</p>
    </div>

    <div v-else class="files-list">
      <div v-for="f in files" :key="f.id" class="file-card" :class="`status-${f.status}`" @click="onOpen(f)">
        <div class="file-icon">{{ f.icon }}</div>
        <div class="file-info">
          <div class="file-name">{{ f.name }}</div>
          <div class="file-meta">
            <span class="file-type">{{ f.type }}</span>
            <span class="file-time">{{ f.pushedAt }}</span>
          </div>
        </div>
        <div :class="['file-status', f.status]">{{ statusLabel(f.status) }}</div>
      </div>
    </div>

    <van-dialog
      v-model:show="showViewer"
      :title="current.name"
      show-cancel-button
      confirm-button-text="签署"
      cancel-button-text="关闭"
      @confirm="onSign"
    >
      <div class="file-viewer">
        <div class="file-preview">
          <div class="preview-icon">{{ current.icon }}</div>
          <div class="preview-name">{{ current.name }}</div>
          <div class="preview-desc">{{ current.desc }}</div>
        </div>
      </div>
    </van-dialog>

    <van-dialog
      v-model:show="showSign"
      title="电子签名"
      show-cancel-button
      confirm-button-text="提交签署"
      @confirm="onSignSubmit"
    >
      <div class="sign-area">
        <p class="sign-tip">请在下方手写签名</p>
        <canvas ref="signCanvasEl" width="320" height="160" class="sign-canvas"
          @touchstart="onSignStart" @touchmove="onSignMove" @touchend="onSignEnd"></canvas>
        <div class="sign-actions">
          <van-button size="small" plain @click="clearSign">清空</van-button>
        </div>
      </div>
    </van-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, nextTick } from 'vue'
import { showToast } from 'vant'

const files = ref([
  { id: 'f001', name: '产品说明书.pdf', type: 'PDF', icon: '📄', status: 'PUSHED', pushedAt: '2026-08-01 10:30', desc: 'BNK-FIN-2026Q3-001 固收理财说明书' },
  { id: 'f002', name: '风险揭示书.pdf', type: 'PDF', icon: '⚠️', status: 'VIEWED', pushedAt: '2026-08-01 10:32', desc: 'R2 中低风险揭示书' },
  { id: 'f003', name: '电子合同.pdf', type: 'PDF', icon: '📝', status: 'PUSHED', pushedAt: '2026-08-01 10:35', desc: '理财合同正文 (3 页)' },
  { id: 'f004', name: '收益走势图.png', type: 'IMG', icon: '📊', status: 'SIGNED', pushedAt: '2026-07-25 14:00', desc: '近 1 年收益曲线' }
])

const showViewer = ref(false)
const showSign = ref(false)
const current = ref<any>({})

const signCanvasEl = ref<HTMLCanvasElement>()
let signContext: CanvasRenderingContext2D | null = null
let isDrawing = false
let lastPos = { x: 0, y: 0 }

function statusLabel(s: string) {
  return ({ PUSHED: '待查阅', VIEWED: '已查阅', SIGNED: '已签署', REJECTED: '已拒签' } as Record<string, string>)[s] || s
}

function onOpen(f: any) {
  current.value = f
  showViewer.value = true
  // 标记已读
  if (f.status === 'PUSHED') f.status = 'VIEWED'
}

function onSign() {
  showViewer.value = false
  showSign.value = true
  nextTick(() => setupCanvas())
}

function setupCanvas() {
  if (!signCanvasEl.value) return
  const c = signCanvasEl.value
  const ratio = window.devicePixelRatio || 1
  c.width = 320 * ratio
  c.height = 160 * ratio
  c.style.width = '100%'
  c.style.height = '160px'
  signContext = c.getContext('2d')!
  signContext.scale(ratio, ratio)
  signContext.strokeStyle = '#000'
  signContext.lineWidth = 2
  signContext.lineCap = 'round'
}

function onSignStart(e: TouchEvent) {
  e.preventDefault()
  isDrawing = true
  const r = signCanvasEl.value!.getBoundingClientRect()
  lastPos = { x: e.touches[0].clientX - r.left, y: e.touches[0].clientY - r.top }
}
function onSignMove(e: TouchEvent) {
  if (!isDrawing || !signContext) return
  e.preventDefault()
  const r = signCanvasEl.value!.getBoundingClientRect()
  const x = e.touches[0].clientX - r.left
  const y = e.touches[0].clientY - r.top
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

function onSignSubmit() {
  current.value.status = 'SIGNED'
  showSign.value = false
  showToast('签署成功')
}
</script>

<style lang="scss" scoped>
.files-page { min-height: 100vh; }

.empty { padding: 100px 20px; text-align: center; color: var(--text-3); }
.empty-icon { font-size: 56px; margin-bottom: 8px; opacity: 0.4; }

.files-list { padding: 12px; }
.file-card {
  display: flex;
  align-items: center;
  gap: 12px;
  background: var(--card);
  padding: 12px;
  border-radius: 12px;
  margin-bottom: 8px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.05);
  &.status-PUSHED { border-left: 3px solid var(--accent); }
  &.status-VIEWED { border-left: 3px solid var(--primary); }
  &.status-SIGNED { border-left: 3px solid var(--success); opacity: 0.7; }
}
.file-icon { font-size: 32px; }
.file-info { flex: 1; min-width: 0; }
.file-name { font-size: 14px; font-weight: 600; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.file-meta { display: flex; gap: 8px; margin-top: 4px; font-size: 11px; color: var(--text-3); }
.file-type { background: var(--cream); padding: 1px 6px; border-radius: 3px; }
.file-status {
  font-size: 11px;
  padding: 3px 8px;
  border-radius: 4px;
  font-weight: 500;
  &.PUSHED { background: rgba(184,134,11,0.1); color: var(--accent); }
  &.VIEWED { background: rgba(30,42,71,0.1); color: var(--primary); }
  &.SIGNED { background: rgba(7,193,96,0.1); color: var(--success); }
  &.REJECTED { background: rgba(238,10,36,0.1); color: var(--danger); }
}

.file-viewer { padding: 20px; }
.file-preview {
  text-align: center;
  padding: 40px 20px;
  background: var(--bg);
  border-radius: 8px;
}
.preview-icon { font-size: 48px; margin-bottom: 12px; }
.preview-name { font-size: 15px; font-weight: 600; }
.preview-desc { font-size: 12px; color: var(--text-3); margin-top: 4px; }

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
