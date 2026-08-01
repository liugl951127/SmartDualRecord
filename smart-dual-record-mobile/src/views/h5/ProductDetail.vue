<template>
  <div class="product-detail">
    <div v-if="loading" class="loading">加载中...</div>
    <div v-else-if="!product" class="empty-state">
      <div class="empty-icon">❌</div>
      <p>产品不存在</p>
    </div>
    <template v-else>
      <div class="hero">
        <div class="type-icon">{{ typeIcon(product.productType) }}</div>
        <h1>{{ getName(product) }}</h1>
        <div class="hero-meta">
          <span :class="['risk-tag', `risk-${product.riskLevel}`]">{{ riskLabel(product.riskLevel) }}风险</span>
          <span class="badge">v{{ product.version }}</span>
        </div>
      </div>

      <div class="metric-cards">
        <div class="m-card">
          <div class="m-value">{{ getYield(product.riskLevel) }}</div>
          <div class="m-label">预期年化</div>
        </div>
        <div class="m-card">
          <div class="m-value">{{ getTerm(product.productType) }}</div>
          <div class="m-label">产品期限</div>
        </div>
        <div class="m-card">
          <div class="m-value">{{ minAmount(product.productType) }}</div>
          <div class="m-label">起购金额</div>
        </div>
      </div>

      <div class="card section" v-if="script?.mandatoryDisclosure?.length">
        <h3 class="card-title">📋 必播项 (坐席必须说)</h3>
        <ul class="list">
          <li v-for="(d, i) in script.mandatoryDisclosure" :key="i">
            <span class="num">{{ i + 1 }}</span>
            <span class="text">{{ d }}</span>
          </li>
        </ul>
      </div>

      <div class="card section" v-if="script?.requiredQuestions?.length">
        <h3 class="card-title">❓ 必问题 (客户必须答)</h3>
        <ul class="list">
          <li v-for="(q, i) in script.requiredQuestions" :key="i">
            <span class="num">{{ i + 1 }}</span>
            <span class="text">{{ q }}</span>
          </li>
        </ul>
      </div>

      <div class="card section" v-if="script?.forbiddenPhrases?.length">
        <h3 class="card-title">🚫 禁播词 (触发即失败)</h3>
        <div class="phrases">
          <span v-for="p in script.forbiddenPhrases" :key="p" class="phrase">{{ p }}</span>
        </div>
      </div>

      <div class="card section" v-if="script?.channelOverrides">
        <h3 class="card-title">🔀 渠道差分</h3>
        <div class="channels">
          <div v-for="(cfg, ch) in script.channelOverrides" :key="ch" class="ch-item">
            <span class="ch-name">{{ ch }}</span>
            <span class="ch-config">{{ formatConfig(cfg) }}</span>
          </div>
        </div>
      </div>

      <div class="card section">
        <h3 class="card-title">🔒 内容指纹</h3>
        <div class="hash-info">
          <div class="hash-label">SHA-256</div>
          <div class="hash-value">{{ product.contentHash }}</div>
          <div class="hash-meta">
            <span>状态: <b>{{ product.status }}</b></span>
            <span v-if="product.approvedBy">审批: {{ product.approvedBy }}</span>
          </div>
        </div>
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
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showDialog, showToast } from 'vant'
import { useAuthStore } from '@/stores/auth'
import { scriptApi } from '@/api'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const productId = route.params.id as string
const product = ref<any>(null)
const script = ref<any>(null)
const amount = ref('')
const loading = ref(true)

onMounted(async () => {
  await Promise.all([loadProduct(), loadScript()])
  loading.value = false
})

async function loadProduct() {
  try {
    const res: any = await scriptApi.getDbTemplate(productId)
    product.value = res
  } catch (e) {
    product.value = null
  }
}

