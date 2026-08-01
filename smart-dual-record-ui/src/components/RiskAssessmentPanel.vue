<script setup lang="ts">
import { ref, computed, reactive, onMounted } from 'vue'
import { riskApi } from '@/api'
import { ElMessage } from 'element-plus'

/**
 * 风险评估问卷组件
 *
 * 9 维评估因子 (合规要求):
 *  1. 流动性需求
 *  2. 到期时限
 *  3. 杠杆承受
 *  4. 结构复杂性
 *  5. 最低金额
 *  6. 投资方向
 *  7. 募集方式
 *  8. 发行人信用
 *  9. 同类业绩
 *
 * 评分规则:
 *  - 保守 → 10 分
 *  - 稳健 → 30 分
 *  - 平衡 → 50 分
 *  - 成长 → 70 分
 *  - 激进 → 90 分
 *  总分 = Σ (维度分数 × 权重) / 100
 *  等级: <20 C1 / <40 C2 / <60 C3 / <80 C4 / 80+ C5
 */

const FACTORS = [
  { key: 'liquidity', name: '流动性需求', weight: 15,
    options: [
      { value: '保守', score: 10, desc: '需要 T+0 赎回, 不能有封闭期' },
      { value: '稳健', score: 30, desc: 'T+1 ~ 7 天可接受' },
      { value: '平衡', score: 50, desc: '30 天内可接受' },
      { value: '成长', score: 70, desc: '90 天内可接受' },
      { value: '激进', score: 90, desc: '1 年以上封闭期可接受' }
    ]},
  { key: 'maturity', name: '到期时限', weight: 10,
    options: [
      { value: '保守', score: 10, desc: '< 30 天' },
      { value: '稳健', score: 30, desc: '30 - 90 天' },
      { value: '平衡', score: 50, desc: '90 - 180 天' },
      { value: '成长', score: 70, desc: '180 - 365 天' },
      { value: '激进', score: 90, desc: '> 1 年' }
    ]},
  { key: 'leverage', name: '杠杆承受', weight: 12,
    options: [
      { value: '保守', score: 10, desc: '不接受任何杠杆' },
      { value: '稳健', score: 30, desc: '最多 1:1 杠杆' },
      { value: '平衡', score: 50, desc: '可接受 1:2 杠杆' },
      { value: '成长', score: 70, desc: '可接受 1:5 杠杆' },
      { value: '激进', score: 90, desc: '可接受 1:10+ 高杠杆' }
    ]},
  { key: 'structural_complexity', name: '结构复杂性', weight: 10,
    options: [
      { value: '保守', score: 10, desc: '只看标准产品 (存款/货基)' },
      { value: '稳健', score: 30, desc: '可看普通理财/债基' },
      { value: '平衡', score: 50, desc: '可看混合型/股债混合' },
      { value: '成长', score: 70, desc: '可看衍生品/分级/ETF 杠杆' },
      { value: '激进', score: 90, desc: '可看结构化/对冲基金/PE/VC' }
    ]},
  { key: 'min_amount', name: '最低金额', weight: 6,
    options: [
      { value: '保守', score: 10, desc: '1 元起' },
      { value: '稳健', score: 30, desc: '1000 元起' },
      { value: '平衡', score: 50, desc: '1 万元起' },
      { value: '成长', score: 70, desc: '10 万元起' },
      { value: '激进', score: 90, desc: '100 万元起' }
    ]},
  { key: 'investment_direction', name: '投资方向', weight: 15,
    options: [
      { value: '保守', score: 10, desc: '存款/国债/政策性金融债' },
      { value: '稳健', score: 30, desc: '信用债/高等级企业债' },
      { value: '平衡', score: 50, desc: '股债混合/混合型基金' },
      { value: '成长', score: 70, desc: '股票/股基/行业 ETF' },
      { value: '激进', score: 90, desc: '衍生品/私募/海外资产/数字资产' }
    ]},
  { key: 'offering_method', name: '募集方式', weight: 6,
    options: [
      { value: '保守', score: 10, desc: '仅公募' },
      { value: '稳健', score: 30, desc: '公募为主' },
      { value: '平衡', score: 50, desc: '公募 + 少量私募' },
      { value: '成长', score: 70, desc: '私募 + 资管计划' },
      { value: '激进', score: 90, desc: '私募 + 跨境 + 另类投资' }
    ]},
  { key: 'issuer_credit', name: '发行人信用', weight: 16,
    options: [
      { value: '保守', score: 10, desc: 'AAA / 国有大行' },
      { value: '稳健', score: 30, desc: 'AA+ / 股份制银行' },
      { value: '平衡', score: 50, desc: 'AA / 城商行/优质券商' },
      { value: '成长', score: 70, desc: 'AA- / 优质民企' },
      { value: '激进', score: 90, desc: 'BBB 及以下 / 私募管理人' }
    ]},
  { key: 'historical_performance', name: '同类业绩', weight: 10,
    options: [
      { value: '保守', score: 10, desc: '业绩波动 < 5%' },
      { value: '稳健', score: 30, desc: '波动 5-15%' },
      { value: '平衡', score: 50, desc: '波动 15-30%' },
      { value: '成长', score: 70, desc: '波动 30-50%' },
      { value: '激进', score: 90, desc: '波动 50%+, 高弹性' }
    ]}
]

