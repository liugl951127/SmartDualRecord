<template>
  <div class="page-container">
    <div class="page-header-bar">
      <div class="page-title-group">
        <div class="page-icon">📞</div>
        <div>
          <h1 class="page-title">双录工作台</h1>
          <div class="page-subtitle">业务号: <span class="mono">{{ businessId }}</span> · {{ productInfo.name }}</div>
        </div>
      </div>
      <div class="page-actions">
        <button class="btn btn-ghost" :class="{ 'btn-danger': recording }" @click="recording ? stopRec() : startRec()">
          <span :class="['rec-dot', recording && 'live']"></span>
          {{ recording ? '⏸ 暂停录制' : '● 开始录制' }}
        </button>
        <button class="btn btn-primary" @click="onSave">
          💾 保存草稿 <span class="kbd">⌘S</span>
        </button>
      </div>
    </div>

    <div class="page-body workbench-body">
      <div class="wb-grid">
        <!-- ============ 左侧: 流程导航 + 快捷键 ============ -->
        <aside class="wb-left">
          <!-- 流程节点 -->
          <div class="card mb-12">
            <div class="card-header">
              <h3 class="card-title">📋 录制流程</h3>
              <span class="tag tag-info">{{ currentStep + 1 }} / {{ steps.length }}</span>
            </div>
            <div class="step-list">
              <div
                v-for="(s, i) in steps"
                :key="s.id"
                :class="['step', i < currentStep && 'done', i === currentStep && 'active', i > currentStep && 'pending']"
                @click="jumpTo(i)"
              >
                <div class="step-num">
                  <span v-if="i < currentStep">✓</span>
                  <span v-else>{{ i + 1 }}</span>
                </div>
                <div class="step-info">
                  <div class="step-name">{{ s.name }}</div>
                  <div class="step-desc text-sm text-muted">{{ s.desc }}</div>
                </div>
                <div v-if="i === currentStep" class="step-cursor">▶</div>
              </div>
            </div>
            <div class="step-progress">
              <div class="sp-bar">
                <div class="sp-fill" :style="{ width: progress + '%' }"></div>
              </div>
              <div class="sp-text">
                <span class="mono font-bold">{{ progress }}%</span>
                <span class="text-sm text-muted">{{ formatTime(elapsed) }} / ~{{ formatTime(estimatedTotal) }}</span>
              </div>
            </div>
          </div>

          <!-- 快捷键 -->
          <div class="card">
            <div class="card-header">
              <h3 class="card-title">⌨️ 快捷键</h3>
            </div>
            <div class="kb-list">
              <div v-for="k in shortcuts" :key="k.label" class="kb-item">
                <span class="kb-label">{{ k.label }}</span>
                <div>
                  <kbd v-for="key in k.keys" :key="key" class="kbd">{{ key }}</kbd>
                </div>
              </div>
            </div>
          </div>
        </aside>

        <!-- ============ 中间: 话术 + 互动 ============ -->
        <main class="wb-main">
          <!-- 当前节点话术 -->
          <div class="card mb-12">
            <div class="card-header">
              <h3 class="card-title">
                <span class="step-icon">{{ steps[currentStep]?.icon }}</span>
                {{ steps[currentStep]?.name }}
              </h3>
              <div class="flex gap-1">
                <button class="btn btn-sm btn-ghost" @click="autoSpeak = !autoSpeak" :class="autoSpeak && 'btn-success'">
                  {{ autoSpeak ? '🔊 自动播报 ON' : '🔇 自动播报 OFF' }}
                </button>
                <button class="btn btn-sm btn-ghost" @click="aiSuggest = !aiSuggest" :class="aiSuggest && 'btn-accent'">
                  🤖 AI 建议 {{ aiSuggest ? 'ON' : 'OFF' }}
                </button>
              </div>
            </div>
            <div class="script-area">
              <div
                v-for="(line, i) in currentScript"
                :key="i"
                :class="['script-line', `s-${line.speaker}`, line.speaking && 'speaking']"
              >
                <div class="sl-meta">
                  <span :class="['sl-avatar', `s-${line.speaker}`]">
                    {{ line.speaker === 'agent' ? '坐席' : line.speaker === 'customer' ? '客户' : 'AI' }}
                  </span>
                </div>
                <div class="sl-text">{{ line.text }}</div>
                <div v-if="line.require" :class="['sl-flag', `req-${line.require}`]">
                  {{ line.require === 'must' ? '必播' : line.require === 'mustask' ? '必问' : '禁播' }}
                </div>
              </div>
            </div>
            <div class="script-actions">
              <button class="btn btn-ghost" @click="markSpoken(currentStep)">
                ✓ 已完成
              </button>
              <button class="btn btn-primary" @click="nextStep">
                下一步 →
                <span class="kbd">→</span>
              </button>
            </div>
          </div>

          <!-- AI 建议 / 实时弹幕 -->
          <div v-if="aiSuggest" class="card mb-12">
            <div class="card-header">
              <h3 class="card-title">🤖 AI 实时建议</h3>
              <span class="tag tag-success">实时</span>
            </div>
            <div class="ai-feed">
              <transition-group name="danmu">
                <div
                  v-for="d in danmuList"
                  :key="d.id"
                  :class="['danmu', `d-${d.level}`]"
                >
                  <div class="d-icon">{{ d.icon }}</div>
                  <div class="d-body">
                    <div class="d-title">{{ d.title }}</div>
                    <div class="d-text">{{ d.text }}</div>
                  </div>
                  <div class="d-time mono text-sm">{{ d.time }}</div>
                </div>
              </transition-group>
            </div>
          </div>

          <!-- 客户对话输入 -->
          <div class="card">
            <div class="card-header">
              <h3 class="card-title">💬 客户对话 (模拟 ASR)</h3>
              <span class="tag tag-info">ASR 自动转写</span>
            </div>
            <div class="chat-input-area">
              <div class="chat-input-row">
                <input
                  v-model="chatInput"
                  class="input"
                  placeholder="输入客户原话或使用语音 (M键)..."
                  @keyup.enter="onSendChat"
                />
                <button class="btn btn-ghost" @click="toggleMic" :class="micOn && 'btn-danger'">
                  {{ micOn ? '🎙️' : '🎤' }}
                </button>
                <button class="btn btn-primary" @click="onSendChat">
                  发送
                </button>
              </div>
              <div class="chat-suggest">
                <div class="cs-label text-sm text-muted">快捷回复:</div>
                <button
                  v-for="s in quickReplies"
                  :key="s.text"
                  class="btn btn-sm btn-ghost"
                  @click="chatInput = s.text"
                >{{ s.text }}</button>
              </div>
            </div>
          </div>
        </main>

        <!-- ============ 右侧: 客户信息 + 风险 + 录像控制 ============ -->
        <aside class="wb-right">
          <!-- 客户卡片 -->
          <div class="card mb-12 customer-card">
            <div class="cc-avatar-lg" :style="{ background: avatarColor(currentCustomer.riskLevel) }">
              {{ currentCustomer.name.charAt(0) }}
            </div>
            <div class="cc-name-lg">
              {{ currentCustomer.name }}
              <span v-if="currentCustomer.vip" class="tag tag-accent">⭐ VIP</span>
            </div>
            <div class="cc-id mono text-sm">{{ currentCustomer.idHash }}</div>
            <div class="cc-tags">
              <span :class="['tag', `tag-${riskColor(currentCustomer.riskLevel)}`]">C{{ currentCustomer.riskLevel }} · {{ currentCustomer.riskName }}</span>
              <span v-for="t in currentCustomer.tags" :key="t" class="tag tag-default">{{ t }}</span>
            </div>
            <div class="cc-assets">
              <div class="ca-item">
                <div class="ca-label text-sm text-muted">总资产</div>
                <div class="ca-value mono font-bold">¥{{ currentCustomer.asset }}</div>
              </div>
              <div class="ca-item">
                <div class="ca-label text-sm text-muted">购买金额</div>
                <div class="ca-value mono font-bold text-accent">¥{{ purchaseAmount }}</div>
              </div>
            </div>
          </div>

          <!-- 风险匹配 -->
          <div class="card mb-12">
            <div class="card-header">
              <h3 class="card-title">⚖️ 风险匹配</h3>
            </div>
            <div class="match-display">
              <div class="match-row">
                <div class="mr-label text-sm text-muted">客户</div>
                <div :class="['match-pill', `m-${riskColor(currentCustomer.riskLevel)}`]">
                  C{{ currentCustomer.riskLevel }}
                </div>
              </div>
              <div class="match-arrow">⚡</div>
              <div class="match-row">
                <div class="mr-label text-sm text-muted">产品</div>
                <div :class="['match-pill', `m-${riskColor(productInfo.risk)}`]">
                  R{{ productInfo.risk }}
                </div>
              </div>
              <div :class="['match-result', matchResult.cls]">
                <div class="mr-icon">{{ matchResult.icon }}</div>
                <div class="mr-text">
                  <div class="mr-title">{{ matchResult.title }}</div>
                  <div class="mr-desc text-sm">{{ matchResult.desc }}</div>
                </div>
              </div>
            </div>
          </div>

          <!-- 禁播词监测 -->
          <div class="card mb-12">
            <div class="card-header">
              <h3 class="card-title">🚫 禁播词监测</h3>
              <span :class="['tag', forbiddenHits.length ? 'tag-danger' : 'tag-success']">
                {{ forbiddenHits.length }} 触发
              </span>
            </div>
            <div class="forbidden-list">
              <div
                v-for="(h, i) in forbiddenHits"
                :key="i"
                class="forbidden-item"
              >
                <span class="fi-icon">🚨</span>
                <span class="fi-text">{{ h }}</span>
                <span class="fi-time text-sm text-muted">{{ h.time }}</span>
              </div>
              <div v-if="!forbiddenHits.length" class="empty">
                <div class="empty-icon">✅</div>
                <p class="empty-text">目前未触发禁播词</p>
              </div>
            </div>
          </div>

          <!-- 录制控制 -->
          <div class="card">
            <div class="card-header">
              <h3 class="card-title">⏺️ 录像控制</h3>
            </div>
            <div class="rec-stats">
              <div class="rs-item">
                <div class="rs-label">录制时长</div>
                <div class="rs-value mono font-bold">{{ formatTime(elapsed) }}</div>
              </div>
              <div class="rs-item">
                <div class="rs-label">分片数</div>
                <div class="rs-value mono font-bold">{{ chunks }}</div>
              </div>
              <div class="rs-item">
                <div class="rs-label">大小</div>
                <div class="rs-value mono font-bold">{{ sizeMB }} MB</div>
              </div>
            </div>
            <div class="rec-actions">
              <button :class="['btn btn-block', recording ? 'btn-danger' : 'btn-success']" @click="recording ? stopRec() : startRec()">
                <span :class="['rec-dot', recording && 'live']"></span>
                {{ recording ? '⏸ 暂停' : '● 开始' }}
              </button>
              <div class="grid grid-2 gap-1">
                <button class="btn btn-ghost btn-sm" @click="markSnapshot">📷 截图</button>
                <button class="btn btn-ghost btn-sm" @click="markNote">📝 备注</button>
              </div>
            </div>
          </div>
        </aside>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'

