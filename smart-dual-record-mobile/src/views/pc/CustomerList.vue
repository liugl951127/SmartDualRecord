<template>
  <div class="page-container">
    <div class="page-header-bar">
      <div class="page-title-group">
        <div class="page-icon">👥</div>
        <div>
          <h1 class="page-title">客户管理</h1>
          <div class="page-subtitle">共 {{ customers.length }} 个客户 · {{ filtered.length }} 匹配</div>
        </div>
      </div>
      <div class="page-actions">
        <button class="btn btn-ghost" @click="exportCSV">📥 导出</button>
        <button class="btn btn-ghost" @click="showFilter = !showFilter">
          🔍 筛选
          <span v-if="activeFilters > 0" class="tag tag-accent">{{ activeFilters }}</span>
        </button>
        <button class="btn btn-primary" @click="newCustomer">+ 新建客户</button>
      </div>
    </div>

    <div class="page-body fade-in">
      <!-- ============ 智能筛选条 ============ -->
      <transition name="filter">
        <div v-if="showFilter" class="filter-bar card">
          <div class="filter-grid">
            <div class="filter-item">
              <label>客户等级</label>
              <div class="filter-chips">
                <button
                  v-for="l in [1,2,3,4,5]"
                  :key="l"
                  :class="['chip', `chip-${riskColor(l)}`, filters.level.includes(l) && 'active']"
                  @click="toggleLevel(l)"
                >C{{ l }}</button>
              </div>
            </div>
            <div class="filter-item">
              <label>产品类型</label>
              <select class="input input-sm" v-model="filters.productType">
                <option value="">全部</option>
                <option>银行理财</option>
                <option>基金</option>
                <option>保险</option>
                <option>结构性存款</option>
              </select>
            </div>
            <div class="filter-item">
              <label>资产规模</label>
              <select class="input input-sm" v-model="filters.assetScale">
                <option value="">不限</option>
                <option value="0-100k">0-10 万</option>
                <option value="100k-500k">10-50 万</option>
                <option value="500k+">50 万以上</option>
              </select>
            </div>
            <div class="filter-item">
              <label>标签</label>
              <div class="filter-chips">
                <button
                  v-for="t in tagOptions"
                  :key="t"
                  :class="['chip', filters.tags.includes(t) && 'active']"
                  @click="toggleTag(t)"
                >{{ t }}</button>
              </div>
            </div>
            <div class="filter-item">
              <label>最近活动</label>
              <select class="input input-sm" v-model="filters.lastActivity">
                <option value="">不限</option>
                <option value="24h">24 小时内</option>
                <option value="7d">一周内</option>
                <option value="30d">一个月内</option>
                <option value="90d+">超过 3 个月</option>
              </select>
            </div>
          </div>
          <div class="filter-actions">
            <button class="btn btn-ghost btn-sm" @click="resetFilter">重置</button>
            <button class="btn btn-accent btn-sm" @click="applyFilter">应用筛选</button>
          </div>
        </div>
      </transition>

      <!-- ============ 批量操作条 ============ -->
      <transition name="filter">
        <div v-if="selected.length > 0" class="batch-bar">
          <div class="batch-info">
            <span class="batch-count">{{ selected.length }}</span>
            已选
            <button class="btn-text" @click="selected = []">清除</button>
          </div>
          <div class="batch-actions">
            <button class="btn btn-ghost btn-sm" @click="batchPush">📤 批量推送</button>
            <button class="btn btn-ghost btn-sm" @click="batchTag">🏷️ 批量打标</button>
            <button class="btn btn-ghost btn-sm" @click="batchExport">📥 导出选中</button>
            <button class="btn btn-ghost btn-sm" @click="batchNotify">📞 群发消息</button>
          </div>
        </div>
      </transition>

      <!-- ============ 客户列表 ============ -->
      <div class="card">
        <table class="data-table">
          <thead>
            <tr>
              <th style="width: 36px;">
                <label class="checkbox">
                  <input type="checkbox" :checked="allSelected" @change="toggleAll" />
                  <span></span>
                </label>
              </th>
              <th>客户</th>
              <th>等级</th>
              <th>总资产</th>
              <th>持有产品</th>
              <th>最近活动</th>
              <th>标签</th>
              <th>风险提示</th>
              <th style="width: 120px;">操作</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="c in pagedList"
              :key="c.id"
              :class="[selected.includes(c.id) && 'selected']"
              @click="onRowClick(c)"
            >
              <td @click.stop>
                <label class="checkbox">
                  <input type="checkbox" :checked="selected.includes(c.id)" @change="toggleSelect(c.id)" />
                  <span></span>
                </label>
              </td>
              <td>
                <div class="customer-cell">
                  <div class="cc-avatar" :style="{ background: avatarColor(c.riskLevel) }">
                    {{ c.name.charAt(0) }}
                  </div>
                  <div class="cc-info">
                    <div class="cc-name">
                      {{ c.name }}
                      <span v-if="c.vip" class="tag tag-accent">⭐ VIP</span>
                    </div>
                    <div class="cc-id mono text-sm text-muted">{{ c.idHash }}</div>
                  </div>
                </div>
              </td>
              <td>
                <span :class="['tag', `tag-${riskColor(c.riskLevel)}`]">C{{ c.riskLevel }} · {{ c.riskName }}</span>
              </td>
              <td class="mono font-bold">¥{{ c.asset }}</td>
              <td>
                <div class="product-stack">
                  <span v-for="(p, i) in c.products.slice(0, 2)" :key="i" class="product-chip">
                    {{ p }}
                  </span>
                  <span v-if="c.products.length > 2" class="product-more">+{{ c.products.length - 2 }}</span>
                </div>
              </td>
              <td>
                <div class="text-sm">
                  <div>{{ c.lastActivity }}</div>
                  <div class="text-muted text-sm">{{ c.lastActivityTime }}</div>
                </div>
              </td>
              <td>
                <div class="tag-stack">
                  <span v-for="t in c.tags" :key="t" class="tag tag-default">{{ t }}</span>
                </div>
              </td>
              <td>
                <span v-if="c.riskAlert" class="tag tag-danger">⚠️ 错配</span>
                <span v-else class="text-muted text-sm">正常</span>
              </td>
              <td @click.stop>
                <div class="row-actions">
                  <button class="btn btn-text btn-sm" @click="quickCall(c)" title="联系">📞</button>
                  <button class="btn btn-text btn-sm" @click="quickPush(c)" title="推送">📤</button>
                  <button class="btn btn-text btn-sm" @click="quickRecord(c)" title="双录">📞</button>
                  <button class="btn btn-icon btn-ghost btn-sm" title="更多">⋯</button>
                </div>
              </td>
            </tr>
          </tbody>
        </table>
        <!-- 分页 -->
        <div class="pagination">
          <div class="pg-info text-sm text-muted">
            显示 {{ (page-1)*pageSize + 1 }}-{{ Math.min(page*pageSize, filtered.length) }} / 共 {{ filtered.length }}
          </div>
          <div class="pg-buttons">
            <button class="btn btn-ghost btn-sm" :disabled="page === 1" @click="page--">‹</button>
            <button
              v-for="p in totalPages"
              :key="p"
              :class="['btn btn-sm', p === page ? 'btn-accent' : 'btn-ghost']"
              @click="page = p"
            >{{ p }}</button>
            <button class="btn btn-ghost btn-sm" :disabled="page === totalPages" @click="page++">›</button>
          </div>
        </div>
      </div>

      <!-- ============ 客户 360° 详情 ============ -->
      <transition name="modal">
        <div v-if="current" class="modal-overlay" @click="current = null">
          <div class="modal-360" @click.stop>
            <div class="m-header" :style="{ background: `linear-gradient(135deg, ${avatarColor(current.riskLevel)} 0%, ${avatarColor(current.riskLevel)}cc 100%)` }">
              <button class="m-close" @click="current = null">×</button>
              <div class="m-avatar">{{ current.name.charAt(0) }}</div>
              <div class="m-name">
                {{ current.name }}
                <span v-if="current.vip" class="tag tag-accent">⭐ VIP</span>
                <span :class="['tag', `tag-${riskColor(current.riskLevel)}`]">C{{ current.riskLevel }}</span>
              </div>
              <div class="m-id mono text-sm">{{ current.idHash }} · {{ current.phone }}</div>
              <div class="m-stats">
                <div class="ms-item">
                  <div class="ms-label">总资产</div>
                  <div class="ms-value mono">¥{{ current.asset }}</div>
                </div>
                <div class="ms-item">
                  <div class="ms-label">持有产品</div>
                  <div class="ms-value mono">{{ current.products.length }}</div>
                </div>
                <div class="ms-item">
                  <div class="ms-label">累计双录</div>
                  <div class="ms-value mono">{{ current.recordings }}</div>
                </div>
                <div class="ms-item">
                  <div class="ms-label">客户分</div>
                  <div class="ms-value mono">96</div>
                </div>
              </div>
            </div>
            <div class="m-tabs">
              <div
                v-for="t in detailTabs"
                :key="t.value"
                :class="['m-tab', detailTab === t.value && 'active']"
                @click="detailTab = t.value"
              >
                {{ t.icon }} {{ t.label }}
              </div>
            </div>
            <div class="m-body">
              <!-- 概览 -->
              <div v-if="detailTab === 'overview'" class="m-grid">
                <div class="info-block">
                  <h5>基本信息</h5>
                  <div class="info-list">
                    <div class="il"><span>性别</span><b>{{ current.gender || '男' }}</b></div>
                    <div class="il"><span>年龄</span><b>42 岁</b></div>
                    <div class="il"><span>职业</span><b>企业高管</b></div>
                    <div class="il"><span>年收入</span><b>¥ 80-100 万</b></div>
                    <div class="il"><span>风险等级</span><b>C{{ current.riskLevel }} · {{ current.riskName }}</b></div>
                    <div class="il"><span>评估日期</span><b>2026-07-15</b></div>
                  </div>
                </div>
                <div class="info-block">
                  <h5>风险评估历史</h5>
                  <div class="risk-timeline">
                    <div class="rt-item" v-for="(r, i) in 3" :key="i">
                      <div :class="['rt-dot', `c${current.riskLevel - i > 0 ? current.riskLevel - i : 1}`]"></div>
                      <div>
                        <div class="rt-label">C{{ current.riskLevel - i > 0 ? current.riskLevel - i : 1 }} 评估</div>
                        <div class="rt-time text-sm text-muted">{{ 2026 - i }}-0{{ 7 - i }}-15</div>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
              <!-- 产品 -->
              <div v-if="detailTab === 'products'">
                <div v-for="(p, i) in current.productDetails" :key="i" class="product-row">
                  <div class="pr-icon">💼</div>
                  <div class="pr-info">
                    <div class="pr-name">{{ p.name }}</div>
                    <div class="pr-meta text-sm text-muted">{{ p.code }} · R{{ p.risk }} · {{ p.holding }}</div>
                  </div>
                  <div class="pr-amount mono font-bold">¥{{ p.amount }}</div>
                </div>
              </div>
              <!-- 录像 -->
              <div v-if="detailTab === 'recordings'">
                <div v-for="(r, i) in current.recordingHistory" :key="i" class="rec-row">
                  <div :class="['rec-status', `s-${r.status}`]">
                    {{ r.statusLabel }}
                  </div>
                  <div class="rec-info">
                    <div class="rec-id mono text-sm">{{ r.id }}</div>
                    <div class="rec-time text-sm text-muted">{{ r.time }}</div>
                  </div>
                  <button class="btn btn-ghost btn-sm">查看</button>
                </div>
              </div>
              <!-- 联系 -->
              <div v-if="detailTab === 'contact'">
                <div class="contact-list">
                  <a v-for="(c, i) in contactHistory" :key="i" class="contact-row">
                    <div :class="['c-icon', `c-${c.type}`]">{{ c.icon }}</div>
                    <div class="c-info">
                      <div class="c-title">{{ c.title }}</div>
                      <div class="c-time text-sm text-muted">{{ c.time }}</div>
                    </div>
                    <div class="c-tag text-sm" :class="c.tagCls">{{ c.result }}</div>
                  </a>
                </div>
              </div>
            </div>
            <div class="m-footer">
              <button class="btn btn-ghost" @click="current = null">关闭</button>
              <button class="btn btn-primary" @click="quickRecord(current)">📞 发起双录</button>
            </div>
          </div>
        </div>
      </transition>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