const customerIdHash = ref('cust-' + Date.now().toString(36).slice(-6))
const answers = reactive<Record<string, string>>({})
const submitting = ref(false)
const latest = ref<any>(null)
const submitted = ref<any>(null)
const matched = ref<{ productLevel: string; result: any } | null>(null)

const totalWeight = FACTORS.reduce((sum, f) => sum + f.weight, 0)

const previewScore = computed(() => {
  let total = 0
  for (const f of FACTORS) {
    const ans = answers[f.key]
    if (!ans) continue
    const opt = f.options.find(o => o.value === ans)
    if (opt) total += opt.score * f.weight / 100
  }
  return Math.round(Math.min(100, total))
})

const previewLevel = computed(() => {
  const s = previewScore.value
  if (s < 20) return 'C1'
  if (s < 40) return 'C2'
  if (s < 60) return 'C3'
  if (s < 80) return 'C4'
  return 'C5'
})

const allAnswered = computed(() => FACTORS.every(f => answers[f.key]))

const levelColor: Record<string, string> = {
  'C1': '#065f46', 'C2': '#1e40af', 'C3': '#92400e',
  'C4': '#9a3412', 'C5': '#991b1b'
}
const levelDesc: Record<string, string> = {
  'C1': '保守型 — 仅适合 R1/P1 产品',
  'C2': '稳健型 — 适合 R1-R2/P1-P2 产品',
  'C3': '平衡型 — 适合 R1-R3/P1-P3 产品',
  'C4': '成长型 — 适合 R1-R4/P1-P4 产品',
  'C5': '激进型 — 适合 R1-R5/P1-P5 产品'
}

async function submit() {
  if (!allAnswered.value) {
    ElMessage.warning('请完成全部 9 维评估')
    return
  }
  submitting.value = true
  try {
    // 调用后端 9 维评分接口
    const result = await riskApi.submit(customerIdHash.value, { ...answers })
    submitted.value = result
    ElMessage.success(`✓ 风险评估已提交: ${result.riskLevel} (${result.overallScore} 分)`)
    await loadLatest()
  } catch (e: any) {
    ElMessage.error(`提交失败: ${e.message}`)
  } finally {
    submitting.value = false
  }
}

async function loadLatest() {
  try {
    latest.value = await riskApi.latest(customerIdHash.value)
  } catch (e) {
    latest.value = null
  }
}

async function checkMatch(productLevel: string) {
  if (!latest.value) {
    ElMessage.warning('请先提交风险评估')
    return
  }
  try {
    const result = await riskApi.match(latest.value.riskLevel, productLevel)
    matched.value = { productLevel, result }
    if (result.matched) {
      ElMessage.success(`✓ 客户 ${latest.value.riskLevel} 可买 ${productLevel} 产品`)
    } else {
      ElMessage.warning(`! 客户 ${latest.value.riskLevel} 不可买 ${productLevel}, 需二次确认`)
    }
  } catch (e: any) {
    ElMessage.error(`匹配检查失败: ${e.message}`)
  }
}

onMounted(() => {
  loadLatest()
})
</script>

