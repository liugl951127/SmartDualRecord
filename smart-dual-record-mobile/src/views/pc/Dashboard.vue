<template>
  <div class="page-container">
    <!-- 顶栏 -->
    <div class="page-header-bar">
      <div class="page-title-group">
        <div class="page-icon">📊</div>
        <div>
          <h1 class="page-title">坐席工作台</h1>
          <div class="page-subtitle">欢迎回来, {{ auth.user?.name }} · {{ todayStr }}</div>
        </div>
      </div>
      <div class="page-actions">
        <button class="btn btn-ghost" @click="refreshAll">
          <span :class="['refresh-icon', refreshing && 'spinning']">↻</span>
          刷新
        </button>
        <button class="btn btn-accent" @click="goRecord">
          ⚡ 立即接单
        </button>
      </div>
    </div>

    <div class="page-body fade-in">
      <!-- ============ KPI 仪表盘 ============ -->
      <div class="grid grid-4 mb-16">
        <div class="stat-box">
          <div class="stat-icon primary">📋</div>
          <div class="stat-value">{{ kpi.totalToday }}</div>
          <div class="stat-label">今日业务总数</div>
          <div class="stat-trend up">↑ 12% vs 昨日</div>
        </div>
        <div class="stat-box">
          <div class="stat-icon accent">⏱️</div>
          <div class="stat-value">{{ kpi.avgDuration }}</div>
          <div class="stat-label">平均双录时长</div>
          <div class="stat-trend down">↓ 2.3 分钟</div>
        </div>
        <div class="stat-box">
          <div class="stat-icon success">✓</div>
          <div class="stat-value">{{ kpi.passRate }}%</div>
          <div class="stat-label">AI 一次通过率</div>
          <div class="stat-trend up">↑ 5.4%</div>
        </div>
        <div class="stat-box">
          <div class="stat-icon danger">🚨</div>
          <div class="stat-value">{{ kpi.alertsToday }}</div>
          <div class="stat-label">今日预警</div>
          <div class="stat-trend up">需关注</div>
        </div>
      </div>

      <!-- ============ 第二行: 实时队列 + 预警收件箱 ============ -->
      <div class="grid grid-2-1 mb-16">
        <!-- 实时客户队列 -->
        <div class="card">
          <div class="card-header">
            <h3 class="card-title">
              <span class="pulse-dot"></span>
              实时客户队列
              <span class="tag tag-info">{{ liveQueue.length }} 个待处理</span>
            </h3>
            <div class="flex gap-1">
              <button
                v-for="f in queueFilters"
                :key="f.value"
                :class="['btn btn-sm', queueFilter === f.value ? 'btn-accent' : 'btn-ghost']"
                @click="queueFilter = f.value"
              >
                {{ f.label }}
              </button>
            </div>
          </div>
          <div class="queue-list">
            <transition-group name="queue">
              <div
                v-for="(c, i) in filteredQueue"
                :key="c.id"
                :class="['queue-item', `priority-${c.priority}`, c.expiresSoon && 'urgent']"
                @click="onCustomerClick(c)"
              >
                <div class="qi-rank">{{ i + 1 }}</div>
                <div class="qi-avatar" :style="{ background: avatarColor(c.riskLevel) }">
                  {{ c.name.charAt(0) }}
                </div>
                <div class="qi-info">
                  <div class="qi-name">
                    {{ c.name }}
                    <span v-if="c.expiresSoon" class="tag tag-danger">⏰ 即将超时</span>
                    <span v-if="c.isVip" class="tag tag-accent">⭐ VIP</span>
                  </div>
                  <div class="qi-product">
                    <span class="mono text-sm">{{ c.productName }}</span>
                    <span :class="['tag', `tag-${riskColor(c.productRisk)}`]">R{{ c.productRisk }}</span>
                  </div>
                  <div class="qi-meta">
                    <span class="text-sm text-muted">¥{{ c.amount }}</span>
                    <span class="text-sm text-muted">·</span>
                    <span class="text-sm text-muted">客户: <span :class="`tag tag-${riskColor(c.riskLevel)}`">C{{ c.riskLevel }}</span></span>
                    <span class="text-sm text-muted">·</span>
                    <span class="text-sm text-muted">等待 {{ c.waitTime }} 分钟</span>
                  </div>
                </div>
                <div class="qi-actions">
                  <button class="btn btn-sm btn-primary" @click.stop="accept(c)">接单</button>
                  <button class="btn btn-sm btn-ghost" @click.stop="transfer(c)">转接</button>
                </div>
              </div>
            </transition-group>
            <div v-if="!filteredQueue.length" class="empty">
              <div class="empty-icon">🎉</div>
              <p class="empty-text">当前队列已清空, 太棒了!</p>
            </div>
          </div>
        </div>

        <!-- 预警收件箱 -->
        <div class="card card-warm">
          <div class="card-header">
            <h3 class="card-title">
              🚨 预警收件箱
            </h3>
            <span class="tag tag-danger">{{ alertList.filter(a => !a.handled).length }} 待处理</span>
          </div>
          <div class="alert-list">
            <div
              v-for="a in alertList"
              :key="a.id"
              :class="['alert-item', a.severity, a.handled && 'handled']"
              @click="onAlertClick(a)"
            >
              <div class="alert-bar"></div>
              <div class="alert-icon">{{ a.icon }}</div>
              <div class="alert-body">
                <div class="alert-title">{{ a.title }}</div>
                <div class="alert-desc">{{ a.desc }}</div>
                <div class="alert-meta">
                  <span class="alert-biz mono">{{ a.bizId }}</span>
                  <span class="alert-time">{{ a.time }}</span>
                </div>
              </div>
              <button
                v-if="!a.handled"
                class="btn btn-sm btn-ghost alert-mark"
                @click.stop="markHandled(a)"
              >
                ✓
              </button>
            </div>
            <div v-if="!alertList.length" class="empty">
              <div class="empty-icon">✅</div>
              <p class="empty-text">全部预警已处理</p>
            </div>
          </div>
        </div>
      </div>

      <!-- ============ 第三行: 业务分布 + 客户标签 + AI 趋势 + 快捷操作 ============ -->
      <div class="grid grid-3 mb-16">
        <!-- 业务分布图 -->
        <div class="card">
          <div class="card-header">
            <h3 class="card-title">📈 业务分布</h3>
            <span class="tag tag-default">实时</span>
          </div>
          <div class="card-body">
            <div class="dist-list">
              <div v-for="d in distribution" :key="d.label" class="dist-item">
                <div class="dist-row">
                  <div class="dist-label">
                    <span :class="['dist-dot', `dot-${d.color}`]"></span>
                    {{ d.label }}
                  </div>
                  <div class="dist-val mono">{{ d.value }} 单</div>
                </div>
                <div class="dist-bar-bg">
                  <div :class="['dist-bar', `bar-${d.color}`]" :style="{ width: d.percent + '%' }">
                  </div>
                </div>
                <div class="dist-percent text-sm text-muted">{{ d.percent }}%</div>
              </div>
            </div>
          </div>
        </div>

        <!-- 客户风险分布 -->
        <div class="card">
          <div class="card-header">
            <h3 class="card-title">🎯 客户风险分布</h3>
            <button class="btn btn-text btn-sm">详情 →</button>
          </div>
          <div class="card-body">
            <div class="risk-grid">
              <div
                v-for="r in riskDist"
                :key="r.level"
                :class="['risk-card', `level-${r.level}`]"
                @click="filterByRisk(r.level)"
              >
                <div class="rc-level">C{{ r.level }}</div>
                <div class="rc-count">{{ r.count }}</div>
                <div class="rc-percent">{{ r.percent }}%</div>
              </div>
            </div>
            <div class="match-warn">
              <div class="mw-icon">⚠️</div>
              <div class="mw-text">
                <div class="mw-title">3 个错配预警</div>
                <div class="mw-desc text-sm">C5 客户购买 R1 / C1 客户购买 R3 等</div>
              </div>
            </div>
          </div>
        </div>

        <!-- 快捷操作 -->
        <div class="card">
          <div class="card-header">
            <h3 class="card-title">⚡ 快捷操作</h3>
          </div>
          <div class="card-body">
            <div class="quick-grid">
              <a v-for="q in quickActions" :key="q.label" :class="['quick-item', `q-${q.color}`]" @click="q.onClick">
                <div class="qi-icon">{{ q.icon }}</div>
                <div class="qi-label">{{ q.label }}</div>
                <div class="qi-shortcut mono">{{ q.shortcut }}</div>
              </a>
            </div>
          </div>
        </div>
      </div>

      <!-- ============ 第四行: 实时事件流 + 待办 ============ -->
      <div class="grid grid-2">
        <!-- 实时事件流 -->
        <div class="card">
          <div class="card-header">
            <h3 class="card-title">
              <span class="pulse-dot"></span>
              实时事件流
            </h3>
            <button class="btn btn-text btn-sm" @click="eventLog = []">清空</button>
          </div>
          <div class="event-stream">
            <transition-group name="event">
              <div
                v-for="e in eventLog"
                :key="e.id"
                :class="['ev-item', `ev-${e.level}`]"
              >
                <span class="ev-time mono">{{ e.time }}</span>
                <span class="ev-icon">{{ e.icon }}</span>
                <span class="ev-text">{{ e.text }}</span>
                <span v-if="e.bizId" class="ev-biz mono text-sm text-muted">{{ e.bizId }}</span>
              </div>
            </transition-group>
            <div v-if="!eventLog.length" class="empty">
              <div class="empty-icon">📡</div>
              <p class="empty-text">暂无事件</p>
            </div>
          </div>
        </div>

        <!-- 待办事项 -->
        <div class="card">
          <div class="card-header">
            <h3 class="card-title">📋 我的待办</h3>
            <span class="tag tag-info">{{ todoList.filter(t => !t.done).length }} 项</span>
          </div>
          <div class="todo-list">
            <div
              v-for="t in todoList"
              :key="t.id"
              :class="['todo-item', t.done && 'done']"
              @click="toggleTodo(t)"
            >
              <div :class="['todo-check', t.done && 'checked']">
                <span v-if="t.done">✓</span>
              </div>
              <div class="todo-content">
                <div class="todo-title">{{ t.title }}</div>
                <div class="todo-meta">
                  <span class="text-sm text-muted">{{ t.due }}</span>
                  <span v-if="t.priority === 'high'" class="tag tag-danger">紧急</span>
                  <span v-else-if="t.priority === 'mid'" class="tag tag-warning">中等</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()