// ============ 状态 ============
const showFilter = ref(true)
const page = ref(1)
const pageSize = 8
const selected = ref<string[]>([])
const current = ref<any>(null)
const detailTab = ref('overview')

const filters = ref({
  level: [1, 2],
  productType: '',
  assetScale: '',
  tags: [] as string[],
  lastActivity: ''
})

const tagOptions = ['VIP', '高净值', '潜力', '老客户', '推荐人', '犹豫']

const activeFilters = computed(() => {
  let n = 0
  if (filters.value.level.length !== 5) n++
  if (filters.value.productType) n++
  if (filters.value.assetScale) n++
  if (filters.value.tags.length) n++
  if (filters.value.lastActivity) n++
  return n
})

// ============ Mock 数据 ============
const customers = ref([
  { id: 'c1', name: '张志强', idHash: 'cust-hash-001', riskLevel: 1, riskName: '保守型', asset: '580,000', products: ['稳赢 3 号', '大额存单', '货币基金'], lastActivity: '今日 10:30', lastActivityTime: '2 小时前', tags: ['VIP', '高净值'], vip: true, riskAlert: false, recordings: 8, gender: '男',
    productDetails: [
      { name: '稳赢 3 号', code: 'BNK-FIN-2026Q3-001', risk: 2, holding: '6 个月', amount: '200,000' },
      { name: '大额存单', code: 'DEP-LRG-2025', risk: 1, holding: '12 个月', amount: '300,000' },
      { name: '货币基金', code: 'FND-MNY', risk: 1, holding: '3 个月', amount: '80,000' }
    ],
    recordingHistory: [
      { id: 'REC20260715-258001', time: '2026-07-15 10:30', status: 'archived', statusLabel: '已归档' },
      { id: 'REC20260628-257852', time: '2026-06-28 14:20', status: 'archived', statusLabel: '已归档' }
    ]
  },
  { id: 'c2', name: '王明华', idHash: 'cust-hash-002', riskLevel: 3, riskName: '平衡型', asset: '320,000', products: ['汇理财 7 日', '混合基金'], lastActivity: '昨日 16:45', lastActivityTime: '17 小时前', tags: ['老客户'], vip: false, riskAlert: true, recordings: 3, gender: '女',
    productDetails: [
      { name: '汇理财 7 日', code: 'FND-MIX-7D', risk: 2, holding: '7 天', amount: '120,000' },
      { name: '混合基金', code: 'FND-MIX-A', risk: 3, holding: '90 天', amount: '200,000' }
    ],
    recordingHistory: [
      { id: 'REC20260730-258103', time: '2026-07-30 16:45', status: 'review', statusLabel: '人工复核' }
    ]
  },
  { id: 'c3', name: '李雪梅', idHash: 'cust-hash-003', riskLevel: 2, riskName: '稳健型', asset: '1,250,000', products: ['结构性存款', '稳赢 4 号', '黄金 ETF'], lastActivity: '3 天前', lastActivityTime: '3 天前', tags: ['VIP', '高净值', '推荐人'], vip: true, riskAlert: false, recordings: 12, gender: '女',
    productDetails: [
      { name: '结构性存款', code: 'STR-DP-2026Q3', risk: 2, holding: '6 个月', amount: '500,000' },
      { name: '稳赢 4 号', code: 'BNK-FIN-2026Q3-004', risk: 2, holding: '12 个月', amount: '500,000' },
      { name: '黄金 ETF', code: 'FND-GLD', risk: 3, holding: '6 个月', amount: '250,000' }
    ],
    recordingHistory: []
  },
  { id: 'c4', name: '赵晓东', idHash: 'cust-hash-004', riskLevel: 4, riskName: '积极型', asset: '880,000', products: ['股票基金', '科技基金'], lastActivity: '今日 09:15', lastActivityTime: '3 小时前', tags: ['潜力', '推荐人'], vip: false, riskAlert: true, recordings: 5, gender: '男',
    productDetails: [
      { name: '股票基金', code: 'FND-STK-A', risk: 4, holding: '24 个月', amount: '500,000' },
      { name: '科技基金', code: 'FND-TECH', risk: 4, holding: '12 个月', amount: '380,000' }
    ],
    recordingHistory: []
  },
  { id: 'c5', name: '陈思琪', idHash: 'cust-hash-005', riskLevel: 2, riskName: '稳健型', asset: '760,000', products: ['保险理财', '国债'], lastActivity: '上周', lastActivityTime: '5 天前', tags: ['VIP', '高净值'], vip: true, riskAlert: false, recordings: 6, gender: '女',
    productDetails: [
      { name: '保险理财', code: 'INS-FIN-2026', risk: 3, holding: '36 个月', amount: '500,000' },
      { name: '国债', code: 'GOV-BND-2026', risk: 1, holding: '60 个月', amount: '260,000' }
    ],
    recordingHistory: []
  },
  { id: 'c6', name: '刘建国', idHash: 'cust-hash-006', riskLevel: 1, riskName: '保守型', asset: '1,800,000', products: ['大额存单', '货币基金', '国债'], lastActivity: '本月 5 日', lastActivityTime: '24 天前', tags: ['VIP', '高净值', '老客户'], vip: true, riskAlert: false, recordings: 15, gender: '男',
    productDetails: [],
    recordingHistory: []
  },
  { id: 'c7', name: '孙文博', idHash: 'cust-hash-007', riskLevel: 3, riskName: '平衡型', asset: '450,000', products: ['混合基金', '债券基金'], lastActivity: '本周一', lastActivityTime: '3 天前', tags: ['老客户'], vip: false, riskAlert: false, recordings: 4, gender: '男',
    productDetails: [],
    recordingHistory: []
  },
  { id: 'c8', name: '周丽华', idHash: 'cust-hash-008', riskLevel: 5, riskName: '激进型', asset: '220,000', products: ['股票基金', '加密基金'], lastActivity: '今日 14:20', lastActivityTime: '刚刚', tags: ['潜力'], vip: false, riskAlert: true, recordings: 2, gender: '女',
    productDetails: [],
    recordingHistory: []
  }
])

