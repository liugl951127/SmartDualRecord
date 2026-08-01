<template>
  <div class="product-list">
    <div class="page-header">
      <h1 class="page-title">产品超市</h1>
      <p class="page-subtitle">从真实话术库加载 · 共 {{ products.length }} 个产品</p>
    </div>

    <div class="filter-bar">
      <div class="filter-tabs">
        <div
          v-for="t in types"
          :key="t.value"
          :class="['tab', activeType === t.value && 'active']"
          @click="activeType = t.value"
        >
          {{ t.label }} ({{ countByType(t.value) }})
        </div>
      </div>
      <van-search
        v-model="searchKey"
        placeholder="搜索产品名称 / 代码"
        shape="round"
        background="transparent"
      />
    </div>

    <div v-if="loading" class="loading-state">加载中...</div>
    <div v-else-if="!filtered.length" class="empty">
      <div class="empty-icon">📦</div>
      <p>暂无符合条件的产品</p>
    </div>

    <div v-else class="product-list-content">
      <div
        v-for="p in filtered"
        :key="p.productId"
        class="product-card"
        @click="$router.push(`/h5/product/${p.productId}`)"
      >
        <div class="card-header">
          <div class="product-type-icon">{{ typeIcon(p.productType) }}</div>
          <div class="header-meta">
            <div class="product-name">{{ getName(p) }}</div>
            <div class="product-id">{{ p.productId }} · v{{ p.version }}</div>
          </div>
          <div :class="['risk-tag', `risk-${p.riskLevel}`]">
            {{ riskLabel(p.riskLevel) }}
          </div>
        </div>
        <div class="card-body">
          <div class="metric">
            <div class="m-value">{{ getYield(p.riskLevel) }}</div>
            <div class="m-label">预期年化</div>
          </div>
          <div class="metric">
            <div class="m-value">{{ getTerm(p.productType) }}</div>
            <div class="m-label">期限</div>
          </div>
          <div class="metric">
            <div class="m-value">{{ p.status }}</div>
            <div class="m-label">状态</div>
          </div>
        </div>
        <div class="card-footer">
          <span class="hash">🔒 SHA256: {{ p.contentHash?.substring(0, 12) }}...</span>
          <span v-if="p.status === 'APPROVED' || p.status === 'FROZEN'" class="badge">{{ p.status }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { scriptApi } from '@/api'

const types = [
  { label: '全部', value: 'ALL' },
  { label: '银行理财', value: 'WEALTH' },
  { label: '基金', value: 'FUND' },
  { label: '保险', value: 'INSURANCE' }
]
const activeType = ref('ALL')
const searchKey = ref('')
const products = ref<any[]>([])
const loading = ref(true)

onMounted(async () => {
  await loadProducts()
})

async function loadProducts() {
  loading.value = true
  try {
    // 真实 API: 从后端 DB 话术表读取所有产品
    const res: any = await scriptApi.listDbTemplates()
    products.value = Array.isArray(res) ? res : []
  } catch (e) {
    products.value = []
  } finally {
    loading.value = false
  }
}

const filtered = computed(() => {
  return products.value.filter(p => {
    const matchType = activeType.value === 'ALL' || p.productType === activeType.value
    const matchKey = !searchKey.value ||
                     (p.productId || '').toLowerCase().includes(searchKey.value.toLowerCase()) ||
                     (p.productType || '').toLowerCase().includes(searchKey.value.toLowerCase())
    return matchType && matchKey
  })
})

function countByType(t: string) {
  if (t === 'ALL') return products.value.length
  return products.value.filter(p => p.productType === t).length
}

function typeIcon(t: string) {
  return ({ WEALTH: '🏦', FUND: '📈', INSURANCE: '🛡️' } as Record<string, string>)[t] || '📊'
}
function riskLabel(level: string) {
  return ({ R1: '低', R2: '中低', R3: '中', R4: '中高', R5: '高', P1: '低', P2: '中低', P3: '中', P4: '中高', P5: '高' } as Record<string, string>)[level] || level
}
function getName(p: any) {
  return p.productType === 'INSURANCE' ? '投资连结险' :
         p.productType === 'FUND' ? '混合基金' :
         p.productType === 'WEALTH' ? '银行理财' : '金融产品'
}
function getYield(risk: string) {
  const map: any = { R1: '2.8%', R2: '3.6%', R3: '4.8%', R4: '5.5%', R5: '6.5%', P1: '2.5%', P2: '3.0%', P3: '4.5%', P4: '6.0%', P5: '7.5%' }
  return map[risk] || '-'
}
function getTerm(type: string) {
  return type === 'INSURANCE' ? '5 年' : type === 'FUND' ? '1 年' : '180 天'
}
</script>

<style lang="scss" scoped>
.product-list { min-height: 100vh; }

.filter-bar {
  background: var(--primary);
  padding: 0 0 12px;
  color: white;
}
.filter-tabs {
  display: flex;
  padding: 0 12px;
  margin-bottom: 8px;
  overflow-x: auto;
}
.tab {
  padding: 8px 14px;
  margin-right: 4px;
  font-size: 13px;
  border-radius: 16px;
  background: rgba(255,255,255,0.1);
  color: rgba(255,255,255,0.8);
  flex-shrink: 0;
  cursor: pointer;
  &.active {
    background: var(--accent);
    color: white;
  }
}
.filter-bar :deep(.van-search) { padding: 0 12px; }

.loading-state { padding: 60px 20px; text-align: center; color: var(--text-3); }
.empty { padding: 80px 20px; text-align: center; color: var(--text-3); }
.empty-icon { font-size: 48px; margin-bottom: 8px; opacity: 0.4; }

.product-list-content { padding: 12px; }
.product-card {
  background: var(--card);
  border-radius: 12px;
  padding: 16px;
  margin-bottom: 12px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.05);
}
.card-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.product-type-icon {
  width: 40px; height: 40px;
  background: var(--cream);
  border-radius: 8px;
  display: flex; align-items: center; justify-content: center;
  font-size: 20px;
}
.header-meta { flex: 1; min-width: 0; }
.product-name { font-size: 15px; font-weight: 600; }
.product-id { font-size: 11px; color: var(--text-3); margin-top: 2px; font-family: monospace; }
.risk-tag {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 4px;
  font-weight: 500;
}
.risk-R1, .risk-P1, .risk-R2, .risk-P2 { background: rgba(7,193,96,0.1); color: var(--success); }
.risk-R3, .risk-P3, .risk-R4, .risk-P4 { background: rgba(255,151,106,0.1); color: var(--warning); }
.risk-R5, .risk-P5 { background: rgba(238,10,36,0.1); color: var(--danger); }

.card-body {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  padding: 12px 0;
  border-top: 1px solid var(--border);
  border-bottom: 1px solid var(--border);
}
.metric { text-align: center; }
.m-value { font-size: 14px; font-weight: 700; color: var(--accent); font-family: 'JetBrains Mono', monospace; }
.m-label { font-size: 11px; color: var(--text-3); margin-top: 2px; }

.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-top: 8px;
  font-size: 11px;
  color: var(--text-3);
}
.hash { font-family: monospace; }
.badge {
  background: rgba(7,193,96,0.1);
  color: var(--success);
  padding: 2px 8px;
  border-radius: 4px;
  font-weight: 500;
}
</style>
