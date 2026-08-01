<template>
  <div class="product-list">
    <div class="page-header">
      <h1 class="page-title">产品超市</h1>
      <p class="page-subtitle">智能匹配您的风险等级</p>
    </div>

    <div class="filter-bar">
      <div class="filter-tabs">
        <div
          v-for="t in types"
          :key="t.value"
          :class="['tab', activeType === t.value && 'active']"
          @click="activeType = t.value"
        >
          {{ t.label }}
        </div>
      </div>
      <div class="search-bar">
        <van-search
          v-model="searchKey"
          placeholder="搜索产品"
          shape="round"
          background="transparent"
        />
      </div>
    </div>

    <div class="product-list-content">
      <div
        v-for="p in filtered"
        :key="p.id"
        class="product-card"
        @click="$router.push(`/h5/product/${p.id}`)"
      >
        <div class="card-header">
          <div class="product-type-icon">{{ typeIcon(p.type) }}</div>
          <div class="header-meta">
            <div class="product-name">{{ p.name }}</div>
            <div class="product-id">{{ p.id }}</div>
          </div>
          <div :class="['risk-tag', `risk-${p.riskLevel}`]">
            {{ riskLabel(p.riskLevel) }}
          </div>
        </div>
        <div class="card-body">
          <div class="metric">
            <div class="m-value">{{ p.expectedYield }}</div>
            <div class="m-label">预期年化</div>
          </div>
          <div class="metric">
            <div class="m-value">{{ p.term }}</div>
            <div class="m-label">期限</div>
          </div>
          <div class="metric">
            <div class="m-value">¥{{ p.minAmount }}</div>
            <div class="m-label">起购</div>
          </div>
        </div>
        <div class="card-footer">
          <span class="hot" v-if="p.hot">🔥 热销</span>
          <span class="badge">{{ p.seller }}</span>
        </div>
      </div>

      <div v-if="!filtered.length" class="empty">
        <div class="empty-icon">📦</div>
        <p>暂无符合条件的产品</p>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'

const types = [
  { label: '全部', value: 'ALL' },
  { label: '银行理财', value: 'WEALTH' },
  { label: '基金', value: 'FUND' },
  { label: '保险', value: 'INSURANCE' }
]
const activeType = ref('ALL')
const searchKey = ref('')

const products = ref([
  { id: 'BNK-FIN-2026Q3-001', name: '稳赢系列 · 固收理财', type: 'WEALTH', riskLevel: 'R2', expectedYield: '3.6%', term: '180 天', minAmount: '1万', hot: true, seller: '工商银行' },
  { id: 'BNK-MIX-2026Q3-002', name: '混合策略 · 股债平衡', type: 'WEALTH', riskLevel: 'R3', expectedYield: '4.8%', term: '365 天', minAmount: '5万', seller: '建设银行' },
  { id: 'FND-STK-2026Q3-002', name: '稳进混合基金', type: 'FUND', riskLevel: 'R3', expectedYield: '5.2%', term: '1 年', minAmount: '1000', hot: true, seller: '华夏基金' },
  { id: 'FND-BOND-2026Q3-001', name: '稳健债基', type: 'FUND', riskLevel: 'R2', expectedYield: '3.2%', term: '90 天', minAmount: '1000', seller: '南方基金' },
  { id: 'LIC-INV-2026Q3-001', name: '稳健投资连结险', type: 'INSURANCE', riskLevel: 'P4', expectedYield: '6.0%', term: '5 年', minAmount: '1万', hot: true, seller: '中国人寿' }
])

const filtered = computed(() => products.value.filter(p => {
  const matchType = activeType.value === 'ALL' || p.type === activeType.value
  const matchKey = !searchKey.value || p.name.includes(searchKey.value) || p.id.includes(searchKey.value)
  return matchType && matchKey
}))

function typeIcon(t: string) {
  return ({ WEALTH: '🏦', FUND: '📈', INSURANCE: '🛡️' } as Record<string, string>)[t] || '📊'
}
function riskLabel(level: string) {
  return ({ R1: '低', R2: '中低', R3: '中', R4: '中高', R5: '高', P1: '低', P2: '中低', P3: '中', P4: '中高', P5: '高' } as Record<string, string>)[level] || level
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
  white-space: nowrap;
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
.search-bar {
  padding: 0 12px;
}

.product-list-content {
  padding: 12px;
  margin-top: -8px;
}
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
.header-meta { flex: 1; }
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
.m-value { font-size: 16px; font-weight: 700; color: var(--accent); font-family: 'JetBrains Mono', monospace; }
.m-label { font-size: 11px; color: var(--text-3); margin-top: 2px; }

.card-footer {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 8px;
  font-size: 11px;
  color: var(--text-3);
}
.hot { color: var(--accent); font-weight: 500; }
.badge {
  background: var(--cream);
  padding: 2px 8px;
  border-radius: 4px;
}

.empty { padding: 60px 20px; text-align: center; color: var(--text-3); }
</style>
