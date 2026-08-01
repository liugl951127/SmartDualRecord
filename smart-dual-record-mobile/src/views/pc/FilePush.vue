<template>
  <div class="page-container">
    <div class="page-header-bar">
      <div class="page-title-group">
        <div class="page-icon">📤</div>
        <div>
          <h1 class="page-title">文件推送</h1>
          <div class="page-subtitle">向客户推送文件 (合同/话术/产品说明)</div>
        </div>
      </div>
      <div class="page-actions">
        <button class="btn btn-ghost" @click="showTemplates = !showTemplates">📚 模板库</button>
        <button class="btn btn-primary" @click="onNewPush">+ 新建推送</button>
      </div>
    </div>

    <div class="page-body fade-in">
      <div class="grid grid-3-1">
        <!-- 左: 推送列表 + 客户选择 -->
        <div>
          <!-- 客户选择 -->
          <div class="card mb-12">
            <div class="card-header">
              <h3 class="card-title">👥 选择客户</h3>
              <span class="tag tag-info">已选 {{ selectedCustomers.length }}</span>
            </div>
            <div class="customer-grid">
              <div
                v-for="c in customers"
                :key="c.id"
                :class="['cust-card', selectedCustomers.includes(c.id) && 'selected']"
                @click="toggleCustomer(c.id)"
              >
                <div class="cc-avatar" :style="{ background: avatarColor(c.riskLevel) }">{{ c.name.charAt(0) }}</div>
                <div class="cc-info">
                  <div class="cc-name">
                    {{ c.name }}
                    <span v-if="c.vip" class="tag tag-accent">VIP</span>
                  </div>
                  <div class="cc-meta text-sm text-muted">{{ c.products[0] }}</div>
                </div>
                <div v-if="selectedCustomers.includes(c.id)" class="cc-check">✓</div>
              </div>
            </div>
          </div>

          <!-- 快捷模板 -->
          <div class="card mb-12">
            <div class="card-header">
              <h3 class="card-title">📚 推送模板 (一键使用)</h3>
            </div>
            <div class="template-grid">
              <a
                v-for="t in templates"
                :key="t.id"
                :class="['tpl', `tpl-${t.color}`]"
                @click="applyTemplate(t)"
              >
                <div class="tpl-icon">{{ t.icon }}</div>
                <div class="tpl-name">{{ t.name }}</div>
                <div class="tpl-desc text-sm text-muted">{{ t.desc }}</div>
                <div class="tpl-meta">
                  <span class="text-sm text-muted">{{ t.files.length }} 文件</span>
                  <span class="tpl-use">使用 →</span>
                </div>
              </a>
            </div>
          </div>
        </div>

        <!-- 右: 推送工作区 + 历史 -->
        <div>
          <div class="card mb-12">
            <div class="card-header">
              <h3 class="card-title">📤 推送工作区</h3>
            </div>
            <div class="push-area">
              <!-- 拖拽上传 -->
              <div :class="['drop-zone', dragOver && 'drag-over']" @dragover.prevent="dragOver = true" @dragleave="dragOver = false" @drop.prevent="onDrop">
                <div class="dz-icon">📁</div>
                <div class="dz-text">拖拽文件到此处</div>
                <div class="dz-sub text-sm text-muted">支持 PDF/Word/图片</div>
                <button class="btn btn-ghost btn-sm mt-8" @click="mockFilePick">选择文件</button>
              </div>

              <!-- 已选文件 -->
              <div v-if="pushFiles.length" class="file-list">
                <div v-for="(f, i) in pushFiles" :key="i" class="file-item">
                  <span class="file-icon">{{ f.icon }}</span>
                  <div class="file-info">
                    <div class="file-name">{{ f.name }}</div>
                    <div class="file-size text-sm text-muted">{{ f.size }}</div>
                  </div>
                  <button class="btn btn-text btn-sm" @click="pushFiles.splice(i, 1)">×</button>
                </div>
              </div>

              <!-- 推送消息 -->
              <div class="push-msg-area">
                <label class="text-sm text-secondary">附加消息 (可选)</label>
                <textarea class="input mt-4" v-model="pushMsg" rows="3" placeholder="例: 张先生您好, 附件是稳赢 3 号的产品说明书, 请查阅后回复确认..."></textarea>
              </div>

              <!-- 推送按钮 -->
              <button class="btn btn-accent btn-block btn-lg mt-12" :disabled="!pushFiles.length || !selectedCustomers.length" @click="onPush">
                📤 推送给 {{ selectedCustomers.length }} 位客户
              </button>
            </div>
          </div>

          <!-- 推送历史 -->
          <div class="card">
            <div class="card-header">
              <h3 class="card-title">📋 推送历史</h3>
              <button class="btn btn-text btn-sm">全部</button>
            </div>
            <div class="hist-list">
              <div v-for="h in history" :key="h.id" class="hist-item">
                <div :class="['hist-status', `s-${h.status}`]">{{ h.statusLabel }}</div>
                <div class="hist-info">
                  <div class="hist-title">{{ h.title }}</div>
                  <div class="hist-meta text-sm text-muted">
                    {{ h.customer }} · {{ h.time }} · {{ h.fileCount }} 文件
                  </div>
                </div>
                <div class="hist-rate text-sm mono">{{ h.readRate }}% 已读</div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const showTemplates = ref(false)
