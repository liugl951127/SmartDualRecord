<script setup lang="ts">
import { ref, computed } from 'vue'
import { recordingApi } from '@/api'
import { STATE_COLORS, CHANNEL_LABELS, NODE_DEFINITIONS } from '@/utils/nodes'
import { ElMessage } from 'element-plus'
import { Search, ArrowRight, Document, VideoCamera, Bell, Lock, DataAnalysis, Promotion } from '@element-plus/icons-vue'

const businessId = ref('BNK20260801-000001')
const loading = ref(false)
const data = ref<any>(null)

async function query() {
  if (!businessId.value) {
    ElMessage.warning('请输入业务 ID')
    return
  }
  loading.value = true
  try {
    data.value = await recordingApi.overview(businessId.value)
  } catch (e: any) {
    ElMessage.error(e.message)
    data.value = null
  } finally {
    loading.value = false
  }
}

const nodeName = (code: string) => {
  return NODE_DEFINITIONS.find(n => n.code === code)?.displayName || code
}

// KPI 计算
const kpi = computed(() => {
  if (!data.value) return null
  return {
    state: data.value.business.state,
    amount: data.value.business.amount || '-',
    risk: data.value.business.riskLevel || '-',
    productRisk: data.value.business.productRiskLevel || '-',
    nodeDone: `${data.value.completed_node_count}/${data.value.node_count}`,
    channel: CHANNEL_LABELS[data.value.business.channel] || data.value.business.channel
  }
})

const completedPercent = computed(() => {
  if (!data.value) return 0
  return Math.round((data.value.completed_node_count / data.value.node_count) * 100)
})

// 推荐查询
const recentSearches = [
  { id: 'BNK20260801-000001', label: '稳赢 3 号 · 50,000', state: 'ARCHIVED' },
  { id: 'BNK20260801-900003', label: '稳赢 3 号 · 50,000', state: 'INIT' },
  { id: 'FND20260801-900004', label: '汇理财 7 日 · 120,000', state: 'FAILED' }
]

function onRecentClick(id: string) {
  businessId.value = id
  query()
}
</script>

