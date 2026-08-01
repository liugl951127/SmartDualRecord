<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Lock, Connection, DataAnalysis, Refresh, CircleCheck, Warning, View, Search } from '@element-plus/icons-vue'

const apiBase = import.meta.env.VITE_API_BASE || ''

// ============ 状态 ============
const chains = ref<string[]>([])
const selectedChain = ref<string>('')
const chainEntries = ref<any[]>([])
const verifyResult = ref<any>(null)
const debugInfo = ref<any[]>([])
const lineageData = ref<any>(null)
const lineageBizId = ref('BNK20260801-900001')
const businessEntries = ref<any[]>([])
const activeTab = ref('chain')

// Demo
const demoChainId = ref('demo-' + Date.now())
const demoEntity = ref({ type: 'demo', id: 'item-1', bizId: '' })

// 加载
const loading = ref(false)

async function loadChains() {
  loading.value = true
  try {
    const res = await fetch(`${apiBase}/api/v1/integrity/chains`)
    chains.value = await res.json()
    if (chains.value.length && !selectedChain.value) {
      selectChain(chains.value[0])
    }
  } catch (e: any) {
    ElMessage.error('加载链失败: ' + e.message)
  } finally {
    loading.value = false
  }
}

async function selectChain(chainId: string) {
  selectedChain.value = chainId
  loading.value = true
  try {
    // 加载条目
    const eRes = await fetch(`${apiBase}/api/v1/integrity/chain/${chainId}`)
    chainEntries.value = await eRes.json()
    // 自动验证
    await verifyChain()
  } catch (e: any) {
    ElMessage.error('加载失败: ' + e.message)
  } finally {
    loading.value = false
  }
}

async function verifyChain() {
  if (!selectedChain.value) return
  try {
    const res = await fetch(`${apiBase}/api/v1/integrity/chain/${selectedChain.value}/verify`)
    verifyResult.value = await res.json()
  } catch (e: any) {
    ElMessage.error('验证失败: ' + e.message)
  }
}

async function loadLineage() {
  if (!lineageBizId.value) return
  loading.value = true
  try {
    const [lRes, eRes] = await Promise.all([
      fetch(`${apiBase}/api/v1/integrity/lineage/business/${lineageBizId.value}`),
      fetch(`${apiBase}/api/v1/integrity/business/${lineageBizId.value}/entries?limit=20`)
    ])
    lineageData.value = await lRes.json()
    businessEntries.value = await eRes.json()
  } catch (e: any) {
    ElMessage.error('血缘加载失败: ' + e.message)
  } finally {
    loading.value = false
  }
}

async function appendDemo() {
  if (!demoEntity.value.type || !demoEntity.value.id) {
    ElMessage.warning('请填写实体类型和 ID')
    return
  }
  try {
    await fetch(`${apiBase}/api/v1/integrity/demo/append`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        chainId: demoChainId.value,
        entityType: demoEntity.value.type,
        entityId: demoEntity.value.id,
        businessId: demoEntity.value.bizId || undefined,
        actorId: 'manual-test',
        actorRole: 'ADMIN',
        data: { msg: '手动添加审计条目', at: new Date().toISOString() }
      })
    })
    ElMessage.success('✓ 已添加, chain_id=' + demoChainId.value)
    loadChains()
  } catch (e: any) {
    ElMessage.error('添加失败: ' + e.message)
  }
}

async function debugChain() {
  if (!selectedChain.value) return
  try {
    const res = await fetch(`${apiBase}/api/v1/integrity/chain/${selectedChain.value}/debug`)
    debugInfo.value = await res.json()
    ElMessage.info('调试信息已加载, 共 ' + debugInfo.value.length + ' 条')
  } catch (e: any) {
    ElMessage.error('调试失败: ' + e.message)
  }
}

// 状态颜色
const statusColor = computed(() => {
  if (!verifyResult.value) return ''
  return verifyResult.value.status === 'PASSED' ? 'success' : 'danger'
})

