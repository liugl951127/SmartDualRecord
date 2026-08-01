<template>
  <div class="workbench">
    <div class="wb-header">
      <div class="biz-info">
        <div class="biz-id">{{ businessId }}</div>
        <div class="biz-meta">{{ customerName }} · {{ productName }}</div>
      </div>
      <div class="timer">
        <div class="t-label">录制时长</div>
        <div class="t-value">{{ formattedTime }}</div>
      </div>
    </div>

    <div class="wb-content">
      <div class="main-area">
        <div class="dual-cam">
          <div class="cam-box cam-customer">
            <div class="cam-label">客户</div>
            <video ref="customerVideo" autoplay muted playsinline class="cam-video"></video>
            <div class="cam-overlay">
              <div class="face-detect"></div>
            </div>
          </div>
          <div class="cam-box cam-agent">
            <div class="cam-label">坐席</div>
            <video ref="agentVideo" autoplay muted playsinline class="cam-video"></video>
          </div>
        </div>

        <div class="node-nav">
          <div class="node-tabs">
            <div
              v-for="(n, i) in nodes"
              :key="n.id"
              :class="['node-tab', getNodeCls(i)]"
              @click="gotoNode(i)"
            >
              <div class="nt-num">{{ i + 1 }}</div>
              <div class="nt-name">{{ n.name }}</div>
            </div>
          </div>
        </div>

        <div class="script-display">
          <div class="sd-title">📋 当前话术</div>
          <div class="sd-content">{{ currentScript }}</div>
        </div>
      </div>

      <div class="side-area">
        <div class="side-card">
          <h4>节点进度</h4>
          <div class="progress-info">
            <div class="pi-num">{{ currentNode + 1 }} / {{ nodes.length }}</div>
            <div class="pi-text">{{ nodes[currentNode].name }}</div>
          </div>
          <div class="progress-bar-mini">
            <div class="pbm-fill" :style="{ width: ((currentNode + 1) / nodes.length * 100) + '%' }"></div>
          </div>
        </div>

        <div class="side-card">
          <h4>禁播词扫描</h4>
          <div class="scan-result">
            <div class="sr-status ok">✓ 0 命中</div>
            <div class="sr-detail">实时监控中</div>
          </div>
        </div>

        <div class="side-card">
          <h4>录像检查</h4>
          <ul class="check-list">
            <li><span>画面</span><span class="ok">正常</span></li>
            <li><span>音频</span><span class="ok">正常</span></li>
            <li><span>人脸</span><span class="ok">已识别</span></li>
            <li><span>清晰度</span><span class="ok">1280×720</span></li>
          </ul>
        </div>

        <div class="side-card">
          <h4>操作</h4>
          <div class="action-list">
            <button :class="['action-btn', recording ? 'recording' : 'start']" @click="toggleRecord">
              {{ recording ? '⏸ 暂停' : '▶ 开始' }}
            </button>
            <button class="action-btn" @click="prev">◀ 上一节点</button>
            <button class="action-btn" @click="next">下一节点 ▶</button>
            <button class="action-btn danger" @click="onEnd">结束双录</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { showToast, showDialog } from 'vant'

const route = useRoute()
const businessId = route.params.businessId as string
const customerName = ref('张志强')
const productName = ref('稳赢系列 · 固收理财')

const nodes = [
  { id: '01-IDENTITY', name: '身份核验' },
  { id: '02-DISCLOSURE', name: '产品披露' },
  { id: '03-PRODUCT', name: '产品介绍' },
  { id: '04-RIGHTS', name: '权益告知' },
  { id: '05-TRUTH_TELL', name: '如实告知' },
  { id: '06-AFFIRMATIVE', name: '明确肯定' },
  { id: '07-SIGN', name: '电子签名' },
  { id: '08-FOLLOWUP', name: '犹豫期' }
]

const currentNode = ref(0)
const recording = ref(false)
const startTime = ref(Date.now())
const nodeElapsed = ref(0)

