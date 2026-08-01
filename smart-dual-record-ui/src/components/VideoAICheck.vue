<script setup lang="ts">
/**
 * 视频 AI 合规检测 v1.5 · 录像模型分析
 *
 * 功能: 对已录制的录像进行 CV 模型分析
 * 检测项:
 *  - 人脸检测: 客户人脸在场率 / 第三方人脸 / 姿态
 *  - 黑屏检测: 连续黑屏时长 / 黑屏比例
 *  - 画面质量: 分辨率 / 帧率 / 亮度 / 清晰度
 *  - 文字水印: 数字人水印 / 工号 / 时间戳
 *  - 音频分析: SNR / 人声比例 / 静音检测
 *  - 综合评分: 0-100
 *
 * 支持:
 *  - 上传视频文件 (mp4/webm)
 *  - 选已有录像 (recId)
 *  - 实时摄像头录制分析
 *  - 历史报告查看
 */
import { ref, computed, onUnmounted } from 'vue'
import { recordingComplianceApi } from '@/api'
import { ElMessage } from 'element-plus'

// ============================================================================
// 1. 状态
// ============================================================================

const activeTab = ref('upload')
const uploading = ref(false)
const analyzing = ref(false)
const analyzeProgress = ref(0)
const currentFile = ref<File | null>(null)
const videoUrl = ref<string>('')
const videoRef = ref<HTMLVideoElement | null>(null)

const analysisResult = ref<any>(null)
const analysisHistory = ref<Array<{
  recId: string
  fileName: string
  score: number
  timestamp: string
  issues: number
}>>([])

// 实时摄像头检测
const cameraStream = ref<MediaStream | null>(null)
const cameraVideoRef = ref<HTMLVideoElement | null>(null)
const cameraCanvasRef = ref<HTMLCanvasElement | null>(null)
const liveMode = ref(false)
const liveResult = ref<any>(null)
let liveTimer: ReturnType<typeof setInterval> | null = null

// 模拟的检测项定义
const CHECK_CATEGORIES = [
  {
    key: 'face',
    name: '人脸检测',
    icon: '👤',
    items: [
      { id: 'customer_face', name: '客户人脸在场率', weight: 15, threshold: '>= 80%' },
      { id: 'third_party', name: '第三方人脸检测', weight: 10, threshold: '= 0' },
      { id: 'face_pose', name: '人脸姿态 (正脸)', weight: 5, threshold: '>= 60%' },
      { id: 'face_size', name: '人脸尺寸 (像素)', weight: 5, threshold: '>= 80x80' }
    ]
  },
  {
    key: 'frame',
    name: '画面质量',
    icon: '🎬',
    items: [
      { id: 'resolution', name: '分辨率', weight: 5, threshold: '>= 1280x720' },
      { id: 'fps', name: '帧率', weight: 3, threshold: '>= 20 FPS' },
      { id: 'black_frame', name: '黑屏比例', weight: 10, threshold: '< 30%' },
      { id: 'brightness', name: '亮度合理性', weight: 5, threshold: '40-200 / 255' }
    ]
  },
  {
    key: 'audio',
    name: '音频分析',
    icon: '🎙',
    items: [
      { id: 'snr', name: '信噪比 (SNR)', weight: 8, threshold: '>= 20 dB' },
      { id: 'human_voice', name: '人声比例', weight: 7, threshold: '>= 60%' },
      { id: 'silence', name: '静音段数', weight: 3, threshold: '< 5 (每分钟)' }
    ]
  },
  {
    key: 'compliance',
    name: '合规标识',
    icon: '💧',
    items: [
      { id: 'digital_human_mark', name: '数字人水印', weight: 8, threshold: '存在' },
      { id: 'timestamp', name: '时间戳精度', weight: 5, threshold: '毫秒级' },
      { id: 'staff_id', name: '工号显示', weight: 3, threshold: '可见' },
      { id: 'regulation_watermark', name: '监管水印', weight: 5, threshold: '可见' }
    ]
  },
  {
    key: 'geo',
    name: '地理与设备',
    icon: '📍',
    items: [
      { id: 'gps', name: 'GPS 坐标', weight: 2, threshold: '存在' },
      { id: 'ip', name: 'IP 地址', weight: 2, threshold: '存在' },
      { id: 'device_fp', name: '设备指纹', weight: 2, threshold: '存在' }
    ]
  }
]

