<script setup lang="ts">
/**
 * 线下双录面板 v1.5 · 银行/保险柜台风格
 *
 * 场景: 客户在银行网点/保险公司柜面, 理财经理操作此面板
 * 特点:
 *  - 大触摸按钮 (适合平板/iPad)
 *  - 客户摄像头 + 理财经理摄像头双路
 *  - 实时显示当前节点的话术 (大字号)
 *  - 实时 ASR + 禁播词检测
 *  - 一键完成节点
 *  - 失败时: 一键生成补录 token → 客户扫码线上继续
 *  - AI 视频模型合规检测 (检测人脸 / 第三方 / 黑屏)
 *
 * 8 节点同 ClientPortal, 但视角是柜员
 */
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { recordingApi } from '@/api'

// ============================================================================
// 1. 状态
// ============================================================================

const businessId = ref('BNK20260801-900001')
const operatorName = ref('王经理')
const branchName = ref('北京朝阳支行')

const currentNodeIdx = ref(0)
const completedNodes = ref<Set<number>>(new Set())
const isRecording = ref(false)
const recordElapsed = ref(0)
const recordQuality = ref({
  blackFrameRatio: 0,
  customerFaceRatio: 0,
  thirdPartyCount: 0,
  score: 100
})

// 实时 ASR
const liveAsr = ref('')
const liveHits = ref<Array<{ phrase: string; severity: string; regulationRef: string }>>([])

// 视频流
const tellerVideoRef = ref<HTMLVideoElement | null>(null)
const customerVideoRef = ref<HTMLVideoElement | null>(null)
const tellerStream = ref<MediaStream | null>(null)
const customerStream = ref<MediaStream | null>(null)
const mediaRecorder = ref<MediaRecorder | null>(null)
const recordedBlobs = ref<Blob[]>([])

// 视频 AI 检测结果
const aiCheckResult = ref<{
  faceCount: number
  blackFrames: number
  thirdParties: number
  posture: string
  lighting: 'GOOD' | 'DIM' | 'HARSH'
  watermark: boolean
  audioSnr: number
  overallScore: number
  issues: string[]
} | null>(null)
const isAiChecking = ref(false)

// 补录二维码模拟
const resumeToken = ref<string>('')
const resumeUrl = ref<string>('')
const showResumeDialog = ref(false)

// 8 节点 (与 ClientPortal 一致)
const NODES = [
  { code: '01-IDENTITY', name: '身份核验', desc: '请客户出示身份证件', scripts: ['请坐正, 露出正脸', '请打开您的身份证件', '我已核对身份证信息'], duration: 30, critical: false },
  { code: '02-DISCLOSURE', name: '风险揭示', desc: '本产品存在投资风险', scripts: ['本产品为非保本浮动收益型理财', '过往业绩不代表未来表现', '投资有风险, 入市需谨慎', '请您确认: 您已了解上述风险'], duration: 90, critical: true },
  { code: '03-PRODUCT', name: '产品详情', desc: '展示产品说明书', scripts: ['产品名称: 稳健型封闭式理财', '业绩比较基准: 3.0% - 3.8%', '封闭期: 180 天', '请翻到第 5 页查看详细条款'], duration: 60, critical: false },
  { code: '04-RIGHTS', name: '权利义务', desc: '15 天犹豫期 + 风险自担', scripts: ['15 天犹豫期内可无理由退保', '工本费不超过 10 元', '本金及收益风险由您自行承担'], duration: 45, critical: false },
  { code: '05-TRUTH_TELL', name: '如实告知', desc: '请如实告知财务状况', scripts: ['请确认: 资金来源合法', '请确认: 非借贷资金', '请确认: 风险等级匹配', '请确认: 家庭总资产情况'], duration: 60, critical: false },
  { code: '06-CONFIRM', name: '明确肯定', desc: '★ 关键: 客户必须说"是"', scripts: ['您是否已了解本产品的全部风险?', '您是否已阅读产品说明书和风险揭示书?', '您是否同意按当前条款进行本次投资?'], duration: 30, critical: true },
  { code: '07-SIGN', name: '电子签署', desc: '客户在平板上签字', scripts: ['请在平板上签名确认', '请确认签名无误', '签名已完成'], duration: 45, critical: false },
  { code: '08-FOLLOWUP', name: '补充询问', desc: '客户提问环节', scripts: ['请问您还有其他问题吗?', '本次双录将在 5 秒后结束'], duration: 20, critical: false }
]

