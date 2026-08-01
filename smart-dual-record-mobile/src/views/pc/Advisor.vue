<template>
  <div class="advisor-panel">
    <div class="page-header">
      <h1>理财经理工作台</h1>
      <p>处理客户转接请求 · 在线咨询服务</p>
    </div>

    <div class="stat-grid">
      <div class="stat-card">
        <div class="stat-num">{{ stats.pending }}</div>
        <div class="stat-label">待接单</div>
      </div>
      <div class="stat-card">
        <div class="stat-num">{{ stats.active }}</div>
        <div class="stat-label">进行中</div>
      </div>
      <div class="stat-card">
        <div class="stat-num">{{ stats.today }}</div>
        <div class="stat-label">今日</div>
      </div>
      <div class="stat-card">
        <div :class="['stat-num', wsConnected ? 'on' : 'off']">
          {{ wsConnected ? '●' : '○' }}
        </div>
        <div class="stat-label">WebSocket</div>
      </div>
    </div>

    <div class="layout">
      <div class="left">
        <div class="card">
          <div class="card-header">
            <h3>📋 待接单 ({{ pendingList.length }})</h3>
          </div>
          <div v-if="!pendingList.length" class="empty-tip">
            <div class="et-icon">📭</div>
            <p>暂无待接单</p>
          </div>
          <div v-else class="pending-list">
            <div v-for="r in pendingList" :key="r.id" class="pending-card">
              <div class="pc-header">
                <div class="pc-customer">👤 {{ r.customer }}</div>
                <div :class="['reason', `reason-${r.reason}`]">{{ reasonLabel(r.reason) }}</div>
              </div>
              <div class="pc-desc">{{ r.desc }}</div>
              <div class="pc-time">{{ r.time }}</div>
              <div class="pc-actions">
                <button class="btn btn-accept" @click="onAccept(r)">✓ 接单</button>
                <button class="btn btn-decline" @click="onDecline(r)">✕ 拒绝</button>
              </div>
            </div>
          </div>
        </div>

        <div class="card">
          <div class="card-header">
            <h3>📊 活跃会话 ({{ activeList.length }})</h3>
          </div>
          <div v-if="!activeList.length" class="empty-tip">
            <div class="et-icon">💼</div>
            <p>暂无活跃会话</p>
          </div>
          <div v-else class="active-list">
            <div v-for="a in activeList" :key="a.id" :class="['active-card', selectedActive?.id === a.id && 'selected']" @click="selectedActive = a">
              <div class="ac-customer">👤 {{ a.customer }}</div>
              <div class="ac-time">{{ a.duration }} · {{ a.lastMsg }}</div>
            </div>
          </div>
        </div>
      </div>

      <div class="right">
        <div v-if="!selectedActive" class="empty-tip card">
          <div class="et-icon">💬</div>
          <p>从左侧选择会话</p>
        </div>
        <div v-else class="chat-panel card">
          <div class="chat-header">
            <div class="ch-customer">👤 {{ selectedActive.customer }}</div>
            <button class="btn btn-end" @click="onEnd">📞 挂断</button>
          </div>
          <div ref="msgListEl" class="chat-messages">
            <div v-for="m in messages" :key="m.id" :class="['msg', `msg-${m.role}`]">
              <div class="msg-avatar">{{ m.role === 'customer' ? '👤' : m.role === 'advisor' ? '💎' : '⚙️' }}</div>
              <div class="msg-bubble">{{ m.text }}</div>
            </div>
          </div>
          <div class="chat-input">
            <input v-model="inputText" class="ci-input" placeholder="输入回复..." @keyup.enter="onSend" />
            <button class="ci-send" @click="onSend">发送</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick } from 'vue'
import { showToast, showDialog } from 'vant'
import { useWebSocketStore } from '@/stores/ws'

const ws = useWebSocketStore()
const wsConnected = computed(() => ws.connected)

const stats = ref({ pending: 3, active: 1, today: 8 })

const pendingList = ref([
  { id: 'r001', customer: '张志强', reason: 'PRODUCT', desc: '想详细了解固收类理财的收益结构', time: '10:32' },
  { id: 'r002', customer: '李建国', reason: 'COMPLIANCE', desc: '对风险揭示书有疑问', time: '10:28' },
  { id: 'r003', customer: '王明华', reason: 'TECH', desc: '摄像头无法启动', time: '10:15' }
])

const activeList = ref([
  { id: 'a001', customer: '赵晓东', duration: '5 分钟', lastMsg: '收益大概多少?' }
])

const selectedActive = ref<any>(activeList.value[0])

const inputText = ref('')
const msgListEl = ref<HTMLDivElement>()

const messages = ref([
  { id: 1, role: 'customer', text: '您好, 我想咨询这个理财产品的具体收益' },
  { id: 2, role: 'advisor', text: '您好, 我是您的专属理财经理。预期年化 3.6%, 投资期限 180 天。' },
  { id: 3, role: 'system', text: '已建立服务连接 · 2026-08-01 10:30' },
  { id: 4, role: 'customer', text: '收益大概多少?' }
])

function reasonLabel(r: string) {
  return ({ TECH: '技术问题', PRODUCT: '产品咨询', COMPLIANCE: '合规咨询', OTHER: '其他' } as Record<string, string>)[r] || r
}

function onAccept(r: any) {
  pendingList.value = pendingList.value.filter(p => p.id !== r.id)
  activeList.value.push({ id: 'a' + Date.now(), customer: r.customer, duration: '0 分钟', lastMsg: '会话开始' })
  selectedActive.value = activeList.value[activeList.value.length - 1]
  messages.value = [
    { id: 1, role: 'system', text: `${r.customer} 已接入服务 · 2026-08-01 ${new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })}` }
  ]
  showToast('已接单')
}

