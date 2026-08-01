<template>
  <div class="product-detail">
    <div class="hero">
      <div class="type-icon">{{ typeIcon(product.type) }}</div>
      <h1>{{ product.name }}</h1>
      <div class="hero-meta">
        <span :class="['risk-tag', `risk-${product.riskLevel}`]">{{ riskLabel(product.riskLevel) }}风险</span>
        <span class="badge">{{ product.seller }}</span>
      </div>
    </div>

    <div class="metric-cards">
      <div class="m-card">
        <div class="m-value">{{ product.expectedYield }}</div>
        <div class="m-label">预期年化</div>
      </div>
      <div class="m-card">
        <div class="m-value">{{ product.term }}</div>
        <div class="m-label">产品期限</div>
      </div>
      <div class="m-card">
        <div class="m-value">¥{{ product.minAmount }}</div>
        <div class="m-label">起购金额</div>
      </div>
    </div>

    <div class="card section">
      <h3 class="card-title">📊 产品详情</h3>
      <ul class="detail-list">
        <li><span class="lbl">产品类型</span><span class="val">{{ productTypeLabel(product.type) }}</span></li>
        <li><span class="lbl">产品代码</span><span class="val">{{ product.id }}</span></li>
        <li><span class="lbl">风险等级</span><span class="val">{{ riskLabel(product.riskLevel) }}</span></li>
        <li><span class="lbl">起息日</span><span class="val">T+1 工作日</span></li>
        <li><span class="lbl">到期日</span><span class="val">{{ product.term }} 后</span></li>
        <li><span class="lbl">赎回规则</span><span class="val">持有 {{ product.term }} 后可赎回</span></li>
      </ul>
    </div>

    <div class="card section">
      <h3 class="card-title">⚠️ 风险提示</h3>
      <p class="warning-text">
        本产品为{{ riskLabel(product.riskLevel) }}风险投资，理财非存款，产品有风险，投资须谨慎。
        {{ product.type === 'INSURANCE' ? '投资连结型保险具有投资风险，请根据自身风险承受能力选择。' : '您应充分了解产品风险并承担相应损失。' }}
      </p>
    </div>

    <div class="card section">
      <h3 class="card-title">📝 购买须知</h3>
      <ol class="notice-list">
        <li>购买前需完成风险评估问卷</li>
        <li>需通过双录 (录音录像) 合规流程</li>
        <li>客户 vs 产品风险等级需匹配</li>
        <li>双录完成后等待签约确认</li>
        <li>{{ product.type === 'INSURANCE' ? '保险产品有 15 天犹豫期' : '产品有 24h 冷静期' }}</li>
      </ol>
    </div>

    <div class="action-bar">
      <div class="action-price">
        <div class="price-label">购买金额 (元)</div>
        <van-field
          v-model="amount"
          type="number"
          placeholder="请输入金额"
          class="amount-input"
        />
      </div>
      <van-button
        block
        round
        size="large"
        type="primary"
        :disabled="!canBuy"
        @click="onBuy"
      >
        立即购买
      </van-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showDialog, showToast } from 'vant'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const productId = route.params.id as string
const product = ref<any>({
  id: productId,
  name: '加载中...',
  type: 'WEALTH',
  riskLevel: 'R2',
  expectedYield: '-',
  term: '-',
  minAmount: '-',
  seller: '-'
})

const amount = ref('')

onMounted(() => {
  const products: any = {
    'BNK-FIN-2026Q3-001': { name: '稳赢系列 · 固收理财', type: 'WEALTH', riskLevel: 'R2', expectedYield: '3.6%', term: '180 天', minAmount: '1万', seller: '工商银行' },
    'FND-STK-2026Q3-002': { name: '稳进混合基金', type: 'FUND', riskLevel: 'R3', expectedYield: '5.2%', term: '1 年', minAmount: '1000', seller: '华夏基金' },
    'LIC-INV-2026Q3-001': { name: '稳健投资连结险', type: 'INSURANCE', riskLevel: 'P4', expectedYield: '6.0%', term: '5 年', minAmount: '1万', seller: '中国人寿' }
  }
  if (products[productId]) {
    product.value = { id: productId, ...products[productId] }
  }
})

