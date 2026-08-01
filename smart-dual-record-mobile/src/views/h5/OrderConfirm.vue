<template>
  <div class="order-confirm">
    <div class="page-header">
      <h1 class="page-title">订单确认</h1>
      <p class="page-subtitle">请确认购买信息，开始双录流程</p>
    </div>

    <div class="card section">
      <div class="product-summary">
        <div class="icon">📦</div>
        <div class="meta">
          <div class="name">{{ product.name }}</div>
          <div class="info">{{ product.id }} · {{ product.term }}</div>
        </div>
      </div>
    </div>

    <div class="card section">
      <h3 class="card-title">购买详情</h3>
      <ul class="detail-list">
        <li><span class="lbl">产品类型</span><span class="val">{{ productTypeLabel(product.type) }}</span></li>
        <li><span class="lbl">风险等级</span><span class="val">{{ riskLabel(product.riskLevel) }} ({{ product.riskLevel }})</span></li>
        <li><span class="lbl">购买金额</span><span class="val amount">¥ {{ Number(amount).toLocaleString() }}</span></li>
        <li><span class="lbl">预期收益</span><span class="val">¥ {{ expectedIncome }}</span></li>
        <li><span class="lbl">产品期限</span><span class="val">{{ product.term }}</span></li>
      </ul>
    </div>

    <div class="card section">
      <h3 class="card-title">⚠️ 风险匹配检查</h3>
      <div class="risk-match">
        <div class="match-item">
          <div class="match-label">您的风险等级</div>
          <div class="match-value">C3 平衡型</div>
        </div>
        <div class="match-arrow">⇋</div>
        <div class="match-item">
          <div class="match-label">产品风险等级</div>
          <div class="match-value">{{ riskLabel(product.riskLevel) }} ({{ product.riskLevel }})</div>
        </div>
      </div>
      <div :class="['match-result', matchResult.cls]">
        {{ matchResult.text }}
      </div>
    </div>

    <div class="card section">
      <h3 class="card-title">📋 购买流程</h3>
      <ol class="flow-list">
        <li><span class="step">1</span><span>确认订单并开始双录</span></li>
        <li><span class="step">2</span><span>8 节点标准流程 (约 15 分钟)</span></li>
        <li><span class="step">3</span><span>AI 智能质检 + 人工复核</span></li>
        <li><span class="step">4</span><span>电子签名 + 合同生效</span></li>
        <li><span class="step">5</span><span>15 天犹豫期 · 3 次回访</span></li>
      </ol>
    </div>

    <div class="agree">
      <van-checkbox v-model="agreed" shape="square">
        我已阅读并同意 <a>《产品说明书》</a> <a>《风险揭示书》</a> <a>《客户权益须知》</a>
      </van-checkbox>
    </div>

    <div class="action-bar">
      <van-button block round size="large" type="primary" :disabled="!agreed" @click="onConfirm">
        开始双录
      </van-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'

const route = useRoute()
const router = useRouter()
const productId = route.query.productId as string
const amount = Number(route.query.amount) || 0

const product = ref<any>({
  id: productId,
  name: '加载中...',
  type: 'WEALTH',
  riskLevel: 'R2',
  term: '-'
})

const agreed = ref(false)

// 模拟产品数据
const products: any = {
  'BNK-FIN-2026Q3-001': { name: '稳赢系列 · 固收理财', type: 'WEALTH', riskLevel: 'R2', term: '180 天' },
  'FND-STK-2026Q3-002': { name: '稳进混合基金', type: 'FUND', riskLevel: 'R3', term: '1 年' },
  'LIC-INV-2026Q3-001': { name: '稳健投资连结险', type: 'INSURANCE', riskLevel: 'P4', term: '5 年' }
}
if (products[productId]) {
  product.value = { id: productId, ...products[productId] }
}

const expectedIncome = computed(() => {
  const yieldMap: any = { R2: 0.036, R3: 0.052, R4: 0.06, P4: 0.06 }
  const y = yieldMap[product.value.riskLevel] || 0.04
  const termMap: any = { '180 天': 0.5, '1 年': 1, '5 年': 5 }
  const t = termMap[product.value.term] || 1
  return Math.round(amount * y * t).toLocaleString()
})

