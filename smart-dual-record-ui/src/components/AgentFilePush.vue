<script setup lang="ts">
/**
 * 坐席推送文件面板 v1.5
 *
 * 功能: 线上双录中, 坐席实时向客户推送文件
 *  - 文件模板库 (产品说明书/风险揭示书/合同/宣传图)
 *  - 自定义上传
 *  - 推送状态实时跟踪 (PUSHED / VIEWED / SIGNED / REJECTED)
 *  - 重新推送 / 撤回
 *  - WebSocket 实时通知客户
 *
 * 数据流:
 *  坐席选文件 → POST /api/v1/file/push
 *           → 服务端落库 + WebSocket 推送给客户
 *           → 客户查看/签署 → WebSocket 回传坐席
 */
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { fileApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'

// ============================================================================
// 1. 状态
// ============================================================================
const businessId = ref('BNK20260801-900001')
const operatorId = ref('teller-wang-001')
const operatorName = ref('王经理')

const templates = ref<any[]>([])
const pushedFiles = ref<any[]>([])
const selectedTemplate = ref<any>(null)
const customFileName = ref('')
const customFileType = ref('PDF')
const customFileCategory = ref('OTHER')
const remark = ref('')
const loading = ref(false)
const pushing = ref(false)

// 上传自定义文件
const uploadRef = ref<HTMLInputElement | null>(null)
const customFile = ref<File | null>(null)
const customFileDataUrl = ref<string>('')

// 类别选项
const CATEGORIES = [
  { value: 'BROCHURE', label: '产品宣传', icon: '📄', color: 'var(--blue)' },
  { value: 'DISCLOSURE', label: '风险揭示', icon: '⚠️', color: 'var(--accent-2)' },
  { value: 'CONTRACT', label: '合同', icon: '📜', color: 'var(--accent)' },
  { value: 'ID_CARD', label: '身份证', icon: '🆔', color: 'var(--ink-3)' },
  { value: 'OTHER', label: '其他', icon: '📎', color: 'var(--ink-3)' }
]

// 状态颜色
const STATUS_META: Record<string, { label: string; color: string; icon: string }> = {
  PUSHED: { label: '已推送', color: 'var(--blue)', icon: '📤' },
  VIEWED: { label: '已查看', color: 'var(--accent)', icon: '👁' },
  SIGNED: { label: '已签署', color: 'var(--green)', icon: '✅' },
  REJECTED: { label: '已拒签', color: 'var(--accent-2)', icon: '❌' }
}

// ============================================================================
// 2. WebSocket 接收 (客户回执)
// ============================================================================
let ws: WebSocket | null = null
const wsConnected = ref(false)

function connectWs() {
  const proto = window.location.protocol === 'https:' ? 'wss' : 'ws'
  const host = window.location.host || 'localhost:8080'
  const url = `${proto}://${host}/ws/recording/${businessId.value}`
  try {
    ws = new WebSocket(url)
    ws.onopen = () => {
      wsConnected.value = true
      console.log('WS connected:', url)
    }
    ws.onclose = () => {
      wsConnected.value = false
      setTimeout(connectWs, 3000)  // 自动重连
    }
    ws.onerror = () => { wsConnected.value = false }
    ws.onmessage = (e) => {
      try {
        const msg = JSON.parse(e.data)
        if (msg.type === 'FILE_VIEWED' || msg.type === 'FILE_SIGNED' || msg.type === 'FILE_REJECTED' || msg.type === 'FILE_PUSHED') {
          // 刷新列表
          loadPushedFiles()
          if (msg.type === 'FILE_VIEWED') ElMessage.info(`👁 客户已查看: ${msg.fileName}`)
          if (msg.type === 'FILE_SIGNED') ElMessage.success(`✅ 客户已签署: ${msg.fileName}`)
          if (msg.type === 'FILE_REJECTED') ElMessage.warning(`❌ 客户已拒签: ${msg.fileName}`)
        }
      } catch (err) { /* ignore */ }
    }
  } catch (e) { console.warn('WS failed', e) }
}

// ============================================================================
// 3. 数据加载
// ============================================================================
async function loadTemplates() {
  try {
    templates.value = await fileApi.templates()
  } catch (e) {
    // 兜底数据
    templates.value = [
      { id: 'TPL-1', name: '产品说明书.pdf', type: 'PDF', category: 'BROCHURE', size: 1024 * 1024, icon: '📄' },
      { id: 'TPL-2', name: '风险揭示书.pdf', type: 'PDF', category: 'DISCLOSURE', size: 512 * 1024, icon: '⚠️' },
      { id: 'TPL-3', name: '理财合同.pdf', type: 'PDF', category: 'CONTRACT', size: 1024 * 1024 * 3, icon: '📜' }
    ]
  }
}

async function loadPushedFiles() {
  loading.value = true
  try {
    pushedFiles.value = await fileApi.list(businessId.value)
  } catch (e) {
    pushedFiles.value = []
  } finally {
    loading.value = false
  }
}

// ============================================================================
// 4. 推送操作
// ============================================================================
async function pushTemplate(t: any) {
  selectedTemplate.value = t
  await doPush({
    businessId: businessId.value,
    fileName: t.name,
    fileType: t.type,
    fileCategory: t.category,
    fileUrl: t.url || `/static/templates/${t.id}.pdf`,
    fileSize: t.size,
    remark: remark.value
  })
}

function onCustomFile(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  customFile.value = file
  customFileName.value = file.name
  // 转 base64 预览
  const reader = new FileReader()
  reader.onload = () => { customFileDataUrl.value = reader.result as string }
  reader.readAsDataURL(file)
}

async function pushCustom() {
  if (!customFile.value) {
    ElMessage.warning('请先选择文件')
    return
  }
  await doPush({
    businessId: businessId.value,
    fileName: customFileName.value,
    fileType: customFile.value.type.split('/')[1]?.toUpperCase() || 'OTHER',
    fileCategory: customFileCategory.value,
    fileUrl: customFileDataUrl.value,
    fileSize: customFile.value.size,
    remark: remark.value
  })
}

async function doPush(req: any) {
  pushing.value = true
  try {
    const result = await fileApi.push(req, operatorId.value)
    ElMessage.success(`📤 已推送: ${result.fileName}`)
    await loadPushedFiles()
    // 清空
    selectedTemplate.value = null
    customFile.value = null
    customFileDataUrl.value = ''
    remark.value = ''
  } catch (e: any) {
    ElMessage.error('推送失败: ' + e.message)
  } finally {
    pushing.value = false
  }
}

async function resend(file: any) {
  try {
    await ElMessageBox.confirm(`重新推送 "${file.fileName}" 给客户?`, '重新推送', { type: 'info' })
  } catch { return }
  await doPush({
    businessId: file.businessId,
    fileName: file.fileName,
    fileType: file.fileType,
    fileCategory: file.fileCategory,
    fileUrl: file.fileUrl,
    fileSize: file.fileSize,
    remark: '重新推送: ' + (file.remark || '')
  })
}

// ============================================================================
// 5. 计算属性
// ============================================================================
const stats = computed(() => ({
  total: pushedFiles.value.length,
  pushed: pushedFiles.value.filter(f => f.status === 'PUSHED').length,
  viewed: pushedFiles.value.filter(f => f.status === 'VIEWED').length,
  signed: pushedFiles.value.filter(f => f.status === 'SIGNED').length,
  rejected: pushedFiles.value.filter(f => f.status === 'REJECTED').length
}))

function getCategoryMeta(cat: string) {
  return CATEGORIES.find(c => c.value === cat) || CATEGORIES[4]
}

function formatSize(bytes?: number) {
  if (!bytes) return '—'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(1) + ' MB'
}

function formatTime(t: any) {
  if (!t) return '—'
  return new Date(t).toLocaleString('zh-CN', { hour12: false })
}

// ============================================================================
// 6. 生命周期
// ============================================================================
onMounted(async () => {
  await loadTemplates()
  await loadPushedFiles()
  connectWs()
})

onUnmounted(() => {
  if (ws) ws.close()
})
</script>

<template>
  <div>
    <!-- 顶部: 业务信息 + WebSocket 状态 -->
    <div class="card">
      <h3 class="card-title">
        <span>
          <el-icon><Share /></el-icon>
          坐席推送文件 · 线上双录
        </span>
        <span class="actions">
          <el-tag :type="wsConnected ? 'success' : 'info'" size="small">
            {{ wsConnected ? '🟢 WebSocket 已连接' : '⚪ WebSocket 未连接' }}
          </el-tag>
        </span>
      </h3>
      <el-row :gutter="12">
        <el-col :span="6">
          <div class="info-cell">
            <div class="label">业务 ID</div>
            <div class="value mono">{{ businessId }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="info-cell">
            <div class="label">坐席</div>
            <div class="value">{{ operatorName }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="info-cell">
            <div class="label">已推送</div>
            <div class="value">{{ stats.total }} 个文件</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="info-cell">
            <div class="label">已签署</div>
            <div class="value" :style="{ color: 'var(--green)' }">{{ stats.signed }} / {{ stats.total }}</div>
          </div>
        </el-col>
      </el-row>
    </div>

    <el-row :gutter="16">
      <!-- 左侧: 文件库 -->
      <el-col :span="10">
        <div class="card">
          <h3 class="card-title">
            <span>📚 文件模板库</span>
            <span class="actions">
              <el-button size="small" @click="loadTemplates">
                <el-icon><Refresh /></el-icon>
              </el-button>
            </span>
          </h3>
          <div class="template-grid">
            <div
              v-for="t in templates"
              :key="t.id"
              class="template-card"
              :class="{ selected: selectedTemplate?.id === t.id }"
              @click="pushTemplate(t)"
            >
              <div class="t-icon">{{ t.icon || (t.type === 'PDF' ? '📄' : t.type === 'PNG' ? '🖼' : '📎') }}</div>
              <div class="t-info">
                <div class="t-name">{{ t.name }}</div>
                <div class="t-meta">
                  <el-tag size="small" :style="{ color: getCategoryMeta(t.category).color }">
                    {{ getCategoryMeta(t.category).label }}
                  </el-tag>
                  <span class="t-size">{{ formatSize(t.size) }}</span>
                </div>
              </div>
              <el-button size="small" type="primary" :loading="pushing">
                <el-icon><Promotion /></el-icon>推送
              </el-button>
            </div>
          </div>
        </div>

        <div class="card">
          <h3 class="card-title">📎 自定义上传</h3>
          <div class="upload-area" @click="uploadRef?.click()">
            <div v-if="!customFile" class="upload-placeholder">
              <el-icon class="upload-icon"><UploadFilled /></el-icon>
              <div>点击选择文件</div>
            </div>
            <div v-else class="upload-preview">
              <div class="up-icon">{{ customFile.type.startsWith('image/') ? '🖼' : '📄' }}</div>
              <div class="up-info">
                <div><strong>{{ customFile.name }}</strong></div>
                <div class="up-size">{{ formatSize(customFile.size) }}</div>
              </div>
              <el-button size="small" type="danger" plain @click.stop="customFile = null">
                <el-icon><Delete /></el-icon>
              </el-button>
            </div>
            <input ref="uploadRef" type="file" style="display:none" @change="onCustomFile" />
          </div>
          <el-form label-width="80px" size="default" style="margin-top: 12px;">
            <el-form-item label="分类">
              <el-select v-model="customFileCategory" style="width: 100%;">
                <el-option v-for="c in CATEGORIES" :key="c.value" :value="c.value" :label="`${c.icon} ${c.label}`" />
              </el-select>
            </el-form-item>
            <el-form-item label="备注">
              <el-input v-model="remark" type="textarea" :rows="2" placeholder="可选, 客户将看到此备注" />
            </el-form-item>
            <el-button type="primary" :loading="pushing" :disabled="!customFile" @click="pushCustom" style="width: 100%;">
              <el-icon><Promotion /></el-icon>推送给客户
            </el-button>
          </el-form>
        </div>
      </el-col>

      <!-- 右侧: 已推送列表 -->
      <el-col :span="14">
        <div class="card">
          <h3 class="card-title">
            <span>📋 已推送文件 ({{ pushedFiles.length }})</span>
            <span class="actions">
              <el-button size="small" @click="loadPushedFiles">
                <el-icon><Refresh /></el-icon>
              </el-button>
            </span>
          </h3>

          <el-table :data="pushedFiles" stripe v-loading="loading">
            <el-table-column label="文件" min-width="240">
              <template #default="{ row }">
                <div class="file-cell">
                  <div class="file-icon">{{ getCategoryMeta(row.fileCategory).icon }}</div>
                  <div class="file-info">
                    <div class="file-name">{{ row.fileName }}</div>
                    <div class="file-meta">
                      <el-tag size="small" :style="{ color: getCategoryMeta(row.fileCategory).color }">
                        {{ getCategoryMeta(row.fileCategory).label }}
                      </el-tag>
                      <span class="file-size">{{ formatSize(row.fileSize) }}</span>
                    </div>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <el-tag :style="{ color: STATUS_META[row.status]?.color, fontWeight: 600 }">
                  {{ STATUS_META[row.status]?.icon }} {{ STATUS_META[row.status]?.label }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="推送时间" width="160">
              <template #default="{ row }">
                <span class="time-cell">{{ formatTime(row.pushedAt) }}</span>
              </template>
            </el-table-column>
            <el-table-column label="查看/签署" width="140">
              <template #default="{ row }">
                <div v-if="row.viewedAt" style="font-size: 11px;">
                  👁 {{ formatTime(row.viewedAt) }}
                </div>
                <div v-if="row.signedAt" style="font-size: 11px; color: var(--green);">
                  ✅ {{ formatTime(row.signedAt) }}
                </div>
                <div v-if="row.rejectedAt" style="font-size: 11px; color: var(--accent-2);">
                  ❌ {{ formatTime(row.rejectedAt) }}
                </div>
                <span v-if="!row.viewedAt" style="color: var(--ink-3); font-size: 11px;">等待客户查看</span>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button size="small" @click="resend(row)" :disabled="row.status === 'SIGNED'">
                  <el-icon><RefreshRight /></el-icon>重推
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <div v-if="pushedFiles.length === 0" style="text-align: center; color: var(--ink-3); padding: 24px; font-size: 12px;">
            还未推送文件, 点击左侧模板或上传自定义文件
          </div>
        </div>

        <!-- 状态时间线 -->
        <div class="card">
          <h3 class="card-title">📊 推送汇总</h3>
          <el-row :gutter="12">
            <el-col :span="6">
              <div class="metric-box">
                <div class="metric-label">已推送</div>
                <div class="metric-value" style="color: var(--blue);">{{ stats.pushed }}</div>
              </div>
            </el-col>
            <el-col :span="6">
              <div class="metric-box">
                <div class="metric-label">已查看</div>
                <div class="metric-value" style="color: var(--accent);">{{ stats.viewed }}</div>
              </div>
            </el-col>
            <el-col :span="6">
              <div class="metric-box">
                <div class="metric-label">已签署</div>
                <div class="metric-value" style="color: var(--green);">{{ stats.signed }}</div>
              </div>
            </el-col>
            <el-col :span="6">
              <div class="metric-box">
                <div class="metric-label">已拒签</div>
                <div class="metric-value" style="color: var(--accent-2);">{{ stats.rejected }}</div>
              </div>
            </el-col>
          </el-row>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.info-cell { background: var(--bg-2); padding: 10px 14px; border-radius: 6px; }
.info-cell .label { font-size: 10px; color: var(--ink-3); text-transform: uppercase; letter-spacing: 0.5px; }
.info-cell .value { font-size: 14px; font-weight: 600; margin-top: 2px; }

.template-grid { display: flex; flex-direction: column; gap: 8px; }
.template-card { display: flex; align-items: center; gap: 12px; padding: 12px; background: var(--bg-2); border: 1px solid transparent; border-radius: 8px; cursor: pointer; transition: all 0.2s; }
.template-card:hover { border-color: var(--accent); }
.template-card.selected { border-color: var(--accent); background: rgba(184, 134, 11, 0.05); }
.t-icon { font-size: 32px; }
.t-info { flex: 1; }
.t-name { font-size: 13px; font-weight: 600; }
.t-meta { display: flex; align-items: center; gap: 6px; margin-top: 4px; }
.t-size { font-size: 11px; color: var(--ink-3); font-family: 'JetBrains Mono', monospace; }

.upload-area { padding: 16px; border: 2px dashed var(--line); border-radius: 8px; text-align: center; cursor: pointer; transition: all 0.2s; }
.upload-area:hover { border-color: var(--accent); }
.upload-placeholder .upload-icon { font-size: 32px; color: var(--ink-3); }
.upload-preview { display: flex; align-items: center; gap: 12px; padding: 8px; }
.up-icon { font-size: 32px; }
.up-info { flex: 1; text-align: left; }
.up-size { font-size: 11px; color: var(--ink-3); font-family: 'JetBrains Mono', monospace; }

.file-cell { display: flex; align-items: center; gap: 10px; }
.file-icon { font-size: 24px; }
.file-info { flex: 1; }
.file-name { font-size: 12px; font-weight: 600; }
.file-meta { display: flex; align-items: center; gap: 4px; margin-top: 2px; }
.file-size { font-size: 10px; color: var(--ink-3); font-family: 'JetBrains Mono', monospace; }
.time-cell { font-size: 11px; color: var(--ink-3); font-family: 'JetBrains Mono', monospace; }

.metric-box { padding: 12px; background: var(--bg-2); border-radius: 6px; text-align: center; }
.metric-label { font-size: 11px; color: var(--ink-3); text-transform: uppercase; }
.metric-value { font-size: 24px; font-weight: 700; margin-top: 4px; }
</style>