// 血缘类型统计
const countByType = computed(() => {
  if (!lineageData.value || !lineageData.value.nodes) return {} as Record<string, number>
  const result: Record<string, number> = {}
  for (const n of lineageData.value.nodes) {
    const t = n.type || 'unknown'
    result[t] = (result[t] || 0) + 1
  }
  return result
})

onMounted(() => {
  loadChains()
  loadLineage()
})
</script>

<template>
  <div class="integrity-page">
    <!-- 顶部统计 -->
    <div class="grid grid-4 mb-16">
      <div class="stat-box">
        <div class="stat-icon primary"><Connection /></div>
        <div class="stat-value mono">{{ chains.length }}</div>
        <div class="stat-label">审计链总数</div>
      </div>
      <div class="stat-box" v-if="verifyResult">
        <div :class="['stat-icon', statusColor === 'success' ? 'success' : 'danger']">
          <CircleCheck v-if="statusColor === 'success'" />
          <Warning v-else />
        </div>
        <div class="stat-value mono">{{ verifyResult.passed || 0 }}</div>
        <div class="stat-label">验证通过</div>
      </div>
      <div class="stat-box" v-if="verifyResult">
        <div :class="['stat-icon', verifyResult.failed > 0 ? 'danger' : 'success']">
          <Warning />
        </div>
        <div class="stat-value mono" :class="verifyResult.failed > 0 ? 'text-danger' : 'text-success'">
          {{ verifyResult.failed || 0 }}
        </div>
        <div class="stat-label">篡改检测</div>
      </div>
      <div class="stat-box" v-if="lineageData">
        <div class="stat-icon accent"><DataAnalysis /></div>
        <div class="stat-value mono">{{ lineageData.nodeCount || 0 }}</div>
        <div class="stat-label">血缘节点</div>
      </div>
    </div>

    <el-tabs v-model="activeTab" type="border-card" class="integrity-tabs">
      <!-- ============ Tab 1: 审计链 ============ -->
      <el-tab-pane label="审计链" name="chain">
        <div class="grid-2-1">
          <div>
            <!-- 链选择 + 详情 -->
            <div class="card">
              <h3 class="card-title">
                <span>🔗 审计链列表</span>
                <button class="btn btn-ghost btn-sm" @click="loadChains">
                  <el-icon><Refresh /></el-icon>刷新
                </button>
              </h3>
              <div class="chain-list">
                <div
                  v-for="c in chains"
                  :key="c"
                  :class="['chain-item', selectedChain === c && 'selected']"
                  @click="selectChain(c)"
                >
                  <span class="chain-icon">🔗</span>
                  <div class="chain-info">
                    <div class="chain-name mono">{{ c }}</div>
                    <div class="chain-meta text-sm text-muted">
                      {{ c.startsWith('biz-') ? '业务链' : '系统链' }}
                    </div>
                  </div>
                </div>
                <div v-if="!chains.length" class="empty">
                  <div class="empty-icon">🔗</div>
                  <p class="empty-text">还没有审计链, 创建一笔业务或手动添加</p>
                </div>
              </div>
            </div>

            <!-- 验证结果 -->
            <div v-if="verifyResult" class="card mt-12">
              <h3 class="card-title">
                <span>✅ 验证结果</span>
                <span :class="['state-badge', statusColor === 'success' ? 'success' : 'danger']">
                  {{ verifyResult.status }}
                </span>
              </h3>
              <div class="verify-grid">
                <div class="verify-item">
                  <div class="vi-label">链 ID</div>
                  <div class="vi-value mono">{{ verifyResult.chainId }}</div>
                </div>
                <div class="verify-item">
                  <div class="vi-label">总条目</div>
                  <div class="vi-value mono">{{ verifyResult.totalEntries }}</div>
                </div>
                <div class="verify-item">
                  <div class="vi-label">通过</div>
                  <div class="vi-value mono text-success">{{ verifyResult.passed || 0 }}</div>
                </div>
                <div class="verify-item">
                  <div class="vi-label">失败</div>
                  <div class="vi-value mono" :class="verifyResult.failed > 0 ? 'text-danger' : ''">
                    {{ verifyResult.failed || 0 }}
                  </div>
                </div>
                <div class="verify-item">
                  <div class="vi-label">耗时</div>
                  <div class="vi-value mono">{{ verifyResult.durationMs }}ms</div>
                </div>
                <div class="verify-item">
                  <div class="vi-label">完成时间</div>
                  <div class="vi-value mono text-sm">{{ verifyResult.finishedAt }}</div>
                </div>
              </div>

              <div v-if="verifyResult.brokenLinks && verifyResult.brokenLinks.length" class="broken-list mt-12">
                <h4>🔓 检测到的篡改 ({{ verifyResult.brokenLinks.length }})</h4>
                <div v-for="(b, i) in verifyResult.brokenLinks" :key="i" class="broken-item">
                  <span class="state-badge danger">seq #{{ b.sequenceNo }}</span>
                  <span class="reason">{{ b.reason }}</span>
                  <span v-if="b.expected" class="hash text-sm text-muted">
                    expected: <code>{{ b.expected }}</code>
                  </span>
                  <span v-if="b.actual" class="hash text-sm text-muted">
                    actual: <code>{{ b.actual }}</code>
                  </span>
                </div>
              </div>
              <div v-else class="all-good mt-12">
                <el-icon :size="20" color="#10b981"><CircleCheck /></el-icon>
                <span>所有条目完整, 链未被篡改</span>
              </div>
            </div>
          </div>

          <!-- 审计条目详情 -->
          <div>
            <div class="card">
              <h3 class="card-title">
                <span>📋 审计条目 ({{ chainEntries.length }})</span>
                <button class="btn btn-ghost btn-sm" @click="debugChain" :disabled="!selectedChain">
                  <el-icon><View /></el-icon>详细调试
                </button>
              </h3>
              <div v-if="!chainEntries.length" class="empty">
                <div class="empty-icon">📋</div>
                <p class="empty-text">选择一条审计链查看</p>
              </div>
              <div v-else class="entries-list">
                <div v-for="e in chainEntries.slice(0, 15)" :key="e.id" class="entry-item">
                  <div class="entry-seq">#{{ e.sequenceNo }}</div>
                  <div class="entry-body">
                    <div class="entry-row">
                      <span class="state-badge primary">{{ e.operationType }}</span>
                      <span class="entry-type">{{ e.entityType }}</span>
                      <span class="entry-eid mono text-sm text-muted">{{ e.entityId }}</span>
                    </div>
                    <div class="entry-hash mono text-sm">
                      hash: <code>{{ e.chainHash?.substring(0, 24) }}...</code>
                    </div>
                    <div class="entry-meta text-sm text-muted">
                      {{ e.serverNode }} · {{ e.signedAt }}
                    </div>
                  </div>
                </div>
                <div v-if="chainEntries.length > 15" class="text-sm text-muted text-center mt-8">
                  还有 {{ chainEntries.length - 15 }} 条未显示
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 手动添加 -->
        <div class="card mt-16">
          <h3 class="card-title">➕ 手动添加审计条目 (测试用)</h3>
          <div class="demo-form">
            <div class="df-row">
              <label>链 ID</label>
              <input v-model="demoChainId" class="input" placeholder="demo-xxx" />
            </div>
            <div class="df-row">
              <label>实体类型</label>
              <input v-model="demoEntity.type" class="input" placeholder="recording / risk / event ..." />
            </div>
            <div class="df-row">
              <label>实体 ID</label>
              <input v-model="demoEntity.id" class="input" placeholder="item-1" />
            </div>
            <div class="df-row">
              <label>业务 ID (可选)</label>
              <input v-model="demoEntity.bizId" class="input" placeholder="BNK..." />
            </div>
            <button class="btn btn-accent" @click="appendDemo">+ 添加</button>
          </div>
        </div>
      </el-tab-pane>

      <!-- ============ Tab 2: 血缘图 ============ -->
      <el-tab-pane label="数据血缘" name="lineage">
        <div class="card">
          <h3 class="card-title">
            <span>🌐 业务数据血缘图 (DAG)</span>
            <div class="search-bar">
              <input
                v-model="lineageBizId"
                class="input"
                placeholder="业务 ID"
                @keyup.enter="loadLineage"
              />
              <button class="btn btn-accent" @click="loadLineage">
                <el-icon><Search /></el-icon>查询
              </button>
            </div>
          </h3>

          <div v-if="lineageData && !lineageData.error" class="lineage-result">
            <div class="lineage-stats">
              <span class="ls-item">
                <span class="ls-label">节点</span>
                <span class="ls-val mono">{{ lineageData.nodeCount }}</span>
              </span>
              <span class="ls-item">
                <span class="ls-label">边</span>
                <span class="ls-val mono">{{ lineageData.edgeCount }}</span>
              </span>
              <span class="ls-item">
                <span class="ls-label">查询</span>
                <span class="ls-val mono">{{ lineageData.queryMs }}ms</span>
              </span>
            </div>

            <!-- 类型分布 -->
            <div class="lineage-types">
              <div v-for="(count, type) in countByType" :key="type" class="lt-chip" :class="`lt-${type}`">
                {{ type }}: <b class="mono">{{ count }}</b>
              </div>
            </div>

            <!-- 节点列表 -->
            <div class="lineage-nodes">
              <h4>节点明细</h4>
              <div class="ln-grid">
                <div
                  v-for="n in lineageData.nodes"
                  :key="n.id"
                  :class="['ln-card', `ln-${n.type}`]"
                >
                  <div class="ln-icon">{{ nodeIcon(n.type) }}</div>
                  <div class="ln-body">
                    <div class="ln-type text-sm text-muted">{{ n.type }}</div>
                    <div class="ln-label">{{ n.label }}</div>
                    <div v-if="n.id" class="ln-id mono text-sm text-muted">{{ n.id.substring(0, 30) }}</div>
                  </div>
                </div>
              </div>
            </div>

            <!-- 边列表 -->
            <div class="lineage-edges mt-12">
              <h4>关系 ({{ lineageData.edgeCount }})</h4>
              <div class="le-list">
                <div v-for="(e, i) in lineageData.edges.slice(0, 30)" :key="i" class="le-item">
                  <span class="le-from mono text-sm">{{ e.from }}</span>
                  <span class="le-arrow">→</span>
                  <span class="le-rel">{{ e.relation }}</span>
                  <span class="le-arrow">→</span>
                  <span class="le-to mono text-sm">{{ e.to }}</span>
                </div>
                <div v-if="lineageData.edges.length > 30" class="text-sm text-muted">
                  还有 {{ lineageData.edges.length - 30 }} 条...
                </div>
              </div>
            </div>
          </div>

          <div v-else-if="lineageData && lineageData.error" class="empty">
            <div class="empty-icon">⚠️</div>
            <p class="empty-text">{{ lineageData.error }}</p>
          </div>
        </div>
      </el-tab-pane>

      <!-- ============ Tab 3: 业务条目 ============ -->
      <el-tab-pane label="业务条目" name="business">
        <div class="card">
          <h3 class="card-title">📜 业务 {{ lineageBizId }} 的审计条目</h3>
          <div v-if="!businessEntries.length" class="empty">
            <p class="empty-text">无审计条目</p>
          </div>
          <table v-else class="tbl">
            <thead>
              <tr>
                <th>Seq</th>
                <th>操作</th>
                <th>实体</th>
                <th>Hash</th>
                <th>时间</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="e in businessEntries" :key="e.id">
                <td class="mono font-bold">#{{ e.sequenceNo }}</td>
                <td><span class="state-badge primary">{{ e.operationType }}</span></td>
                <td>
                  <div class="text-sm font-bold">{{ e.entityType }}</div>
                  <div class="mono text-sm text-muted">{{ e.entityId?.substring(0, 20) }}</div>
                </td>
                <td><code class="text-sm">{{ e.chainHash?.substring(0, 16) }}...</code></td>
                <td><span class="mono text-sm">{{ e.signedAt }}</span></td>
              </tr>
            </tbody>
          </table>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script lang="ts">