const selectedCustomers = ref<string[]>(['c1', 'c2'])
const pushFiles = ref<any[]>([
  { name: '稳赢 3 号说明书.pdf', size: '2.4 MB', icon: '📕' }
])
const pushMsg = ref('')
const dragOver = ref(false)

const customers = [
  { id: 'c1', name: '张志强', riskLevel: 1, vip: true, products: ['稳赢 3 号'] },
  { id: 'c2', name: '王明华', riskLevel: 3, vip: false, products: ['汇理财 7 日'] },
  { id: 'c3', name: '李雪梅', riskLevel: 2, vip: true, products: ['结构性存款'] },
  { id: 'c4', name: '赵晓东', riskLevel: 4, vip: false, products: ['股票基金'] },
  { id: 'c5', name: '陈思琪', riskLevel: 2, vip: true, products: ['保险理财'] },
  { id: 'c6', name: '刘建国', riskLevel: 1, vip: true, products: ['大额存单'] }
]

const templates = [
  { id: 't1', name: '产品说明书', desc: '银行理财标准模板', files: ['说明书.pdf', '风险揭示书.pdf'], icon: '📘', color: 'primary' },
  { id: 't2', name: '合同签署包', desc: '销售合同 + 风险协议', files: ['合同.pdf', '协议.pdf', '回执.docx'], icon: '📜', color: 'accent' },
  { id: 't3', name: '回访问卷', desc: 'NPS 调查 + 满意度', files: ['问卷.pdf'], icon: '📋', color: 'success' },
  { id: 't4', name: '生日祝福', desc: '节假日关怀素材', files: ['祝福.png'], icon: '🎂', color: 'warning' }
]

const history = [
  { id: 'h1', title: '稳赢 3 号 说明书', customer: '张志强', time: '今日 10:30', fileCount: 2, status: 'read', statusLabel: '已读', readRate: 100 },
  { id: 'h2', title: '合同签署包', customer: '王明华', time: '昨日 16:45', fileCount: 3, status: 'pending', statusLabel: '待签', readRate: 80 },
  { id: 'h3', title: '结构性存款 协议', customer: '李雪梅', time: '昨日 14:20', fileCount: 2, status: 'signed', statusLabel: '已签', readRate: 100 },
  { id: 'h4', title: '股票基金 风险揭示', customer: '赵晓东', time: '2 天前', fileCount: 1, status: 'read', statusLabel: '已读', readRate: 100 }
]

function toggleCustomer(id: string) {
  if (selectedCustomers.value.includes(id)) {
    selectedCustomers.value = selectedCustomers.value.filter(x => x !== id)
  } else {
    selectedCustomers.value.push(id)
  }
}

function applyTemplate(t: any) {
  pushFiles.value = t.files.map((f: string) => ({
    name: f, size: (Math.random() * 3 + 0.5).toFixed(1) + ' MB', icon: f.endsWith('.pdf') ? '📕' : f.endsWith('.docx') ? '📘' : '🖼️'
  }))
}

function onDrop(e: DragEvent) {
  dragOver.value = false
  // 模拟
  pushFiles.value.push({ name: '新文件.pdf', size: '1.2 MB', icon: '📕' })
}
function mockFilePick() {
  pushFiles.value.push({ name: '新文件.pdf', size: '1.2 MB', icon: '📕' })
}
function onNewPush() {
  pushFiles.value = []
  pushMsg.value = ''
}
function onPush() {
  alert(`已推送 ${pushFiles.value.length} 个文件给 ${selectedCustomers.value.length} 位客户`)
  onNewPush()
}