async function loadScript() {
  try {
    const res: any = await scriptApi.getDbTemplate(productId)
    // 解析 JSON 字段
    if (res.mandatoryDisclosure) {
      try { res.mandatoryDisclosure = typeof res.mandatoryDisclosure === 'string' ? JSON.parse(res.mandatoryDisclosure) : res.mandatoryDisclosure } catch {}
    }
    if (res.requiredQuestions) {
      try { res.requiredQuestions = typeof res.requiredQuestions === 'string' ? JSON.parse(res.requiredQuestions) : res.requiredQuestions } catch {}
    }
    if (res.forbiddenPhrases) {
      try { res.forbiddenPhrases = typeof res.forbiddenPhrases === 'string' ? JSON.parse(res.forbiddenPhrases) : res.forbiddenPhrases } catch {}
    }
    if (res.channelOverrides) {
      try { res.channelOverrides = typeof res.channelOverrides === 'string' ? JSON.parse(res.channelOverrides) : res.channelOverrides } catch {}
    }
    script.value = res
  } catch (e) {
    script.value = null
  }
}

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
function getName(p: any) {
  return p.productType === 'INSURANCE' ? '投资连结险' :
         p.productType === 'FUND' ? (p.productId.includes('BOND') ? '稳健债基' : '混合基金') :
         '固收理财'
}
function getYield(risk: string) {
  const map: any = { R1: '2.8%', R2: '3.6%', R3: '4.8%', R4: '5.5%', R5: '6.5%', P1: '2.5%', P2: '3.0%', P3: '4.5%', P4: '6.0%', P5: '7.5%' }
  return map[risk] || '-'
}
function getTerm(type: string) {
  return type === 'INSURANCE' ? '5 年' : type === 'FUND' ? '1 年' : '180 天'
}
function minAmount(type: string) {
  return type === 'INSURANCE' ? '1 万' : type === 'FUND' ? '1000' : '1 万'
}
function formatConfig(cfg: any) {
  if (typeof cfg === 'string') return cfg
  return Object.entries(cfg).map(([k, v]) => `${k}: ${v}`).join(' · ')
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
      message: `您将购买 ${getName(product.value)}，金额 ¥${n.toLocaleString()}`,
      showCancelButton: true,
      confirmButtonText: '开始双录',
      cancelButtonText: '再想想'
    })
    router.push(`/h5/order?productId=${product.value.productId}&amount=${n}`)
  } catch {}
}
</script>

<style lang="scss" scoped>
.product-detail {
  min-height: 100vh;
  padding-bottom: 200px;
}
.loading { padding: 80px 20px; text-align: center; color: var(--text-3); }
.empty-state { padding: 80px 20px; text-align: center; }
.empty-icon { font-size: 48px; margin-bottom: 8px; }

.hero {
  background: linear-gradient(135deg, #1e2a47 0%, #2c3a5c 100%);
  color: white;
  padding: 32px 16px;
  text-align: center;
}
.type-icon { font-size: 48px; margin-bottom: 12px; }
.hero h1 { font-size: 22px; margin: 0 0 12px; font-weight: 600; }
.hero-meta { display: flex; gap: 8px; justify-content: center; }
.risk-tag, .badge {
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
.card-title { font-size: 15px; font-weight: 600; margin: 0 0 12px; }

.list { list-style: none; padding: 0; margin: 0; }
.list li {
  display: flex;
  gap: 8px;
  padding: 8px 0;
  border-bottom: 1px solid var(--border);
  font-size: 13px;
  line-height: 1.5;
  &:last-child { border-bottom: none; }
}
.list .num {
  background: var(--accent);
  color: white;
  width: 20px; height: 20px;
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-size: 11px;
  font-weight: 600;
  flex-shrink: 0;
}
.list .text { flex: 1; }

.phrases { display: flex; flex-wrap: wrap; gap: 6px; }
.phrase {
  background: rgba(238,10,36,0.1);
  color: var(--danger);
  padding: 4px 10px;
  border-radius: 4px;
  font-size: 12px;
}

.channels { display: flex; flex-direction: column; gap: 6px; }
.ch-item {
  display: flex;
  justify-content: space-between;
  padding: 8px 12px;
  background: var(--bg);
  border-radius: 6px;
  font-size: 12px;
}
.ch-name { font-weight: 600; font-family: monospace; }
.ch-config { color: var(--text-3); }

.hash-info {
  background: var(--bg);
  border-radius: 8px;
  padding: 12px;
  font-family: monospace;
}
.hash-label { font-size: 11px; color: var(--text-3); }
.hash-value { font-size: 13px; color: var(--primary); word-break: break-all; margin: 4px 0; }
.hash-meta { display: flex; justify-content: space-between; font-size: 11px; color: var(--text-3); margin-top: 8px; }

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
