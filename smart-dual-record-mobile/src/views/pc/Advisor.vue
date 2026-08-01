<template>
  <div class="page-container">
    <div class="page-header-bar">
      <div class="page-title-group">
        <div class="page-icon">💎</div>
        <div>
          <h1 class="page-title">理财经理</h1>
          <div class="page-subtitle">复杂业务转接 + 高净值客户管理</div>
        </div>
      </div>
      <div class="page-actions">
        <button class="btn btn-ghost" @click="refreshList">↻ 刷新</button>
        <button class="btn btn-accent" @click="onAccept">✅ 接受下一个</button>
      </div>
    </div>

    <div class="page-body fade-in">
      <!-- KPI -->
      <div class="grid grid-4 mb-12">
        <div class="stat-box">
          <div class="stat-icon primary">⏳</div>
          <div class="stat-value">{{ queue.length }}</div>
          <div class="stat-label">待接单</div>
        </div>
        <div class="stat-box">
          <div class="stat-icon success">✓</div>
          <div class="stat-value">{{ kpi.handled }}</div>
          <div class="stat-label">今日已处理</div>
        </div>
        <div class="stat-box">
          <div class="stat-icon accent">💰</div>
          <div class="stat-value mono">¥{{ kpi.amount }}</div>
          <div class="stat-label">今日成交额</div>
        </div>
        <div class="stat-box">
          <div class="stat-icon warning">⭐</div>
          <div class="stat-value">{{ kpi.rating }}</div>
          <div class="stat-label">客户评分</div>
        </div>
      </div>

      <div class="grid grid-2-1">
        <!-- 转接队列 -->
        <div class="card">
          <div class="card-header">
            <h3 class="card-title">🔄 转接队列</h3>
            <span class="tag tag-info">{{ queue.length }} 待处理</span>
          </div>
          <div class="transfer-list">
            <transition-group name="queue">
              <div
                v-for="(q, i) in queue"
                :key="q.id"
                :class="['tr-item', `pri-${q.priority}`, q.urgent && 'urgent']"
              >
                <div class="tr-rank">{{ i + 1 }}</div>
                <div class="tr-cust">
                  <div class="tr-name">
                    {{ q.customer }}
                    <span v-if="q.vip" class="tag tag-accent">VIP</span>
                    <span :class="['tag', `tag-${riskColor(q.risk)}`]">C{{ q.risk }}</span>
                  </div>
                  <div class="tr-product mono text-sm text-muted">{{ q.product }} · ¥{{ q.amount }}</div>
                  <div class="tr-reason text-sm text-secondary">"{{ q.reason }}"</div>
                </div>
                <div class="tr-meta">
                  <div class="tr-from text-sm text-muted">来自 {{ q.fromAgent }}</div>
                  <div class="tr-time text-sm text-muted">{{ q.waitTime }} 分钟前</div>
                </div>
                <div class="tr-actions">
                  <button class="btn btn-sm btn-success" @click="accept(q)">接受</button>
                  <button class="btn btn-sm btn-ghost" @click="decline(q)">拒绝</button>
                </div>
              </div>
            </transition-group>
            <div v-if="!queue.length" class="empty">
              <div class="empty-icon">🎉</div>
              <p class="empty-text">队列已清空</p>
            </div>
          </div>
        </div>

        <!-- 我的客户 -->
        <div class="card">
          <div class="card-header">
            <h3 class="card-title">⭐ 我的 VIP 客户</h3>
          </div>
          <div class="vip-list">
            <div v-for="c in vipCustomers" :key="c.id" class="vip-item">
              <div class="vip-avatar" :style="{ background: avatarColor(c.risk) }">
                {{ c.name.charAt(0) }}
              </div>
              <div class="vip-info">
                <div class="vip-name">{{ c.name }} <span class="tag tag-accent">VIP{{ c.level }}</span></div>
                <div class="vip-meta text-sm text-muted">
                  总资产 ¥{{ c.asset }} · {{ c.products }} 个产品
                </div>
              </div>
              <button class="btn btn-text btn-sm">📞</button>
            </div>
          </div>
        </div>
      </div>

      <!-- 转接原因分析 -->
      <div class="card mt-12">
        <div class="card-header">
          <h3 class="card-title">📊 转接原因分析 (本月)</h3>
        </div>
        <div class="reason-grid">
          <div v-for="r in reasons" :key="r.label" class="reason-item">
            <div class="ri-head">
              <div class="ri-label">{{ r.label }}</div>
              <div class="ri-val mono">{{ r.count }} <span class="text-sm text-muted">({{ r.percent }}%)</span></div>
            </div>
            <div class="dist-bar-bg mt-4">
              <div :class="['dist-bar', `bar-${r.color}`]" :style="{ width: r.percent + '%' }"></div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const queue = ref([
  { id: 'q1', customer: '张志强', product: 'BNK-FIN-2026Q3-001 稳赢 3 号', amount: '500,000', risk: 1, vip: true,
    reason: '客户要求 50 万大额购买, 请协助', fromAgent: '坐席小李', waitTime: 2, priority: 'high', urgent: true },
  { id: 'q2', customer: '李雪梅', product: 'STR-DP-2026Q3 结构性存款', amount: '1,000,000', risk: 2, vip: true,
    reason: '客户对挂钩标的有疑问, 请详细解释', fromAgent: '坐席小王', waitTime: 5, priority: 'high', urgent: false },
  { id: 'q3', customer: '陈思琪', product: 'INS-FIN-2026 保险理财', amount: '300,000', risk: 2, vip: true,
    reason: '客户希望了解提前退保条款', fromAgent: '坐席小赵', waitTime: 8, priority: 'mid', urgent: false }
])

