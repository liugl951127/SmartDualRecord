<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { recordingComplianceApi, recordingApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'

/**
 * 录像合规面板 (v1.2)
 *
 * 5 大功能:
 *  1. 32 项检查清单展示
 *  2. 跑某录像的合规检查 → 显示 PASS / PASS_WITH_FINDINGS / FAIL
 *  3. 签发回放 token (5 分钟)
 *  4. 断点续传 session
 *  5. 留存扫描 + 归档
 */

const recId = ref('')
const businessId = ref('')
const checklist = ref<any[]>([])
const report = ref<any>(null)
const annotations = ref<any[]>([])
const accessLogs = ref<any[]>([])
const playbackToken = ref<any>(null)
const userId = ref('auditor-001')
const userRole = ref('AUDITOR')

const uploadSession = ref<any>(null)
const uploadStatus = ref<any>(null)
const retentionResult = ref<any>(null)
const beforeDate = ref('2025-01-01')

const loading = ref(false)
const activeTab = ref('check')

onMounted(async () => {
  try {
    checklist.value = await recordingComplianceApi.checklist()
  } catch (e) {
    console.error('加载清单失败', e)
  }
})

async function runCheck() {
  if (!recId.value) {
    ElMessage.warning('请输入录像 ID')
    return
  }
  loading.value = true
  try {
    report.value = await recordingComplianceApi.check(recId.value)
    if (report.value.status === 'FAIL') {
      ElMessage.error(`录像合规 FAIL: ${report.value.criticalCount} 项严重失败`)
    } else if (report.value.status === 'PASS_WITH_FINDINGS') {
      ElMessage.warning(`录像合规 PASS_WITH_FINDINGS: ${report.value.warnCount} 项警告`)
    } else {
      ElMessage.success(`录像合规 PASS: 总分 ${report.value.score}`)
    }
  } catch (e: any) {
    ElMessage.error(`检查失败: ${e.message}`)
  } finally {
    loading.value = false
  }
}

async function listAnnotations() {
  if (!recId.value) {
    ElMessage.warning('请输入录像 ID')
    return
  }
  try {
    annotations.value = await recordingComplianceApi.listAnnotations(recId.value)
  } catch (e: any) {
    ElMessage.error(`加载标注失败: ${e.message}`)
  }
}

async function listAccessLog() {
  if (!recId.value) {
    ElMessage.warning('请输入录像 ID')
    return
  }
  try {
    accessLogs.value = await recordingComplianceApi.accessLog(recId.value)
  } catch (e: any) {
    ElMessage.error(`加载访问日志失败: ${e.message}`)
  }
}

async function issueToken() {
  if (!recId.value) {
    ElMessage.warning('请输入录像 ID')
    return
  }
  try {
    playbackToken.value = await recordingComplianceApi.playbackToken(
      recId.value, userId.value, userRole.value, 300)
    ElMessage.success(`✓ 回放 token 已签发 (5 分钟有效)`)
  } catch (e: any) {
    ElMessage.error(`签发失败: ${e.message}`)
  }
}

async function initUpload() {
  if (!businessId.value) {
    ElMessage.warning('请输入业务 ID')
    return
  }
  try {
    uploadSession.value = await recordingComplianceApi.uploadInit({
      businessId: businessId.value,
      channel: 'OFFLINE',
      totalChunks: 5,
      totalSizeBytes: 5 * 1024 * 1024 * 5  // 5MB × 5
    })
    ElMessage.success(`✓ 上传 session 已初始化: ${uploadSession.value.sessionId}`)
  } catch (e: any) {
    ElMessage.error(`初始化失败: ${e.message}`)
  }
}

async function uploadOneChunk() {
  if (!uploadSession.value) {
    ElMessage.warning('请先初始化 session')
    return
  }
  const next = (uploadStatus.value?.uploadedChunks ?? 0)
  try {
    uploadStatus.value = await recordingComplianceApi.uploadChunk(
      uploadSession.value.sessionId, next)
    ElMessage.success(`✓ 上传分片 ${next}/${uploadSession.value.totalChunks}`)
  } catch (e: any) {
    ElMessage.error(`上传失败: ${e.message}`)
  }
}

async function queryUploadStatus() {
  if (!uploadSession.value) {
    ElMessage.warning('请先初始化 session')
    return
  }
  try {
    uploadStatus.value = await recordingComplianceApi.uploadStatus(uploadSession.value.sessionId)
  } catch (e: any) {
    ElMessage.error(`查询失败: ${e.message}`)
  }
}

async function scanRetention() {
  try {
    await recordingComplianceApi.retentionScan()
    ElMessage.success('✓ 留存扫描已触发 (T-30 天预警 / T-7 天通知)')
  } catch (e: any) {
    ElMessage.error(`扫描失败: ${e.message}`)
  }
}

async function archiveRetention() {
  try {
    const result = await recordingComplianceApi.retentionArchive(beforeDate.value)
    retentionResult.value = result
    ElMessage.success(`✓ 归档候选: ${result.archivedCount} 个 (recEnd < ${result.beforeDate})`)
  } catch (e: any) {
    ElMessage.error(`归档失败: ${e.message}`)
  }
}

const categoryColors: Record<string, string> = {
  BASIC: 'var(--primary)',
  TIME: 'var(--blue)',
  VIDEO: 'var(--accent)',
  AUDIO: 'var(--green)',
  INTEGRITY: 'var(--accent-2)',
  MARK: 'var(--purple)',
  GEO: 'var(--blue)',
  ANNOTATION: 'var(--green)'
}

const groupedChecklist = computed(() => {
  const groups: Record<string, any[]> = {}
  for (const item of checklist.value) {
    if (!groups[item.category]) groups[item.category] = []
    groups[item.category].push(item)
  }
  return groups
})

import { computed } from 'vue'
</script>

<template>
  <div>
    <!-- 顶部：录像 + 业务 ID 输入 -->
    <div class="card">
      <el-row :gutter="16">
        <el-col :span="10">
          <el-form-item label="录像 ID (recId)">
            <el-input v-model="recId" placeholder="例如 REC-XXX" />
          </el-form-item>
        </el-col>
        <el-col :span="10">
          <el-form-item label="业务 ID (businessId, 续传用)">
            <el-input v-model="businessId" placeholder="例如 BNK-001" />
          </el-form-item>
        </el-col>
        <el-col :span="4">
          <el-form-item label=" ">
            <el-button type="primary" :loading="loading" @click="runCheck">
              <el-icon><Check /></el-icon>
              跑 32 项检查
            </el-button>
          </el-form-item>
        </el-col>
      </el-row>
    </div>

    <el-tabs v-model="activeTab">
      <!-- ===== Tab 1: 32 项检查结果 ===== -->
      <el-tab-pane label="① 32 项检查结果" name="check">
        <div v-if="report" class="card">
          <h3 class="card-title">
            <span>检查报告 · 录像 {{ recId }}</span>
            <span class="state-badge" :style="{
              color: report.status === 'PASS' ? 'var(--green)' :
                     report.status === 'PASS_WITH_FINDINGS' ? 'var(--accent)' : 'var(--accent-2)'
            }">
              {{ report.status }} · 总分 {{ report.score }}
            </span>
          </h3>

          <el-row :gutter="16" style="margin-bottom: 16px;">
            <el-col :span="8">
              <div class="metric-box">
                <div class="metric-label">总分</div>
                <div class="metric-value" :style="{ color: report.score >= 80 ? 'var(--green)' : report.score >= 60 ? 'var(--accent)' : 'var(--accent-2)' }">
                  {{ report.score }}
                </div>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="metric-box">
                <div class="metric-label">严重失败</div>
                <div class="metric-value" style="color: var(--accent-2);">
                  {{ report.criticalCount }}
                </div>
              </div>
            </el-col>
            <el-col :span="8">
              <div class="metric-box">
                <div class="metric-label">警告</div>
                <div class="metric-value" style="color: var(--accent);">
                  {{ report.warnCount }}
                </div>
              </div>
            </el-col>
          </el-row>

          <el-table :data="report.results" stripe>
            <el-table-column prop="seq" label="#" width="50" />
            <el-table-column prop="category" label="分类" width="100">
              <template #default="{ row }">
                <el-tag size="small" :style="{ background: categoryColors[row.category] + '20', color: categoryColors[row.category], border: 'none' }">
                  {{ row.category }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="name" label="检查项" width="200" />
            <el-table-column prop="actualValue" label="实际值">
              <template #default="{ row }">
                <span class="mono" style="font-size: 11px;">{{ row.actualValue }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="regulationRef" label="监管依据" width="220">
              <template #default="{ row }">
                <span style="font-size: 11px; color: var(--ink-3);">{{ row.regulationRef }}</span>
              </template>
            </el-table-column>
            <el-table-column label="结果" width="80">
              <template #default="{ row }">
                <el-tag v-if="row.severity === 'OK'" type="success" size="small">通过</el-tag>
                <el-tag v-else-if="row.severity === 'WARN'" type="warning" size="small">警告</el-tag>
                <el-tag v-else type="danger" size="small">失败</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </div>

        <div v-else class="card">
          <div style="text-align: center; padding: 40px; color: var(--ink-3);">
            <p>输入录像 ID 并点击「跑 32 项检查」</p>
          </div>
        </div>
      </el-tab-pane>

      <!-- ===== Tab 2: 32 项检查清单定义 ===== -->
      <el-tab-pane label="② 检查清单定义" name="checklist">
        <div v-for="(items, cat) in groupedChecklist" :key="cat" class="card">
          <h3 class="card-title">
            <span :style="{ color: categoryColors[cat] }">{{ cat }} · {{ items.length }} 项</span>
          </h3>
          <el-table :data="items" stripe size="small">
            <el-table-column prop="seq" label="#" width="50" />
            <el-table-column prop="name" label="检查项" width="200" />
            <el-table-column prop="description" label="说明" />
            <el-table-column prop="severityOnFail" label="失败等级" width="100">
              <template #default="{ row }">
                <el-tag :type="row.severityOnFail === 'FAIL' ? 'danger' : 'warning'" size="small">
                  {{ row.severityOnFail }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="regulationRef" label="监管依据" width="220">
              <template #default="{ row }">
                <span style="font-size: 11px; color: var(--ink-3);">{{ row.regulationRef }}</span>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-tab-pane>

      <!-- ===== Tab 3: 事件标注 ===== -->
      <el-tab-pane label="③ 事件标注" name="annotations">
        <div class="card">
          <h3 class="card-title">
            <span>关键事件时间戳标注</span>
            <span class="actions">
              <el-button size="small" @click="listAnnotations"><el-icon><Refresh /></el-icon></el-button>
            </span>
          </h3>
          <p style="color: var(--ink-2); font-size: 13px; margin: 0 0 12px;">
            8 节点进度 / 风险揭示 / 客户肯定 / 签字时刻 / 禁播词命中 / 反深伪命中
          </p>
          <el-table :data="annotations" stripe size="small">
            <el-table-column prop="annotationType" label="类型" width="180">
              <template #default="{ row }">
                <el-tag size="small" :type="row.annotationType === 'CUSTOMER_AFFIRMATIVE' ? 'success' :
                                            row.annotationType === 'DEEPFAKE_SUSPECT' ? 'danger' : 'info'">
                  {{ row.annotationType }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="nodeId" label="节点" width="160" />
            <el-table-column prop="timestampMs" label="录像内偏移 (ms)" width="140">
              <template #default="{ row }">
                <span class="mono">{{ row.timestampMs }}</span>
              </template>
            </el-table-column>
            <el-table-column prop="note" label="备注" />
            <el-table-column prop="operatorId" label="操作员" width="120" />
            <el-table-column prop="createdAt" label="写入时间" width="200" />
          </el-table>
        </div>
      </el-tab-pane>

      <!-- ===== Tab 4: 回放 + DRM ===== -->
      <el-tab-pane label="④ 回放 + DRM" name="playback">
        <div class="card">
          <h3 class="card-title">签发 5 分钟回放 token</h3>
          <el-row :gutter="16">
            <el-col :span="8">
              <el-form-item label="用户 ID">
                <el-input v-model="userId" />
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="角色">
                <el-select v-model="userRole" style="width: 100%;">
                  <el-option value="CUSTOMER" label="客户" />
                  <el-option value="SELLER" label="理财经理" />
                  <el-option value="AUDITOR" label="审计" />
                  <el-option value="REGULATOR" label="监管" />
                  <el-option value="ADMIN" label="管理员" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label=" ">
                <el-button type="primary" @click="issueToken">
                  <el-icon><VideoCamera /></el-icon>
                  签发 token
                </el-button>
              </el-form-item>
            </el-col>
          </el-row>
          <el-alert
            v-if="playbackToken"
            type="success"
            :title="`✓ Token 已签发 (${playbackToken.ttlSec}s 有效)`"
            :closable="false"
            style="margin-top: 12px;"
          >
            <template #default>
              <div style="word-break: break-all; font-family: 'JetBrains Mono', monospace; font-size: 11px;">
                <strong>URL:</strong> {{ playbackToken.url }}
              </div>
            </template>
          </el-alert>
        </div>

        <div class="card">
          <h3 class="card-title">
            <span>录像访问审计日志</span>
            <span class="actions">
              <el-button size="small" @click="listAccessLog"><el-icon><Refresh /></el-icon></el-button>
            </span>
          </h3>
          <el-table :data="accessLogs" stripe size="small">
            <el-table-column prop="userId" label="用户" width="140" />
            <el-table-column prop="userRole" label="角色" width="100" />
            <el-table-column prop="accessType" label="操作" width="100" />
            <el-table-column prop="durationSec" label="时长 (秒)" width="100" />
            <el-table-column prop="ipAddress" label="IP" width="140" />
            <el-table-column prop="accessToken" label="Token (脱敏)" width="180" />
            <el-table-column prop="accessedAt" label="访问时间" />
          </el-table>
        </div>
      </el-tab-pane>

      <!-- ===== Tab 5: 断点续传 ===== -->
      <el-tab-pane label="⑤ 断点续传" name="upload">
        <div class="card">
          <h3 class="card-title">分片上传 (5MB/片, 7 天过期)</h3>
          <el-button @click="initUpload" type="primary" :disabled="!businessId">
            <el-icon><Plus /></el-icon>
            初始化 Session
          </el-button>
          <el-button @click="uploadOneChunk" :disabled="!uploadSession">
            <el-icon><Upload /></el-icon>
            上传 1 个分片
          </el-button>
          <el-button @click="queryUploadStatus" :disabled="!uploadSession">
            <el-icon><Refresh /></el-icon>
            查询进度
          </el-button>

          <div v-if="uploadSession" style="margin-top: 12px; padding: 12px; background: var(--bg-2); border-radius: 6px;">
            <div><strong>Session ID:</strong> <span class="mono" style="font-size: 12px;">{{ uploadSession.sessionId }}</span></div>
            <div><strong>进度:</strong> {{ uploadStatus?.uploadedChunks || 0 }} / {{ uploadSession.totalChunks }} 片</div>
            <div><strong>分片大小:</strong> {{ (uploadSession.chunkSize / 1024 / 1024).toFixed(1) }} MB</div>
            <div><strong>总大小:</strong> {{ (uploadSession.totalSizeBytes / 1024 / 1024).toFixed(1) }} MB</div>
            <div><strong>状态:</strong>
              <el-tag size="small" :type="uploadStatus?.complete ? 'success' : 'info'">
                {{ uploadStatus?.complete ? '已完成' : '上传中' }}
              </el-tag>
            </div>
            <div><strong>过期时间:</strong> {{ uploadSession.expiresAt }}</div>
          </div>
        </div>
      </el-tab-pane>

      <!-- ===== Tab 6: 留存调度 ===== -->
      <el-tab-pane label="⑥ 留存调度" name="retention">
        <div class="card">
          <h3 class="card-title">
            <span>留存到期调度 (T-30 预警 / T-7 通知 / T+0 归档 / T+10 销毁)</span>
          </h3>
          <el-row :gutter="16" style="margin-top: 12px;">
            <el-col :span="12">
              <el-button @click="scanRetention" type="primary">
                <el-icon><Refresh /></el-icon>
                手动触发扫描
              </el-button>
            </el-col>
            <el-col :span="12">
              <el-input v-model="beforeDate" placeholder="归档截止日期 (YYYY-MM-DD)" style="width: 200px;" />
              <el-button @click="archiveRetention" type="warning">
                <el-icon><Box /></el-icon>
                归档到冷存储
              </el-button>
            </el-col>
          </el-row>
          <el-alert
            v-if="retentionResult"
            type="info"
            :title="`✓ 归档候选: ${retentionResult.archivedCount} 个`"
            :description="`recEnd < ${retentionResult.beforeDate} 的录像将转 OSS-IA 冷存储`"
            :closable="false"
            style="margin-top: 12px;"
          />
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<style scoped>
.metric-box {
  background: var(--bg-2);
  padding: 12px 16px;
  border-radius: 8px;
  text-align: center;
}
.metric-label { font-size: 11px; color: var(--ink-3); text-transform: uppercase; letter-spacing: 0.5px; }
.metric-value { font-size: 32px; font-weight: 700; margin-top: 4px; }
</style>