const businessId = 'BNK20260801-900003'
const recording = ref(false)
const elapsed = ref(0)
const chunks = ref(0)
const sizeMB = ref('0.0')
const currentStep = ref(0)
const autoSpeak = ref(true)
const aiSuggest = ref(true)
const chatInput = ref('')
const micOn = ref(false)
let timer: any = null
let chunkTimer: any = null

const productInfo = { name: '稳赢 3 号理财', code: 'BNK-FIN-2026Q3-001', risk: 2 }
const purchaseAmount = '50,000'

const currentCustomer = ref({
  name: '张志强', idHash: 'cust-hash-001', riskLevel: 1, riskName: '保守型',
  asset: '580,000', vip: true, tags: ['VIP', '高净值']
})

const steps = [
  { id: 's1', name: '开场白', desc: '自我介绍 + 业务说明', icon: '👋' },
  { id: 's2', name: '客户身份核验', desc: '身份证 + 人脸识别', icon: '🆔' },
  { id: 's3', name: '风险揭示', desc: '必播 6 条', icon: '⚠️' },
  { id: 's4', name: '产品介绍', desc: '必播 5 条 + 必问 3 条', icon: '💼' },
  { id: 's5', name: '客户确认', desc: '关键问题确认', icon: '❓' },
  { id: 's6', name: '协议条款', desc: '必播 4 条', icon: '📜' },
  { id: 's7', name: '电子签字', desc: '客户 + 坐席', icon: '✍️' },
  { id: 's8', name: '结束语', desc: '结束录制', icon: '👋' }
]