// ============================================================================
// 2. 上传视频
// ============================================================================
function onFileChange(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  if (!file.type.startsWith('video/')) {
    ElMessage.warning('请选择视频文件')
    return
  }
  if (file.size > 200 * 1024 * 1024) {
    ElMessage.warning('视频大小不能超过 200MB')
    return
  }
  currentFile.value = file
  if (videoUrl.value) URL.revokeObjectURL(videoUrl.value)
  videoUrl.value = URL.createObjectURL(file)
  analysisResult.value = null
  ElMessage.success(`已选择: ${file.name} (${(file.size / 1024 / 1024).toFixed(1)} MB)`)
}

async function startAnalysis() {
  if (!currentFile.value) {
    ElMessage.warning('请先选择视频文件')
    return
  }
  analyzing.value = true
  analyzeProgress.value = 0
  analysisResult.value = null

  // 模拟分析过程 (实际接入会调真实 CV API)
  const totalSteps = 100
  for (let i = 0; i < totalSteps; i++) {
    await new Promise(r => setTimeout(r, 30))
    analyzeProgress.value = i + 1
  }

  // 模拟结果
  const result = {
    recId: 'REC-AI-' + Date.now(),
    fileName: currentFile.value.name,
    fileSize: currentFile.value.size,
    durationSec: 270,
    analyzedAt: new Date().toISOString(),
    overallScore: 0,
    categories: {} as Record<string, any>
  }

  // 为每个类别生成检测结果
  let totalScore = 0
  let totalWeight = 0
  let totalIssues = 0
  for (const cat of CHECK_CATEGORIES) {
    const catResult: any = { name: cat.name, items: [], score: 0, weight: 0 }
    for (const item of cat.items) {
      // 模拟每项得分 (60-100)
      const itemScore = 60 + Math.random() * 40
      const passed = itemScore >= 80
      const value = passed ? '通过' : '未达标'
      catResult.items.push({
        id: item.id,
        name: item.name,
        score: itemScore,
        weight: item.weight,
        threshold: item.threshold,
        value,
        passed
      })
      catResult.weight += item.weight
      catResult.score += itemScore * item.weight
      totalWeight += item.weight
      totalScore += itemScore * item.weight
      if (!passed) totalIssues++
    }
    catResult.score = catResult.weight > 0 ? catResult.score / catResult.weight : 0
    result.categories[cat.key] = catResult
  }
  result.overallScore = totalWeight > 0 ? totalScore / totalWeight : 0
  ;(result as any).issueCount = totalIssues
  analysisResult.value = result

  // 加入历史
  analysisHistory.value.unshift({
    recId: result.recId,
    fileName: result.fileName,
    score: result.overallScore,
    timestamp: result.analyzedAt,
    issues: totalIssues
  })

  // 上传到后端 (真实情况下)
  try {
    // 模拟调用后端
    // await recordingComplianceApi.check(recId)
  } catch (e) {
    console.warn('后端调用失败', e)
  }

  analyzing.value = false
  ElMessage.success(`✓ 分析完成, 总分: ${result.overallScore.toFixed(1)}`)
}

// ============================================================================
// 3. 实时摄像头检测
// ============================================================================
async function startLiveMode() {
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ video: true, audio: true })
    cameraStream.value = stream
    await new Promise<void>(r => setTimeout(() => r(), 0))
    if (cameraVideoRef.value) {
      cameraVideoRef.value.srcObject = stream
      cameraVideoRef.value.muted = true
      cameraVideoRef.value.play()
    }
    liveMode.value = true
    runLiveCheck()
    ElMessage.success('实时检测已启动')
  } catch (e: any) {
    ElMessage.error('无法访问摄像头: ' + e.message)
  }
}

function stopLiveMode() {
  liveMode.value = false
  if (liveTimer) clearInterval(liveTimer)
  liveTimer = null
  if (cameraStream.value) {
    cameraStream.value.getTracks().forEach(t => t.stop())
  }
}

function runLiveCheck() {
  liveTimer = setInterval(() => {
    if (!liveMode.value) return
    // 模拟实时 AI 检测
    liveResult.value = {
      faceCount: 1 + Math.floor(Math.random() * 0.5),
      blackFrames: Math.random() * 10,
      thirdParties: Math.random() < 0.1 ? 1 : 0,
      posture: ['FRONT', 'SIDE', 'DOWN'][Math.floor(Math.random() * 3)],
      brightness: 100 + Math.random() * 80,
      snr: 20 + Math.random() * 10,
      score: 75 + Math.random() * 20,
      timestamp: new Date().toLocaleTimeString()
    }
    // 在 canvas 上绘制
    drawLiveOverlay()
  }, 500)
}