const filtered = computed(() => {
  let list = customers.value
  if (filters.value.level.length) {
    list = list.filter(c => filters.value.level.includes(c.riskLevel))
  }
  if (filters.value.tags.length) {
    list = list.filter(c => c.tags.some(t => filters.value.tags.includes(t)))
  }
  return list
})

const totalPages = computed(() => Math.ceil(filtered.value.length / pageSize))
const pagedList = computed(() => {
  const start = (page.value - 1) * pageSize
  return filtered.value.slice(start, start + pageSize)
})

// 选择
const allSelected = computed(() =>
  pagedList.value.length > 0 && pagedList.value.every(c => selected.value.includes(c.id))
)

function toggleAll() {
  if (allSelected.value) {
    selected.value = selected.value.filter(id => !pagedList.value.find(c => c.id === id))
  } else {
    const ids = pagedList.value.map(c => c.id)
    selected.value = Array.from(new Set([...selected.value, ...ids]))
  }
}
function toggleSelect(id: string) {
  if (selected.value.includes(id)) {
    selected.value = selected.value.filter(x => x !== id)
  } else {
    selected.value.push(id)
  }
}

function toggleLevel(l: number) {
  if (filters.value.level.includes(l)) {
    filters.value.level = filters.value.level.filter(x => x !== l)
  } else {
    filters.value.level.push(l)
  }
}
function toggleTag(t: string) {
  if (filters.value.tags.includes(t)) {
    filters.value.tags = filters.value.tags.filter(x => x !== t)
  } else {
    filters.value.tags.push(t)
  }
}
function resetFilter() {
  filters.value = { level: [1,2,3,4,5], productType: '', assetScale: '', tags: [], lastActivity: '' }
}
function applyFilter() {
  page.value = 1
}