const progress = computed(() => Math.round((currentStep.value / steps.length) * 100))
const estimatedTotal = computed(() => steps.length * 90) // 90s per step

// 当前节点话术
const currentScript = computed(() => {
  const m: any = {
    0: [
      { speaker: 'agent', text: '您好, 我是智能双录坐席小李, 请问是张志强先生吗?', speaking: true },
      { speaker: 'agent', text: '今天为您介绍的是稳赢 3 号理财产品, 整个过程会录音录像, 请确认知晓。', require: 'must' }
    ],
    1: [
      { speaker: 'agent', text: '为了确保是您本人, 请出示身份证, 并正对摄像头。', require: 'must' },
      { speaker: 'customer', text: '好的, 这是我的身份证。' },
      { speaker: 'agent', text: '请问您的出生年月是?', require: 'mustask' }
    ],
    2: [
      { speaker: 'agent', text: '理财有风险, 投资需谨慎。', require: 'must' },
      { speaker: 'agent', text: '本产品不保本, 极端情况下可能损失全部本金。', require: 'must' },
      { speaker: 'agent', text: '过往业绩不代表未来表现。', require: 'must' }
    ],
    3: [
      { speaker: 'agent', text: '稳赢 3 号为 R2 稳健型产品, 预期年化 3.6%。', require: 'must' },
      { speaker: 'agent', text: '产品期限 6 个月, 5 万元起购。', require: 'must' },
      { speaker: 'agent', text: '请问您是否了解产品风险等级?', require: 'mustask' }
    ],
    4: [
      { speaker: 'agent', text: '请问您购买本产品的资金是闲置资金吗?', require: 'mustask' },
      { speaker: 'agent', text: '请问您的预期持有期是多长?', require: 'mustask' }
    ],
    5: [
      { speaker: 'agent', text: '产品说明书已经推送给您, 请仔细阅读。', require: 'must' },
      { speaker: 'agent', text: '如对条款有疑问, 我将为您详细解答。' }
    ],
    6: [
      { speaker: 'agent', text: '请在签字板上签名确认。' },
      { speaker: 'customer', text: '已签字。' }
    ],
    7: [
      { speaker: 'agent', text: '本次双录结束, 录像将保存 5 年供合规检查, 感谢您的信任。' }
    ]
  }
  return m[currentStep.value] || []
})