const canBuy = computed(() => {
  const n = Number(amount.value)
  return n > 0
})

function typeIcon(t: string) {
  return ({ WEALTH: '🏦', FUND: '📈', INSURANCE: '🛡️' } as Record<string, string>)[t] || '📊'
}
function riskLabel(level: string) {
  return ({ R1: '低', R2: '中低', R3: '中', R4: '中高', R5: '高', P1: '低', P2: '中低', P3: '中', P4: '中高', P5: '高' } as Record<string, string>)[level] || level
}
function productTypeLabel(t: string) {
  return ({ WEALTH: '银行理财', FUND: '基金', INSURANCE: '保险' } as Record<string, string>)[t] || t
}

async function onBuy() {
  const n = Number(amount.value)
  if (!n || n <= 0) {
    showToast('请输入有效金额')
    return
  }
  try {
    await showDialog({
      title: '购买确认',
      message: `您将购买 ${product.value.name}，金额 ¥${n.toLocaleString()}，确认开始双录流程？`,
      showCancelButton: true,
      confirmButtonText: '开始双录',
      cancelButtonText: '再想想'
    })
    // 跳转到双录
    const tempBiz = `BIZ-MOBILE-${Date.now()}`
    sessionStorage.setItem('pendingBuy', JSON.stringify({
      productId: product.value.id,
      productName: product.value.name,
      productRiskLevel: product.value.riskLevel,
      productType: product.value.type,
      amount: n
    }))
    router.push(`/h5/order?productId=${product.value.id}&amount=${n}`)
  } catch {}
}
</script>

<style lang="scss" scoped>
.product-detail {
  min-height: 100vh;
  padding-bottom: 200px;
}

.hero {
  background: linear-gradient(135deg, #1e2a47 0%, #2c3a5c 100%);
  color: white;
  padding: 32px 16px;
  text-align: center;
}
.type-icon { font-size: 48px; margin-bottom: 12px; }
.hero h1 { font-size: 22px; margin: 0 0 12px; font-weight: 600; }
.hero-meta { display: flex; gap: 8px; justify-content: center; }
.risk-tag {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 4px;
  background: rgba(255,255,255,0.2);
  color: white;
}
.badge {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 4px;
  background: rgba(255,255,255,0.2);
  color: white;
}

.metric-cards {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1px;
  background: var(--border);
  margin: -20px 12px 0;
  border-radius: 12px;
  overflow: hidden;
  position: relative;
  box-shadow: 0 2px 8px rgba(0,0,0,0.08);
}
.m-card {
  background: white;
  padding: 16px 8px;
  text-align: center;
}
.m-value { font-size: 16px; font-weight: 700; color: var(--accent); font-family: 'JetBrains Mono', monospace; }
.m-label { font-size: 11px; color: var(--text-3); margin-top: 4px; }

.card { background: var(--card); border-radius: 12px; padding: 16px; margin: 12px; box-shadow: 0 1px 4px rgba(0,0,0,0.05); }
.section { margin-top: 0; }
.card-title { font-size: 15px; font-weight: 600; margin: 0 0 12px; }

.detail-list { list-style: none; padding: 0; margin: 0; }
.detail-list li {
  display: flex; justify-content: space-between;
  padding: 8px 0;
  border-bottom: 1px solid var(--border);
  font-size: 13px;
  &:last-child { border-bottom: none; }
}
.lbl { color: var(--text-3); }
.val { color: var(--text-1); font-weight: 500; }

.warning-text {
  font-size: 13px;
  color: var(--text-2);
  line-height: 1.6;
  margin: 0;
}

.notice-list {
  padding-left: 20px;
  margin: 0;
  font-size: 13px;
  color: var(--text-2);
  line-height: 1.8;
}

.action-bar {
  position: fixed;
  bottom: 0; left: 0; right: 0;
  background: white;
  padding: 12px 16px;
  border-top: 1px solid var(--border);
  box-shadow: 0 -2px 8px rgba(0,0,0,0.05);
  z-index: 100;
}
.action-price { margin-bottom: 8px; }
.price-label { font-size: 12px; color: var(--text-3); margin-bottom: 4px; }
.amount-input { padding: 8px; }
</style>