<template>
  <div>
    <!-- ============ 搜索卡 ============ -->
    <div class="card">
      <h3 class="card-title">
        <span>🔍 业务全景查询</span>
        <span class="text-sm text-muted">输入业务 ID 查看完整状态机轨迹</span>
      </h3>
      <div class="search-bar">
        <div class="search-input-wrap">
          <el-icon class="search-icon"><Search /></el-icon>
          <input
            v-model="businessId"
            class="search-input"
            placeholder="例如 BNK20260801-000001"
            @keyup.enter="query"
          />
          <button class="search-btn" :class="{ loading }" @click="query" :disabled="loading">
            <span v-if="!loading">查询 →</span>
            <span v-else>查询中...</span>
          </button>
        </div>
        <div class="recent-row">
          <span class="recent-label">最近查询:</span>
          <button
            v-for="r in recentSearches"
            :key="r.id"
            class="recent-chip"
            @click="onRecentClick(r.id)"
          >
            <span class="mono">{{ r.id }}</span>
            <span class="text-sm text-muted">{{ r.label }}</span>
            <span class="state-badge" :class="r.state === 'ARCHIVED' ? 'success' : r.state === 'FAILED' ? 'danger' : 'info'" style="font-size: 9px; padding: 1px 6px;">
              {{ r.state }}
            </span>
          </button>
        </div>
      </div>
    </div>

    <!-- 空状态 -->
    <div v-if="!data && !loading" class="card">
      <div class="empty-state">
        <div class="empty-icon"><DataAnalysis /></div>
        <h3>输入业务 ID 开始查询</h3>
        <p>支持查询完整状态机轨迹、节点明细、录像信息、风险匹配</p>
      </div>
    </div>

    <!-- ============ 业务概况 ============ -->
    <div v-if="data" class="overview-grid">
      <!-- 左侧: 状态机概要 -->
      <div class="card overview-main">
        <h3 class="card-title">
          <span>📊 业务概况</span>
          <span class="state-badge primary" v-if="kpi">{{ kpi.state }}</span>
        </h3>

        <!-- 进度条 -->
        <div class="progress-section">
          <div class="progress-header">
            <span>节点完成度</span>
            <span class="mono font-bold">{{ kpi?.nodeDone }} ({{ completedPercent }}%)</span>
          </div>
          <div class="progress-bar large">
            <div class="progress-fill" :style="{ width: completedPercent + '%' }"></div>
          </div>
        </div>

        <!-- 字段网格 -->
        <div class="field-grid">
          <div class="field">
            <div class="field-label">业务 ID</div>
            <div class="field-value mono">{{ data.business.businessId }}</div>
          </div>
          <div class="field">
            <div class="field-label">业务类型</div>
            <div class="field-value">{{ data.business.businessType }}</div>
          </div>
          <div class="field">
            <div class="field-label">产品 ID</div>
            <div class="field-value mono">{{ data.business.productId }}</div>
          </div>
          <div class="field">
            <div class="field-label">渠道</div>
            <div class="field-value">{{ kpi?.channel }}</div>
          </div>
          <div class="field">
            <div class="field-label">客户 ID</div>
            <div class="field-value mono">{{ data.business.customerIdHash }}</div>
          </div>
          <div class="field">
            <div class="field-label">金额</div>
            <div class="field-value mono font-bold text-accent">¥{{ data.business.amount || '-' }}</div>
          </div>
          <div class="field">
            <div class="field-label">客户风险</div>
            <div class="field-value">
              <span v-if="data.business.riskLevel" class="risk-pill" :class="data.business.riskLevel">
                {{ data.business.riskLevel }}
              </span>
            </div>
          </div>
          <div class="field">
            <div class="field-label">产品风险</div>
            <div class="field-value">
              <span v-if="data.business.productRiskLevel" class="risk-pill" :class="data.business.productRiskLevel">
                {{ data.business.productRiskLevel }}
              </span>
            </div>
          </div>
          <div class="field">
            <div class="field-label">创建时间</div>
            <div class="field-value mono text-sm">{{ data.business.createdAt }}</div>
          </div>
          <div class="field">
            <div class="field-label">更新时间</div>
            <div class="field-value mono text-sm">{{ data.business.updatedAt }}</div>
          </div>
          <div v-if="data.business.archivedAt" class="field">
            <div class="field-label">归档时间</div>
            <div class="field-value mono text-sm">{{ data.business.archivedAt }}</div>
          </div>
        </div>
      </div>

      <!-- 右侧: 快捷操作 -->
      <div class="overview-side">
        <div class="card">
          <h3 class="card-title"><span>⚡ 快捷操作</span></h3>
          <div class="action-list">
            <a class="action-item">
              <div class="action-icon"><VideoCamera /></div>
              <div class="action-body">
                <div class="action-name">查看录像</div>
                <div class="action-desc text-sm text-muted">查看该业务所有录像文件</div>
              </div>
              <el-icon><ArrowRight /></el-icon>
            </a>
            <a class="action-item">
              <div class="action-icon"><Document /></div>
              <div class="action-body">
                <div class="action-name">导出报告</div>
                <div class="action-desc text-sm text-muted">导出 PDF 双录报告</div>
              </div>
              <el-icon><ArrowRight /></el-icon>
            </a>
            <a class="action-item">
              <div class="action-icon"><Lock /></div>
              <div class="action-body">
                <div class="action-name">申请公证</div>
                <div class="action-desc text-sm text-muted">向公证处申请证据保全</div>
              </div>
              <el-icon><ArrowRight /></el-icon>
            </a>
            <a class="action-item">
              <div class="action-icon"><Promotion /></div>
              <div class="action-body">
                <div class="action-name">推送给客户</div>
                <div class="action-desc text-sm text-muted">推送签字/合同给客户</div>
              </div>
              <el-icon><ArrowRight /></el-icon>
            </a>
            <a class="action-item">
              <div class="action-icon"><Bell /></div>
              <div class="action-body">
                <div class="action-name">设置回访</div>
                <div class="action-desc text-sm text-muted">D+1 / D+7 / D+30 回访</div>
              </div>
              <el-icon><ArrowRight /></el-icon>
            </a>
          </div>
        </div>
      </div>
    </div>

    <!-- ============ 8 节点明细 ============ -->
    <div v-if="data && data.nodes && data.nodes.length" class="card">
      <h3 class="card-title">
        <span>📋 8 节点明细</span>
        <span class="text-sm text-muted">{{ data.nodes.length }} 个节点</span>
      </h3>
      <table class="tbl">
        <thead>
          <tr>
            <th>节点</th>
            <th>开始</th>
            <th>结束</th>
            <th>时长</th>
            <th>状态</th>
            <th>操作员</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="row in data.nodes" :key="row.nodeId">
            <td>
              <div class="mono text-sm font-bold">{{ row.nodeId }}</div>
              <div class="text-sm text-muted">{{ nodeName(row.nodeId) }}</div>
            </td>
            <td><span class="mono text-sm">{{ row.startUtc }}</span></td>
            <td><span class="mono text-sm">{{ row.endUtc || '-' }}</span></td>
            <td><span class="mono">{{ row.durationMs }}ms</span></td>
            <td>
              <span v-if="row.completed" class="state-badge success">已完成</span>
              <span v-else class="state-badge">未开始</span>
            </td>
            <td><span class="mono text-sm">{{ row.operatorId }}</span></td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- ============ 录像信息 ============ -->
    <div v-if="data && data.recordings && data.recordings.length" class="card">
      <h3 class="card-title">
        <span>🎥 录像信息</span>
        <span class="text-sm text-muted">{{ data.recordings.length }} 个录像</span>
      </h3>
      <div class="recording-grid">
        <div v-for="r in data.recordings" :key="r.recId" class="rec-card">
          <div class="rec-header">
            <div class="rec-icon">🎥</div>
            <div class="rec-id-section">
              <div class="rec-id mono">{{ r.recId }}</div>
              <div class="text-sm text-muted">{{ r.channel }} · {{ r.sellerType }}</div>
            </div>
          </div>
          <div class="rec-stats">
            <div class="rs-item">
              <div class="rs-label">时长</div>
              <div class="rs-value mono">{{ r.durationMs }}ms</div>
            </div>
            <div class="rs-item">
              <div class="rs-label">加密</div>
              <div class="rs-value">{{ r.encryption }}</div>
            </div>
            <div class="rs-item">
              <div class="rs-label">留存至</div>
              <div class="rs-value text-sm">{{ r.retentionUntil }}</div>
            </div>
            <div class="rs-item">
              <div class="rs-label">水印</div>
              <div class="rs-value">
                <span v-if="r.watermarkVisible" class="state-badge success">已开启</span>
                <span v-else class="state-badge">未开</span>
              </div>
            </div>
          </div>
          <div class="rec-actions">
            <button class="btn btn-ghost btn-sm">▶ 播放</button>
            <button class="btn btn-ghost btn-sm">⬇ 下载</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