// AI 弹幕
const danmuList = ref<any[]>([
  { id: 1, icon: '🤖', title: 'AI 提示', text: '客户 C1 + 产品 R2, 风险匹配 ✓', time: '14:32:01', level: 'success' },
  { id: 2, icon: '👀', title: '注意', text: '客户语速偏快, 可适当放慢节奏', time: '14:32:15', level: 'info' },
  { id: 3, icon: '🚫', title: '禁播词检测', text: '客户说 "稳赚不赔", 请立即纠正', time: '14:33:02', level: 'danger' }
])

const forbiddenHits = ref<{ text: string; time: string }[]>([
  { text: '"稳赚不赔" - 禁播词', time: '14:33' }
])

// 快捷回复
const quickReplies = [
  { text: '好的, 我明白了' },
  { text: '请问有什么风险?' },
  { text: '我再考虑一下' },
  { text: '可以, 继续' }
]

// 风险匹配
const matchResult = computed(() => {
  const d = Math.abs(currentCustomer.value.riskLevel - productInfo.risk)
  if (d <= 1) return { cls: 'match-ok', icon: '✓', title: '风险匹配', desc: '客户与产品风险等级相符' }
  if (d === 2) return { cls: 'match-warn', icon: '⚠️', title: '需特别说明', desc: '请详细解释产品风险' }
  return { cls: 'match-danger', icon: '🚫', title: '严重错配', desc: '客户与产品风险等级严重不符, 建议终止' }
})

