<template>
  <div class="home">
    <div class="banner">
      <div class="user-row">
        <div class="avatar">👤</div>
        <div class="user-info">
          <div class="name">{{ auth.user?.name || '游客' }}</div>
          <div class="phone">{{ maskedPhone }}</div>
        </div>
        <div class="logout-btn" @click="onLogout">退出</div>
      </div>
      <div class="banner-stats">
        <div class="stat-box">
          <div class="num">{{ stats.orders }}</div>
          <div class="lbl">持有产品</div>
        </div>
        <div class="stat-box">
          <div class="num">¥{{ formatAmount(stats.totalAmount) }}</div>
          <div class="lbl">总资产</div>
        </div>
        <div class="stat-box">
          <div class="num">{{ stats.riskLevel || '-' }}</div>
          <div class="lbl">风险等级</div>
        </div>
        <div class="stat-box">
          <div class="num">{{ stats.unreadFiles }}</div>
          <div class="lbl">待签文件</div>
        </div>
      </div>
    </div>

    <div class="quick-grid">
      <div class="quick" @click="$router.push('/h5/risk')">
        <div class="qi">📋</div>
        <div class="qn">风险评估</div>
      </div>
      <div class="quick" @click="$router.push('/h5/products')">
        <div class="qi">🛒</div>
        <div class="qn">产品超市</div>
      </div>
      <div class="quick" @click="$router.push('/h5/orders')">
        <div class="qi">📦</div>
        <div class="qn">我的订单</div>
      </div>
      <div class="quick" @click="$router.push('/h5/files')">
        <div class="qi">📄</div>
        <div class="qn">待签文件</div>
      </div>
    </div>

    <div class="card section">
      <div class="section-title">
        <span>推荐产品</span>
        <span class="more" @click="$router.push('/h5/products')">更多 ›</span>
      </div>
      <div v-if="loadingProducts" class="loading">加载中...</div>
      <div v-else-if="!recommended.length" class="empty">暂无产品</div>
      <div v-for="p in recommended" :key="p.productId" class="product-card" @click="$router.push(`/h5/product/${p.productId}`)">
        <div class="product-row">
          <div class="product-meta">
            <div class="product-name">{{ getName(p) }}</div>
            <div class="product-tags">
              <span :class="['tag', riskTag(p.riskLevel).cls]">{{ riskTag(p.riskLevel).label }}</span>
            </div>
          </div>
          <div class="product-yield">
            <div class="yield-num">{{ getYield(p) }}</div>
            <div class="yield-lbl">预期年化</div>
          </div>
        </div>
      </div>
    </div>

    <div class="card section">
      <div class="section-title">最近订单</div>
      <div v-if="loadingOrders" class="loading">加载中...</div>
      <div v-else-if="!recentOrders.length" class="empty">暂无订单</div>
      <div v-for="o in recentOrders" :key="o.businessId" class="order-card" @click="$router.push(`/h5/order/${o.businessId}`)">
        <div class="order-row">
          <div>
            <div class="order-name">{{ getProductName(o.productId) }}</div>
            <div class="order-time">{{ formatDate(o.createdAt) }}</div>
          </div>
          <div class="order-amount">¥{{ formatAmount(o.amount) }}</div>
          <div :class="['order-status', `status-${statusCls(o.state)}`]">{{ stateLabel(o.state) }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'
import { useAuthStore } from '@/stores/auth'
import { recordingApi, scriptApi, fileApi, riskApi } from '@/api'

const router = useRouter()
const auth = useAuthStore()

const maskedPhone = computed(() => {
  if (!auth.user?.phone) return '未登录'
  return auth.user.phone.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2')
})

const stats = ref({ orders: 0, totalAmount: 0, riskLevel: '', unreadFiles: 0 })
const recommended = ref<any[]>([])
const recentOrders = ref<any[]>([])
const productMap = ref<Record<string, any>>({})
const loadingProducts = ref(true)
const loadingOrders = ref(true)

onMounted(async () => {
  await Promise.all([
    loadProducts(),
    loadRecentOrders(),
    loadRiskLevel(),
    loadFileCount()
  ])
})

async function loadProducts() {
  try {
    // 真实 API: 加载全部 DB 话术 (作为产品列表)
    const res: any = await scriptApi.listDbTemplates()
    recommended.value = (Array.isArray(res) ? res : []).slice(0, 3)
    // 缓存 productId → 产品信息
    recommended.value.forEach((p: any) => {
      productMap.value[p.productId] = p
    })
  } catch (e) {
    recommended.value = []
  } finally {
    loadingProducts.value = false
  }
}

async function loadRecentOrders() {
  try {
    // 真实 API: 这里可以通过 business 列表, 但目前没有 list 端点
    // 改用文件推送列表反推
    const customerId = auth.user?.customerIdHash
    if (!customerId) {
      recentOrders.value = []
      return
    }
    // 用硬编码演示 (实际应该后端提供 /business/customer/{id})
    // 暂时用业务ID直接查
    recentOrders.value = []
    // TODO: 真实后端需要 /business/customer/{customerIdHash} 接口
  } catch (e) {
    recentOrders.value = []
  } finally {
    loadingOrders.value = false
  }
}

async function loadRiskLevel() {
  try {
    const customerId = auth.user?.customerIdHash
    if (!customerId) return
    const res: any = await riskApi.latest(customerId)
    if (res && res.riskLevel) {
      stats.value.riskLevel = res.riskLevel
    }
  } catch {}
}

async function loadFileCount() {
  try {
    // 暂时设为 0, 实际应调 fileApi.list
    stats.value.unreadFiles = 0
  } catch {}
}

function formatAmount(n: number | undefined) {
  if (!n) return '0'
  return n.toLocaleString()
}
function formatDate(d: string) {
  if (!d) return '-'
  return d.substring(0, 10)
}
function getName(p: any) {
  return productMap.value[p.productId]?.productType === 'INSURANCE' ? '投资连结险' :
         productMap.value[p.productId]?.productType === 'FUND' ? '混合基金' :
         '固收理财'
}
function getYield(p: any) {
  const map: any = { R1: '2.8%', R2: '3.6%', R3: '4.8%', R4: '5.5%', P3: '5.2%', P4: '6.0%', P5: '7.5%' }
  return map[p.riskLevel] || '-'
}
function getProductName(id: string) {
  return productMap.value[id]?.productType || '理财产品'
}
function riskTag(level: string) {
  const map: Record<string, { label: string; cls: string }> = {
    R1: { label: '低', cls: 'tag-success' },
    R2: { label: '中低', cls: 'tag-success' },
    R3: { label: '中', cls: 'tag-warning' },
    R4: { label: '中高', cls: 'tag-warning' },
    R5: { label: '高', cls: 'tag-danger' }
  }
  return map[level] || { label: level, cls: 'tag-primary' }
}
function stateLabel(s: string) {
  const map: any = {
    INIT: '待开始', IDENTITY_VERIFIED: '已核身', RISK_ASSESSED: '已评估',
    SCRIPT_LOADED: '已加载话术', RECORDING: '录制中', RECORDED: '已录制',
    AI_QA: 'AI 质检', AI_QA_PASSED: 'AI 通过', AI_QA_FLAGGED: 'AI 标记',
    HUMAN_REVIEW: '人工复核', HUMAN_REVIEWED: '复核完成', SIGNED: '已签署',
    ARCHIVED: '已归档', FAILED: '已失败', OFFLINE_FAILED: '线下失败', ROLLED_BACK: '已回滚'
  }
  return map[s] || s
}
function statusCls(s: string) {
  if (s === 'ARCHIVED' || s === 'SIGNED') return 'success'
  if (s === 'FAILED' || s === 'OFFLINE_FAILED' || s === 'ROLLED_BACK') return 'danger'
  if (s === 'RECORDING' || s === 'AI_QA' || s === 'HUMAN_REVIEW') return 'warning'
  return 'info'
}

function onLogout() {
  auth.logout()
  router.replace('/')
}
</script>

<style lang="scss" scoped>
.home { padding: 0 0 24px; }

.banner {
  background: linear-gradient(135deg, #1e2a47 0%, #2c3a5c 100%);
  color: white;
  padding: 24px 16px 60px;
}
.user-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 24px;
}
.avatar {
  width: 48px; height: 48px;
  background: rgba(255,255,255,0.2);
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-size: 24px;
}
.user-info { flex: 1; }
.name { font-size: 16px; font-weight: 600; }
.phone { font-size: 12px; opacity: 0.7; margin-top: 2px; }
.logout-btn {
  font-size: 12px;
  padding: 4px 12px;
  border: 1px solid rgba(255,255,255,0.3);
  border-radius: 16px;
}

.banner-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  background: rgba(255,255,255,0.1);
  border-radius: 12px;
  padding: 12px;
  backdrop-filter: blur(10px);
}
.stat-box { text-align: center; }
.stat-box .num { font-size: 16px; font-weight: 700; color: var(--accent-light); font-family: 'JetBrains Mono', monospace; }
.stat-box .lbl { font-size: 10px; opacity: 0.85; margin-top: 2px; }