function riskColor(level: number) {
  const m: any = { 1: 'success', 2: 'primary', 3: 'warning', 4: 'warning', 5: 'danger' }
  return m[level] || 'default'
}
function avatarColor(level: number) {
  const colors: any = { 1: '#d1fae5', 2: '#dbeafe', 3: '#fef3c7', 4: '#fed7aa', 5: '#fee2e2' }
  return colors[level] || '#f0f2f7'
}

const detailTabs = [
  { value: 'overview', label: '概览', icon: '📋' },
  { value: 'products', label: '产品', icon: '💼' },
  { value: 'recordings', label: '录像', icon: '🎥' },
  { value: 'contact', label: '联系', icon: '📞' }
]

const contactHistory = [
  { icon: '📞', title: '电话沟通: 稳赢 4 号', time: '今日 10:30', result: '已成交', tagCls: 'text-success', type: 'phone' },
  { icon: '📤', title: '推送话术: 大额存单', time: '昨日 16:00', result: '已查看', tagCls: 'text-info', type: 'push' },
  { icon: '💬', title: '在线咨询', time: '2 天前', result: '已回复', tagCls: 'text-success', type: 'chat' },
  { icon: '📞', title: '电话回访', time: '上周', result: '未接通', tagCls: 'text-warning', type: 'phone' }
]