function onDecline(r: any) {
  pendingList.value = pendingList.value.filter(p => p.id !== r.id)
  showToast('已拒绝')
}

function onEnd() {
  showDialog({ title: '结束会话', message: '确认结束当前会话?', showCancelButton: true })
    .then(() => {
      activeList.value = activeList.value.filter(a => a.id !== selectedActive.value?.id)
      selectedActive.value = activeList.value[0] || null
      showToast('会话已结束')
    })
    .catch(() => {})
}

function onSend() {
  if (!inputText.value.trim()) return
  messages.value.push({
    id: Date.now(),
    role: 'advisor',
    text: inputText.value
  })
  inputText.value = ''
  nextTick(() => {
    if (msgListEl.value) {
      msgListEl.value.scrollTop = msgListEl.value.scrollHeight
    }
  })
  // 模拟客户回复
  setTimeout(() => {
    messages.value.push({
      id: Date.now() + 1,
      role: 'customer',
      text: '收到, 谢谢!'
    })
  }, 1500)
}
</script>

<style lang="scss" scoped>
.advisor-panel { padding: 24px; }
.page-header { margin-bottom: 16px; h1 { font-size: 22px; font-weight: 600; margin: 0 0 4px; } p { font-size: 13px; color: var(--text-3); margin: 0; } }

.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}
.stat-card {
  background: white;
  border-radius: 12px;
  padding: 16px;
  text-align: center;
  box-shadow: 0 1px 4px rgba(0,0,0,0.05);
}
.stat-num {
  font-size: 28px;
  font-weight: 700;
  font-family: 'JetBrains Mono', monospace;
  color: var(--primary);
  &.on { color: var(--success); animation: pulse 2s infinite; }
  &.off { color: var(--text-3); }
}
@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}
.stat-label { font-size: 12px; color: var(--text-3); margin-top: 4px; }

.layout {
  display: grid;
  grid-template-columns: 360px 1fr;
  gap: 16px;
  height: calc(100vh - 200px);
}
.left { display: flex; flex-direction: column; gap: 12px; overflow-y: auto; }
.right { display: flex; flex-direction: column; }
.card { background: white; border-radius: 12px; padding: 16px; box-shadow: 0 1px 4px rgba(0,0,0,0.05); }
.card-header { margin-bottom: 12px; h3 { font-size: 14px; font-weight: 600; margin: 0; } }

.empty-tip { text-align: center; padding: 40px 20px; color: var(--text-3); }
.et-icon { font-size: 36px; margin-bottom: 8px; opacity: 0.4; }

.pending-list { display: flex; flex-direction: column; gap: 8px; }
.pending-card {
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 12px;
}
.pc-header { display: flex; justify-content: space-between; align-items: center; }
.pc-customer { font-size: 14px; font-weight: 600; }
.reason { font-size: 11px; padding: 2px 8px; border-radius: 4px; background: var(--bg); color: var(--text-2); }
.pc-desc { font-size: 13px; color: var(--text-2); margin: 8px 0 4px; }
.pc-time { font-size: 11px; color: var(--text-3); font-family: monospace; }
.pc-actions { display: flex; gap: 8px; margin-top: 8px; }

.btn {
  flex: 1;
  padding: 8px;
  border: 1px solid var(--border);
  background: white;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
  &.btn-accept { background: var(--success); color: white; border-color: var(--success); }
  &.btn-decline { background: var(--bg); color: var(--text-2); }
  &.btn-end { background: rgba(238,10,36,0.1); color: var(--danger); border-color: rgba(238,10,36,0.2); }
}

.active-list { display: flex; flex-direction: column; gap: 6px; }
.active-card {
  padding: 10px;
  border-radius: 6px;
  cursor: pointer;
  &:hover { background: var(--bg); }
  &.selected { background: rgba(184,134,11,0.1); border: 1px solid var(--accent); }
}
.ac-customer { font-size: 14px; font-weight: 500; }
.ac-time { font-size: 12px; color: var(--text-3); margin-top: 4px; }

.chat-panel { display: flex; flex-direction: column; height: 100%; padding: 0; }
.chat-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border);
}
.ch-customer { font-size: 15px; font-weight: 600; }

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
  background: var(--bg);
}
.msg { display: flex; gap: 8px; margin-bottom: 12px; }
.msg-customer { flex-direction: row; }
.msg-advisor { flex-direction: row-reverse; }
.msg-system { justify-content: center; }
.msg-avatar { font-size: 28px; flex-shrink: 0; }
.msg-bubble {
  max-width: 60%;
  padding: 8px 12px;
  border-radius: 12px;
  font-size: 13px;
  line-height: 1.4;
}
.msg-customer .msg-bubble { background: white; }
.msg-advisor .msg-bubble { background: var(--primary); color: white; }
.msg-system .msg-bubble {
  background: var(--bg);
  color: var(--text-3);
  font-size: 12px;
  padding: 4px 12px;
  border: 1px solid var(--border);
}

.chat-input {
  display: flex;
  padding: 12px 16px;
  border-top: 1px solid var(--border);
  gap: 8px;
}
.ci-input {
  flex: 1;
  padding: 8px 12px;
  border: 1px solid var(--border);
  border-radius: 6px;
  font-size: 14px;
  font-family: inherit;
  &:focus { outline: none; border-color: var(--accent); }
}
.ci-send {
  padding: 8px 16px;
  background: var(--primary);
  color: white;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
}
</style>