// ============ 辅助函数 ============
export default {
  methods: {
    nodeIcon(type: string): string {
      const m: any = {
        business: '💼',
        recording: '🎥',
        node: '🔹',
        risk: '⚠️',
        qa: '🤖',
        script: '📋',
        event: '📡',
        advisor: '💎'
      }
      return m[type] || '📦'
    }
  }
}
</script>

<style lang="scss" scoped>
// 主题色直接引用全局 CSS 变量 (来自 /src/style.css)

.mb-16 { margin-bottom: 16px; }
.mt-12 { margin-top: 12px; }
.mt-16 { margin-top: 16px; }
.mt-8 { margin-top: 8px; }
.text-danger { color: var(--red); }
.text-success { color: var(--success); }
.text-muted { color: var(--text-3); }
.font-bold { font-weight: 700; }

.integrity-page {
  min-height: 100%;
}

.integrity-tabs {
  :deep(.el-tabs__content) {
    padding: 20px 0 0;
  }
}

.grid-2-1 {
  display: grid;
  grid-template-columns: 1fr 1.3fr;
  gap: 16px;
  @media (max-width: 1200px) { grid-template-columns: 1fr; }
}

// ============ 链列表 ============
.chain-list { max-height: 400px; overflow-y: auto; }
.chain-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border: 1px solid var(--line-2);
  border-radius: var(--radius);
  margin-bottom: 6px;
  cursor: pointer;
  transition: all 0.15s;
  &:hover {
    border-color: var(--accent);
    background: var(--bg-accent);
    transform: translateX(2px);
  }
  &.selected {
    background: var(--bg-accent);
    border-color: var(--accent);
    box-shadow: 0 0 0 1px var(--accent);
  }
}
.chain-icon { font-size: 20px; }
.chain-info { flex: 1; min-width: 0; }
.chain-name { font-size: 13px; font-weight: 600; }
.chain-meta { margin-top: 2px; }

