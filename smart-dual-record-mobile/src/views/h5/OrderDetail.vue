<template>
  <div class="order-detail">
    <div class="page-header">
      <h1 class="page-title">订单详情</h1>
      <p class="page-subtitle">{{ orderId }}</p>
    </div>

    <div :class="['status-banner', `status-${order.statusCls}`]">
      <div class="status-icon">{{ order.statusIcon }}</div>
      <div>
        <div class="status-label">{{ order.statusLabel }}</div>
        <div class="status-desc">{{ order.statusDesc }}</div>
      </div>
    </div>

    <div class="card section">
      <h3 class="card-title">📦 商品信息</h3>
      <ul class="detail-list">
        <li><span class="lbl">产品名称</span><span class="val">{{ order.productName }}</span></li>
        <li><span class="lbl">产品代码</span><span class="val">{{ order.productId }}</span></li>
        <li><span class="lbl">购买金额</span><span class="val">¥{{ order.amount.toLocaleString() }}</span></li>
        <li><span class="lbl">预期年化</span><span class="val">{{ order.expectedYield }}</span></li>
      </ul>
    </div>

    <div class="card section">
      <h3 class="card-title">📹 双录进度</h3>
      <div class="timeline">
        <div
          v-for="(n, i) in nodes"
          :key="n.id"
          :class="['timeline-item', getNodeCls(i)]"
        >
          <div class="timeline-dot">{{ i + 1 }}</div>
          <div class="timeline-content">
            <div class="node-name">{{ n.name }}</div>
            <div class="node-desc">{{ n.desc }}</div>
          </div>
        </div>
      </div>
    </div>

    <div class="card section">
      <h3 class="card-title">🔍 合规检查</h3>
      <ul class="check-list">
        <li v-for="c in order.compliance" :key="c.label">
          <span class="check-label">{{ c.label }}</span>
          <span :class="['check-result', c.cls]">{{ c.result }}</span>
        </li>
      </ul>
    </div>

    <div class="card section">
      <h3 class="card-title">📞 客服/申诉</h3>
      <div class="contact-list">
        <div class="contact-item" @click="call('95588')">
          <div class="c-icon">📞</div>
          <div class="c-info">
            <div class="c-name">客服热线</div>
            <div class="c-num">95588</div>
          </div>
        </div>
        <div class="contact-item" @click="appeal">
          <div class="c-icon">📝</div>
          <div class="c-info">
            <div class="c-name">发起申诉</div>
            <div class="c-num">7×24h 处理</div>
          </div>
        </div>
        <div class="contact-item" @click="playRecord">
          <div class="c-icon">📹</div>
          <div class="c-info">
            <div class="c-name">录像调阅</div>
            <div class="c-num">需 2 级审批</div>
          </div>
        </div>
      </div>
    </div>

    <div class="action-bar" v-if="order.status === 'PENDING'">
      <van-button block round size="large" type="primary" @click="$router.push(`/h5/record/${orderId}`)">
        继续双录
      </van-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast, showDialog } from 'vant'

const route = useRoute()
const router = useRouter()
const orderId = route.params.id as string

const nodes = [
  { id: '01-IDENTITY', name: '身份核验', desc: '出示身份证 + 联网核查' },
  { id: '02-DISCLOSURE', name: '产品披露', desc: '产品基本信息 / 风险等级' },
  { id: '03-PRODUCT', name: '产品介绍', desc: '产品特点 / 收益 / 费用' },
  { id: '04-RIGHTS', name: '权益告知', desc: '犹豫期 / 赎回 / 投诉' },
  { id: '05-TRUTH_TELL', name: '如实告知', desc: '客户风险承受能力确认' },
  { id: '06-AFFIRMATIVE', name: '明确肯定', desc: '客户明确购买意愿' },
  { id: '07-SIGN', name: '电子签名', desc: '合同 + 风险揭示书签字' },
  { id: '08-FOLLOWUP', name: '犹豫期', desc: '15 天 3 次回访' }
]