/* ============ 搜索 ============ */
.search-bar { padding: 4px 0; }
.search-input-wrap {
  display: flex;
  align-items: center;
  gap: 0;
  background: var(--bg-2);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  padding: 4px 4px 4px 16px;
  transition: all 0.2s;
  &:focus-within {
    background: white;
    border-color: var(--accent);
    box-shadow: 0 0 0 3px rgba(192, 133, 82, 0.12);
  }
}
.search-icon { color: var(--ink-muted); font-size: 18px; }
.search-input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  font-size: 14px;
  padding: 10px 12px;
  font-family: 'JetBrains Mono', monospace;
  color: var(--ink);
  &::placeholder { color: var(--ink-muted); font-family: inherit; }
}
.search-btn {
  padding: 8px 20px;
  background: var(--primary-gradient);
  color: white;
  border: none;
  border-radius: var(--radius-sm);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 2px 4px rgba(30, 42, 71, 0.15);
  transition: all 0.2s;
  &:hover:not(:disabled) {
    box-shadow: var(--shadow-primary);
    transform: translateY(-1px);
  }
  &.loading { opacity: 0.7; cursor: wait; }
  &:disabled { opacity: 0.5; cursor: not-allowed; }
}

.recent-row {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  flex-wrap: wrap;
}
.recent-label {
  font-size: 11px;
  color: var(--ink-3);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  font-weight: 600;
}
.recent-chip {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 5px 10px;
  background: var(--bg-2);
  border: 1px solid var(--line);
  border-radius: 999px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s;
  &:hover {
    background: var(--bg-accent);
    border-color: var(--line-accent);
    transform: translateY(-1px);
  }
}

/* ============ 概览布局 ============ */
.overview-grid {
  display: grid;
  grid-template-columns: 1fr 320px;
  gap: 16px;
  margin-bottom: 16px;
}
@media (max-width: 1100px) { .overview-grid { grid-template-columns: 1fr; } }

.overview-main { padding: 24px 28px; }
.overview-side { display: flex; flex-direction: column; gap: 16px; }

/* ============ 进度 ============ */
.progress-section { margin: 16px 0 20px; }
.progress-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 12px;
  color: var(--ink-3);
  margin-bottom: 6px;
}
.progress-bar.large {
  height: 10px;
}