// ============ 验证结果 ============
.verify-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  @media (max-width: 800px) { grid-template-columns: 1fr 1fr; }
}
.vi-item {
  padding: 10px 12px;
  background: var(--bg-2);
  border-radius: var(--radius);
  border: 1px solid var(--line-2);
}
.vi-label {
  font-size: 10px;
  color: var(--text-3);
  text-transform: uppercase;
  font-weight: 600;
  margin-bottom: 4px;
}
.vi-value { font-size: 14px; font-weight: 600; color: var(--text-1); }

.broken-list {
  background: rgba(239, 68, 68, 0.05);
  border: 1px solid rgba(239, 68, 68, 0.2);
  border-radius: var(--radius);
  padding: 12px;
  h4 { margin: 0 0 8px; font-size: 13px; color: var(--red); }
}
.broken-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 0;
  font-size: 12px;
  code { background: white; padding: 1px 4px; border-radius: 3px; }
  .reason { font-weight: 600; color: var(--red); }
  .hash code { font-family: 'JetBrains Mono', monospace; }
}

.all-good {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px;
  background: var(--success-light);
  color: #047857;
  border-radius: var(--radius);
  font-weight: 600;
  font-size: 13px;
}

// ============ 审计条目 ============
.entries-list { max-height: 600px; overflow-y: auto; }
.entry-item {
  display: flex;
  gap: 12px;
  padding: 12px 14px;
  background: var(--bg-2);
  border: 1px solid var(--line-2);
  border-radius: var(--radius);
  margin-bottom: 6px;
  transition: all 0.15s;
  &:hover {
    background: white;
    border-color: var(--line);
    box-shadow: var(--shadow-sm);
  }
}
.entry-seq {
  width: 32px; height: 32px;
  background: var(--primary);
  color: white;
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-size: 11px;
  font-weight: 700;
  font-family: 'JetBrains Mono', monospace;
  flex-shrink: 0;
}
.entry-body { flex: 1; min-width: 0; }
.entry-row { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; }
.entry-type { font-size: 13px; font-weight: 600; }
.entry-eid { }
.entry-hash { margin-bottom: 2px; code { background: white; padding: 1px 6px; border-radius: 3px; } }
.entry-meta { }