// ============ 状态 ============
const refreshing = ref(false)
const queueFilter = ref('all')
const todayStr = new Date().toLocaleDateString('zh-CN', { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' })

const kpi = ref({
  totalToday: 28,
  avgDuration: '12:34',
  passRate: 92.5,
  alertsToday: 7
})

// 实时客户队列
const liveQueue = ref([
  { id: 'q1', name: '张志强', productName: '稳赢 3 号', productRisk: 2, riskLevel: 1, amount: '50,000', waitTime: 0, priority: 'high', isVip: true, expiresSoon: false },
  { id: 'q2', name: '王明华', productName: '汇理财 7 日', productRisk: 1, riskLevel: 3, amount: '120,000', waitTime: 1, priority: 'high', isVip: false, expiresSoon: true },
  { id: 'q3', name: '李雪梅', productName: '结构性存款', productRisk: 2, riskLevel: 2, amount: '200,000', waitTime: 2, priority: 'mid', isVip: true, expiresSoon: false },
  { id: 'q4', name: '赵晓东', productName: '股票基金', productRisk: 4, riskLevel: 4, amount: '80,000', waitTime: 3, priority: 'mid', isVip: false, expiresSoon: false },
  { id: 'q5', name: '陈思琪', productName: '保险理财', productRisk: 3, riskLevel: 2, amount: '300,000', waitTime: 5, priority: 'low', isVip: true, expiresSoon: true },
  { id: 'q6', name: '刘建国', productName: '大额存单', productRisk: 1, riskLevel: 1, amount: '500,000', waitTime: 8, priority: 'low', isVip: true, expiresSoon: false }
])

const queueFilters = [
  { label: '全部', value: 'all' },
  { label: 'VIP', value: 'vip' },
  { label: '紧急', value: 'urgent' }
]
const filteredQueue = computed(() => {
  if (queueFilter.value === 'vip') return liveQueue.value.filter(c => c.isVip)
  if (queueFilter.value === 'urgent') return liveQueue.value.filter(c => c.expiresSoon)
  return liveQueue.value
})

// 预警收件箱
const alertList = ref([
  { id: 'a1', title: '禁播词触发: 保证收益', desc: '客户问 "这个保本吗? 你给我保证一下"', bizId: 'FND20260801-900004', time: '2 分钟前', severity: 'critical', icon: '🚫', handled: false },
  { id: 'a2', title: '风险等级错配', desc: 'C5 客户 申请购买 R1 低风险产品', bizId: 'LIC20260801-900001', time: '12 分钟前', severity: 'high', icon: '⚠️', handled: false },
  { id: 'a3', title: '录像中断', desc: '客户网络抖动, 录像分片丢失', bizId: 'BNK20260801-900003', time: '25 分钟前', severity: 'medium', icon: '📹', handled: false },
  { id: 'a4', title: '等待签字超时', desc: '客户签字页面停留超过 5 分钟', bizId: 'FND20260801-900002', time: '1 小时前', severity: 'low', icon: '⏰', handled: true }
])

// 业务分布
const distribution = ref([
  { label: '银行理财', value: 12, percent: 42.9, color: 'primary' },
  { label: '基金', value: 8, percent: 28.6, color: 'accent' },
  { label: '保险', value: 5, percent: 17.9, color: 'success' },
  { label: '结构性存款', value: 3, percent: 10.6, color: 'info' }
])

// 风险分布
const riskDist = ref([
  { level: 1, count: 8, percent: 28.6 },
  { level: 2, count: 10, percent: 35.7 },
  { level: 3, count: 6, percent: 21.4 },
  { level: 4, count: 3, percent: 10.7 },
  { level: 5, count: 1, percent: 3.6 }
])

// 实时事件流
const eventLog = ref<any[]>([])
let eventTimer: any = null

function pushEvent(e: any) {
  eventLog.value.unshift({ id: Date.now() + Math.random(), ...e })
  if (eventLog.value.length > 20) eventLog.value.pop()
}

// 待办
const todoList = ref([
  { id: 't1', title: '完成 张志强 双录 (¥50,000 稳赢 3 号)', due: '今天 14:30', priority: 'high', done: false },
  { id: 't2', title: '回访 王明华 (上周产品咨询)', due: '今天 16:00', priority: 'mid', done: false },
  { id: 't3', title: '提交 5 笔业务的 AI 终检报告', due: '今天 18:00', priority: 'high', done: false },
  { id: 't4', title: '参加产品培训: 稳赢 4 号 (新增)', due: '明天 10:00', priority: 'low', done: true }
])

// 快捷操作
const quickActions = [
  { label: '新建双录', icon: '➕', shortcut: 'N', color: 'primary', onClick: () => goRecord() },
  { label: '搜索客户', icon: '🔍', shortcut: '/', color: 'accent', onClick: () => router.push('/pc/customers') },
  { label: '话术模板', icon: '📝', shortcut: 'T', color: 'success', onClick: () => router.push('/pc/filepush') },
  { label: '禁播词', icon: '🚫', shortcut: 'F', color: 'danger', onClick: () => alert('禁播词库') },
  { label: 'AI 报告', icon: '🤖', shortcut: 'A', color: 'info', onClick: () => alert('AI 终检') },
  { label: '导出报表', icon: '📊', shortcut: 'E', color: 'default', onClick: () => alert('导出') }
]

// ============ 工具 ============
function riskColor(level: number) {
  const m: any = { 1: 'success', 2: 'primary', 3: 'warning', 4: 'warning', 5: 'danger' }
  return m[level] || 'default'
}
function avatarColor(level: number) {
  const colors: any = { 1: '#d1fae5', 2: '#dbeafe', 3: '#fef3c7', 4: '#fed7aa', 5: '#fee2e2' }
  return colors[level] || '#f0f2f7'
}

function onCustomerClick(c: any) {
  console.log('查看客户', c)
}
function accept(c: any) {
  pushEvent({ icon: '✅', text: `坐席接单: ${c.name} (${c.productName})`, level: 'success', bizId: c.id })
  liveQueue.value = liveQueue.value.filter(x => x.id !== c.id)
  router.push('/pc/record/' + c.id)
}
function transfer(c: any) {
  pushEvent({ icon: '🔄', text: `转接: ${c.name} → 理财经理`, level: 'info', bizId: c.id })
  liveQueue.value = liveQueue.value.filter(x => x.id !== c.id)
}
function onAlertClick(a: any) {
  console.log('查看预警', a)
}
function markHandled(a: any) {
  a.handled = true
  pushEvent({ icon: '✓', text: `预警已处理: ${a.title}`, level: 'info', bizId: a.bizId })
}
function toggleTodo(t: any) { t.done = !t.done }
function filterByRisk(level: number) {
  router.push('/pc/customers?level=' + level)
}
function goRecord() { router.push('/pc/bilateral') }
async function refreshAll() {
  refreshing.value = true
  await new Promise(r => setTimeout(r, 800))
  refreshing.value = false
  pushEvent({ icon: '↻', text: '数据已刷新', level: 'info' })
}

// 模拟事件流
function mockEvents() {
  const events = [
    { icon: '🆕', text: '新业务进线: 李雪梅 (¥200,000)', level: 'info', bizId: 'BNK20260801-900008' },
    { icon: '✅', text: 'AI 终检通过: 张志强 (92 分)', level: 'success', bizId: 'BNK20260801-900003' },
    { icon: '🚨', text: '禁播词告警: 客户说 "稳赚不赔"', level: 'danger', bizId: 'FND20260801-900005' },
    { icon: '✍️', text: '客户完成签字: 王明华', level: 'success', bizId: 'BNK20260801-900004' },
    { icon: '🔄', text: '业务转接: 赵晓东 → 理财经理', level: 'info', bizId: 'LIC20260801-900002' }
  ]
  const e = events[Math.floor(Math.random() * events.length)]
  pushEvent({ ...e, time: new Date().toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' }) })
}

onMounted(() => {
  eventTimer = setInterval(mockEvents, 8000)
  // 初始加几条
  for (let i = 0; i < 3; i++) {
    setTimeout(() => mockEvents(), i * 500)
  }
})

onUnmounted(() => {
  if (eventTimer) clearInterval(eventTimer)
})
</script>

<style lang="scss" scoped>
@import '@/styles/agent-theme.scss';

.mb-16 { margin-bottom: 16px; }

// ============ 仪表盘 2:1 布局 ============
.grid-2-1 {
  display: grid;
  grid-template-columns: 1.6fr 1fr;
  gap: 16px;
}
@media (max-width: 1280px) {
  .grid-2-1 { grid-template-columns: 1fr; }
}

// ============ 脉冲点 ============
.pulse-dot {
  display: inline-block;
  width: 8px; height: 8px;
  border-radius: 50%;
  background: var(--danger);
  margin-right: 6px;
  position: relative;
  &::after {
    content: '';
    position: absolute;
    inset: -3px;
    border-radius: 50%;
    background: var(--danger);
    opacity: 0.4;
    animation: pulse-ring 1.5s infinite;
  }
}
@keyframes pulse-ring {
  0% { transform: scale(0.8); opacity: 0.4; }
  100% { transform: scale(2); opacity: 0; }
}

// ============ 队列列表 ============
.queue-list {
  max-height: 460px;
  overflow-y: auto;
}
.queue-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border-light);
  cursor: pointer;
  transition: all 0.15s;
  position: relative;
  &:hover {
    background: var(--bg);
    transform: translateX(2px);
  }
  &.priority-high { border-left: 3px solid var(--danger); padding-left: 13px; }
  &.priority-mid { border-left: 3px solid var(--warning); padding-left: 13px; }
  &.priority-low { border-left: 3px solid var(--text-3); padding-left: 13px; }
  &.urgent {
    background: rgba(239, 68, 68, 0.03);
    animation: urgent-flash 2s ease-in-out infinite;
  }
}
@keyframes urgent-flash {
  0%, 100% { background: rgba(239, 68, 68, 0.03); }
  50% { background: rgba(239, 68, 68, 0.08); }
}
.qi-rank {
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
  font-weight: 700;
  color: var(--text-3);
  min-width: 20px;
  text-align: center;
}
.qi-avatar {
  width: 40px; height: 40px;
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-weight: 700;
  font-size: 16px;
  color: var(--text-1);
  flex-shrink: 0;
}
.qi-info { flex: 1; min-width: 0; }
.qi-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-1);
  display: flex;
  align-items: center;
  gap: 6px;
}
.qi-product {
  font-size: 12px;
  color: var(--text-2);
  margin-top: 2px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.qi-meta {
  display: flex;
  align-items: center;
  gap: 4px;
  margin-top: 4px;
  flex-wrap: wrap;
}
.qi-actions {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
  .btn-primary {
    box-shadow: 0 2px 6px rgba(30, 42, 71, 0.25);
    font-weight: 600;
  }
  .btn-ghost {
    background: white;
    border-color: var(--border);
    &:hover {
      background: var(--bg-accent);
      border-color: var(--line-accent);
      color: var(--accent-2);
    }
  }
}

// ============ 预警收件箱 ============
.alert-list {
  max-height: 460px;
  overflow-y: auto;
  padding: 4px;
}
.alert-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  background: white;
  border: 1px solid var(--border-light);
  border-radius: var(--radius);
  margin-bottom: 8px;
  cursor: pointer;
  position: relative;
  transition: all 0.2s;
  &:hover {
    background: var(--bg-accent);
    border-color: var(--line-accent);
    transform: translateX(2px);
    box-shadow: var(--shadow-sm);
  }
  &.handled { opacity: 0.5; }
}
.alert-bar {
  width: 4px;
  height: 36px;
  border-radius: 2px;
  background: var(--text-3);
  flex-shrink: 0;
}
.alert-item.severity-critical .alert-bar { background: var(--danger); box-shadow: 0 0 8px rgba(239,68,68,0.4); }
.alert-item.severity-high .alert-bar { background: var(--warning); }
.alert-item.severity-medium .alert-bar { background: var(--info); }
.alert-item.severity-low .alert-bar { background: var(--text-3); }