// ============ 操作 ============
function onRowClick(c: any) {
  current.value = c
  detailTab.value = 'overview'
}
function newCustomer() { alert('新建客户 (演示)') }
function exportCSV() { alert('导出 CSV (演示)') }
function quickCall(c: any) { alert(`呼叫 ${c.name} (演示)`) }
function quickPush(c: any) { router.push('/pc/filepush') }
function quickRecord(c: any) { router.push('/pc/bilateral') }
function batchPush() { alert(`批量推送 ${selected.value.length} 个客户`) }
function batchTag() { alert(`批量打标 ${selected.value.length} 个客户`) }
function batchExport() { alert(`导出 ${selected.value.length} 个客户`) }
function batchNotify() { alert(`群发 ${selected.value.length} 个客户`) }
</script>

<style lang="scss" scoped>
@import '@/styles/agent-theme.scss';

// ============ 筛选条 ============
.filter-bar { margin-bottom: 16px; padding: 16px 20px; }
.filter-grid {
  display: grid;
  grid-template-columns: 2fr 1fr 1fr 2fr 1fr;
  gap: 16px;
  margin-bottom: 12px;
}
@media (max-width: 1280px) {
  .filter-grid { grid-template-columns: 1fr 1fr; }
}
.filter-item label {
  display: block;
  font-size: 12px;
  color: var(--text-3);
  font-weight: 600;
  margin-bottom: 6px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}