// 失败原因选项
const FAIL_REASONS = [
  { value: 'FORBIDDEN_PHRASE', label: '禁播词命中', icon: '🚫' },
  { value: 'NO_AFFIRMATIVE', label: '客户未明确肯定', icon: '❓' },
  { value: 'BLACK_FRAME', label: '黑屏时间过长', icon: '⬛' },
  { value: 'FACE_MISSING', label: '客户人脸缺失', icon: '👤' },
  { value: 'THIRD_PARTY', label: '第三方介入', icon: '👥' },
  { value: 'OTHER', label: '其他', icon: '⚠️' }
]

// 视频 AI 实时检测 (本地模拟)
const aiCheckInterval = ref<ReturnType<typeof setInterval> | null>(null)
let elapsedTimer: ReturnType<typeof setInterval> | null = null

// ============================================================================
// 2. 计算属性
// ============================================================================
const currentNode = computed(() => NODES[currentNodeIdx.value])
const recProgressPct = computed(() => (completedNodes.value.size / NODES.length) * 100)
const recFormattedTime = computed(() => {
  const s = Math.floor(recordElapsed.value / 1000)
  return `${Math.floor(s / 60).toString().padStart(2, '0')}:${(s % 60).toString().padStart(2, '0')}`
})

// ============================================================================
// 3. 录制控制
// ============================================================================
async function startRecording() {
  try {
    // 尝试获取两个视频流 (理财经理 + 客户), 失败则进入模拟
    const stream = await navigator.mediaDevices.getUserMedia({
      video: { width: 1280, height: 720 },
      audio: true
    })
    tellerStream.value = stream
    await nextTick()
    if (tellerVideoRef.value) {
      tellerVideoRef.value.srcObject = stream
      tellerVideoRef.value.muted = true
      tellerVideoRef.value.play()
    }
    // 模拟客户侧
    if (customerVideoRef.value) {
      customerVideoRef.value.srcObject = stream
      customerVideoRef.value.muted = true
      customerVideoRef.value.play()
    }
    // MediaRecorder
    const mr = new MediaRecorder(stream, { mimeType: 'video/webm' })
    mr.ondataavailable = e => { if (e.data && e.data.size > 0) recordedBlobs.value.push(e.data) }
    mr.start(1000)
    mediaRecorder.value = mr
  } catch (e) {
    console.warn('无摄像头, 模拟模式', e)
  }
  isRecording.value = true
  recordElapsed.value = 0
  startElapsedTimer()
  startAiCheckLoop()
  ElMessage.success('🎬 录制已开始')
}

function stopRecording() {
  isRecording.value = false
  if (elapsedTimer) clearInterval(elapsedTimer)
  if (aiCheckInterval.value) clearInterval(aiCheckInterval.value)
  if (mediaRecorder.value && mediaRecorder.value.state !== 'inactive') {
    mediaRecorder.value.stop()
  }
  if (tellerStream.value) {
    tellerStream.value.getTracks().forEach(t => t.stop())
  }
}

function startElapsedTimer() {
  elapsedTimer = setInterval(() => {
    if (isRecording.value) recordElapsed.value += 100
  }, 100)
}

// ============================================================================
// 4. 节点操作
// ============================================================================
async function completeCurrentNode() {
  if (completedNodes.value.has(currentNodeIdx.value)) return
  // 关键节点需要 AI 检测
  if (currentNode.value.critical) {
    ElMessage.info('正在运行 AI 视频模型合规检测...')
    const result = await runAiCheck()
    if (result.overallScore < 60) {
      // 触发失败
      await handleNodeFailed(result)
      return
    }
  }
  completedNodes.value.add(currentNodeIdx.value)
  ElMessage.success(`✓ 节点 ${currentNodeIdx.value + 1} (${currentNode.value.name}) 完成`)
  if (currentNodeIdx.value < NODES.length - 1) {
    currentNodeIdx.value++
  } else {
    ElMessage.success('🎉 全部 8 节点完成!')
    stopRecording()
  }
}

async function skipCurrentNode() {
  completedNodes.value.add(currentNodeIdx.value)
  if (currentNodeIdx.value < NODES.length - 1) {
    currentNodeIdx.value++
  }
}