<template>
  <div>
    <!-- 顶部：客户 + 当前等级 -->
    <el-row :gutter="16">
      <el-col :span="14">
        <div class="card">
          <h3 class="card-title">
            <span>客户风险评估问卷 · 9 维加权</span>
            <span class="actions">
              <el-tag v-if="latest" size="small" type="success">
                已有评估: {{ latest.riskLevel }}
              </el-tag>
            </span>
          </h3>

          <el-form-item label="客户 ID (脱敏)">
            <el-input v-model="customerIdHash" placeholder="cust-xxx" style="width: 300px;" />
          </el-form-item>

          <!-- 9 维问卷 -->
          <div class="factor-list">
            <div v-for="f in FACTORS" :key="f.key" class="factor-row">
              <div class="factor-head">
                <span class="factor-name">
                  <el-tag size="small" type="info">权重 {{ f.weight }}%</el-tag>
                  {{ f.name }}
                </span>
                <span v-if="answers[f.key]" class="factor-answered" :style="{ color: levelColor[previewLevel] }">
                  已选: {{ answers[f.key] }}
                </span>
              </div>
              <el-radio-group v-model="answers[f.key]" class="factor-options">
                <el-radio-button
                  v-for="opt in f.options"
                  :key="opt.value"
                  :value="opt.value"
                >
                  {{ opt.value }}
                  <el-tooltip :content="opt.desc" placement="top">
                    <el-icon style="margin-left: 2px;"><QuestionFilled /></el-icon>
                  </el-tooltip>
                </el-radio-button>
              </el-radio-group>
            </div>
          </div>

          <div style="margin-top: 20px; display: flex; gap: 12px; align-items: center;">
            <el-button
              type="primary"
              size="large"
              :loading="submitting"
              :disabled="!allAnswered"
              @click="submit"
            >
              <el-icon><Check /></el-icon>
              提交评估 (12 个月有效)
            </el-button>
            <el-button @click="Object.keys(answers).forEach(k => answers[k] = '')" plain>
              <el-icon><Delete /></el-icon>
              清空
            </el-button>
            <div style="margin-left: auto; font-size: 12px; color: var(--ink-3);">
              实时预览:
              <span class="mono" :style="{ fontSize: '20px', fontWeight: 700, color: levelColor[previewLevel] }">
                {{ previewScore }} 分 · {{ previewLevel }}
              </span>
            </div>
          </div>
        </div>
      </el-col>

      <el-col :span="10">
        <!-- 当前客户评估 -->
        <div v-if="latest" class="card">
          <h3 class="card-title">
            <span>客户最新评估</span>
          </h3>
          <el-descriptions :column="1" border>
            <el-descriptions-item label="客户 ID">
              <span class="mono">{{ latest.customerIdHash }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="评估 ID">
              <span class="mono" style="font-size: 11px;">{{ latest.assessmentId }}</span>
            </el-descriptions-item>
            <el-descriptions-item label="风险等级">
              <span class="risk-pill" :class="latest.riskLevel" style="font-size: 14px;">
                {{ latest.riskLevel }}
              </span>
              <span style="margin-left: 8px; font-size: 12px; color: var(--ink-3);">
                {{ levelDesc[latest.riskLevel] }}
              </span>
            </el-descriptions-item>
            <el-descriptions-item label="总分">
              <span class="mono" :style="{ fontSize: '18px', fontWeight: 600, color: levelColor[latest.riskLevel] }">
                {{ latest.overallScore }}
              </span>
            </el-descriptions-item>
            <el-descriptions-item label="评估时间">
              {{ latest.assessedAt }}
            </el-descriptions-item>
            <el-descriptions-item label="有效期至">
              <span :class="{ 'expired': new Date(latest.validUntil) < new Date() }">
                {{ latest.validUntil }}
                <el-tag v-if="new Date(latest.validUntil) < new Date()" type="danger" size="small" style="margin-left: 6px;">
                  已过期, 需重测
                </el-tag>
              </span>
            </el-descriptions-item>
          </el-descriptions>
        </div>

        <!-- 适当性匹配测试 -->
        <div v-if="latest" class="card">
          <h3 class="card-title">
            <span>适当性匹配测试</span>
          </h3>
          <p style="color: var(--ink-2); font-size: 13px; margin: 0 0 12px;">
            客户 {{ latest.riskLevel }} 买 {{ matched?.productLevel || '?' }} 产品
          </p>
          <div style="display: flex; gap: 8px; flex-wrap: wrap;">
            <el-button size="small" @click="checkMatch('R1')">试买 R1</el-button>
            <el-button size="small" @click="checkMatch('R2')">试买 R2</el-button>
            <el-button size="small" @click="checkMatch('R3')">试买 R3</el-button>
            <el-button size="small" @click="checkMatch('R4')" type="warning">试买 R4</el-button>
            <el-button size="small" @click="checkMatch('R5')" type="danger">试买 R5</el-button>
          </div>
          <div v-if="matched" style="margin-top: 12px;">
            <el-alert
              :type="matched.result.matched ? 'success' : 'warning'"
              :title="matched.result.matched ? '✓ 匹配: 可直接销售' : '! 不匹配: 需客户主动申请 + 二次书面确认'"
              :description="matched.result.reason"
              :closable="false"
            />
          </div>
        </div>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.factor-list { display: flex; flex-direction: column; gap: 14px; }
.factor-row {
  padding: 12px 14px;
  background: var(--bg-2);
  border-radius: 8px;
  border-left: 3px solid var(--accent);
}
.factor-head { display: flex; justify-content: space-between; margin-bottom: 8px; }
.factor-name { font-weight: 600; font-size: 13px; }
.factor-answered { font-size: 12px; font-weight: 600; }
.factor-options { display: flex; flex-wrap: wrap; gap: 6px; }
.factor-options :deep(.el-radio-button__inner) { padding: 6px 12px; }
.expired { color: var(--accent-2); }
</style>