/* ============ 字段网格 ============ */
.field-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px 20px;
  margin-top: 8px;
}
@media (max-width: 800px) { .field-grid { grid-template-columns: 1fr 1fr; } }
@media (max-width: 500px) { .field-grid { grid-template-columns: 1fr; } }
.field {
  padding: 12px 14px;
  background: var(--bg-2);
  border-radius: var(--radius);
  border: 1px solid var(--line-2);
  transition: all 0.15s;
  &:hover {
    border-color: var(--line);
    background: white;
  }
}
.field-label {
  font-size: 10px;
  color: var(--ink-3);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  font-weight: 600;
  margin-bottom: 4px;
}
.field-value {
  font-size: 13px;
  color: var(--ink);
  font-weight: 500;
}

/* ============ 风险 pill ============ */
.risk-pill {
  display: inline-flex;
  align-items: center;
  padding: 2px 10px;
  font-size: 11px;
  font-weight: 700;
  border-radius: 999px;
  font-family: 'JetBrains Mono', monospace;
}
.risk-pill.C1, .risk-pill.R1 { background: var(--green-light); color: #047857; }
.risk-pill.C2, .risk-pill.R2 { background: var(--blue-light); color: #1d4ed8; }
.risk-pill.C3, .risk-pill.R3 { background: var(--orange-light); color: var(--orange); }
.risk-pill.C4, .risk-pill.R4 { background: #ffedd5; color: #c2410c; }
.risk-pill.C5, .risk-pill.R5 { background: var(--red-light); color: var(--red); }

/* ============ 快捷操作 ============ */
.action-list { display: flex; flex-direction: column; gap: 4px; }
.action-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-radius: var(--radius);
  cursor: pointer;
  text-decoration: none;
  color: inherit;
  transition: all 0.15s;
  &:hover {
    background: var(--bg-2);
    transform: translateX(2px);
    .action-icon { background: var(--bg-accent); color: var(--accent-2); }
  }
}
.action-icon {
  width: 36px; height: 36px;
  background: var(--bg-2);
  color: var(--ink-3);
  border-radius: var(--radius);
  display: flex; align-items: center; justify-content: center;
  font-size: 16px;
  flex-shrink: 0;
  transition: all 0.15s;
}
.action-body { flex: 1; min-width: 0; }
.action-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--ink);
}
.action-desc { margin-top: 1px; }

/* ============ 空状态 ============ */
.empty-state {
  text-align: center;
  padding: 60px 20px;
  color: var(--ink-3);
}
.empty-icon {
  font-size: 64px;
  color: var(--line);
  margin-bottom: 12px;
  display: flex;
  justify-content: center;
}
.empty-state h3 {
  margin: 0 0 8px;
  font-size: 16px;
  font-weight: 600;
  color: var(--ink-2);
}
.empty-state p {
  margin: 0;
  font-size: 13px;
}

/* ============ 录像卡 ============ */
.recording-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
  gap: 12px;
  margin-top: 8px;
}
.rec-card {
  background: var(--bg-2);
  border: 1px solid var(--line-2);
  border-radius: var(--radius);
  padding: 14px;
  transition: all 0.2s;
  &:hover {
    background: white;
    border-color: var(--line);
    box-shadow: var(--shadow-sm);
  }
}
.rec-header { display: flex; align-items: center; gap: 10px; margin-bottom: 12px; }
.rec-icon {
  width: 40px; height: 40px;
  background: var(--primary-gradient);
  color: white;
  border-radius: var(--radius);
  display: flex; align-items: center; justify-content: center;
  font-size: 20px;
  flex-shrink: 0;
}
.rec-id-section { flex: 1; min-width: 0; }
.rec-id {
  font-size: 12px;
  font-weight: 600;
  color: var(--ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.rec-stats {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  padding-top: 10px;
  border-top: 1px solid var(--line-2);
}
.rs-item { padding: 4px 0; }
.rs-label { font-size: 10px; color: var(--ink-3); text-transform: uppercase; }
.rs-value { font-size: 12px; font-weight: 600; color: var(--ink); margin-top: 2px; }
.rec-actions { display: flex; gap: 6px; margin-top: 10px; }

/* ============ 按钮 ============ */
.btn { display: inline-flex; align-items: center; gap: 4px; padding: 6px 12px; border: 1px solid transparent; border-radius: var(--radius-sm); font-size: 12px; font-weight: 500; cursor: pointer; transition: all 0.15s; }
.btn-ghost { background: white; color: var(--ink-2); border-color: var(--line); }
.btn-ghost:hover { background: var(--bg-2); color: var(--ink); }
.btn-sm { padding: 4px 10px; font-size: 11px; }
</style>
