<template>
  <div class="order-confirm">
    <div class="page-header">
      <h1 class="page-title">订单确认</h1>
      <p class="page-subtitle">请确认购买信息，开始双录流程</p>
    </div>

    <div v-if="loading" class="loading">加载中...</div>
    <div v-else-if="!product" class="empty-state">
      <p>产品不存在</p>
    </div>
    <template v-else>
      <div class="card section">
        <div class="product-summary">
          <div class="icon">{{ typeIcon(product.productType) }}</div>
          <div class="meta">
            <div class="name">{{ getName(product) }}</div>
            <div class="info">{{ product.productId }} · v{{ product.version }}</div>
          </div>
        </div>
      </div>

      <div class="card section">
        <h3 class="card-title">购买详情</h3>
        <ul class="detail-list">
          <li><span class="lbl">产品类型</span><span class="val">{{ productTypeLabel(product.productType) }}</span></li>
          <li><span class="lbl">风险等级</span><span class="val">{{ riskLabel(product.riskLevel) }} ({{ product.riskLevel }})</span></li>
          <li><span class="lbl">购买金额</span><span class="val amount">¥ {{ Number(amount).toLocaleString() }}</span></li>
          <li><span class="lbl">预期年化</span><span class="val">{{ getYield(product.riskLevel) }}</span></li>
          <li><span class="lbl">产品期限</span><span class="val">{{ getTerm(product.productType) }}</span></li>
        </ul>
      </div>

      <div class="card section">
        <h3 class="card-title">⚠️ 风险匹配检查</h3>
        <div v-if="loadingMatch" class="loading-inline">检查中...</div>
        <div v-else class="risk-match">
          <div class="match-item">
            <div class="match-label">您的风险等级</div>
            <div class="match-value">{{ customerRisk || '未评估' }}</div>
          </div>
          <div class="match-arrow">⇋</div>
          <div class="match-item">
            <div class="match-label">产品风险等级</div>
            <div class="match-value">{{ riskLabel(product.riskLevel) }} ({{ product.riskLevel }})</div>
          </div>
        </div>
        <div v-if="!loadingMatch && matchResult" :class="['match-result', matchResult.cls]">
          {{ matchResult.icon }} {{ matchResult.text }}
        </div>
      </div>

      <div class="card section">
        <h3 class="card-title">📋 购买流程</h3>
        <ol class="flow-list">
          <li><span class="step">1</span><span>确认订单并创建业务</span></li>
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
        <van-button block round size="large" type="primary" :disabled="!agreed || creating" :loading="creating" @click="onConfirm">
          {{ creating ? '创建中...' : '开始双录' }}
        </van-button>
      </div>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast, showDialog } from 'vant'
import { useAuthStore } from '@/stores/auth'
import { scriptApi, recordingApi, riskApi } from '@/api'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const productId = route.query.productId as string
const amount = Number(route.query.amount) || 0

const product = ref<any>(null)
const loading = ref(true)
const creating = ref(false)
const agreed = ref(false)
const customerRisk = ref<string>('')
const matchResult = ref<{ cls: string, icon: string, text: string } | null>(null)
const loadingMatch = ref(false)

onMounted(async () => {
  await loadProduct()
  await loadRiskAndMatch()
  loading.value = false
})

async function loadProduct() {
  try {
    const res: any = await scriptApi.getDbTemplate(productId)
    product.value = res
  } catch {
    product.value = null
  }
}

