<script setup lang="ts">
import { ref, computed, onMounted, watch, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { scriptConfigApi } from '@/api'

/**
 * 产品话术配置工作台 v1.4
 *
 * 功能: 通过 DB 配置产品话术
 *  - 三层叠加流程可视化 (产品专属 → 产品族 → 全局默认)
 *  - 7 步交互向导 (新建/编辑/审核/冻结/同步/对比/历史)
 *  - 实时禁播词/必播项编辑
 *  - 状态机: DRAFT → PENDING_REVIEW → APPROVED → FROZEN
 *
 * 数据流:
 *   Vue → POST /api/v1/script-config/db-template
 *   Vue → POST /api/v1/script-config/db-template/{id}/{submit|approve|freeze}
 *   Vue → GET  /api/v1/script-config/db-templates
 */

// ============================================================================
// 1. 状态
// ============================================================================

const loading = ref(false)
const activeStep = ref(1)

const dbTemplates = ref<any[]>([])
const currentTemplate = ref<any>({
  id: null,
  productId: '',
  productType: 'WEALTH',
  version: 'v1.0',
  riskLevel: 'R2',
  mandatoryDisclosure: [],
  forbiddenPhrases: [],
  requiredQuestions: [],
  channelOverrides: {
    OFFLINE: { syncMode: 'same_frame', watermarkVisible: false, aiDisclosure: false, audioIdPerMinute: 0 },
    REMOTE_VIDEO: { syncMode: 'same_frame', watermarkVisible: false, aiDisclosure: false, audioIdPerMinute: 0 },
    SELF_AI: { syncMode: 'ai_with_disclosure', watermarkVisible: true, aiDisclosure: true, audioIdPerMinute: 1 },
    INTERNET_TEXT: { syncMode: 'text_only', watermarkVisible: false, aiDisclosure: false, audioIdPerMinute: 0 }
  }
})

// 模拟的三层叠加预览
const resolutionPreview = computed(() => {
  if (!currentTemplate.value.productId) return null
  const productHas = dbTemplates.value.find(t => t.productId === currentTemplate.value.productId)
  return {
    L3: { exists: !!productHas, source: productHas?.productId || '当前编辑' },
    L2: { exists: !!currentTemplate.value.productType, source: `type:${currentTemplate.value.productType}` },
    L1: { exists: true, source: 'application.yml 全局默认' }
  }
})

// 流程步骤
const STEPS = [
  { key: 1, title: '基础信息', icon: 'Edit', desc: '产品 ID / 类型 / 风险等级' },
  { key: 2, title: '必播项', icon: 'Microphone', desc: 'mandatory_disclosure' },
  { key: 3, title: '禁播词', icon: 'Warning', desc: 'forbidden_phrases' },
  { key: 4, title: '必问问题', icon: 'QuestionFilled', desc: 'required_questions' },
  { key: 5, title: '渠道差分', icon: 'Connection', desc: 'channel_overrides' },
  { key: 6, title: '预览确认', icon: 'View', desc: '三层叠加预览' },
  { key: 7, title: '提交审核', icon: 'CircleCheck', desc: 'DRAFT → PENDING_REVIEW' }
]

const PRODUCT_TYPES = [
  { value: 'INSURANCE', label: '保险', color: 'var(--accent-2)', example: '投连险/年金险' },
  { value: 'WEALTH', label: '银行理财', color: 'var(--blue)', example: '稳健/混合/股票型' },
  { value: 'FUND', label: '基金', color: 'var(--green)', example: '债基/混合/股票/指数' }
]

const RISK_LEVELS = {
  INSURANCE: ['P1', 'P2', 'P3', 'P4', 'P5'],
  WEALTH: ['R1', 'R2', 'R3', 'R4', 'R5'],
  FUND: ['R1', 'R2', 'R3', 'R4', 'R5']
}

const STATUS_OPTIONS = [
  { value: 'DRAFT', label: '草稿', color: 'var(--ink-3)', desc: '可编辑' },
  { value: 'PENDING_REVIEW', label: '待审核', color: 'var(--accent)', desc: '不可编辑' },
  { value: 'APPROVED', label: '已批准', color: 'var(--green)', desc: '已生效' },
  { value: 'FROZEN', label: '已冻结', color: 'var(--primary)', desc: '司法锁' }
]

// 临时输入
const newMandatory = ref('')
const newForbidden = ref('')
const newQuestion = ref('')
const newProductId = ref('')
const filterStatus = ref<string>('')

// 审核弹窗
const approveDialog = ref(false)
const approverName = ref('compliance-001')

// ============================================================================
// 2. 数据加载
// ============================================================================
async function loadTemplates() {
  loading.value = true
  try {
    dbTemplates.value = await scriptConfigApi.listDbTemplates()
  } catch (e: any) {
    ElMessage.error('加载失败: ' + e.message)
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadTemplates()
})

// ============================================================================
// 3. 三层叠加流程
// ============================================================================
const resolutionChain = computed(() => {
  const steps: Array<{ layer: string; source: string; exists: boolean; selected: boolean }> = []
  // L3
  steps.push({
    layer: 'L3',
    source: `产品专属: ${currentTemplate.value.productId || '(未填)'}`,
    exists: dbTemplates.value.some(t => t.productId === currentTemplate.value.productId),
    selected: !!currentTemplate.value.productId && dbTemplates.value.some(t => t.productId === currentTemplate.value.productId)
  })
  // L2
  steps.push({
    layer: 'L2',
    source: `产品族: ${currentTemplate.value.productType || '(未选)'}`,
    exists: !!currentTemplate.value.productType,
    selected: false
  })
  // L1
  steps.push({
    layer: 'L1',
    source: '全局默认 (application.yml)',
    exists: true,
    selected: false
  })
  return steps
})

// ============================================================================
// 4. 操作
// ============================================================================
function newTemplate() {
  currentTemplate.value = {
    id: null,
    productId: '',
    productType: 'WEALTH',
    version: 'v1.0',
    riskLevel: 'R2',
    mandatoryDisclosure: [],
    forbiddenPhrases: [],
    requiredQuestions: [],
    channelOverrides: {
      OFFLINE: { syncMode: 'same_frame', watermarkVisible: false, aiDisclosure: false, audioIdPerMinute: 0 },
      REMOTE_VIDEO: { syncMode: 'same_frame', watermarkVisible: false, aiDisclosure: false, audioIdPerMinute: 0 },
      SELF_AI: { syncMode: 'ai_with_disclosure', watermarkVisible: true, aiDisclosure: true, audioIdPerMinute: 1 },
      INTERNET_TEXT: { syncMode: 'text_only', watermarkVisible: false, aiDisclosure: false, audioIdPerMinute: 0 }
    },
    status: 'DRAFT'
  }
  activeStep.value = 1
}

function editTemplate(t: any) {
  currentTemplate.value = JSON.parse(JSON.stringify(t))
  activeStep.value = 1
}

async function deleteTemplate(t: any) {
  try {
    await ElMessageBox.confirm(
      `确认删除产品 ${t.productId} (${t.version})?`,
      '删除话术',
      { type: 'warning' }
    )
  } catch { return }
  try {
    await scriptConfigApi.deleteDbTemplate(t.id)
    ElMessage.success('✓ 已删除')
    await loadTemplates()
  } catch (e: any) {
    ElMessage.error('删除失败: ' + e.message)
  }
}

async function saveDraft() {
  if (!currentTemplate.value.productId) {
    ElMessage.warning('请先填写产品 ID')
    activeStep.value = 1
    return
  }
  if (currentTemplate.value.mandatoryDisclosure.length === 0) {
    ElMessage.warning('必播项不能为空')
    activeStep.value = 2
    return
  }
  if (currentTemplate.value.forbiddenPhrases.length === 0) {
    ElMessage.warning('禁播词不能为空')
    activeStep.value = 3
    return
  }
  loading.value = true
  try {
    const saved = await scriptConfigApi.upsertDbTemplate({ ...currentTemplate.value, status: 'DRAFT' })
    currentTemplate.value.id = saved.id
    currentTemplate.value.status = 'DRAFT'
    ElMessage.success('✓ 草稿已保存')
    await loadTemplates()
  } catch (e: any) {
    ElMessage.error('保存失败: ' + e.message)
  } finally {
    loading.value = false
  }
}

async function submitForReview() {
  if (!currentTemplate.value.id) {
    await saveDraft()
  }
  if (!currentTemplate.value.id) return
  try {
    const updated = await scriptConfigApi.submitForReview(currentTemplate.value.id)
    currentTemplate.value = { ...currentTemplate.value, ...updated, status: 'PENDING_REVIEW' }
    ElMessage.success('✓ 已提交审核')
    await loadTemplates()
  } catch (e: any) {
    ElMessage.error('提交失败: ' + e.message)
  }
}

async function approve() {
  if (!currentTemplate.value.id) return
  try {
    const updated = await scriptConfigApi.approveTemplate(currentTemplate.value.id, approverName.value)
    ElMessage.success(`✓ 已批准 (审核人: ${approverName.value})`)
    approveDialog.value = false
    await loadTemplates()
    const fresh = dbTemplates.value.find(t => t.id === currentTemplate.value.id)
    if (fresh) editTemplate(fresh)
  } catch (e: any) {
    ElMessage.error('批准失败: ' + e.message)
  }
}

async function freeze() {
  if (!currentTemplate.value.id) return
  try {
    await ElMessageBox.confirm(
      '冻结后该话术不可再编辑 (司法级锁定), 确认?',
      '冻结话术',
      { type: 'warning' }
    )
  } catch { return }
  try {
    await scriptConfigApi.freezeTemplate(currentTemplate.value.id)
    ElMessage.success('✓ 已冻结')
    await loadTemplates()
    const fresh = dbTemplates.value.find(t => t.id === currentTemplate.value.id)
    if (fresh) editTemplate(fresh)
  } catch (e: any) {
    ElMessage.error('冻结失败: ' + e.message)
  }
}

// ============================================================================
// 5. 数组操作
// ============================================================================
function addMandatory() {
  if (!newMandatory.value.trim()) return
  currentTemplate.value.mandatoryDisclosure.push(newMandatory.value.trim())
  newMandatory.value = ''
}
function removeMandatory(idx: number) {
  currentTemplate.value.mandatoryDisclosure.splice(idx, 1)
}

function addForbidden() {
  if (!newForbidden.value.trim()) return
  if (newForbidden.value.trim().length < 3) {
    ElMessage.warning('禁播词长度至少 3 字符, 避免误报')
    return
  }
  currentTemplate.value.forbiddenPhrases.push(newForbidden.value.trim())
  newForbidden.value = ''
}
function removeForbidden(idx: number) {
  currentTemplate.value.forbiddenPhrases.splice(idx, 1)
}

function addQuestion() {
  if (!newQuestion.value.trim()) return
  currentTemplate.value.requiredQuestions.push(newQuestion.value.trim())
  newQuestion.value = ''
}
function removeQuestion(idx: number) {
  currentTemplate.value.requiredQuestions.splice(idx, 1)
}

// ============================================================================
// 6. 计算属性
// ============================================================================
const filteredTemplates = computed(() => {
  if (!filterStatus.value) return dbTemplates.value
  return dbTemplates.value.filter(t => t.status === filterStatus.value)
})

const canEdit = computed(() => currentTemplate.value.status === 'DRAFT' || !currentTemplate.value.id)

const stats = computed(() => ({
  total: dbTemplates.value.length,
  draft: dbTemplates.value.filter(t => t.status === 'DRAFT').length,
  pending: dbTemplates.value.filter(t => t.status === 'PENDING_REVIEW').length,
  approved: dbTemplates.value.filter(t => t.status === 'APPROVED').length,
  frozen: dbTemplates.value.filter(t => t.status === 'FROZEN').length
}))

// 风险等级选项 (随产品族变)
const availableRisks = computed(() => RISK_LEVELS[currentTemplate.value.productType as keyof typeof RISK_LEVELS] || [])

const statusMeta = computed(() => {
  return STATUS_OPTIONS.find(s => s.value === currentTemplate.value.status) || STATUS_OPTIONS[0]
})

// ============================================================================
// 7. 工具
// ============================================================================
function nextStep() {
  if (activeStep.value < STEPS.length) activeStep.value++
}
function prevStep() {
  if (activeStep.value > 1) activeStep.value--
}
function gotoStep(n: number) {
  activeStep.value = n
}

function formatTime(t: any) {
  if (!t) return '—'
  return new Date(t).toLocaleString('zh-CN')
}
</script>

<template>
  <div>
    <!-- 顶部: 流程状态 + 统计 -->
    <div class="card">
      <h3 class="card-title">
        <span>
          <el-icon><Tools /></el-icon>
          产品话术配置工作台 · 通过 DB 配置
        </span>
        <span class="actions">
          <el-button type="primary" @click="newTemplate">
            <el-icon><Plus /></el-icon>新建话术
          </el-button>
          <el-button @click="loadTemplates">
            <el-icon><Refresh /></el-icon>
          </el-button>
        </span>
      </h3>

      <!-- 统计 -->
      <el-row :gutter="12">
        <el-col :span="5">
          <div class="stat-box">
            <div class="stat-label">总模板</div>
            <div class="stat-value">{{ stats.total }}</div>
          </div>
        </el-col>
        <el-col :span="5">
          <div class="stat-box draft">
            <div class="stat-label">草稿</div>
            <div class="stat-value">{{ stats.draft }}</div>
          </div>
        </el-col>
        <el-col :span="5">
          <div class="stat-box pending">
            <div class="stat-label">待审核</div>
            <div class="stat-value">{{ stats.pending }}</div>
          </div>
        </el-col>
        <el-col :span="5">
          <div class="stat-box approved">
            <div class="stat-label">已批准</div>
            <div class="stat-value">{{ stats.approved }}</div>
          </div>
        </el-col>
        <el-col :span="4">
          <div class="stat-box frozen">
            <div class="stat-label">已冻结</div>
            <div class="stat-value">{{ stats.frozen }}</div>
          </div>
        </el-col>
      </el-row>

      <!-- 三层叠加流程图 (核心) -->
      <div class="layer-flow">
        <h4 style="margin: 16px 0 8px; font-size: 12px; color: var(--ink-3); text-transform: uppercase; letter-spacing: 1px;">
          话术解析流程 · 三层叠加
        </h4>
        <div class="flow-row">
          <div v-for="(step, i) in resolutionChain" :key="i" class="flow-step" :class="{ active: step.selected }">
            <div class="flow-layer">{{ step.layer }}</div>
            <div class="flow-source">{{ step.source }}</div>
            <div class="flow-status">
              <el-tag size="small" :type="step.selected ? 'success' : (step.exists ? 'info' : 'danger')" effect="plain">
                {{ step.selected ? '✓ 命中' : (step.exists ? '已配置' : '缺失') }}
              </el-tag>
            </div>
            <div v-if="i < resolutionChain.length - 1" class="flow-arrow">↓</div>
          </div>
        </div>
      </div>
    </div>

    <el-row :gutter="16">
      <!-- 左侧: 模板列表 -->
      <el-col :span="9">
        <div class="card">
          <h3 class="card-title">
            <span>话术模板列表 ({{ filteredTemplates.length }})</span>
            <span class="actions">
              <el-select v-model="filterStatus" size="small" style="width: 120px;" placeholder="全部" clearable>
                <el-option v-for="s in STATUS_OPTIONS" :key="s.value" :value="s.value" :label="s.label" />
              </el-select>
            </span>
          </h3>
          <div class="template-list">
            <div
              v-for="t in filteredTemplates"
              :key="t.id"
              class="template-item"
              :class="{ selected: currentTemplate.id === t.id, [t.status?.toLowerCase()]: true }"
              @click="editTemplate(t)"
            >
              <div class="t-header">
                <div>
                  <span class="t-id mono">{{ t.productId }}</span>
                  <span class="t-version">{{ t.version }}</span>
                </div>
                <el-tag size="small" :style="{ color: STATUS_OPTIONS.find(s => s.value === t.status)?.color }">
                  {{ STATUS_OPTIONS.find(s => s.value === t.status)?.label || t.status }}
                </el-tag>
              </div>
              <div class="t-meta">
                <span class="risk-pill" :class="t.riskLevel">{{ t.riskLevel }}</span>
                <span class="t-type">{{ t.productType }}</span>
                <span class="t-fp" v-if="t.forbiddenPhrases?.length">
                  🚫 {{ t.forbiddenPhrases.length }}
                </span>
                <span class="t-mp" v-if="t.mandatoryDisclosure?.length">
                  📢 {{ t.mandatoryDisclosure.length }}
                </span>
              </div>
              <div class="t-time">{{ formatTime(t.updatedAt) }}</div>
              <div class="t-actions" @click.stop>
                <el-button size="small" @click="editTemplate(t)">编辑</el-button>
                <el-button size="small" type="danger" @click="deleteTemplate(t)">删除</el-button>
              </div>
            </div>
            <div v-if="filteredTemplates.length === 0" style="text-align: center; color: var(--ink-3); padding: 24px; font-size: 12px;">
              暂无话术, 点击右上"新建话术"开始
            </div>
          </div>
        </div>
      </el-col>

      <!-- 右侧: 7 步向导 -->
      <el-col :span="15">
        <div class="card">
          <h3 class="card-title">
            <span>
              向导 · {{ currentTemplate.id ? '编辑' : '新建' }}话术
              <el-tag v-if="currentTemplate.status" size="small" :style="{ color: statusMeta.color, marginLeft: '8px' }">
                {{ statusMeta.label }}
              </el-tag>
            </span>
          </h3>

          <!-- Stepper -->
          <el-steps :active="activeStep - 1" finish-status="success" align-center style="margin-bottom: 24px;">
            <el-step v-for="s in STEPS" :key="s.key" :title="s.title" :description="s.desc" @click="gotoStep(s.key)" style="cursor: pointer;" />
          </el-steps>

          <!-- 步骤 1: 基础信息 -->
          <div v-show="activeStep === 1">
            <h4>📋 产品基础信息</h4>
            <el-form label-width="120px" :disabled="!canEdit">
              <el-form-item label="产品 ID" required>
                <el-input v-model="currentTemplate.productId" placeholder="如: LIC-INV-2026Q4-001" />
                <div class="hint">全行唯一, 一旦创建不可修改</div>
              </el-form-item>
              <el-form-item label="产品族" required>
                <el-radio-group v-model="currentTemplate.productType">
                  <el-radio-button v-for="t in PRODUCT_TYPES" :key="t.value" :value="t.value">
                    {{ t.label }} <small>({{ t.example }})</small>
                  </el-radio-button>
                </el-radio-group>
              </el-form-item>
              <el-form-item label="风险等级" required>
                <el-radio-group v-model="currentTemplate.riskLevel">
                  <el-radio-button v-for="r in availableRisks" :key="r" :value="r">
                    <span :class="`risk-pill ${r}`">{{ r }}</span>
                  </el-radio-button>
                </el-radio-group>
              </el-form-item>
              <el-form-item label="版本号">
                <el-input v-model="currentTemplate.version" style="width: 200px;" />
                <div class="hint">同 productId 多版本并存, 最新生效</div>
              </el-form-item>
            </el-form>
          </div>

          <!-- 步骤 2: 必播项 -->
          <div v-show="activeStep === 2">
            <h4>📢 必播项 (mandatory_disclosure) · 坐席必读</h4>
            <div class="hint">每条必播项会在录制前 5 秒自动语音播报, 客户必须听到</div>
            <el-input
              v-model="newMandatory"
              placeholder="输入必播项内容, 回车添加"
              @keyup.enter="addMandatory"
              :disabled="!canEdit"
              style="margin: 12px 0;"
            >
              <template #append>
                <el-button @click="addMandatory" :disabled="!canEdit">
                  <el-icon><Plus /></el-icon>添加
                </el-button>
              </template>
            </el-input>
            <div class="phrase-list">
              <div v-for="(p, i) in currentTemplate.mandatoryDisclosure" :key="i" class="phrase-item mandatory">
                <span class="phrase-num">{{ i + 1 }}.</span>
                <span class="phrase-text">{{ p }}</span>
                <el-button v-if="canEdit" size="small" type="danger" plain @click="removeMandatory(i)">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </div>
              <div v-if="currentTemplate.mandatoryDisclosure.length === 0" class="empty">
                还没有必播项, 添加第一条 ↑
              </div>
            </div>
            <div style="margin-top: 12px;">
              <el-button size="small" @click="newMandatory = '本产品为非保本浮动收益型理财，不保证本金和收益'; addMandatory()" :disabled="!canEdit">
                💡 使用模板示例
              </el-button>
            </div>
          </div>

          <!-- 步骤 3: 禁播词 -->
          <div v-show="activeStep === 3">
            <h4>🚫 禁播词 (forbidden_phrases) · 实时阻断</h4>
            <div class="hint">长度 ≥ 3 字符避免误报; 命中后立即阻断录制</div>
            <el-input
              v-model="newForbidden"
              placeholder="输入禁播词, 回车添加 (如: 保证收益)"
              @keyup.enter="addForbidden"
              :disabled="!canEdit"
              style="margin: 12px 0;"
            >
              <template #append>
                <el-button @click="addForbidden" :disabled="!canEdit">
                  <el-icon><Plus /></el-icon>添加
                </el-button>
              </template>
            </el-input>
            <div class="phrase-list">
              <div v-for="(p, i) in currentTemplate.forbiddenPhrases" :key="i" class="phrase-item forbidden">
                <el-tag size="small" type="danger">HIGH</el-tag>
                <span class="phrase-text">{{ p }}</span>
                <el-button v-if="canEdit" size="small" type="danger" plain @click="removeForbidden(i)">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </div>
              <div v-if="currentTemplate.forbiddenPhrases.length === 0" class="empty">
                还没有禁播词, 添加第一条 ↑
              </div>
            </div>
            <div style="margin-top: 12px;">
              <el-button size="small" @click="newForbidden = '稳赚不赔'; addForbidden()" :disabled="!canEdit">
                💡 稳赚不赔
              </el-button>
              <el-button size="small" @click="newForbidden = '保本保息'; addForbidden()" :disabled="!canEdit">
                💡 保本保息
              </el-button>
              <el-button size="small" @click="newForbidden = '刚兑'; addForbidden()" :disabled="!canEdit">
                💡 刚兑
              </el-button>
            </div>
          </div>

          <!-- 步骤 4: 必问问题 -->
          <div v-show="activeStep === 4">
            <h4>❓ 必问问题 (required_questions) · 客户确认</h4>
            <div class="hint">坐席必须口头询问, 客户必须回答后才能进入下一节点</div>
            <el-input
              v-model="newQuestion"
              placeholder="输入必问问题, 回车添加"
              @keyup.enter="addQuestion"
              :disabled="!canEdit"
              style="margin: 12px 0;"
            >
              <template #append>
                <el-button @click="addQuestion" :disabled="!canEdit">
                  <el-icon><Plus /></el-icon>添加
                </el-button>
              </template>
            </el-input>
            <div class="phrase-list">
              <div v-for="(p, i) in currentTemplate.requiredQuestions" :key="i" class="phrase-item question">
                <el-icon style="color: var(--blue);"><QuestionFilled /></el-icon>
                <span class="phrase-text">{{ p }}</span>
                <el-button v-if="canEdit" size="small" type="danger" plain @click="removeQuestion(i)">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </div>
              <div v-if="currentTemplate.requiredQuestions.length === 0" class="empty">
                还没有必问问题
              </div>
            </div>
          </div>

          <!-- 步骤 5: 渠道差分 -->
          <div v-show="activeStep === 5">
            <h4>📡 渠道差分 (channel_overrides)</h4>
            <div class="hint">不同渠道可配置不同表现: 水印 / AI 语音 / 同步模式 / 音频 ID 频率</div>
            <el-table :data="Object.entries(currentTemplate.channelOverrides).map(([k, v]: any) => ({ channel: k, ...v }))" border size="small">
              <el-table-column prop="channel" label="渠道" width="120">
                <template #default="{ row }">
                  <span class="channel-pill" :class="row.channel.toLowerCase()">{{ row.channel }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="syncMode" label="同步模式">
                <template #default="{ row }">
                  <el-select v-model="row.syncMode" size="small" :disabled="!canEdit">
                    <el-option value="same_frame" label="同框同帧" />
                    <el-option value="ai_with_disclosure" label="AI 数字人 + 显著标识" />
                    <el-option value="text_only" label="仅文本" />
                    <el-option value="ai_only" label="仅 AI" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="水印" width="80">
                <template #default="{ row }">
                  <el-switch v-model="row.watermarkVisible" :disabled="!canEdit" />
                </template>
              </el-table-column>
              <el-table-column label="AI 标识" width="80">
                <template #default="{ row }">
                  <el-switch v-model="row.aiDisclosure" :disabled="!canEdit" />
                </template>
              </el-table-column>
              <el-table-column label="音频 ID 频率 (次/分)" width="140">
                <template #default="{ row }">
                  <el-input-number v-model="row.audioIdPerMinute" :min="0" :max="10" size="small" :disabled="!canEdit" style="width: 80px;" />
                </template>
              </el-table-column>
            </el-table>
          </div>

          <!-- 步骤 6: 预览 -->
          <div v-show="activeStep === 6">
            <h4>👁 预览合并后的最终话术</h4>
            <div class="preview-box">
              <div class="preview-header">
                <span class="mono" style="font-size: 14px; font-weight: 700;">{{ currentTemplate.productId || '(未填)' }}</span>
                <el-tag size="small" :class="currentTemplate.riskLevel">{{ currentTemplate.riskLevel }}</el-tag>
                <el-tag size="small">{{ currentTemplate.productType }}</el-tag>
                <el-tag size="small">{{ currentTemplate.version }}</el-tag>
                <el-tag size="small" :type="currentTemplate.status === 'APPROVED' ? 'success' : 'info'">
                  {{ statusMeta.label }}
                </el-tag>
              </div>
              <el-tabs>
                <el-tab-pane label="📢 必播项 ({{ currentTemplate.mandatoryDisclosure.length }})">
                  <ul class="preview-list">
                    <li v-for="(p, i) in currentTemplate.mandatoryDisclosure" :key="i">{{ p }}</li>
                    <li v-if="currentTemplate.mandatoryDisclosure.length === 0" class="empty">未配置</li>
                  </ul>
                </el-tab-pane>
                <el-tab-pane label="🚫 禁播词 ({{ currentTemplate.forbiddenPhrases.length }})">
                  <div class="phrase-list">
                    <div v-for="(p, i) in currentTemplate.forbiddenPhrases" :key="i" class="phrase-item forbidden">
                      <el-tag size="small" type="danger">HIGH</el-tag> {{ p }}
                    </div>
                    <div v-if="currentTemplate.forbiddenPhrases.length === 0" class="empty">未配置</div>
                  </div>
                </el-tab-pane>
                <el-tab-pane label="❓ 必问 ({{ currentTemplate.requiredQuestions.length }})">
                  <ul class="preview-list">
                    <li v-for="(p, i) in currentTemplate.requiredQuestions" :key="i">{{ p }}</li>
                    <li v-if="currentTemplate.requiredQuestions.length === 0" class="empty">未配置</li>
                  </ul>
                </el-tab-pane>
                <el-tab-pane label="📡 渠道差分 ({{ Object.keys(currentTemplate.channelOverrides).length }})">
                  <el-table :data="Object.entries(currentTemplate.channelOverrides).map(([k, v]: any) => ({ channel: k, ...v }))" size="small">
                    <el-table-column prop="channel" label="渠道" />
                    <el-table-column prop="syncMode" label="模式" />
                    <el-table-column label="水印" width="60">
                      <template #default="{ row }">
                        <el-tag size="small" :type="row.watermarkVisible ? 'success' : 'info'">
                          {{ row.watermarkVisible ? '是' : '否' }}
                        </el-tag>
                      </template>
                    </el-table-column>
                    <el-table-column label="AI 标识" width="70">
                      <template #default="{ row }">
                        <el-tag size="small" :type="row.aiDisclosure ? 'success' : 'info'">
                          {{ row.aiDisclosure ? '是' : '否' }}
                        </el-tag>
                      </template>
                    </el-table-column>
                    <el-table-column prop="audioIdPerMinute" label="音频 ID 频率" width="100" />
                  </el-table>
                </el-tab-pane>
              </el-tabs>
            </div>
          </div>

          <!-- 步骤 7: 提交审核 -->
          <div v-show="activeStep === 7">
            <h4>✅ 提交审核 / 状态机</h4>
            <div class="state-machine-flow">
              <div
                v-for="(s, i) in STATUS_OPTIONS"
                :key="s.value"
                class="sm-step"
                :class="{ active: currentTemplate.status === s.value, passed: STATUS_OPTIONS.findIndex(o => o.value === currentTemplate.status) > i }"
              >
                <div class="sm-circle">
                  <span v-if="STATUS_OPTIONS.findIndex(o => o.value === currentTemplate.status) > i">✓</span>
                  <span v-else>{{ i + 1 }}</span>
                </div>
                <div class="sm-label">{{ s.label }}</div>
                <div class="sm-desc">{{ s.desc }}</div>
              </div>
            </div>
            <el-alert
              v-if="currentTemplate.status === 'DRAFT'"
              type="warning" :closable="false" show-icon
              title="当前是 DRAFT 状态"
              description="点击下方 保存草稿 → 回到 DRAFT, 点击 提交审核 → 进入 PENDING_REVIEW"
            />
            <el-alert
              v-else-if="currentTemplate.status === 'PENDING_REVIEW'"
              type="info" :closable="false" show-icon
              title="当前是 PENDING_REVIEW 状态"
              description="由合规审核员点击 批准 或 驳回"
            />
            <el-alert
              v-else-if="currentTemplate.status === 'APPROVED'"
              type="success" :closable="false" show-icon
              title="当前是 APPROVED 状态"
              description="已生效, 可选择 冻结 以锁定不可再编辑"
            />
            <el-alert
              v-else-if="currentTemplate.status === 'FROZEN'"
              type="error" :closable="false" show-icon
              title="当前是 FROZEN 状态"
              description="已冻结, 司法级别锁, 不可再编辑/审核/删除 (监管要求)"
            />
          </div>

          <!-- 底部操作 -->
          <div class="wizard-footer">
            <el-button @click="prevStep" :disabled="activeStep === 1">
              <el-icon><ArrowLeft /></el-icon>上一步
            </el-button>
            <div style="flex: 1;"></div>
            <el-button @click="saveDraft" :disabled="!canEdit" :loading="loading">
              <el-icon><Document /></el-icon>保存草稿
            </el-button>
            <el-button v-if="activeStep < STEPS.length" type="primary" @click="nextStep">
              下一步<el-icon><ArrowRight /></el-icon>
            </el-button>
            <el-button
              v-if="activeStep === STEPS.length && currentTemplate.status === 'DRAFT'"
              type="warning" @click="submitForReview"
            >
              <el-icon><Promotion /></el-icon>提交审核
            </el-button>
            <el-button
              v-if="activeStep === STEPS.length && currentTemplate.status === 'PENDING_REVIEW'"
              type="success" @click="approveDialog = true"
            >
              <el-icon><Check /></el-icon>批准
            </el-button>
            <el-button
              v-if="activeStep === STEPS.length && currentTemplate.status === 'APPROVED'"
              type="danger" @click="freeze"
            >
              <el-icon><Lock /></el-icon>冻结 (司法锁)
            </el-button>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 批准弹窗 -->
    <el-dialog v-model="approveDialog" title="批准话术" width="400px">
      <p>确认批准此话术模板? 批准后立即生效, 全行所有渠道可见。</p>
      <el-form label-width="80px">
        <el-form-item label="审核人">
          <el-input v-model="approverName" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="approveDialog = false">取消</el-button>
        <el-button type="success" @click="approve">确认批准</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.stat-box { padding: 12px; background: var(--bg-2); border-radius: 6px; text-align: center; }
.stat-label { font-size: 11px; color: var(--ink-3); text-transform: uppercase; letter-spacing: 0.5px; }
.stat-value { font-size: 24px; font-weight: 700; margin-top: 4px; }
.stat-box.draft { border-left: 3px solid var(--ink-3); }
.stat-box.pending { border-left: 3px solid var(--accent); }
.stat-box.approved { border-left: 3px solid var(--green); }
.stat-box.frozen { border-left: 3px solid var(--primary); }

.layer-flow { padding: 12px 0; }
.flow-row { display: flex; gap: 8px; align-items: stretch; }
.flow-step { flex: 1; background: var(--bg-2); border: 2px solid var(--line); border-radius: 8px; padding: 12px; text-align: center; position: relative; transition: all 0.3s; }
.flow-step.active { border-color: var(--accent); background: rgba(184, 134, 11, 0.05); }
.flow-layer { font-size: 18px; font-weight: 700; color: var(--accent); }
.flow-source { font-size: 11px; color: var(--ink-3); margin: 4px 0; font-family: 'JetBrains Mono', monospace; }
.flow-status { margin-top: 6px; }
.flow-arrow { position: absolute; right: -16px; top: 50%; transform: translateY(-50%); font-size: 18px; color: var(--ink-3); z-index: 1; }

.template-list { max-height: 700px; overflow-y: auto; display: flex; flex-direction: column; gap: 6px; }
.template-item { padding: 12px; background: var(--card); border: 1px solid var(--line); border-radius: 8px; cursor: pointer; transition: all 0.2s; }
.template-item:hover { border-color: var(--accent); }
.template-item.selected { border-color: var(--accent); background: rgba(184, 134, 11, 0.05); }
.template-item.draft { border-left: 3px solid var(--ink-3); }
.template-item.pending_review { border-left: 3px solid var(--accent); }
.template-item.approved { border-left: 3px solid var(--green); }
.template-item.frozen { border-left: 3px solid var(--primary); }
.t-header { display: flex; justify-content: space-between; align-items: center; }
.t-id { font-size: 12px; font-weight: 600; }
.t-version { font-size: 10px; color: var(--ink-3); margin-left: 6px; }
.t-meta { display: flex; gap: 8px; align-items: center; margin-top: 6px; flex-wrap: wrap; }
.t-type { font-size: 11px; color: var(--ink-3); }
.t-fp, .t-mp { font-size: 10px; color: var(--ink-3); }
.t-time { font-size: 10px; color: var(--ink-3); margin-top: 4px; font-family: 'JetBrains Mono', monospace; }
.t-actions { margin-top: 8px; display: flex; gap: 4px; }

h4 { font-size: 14px; margin: 0 0 8px; color: var(--ink); font-weight: 600; }
.hint { font-size: 11px; color: var(--ink-3); margin-top: 4px; }

.phrase-list { display: flex; flex-direction: column; gap: 6px; }
.phrase-item { display: flex; align-items: center; gap: 8px; padding: 8px 12px; background: var(--bg-2); border-radius: 6px; border-left: 3px solid var(--line); }
.phrase-item.mandatory { border-left-color: var(--blue); }
.phrase-item.forbidden { border-left-color: var(--accent-2); }
.phrase-item.question { border-left-color: var(--accent); }
.phrase-num { font-weight: 600; color: var(--ink-3); }
.phrase-text { flex: 1; font-size: 13px; }
.empty { text-align: center; color: var(--ink-3); padding: 16px; font-size: 12px; font-style: italic; }

.preview-box { background: var(--bg-2); border-radius: 8px; padding: 16px; }
.preview-header { display: flex; gap: 8px; align-items: center; margin-bottom: 12px; flex-wrap: wrap; }
.preview-list { margin: 0; padding-left: 20px; }
.preview-list li { padding: 4px 0; line-height: 1.5; }

.channel-pill { padding: 2px 8px; border-radius: 4px; font-size: 11px; font-weight: 600; font-family: 'JetBrains Mono', monospace; }
.channel-pill.offline { background: var(--blue); color: #fff; }
.channel-pill.remote_video { background: var(--accent); color: #fff; }
.channel-pill.self_ai { background: var(--accent-2); color: #fff; }
.channel-pill.internet_text { background: var(--ink-3); color: #fff; }

.state-machine-flow { display: flex; align-items: center; justify-content: space-between; margin: 24px 0; padding: 16px; background: var(--bg-2); border-radius: 8px; }
.sm-step { flex: 1; text-align: center; position: relative; }
.sm-circle { width: 48px; height: 48px; border-radius: 50%; background: var(--card); border: 3px solid var(--line); display: flex; align-items: center; justify-content: center; margin: 0 auto 8px; font-weight: 700; transition: all 0.3s; }
.sm-step.active .sm-circle { background: var(--accent); color: #fff; border-color: var(--accent); }
.sm-step.passed .sm-circle { background: var(--green); color: #fff; border-color: var(--green); }
.sm-label { font-size: 12px; font-weight: 600; }
.sm-desc { font-size: 10px; color: var(--ink-3); margin-top: 2px; }
.sm-step:not(:last-child)::after { content: '→'; position: absolute; top: 18px; right: -16px; font-size: 18px; color: var(--ink-3); }

.wizard-footer { display: flex; align-items: center; padding-top: 24px; border-top: 1px solid var(--line); margin-top: 16px; }
</style>