.alert-icon {
  font-size: 20px;
  width: 36px; height: 36px;
  display: flex; align-items: center; justify-content: center;
  border-radius: var(--radius-sm);
  background: var(--bg);
  flex-shrink: 0;
}
.alert-item.severity-critical .alert-icon { background: var(--danger-light); }
.alert-item.severity-high .alert-icon { background: var(--warning-light); }
.alert-item.severity-medium .alert-icon { background: var(--info-light); }
.alert-body { flex: 1; min-width: 0; }
.alert-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-1);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.alert-desc {
  font-size: 12px;
  color: var(--text-2);
  margin-top: 4px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.alert-meta {
  display: flex;
  gap: 8px;
  margin-top: 6px;
  font-size: 11px;
}
.alert-biz {
  color: var(--text-3);
  font-weight: 500;
  background: var(--bg);
  padding: 1px 6px;
  border-radius: 3px;
}
.alert-time { color: var(--text-3); }
.alert-mark {
  font-size: 14px;
  font-weight: 700;
  color: var(--success);
  border-color: var(--success);
  &:hover {
    background: var(--success);
    color: white;
  }
}

// ============ 业务分布 ============
.dist-list { display: flex; flex-direction: column; gap: 14px; }
.dist-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 4px;
}
.dist-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  font-weight: 500;
  color: var(--text-1);
}
.dist-dot { width: 8px; height: 8px; border-radius: 50%; }
.dot-primary { background: var(--primary); }
.dot-accent { background: var(--accent); }
.dot-success { background: var(--success); }
.dot-info { background: var(--info); }
.dist-val { font-size: 12px; color: var(--text-2); }
.dist-bar-bg {
  height: 6px;
  background: var(--bg);
  border-radius: 3px;
  overflow: hidden;
}
.dist-bar {
  height: 100%;
  border-radius: 3px;
  transition: width 0.5s ease;
}
.bar-primary { background: var(--primary); }
.bar-accent { background: var(--accent); }
.bar-success { background: var(--success); }
.bar-info { background: var(--info); }
.dist-percent { text-align: right; margin-top: 2px; }