.filter-chips { display: flex; gap: 6px; flex-wrap: wrap; }
.chip {
  padding: 4px 10px;
  font-size: 12px;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  cursor: pointer;
  color: var(--text-2);
  transition: all 0.15s;
  &:hover { background: white; }
  &.active {
    background: var(--accent);
    color: white;
    border-color: var(--accent);
  }
  &.chip-success.active { background: var(--success); border-color: var(--success); }
  &.chip-primary.active { background: var(--info); border-color: var(--info); }
  &.chip-warning.active { background: var(--warning); border-color: var(--warning); }
  &.chip-danger.active { background: var(--danger); border-color: var(--danger); }
}
.input-sm { padding: 5px 10px; font-size: 12px; }
.filter-actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  border-top: 1px solid var(--border-light);
  padding-top: 12px;
}

// ============ 批量操作条 ============
.batch-bar {
  background: linear-gradient(90deg, var(--primary) 0%, var(--primary-light) 100%);
  color: white;
  padding: 10px 16px;
  border-radius: var(--radius);
  margin-bottom: 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  animation: slideDown 0.2s;
}
.batch-info {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
}
.batch-count {
  font-size: 18px;
  font-weight: 700;
  color: var(--accent-light);
  font-family: 'JetBrains Mono', monospace;
}
.btn-text { background: transparent; border: none; color: white; cursor: pointer; padding: 2px 6px; opacity: 0.7; }
.batch-actions { display: flex; gap: 6px; }
.batch-actions .btn-ghost {
  background: rgba(255,255,255,0.1);
  color: white;
  border-color: rgba(255,255,255,0.2);
  &:hover { background: rgba(255,255,255,0.2); }
}