.quick-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 8px;
  background: white;
  margin: -32px 12px 0;
  border-radius: 12px;
  padding: 16px 8px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.08);
  position: relative;
}
.quick {
  text-align: center;
  padding: 8px 0;
  cursor: pointer;
  &:active { opacity: 0.6; }
}
.qi { font-size: 28px; margin-bottom: 4px; }
.qn { font-size: 12px; color: var(--text-2); }

.card {
  background: var(--card);
  border-radius: 12px;
  padding: 16px;
  margin: 12px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.05);
}
.section-title {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.more { font-size: 12px; color: var(--text-3); }

.product-card {
  border-bottom: 1px solid var(--border);
  padding: 12px 0;
  &:last-child { border-bottom: none; padding-bottom: 0; }
  &:first-child { padding-top: 0; }
}
.product-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.product-name { font-size: 14px; font-weight: 600; margin-bottom: 6px; }
.product-tags { display: flex; gap: 4px; }
.yield-num {
  font-size: 20px;
  font-weight: 700;
  color: var(--accent);
  font-family: 'JetBrains Mono', monospace;
}
.yield-lbl { font-size: 10px; color: var(--text-3); text-align: right; margin-top: 2px; }

.order-card {
  border-bottom: 1px solid var(--border);
  padding: 10px 0;
  &:last-child { border-bottom: none; padding-bottom: 0; }
}
.order-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.order-name { font-size: 13px; font-weight: 500; }
.order-time { font-size: 11px; color: var(--text-3); margin-top: 2px; }
.order-amount { font-size: 14px; font-weight: 600; color: var(--accent); font-family: 'JetBrains Mono', monospace; flex: 1; text-align: right; }
.order-status {
  font-size: 11px;
  padding: 2px 6px;
  border-radius: 4px;
  &.status-success { background: rgba(7,193,96,0.1); color: var(--success); }
  &.status-warning { background: rgba(255,151,106,0.1); color: var(--warning); }
  &.status-danger { background: rgba(238,10,36,0.1); color: var(--danger); }
  &.status-info { background: rgba(30,42,71,0.1); color: var(--primary); }
}

.tag { display: inline-block; padding: 1px 6px; border-radius: 3px; font-size: 11px; }
.tag-primary { background: rgba(30,42,71,0.1); color: var(--primary); }
.tag-success { background: rgba(7,193,96,0.1); color: var(--success); }
.tag-warning { background: rgba(255,151,106,0.1); color: var(--warning); }
.tag-danger { background: rgba(238,10,36,0.1); color: var(--danger); }

.loading { text-align: center; padding: 20px; color: var(--text-3); font-size: 12px; }
.empty { text-align: center; padding: 20px; color: var(--text-3); font-size: 12px; }
</style>