async function loadRiskAndMatch() {
  loadingMatch.value = true
  try {
    const cid = auth.user?.customerIdHash
    if (cid) {
      const latest: any = await riskApi.latest(cid)
      if (latest?.riskLevel) {
        customerRisk.value = latest.riskLevel
      }
    }
    if (customerRisk.value) {
      const match: any = await riskApi.match(customerRisk.value, product.value.riskLevel)
      // match 可能是 {matched: true/false, ...} 或其他格式
      const matched = match?.matched !== false
      const userLevel = parseInt(customerRisk.value.replace('C', ''))
      const prodLevel = parseInt(product.value.riskLevel.replace(/[RP]/, ''))
      if (userLevel >= prodLevel) {
        matchResult.value = { cls: 'ok', icon: '✓', text: '您的风险等级与产品匹配, 可以购买' }
      } else if (userLevel === prodLevel - 1) {
        matchResult.value = { cls: 'warn', icon: '⚠', text: '产品风险略高于您, 请谨慎评估' }
      } else {
        matchResult.value = { cls: 'danger', icon: '✗', text: '风险等级不匹配, 不建议购买' }
      }
    } else {
      matchResult.value = { cls: 'warn', icon: '!', text: '请先完成风险评估' }
    }
  } catch {
    matchResult.value = { cls: 'warn', icon: '!', text: '风险评估查询失败' }
  } finally {
    loadingMatch.value = false
  }
}

function typeIcon(t: string) {
  return ({ WEALTH: '🏦', FUND: '📈', INSURANCE: '🛡️' } as Record<string, string>)[t] || '📊'
}
function productTypeLabel(t: string) {
  return ({ WEALTH: '银行理财', FUND: '基金', INSURANCE: '保险' } as Record<string, string>)[t] || t
}
function riskLabel(level: string) {
  return ({ R1: '低', R2: '中低', R3: '中', R4: '中高', R5: '高', P1: '低', P2: '中低', P3: '中', P4: '中高', P5: '高' } as Record<string, string>)[level] || level
}
function getName(p: any) {
  return p.productType === 'INSURANCE' ? '投资连结险' :
         p.productType === 'FUND' ? (p.productId.includes('BOND') ? '稳健债基' : '混合基金') :
         '固收理财'
}
function getYield(risk: string) {
  const map: any = { R1: '2.8%', R2: '3.6%', R3: '4.8%', R4: '5.5%', R5: '6.5%' }
  return map[risk] || '-'
}
function getTerm(type: string) {
  return type === 'INSURANCE' ? '5 年' : type === 'FUND' ? '1 年' : '180 天'
}

async function onConfirm() {
  if (!agreed.value) {
    showToast('请先同意相关协议')
    return
  }
  if (matchResult.value?.cls === 'danger') {
    try {
      await showDialog({ title: '风险不匹配', message: '您的风险等级不足以购买本产品, 是否继续?', showCancelButton: true })
    } catch { return }
  }
  creating.value = true
  try {
    // 真实 API: 创建业务
    const res: any = await recordingApi.startBusiness({
      businessType: product.value.productType,
      productId: product.value.productId,
      customerIdHash: auth.user?.customerIdHash || 'cust-anonymous',
      sellerIdHash: 'seller-mobile-001',
      channel: 'INTERNET_TEXT',
      sellerType: 'HUMAN',
      amount: amount
    })
    showToast('业务已创建, 开始双录')
    router.push(`/h5/record/${res.businessId}`)
  } catch (e: any) {
    showToast('创建失败: ' + (e?.message || '未知错误'))
  } finally {
    creating.value = false
  }
}
</script>

<style lang="scss" scoped>
.order-confirm { min-height: 100vh; padding-bottom: calc(140px + env(safe-area-inset-bottom, 0px)); }
.loading, .loading-inline { padding: 40px 20px; text-align: center; color: var(--text-3); }
.loading-inline { padding: 16px; font-size: 12px; }
.empty-state { padding: 60px 20px; text-align: center; color: var(--text-3); }

.card { background: var(--card); border-radius: 12px; padding: 16px; margin: 12px; box-shadow: 0 1px 4px rgba(0,0,0,0.05); }
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

.flow-list { padding-left: 0; margin: 0; list-style: none; }
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
  // 浮在 tabbar 上面 (tabbar 高度 60px + safe area)
  bottom: calc(60px + env(safe-area-inset-bottom, 0px));
  left: 0; right: 0;
  background: white;
  padding: 12px 16px;
  border-top: 1px solid var(--border);
  box-shadow: 0 -2px 12px rgba(60, 40, 20, 0.06);
  z-index: 99;
}
</style>