// ============ Demo 表单 ============
.demo-form {
  display: grid;
  grid-template-columns: repeat(4, 1fr) auto;
  gap: 12px;
  align-items: end;
  @media (max-width: 800px) { grid-template-columns: 1fr 1fr; }
}
.df-row { display: flex; flex-direction: column; gap: 4px; }
.df-row label { font-size: 11px; color: var(--text-3); font-weight: 600; }

// ============ 血缘图 ============
.lineage-result { padding: 4px 0; }
.lineage-stats {
  display: flex;
  gap: 24px;
  padding: 14px 18px;
  background: linear-gradient(135deg, var(--bg-accent) 0%, white 100%);
  border: 1px solid var(--line-accent);
  border-radius: var(--radius);
  margin-bottom: 16px;
}
.ls-item { display: flex; flex-direction: column; gap: 2px; }
.ls-label { font-size: 11px; color: var(--text-3); text-transform: uppercase; font-weight: 600; }
.ls-val { font-size: 20px; font-weight: 700; color: var(--accent-2); }

.lineage-types {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 16px;
}
.lt-chip {
  padding: 4px 12px;
  background: var(--bg-2);
  border: 1px solid var(--line);
  border-radius: 999px;
  font-size: 11px;
  color: var(--text-2);
  &.lt-business { border-color: var(--primary); color: var(--primary); }
  &.lt-recording { border-color: var(--accent); color: var(--accent-2); }
  &.lt-node { border-color: var(--success); color: #047857; }
  &.lt-risk { border-color: var(--warning); color: var(--orange); }
  &.lt-event { border-color: var(--info); color: #1d4ed8; }
}

.lineage-nodes h4, .lineage-edges h4 {
  font-size: 13px;
  margin: 0 0 10px;
  color: var(--text-1);
}
.ln-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 8px;
}
.ln-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  background: var(--bg-2);
  border: 1px solid var(--line-2);
  border-radius: var(--radius);
  transition: all 0.15s;
  &:hover {
    background: white;
    border-color: var(--line);
    transform: translateY(-1px);
    box-shadow: var(--shadow-sm);
  }
  &.ln-business { border-left: 3px solid var(--primary); }
  &.ln-recording { border-left: 3px solid var(--accent); }
  &.ln-node { border-left: 3px solid var(--success); }
  &.ln-risk { border-left: 3px solid var(--warning); }
  &.ln-event { border-left: 3px solid var(--info); }
}
.ln-icon { font-size: 24px; }
.ln-body { flex: 1; min-width: 0; }
.ln-type { text-transform: uppercase; letter-spacing: 0.5px; }
.ln-label { font-size: 13px; font-weight: 600; color: var(--text-1); overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.ln-id { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }

.le-list { max-height: 300px; overflow-y: auto; }
.le-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  font-size: 12px;
  border-bottom: 1px solid var(--line-2);
  &:last-child { border-bottom: none; }
  code { background: var(--bg-2); padding: 1px 4px; border-radius: 3px; }
}
.le-from, .le-to { color: var(--text-3); }
.le-rel {
  background: var(--bg-accent);
  color: var(--accent-2);
  padding: 1px 8px;
  border-radius: 999px;
  font-size: 10px;
  font-weight: 700;
}
.le-arrow { color: var(--text-3); }

.search-bar { display: flex; gap: 8px; align-items: center; }
.search-bar .input { width: 240px; }
</style>