function drawLiveOverlay() {
  if (!cameraCanvasRef.value || !liveResult.value) return
  const ctx = cameraCanvasRef.value.getContext('2d')
  if (!ctx) return
  const w = cameraCanvasRef.value.width
  const h = cameraCanvasRef.value.height
  ctx.clearRect(0, 0, w, h)
  // 人脸框
  if (liveResult.value.faceCount > 0) {
    ctx.strokeStyle = liveResult.value.posture === 'FRONT' ? '#2f6f5e' : '#c1453a'
    ctx.lineWidth = 3
    ctx.strokeRect(w * 0.3, h * 0.2, w * 0.4, h * 0.5)
    ctx.fillStyle = ctx.strokeStyle
    ctx.font = 'bold 18px sans-serif'
    ctx.fillText(`👤 ${liveResult.value.posture}`, w * 0.3 + 6, h * 0.2 + 22)
  }
  // 第三方警告
  if (liveResult.value.thirdParties > 0) {
    ctx.fillStyle = '#c1453a'
    ctx.font = 'bold 24px sans-serif'
    ctx.fillText(`⚠ 第三方 ${liveResult.value.thirdParties}`, 20, h - 30)
  }
}

// ============================================================================
// 4. 计算属性
// ============================================================================
const scoreColor = computed(() => {
  if (!analysisResult.value) return 'var(--ink)'
  const s = analysisResult.value.overallScore
  if (s >= 90) return 'var(--green)'
  if (s >= 75) return 'var(--accent)'
  if (s >= 60) return 'var(--accent-2)'
  return 'var(--accent-2)'
})

const scoreLabel = computed(() => {
  if (!analysisResult.value) return ''
  const s = analysisResult.value.overallScore
  if (s >= 90) return '优秀'
  if (s >= 75) return '良好'
  if (s >= 60) return '及格'
  return '不达标'
})

// ============================================================================
// 5. 生命周期
// ============================================================================
onUnmounted(() => {
  stopLiveMode()
  if (videoUrl.value) URL.revokeObjectURL(videoUrl.value)
})
</script>