@keyframes slideDown {
  from { opacity: 0; transform: translateY(-8px); }
  to { opacity: 1; transform: translateY(0); }
}

// ============ 客户列表 ============
.customer-cell { display: flex; align-items: center; gap: 10px; }
.cc-avatar {
  width: 36px; height: 36px;
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-weight: 700;
  font-size: 14px;
  color: var(--text-1);
  flex-shrink: 0;
}
.cc-name { font-weight: 600; color: var(--text-1); display: flex; align-items: center; gap: 4px; }

.product-stack { display: flex; gap: 4px; flex-wrap: wrap; }
.product-chip {
  font-size: 11px;
  padding: 2px 6px;
  background: var(--bg);
  border-radius: 3px;
  color: var(--text-2);
}
.product-more {
  font-size: 11px;
  color: var(--text-3);
  font-weight: 600;
}
.tag-stack { display: flex; gap: 3px; flex-wrap: wrap; }
.row-actions { display: flex; gap: 2px; }

// ============ 复选框 ============
.checkbox {
  display: inline-flex;
  align-items: center;
  cursor: pointer;
  input { display: none; }
  span {
    width: 16px; height: 16px;
    border: 1.5px solid var(--border);
    border-radius: 3px;
    display: flex; align-items: center; justify-content: center;
    background: white;
    transition: all 0.15s;
  }
  input:checked + span {
    background: var(--accent);
    border-color: var(--accent);
    &::after {
      content: '✓';
      color: white;
      font-size: 11px;
      font-weight: 700;
    }
  }
}

// ============ 分页 ============
.pagination {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 20px;
  border-top: 1px solid var(--border-light);
  background: var(--bg);
  border-radius: 0 0 var(--radius-lg) var(--radius-lg);
}
.pg-buttons { display: flex; gap: 4px; }

// ============ 360° 弹窗 ============
.modal-360 {
  background: white;
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xl);
  max-width: 800px;
  width: 100%;
  max-height: 90vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.m-header {
  padding: 24px;
  color: var(--text-1);
  position: relative;
  flex-shrink: 0;
}
.m-close {
  position: absolute;
  top: 12px; right: 12px;
  width: 28px; height: 28px;
  background: rgba(255,255,255,0.5);
  border: none;
  border-radius: var(--radius);
  font-size: 18px;
  cursor: pointer;
  color: var(--text-1);
  &:hover { background: white; }
}
.m-avatar {
  width: 64px; height: 64px;
  border-radius: 50%;
  background: white;
  display: flex; align-items: center; justify-content: center;
  font-size: 28px;
  font-weight: 700;
  margin-bottom: 12px;
  box-shadow: var(--shadow);
}
.m-name {
  font-size: 24px;
  font-weight: 700;
  display: flex;
  align-items: center;
  gap: 8px;
}
.m-id { color: var(--text-2); margin-top: 4px; }
.m-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid rgba(0,0,0,0.06);
}
.ms-label { font-size: 11px; color: var(--text-2); }
.ms-value { font-size: 18px; font-weight: 700; margin-top: 2px; }