// ============ 风险分布 ============
.risk-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 6px;
  margin-bottom: 12px;
}
.risk-card {
  text-align: center;
  padding: 12px 4px;
  border-radius: var(--radius);
  cursor: pointer;
  transition: all 0.15s;
  background: var(--bg);
  border: 1px solid transparent;
  &:hover { transform: translateY(-2px); box-shadow: var(--shadow); }
  &.level-1 { color: var(--success); }
  &.level-2 { color: var(--info); }
  &.level-3 { color: var(--warning); }
  &.level-4 { color: #ea580c; }
  &.level-5 { color: var(--danger); }
}
.rc-level { font-size: 11px; font-weight: 600; opacity: 0.8; }
.rc-count { font-size: 20px; font-weight: 700; margin: 2px 0; font-family: 'JetBrains Mono', monospace; }
.rc-percent { font-size: 10px; opacity: 0.7; }

.match-warn {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  background: var(--warning-light);
  border-radius: var(--radius);
  border-left: 3px solid var(--warning);
}
.mw-icon { font-size: 20px; }
.mw-text { flex: 1; }
.mw-title { font-size: 12px; font-weight: 600; color: var(--warning); }
.mw-desc { color: var(--text-2); }

// ============ 快捷操作 ============
.quick-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
}
.quick-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 14px 8px;
  border-radius: var(--radius);
  cursor: pointer;
  transition: all 0.15s;
  text-align: center;
  background: var(--bg);
  border: 1px solid transparent;
  &:hover { transform: translateY(-2px); box-shadow: var(--shadow); }
  &.q-primary:hover { background: rgba(30, 42, 71, 0.05); border-color: var(--primary); }
  &.q-accent:hover { background: rgba(184, 134, 11, 0.05); border-color: var(--accent); }
  &.q-success:hover { background: rgba(16, 185, 129, 0.05); border-color: var(--success); }
  &.q-danger:hover { background: rgba(239, 68, 68, 0.05); border-color: var(--danger); }
  &.q-info:hover { background: rgba(59, 130, 246, 0.05); border-color: var(--info); }
}
.qi-icon { font-size: 24px; }
.qi-label { font-size: 12px; font-weight: 500; color: var(--text-1); }
.qi-shortcut {
  font-size: 10px;
  padding: 1px 5px;
  background: white;
  border: 1px solid var(--border);
  border-radius: 3px;
  color: var(--text-3);
  font-weight: 600;
}