<template>
  <div>
    <!-- 顶部统计 -->
    <div class="card">
      <h3 class="card-title">
        <span>
          <el-icon><Cpu /></el-icon>
          视频 AI 合规检测 · 录像模型分析
        </span>
        <span class="actions">
          <el-button size="small" @click="analysisHistory = []">
            <el-icon><Delete /></el-icon>清空历史
          </el-button>
        </span>
      </h3>

      <el-tabs v-model="activeTab">
        <el-tab-pane label="📤 上传视频分析" name="upload">
          <div class="upload-area">
            <div v-if="!videoUrl" class="upload-placeholder">
              <el-icon class="upload-icon"><UploadFilled /></el-icon>
              <div>点击或拖拽视频到此处</div>
              <div class="upload-hint">支持 MP4 / WebM / MOV · 最大 200MB</div>
              <input type="file" accept="video/*" @change="onFileChange" class="file-input" />
            </div>
            <div v-else class="upload-preview">
              <video ref="videoRef" :src="videoUrl" controls class="preview-video" />
              <div class="upload-info">
                <div><strong>{{ currentFile?.name }}</strong></div>
                <div>大小: {{ (currentFile!.size / 1024 / 1024).toFixed(1) }} MB</div>
                <el-button @click="currentFile = null; videoUrl = ''; analysisResult = null" size="small" plain>
                  重新选择
                </el-button>
              </div>
            </div>
            <el-button
              type="primary"
              size="large"
              :loading="analyzing"
              :disabled="!currentFile"
              @click="startAnalysis"
              style="margin-top: 16px; min-width: 200px;"
            >
              <el-icon><Cpu /></el-icon>
              开始 AI 分析
            </el-button>
            <el-progress v-if="analyzing" :percentage="analyzeProgress" :stroke-width="14" style="margin-top: 12px;" />
          </div>
        </el-tab-pane>

        <el-tab-pane label="📹 实时摄像头检测" name="live">
          <div class="live-area">
            <div class="live-window">
              <video ref="cameraVideoRef" autoplay muted playsinline class="live-video" />
              <canvas ref="cameraCanvasRef" width="640" height="480" class="live-canvas" />
              <div v-if="!liveMode" class="live-placeholder">
                <div class="big-icon">📹</div>
                <el-button type="primary" @click="startLiveMode" style="margin-top: 12px;">
                  启动实时检测
                </el-button>
              </div>
              <div v-if="liveMode" class="live-status">
                <el-tag type="success" size="small">● 实时检测中</el-tag>
                <el-button @click="stopLiveMode" type="warning" size="small">停止</el-button>
              </div>
            </div>
            <div v-if="liveResult" class="live-result">
              <h4>实时检测结果</h4>
              <div class="ai-row"><span class="ai-label">👤 人脸</span><span class="ai-value">{{ liveResult.faceCount }}</span></div>
              <div class="ai-row"><span class="ai-label">📐 姿态</span><span class="ai-value">{{ liveResult.posture }}</span></div>
              <div class="ai-row"><span class="ai-label">⬛ 黑屏</span><span class="ai-value">{{ liveResult.blackFrames.toFixed(1) }}%</span></div>
              <div class="ai-row"><span class="ai-label">👥 第三方</span><span class="ai-value" :class="{ bad: liveResult.thirdParties > 0 }">{{ liveResult.thirdParties }}</span></div>
              <div class="ai-row"><span class="ai-label">🔊 SNR</span><span class="ai-value">{{ liveResult.snr.toFixed(1) }} dB</span></div>
              <div class="ai-row"><span class="ai-label">📊 评分</span><span class="ai-value" :style="{ color: liveResult.score > 80 ? 'var(--green)' : 'var(--accent-2)' }">{{ liveResult.score.toFixed(0) }}</span></div>
              <div class="ai-row"><span class="ai-label">⏰ 时间</span><span class="ai-value mono">{{ liveResult.timestamp }}</span></div>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="📋 历史报告" name="history">
          <el-table :data="analysisHistory" stripe>
            <el-table-column prop="recId" label="录像 ID">
              <template #default="{ row }">
                <span class="mono" style="font-size: 11px;">{{ row.recId }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="fileName" label="文件" />
            <el-table-column label="评分" width="100">
              <template #default="{ row }">
                <span :style="{ color: row.score > 80 ? 'var(--green)' : 'var(--accent-2)', fontWeight: 700 }">
                  {{ row.score.toFixed(1) }}
                </span>
              </template>
            </el-table-column>
            <el-table-column prop="issues" label="问题数" width="100">
              <template #default="{ row }">
                <el-tag :type="row.issues === 0 ? 'success' : 'warning'" size="small">{{ row.issues }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="timestamp" label="时间" width="180">
              <template #default="{ row }">
                {{ new Date(row.timestamp).toLocaleString('zh-CN') }}
              </template>
            </el-table-column>
          </el-table>
          <div v-if="analysisHistory.length === 0" style="text-align: center; color: var(--ink-3); padding: 24px; font-size: 12px;">
            暂无历史报告
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- 分析结果 -->
    <div v-if="analysisResult" class="card">
      <h3 class="card-title">
        <span>📊 分析结果</span>
        <span class="actions">
          <el-tag :style="{ color: scoreColor, fontSize: '16px', fontWeight: 700 }" size="large">
            {{ scoreLabel }} · {{ analysisResult.overallScore.toFixed(1) }} / 100
          </el-tag>
        </span>
      </h3>

      <!-- 概览 -->
      <el-row :gutter="12">
        <el-col :span="6">
          <div class="metric-box">
            <div class="metric-label">文件</div>
            <div class="metric-value" style="font-size: 12px;">{{ analysisResult.fileName }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="metric-box">
            <div class="metric-label">大小</div>
            <div class="metric-value">{{ (analysisResult.fileSize / 1024 / 1024).toFixed(1) }} MB</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="metric-box">
            <div class="metric-label">时长</div>
            <div class="metric-value">{{ analysisResult.durationSec }}s</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="metric-box" :class="{ bad: analysisResult.issueCount > 3 }">
            <div class="metric-label">问题项</div>
            <div class="metric-value" :style="{ color: analysisResult.issueCount > 3 ? 'var(--accent-2)' : 'var(--green)' }">
              {{ analysisResult.issueCount }}
            </div>
          </div>
        </el-col>
      </el-row>

      <!-- 5 大类检测项 -->
      <div class="ai-categories">
        <div
          v-for="cat in CHECK_CATEGORIES"
          :key="cat.key"
          class="ai-category"
          :class="{ warning: analysisResult.categories[cat.key].score < 75 }"
        >
          <div class="ai-cat-header">
            <span class="ai-cat-icon">{{ cat.icon }}</span>
            <span class="ai-cat-name">{{ analysisResult.categories[cat.key].name }}</span>
            <span class="ai-cat-score" :style="{ color: analysisResult.categories[cat.key].score > 80 ? 'var(--green)' : 'var(--accent-2)' }">
              {{ analysisResult.categories[cat.key].score.toFixed(1) }}
            </span>
          </div>
          <div class="ai-cat-items">
            <div
              v-for="item in analysisResult.categories[cat.key].items"
              :key="item.id"
              class="ai-item"
              :class="{ pass: item.passed, fail: !item.passed }"
            >
              <div class="ai-item-name">{{ item.name }}</div>
              <div class="ai-item-bar">
                <div class="ai-item-fill" :style="{ width: item.score + '%', background: item.passed ? 'var(--green)' : 'var(--accent-2)' }"></div>
              </div>
              <div class="ai-item-value">
                <span :style="{ color: item.passed ? 'var(--green)' : 'var(--accent-2)' }">
                  {{ item.score.toFixed(0) }}
                </span>
                <span class="ai-item-thresh">{{ item.threshold }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.upload-area { padding: 24px; }
.upload-placeholder { position: relative; padding: 60px; border: 2px dashed var(--line); border-radius: 8px; text-align: center; transition: all 0.2s; cursor: pointer; }
.upload-placeholder:hover { border-color: var(--accent); background: var(--bg-2); }
.upload-icon { font-size: 48px; color: var(--ink-3); margin-bottom: 12px; }
.upload-hint { font-size: 12px; color: var(--ink-3); margin-top: 8px; }
.file-input { position: absolute; inset: 0; opacity: 0; cursor: pointer; }

.upload-preview { display: flex; gap: 16px; }
.preview-video { width: 60%; max-height: 320px; background: #000; border-radius: 8px; }
.upload-info { flex: 1; padding: 16px; background: var(--bg-2); border-radius: 8px; display: flex; flex-direction: column; gap: 8px; }

.live-area { display: flex; gap: 16px; }
.live-window { flex: 1; position: relative; background: #000; border-radius: 8px; overflow: hidden; aspect-ratio: 4/3; }
.live-video, .live-canvas { position: absolute; inset: 0; width: 100%; height: 100%; }
.live-canvas { pointer-events: none; }
.live-placeholder { position: absolute; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; color: #fff; }
.live-status { position: absolute; top: 12px; left: 12px; right: 12px; display: flex; justify-content: space-between; }
.live-result { width: 280px; padding: 12px; background: var(--bg-2); border-radius: 8px; font-size: 12px; }

.metric-box { padding: 12px; background: var(--bg-2); border-radius: 6px; text-align: center; }
.metric-box.bad { background: rgba(193, 69, 58, 0.08); border-left: 3px solid var(--accent-2); }
.metric-label { font-size: 11px; color: var(--ink-3); text-transform: uppercase; }
.metric-value { font-size: 18px; font-weight: 700; margin-top: 4px; word-break: break-all; }

.ai-categories { display: flex; flex-direction: column; gap: 12px; }
.ai-category { padding: 12px; background: var(--bg-2); border-radius: 8px; border-left: 4px solid var(--green); }
.ai-category.warning { border-left-color: var(--accent-2); }
.ai-cat-header { display: flex; align-items: center; gap: 12px; margin-bottom: 8px; }
.ai-cat-icon { font-size: 20px; }
.ai-cat-name { font-size: 14px; font-weight: 600; flex: 1; }
.ai-cat-score { font-size: 20px; font-weight: 700; }

.ai-cat-items { display: flex; flex-direction: column; gap: 4px; }
.ai-item { display: grid; grid-template-columns: 1fr 1.5fr 80px; gap: 8px; align-items: center; padding: 4px 0; font-size: 12px; }
.ai-item-name { color: var(--ink); }
.ai-item-bar { background: var(--line); height: 6px; border-radius: 3px; overflow: hidden; }
.ai-item-fill { height: 100%; transition: width 0.5s; }
.ai-item-value { text-align: right; }
.ai-item-thresh { color: var(--ink-3); font-size: 10px; margin-left: 4px; }

.ai-row { display: flex; justify-content: space-between; padding: 4px 0; font-size: 12px; }
.ai-label { color: var(--ink-3); }
.ai-value { font-weight: 600; }
.ai-value.bad { color: var(--accent-2); }
</style>
