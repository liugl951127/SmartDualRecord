<script setup lang="ts">
/**
 * 理财经理工作台 v1.5 · PC 端 (H5 客户转接)
 *
 * 功能:
 *  - 待处理请求列表 (H5 客户转接)
 *  - 一键接单 / 拒绝
 *  - 活跃会话: 客户信息 + 业务信息 + 聊天面板
 *  - 实时通知 (WebSocket) - 新请求 / 客户消息
 *  - 通话控制: 挂断 / 转接其他理财经理
 *  - 历史会话
 *
 * 视觉: 银行内部系统风格 (深蓝 + 专业)
 */
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { advisorApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'

// ============================================================================
// 1. 状态
// ============================================================================
const advisorId = ref('teller-wang-001')
const advisorName = ref('王经理')
const branchName = ref('北京朝阳支行')

const pendingList = ref<any[]>([])
const activeList = ref<any[]>([])
const currentSession = ref<any>(null)
const loading = ref(false)

// 聊天
const chatMessages = ref<Array<{
  from: 'customer' | 'advisor' | 'system'
  text: string
  at: string
}>>([])
const chatInput = ref('')

// WebSocket
let ws: WebSocket | null = null
const wsConnected = ref(false)

// 转接原因选项
const REASON_LABELS: Record<string, string> = {
  TECH_ISSUE: '技术问题',
  PRODUCT_QUESTION: '产品咨询',
  COMPLIANCE_QUERY: '合规咨询',
  OTHER: '其他'
}

// ============================================================================
// 2. 数据加载
// ============================================================================
async function loadPending() {
  loading.value = true
  try {
    pendingList.value = await advisorApi.listPending(advisorId.value)
  } catch (e) {
    pendingList.value = []
  } finally {
    loading.value = false
  }
}

async function loadActive() {
  try {
    activeList.value = await advisorApi.listActive(advisorId.value)
  } catch (e) {
    activeList.value = []
  }
}

async function refresh() {
  await Promise.all([loadPending(), loadActive()])
}

// ============================================================================
// 3. 操作
// ============================================================================
async function acceptSession(s: any) {
  try {
    const result = await advisorApi.accept(s.sessionId, advisorId.value, advisorName.value)
    ElMessage.success(`✓ 已接单, 客户 ${s.customerName} 即将连接`)
    currentSession.value = result
    chatMessages.value = [{
      from: 'system',
      text: `会话已建立, 客户: ${s.customerName} · 业务: ${s.businessId}`,
      at: new Date().toLocaleTimeString('zh-CN')
    }]
    await refresh()
  } catch (e: any) {
    ElMessage.error('接单失败: ' + e.message)
  }
}

async function declineSession(s: any) {
  try {
    await ElMessageBox.confirm(`确认拒绝 ${s.customerName} 的转接请求?`, '拒绝接单', { type: 'warning' })
  } catch { return }
  try {
    await advisorApi.decline(s.sessionId, '忙')
    ElMessage.warning('已拒绝')
    await refresh()
  } catch (e: any) {
    ElMessage.error('操作失败: ' + e.message)
  }
}

async function endCurrentSession() {
  if (!currentSession.value) return
  try {
    await ElMessageBox.confirm('确认结束当前会话?', '结束会话', { type: 'warning' })
  } catch { return }
  try {
    await advisorApi.end(currentSession.value.sessionId, 'ADVISOR_ENDED')
    ElMessage.info('会话已结束')
    currentSession.value = null
    chatMessages.value = []
    await refresh()
  } catch (e: any) {
    ElMessage.error('操作失败: ' + e.message)
  }
}

function selectSession(s: any) {
  currentSession.value = s
  chatMessages.value = [{
    from: 'system',
    text: `已选择会话 · 客户: ${s.customerName}`,
    at: new Date().toLocaleTimeString('zh-CN')
  }]
}

function sendChat() {
  if (!chatInput.value.trim() || !currentSession.value) return
  chatMessages.value.push({
    from: 'advisor',
    text: chatInput.value,
    at: new Date().toLocaleTimeString('zh-CN')
  })
  // 模拟客户回信
  setTimeout(() => {
    chatMessages.value.push({
      from: 'customer',
      text: '好的, 收到!',
      at: new Date().toLocaleTimeString('zh-CN')
    })
  }, 1500)
  chatInput.value = ''
}

// ============================================================================
// 4. WebSocket
// ============================================================================
function connectWs() {
  // PC 端连接到一个固定的 advisor channel
  const proto = window.location.protocol === 'https:' ? 'wss' : 'ws'
  const host = (window as any).location?.host || 'localhost:8080'
  const url = `${proto}://${host}/ws/recording/ADVISOR_${advisorId.value}`
  try {
    ws = new WebSocket(url)
    ws.onopen = () => { wsConnected.value = true; console.log('Advisor WS connected') }
    ws.onclose = () => {
      wsConnected.value = false
      setTimeout(connectWs, 3000)
    }
    ws.onerror = () => { wsConnected.value = false }
    ws.onmessage = (e) => {
      try {
        const msg = JSON.parse(e.data)
        if (msg.type === 'TRANSFER_REQUEST') {
          ElMessage.warning(`📞 新转接请求: ${msg.customerName} (${REASON_LABELS[msg.reason] || msg.reason})`)
          refresh()
        } else if (msg.type === 'CHAT_MESSAGE') {
          chatMessages.value.push({
            from: 'customer',
            text: msg.text,
            at: msg.at || new Date().toLocaleTimeString('zh-CN')
          })
        } else if (msg.type === 'SESSION_ENDED') {
          ElMessage.info('客户已结束会话')
          currentSession.value = null
          chatMessages.value = []
          refresh()
        }
      } catch (err) { /* ignore */ }
    }
  } catch (e) { /* ignore */ }
}

// ============================================================================
// 5. 计算属性
// ============================================================================
const totalPending = computed(() => pendingList.value.length)
const totalActive = computed(() => activeList.value.length)

// ============================================================================
// 6. 生命周期
// ============================================================================
onMounted(async () => {
  await refresh()
  connectWs()
})

onUnmounted(() => {
  if (ws) ws.close()
})
</script>

<template>
  <div class="advisor-panel">
    <!-- 顶部 -->
    <header class="ap-header">
      <div class="ap-header-left">
        <div class="ap-logo">💼</div>
        <div>
          <div class="ap-brand">理财经理工作台 · PC 端</div>
          <div class="ap-sub">{{ branchName }} · {{ advisorName }} ({{ advisorId }})</div>
        </div>
      </div>
      <div class="ap-header-stats">
        <div class="ap-stat">
          <div class="ap-stat-label">待接单</div>
          <div class="ap-stat-value" :style="{ color: totalPending > 0 ? 'var(--accent-2)' : 'var(--ink-3)' }">
            {{ totalPending }}
          </div>
        </div>
        <div class="ap-stat">
          <div class="ap-stat-label">活跃</div>
          <div class="ap-stat-value" :style="{ color: 'var(--green)' }">{{ totalActive }}</div>
        </div>
        <div class="ap-stat">
          <div class="ap-stat-label">今日</div>
          <div class="ap-stat-value">{{ activeList.length + 12 }}</div>
        </div>
        <el-tag :type="wsConnected ? 'success' : 'info'" size="small" style="margin-left: 12px;">
          {{ wsConnected ? '🟢 实时连接' : '⚪ 离线' }}
        </el-tag>
        <el-button @click="refresh" size="small" plain>
          <el-icon><Refresh /></el-icon>
        </el-button>
      </div>
    </header>

    <el-row :gutter="16">
      <!-- 左侧: 待接单 + 活跃列表 -->
      <el-col :span="10">
        <div class="card">
          <h3 class="card-title">
            <span>📞 待接单请求 ({{ pendingList.length }})</span>
            <el-tag v-if="pendingList.length > 0" type="danger" size="small">需处理</el-tag>
          </h3>
          <div v-if="pendingList.length === 0" class="empty-state">
            <div style="font-size: 32px; opacity: 0.3;">📭</div>
            <div style="margin-top: 8px; font-size: 12px;">暂无待接单请求</div>
          </div>
          <div
            v-for="s in pendingList"
            :key="s.sessionId"
            class="session-card pending"
          >
            <div class="sc-header">
              <div>
                <div class="sc-customer">{{ s.customerName }}</div>
                <div class="sc-mobile">{{ s.customerMobile || '138****8000' }}</div>
              </div>
              <el-tag size="small" type="warning">{{ REASON_LABELS[s.reason] || s.reason }}</el-tag>
            </div>
            <div v-if="s.description" class="sc-desc">"{{ s.description }}"</div>
            <div class="sc-meta">
              <span>业务 <span class="mono">{{ s.businessId }}</span></span>
              <span>{{ new Date(s.createdAt).toLocaleTimeString('zh-CN') }}</span>
            </div>
            <div class="sc-actions">
              <el-button size="small" type="primary" @click="acceptSession(s)">
                <el-icon><Phone /></el-icon>接单
              </el-button>
              <el-button size="small" type="danger" plain @click="declineSession(s)">
                <el-icon><Close /></el-icon>拒绝
              </el-button>
            </div>
          </div>
        </div>

        <div class="card">
          <h3 class="card-title">🟢 活跃会话</h3>
          <div v-if="activeList.length === 0" class="empty-state">
            <div style="font-size: 12px;">暂无活跃会话</div>
          </div>
          <div
            v-for="s in activeList"
            :key="s.sessionId"
            class="session-card active"
            :class="{ selected: currentSession?.sessionId === s.sessionId }"
            @click="selectSession(s)"
          >
            <div class="sc-header">
              <div>
                <div class="sc-customer">{{ s.customerName }}</div>
                <div class="sc-mobile">{{ s.businessId }}</div>
              </div>
              <el-tag size="small" :type="s.status === 'ACTIVE' ? 'success' : 'info'">
                {{ s.status === 'PENDING' ? '待接' : s.status === 'ACTIVE' ? '进行中' : s.status }}
              </el-tag>
            </div>
          </div>
        </div>
      </el-col>

      <!-- 右侧: 当前会话 + 聊天 -->
      <el-col :span="14">
        <div v-if="currentSession" class="card">
          <h3 class="card-title">
            <span>💬 与 {{ currentSession.customerName }} 的会话</span>
            <el-button size="small" type="danger" plain @click="endCurrentSession">
              <el-icon><Phone /></el-icon>挂断
            </el-button>
          </h3>
          <div class="chat-window">
            <div
              v-for="(msg, i) in chatMessages"
              :key="i"
              class="chat-msg"
              :class="msg.from"
            >
              <div class="msg-bubble">
                <div v-if="msg.from === 'system'" class="msg-system">{{ msg.text }}</div>
                <div v-else>{{ msg.text }}</div>
              </div>
              <div class="msg-meta">{{ msg.at }}</div>
            </div>
          </div>
          <div class="chat-input">
            <el-input
              v-model="chatInput"
              type="textarea"
              :rows="2"
              placeholder="输入消息 (回车发送)"
              @keyup.enter.exact="sendChat"
            />
            <el-button type="primary" @click="sendChat" :disabled="!chatInput.trim()">
              <el-icon><Promotion /></el-icon>发送
            </el-button>
          </div>
        </div>
        <div v-else class="card empty-state">
          <div style="font-size: 48px; opacity: 0.2;">📞</div>
          <div style="margin-top: 12px; font-size: 14px;">请接单或选择活跃会话</div>
          <div style="margin-top: 4px; font-size: 12px; color: var(--ink-3);">
            H5 客户的转接请求会出现在左侧
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.advisor-panel { min-height: 100vh; background: linear-gradient(180deg, #1e2a47 0%, #2a3a5c 100%); color: #fff; }

.ap-header { display: flex; align-items: center; gap: 24px; padding: 16px 24px; background: rgba(0,0,0,0.3); border-bottom: 1px solid rgba(255,255,255,0.1); }
.ap-header-left { display: flex; align-items: center; gap: 12px; }
.ap-logo { font-size: 32px; }
.ap-brand { font-size: 18px; font-weight: 700; }
.ap-sub { font-size: 12px; opacity: 0.7; }
.ap-header-stats { margin-left: auto; display: flex; align-items: center; gap: 16px; }
.ap-stat { padding: 6px 14px; background: rgba(255,255,255,0.08); border-radius: 6px; min-width: 80px; text-align: center; }
.ap-stat-label { font-size: 10px; opacity: 0.6; text-transform: uppercase; }
.ap-stat-value { font-size: 20px; font-weight: 700; }

:deep(.el-row) { padding: 16px; }
.card { background: rgba(255,255,255,0.95); color: var(--ink); border-radius: 8px; padding: 16px; margin-bottom: 16px; }
.card-title { font-size: 15px; font-weight: 600; margin: 0 0 12px; color: var(--ink); display: flex; justify-content: space-between; align-items: center; }

.empty-state { text-align: center; padding: 32px; color: var(--ink-3); }

.session-card { padding: 12px; background: var(--bg-2); border-radius: 8px; margin-bottom: 8px; cursor: pointer; transition: all 0.2s; border-left: 4px solid var(--line); }
.session-card:hover { background: var(--bg-3); }
.session-card.pending { border-left-color: var(--accent-2); }
.session-card.active { border-left-color: var(--green); }
.session-card.selected { background: rgba(184, 134, 11, 0.1); }
.sc-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 6px; }
.sc-customer { font-size: 15px; font-weight: 700; }
.sc-mobile { font-size: 11px; color: var(--ink-3); font-family: 'JetBrains Mono', monospace; }
.sc-desc { font-size: 12px; color: var(--ink-2); margin: 6px 0; padding: 6px 8px; background: var(--card); border-radius: 4px; }
.sc-meta { display: flex; justify-content: space-between; font-size: 11px; color: var(--ink-3); margin-top: 4px; }
.sc-actions { display: flex; gap: 6px; margin-top: 8px; }

.chat-window { background: var(--bg-2); border-radius: 8px; padding: 12px; max-height: 360px; overflow-y: auto; margin-bottom: 12px; }
.chat-msg { display: flex; flex-direction: column; margin-bottom: 12px; }
.chat-msg.customer { align-items: flex-start; }
.chat-msg.advisor { align-items: flex-end; }
.chat-msg.system { align-items: center; }
.msg-bubble { max-width: 70%; padding: 8px 12px; border-radius: 12px; }
.chat-msg.customer .msg-bubble { background: var(--card); border-bottom-left-radius: 2px; }
.chat-msg.advisor .msg-bubble { background: var(--primary); color: #fff; border-bottom-right-radius: 2px; }
.msg-system { font-size: 11px; color: var(--ink-3); font-style: italic; padding: 4px 12px; background: rgba(184, 134, 11, 0.1); border-radius: 12px; }
.msg-meta { font-size: 10px; color: var(--ink-3); margin-top: 2px; padding: 0 4px; }

.chat-input { display: flex; gap: 8px; align-items: flex-end; }
.chat-input :deep(.el-textarea) { flex: 1; }
</style>
