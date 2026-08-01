<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { recordingComplianceApi, recordingApi } from '@/api'
import { ElMessage, ElMessageBox } from 'element-plus'

/**
 * 司法/公证证据保全面板 (v1.2)
 *
 * 触发场景:
 *  - 客户投诉进入司法程序
 *  - 监管现场检查
 *  - 客户理赔纠纷
 *  - 内部审计抽查
 *
 * 流程: submit → notarize → verify
 * 保全期内: 不可删/改/销毁 (冻结)
 */

const recId = ref('')
const requesterId = ref('auditor-001')
const requesterRole = ref('AUDITOR')
const reason = ref('')
const notaryOrg = ref('北京公证处')
const notaryCertNo = ref('GZ-2026-001')

const submitting = ref(false)
const notarizing = ref(false)
const verifying = ref(false)
const listLoading = ref(false)
const preservationList = ref<any[]>([])
const verification = ref<any>(null)
const lastSubmitted = ref<any>(null)

const ROLE_OPTIONS = [
  { value: 'CUSTOMER', label: '客户本人' },
  { value: 'AUDITOR', label: '内部审计' },
  { value: 'REGULATOR', label: '监管' },
  { value: 'COURT', label: '法院' }
]

const PRESET_REASONS = [
  '客户投诉进入司法程序',
  '监管现场检查抽检',
  '客户理赔纠纷, 需取证',
  '内部审计抽查本笔业务',
  '其他保单纠纷的关联证据'
]

async function submit() {
  if (!recId.value || !reason.value) {
    ElMessage.warning('请填写录像 ID + 保全原因')
    return
  }
  submitting.value = true
  try {
    lastSubmitted.value = await recordingComplianceApi.preservationSubmit(
      recId.value, requesterId.value, requesterRole.value, reason.value)
    ElMessage.success(`✓ 保全申请已提交: ${lastSubmitted.value.preservationId}`)
    await listPreservations()
  } catch (e: any) {
    ElMessage.error(`提交失败: ${e.message}`)
  } finally {
    submitting.value = false
  }
}