const order = ref({
  productName: '稳赢系列 · 固收理财',
  productId: 'BNK-FIN-2026Q3-001',
  amount: 50000,
  expectedYield: '3.6%',
  status: 'COMPLETED',
  statusLabel: '已完成',
  statusDesc: '双录已结束, 已签合同',
  statusIcon: '✓',
  statusCls: 'success',
  progressNode: 8,
  compliance: [
    { label: '客户身份核验', result: '通过', cls: 'ok' },
    { label: '风险等级匹配', result: '匹配', cls: 'ok' },
    { label: '禁播词扫描', result: '0 命中', cls: 'ok' },
    { label: '录像画质', result: '1280×720', cls: 'ok' },
    { label: '客户人脸可见', result: '98%', cls: 'ok' },
    { label: '犹豫期告知', result: '已告知', cls: 'ok' },
    { label: '电子签名', result: '已签名', cls: 'ok' },
    { label: '录像文件完整性', result: '完整', cls: 'ok' }
  ]
})

const currentNode = computed(() => {
  if (order.value.status === 'COMPLETED') return nodes.length
  if (order.value.status === 'FAILED') return 1
  return order.value.progressNode || 0
})

function getNodeCls(i: number) {
  if (i < currentNode.value) return 'completed'
  if (i === currentNode.value) return 'active'
  return ''
}

function call(num: string) {
  showToast(`正在拨打 ${num}`)
}
function appeal() {
  showDialog({ title: '发起申诉', message: '请联系客服 95588 发起申诉, 1-3 个工作日内处理' })
}
function playRecord() {
  showToast('录像调阅需 2 级审批, 请联系客服')
}
</script>

<style lang="scss" scoped>
.order-detail { min-height: 100vh; padding-bottom: 100px; }

.status-banner {
  margin: 12px;
  padding: 16px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  gap: 12px;
  &.status-success { background: rgba(7,193,96,0.1); color: var(--success); }
  &.status-warning { background: rgba(255,151,106,0.1); color: var(--warning); }
  &.status-danger { background: rgba(238,10,36,0.1); color: var(--danger); }
}
.status-icon { font-size: 32px; }
.status-label { font-size: 16px; font-weight: 600; }
.status-desc { font-size: 12px; opacity: 0.85; margin-top: 4px; }

.card { background: var(--card); border-radius: 12px; padding: 16px; margin: 12px; box-shadow: 0 1px 4px rgba(0,0,0,0.05); }
.card-title { font-size: 15px; font-weight: 600; margin: 0 0 12px; }

.detail-list { list-style: none; padding: 0; margin: 0; }
.detail-list li {
  display: flex; justify-content: space-between;
  padding: 10px 0;
  border-bottom: 1px solid var(--border);
  font-size: 13px;
  &:last-child { border-bottom: none; }
}
.lbl { color: var(--text-3); }
.val { font-weight: 500; }

.timeline { position: relative; padding-left: 32px; }
.timeline::before {
  content: ''; position: absolute;
  left: 11px; top: 8px; bottom: 8px;
  width: 2px; background: var(--border);
}
.timeline-item {
  position: relative;
  padding: 6px 0;
  &.completed .timeline-dot { background: var(--success); border-color: var(--success); color: white; }
  &.active .timeline-dot { background: var(--accent); border-color: var(--accent); color: white; }
}
.timeline-dot {
  position: absolute;
  left: -27px; top: 6px;
  width: 24px; height: 24px;
  border-radius: 50%;
  background: white;
  border: 2px solid var(--border);
  display: flex; align-items: center; justify-content: center;
  font-size: 11px; font-weight: 600;
  color: var(--text-3);
}
.node-name { font-size: 13px; font-weight: 600; }
.node-desc { font-size: 11px; color: var(--text-3); margin-top: 2px; }

.check-list { list-style: none; padding: 0; margin: 0; }
.check-list li {
  display: flex; justify-content: space-between;
  padding: 8px 0;
  font-size: 13px;
  border-bottom: 1px solid var(--border);
  &:last-child { border-bottom: none; }
}
.check-label { color: var(--text-2); }
.check-result {
  font-weight: 500;
  &.ok { color: var(--success); }
  &.warn { color: var(--warning); }
  &.danger { color: var(--danger); }
}

.contact-list { display: flex; flex-direction: column; gap: 8px; }
.contact-item {
  display: flex; align-items: center; gap: 12px;
  padding: 12px;
  background: var(--bg);
  border-radius: 8px;
  cursor: pointer;
  &:active { background: var(--border); }
}
.c-icon { font-size: 24px; }
.c-name { font-size: 14px; font-weight: 600; }
.c-num { font-size: 11px; color: var(--text-3); margin-top: 2px; }

.action-bar {
  position: fixed;
  bottom: calc(60px + env(safe-area-inset-bottom, 0px)); left: 0; right: 0;
  background: white;
  padding: 12px 16px;
  border-top: 1px solid var(--border);
}
</style>