async function failCurrentNode(reason: string) {
  try {
    await ElMessageBox.confirm(
      `确认节点 ${currentNodeIdx.value + 1} (${currentNode.value.name}) 失败: ${reason}?`,
      '标记失败',
      { type: 'warning' }
    )
  } catch { return }
  await handleNodeFailed({ issues: [reason], overallScore: 30 })
}

async function handleNodeFailed(aiResult: any) {
  // 1. 停止录制
  stopRecording()
  // 2. 调用后端生成 resume token
  try {
    const r = await recordingApi.markOfflineFailed(
      businessId.value,
      currentNode.value.code,
      aiResult.issues[0] || 'OTHER',
      JSON.stringify(aiResult)
    )
    resumeToken.value = r.resumeToken
    resumeUrl.value = window.location.origin + r.resumeUrl
    showResumeDialog.value = true
    ElMessageBox.confirm(
      `节点失败: ${aiResult.issues.join(', ')}。是否让客户扫码继续?`,
      '触发线上补录',
      { confirmButtonText: '是, 发起补录', cancelButtonText: '人工介入', type: 'warning' }
    ).then(() => {
      showResumeDialog.value = true
    }).catch(() => {})
  } catch (e: any) {
    ElMessage.error('生成补录 token 失败: ' + e.message)
  }
}

// ============================================================================
// 5. 视频 AI 合规检测 (模拟 + 真实)
// ============================================================================
function startAiCheckLoop() {
  // 每 3 秒跑一次 AI 检测
  aiCheckInterval.value = setInterval(async () => {
    if (!isRecording.value) return
    await runAiCheck()
  }, 3000)
}

async function runAiCheck(): Promise<any> {
  isAiChecking.value = true
  // 模拟 AI 检测 (实际接入会调真实 API)
  const result = {
    faceCount: 1 + Math.floor(Math.random() * 0.5),  // 1 (客户) 或 2 (有第三方)
    blackFrames: Math.random() * 5,                     // 黑屏比例
    thirdParties: Math.random() < 0.05 ? 1 : 0,         // 5% 概率有第三方
    posture: ['FRONT', 'SIDE', 'DOWN'][Math.floor(Math.random() * 3)],
    lighting: (['GOOD', 'GOOD', 'GOOD', 'DIM', 'HARSH'][Math.floor(Math.random() * 5)]) as any,
    watermark: Math.random() > 0.1,
    audioSnr: 18 + Math.random() * 8,
    overallScore: 80 + Math.random() * 15,
    issues: [] as string[]
  }
  // 评估
  if (result.faceCount === 0) result.issues.push('FACE_MISSING')
  if (result.blackFrames > 30) result.issues.push('BLACK_FRAME')
  if (result.thirdParties > 0) result.issues.push('THIRD_PARTY')
  if (result.lighting !== 'GOOD') result.issues.push('LIGHTING_' + result.lighting)
  if (!result.watermark) result.issues.push('WATERMARK_MISSING')
  if (result.audioSnr < 20) result.issues.push('AUDIO_LOW')
  if (result.posture === 'DOWN' || result.posture === 'SIDE') result.issues.push('POSTURE_BAD')
  if (result.issues.length > 0) {
    result.overallScore = Math.max(0, result.overallScore - result.issues.length * 15)
  }

  aiCheckResult.value = result
  // 更新录制品质量摘要
  recordQuality.value = {
    blackFrameRatio: result.blackFrames,
    customerFaceRatio: result.faceCount > 0 ? 95 : 0,
    thirdPartyCount: result.thirdParties,
    score: result.overallScore
  }
  isAiChecking.value = false
  return result
}

// ============================================================================
// 6. ASR 模拟 + 禁播词扫描
// ============================================================================
const asrTimer = ref<number | null>(null)
function onAsrInput() {
  if (asrTimer.value) clearTimeout(asrTimer.value)
  if (liveAsr.value.length < 4) {
    liveHits.value = []
    return
  }
  asrTimer.value = window.setTimeout(() => {
    // 模拟扫描
    const forbidden = ['保证收益', '保本保息', '稳赚不赔', '绝对安全', '刚性兑付', '无风险', '肯定盈利', '肯定超过']
    liveHits.value = forbidden
      .filter(f => liveAsr.value.includes(f))
      .map(f => ({ phrase: f, severity: 'HIGH', regulationRef: '金发〔2026〕8号' }))
  }, 400)
}

function fillExample() {
  const node = currentNode.value
  liveAsr.value = node.scripts.join('。')
  onAsrInput()
}