const kpi = ref({ handled: 12, amount: '4.8M', rating: 4.9 })

const vipCustomers = [
  { id: 'v1', name: '李雪梅', level: 5, risk: 2, asset: '1,250,000', products: 3 },
  { id: 'v2', name: '刘建国', level: 5, risk: 1, asset: '1,800,000', products: 4 },
  { id: 'v3', name: '陈思琪', level: 4, risk: 2, asset: '760,000', products: 2 },
  { id: 'v4', name: '张志强', level: 3, risk: 1, asset: '580,000', products: 3 }
]

const reasons = [
  { label: '大额购买 (>= 50万)', count: 8, percent: 40, color: 'accent' },
  { label: '产品疑问', count: 6, percent: 30, color: 'primary' },
  { label: '条款咨询', count: 4, percent: 20, color: 'success' },
  { label: '其他', count: 2, percent: 10, color: 'info' }
]

function riskColor(level: number) {
  const m: any = { 1: 'success', 2: 'primary', 3: 'warning', 4: 'warning', 5: 'danger' }
  return m[level] || 'default'
}
function avatarColor(level: number) {
  const colors: any = { 1: '#d1fae5', 2: '#dbeafe', 3: '#fef3c7', 4: '#fed7aa', 5: '#fee2e2' }
  return colors[level] || '#f0f2f7'
}

function accept(q: any) {
  alert(`已接受 ${q.customer}, 跳转业务详情`)
  queue.value = queue.value.filter(x => x.id !== q.id)
}
function decline(q: any) {
  alert(`已拒绝 ${q.customer}`)
  queue.value = queue.value.filter(x => x.id !== q.id)
}
function onAccept() {
  if (queue.value[0]) accept(queue.value[0])
}
function refreshList() { alert('刷新') }
</script>

<style lang="scss" scoped>
@use '@/styles/agent-theme.scss' as *;

.mb-12 { margin-bottom: 12px; }
.mt-4 { margin-top: 4px; }
.mt-12 { margin-top: 12px; }

.grid-2-1 {
  display: grid;
  grid-template-columns: 1.5fr 1fr;
  gap: 12px;
  margin-bottom: 12px;
}
@media (max-width: 1200px) { .grid-2-1 { grid-template-columns: 1fr; } }

// 转接队列
.transfer-list { padding: 4px 0; max-height: 480px; overflow-y: auto; }
.tr-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--border-light);
  transition: all 0.15s;
  &.pri-high { border-left: 3px solid var(--danger); padding-left: 13px; }
  &.pri-mid { border-left: 3px solid var(--warning); padding-left: 13px; }
  &.pri-low { border-left: 3px solid var(--text-3); padding-left: 13px; }
  &.urgent {
    background: rgba(239, 68, 68, 0.03);
    animation: urgent-flash 2s infinite;
  }
  &:hover { background: var(--bg); }
}
@keyframes urgent-flash {
  0%, 100% { background: rgba(239, 68, 68, 0.03); }
  50% { background: rgba(239, 68, 68, 0.08); }
}
.tr-rank {
  font-family: 'JetBrains Mono', monospace;
  font-size: 12px;
  font-weight: 700;
  color: var(--text-3);
  min-width: 20px;
}
.tr-cust { flex: 1; min-width: 0; }
.tr-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-1);
  display: flex;
  align-items: center;
  gap: 6px;
}
.tr-product { margin-top: 2px; }
.tr-reason {
  margin-top: 4px;
  padding: 4px 8px;
  background: var(--bg);
  border-radius: var(--radius-sm);
  border-left: 2px solid var(--accent);
  font-style: italic;
}
.tr-meta { text-align: right; }
.tr-actions { display: flex; flex-direction: column; gap: 4px; }

// VIP 客户
.vip-list { padding: 4px 0; }
.vip-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
  border-bottom: 1px solid var(--border-light);
  &:last-child { border-bottom: none; }
  &:hover { background: var(--bg); }
}
.vip-avatar {
  width: 36px; height: 36px;
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-weight: 700;
  font-size: 14px;
  flex-shrink: 0;
}
.vip-info { flex: 1; min-width: 0; }
.vip-name { font-size: 13px; font-weight: 600; display: flex; align-items: center; gap: 4px; }

// 原因分析
.reason-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
  padding: 16px 20px;
}
.ri-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.ri-label { font-size: 13px; font-weight: 500; color: var(--text-1); }
.ri-val { font-size: 14px; font-weight: 700; color: var(--text-1); }
.dist-bar-bg { height: 6px; background: var(--bg); border-radius: 3px; overflow: hidden; }
.dist-bar { height: 100%; border-radius: 3px; transition: width 0.5s; }
.bar-primary { background: var(--primary); }
.bar-accent { background: var(--accent); }
.bar-success { background: var(--success); }
.bar-info { background: var(--info); }

.queue-enter-active, .queue-leave-active { transition: all 0.3s; }
.queue-enter-from { opacity: 0; transform: translateX(-20px); }
.queue-leave-to { opacity: 0; }
</style>