.m-tabs {
  display: flex;
  border-bottom: 1px solid var(--border);
  background: white;
  flex-shrink: 0;
}
.m-tab {
  flex: 1;
  padding: 12px;
  text-align: center;
  font-size: 13px;
  color: var(--text-2);
  cursor: pointer;
  border-bottom: 2px solid transparent;
  transition: all 0.15s;
  &:hover { color: var(--text-1); background: var(--bg); }
  &.active {
    color: var(--accent);
    border-bottom-color: var(--accent);
    font-weight: 600;
  }
}
.m-body {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}
.m-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}
@media (max-width: 768px) { .m-grid { grid-template-columns: 1fr; } }

.info-block h5 {
  font-size: 12px;
  color: var(--text-3);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  margin: 0 0 12px;
  font-weight: 600;
}
.info-list { display: flex; flex-direction: column; gap: 8px; }
.il {
  display: flex;
  justify-content: space-between;
  padding: 8px 12px;
  background: var(--bg);
  border-radius: var(--radius-sm);
  font-size: 13px;
  span { color: var(--text-3); }
  b { color: var(--text-1); font-weight: 600; }
}
.risk-timeline { display: flex; flex-direction: column; gap: 8px; position: relative; padding-left: 16px; }
.risk-timeline::before {
  content: '';
  position: absolute;
  left: 6px; top: 8px; bottom: 8px;
  width: 2px;
  background: var(--border);
}
.rt-item { display: flex; align-items: center; gap: 12px; position: relative; }
.rt-dot {
  width: 12px; height: 12px;
  border-radius: 50%;
  background: var(--text-3);
  position: absolute;
  left: -16px;
  border: 2px solid white;
  box-shadow: 0 0 0 1px var(--border);
  &.c1 { background: var(--success); }
  &.c2 { background: var(--info); }
  &.c3 { background: var(--warning); }
  &.c4 { background: #ea580c; }
  &.c5 { background: var(--danger); }
}
.rt-label { font-size: 13px; font-weight: 500; }

.product-row, .rec-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--border-light);
  &:last-child { border-bottom: none; }
}
.pr-icon { font-size: 24px; }
.pr-info { flex: 1; }
.pr-name { font-weight: 600; color: var(--text-1); font-size: 13px; }
.rec-status {
  padding: 2px 8px;
  font-size: 11px;
  font-weight: 600;
  border-radius: var(--radius-sm);
  &.s-archived { background: var(--success-light); color: var(--success); }
  &.s-review { background: var(--warning-light); color: var(--warning); }
  &.s-active { background: var(--info-light); color: var(--info); }
}
.rec-info { flex: 1; }

.contact-list { display: flex; flex-direction: column; gap: 4px; }
.contact-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  border-radius: var(--radius);
  cursor: pointer;
  text-decoration: none;
  color: inherit;
  &:hover { background: var(--bg); }
}
.c-icon {
  width: 32px; height: 32px;
  border-radius: var(--radius);
  display: flex; align-items: center; justify-content: center;
  font-size: 14px;
  background: var(--bg);
}
.c-icon.c-phone { background: rgba(59, 130, 246, 0.1); }
.c-icon.c-push { background: rgba(184, 134, 11, 0.1); }
.c-icon.c-chat { background: rgba(16, 185, 129, 0.1); }
.c-info { flex: 1; }
.c-title { font-size: 13px; font-weight: 500; }
.c-tag { font-weight: 600; }

.m-footer {
  padding: 12px 20px;
  border-top: 1px solid var(--border);
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  background: var(--bg);
  flex-shrink: 0;
}

// 过渡
.filter-enter-active, .filter-leave-active { transition: all 0.2s; }
.filter-enter-from, .filter-leave-to { opacity: 0; transform: translateY(-8px); }
</style>
