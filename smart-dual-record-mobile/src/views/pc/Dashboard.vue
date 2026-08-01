<template>
  <div class="dashboard">
    <div class="page-header">
      <h1>工作台</h1>
      <p>实时业务概览 · {{ today }}</p>
    </div>

    <div class="stat-grid">
      <div class="stat-card">
        <div class="stat-num">{{ stats.activeSessions }}</div>
        <div class="stat-label">进行中</div>
        <div class="stat-trend up">↑ 12%</div>
      </div>
      <div class="stat-card">
        <div class="stat-num">{{ stats.todayCompleted }}</div>
        <div class="stat-label">今日完成</div>
        <div class="stat-trend up">↑ 8%</div>
      </div>
      <div class="stat-card">
        <div class="stat-num">{{ stats.todayFailed }}</div>
        <div class="stat-label">今日失败</div>
        <div class="stat-trend down">↓ 5%</div>
      </div>
      <div class="stat-card">
        <div class="stat-num">{{ stats.complianceRate }}%</div>
        <div class="stat-label">合规率</div>
        <div class="stat-trend up">↑ 0.5%</div>
      </div>
    </div>

    <div class="content-grid">
      <div class="card">
        <div class="card-header">
          <h3>📈 业务趋势 (近 7 天)</h3>
        </div>
        <div ref="chartEl" class="chart"></div>
      </div>

      <div class="card">
        <div class="card-header">
          <h3>🚨 实时告警</h3>
          <span class="badge">{{ alerts.length }}</span>
        </div>
        <div class="alert-list">
          <div v-for="a in alerts" :key="a.id" :class="['alert-item', `severity-${a.severity}`]">
            <div class="alert-time">{{ a.time }}</div>
            <div class="alert-msg">{{ a.msg }}</div>
          </div>
        </div>
      </div>
    </div>

    <div class="card">
      <div class="card-header">
        <h3>📋 今日业务</h3>
        <button class="btn btn-sm" @click="$router.push('/pc/customers')">查看全部</button>
      </div>
      <table class="data-table">
        <thead>
          <tr>
            <th>业务ID</th>
            <th>客户</th>
            <th>产品</th>
            <th>金额</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="b in businesses" :key="b.id">
            <td class="mono">{{ b.id }}</td>
            <td>{{ b.customer }}</td>
            <td>{{ b.product }}</td>
            <td>¥{{ b.amount.toLocaleString() }}</td>
            <td>
              <span :class="['status', `status-${b.statusCls}`]">{{ b.statusLabel }}</span>
            </td>
            <td>
              <button class="btn btn-sm" @click="$router.push(`/pc/record/${b.id}`)">双录</button>
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import * as echarts from 'echarts'

const today = new Date().toLocaleDateString('zh-CN')

const stats = ref({
  activeSessions: 12,
  todayCompleted: 28,
  todayFailed: 2,
  complianceRate: 99.6
})

const alerts = ref([
  { id: 1, time: '10:32', msg: '客户 FND20260801-900004 触发禁播词: "保本收益"', severity: 'high' },
  { id: 2, time: '10:15', msg: '客户 BNK20260801-900003 双录进行中 (节点 5/8)', severity: 'low' },
  { id: 3, time: '09:45', msg: '客户 LIC20260801-900001 合同已生效', severity: 'info' }
])

const businesses = ref([
  { id: 'BNK20260801-900001', customer: '张*', product: '稳赢理财', amount: 50000, status: 'COMPLETED', statusLabel: '已完成', statusCls: 'success' },
  { id: 'LIC20260801-900001', customer: '李**', product: '投资连结险', amount: 100000, status: 'COMPLETED', statusLabel: '已完成', statusCls: 'success' },
  { id: 'BNK20260801-900003', customer: '王**', product: '混合策略', amount: 80000, status: 'RECORDING', statusLabel: '双录中', statusCls: 'warning' },
  { id: 'FND20260801-900004', customer: '赵**', product: '稳进基金', amount: 60000, status: 'FAILED', statusLabel: '已失败', statusCls: 'danger' }
])