function avatarColor(level: number) {
  const colors: any = { 1: '#d1fae5', 2: '#dbeafe', 3: '#fef3c7', 4: '#fed7aa', 5: '#fee2e2' }
  return colors[level] || '#f0f2f7'
}
</script>

<style lang="scss" scoped>
@use '@/styles/agent-theme.scss' as *;

.mb-12 { margin-bottom: 12px; }
.mt-4 { margin-top: 4px; }
.mt-8 { margin-top: 8px; }
.mt-12 { margin-top: 12px; }

.grid-3-1 {
  display: grid;
  grid-template-columns: 1fr 1.2fr;
  gap: 12px;
}
@media (max-width: 1200px) { .grid-3-1 { grid-template-columns: 1fr; } }

// 客户网格
.customer-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 6px;
  padding: 12px;
}
.cust-card {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border: 1px solid var(--border-light);
  border-radius: var(--radius);
  cursor: pointer;
  transition: all 0.15s;
  position: relative;
  &:hover { border-color: var(--accent); }
  &.selected {
    background: rgba(184, 134, 11, 0.05);
    border-color: var(--accent);
  }
}
.cc-avatar {
  width: 32px; height: 32px;
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-weight: 700;
  font-size: 13px;
  flex-shrink: 0;
}
.cc-info { flex: 1; min-width: 0; }
.cc-name { font-size: 12px; font-weight: 600; display: flex; align-items: center; gap: 4px; }
.cc-meta { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.cc-check {
  position: absolute;
  top: 4px; right: 4px;
  width: 18px; height: 18px;
  background: var(--accent);
  color: white;
  border-radius: 50%;
  font-size: 11px;
  font-weight: 700;
  display: flex; align-items: center; justify-content: center;
}

// 模板
.template-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 8px;
  padding: 12px;
}
.tpl {
  display: block;
  padding: 12px;
  border: 1px solid var(--border-light);
  border-radius: var(--radius);
  cursor: pointer;
  transition: all 0.15s;
  text-decoration: none;
  color: inherit;
  &:hover { transform: translateY(-2px); box-shadow: var(--shadow); }
  &.tpl-primary { border-left: 3px solid var(--info); }
  &.tpl-accent { border-left: 3px solid var(--accent); }
  &.tpl-success { border-left: 3px solid var(--success); }
  &.tpl-warning { border-left: 3px solid var(--warning); }
}
.tpl-icon { font-size: 20px; margin-bottom: 4px; }
.tpl-name { font-size: 13px; font-weight: 600; }
.tpl-desc { margin-top: 2px; }
.tpl-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 6px;
  padding-top: 6px;
  border-top: 1px solid var(--border-light);
}
.tpl-use { font-size: 11px; color: var(--accent); font-weight: 600; }

// 推送工作区
.push-area { padding: 16px 20px; }
.drop-zone {
  border: 2px dashed var(--border);
  border-radius: var(--radius);
  padding: 24px;
  text-align: center;
  transition: all 0.15s;
  cursor: pointer;
  &:hover, &.drag-over { border-color: var(--accent); background: rgba(184, 134, 11, 0.03); }
}
.dz-icon { font-size: 32px; }
.dz-text { font-size: 13px; font-weight: 500; margin-top: 4px; }
.dz-sub { margin-top: 2px; }

.file-list { margin-top: 12px; }
.file-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  background: var(--bg);
  border-radius: var(--radius);
  margin-bottom: 4px;
}
.file-icon { font-size: 20px; }
.file-info { flex: 1; }
.file-name { font-size: 12px; font-weight: 500; }
.file-size { color: var(--text-3); }

.push-msg-area { margin-top: 12px; }

// 历史
.hist-list { padding: 4px 0; max-height: 400px; overflow-y: auto; }
.hist-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
  border-bottom: 1px solid var(--border-light);
  cursor: pointer;
  &:hover { background: var(--bg); }
  &:last-child { border-bottom: none; }
}
.hist-status {
  padding: 2px 8px;
  font-size: 10px;
  font-weight: 600;
  border-radius: var(--radius-sm);
  &.s-read { background: var(--info-light); color: var(--info); }
  &.s-pending { background: var(--warning-light); color: var(--warning); }
  &.s-signed { background: var(--success-light); color: var(--success); }
}
.hist-info { flex: 1; }
.hist-title { font-size: 12px; font-weight: 600; color: var(--text-1); }
.hist-rate {
  font-size: 11px;
  color: var(--text-2);
  font-weight: 600;
}
</style>