async function notarize() {
  if (!lastSubmitted.value) {
    ElMessage.warning('请先提交保全申请')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认公证 ${notaryOrg.value} 介入, 公证书号 ${notaryCertNo.value}?`,
      '公证介入', { type: 'warning' }
    )
  } catch { return }
  notarizing.value = true
  try {
    const result = await recordingComplianceApi.preservationNotarize(
      lastSubmitted.value.preservationId, notaryOrg.value, notaryCertNo.value)
    lastSubmitted.value = result
    ElMessage.success(`✓ 公证完成: 5 年冻结`)
    await listPreservations()
  } catch (e: any) {
    ElMessage.error(`公证失败: ${e.message}`)
  } finally {
    notarizing.value = false
  }
}

async function verify(preservationId: string) {
  verifying.value = true
  try {
    verification.value = await recordingComplianceApi.preservationVerify(preservationId)
    if (verification.value.valid) {
      ElMessage.success('✓ 保全完整, 验证通过')
    } else {
      ElMessage.warning(`! 保全异常: ${verification.value.message}`)
    }
  } catch (e: any) {
    ElMessage.error(`验证失败: ${e.message}`)
  } finally {
    verifying.value = false
  }
}

async function listPreservations() {
  if (!recId.value) return
  listLoading.value = true
  try {
    preservationList.value = await recordingComplianceApi.preservationList(recId.value)
  } catch (e: any) {
    preservationList.value = []
  } finally {
    listLoading.value = false
  }
}

function pickReason(r: string) {
  reason.value = r
}

const statusColor: Record<string, string> = {
  SUBMITTED: 'var(--accent)',
  NOTARIZED: 'var(--green)',
  REJECTED: 'var(--accent-2)',
  EXPIRED: 'var(--ink-3)'
}
const statusLabel: Record<string, string> = {
  SUBMITTED: '已申请',
  NOTARIZED: '已公证 (5 年冻结)',
  REJECTED: '已拒绝',
  EXPIRED: '已过期'
}

const shortHash = (h: string | undefined) =>
  h ? h.substring(0, 16) + '...' : '-'
</script>

<template>
  <div>
    <!-- 顶部：提交申请 -->
    <el-row :gutter="16">
      <el-col :span="14">
        <div class="card">
          <h3 class="card-title">证据保全申请</h3>
          <el-form label-width="120px">
            <el-form-item label="录像 ID">
              <el-input v-model="recId" placeholder="REC-XXX (提交后自动加载该录像的保全列表)" />
            </el-form-item>
            <el-form-item label="申请人 ID">
              <el-input v-model="requesterId" />
            </el-form-item>
            <el-form-item label="申请人角色">
              <el-select v-model="requesterRole" style="width: 100%;">
                <el-option v-for="o in ROLE_OPTIONS" :key="o.value" :value="o.value" :label="o.label" />
              </el-select>
            </el-form-item>
            <el-form-item label="保全原因">
              <el-input v-model="reason" type="textarea" :rows="3" />
              <div style="margin-top: 4px;">
                <el-tag
                  v-for="r in PRESET_REASONS"
                  :key="r"
                  size="small"
                  style="margin-right: 4px; cursor: pointer;"
                  @click="pickReason(r)"
                >
                  {{ r }}
                </el-tag>
              </div>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="submitting" @click="submit" size="large">
                <el-icon><Lock /></el-icon>
                提交保全申请
              </el-button>
              <el-button :loading="notarizing" @click="notarize" type="success" :disabled="!lastSubmitted || lastSubmitted.status === 'NOTARIZED'">
                <el-icon><Postcard /></el-icon>
                公证处介入
              </el-button>
            </el-form-item>
          </el-form>

          <!-- 公证配置 -->
          <el-collapse>
            <el-collapse-item title="公证配置" name="config">
              <el-row :gutter="16">
                <el-col :span="12">
                  <el-form-item label="公证机构">
                    <el-input v-model="notaryOrg" />
                  </el-form-item>
                </el-col>
                <el-col :span="12">
                  <el-form-item label="公证书号">
                    <el-input v-model="notaryCertNo" />
                  </el-form-item>
                </el-col>
              </el-row>
            </el-collapse-item>
          </el-collapse>
        </div>
      </el-col>

      <el-col :span="10">
        <!-- 最近保全 -->
        <div v-if="lastSubmitted" class="card">
          <h3 class="card-title">最近保全记录</h3>
          <el-descriptions :column="1" border size="small">
            <el-descriptions-item label="保全 ID">
              <span class="mono" style="font-size: 11px;">{{ lastSubmitted.preservationId }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="录像">
              <span class="mono">{{ lastSubmitted.recId }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="申请人">
              {{ lastSubmitted.requesterId }} ({{ lastSubmitted.requesterRole }})
            </el-descriptions-item>
            <el-descriptions-item label="原因">
              {{ lastSubmitted.reason }}
            </el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :style="{ color: statusColor[lastSubmitted.status] }" size="small">
                {{ statusLabel[lastSubmitted.status] }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="保全 Hash">
              <span class="mono" style="font-size: 10px; word-break: break-all;">
                {{ shortHash(lastSubmitted.preservationHash) }}
              </span>
            </el-descriptions-item>
            <el-descriptions-item v-if="lastSubmitted.notaryOrg" label="公证机构">
              {{ lastSubmitted.notaryOrg }} (证书号: {{ lastSubmitted.notaryCertNo }})
            </el-descriptions-item>
            <el-descriptions-item v-if="lastSubmitted.preservedAt" label="保全时间">
              {{ lastSubmitted.preservedAt }}
            </el-descriptions-item>
            <el-descriptions-item v-if="lastSubmitted.expiresAt" label="有效期至">
              <span style="color: var(--accent); font-weight: 600;">{{ lastSubmitted.expiresAt }} (5 年)</span>
            </el-descriptions-item>
            <el-descriptions-item>
              <el-button size="small" :loading="verifying" @click="verify(lastSubmitted.preservationId)">
                <el-icon><Check /></el-icon>
                验证完整性
              </el-button>
            </el-descriptions-item>
          </el-descriptions>
        </div>

        <!-- 验证结果 -->
        <div v-if="verification" class="card">
          <h3 class="card-title">完整性验证</h3>
          <el-alert
            :type="verification.valid ? 'success' : 'error'"
            :title="verification.valid ? '✓ 保全有效' : '✗ 保全异常'"
            :description="verification.message"
            :closable="false"
          />
        </div>
      </el-col>
    </el-row>

    <!-- 该录像的保全列表 -->
    <div class="card" style="margin-top: 16px;">
      <h3 class="card-title">
        <span>录像 {{ recId || '?' }} 的保全记录</span>
        <span class="actions">
          <el-button size="small" :loading="listLoading" :disabled="!recId" @click="listPreservations">
            <el-icon><Refresh /></el-icon>
          </el-button>
        </span>
      </h3>
      <el-table :data="preservationList" stripe>
        <el-table-column prop="preservationId" label="保全 ID" width="200">
          <template #default="{ row }">
            <span class="mono" style="font-size: 11px;">{{ row.preservationId }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="requesterId" label="申请人" width="100" />
        <el-table-column prop="requesterRole" label="角色" width="100" />
        <el-table-column prop="reason" label="原因" />
        <el-table-column prop="status" label="状态" width="120">
          <template #default="{ row }">
            <el-tag :style="{ color: statusColor[row.status] }" size="small">
              {{ statusLabel[row.status] }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="notaryOrg" label="公证机构" width="120" />
        <el-table-column prop="preservedAt" label="保全时间" width="180" />
        <el-table-column label="操作" width="100">
          <template #default="{ row }">
            <el-button size="small" @click="verify(row.preservationId)">验证</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 流程图 -->
    <div class="card">
      <h3 class="card-title">证据保全流程</h3>
      <div class="flow">
        <div class="step">
          <div class="step-circle" style="background: var(--accent); color: #fff;">1</div>
          <div class="step-label">submit 申请</div>
          <div class="step-desc">SUBMITTED 状态</div>
        </div>
        <div class="arrow">→</div>
        <div class="step">
          <div class="step-circle" style="background: var(--blue); color: #fff;">2</div>
          <div class="step-label">notarize 公证</div>
          <div class="step-desc">NOTARIZED 5 年冻结</div>
        </div>
        <div class="arrow">→</div>
        <div class="step">
          <div class="step-circle" style="background: var(--green); color: #fff;">3</div>
          <div class="step-label">verify 验证</div>
          <div class="step-desc">司法 / 监管调用</div>
        </div>
        <div class="arrow">→</div>
        <div class="step">
          <div class="step-circle" style="background: var(--primary); color: #fff;">4</div>
          <div class="step-label">归档入卷</div>
          <div class="step-desc">法院 / 监管存证</div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.flow { display: flex; align-items: center; justify-content: center; padding: 20px 0; }
.step { display: flex; flex-direction: column; align-items: center; }
.step-circle { width: 48px; height: 48px; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-weight: 700; font-size: 18px; }
.step-label { margin-top: 8px; font-size: 13px; font-weight: 600; }
.step-desc { font-size: 11px; color: var(--ink-3); margin-top: 2px; }
.arrow { font-size: 24px; color: var(--ink-3); margin: 0 16px; }
</style>
