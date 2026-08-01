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
          <div class="num">3</div>
          <div class="lbl">持有产品</div>
        </div>
        <div class="stat-box">
          <div class="num">¥ 280K</div>
          <div class="lbl">总资产</div>
        </div>
        <div class="stat-box">
          <div class="num">+12%</div>
          <div class="lbl">累计收益</div>
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
      <div v-for="p in recommended" :key="p.id" class="product-card" @click="$router.push(`/h5/product/${p.id}`)">
        <div class="product-row">
          <div class="product-meta">
            <div class="product-name">{{ p.name }}</div>
            <div class="product-tags">
              <span :class="['tag', riskTag(p.riskLevel).cls]">{{ riskTag(p.riskLevel).label }}</span>
              <span class="tag tag-primary">{{ p.term }}</span>
            </div>
          </div>
          <div class="product-yield">
            <div class="yield-num">{{ p.expectedYield }}</div>
            <div class="yield-lbl">预期年化</div>
          </div>
        </div>
      </div>
    </div>

    <div class="card section">
      <div class="section-title">双录状态</div>
      <div v-if="recording" class="recording-status">
        <div class="rec-dot"></div>
        <span>正在进行双录 · 第 {{ currentNode + 1 }} 节点</span>
      </div>
      <div v-else class="empty">
        <div class="empty-icon">📹</div>
        <p>暂无进行中的双录</p>
        <van-button size="small" type="primary" round @click="$router.push('/h5/products')">开始购买</van-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()

const maskedPhone = computed(() => {
  if (!auth.user?.phone) return '未登录'
  return auth.user.phone.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2')
})

const recommended = ref([
  { id: 'BNK-FIN-2026Q3-001', name: '稳赢系列 · 固收理财', riskLevel: 'R2', term: '180 天', expectedYield: '3.6%' },
  { id: 'FND-STK-2026Q3-002', name: '稳进混合基金', riskLevel: 'R3', term: '1 年', expectedYield: '5.2%' },
  { id: 'LIC-INV-2026Q3-001', name: '稳健投资连结险', riskLevel: 'P4', term: '5 年', expectedYield: '6.0%' }
])

const recording = ref(false)
const currentNode = ref(0)

function riskTag(level: string) {
  const map: Record<string, { label: string; cls: string }> = {
    R1: { label: '低风险', cls: 'tag-success' },
    R2: { label: '中低风险', cls: 'tag-success' },
    R3: { label: '中风险', cls: 'tag-warning' },
    R4: { label: '中高风险', cls: 'tag-warning' },
    R5: { label: '高风险', cls: 'tag-danger' }
  }
  return map[level] || { label: level, cls: 'tag-primary' }
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
  position: relative;
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
  grid-template-columns: repeat(3, 1fr);
  gap: 8px;
  background: rgba(255,255,255,0.1);
  border-radius: 12px;
  padding: 12px;
  backdrop-filter: blur(10px);
}
.stat-box { text-align: center; }
.stat-box .num { font-size: 18px; font-weight: 700; color: var(--accent-light); }
.stat-box .lbl { font-size: 11px; opacity: 0.85; margin-top: 2px; }

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
.section { margin-top: 0; }
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

.recording-status {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px;
  background: rgba(255,151,106,0.1);
  border-radius: 8px;
  color: var(--warning);
  font-size: 14px;
}
.rec-dot {
  width: 8px; height: 8px;
  background: var(--danger);
  border-radius: 50%;
  animation: blink 1s infinite;
}
@keyframes blink { 0%, 100% { opacity: 1; } 50% { opacity: 0.3; } }

.empty { text-align: center; padding: 24px 12px; }
.empty-icon { font-size: 36px; margin-bottom: 8px; opacity: 0.4; }
.empty p { font-size: 13px; color: var(--text-3); margin: 0 0 12px; }
</style>