const chartEl = ref<HTMLDivElement>()
let chart: echarts.ECharts | null = null

onMounted(() => {
  nextTick(() => initChart())
})
onUnmounted(() => { chart?.dispose() })

function initChart() {
  if (!chartEl.value) return
  chart = echarts.init(chartEl.value)
  chart.setOption({
    grid: { top: 30, left: 40, right: 20, bottom: 30 },
    xAxis: {
      type: 'category',
      data: ['07-26', '07-27', '07-28', '07-29', '07-30', '07-31', '08-01'],
      axisLine: { lineStyle: { color: '#969799' } }
    },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: '#ebedf0' } } },
    series: [
      {
        name: '完成', type: 'line', smooth: true,
        data: [22, 28, 25, 30, 32, 26, 28],
        itemStyle: { color: '#07c160' },
        areaStyle: { color: 'rgba(7,193,96,0.1)' }
      },
      {
        name: '失败', type: 'line', smooth: true,
        data: [1, 2, 1, 3, 2, 1, 2],
        itemStyle: { color: '#ee0a24' }
      }
    ],
    legend: { data: ['完成', '失败'], top: 0, right: 0 }
  })
}
</script>

<style lang="scss" scoped>
.dashboard { padding: 24px; }

.page-header {
  margin-bottom: 24px;
  h1 { font-size: 22px; font-weight: 600; margin: 0 0 4px; }
  p { font-size: 13px; color: var(--text-3); margin: 0; }
}

.stat-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-bottom: 16px;
}
.stat-card {
  background: white;
  border-radius: 12px;
  padding: 20px;
  position: relative;
  box-shadow: 0 1px 4px rgba(0,0,0,0.05);
}
.stat-num {
  font-size: 32px;
  font-weight: 700;
  font-family: 'JetBrains Mono', monospace;
  color: var(--primary);
}
.stat-label { font-size: 13px; color: var(--text-3); margin-top: 4px; }
.stat-trend {
  position: absolute;
  top: 20px; right: 20px;
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 4px;
  &.up { background: rgba(7,193,96,0.1); color: var(--success); }
  &.down { background: rgba(238,10,36,0.1); color: var(--danger); }
}

.content-grid {
  display: grid;
  grid-template-columns: 2fr 1fr;
  gap: 16px;
  margin-bottom: 16px;
}
.card { background: white; border-radius: 12px; padding: 20px; box-shadow: 0 1px 4px rgba(0,0,0,0.05); }
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  h3 { font-size: 15px; font-weight: 600; margin: 0; }
}
.badge {
  background: var(--danger);
  color: white;
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 10px;
  font-weight: 500;
}
.chart { height: 240px; }

.alert-list { max-height: 240px; overflow-y: auto; }
.alert-item {
  padding: 8px 12px;
  border-radius: 6px;
  margin-bottom: 6px;
  font-size: 13px;
  border-left: 3px solid;
  &.severity-high { background: rgba(238,10,36,0.05); border-color: var(--danger); }
  &.severity-low { background: rgba(255,151,106,0.05); border-color: var(--warning); }
  &.severity-info { background: rgba(30,42,71,0.05); border-color: var(--primary); }
}
.alert-time { font-size: 11px; color: var(--text-3); font-family: monospace; }

.data-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
  th, td { padding: 10px 8px; text-align: left; border-bottom: 1px solid var(--border); }
  th { color: var(--text-3); font-weight: 500; background: var(--bg); }
}
.mono { font-family: monospace; font-size: 12px; }

.status {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 4px;
  &.status-success { background: rgba(7,193,96,0.1); color: var(--success); }
  &.status-warning { background: rgba(255,151,106,0.1); color: var(--warning); }
  &.status-danger { background: rgba(238,10,36,0.1); color: var(--danger); }
}

.btn { padding: 6px 12px; border: 1px solid var(--border); background: white; border-radius: 4px; cursor: pointer; font-size: 12px; }
.btn-sm { padding: 4px 10px; font-size: 12px; }
</style>