const customerVideo = ref<HTMLVideoElement>()
const agentVideo = ref<HTMLVideoElement>()

let customerStream: MediaStream | null = null
let agentStream: MediaStream | null = null
let mediaRecorder: MediaRecorder | null = null
let timer: any = null

const formattedTime = computed(() => {
  const total = nodeElapsed.value / 1000
  const m = Math.floor(total / 60)
  const s = Math.floor(total % 60)
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`
})

const currentScript = computed(() => {
  const map: any = {
    '01-IDENTITY': '请客户出示身份证原件, 联网核查身份...',
    '02-DISCLOSURE': '本产品属于 R2 级中低风险, 预期年化 3.6%, 投资期限 180 天...',
    '03-PRODUCT': '本产品为固收类理财, 主要投资于国债、政策性金融债...',
    '04-RIGHTS': '本产品有 24 小时冷静期, 冷静期内可无条件撤单...',
    '05-TRUTH_TELL': '请问客户的投资经验? 收入来源? 请确认以上信息真实有效...',
    '06-AFFIRMATIVE': '您是否已了解产品风险? 是否自愿购买? 是否同意相关协议?',
    '07-SIGN': '请客户在下方手写电子签名...',
    '08-FOLLOWUP': '双录已完成, 产品有 15 天犹豫期, 犹豫期内可申请撤单...'
  }
  return map[nodes[currentNode.value].id] || ''
})

function getNodeCls(i: number) {
  if (i < currentNode.value) return 'completed'
  if (i === currentNode.value) return 'active'
  return ''
}

onMounted(() => {
  startCameras()
  timer = setInterval(() => {
    nodeElapsed.value = Date.now() - startTime.value
  }, 1000)
})

onUnmounted(() => {
  stopAll()
  if (timer) clearInterval(timer)
})

async function startCameras() {
  try {
    // 模拟 - 实际项目会用 WebRTC + 客户授权
    const stream = await navigator.mediaDevices.getUserMedia({
      video: { width: 1280, height: 720 },
      audio: true
    })
    customerStream = stream
    if (customerVideo.value) customerVideo.value.srcObject = stream
    
    // 坐席摄像头 (示意: 同一摄像头显示两次, 实际是不同来源)
    if (agentVideo.value) {
      // 复制给坐席
      agentStream = stream
      agentVideo.value.srcObject = stream
    }
  } catch (e: any) {
    showToast('摄像头访问失败')
  }
}

function toggleRecord() {
  if (recording.value) {
    mediaRecorder?.pause()
    recording.value = false
  } else {
    if (customerStream) {
      mediaRecorder = new MediaRecorder(customerStream)
      mediaRecorder.start(1000)
      recording.value = true
    }
  }
}

function prev() {
  if (currentNode.value > 0) currentNode.value--
}
function next() {
  if (currentNode.value < nodes.length - 1) currentNode.value++
}
function gotoNode(i: number) {
  currentNode.value = i
}

function onEnd() {
  showDialog({ title: '结束双录', message: '确认结束本次双录?', showCancelButton: true })
    .then(() => {
      showToast('双录已结束')
      stopAll()
    })
    .catch(() => {})
}

function stopAll() {
  mediaRecorder?.stop()
  customerStream?.getTracks().forEach(t => t.stop())
  agentStream?.getTracks().forEach(t => t.stop())
}
</script>

<style lang="scss" scoped>
.workbench { height: 100vh; display: flex; flex-direction: column; background: var(--bg); }

.wb-header {
  background: linear-gradient(90deg, #1e2a47 0%, #2c3a5c 100%);
  color: white;
  padding: 16px 24px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.biz-id { font-size: 16px; font-weight: 600; font-family: monospace; color: var(--accent-light); }
.biz-meta { font-size: 12px; opacity: 0.85; margin-top: 2px; }
.timer { text-align: right; }
.t-label { font-size: 11px; opacity: 0.7; }
.t-value { font-size: 24px; font-weight: 700; font-family: monospace; color: var(--accent-light); }

.wb-content {
  flex: 1;
  display: grid;
  grid-template-columns: 1fr 320px;
  gap: 16px;
  padding: 16px;
  overflow: hidden;
}

.main-area { display: flex; flex-direction: column; gap: 12px; overflow-y: auto; }

.dual-cam {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  height: 320px;
}
.cam-box {
  position: relative;
  background: #000;
  border-radius: 8px;
  overflow: hidden;
}
.cam-label {
  position: absolute;
  top: 8px; left: 8px;
  background: rgba(238,10,36,0.85);
  color: white;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  z-index: 10;
}
.cam-video { width: 100%; height: 100%; object-fit: cover; }
.cam-overlay { position: absolute; inset: 0; pointer-events: none; }
.face-detect {
  position: absolute;
  top: 30%; left: 30%;
  width: 40%; height: 40%;
  border: 2px solid #07c160;
  border-radius: 8px;
  box-shadow: 0 0 0 1px rgba(7,193,96,0.3);
}

.node-nav { background: white; border-radius: 8px; padding: 12px; }
.node-tabs {
  display: grid;
  grid-template-columns: repeat(8, 1fr);
  gap: 4px;
}
.node-tab {
  text-align: center;
  padding: 8px 4px;
  border-radius: 6px;
  cursor: pointer;
  background: var(--bg);
  &.completed { background: rgba(7,193,96,0.1); color: var(--success); }
  &.active { background: var(--accent); color: white; }
  &:hover { background: var(--border); }
}
.nt-num { font-size: 14px; font-weight: 700; }
.nt-name { font-size: 10px; margin-top: 2px; }

.script-display { background: white; border-radius: 8px; padding: 16px; flex: 1; }
.sd-title { font-size: 13px; font-weight: 600; color: var(--text-2); margin-bottom: 8px; }
.sd-content { font-size: 14px; line-height: 1.6; color: var(--text-1); }

.side-area { display: flex; flex-direction: column; gap: 12px; overflow-y: auto; }
.side-card {
  background: white;
  border-radius: 8px;
  padding: 12px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.05);
  h4 { font-size: 13px; font-weight: 600; margin: 0 0 8px; color: var(--text-2); }
}
.progress-info { display: flex; align-items: baseline; gap: 8px; }
.pi-num { font-size: 24px; font-weight: 700; color: var(--accent); font-family: monospace; }
.pi-text { font-size: 13px; color: var(--text-2); }
.progress-bar-mini {
  margin-top: 8px;
  height: 6px;
  background: var(--bg);
  border-radius: 3px;
  overflow: hidden;
}
.pbm-fill {
  height: 100%;
  background: linear-gradient(90deg, var(--accent) 0%, var(--accent-light) 100%);
  transition: width 0.3s;
}

.scan-result { padding: 8px 0; }
.sr-status {
  font-size: 18px;
  font-weight: 700;
  &.ok { color: var(--success); }
  &.danger { color: var(--danger); }
}
.sr-detail { font-size: 12px; color: var(--text-3); margin-top: 4px; }

.check-list { list-style: none; padding: 0; margin: 0; font-size: 12px; }
.check-list li {
  display: flex; justify-content: space-between;
  padding: 4px 0;
}
.check-list li .ok { color: var(--success); }

.action-list { display: flex; flex-direction: column; gap: 8px; }
.action-btn {
  padding: 10px;
  border: 1px solid var(--border);
  background: white;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
  &:hover { background: var(--bg); }
  &.start { background: var(--accent); color: white; border-color: var(--accent); }
  &.recording { background: var(--danger); color: white; border-color: var(--danger); }
  &.danger { background: rgba(238,10,36,0.1); color: var(--danger); border-color: rgba(238,10,36,0.2); }
}
</style>