function injectForbidden() {
  liveAsr.value = '本产品保证收益, 保本保息, 稳赚不赔, 您完全可以放心。'
  onAsrInput()
}

function openResumeUrl() {
  const w: any = (globalThis as any)
  if (w?.open) w.open(resumeUrl.value, '_blank')
}

// ============================================================================
// 7. 生命周期
// ============================================================================
onMounted(() => {
  // 启动 AI 检测 (即使没开始录制, 也能展示能力)
  runAiCheck()
})

onUnmounted(() => {
  stopRecording()
  if (aiCheckInterval.value) clearInterval(aiCheckInterval.value)
})

// 监听 ASR 命中
watch(liveHits, (hits) => {
  if (hits.length > 0 && isRecording.value) {
    // 命中禁播词 → 弹窗
    ElMessageBox.confirm(
      `检测到禁播词: ${hits.map(h => h.phrase).join(', ')}。需要标记本节点失败吗?`,
      '禁播词命中',
      { type: 'error', confirmButtonText: '标记失败 → 线上补录', cancelButtonText: '忽略' }
    ).then(() => {
      failCurrentNode('FORBIDDEN_PHRASE')
    }).catch(() => {})
  }
})
</script>

<template>
  <div class="offline-portal">
    <!-- 顶部 -->
    <header class="opp-header">
      <div class="opp-header-left">
        <div class="opp-logo">🏦</div>
        <div>
          <div class="opp-brand">线下双录面板 · 柜员端</div>
          <div class="opp-sub">{{ branchName }} · 操作员: {{ operatorName }}</div>
        </div>
      </div>
      <div class="opp-header-center">
        <div class="opp-stat">
          <div class="opp-stat-label">业务 ID</div>
          <div class="opp-stat-value mono">{{ businessId }}</div>
        </div>
        <div class="opp-stat">
          <div class="opp-stat-label">进度</div>
          <div class="opp-stat-value">{{ completedNodes.size }} / 8</div>
        </div>
        <div class="opp-stat">
          <div class="opp-stat-label">录制时长</div>
          <div class="opp-stat-value mono">{{ recFormattedTime }}</div>
        </div>
        <div class="opp-stat">
          <div class="opp-stat-label">AI 评分</div>
          <div class="opp-stat-value" :style="{ color: recordQuality.score > 80 ? 'var(--green)' : 'var(--accent-2)' }">
            {{ Math.round(recordQuality.score) }}
          </div>
        </div>
      </div>
      <div class="opp-header-right">
        <el-button v-if="!isRecording" type="primary" size="large" @click="startRecording">
          <el-icon><VideoCamera /></el-icon>开始录制
        </el-button>
        <el-button v-else type="warning" size="large" @click="stopRecording">
          <el-icon><VideoPause /></el-icon>停止录制
        </el-button>
      </div>
    </header>

    <el-row :gutter="16">
      <!-- 左侧: 客户侧 + 理财经理侧 -->
      <el-col :span="16">
        <el-row :gutter="16">
          <el-col :span="12">
            <div class="card">
              <h3 class="card-title">
                <span>👤 客户画面</span>
                <el-tag v-if="isRecording" type="danger" size="small">
                  <span class="rec-dot"></span>REC
                </el-tag>
              </h3>
              <div class="cam-window">
                <video ref="customerVideoRef" autoplay muted playsinline class="cam-video" />
                <div v-if="!isRecording" class="cam-overlay-big">
                  <div class="big-icon">👤</div>
                  <div>点击"开始录制"启动客户摄像头</div>
                </div>
                <div v-if="isRecording" class="face-overlay">
                  <div class="face-box"></div>
                </div>
              </div>
            </div>
          </el-col>
          <el-col :span="12">
            <div class="card">
              <h3 class="card-title">
                <span>👨‍💼 理财经理画面</span>
                <el-tag size="small">{{ operatorName }}</el-tag>
              </h3>
              <div class="cam-window">
                <video ref="tellerVideoRef" autoplay muted playsinline class="cam-video" />
                <div v-if="!isRecording" class="cam-overlay-big">
                  <div class="big-icon">👨‍💼</div>
                  <div>{{ operatorName }}</div>
                </div>
              </div>
            </div>
          </el-col>
        </el-row>

        <!-- 8 节点时间轴 -->
        <div class="card">
          <h3 class="card-title">
            <span>📋 8 节点流程</span>
            <el-progress :percentage="Math.round(recProgressPct)" :stroke-width="10" :show-text="false" style="width: 200px;" />
          </h3>
          <div class="opp-timeline">
            <div
              v-for="(n, idx) in NODES"
              :key="n.code"
              class="opp-node"
              :class="{
                active: idx === currentNodeIdx && isRecording,
                completed: completedNodes.has(idx),
                critical: n.critical
              }"
              @click="currentNodeIdx = idx"
            >
              <div class="opp-node-dot">
                <span v-if="completedNodes.has(idx)">✓</span>
                <span v-else>{{ idx + 1 }}</span>
              </div>
              <div class="opp-node-name">{{ n.name }}</div>
              <div v-if="n.critical" class="opp-node-star">★</div>
            </div>
          </div>
        </div>

        <!-- ASR + 节点话术 -->
        <div class="card">
          <h3 class="card-title">
            <span>🎤 ASR 实时转写 + 禁播词扫描</span>
            <el-tag v-if="liveHits.length > 0" type="danger" size="small">
              ⚠️ {{ liveHits.length }} 个禁播词
            </el-tag>
          </h3>
          <el-input
            v-model="liveAsr"
            type="textarea"
            :rows="4"
            placeholder="实时录入对话内容 (或点击'示例'快速填充)"
            @input="onAsrInput"
            size="large"
          />
          <div class="asr-actions">
            <el-button size="small" @click="fillExample">
              <el-icon><Document /></el-icon>填充当前节点示例
            </el-button>
            <el-button size="small" type="warning" @click="injectForbidden">
              <el-icon><Warning /></el-icon>注入禁播词 (测试)
            </el-button>
          </div>
          <div v-if="liveHits.length > 0" class="hits-list">
            <div v-for="(h, i) in liveHits" :key="i" class="hit-item">
              <el-tag type="danger" size="small">{{ h.severity }}</el-tag>
              <strong>"{{ h.phrase }}"</strong>
              <span class="hit-ref">{{ h.regulationRef }}</span>
            </div>
          </div>
        </div>
      </el-col>

      <!-- 右侧: 当前节点 + 视频 AI 检测 -->
      <el-col :span="8">
        <div class="card">
          <h3 class="card-title">
            <span>📌 当前节点</span>
            <el-tag v-if="currentNode.critical" type="danger" size="small">★ 关键</el-tag>
          </h3>
          <div class="opp-current-node">
            <div class="opp-cn-name">{{ currentNode.name }}</div>
            <div class="opp-cn-desc">{{ currentNode.desc }}</div>
            <div class="opp-cn-duration">建议时长: {{ currentNode.duration }} 秒</div>
          </div>

          <h4 style="margin: 16px 0 8px; font-size: 13px;">📜 当前节点话术 (大字号朗读)</h4>
          <div class="opp-scripts">
            <div v-for="(s, i) in currentNode.scripts" :key="i" class="opp-script-item">
              <div class="opp-script-num">{{ i + 1 }}</div>
              <div class="opp-script-text">{{ s }}</div>
            </div>
          </div>

          <div class="opp-cn-actions">
            <el-button
              type="success"
              size="large"
              @click="completeCurrentNode"
              :disabled="!isRecording || completedNodes.has(currentNodeIdx)"
              style="flex: 2;"
            >
              <el-icon><Check /></el-icon>
              {{ completedNodes.has(currentNodeIdx) ? '已完成' : '本节点完成 → 下一节点' }}
            </el-button>
            <el-button
              size="large"
              @click="skipCurrentNode"
              :disabled="!isRecording"
              plain
            >
              跳过
            </el-button>
          </div>
          <el-button
            v-if="isRecording"
            type="danger"
            size="large"
            @click="failCurrentNode('NO_AFFIRMATIVE')"
            style="width: 100%; margin-top: 8px;"
            plain
          >
            <el-icon><Warning /></el-icon>标记失败 (触发线上补录)
          </el-button>
        </div>

        <!-- 视频 AI 检测结果 -->
        <div class="card">
          <h3 class="card-title">
            <span>🤖 视频 AI 合规检测</span>
            <el-tag v-if="isAiChecking" size="small" type="info">检测中...</el-tag>
            <el-tag v-else-if="aiCheckResult" size="small" :type="aiCheckResult.overallScore > 80 ? 'success' : 'warning'">
              {{ Math.round(aiCheckResult.overallScore) }} 分
            </el-tag>
          </h3>
          <div v-if="aiCheckResult" class="ai-result">
            <div class="ai-row">
              <span class="ai-label">👤 人脸数</span>
              <span class="ai-value" :class="{ bad: aiCheckResult.faceCount === 0 }">
                {{ aiCheckResult.faceCount }}
              </span>
            </div>
            <div class="ai-row">
              <span class="ai-label">⬛ 黑屏</span>
              <span class="ai-value" :class="{ bad: aiCheckResult.blackFrames > 30 }">
                {{ aiCheckResult.blackFrames.toFixed(1) }}%
              </span>
            </div>
            <div class="ai-row">
              <span class="ai-label">👥 第三方</span>
              <span class="ai-value" :class="{ bad: aiCheckResult.thirdParties > 0 }">
                {{ aiCheckResult.thirdParties }}
              </span>
            </div>
            <div class="ai-row">
              <span class="ai-label">📐 姿态</span>
              <span class="ai-value" :class="{ bad: aiCheckResult.posture !== 'FRONT' }">
                {{ aiCheckResult.posture }}
              </span>
            </div>
            <div class="ai-row">
              <span class="ai-label">💡 光照</span>
              <span class="ai-value" :class="{ bad: aiCheckResult.lighting !== 'GOOD' }">
                {{ aiCheckResult.lighting }}
              </span>
            </div>
            <div class="ai-row">
              <span class="ai-label">💧 水印</span>
              <span class="ai-value" :class="{ bad: !aiCheckResult.watermark }">
                {{ aiCheckResult.watermark ? '✓' : '✗' }}
              </span>
            </div>
            <div class="ai-row">
              <span class="ai-label">🔊 音频 SNR</span>
              <span class="ai-value" :class="{ bad: aiCheckResult.audioSnr < 20 }">
                {{ aiCheckResult.audioSnr.toFixed(1) }} dB
              </span>
            </div>
            <div v-if="aiCheckResult.issues.length > 0" class="ai-issues">
              <div class="ai-issue-title">⚠️ 检测到问题</div>
              <el-tag v-for="iss in aiCheckResult.issues" :key="iss" type="danger" size="small" style="margin: 2px;">
                {{ iss }}
              </el-tag>
            </div>
          </div>
          <el-button @click="runAiCheck" :loading="isAiChecking" size="small" style="width: 100%; margin-top: 12px;">
            <el-icon><Refresh /></el-icon>立即检测
          </el-button>
        </div>
      </el-col>
    </el-row>

    <!-- 补录对话框 -->
    <el-dialog v-model="showResumeDialog" title="生成线上补录链接" width="500px">
      <p>线下节点未通过合规检测, 客户可通过此链接在线上完成剩余流程。</p>
      <el-form label-width="80px">
        <el-form-item label="Token">
          <el-input v-model="resumeToken" readonly />
        </el-form-item>
        <el-form-item label="补录链接">
          <el-input v-model="resumeUrl" readonly />
        </el-form-item>
      </el-form>
      <div style="text-align: center; padding: 16px;">
        <div style="display: inline-block; padding: 16px; background: #fff; border: 2px solid #000; border-radius: 8px;">
          <!-- 二维码模拟 (实际生产接 QRCode 库) -->
          <div style="width: 160px; height: 160px; background: repeating-linear-gradient(45deg, #000, #000 4px, #fff 4px, #fff 8px);"></div>
        </div>
        <div style="margin-top: 8px; font-size: 12px; color: var(--ink-3);">客户扫码即可线上补录</div>
      </div>
      <template #footer>
        <el-button @click="showResumeDialog = false">关闭</el-button>
        <el-button type="primary" @click="openResumeUrl">打开链接</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.offline-portal { min-height: 100vh; background: linear-gradient(180deg, #1e2a47 0%, #2a3a5c 100%); color: #fff; }

.opp-header { display: flex; align-items: center; gap: 16px; padding: 12px 24px; background: rgba(0,0,0,0.3); border-bottom: 1px solid rgba(255,255,255,0.1); }
.opp-header-left { display: flex; align-items: center; gap: 10px; min-width: 280px; }
.opp-logo { font-size: 32px; }
.opp-brand { font-size: 16px; font-weight: 700; }
.opp-sub { font-size: 11px; opacity: 0.7; }
.opp-header-center { flex: 1; display: flex; gap: 24px; }
.opp-stat { padding: 4px 12px; background: rgba(255,255,255,0.08); border-radius: 6px; }
.opp-stat-label { font-size: 10px; opacity: 0.6; }
.opp-stat-value { font-size: 14px; font-weight: 700; }
.opp-header-right { display: flex; gap: 8px; }

:deep(.el-row) { padding: 0 16px; }
.card { background: rgba(255,255,255,0.95); color: var(--ink); border-radius: 8px; padding: 12px; margin-bottom: 16px; }
.card-title { font-size: 14px; font-weight: 600; margin: 0 0 12px; color: var(--ink); display: flex; justify-content: space-between; align-items: center; }

.cam-window { position: relative; background: #000; border-radius: 6px; overflow: hidden; aspect-ratio: 4/3; }
.cam-video { width: 100%; height: 100%; object-fit: cover; }
.cam-overlay-big { position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; color: #fff; gap: 8px; }
.big-icon { font-size: 64px; opacity: 0.4; }
.face-overlay { position: absolute; top: 30%; left: 30%; width: 40%; height: 50%; }
.face-box { width: 100%; height: 100%; border: 2px solid var(--accent); border-radius: 8px; box-shadow: 0 0 20px var(--accent); }

.opp-timeline { display: flex; gap: 6px; }
.opp-node { flex: 1; display: flex; flex-direction: column; align-items: center; gap: 4px; padding: 12px 4px; background: var(--bg-2); border-radius: 8px; cursor: pointer; transition: all 0.2s; position: relative; }
.opp-node:hover { background: var(--bg-3); }
.opp-node.active { background: var(--accent); color: #fff; }
.opp-node.completed { background: var(--green); color: #fff; }
.opp-node-dot { width: 32px; height: 32px; border-radius: 50%; background: #fff; color: var(--ink); display: flex; align-items: center; justify-content: center; font-weight: 700; }
.opp-node.active .opp-node-dot, .opp-node.completed .opp-node-dot { background: rgba(255,255,255,0.3); color: #fff; }
.opp-node-name { font-size: 11px; font-weight: 600; text-align: center; }
.opp-node-star { color: var(--accent-2); font-size: 10px; position: absolute; top: 4px; right: 4px; }

.opp-current-node { padding: 12px; background: var(--bg-2); border-radius: 8px; margin-bottom: 12px; }
.opp-cn-name { font-size: 20px; font-weight: 700; color: var(--primary); }
.opp-cn-desc { font-size: 13px; color: var(--ink-3); margin-top: 4px; }
.opp-cn-duration { font-size: 12px; color: var(--ink-3); margin-top: 8px; font-family: 'JetBrains Mono', monospace; }

.opp-scripts { display: flex; flex-direction: column; gap: 8px; }
.opp-script-item { display: flex; align-items: center; gap: 12px; padding: 12px; background: linear-gradient(90deg, rgba(184, 134, 11, 0.1) 0%, transparent 100%); border-left: 4px solid var(--accent); border-radius: 0 6px 6px 0; }
.opp-script-num { width: 28px; height: 28px; border-radius: 50%; background: var(--accent); color: #fff; display: flex; align-items: center; justify-content: center; font-weight: 700; }
.opp-script-text { font-size: 14px; line-height: 1.5; }

.opp-cn-actions { display: flex; gap: 8px; margin-top: 12px; }

.asr-actions { display: flex; gap: 8px; margin-top: 8px; }
.hits-list { margin-top: 8px; display: flex; flex-direction: column; gap: 4px; }
.hit-item { display: flex; align-items: center; gap: 8px; padding: 6px 8px; background: rgba(193, 69, 58, 0.05); border-left: 3px solid var(--accent-2); border-radius: 0 4px 4px 0; font-size: 12px; }
.hit-ref { color: var(--ink-3); margin-left: auto; font-size: 11px; }

.ai-result { font-size: 12px; }
.ai-row { display: flex; justify-content: space-between; padding: 6px 0; border-bottom: 1px dashed var(--line); }
.ai-label { color: var(--ink-3); }
.ai-value { font-weight: 600; }
.ai-value.bad { color: var(--accent-2); }
.ai-issues { margin-top: 8px; padding-top: 8px; border-top: 1px solid var(--line); }
.ai-issue-title { font-size: 11px; color: var(--accent-2); font-weight: 600; margin-bottom: 4px; }

.rec-dot { display: inline-block; width: 8px; height: 8px; border-radius: 50%; background: #fff; margin-right: 4px; animation: pulse 1s infinite; }
@keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.3; } }
</style>