// 快捷键
const shortcuts = [
  { label: '下一节点', keys: ['→'] },
  { label: '上一节点', keys: ['←'] },
  { label: '开始/暂停', keys: ['Space'] },
  { label: 'AI 建议', keys: ['A'] },
  { label: '自动播报', keys: ['S'] },
  { label: '语音输入', keys: ['M'] },
  { label: '保存草稿', keys: ['⌘', 'S'] }
]

function onKeydown(e: KeyboardEvent) {
  const target = e.target as HTMLElement
  if (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA') return
  if (e.key === 'ArrowRight') nextStep()
  else if (e.key === 'ArrowLeft' && currentStep.value > 0) currentStep.value--
  else if (e.key === ' ') { e.preventDefault(); recording.value ? stopRec() : startRec() }
  else if (e.key === 'a' || e.key === 'A') aiSuggest.value = !aiSuggest.value
  else if (e.key === 's' || e.key === 'S') autoSpeak.value = !autoSpeak.value
  else if (e.key === 'm' || e.key === 'M') toggleMic()
  else if ((e.metaKey || e.ctrlKey) && e.key === 's') { e.preventDefault(); onSave() }
}

function nextStep() {
  if (currentStep.value < steps.length - 1) {
    currentStep.value++
    addDanmu({ icon: '✓', title: '节点完成', text: steps[currentStep.value - 1].name + ' 已完成', time: now(), level: 'success' })
  } else {
    addDanmu({ icon: '🎉', title: '流程完成', text: '所有节点已结束, 可提交审核', time: now(), level: 'success' })
  }
}
function jumpTo(i: number) {
  if (i <= currentStep.value) currentStep.value = i
}
function markSpoken(s: number) {
  addDanmu({ icon: '✓', title: '已确认', text: '话术已读完', time: now(), level: 'success' })
}
function startRec() {
  recording.value = true
  timer = setInterval(() => { elapsed.value++ }, 1000)
  chunkTimer = setInterval(() => {
    chunks.value++
    sizeMB.value = (chunks.value * 2.5).toFixed(1)
  }, 8000)
  addDanmu({ icon: '●', title: '录制开始', text: '音视频已启动', time: now(), level: 'info' })
}
function stopRec() {
  recording.value = false
  if (timer) clearInterval(timer)
  if (chunkTimer) clearInterval(chunkTimer)
  addDanmu({ icon: '⏸', title: '录制暂停', text: '已保存 ' + chunks.value + ' 个分片', time: now(), level: 'warning' })
}
function toggleMic() {
  micOn.value = !micOn.value
  addDanmu({ icon: micOn.value ? '🎙️' : '🎤', title: micOn.value ? '麦克风开' : '麦克风关', text: micOn.value ? 'ASR 开始转写' : '停止转写', time: now(), level: 'info' })
}
function markSnapshot() { addDanmu({ icon: '📷', title: '已截图', text: '截图已保存到录像日志', time: now(), level: 'info' }) }
function markNote() { addDanmu({ icon: '📝', title: '已添加备注', text: '备注将加入 AI 终检报告', time: now(), level: 'info' }) }

function onSendChat() {
  if (!chatInput.value) return
  // 检查禁播词
  if (chatInput.value.includes('稳赚') || chatInput.value.includes('保证')) {
    forbiddenHits.value.unshift({ text: '"' + chatInput.value.substring(0, 10) + '..." - 禁播词', time: now().substring(0, 5) })
    addDanmu({ icon: '🚨', title: '禁播词告警', text: '客户: "' + chatInput.value + '"', time: now(), level: 'danger' })
  } else {
    addDanmu({ icon: '💬', title: '客户发言', text: chatInput.value, time: now(), level: 'info' })
  }
  chatInput.value = ''
}

function onSave() {
  addDanmu({ icon: '💾', title: '草稿已保存', text: '所有进度已保存', time: now(), level: 'success' })
}

function addDanmu(d: any) {
  danmuList.value.unshift({ id: Date.now() + Math.random(), ...d })
  if (danmuList.value.length > 20) danmuList.value.pop()
}

function now() {
  return new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}
function formatTime(s: number) {
  const m = Math.floor(s / 60)
  const ss = s % 60
  return `${m.toString().padStart(2, '0')}:${ss.toString().padStart(2, '0')}`
}
function riskColor(level: number) {
  const m: any = { 1: 'success', 2: 'primary', 3: 'warning', 4: 'warning', 5: 'danger' }
  return m[level] || 'default'
}
function avatarColor(level: number) {
  const colors: any = { 1: '#d1fae5', 2: '#dbeafe', 3: '#fef3c7', 4: '#fed7aa', 5: '#fee2e2' }
  return colors[level] || '#f0f2f7'
}

onMounted(() => {
  window.addEventListener('keydown', onKeydown)
  // 模拟一些 AI 弹幕
  setTimeout(() => addDanmu({ icon: '💡', title: '话术建议', text: '建议加一句产品亮点介绍', time: now(), level: 'info' }), 5000)
})
onUnmounted(() => {
  window.removeEventListener('keydown', onKeydown)
  if (timer) clearInterval(timer)
  if (chunkTimer) clearInterval(chunkTimer)
})
</script>

<style lang="scss" scoped>
@import '@/styles/agent-theme.scss';

.mb-12 { margin-bottom: 12px; }

.workbench-body {
  padding: 16px 20px;
}

.wb-grid {
  display: grid;
  grid-template-columns: 280px 1fr 320px;
  gap: 12px;
  height: 100%;
}
@media (max-width: 1400px) {
  .wb-grid { grid-template-columns: 240px 1fr 280px; }
}
@media (max-width: 1200px) {
  .wb-grid { grid-template-columns: 1fr; }
}

.wb-left, .wb-main, .wb-right {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

// ============ 流程节点 ============
.step-list { padding: 4px 0; }
.step {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
  cursor: pointer;
  position: relative;
  transition: all 0.15s;
  &.active {
    background: linear-gradient(90deg, rgba(184, 134, 11, 0.08) 0%, transparent 100%);
    border-left: 3px solid var(--accent);
    padding-left: 13px;
  }
  &.done .step-num {
    background: var(--success);
    color: white;
  }
  &.pending { opacity: 0.5; }
  &:hover { background: var(--bg); }
}
.step-num {
  width: 24px; height: 24px;
  border-radius: 50%;
  background: var(--bg);
  display: flex; align-items: center; justify-content: center;
  font-size: 12px;
  font-weight: 700;
  color: var(--text-3);
  flex-shrink: 0;
  font-family: 'JetBrains Mono', monospace;
}
.step.active .step-num { background: var(--accent); color: white; }
.step-info { flex: 1; min-width: 0; }
.step-name { font-size: 13px; font-weight: 500; color: var(--text-1); }
.step-cursor {
  color: var(--accent);
  font-size: 10px;
  animation: cursor-blink 1.5s infinite;
}
@keyframes cursor-blink {
  0%, 50%, 100% { opacity: 1; }
  25%, 75% { opacity: 0.3; }
}

.step-progress {
  padding: 12px 16px;
  border-top: 1px solid var(--border-light);
  background: var(--bg);
  border-radius: 0 0 var(--radius-lg) var(--radius-lg);
}
.sp-bar {
  height: 6px;
  background: var(--border);
  border-radius: 3px;
  overflow: hidden;
  margin-bottom: 6px;
}
.sp-fill {
  height: 100%;
  background: linear-gradient(90deg, var(--accent) 0%, var(--accent-light) 100%);
  border-radius: 3px;
  transition: width 0.4s;
}
.sp-text { display: flex; justify-content: space-between; }

// ============ 话术区 ============
.script-area {
  padding: 16px 20px;
  max-height: 360px;
  overflow-y: auto;
  background: var(--bg);
}
.script-line {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  margin-bottom: 12px;
  padding: 8px 12px;
  border-radius: var(--radius);
  position: relative;
  &.speaking {
    background: white;
    box-shadow: var(--shadow);
    border-left: 3px solid var(--accent);
  }
  &.s-agent { background: rgba(30, 42, 71, 0.04); }
  &.s-customer { background: rgba(184, 134, 11, 0.04); }
}
.sl-meta { flex-shrink: 0; }
.sl-avatar {
  display: inline-block;
  padding: 2px 8px;
  font-size: 11px;
  font-weight: 600;
  border-radius: var(--radius-sm);
  &.s-agent { background: var(--primary); color: white; }
  &.s-customer { background: var(--accent); color: white; }
  &.s-ai { background: var(--info); color: white; }
}
.sl-text { flex: 1; font-size: 13px; line-height: 1.6; color: var(--text-1); }
.sl-flag {
  font-size: 10px;
  padding: 2px 6px;
  border-radius: var(--radius-sm);
  font-weight: 600;
  &.req-must { background: var(--danger); color: white; }
  &.req-mustask { background: var(--warning); color: white; }
  &.req-forbid { background: var(--text-3); color: white; }
}
.script-actions {
  display: flex;
  justify-content: space-between;
  padding: 12px 20px;
  border-top: 1px solid var(--border-light);
  background: white;
  border-radius: 0 0 var(--radius-lg) var(--radius-lg);
}

// ============ AI 弹幕 ============
.ai-feed {
  padding: 8px;
  max-height: 240px;
  overflow-y: auto;
}
.danmu {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 8px 12px;
  border-radius: var(--radius);
  margin-bottom: 4px;
  &.d-success { background: var(--success-light); }
  &.d-info { background: var(--info-light); }
  &.d-warning { background: var(--warning-light); }
  &.d-danger { background: var(--danger-light); animation: shake 0.3s; }
}
@keyframes shake {
  0%, 100% { transform: translateX(0); }
  25% { transform: translateX(-4px); }
  75% { transform: translateX(4px); }
}
.d-icon { font-size: 16px; flex-shrink: 0; }
.d-body { flex: 1; min-width: 0; }
.d-title { font-size: 12px; font-weight: 600; color: var(--text-1); }
.d-text { font-size: 12px; color: var(--text-2); margin-top: 2px; }
.d-time { color: var(--text-3); white-space: nowrap; }

// ============ 对话输入 ============
.chat-input-area { padding: 12px 16px; }
.chat-input-row { display: flex; gap: 8px; align-items: center; margin-bottom: 8px; }
.chat-suggest { display: flex; gap: 6px; align-items: center; flex-wrap: wrap; }
.cs-label { margin-right: 4px; }

// ============ 客户卡 ============
.customer-card {
  padding: 20px;
  text-align: center;
  position: relative;
  overflow: hidden;
  &::before {
    content: '';
    position: absolute;
    top: 0; left: 0; right: 0;
    height: 60px;
    background: linear-gradient(135deg, var(--primary) 0%, var(--primary-light) 100%);
    z-index: 0;
  }
}
.cc-avatar-lg {
  position: relative;
  z-index: 1;
  width: 64px; height: 64px;
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-size: 28px;
  font-weight: 700;
  color: var(--text-1);
  margin: 0 auto 12px;
  border: 3px solid white;
  box-shadow: var(--shadow);
}
.cc-name-lg {
  position: relative;
  z-index: 1;
  font-size: 18px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin-bottom: 4px;
}
.cc-id {
  position: relative;
  z-index: 1;
  color: var(--text-3);
  margin-bottom: 12px;
}
.cc-tags {
  position: relative;
  z-index: 1;
  display: flex;
  gap: 4px;
  justify-content: center;
  flex-wrap: wrap;
  margin-bottom: 16px;
}
.cc-assets {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  padding-top: 12px;
  border-top: 1px solid var(--border-light);
}
.ca-item { text-align: center; }
.ca-value { font-size: 16px; }

// ============ 风险匹配 ============
.match-display {
  padding: 16px 20px;
  text-align: center;
}
.match-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  margin-bottom: 8px;
}
.mr-label { min-width: 32px; text-align: right; }
.match-pill {
  padding: 4px 12px;
  font-size: 14px;
  font-weight: 700;
  border-radius: var(--radius-full);
  font-family: 'JetBrains Mono', monospace;
  &.m-success { background: var(--success-light); color: var(--success); }
  &.m-primary { background: var(--info-light); color: var(--info); }
  &.m-warning { background: var(--warning-light); color: var(--warning); }
  &.m-danger { background: var(--danger-light); color: var(--danger); }
}
.match-arrow { font-size: 20px; color: var(--accent); }
.match-result {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: var(--radius);
  margin-top: 12px;
  text-align: left;
  &.match-ok { background: var(--success-light); }
  &.match-warn { background: var(--warning-light); }
  &.match-danger { background: var(--danger-light); }
}
.mr-icon { font-size: 20px; }
.mr-text { flex: 1; }
.mr-title { font-size: 13px; font-weight: 600; color: var(--text-1); }
.mr-desc { color: var(--text-2); }

// ============ 禁播词 ============
.forbidden-list { padding: 8px 0; max-height: 180px; overflow-y: auto; }
.forbidden-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 16px;
  font-size: 12px;
}
.fi-icon { color: var(--danger); }
.fi-text { flex: 1; color: var(--text-1); font-weight: 500; }
.fi-time { color: var(--text-3); }

// ============ 录制控制 ============
.rec-stats {
  display: grid;
  grid-template-columns: 1fr 1fr 1fr;
  gap: 4px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border-light);
}
.rs-item { text-align: center; }
.rs-label { font-size: 10px; color: var(--text-3); }
.rs-value { font-size: 16px; }
.rec-actions { padding: 12px 16px; display: flex; flex-direction: column; gap: 8px; }
.rec-dot {
  display: inline-block;
  width: 8px; height: 8px;
  background: var(--text-3);
  border-radius: 50%;
  margin-right: 4px;
  &.live {
    background: var(--danger);
    animation: blink 1s infinite;
  }
}
@keyframes blink {
  0%, 50%, 100% { opacity: 1; }
  25%, 75% { opacity: 0.3; }
}

// 过渡
.danmu-enter-active, .danmu-leave-active { transition: all 0.3s; }
.danmu-enter-from { opacity: 0; transform: translateX(20px); }
.danmu-leave-to { opacity: 0; }
</style>