// ============ 事件流 ============
.event-stream {
  max-height: 320px;
  overflow-y: auto;
  padding: 4px;
}
.ev-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border-radius: var(--radius-sm);
  font-size: 12px;
  margin-bottom: 4px;
  &.ev-success { background: var(--success-light); }
  &.ev-info { background: var(--info-light); }
  &.ev-warning { background: var(--warning-light); }
  &.ev-danger { background: var(--danger-light); }
}
.ev-time { color: var(--text-3); font-size: 10px; }
.ev-icon { font-size: 14px; }
.ev-text { flex: 1; color: var(--text-1); }
.ev-biz { white-space: nowrap; }

// ============ 待办 ============
.todo-list { max-height: 320px; overflow-y: auto; padding: 4px; }
.todo-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-radius: var(--radius);
  cursor: pointer;
  transition: all 0.15s;
  &:hover { background: var(--bg); }
  &.done .todo-title { text-decoration: line-through; color: var(--text-3); }
}
.todo-check {
  width: 18px; height: 18px;
  border: 2px solid var(--border);
  border-radius: 4px;
  flex-shrink: 0;
  display: flex; align-items: center; justify-content: center;
  font-size: 11px;
  color: white;
  font-weight: 700;
  &.checked { background: var(--success); border-color: var(--success); }
}
.todo-content { flex: 1; min-width: 0; }
.todo-title { font-size: 13px; font-weight: 500; color: var(--text-1); }
.todo-meta { display: flex; align-items: center; gap: 6px; margin-top: 2px; }

// ============ 旋转图标 ============
.refresh-icon {
  display: inline-block;
  transition: transform 0.5s;
  &.spinning { animation: spin 1s linear infinite; }
}
@keyframes spin {
  to { transform: rotate(360deg); }
}

// ============ 过渡 ============
.queue-enter-active, .queue-leave-active { transition: all 0.3s; }
.queue-enter-from { opacity: 0; transform: translateX(-20px); }
.queue-leave-to { opacity: 0; transform: translateX(20px); }
.event-enter-active, .event-leave-active { transition: all 0.3s; }
.event-enter-from { opacity: 0; transform: translateY(-10px); }
.event-leave-to { opacity: 0; }
</style>
