<template>
  <div class="my-orders">
    <div class="page-header">
      <h1 class="page-title">我的订单</h1>
      <p class="page-subtitle">查看订单状态 · 跟踪双录进度</p>
    </div>

    <div class="filter-tabs">
      <div
        v-for="t in tabs"
        :key="t.value"
        :class="['tab', active === t.value && 'active']"
        @click="active = t.value"
      >{{ t.label }}</div>
    </div>

    <div class="orders-list">
      <div
        v-for="o in filtered"
        :key="o.id"
        class="order-card"
        @click="$router.push(`/h5/order/${o.id}`)"
      >
        <div class="order-header">
          <div class="order-id">{{ o.id }}</div>
          <div :class="['status', `status-${o.statusCls}`]">{{ o.statusLabel }}</div>
        </div>
        <div class="order-body">
          <div class="product">
            <div class="p-icon">{{ o.icon }}</div>
            <div class="p-info">
              <div class="p-name">{{ o.productName }}</div>
              <div class="p-meta">{{ o.productId }}</div>
            </div>
          </div>
          <div class="amount">¥{{ o.amount.toLocaleString() }}</div>
        </div>
        <div class="order-footer">
          <span class="time">{{ o.createTime }}</span>
          <span class="channel">{{ channelLabel(o.channel) }}</span>
        </div>
      </div>
      <div v-if="!filtered.length" class="empty">
        <div class="empty-icon">📭</div>
        <p>暂无订单</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'

const tabs = [
  { label: '全部', value: 'ALL' },
  { label: '待双录', value: 'PENDING' },
  { label: '已完成', value: 'COMPLETED' },
  { label: '失败', value: 'FAILED' }
]
const active = ref('ALL')

const orders = ref([
  { id: 'BIZ-20260801-100001', productId: 'BNK-FIN-2026Q3-001', productName: '稳赢系列 · 固收理财', amount: 50000, icon: '🏦', channel: 'OFFLINE', status: 'COMPLETED', statusLabel: '已完成', statusCls: 'success', createTime: '2026-07-20 10:00' },
  { id: 'BIZ-20260801-100002', productId: 'LIC-INV-2026Q3-001', productName: '稳健投资连结险', amount: 100000, icon: '🛡️', channel: 'REMOTE_VIDEO', status: 'COMPLETED', statusLabel: '已完成', statusCls: 'success', createTime: '2026-07-25 14:00' },
  { id: 'BIZ-20260801-100003', productId: 'BNK-MIX-2026Q3-002', productName: '混合策略 · 股债平衡', amount: 80000, icon: '🏦', channel: 'OFFLINE', status: 'PENDING', statusLabel: '双录中', statusCls: 'warning', createTime: '2026-08-01 09:00' },
  { id: 'BIZ-20260801-100004', productId: 'FND-STK-2026Q3-002', productName: '稳进混合基金', amount: 60000, icon: '📈', channel: 'SELF_AI', status: 'FAILED', statusLabel: '已失败', statusCls: 'danger', createTime: '2026-08-01 10:00' }
])

const filtered = computed(() => {
  if (active.value === 'ALL') return orders.value
  return orders.value.filter(o => o.status === active.value)
})

function channelLabel(c: string) {
  return ({ OFFLINE: '线下柜面', REMOTE_VIDEO: '远程视频', SELF_AI: '数字人', INTERNET_TEXT: '互联网' } as Record<string, string>)[c] || c
}
</script>

<style lang="scss" scoped>
.my-orders { min-height: 100vh; }

.filter-tabs {
  display: flex;
  padding: 0 12px;
  background: var(--primary);
  gap: 8px;
  overflow-x: auto;
}
.tab {
  padding: 10px 16px;
  color: rgba(255,255,255,0.7);
  font-size: 13px;
  flex-shrink: 0;
  position: relative;
  &.active {
    color: white;
    font-weight: 600;
    &::after {
      content: '';
      position: absolute;
      bottom: 0; left: 16px; right: 16px;
      height: 2px;
      background: var(--accent);
    }
  }
}

.orders-list { padding: 12px; }
.order-card {
  background: var(--card);
  border-radius: 12px;
  padding: 16px;
  margin-bottom: 12px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.05);
}
.order-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
  padding-bottom: 12px;
  border-bottom: 1px solid var(--border);
}
.order-id { font-size: 12px; color: var(--text-3); font-family: monospace; }
.status {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 4px;
  font-weight: 500;
  &.status-success { background: rgba(7,193,96,0.1); color: var(--success); }
  &.status-warning { background: rgba(255,151,106,0.1); color: var(--warning); }
  &.status-danger { background: rgba(238,10,36,0.1); color: var(--danger); }
}
.order-body {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.product { display: flex; align-items: center; gap: 12px; }
.p-icon { font-size: 32px; }
.p-name { font-size: 14px; font-weight: 600; }
.p-meta { font-size: 11px; color: var(--text-3); margin-top: 2px; font-family: monospace; }
.amount { font-size: 18px; font-weight: 700; color: var(--accent); font-family: 'JetBrains Mono', monospace; }

.order-footer {
  display: flex;
  justify-content: space-between;
  margin-top: 12px;
  font-size: 11px;
  color: var(--text-3);
}

.empty { padding: 80px 20px; text-align: center; color: var(--text-3); }
.empty-icon { font-size: 48px; margin-bottom: 8px; opacity: 0.4; }
</style>
