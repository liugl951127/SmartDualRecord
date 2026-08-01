<template>
  <div class="customer-list">
    <div class="page-header">
      <h1>客户管理</h1>
      <p>{{ filtered.length }} 个客户 · 实时</p>
    </div>

    <div class="toolbar">
      <input v-model="searchKey" placeholder="搜索客户姓名/手机号" class="search-input" />
      <select v-model="filterStatus" class="filter-select">
        <option value="ALL">全部状态</option>
        <option value="RECORDING">双录中</option>
        <option value="COMPLETED">已完成</option>
        <option value="FAILED">失败</option>
        <option value="PENDING">待开始</option>
      </select>
    </div>

    <table class="data-table">
      <thead>
        <tr>
          <th>客户ID</th>
          <th>姓名</th>
          <th>手机</th>
          <th>风险</th>
          <th>订单数</th>
          <th>最近活动</th>
          <th>状态</th>
          <th>操作</th>
        </tr>
      </thead>
      <tbody>
        <tr v-for="c in filtered" :key="c.id">
          <td class="mono">{{ c.id }}</td>
          <td>{{ c.name }}</td>
          <td>{{ maskedPhone(c.phone) }}</td>
          <td><span :class="['risk', `risk-${c.riskLevel}`]">{{ c.riskLevel }}</span></td>
          <td>{{ c.orderCount }}</td>
          <td>{{ c.lastActivity }}</td>
          <td>
            <span :class="['status', `status-${c.statusCls}`]">{{ c.statusLabel }}</span>
          </td>
          <td>
            <button class="btn btn-sm" @click="$router.push(`/pc/record/${c.activeBiz}`)" v-if="c.activeBiz">双录</button>
            <button class="btn btn-sm" @click="onContact(c)">联系</button>
            <button class="btn btn-sm" @click="$router.push('/pc/filepush')">推送文件</button>
          </td>
        </tr>
      </tbody>
    </table>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { showDialog } from 'vant'

const searchKey = ref('')
const filterStatus = ref('ALL')

const customers = ref([
  { id: 'cust-hash-001', name: '张志强', phone: '13800138001', riskLevel: 'C1', orderCount: 3, lastActivity: '2026-07-20 10:45', status: 'COMPLETED', statusLabel: '已完成', statusCls: 'success', activeBiz: '' },
  { id: 'cust-hash-002', name: '王明华', phone: '13900139002', riskLevel: 'C3', orderCount: 5, lastActivity: '2026-08-01 10:15', status: 'RECORDING', statusLabel: '双录中', statusCls: 'warning', activeBiz: 'BNK20260801-900003' },
  { id: 'cust-hash-003', name: '李建国', phone: '13700137003', riskLevel: 'C5', orderCount: 8, lastActivity: '2026-07-25 14:50', status: 'COMPLETED', statusLabel: '已完成', statusCls: 'success', activeBiz: '' },
  { id: 'cust-hash-004', name: '赵晓东', phone: '13600136004', riskLevel: 'C3', orderCount: 2, lastActivity: '2026-08-01 10:20', status: 'FAILED', statusLabel: '失败', statusCls: 'danger', activeBiz: 'FND20260801-900004' },
  { id: 'cust-hash-005', name: '陈美丽', phone: '13500135005', riskLevel: 'C2', orderCount: 1, lastActivity: '2026-08-01 11:00', status: 'PENDING', statusLabel: '待开始', statusCls: 'info', activeBiz: '' }
])

const filtered = computed(() => {
  return customers.value.filter(c => {
    const m1 = !searchKey.value || c.name.includes(searchKey.value) || c.phone.includes(searchKey.value)
    const m2 = filterStatus.value === 'ALL' || c.status === filterStatus.value
    return m1 && m2
  })
})

function maskedPhone(p: string) {
  return p.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2')
}

function onContact(c: any) {
  showDialog({ title: '联系客户', message: `客户: ${c.name}\n电话: ${c.phone}` })
}
</script>

<style lang="scss" scoped>
.customer-list { padding: 24px; }
.page-header { margin-bottom: 16px; h1 { font-size: 22px; font-weight: 600; margin: 0 0 4px; } p { font-size: 13px; color: var(--text-3); margin: 0; } }

.toolbar {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}
.search-input, .filter-select {
  padding: 8px 12px;
  border: 1px solid var(--border);
  border-radius: 6px;
  font-size: 13px;
  background: white;
}
.search-input { flex: 1; max-width: 300px; }

.data-table {
  width: 100%;
  background: white;
  border-radius: 12px;
  overflow: hidden;
  border-collapse: collapse;
  font-size: 13px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.05);
  th, td { padding: 12px; text-align: left; border-bottom: 1px solid var(--border); }
  th { color: var(--text-3); font-weight: 500; background: var(--bg); font-size: 12px; }
  tr:last-child td { border-bottom: none; }
}
.mono { font-family: monospace; font-size: 12px; }

.risk { padding: 2px 6px; border-radius: 3px; font-size: 11px; font-weight: 600; }
.risk-C1, .risk-C2 { background: rgba(7,193,96,0.1); color: var(--success); }
.risk-C3 { background: rgba(255,151,106,0.1); color: var(--warning); }
.risk-C4, .risk-C5 { background: rgba(238,10,36,0.1); color: var(--danger); }

.status {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 4px;
  &.status-success { background: rgba(7,193,96,0.1); color: var(--success); }
  &.status-warning { background: rgba(255,151,106,0.1); color: var(--warning); }
  &.status-danger { background: rgba(238,10,36,0.1); color: var(--danger); }
  &.status-info { background: rgba(30,42,71,0.1); color: var(--primary); }
}

.btn { padding: 4px 10px; border: 1px solid var(--border); background: white; border-radius: 4px; cursor: pointer; font-size: 12px; margin-right: 4px; &:hover { background: var(--bg); } }
</style>