const matchResult = computed(() => {
  // C3 可以买 R3 及以下
  const userLevel = 3
  const productLevel = parseInt(product.value.riskLevel.replace(/[RP]/, ''))
  if (productLevel <= userLevel) {
    return { cls: 'ok', text: '✓ 您的风险等级与产品匹配, 可以购买' }
  } else if (productLevel === userLevel + 1) {
    return { cls: 'warn', text: '⚠ 风险略高, 建议先完成风险评估' }
  }
  return { cls: 'danger', text: '✗ 风险等级不匹配, 不建议购买' }
})

function riskLabel(level: string) {
  return ({ R1: '低', R2: '中低', R3: '中', R4: '中高', R5: '高', P1: '低', P2: '中低', P3: '中', P4: '中高', P5: '高' } as Record<string, string>)[level] || level
}
function productTypeLabel(t: string) {
  return ({ WEALTH: '银行理财', FUND: '基金', INSURANCE: '保险' } as Record<string, string>)[t] || t
}

function onConfirm() {
  if (!agreed.value) {
    showToast('请先同意相关协议')
    return
  }
  // 创建业务并跳转双录
  const bid = `BIZ-${Date.now()}`
  router.push(`/h5/record/${bid}?productId=${productId}&amount=${amount}`)
}
</script>

<style lang="scss" scoped>
.order-confirm { min-height: 100vh; padding-bottom: 100px; }

.card { background: var(--card); border-radius: 12px; padding: 16px; margin: 12px; box-shadow: 0 1px 4px rgba(0,0,0,0.05); }
.section { margin-top: 0; }
.card-title { font-size: 15px; font-weight: 600; margin: 0 0 12px; }

.product-summary { display: flex; align-items: center; gap: 12px; }
.product-summary .icon { font-size: 36px; }
.product-summary .name { font-size: 15px; font-weight: 600; }
.product-summary .info { font-size: 12px; color: var(--text-3); margin-top: 4px; font-family: monospace; }

.detail-list { list-style: none; padding: 0; margin: 0; }
.detail-list li {
  display: flex; justify-content: space-between;
  padding: 10px 0;
  border-bottom: 1px solid var(--border);
  font-size: 13px;
  &:last-child { border-bottom: none; }
}
.lbl { color: var(--text-3); }
.val { color: var(--text-1); font-weight: 500; }
.amount { color: var(--accent); font-size: 16px; font-weight: 700; font-family: 'JetBrains Mono', monospace; }

.risk-match {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  align-items: center;
  gap: 8px;
  padding: 12px;
  background: var(--bg);
  border-radius: 8px;
  margin-bottom: 12px;
}
.match-item { text-align: center; }
.match-label { font-size: 11px; color: var(--text-3); }
.match-value { font-size: 14px; font-weight: 600; margin-top: 4px; }
.match-arrow { font-size: 20px; color: var(--text-3); }
.match-result {
  padding: 12px;
  border-radius: 8px;
  text-align: center;
  font-size: 13px;
  &.ok { background: rgba(7,193,96,0.1); color: var(--success); }
  &.warn { background: rgba(255,151,106,0.1); color: var(--warning); }
  &.danger { background: rgba(238,10,36,0.1); color: var(--danger); }
}

.flow-list {
  padding-left: 0;
  margin: 0;
  list-style: none;
  counter-reset: step;
}
.flow-list li {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 0;
  font-size: 13px;
}
.step {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px; height: 24px;
  background: var(--primary);
  color: white;
  border-radius: 50%;
  font-size: 12px;
  font-weight: 600;
  font-family: monospace;
}

.agree { padding: 12px 16px; font-size: 12px; }
.agree a { color: var(--accent); }

.action-bar {
  position: fixed;
  bottom: 0; left: 0; right: 0;
  background: white;
  padding: 12px 16px;
  border-top: 1px solid var(--border);
}
</style>
