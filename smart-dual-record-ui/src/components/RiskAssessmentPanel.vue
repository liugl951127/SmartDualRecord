<script setup lang="ts">
import { ref, computed, reactive } from 'vue'
import { riskApi } from '@/api'
import { ElMessage } from 'element-plus'
import { DataLine, Aim, InfoFilled, Check, ArrowRight, User } from '@element-plus/icons-vue'

const FACTORS = [
  { key: 'liquidity', name: '流动性需求', weight: 15, icon: '💧',
    options: [
      { value: '保守', score: 10, desc: 'T+0 赎回, 不能有封闭期' },
      { value: '稳健', score: 30, desc: 'T+1 ~ 7 天可接受' },
      { value: '平衡', score: 50, desc: '30 天内可接受' },
      { value: '成长', score: 70, desc: '90 天内可接受' },
      { value: '激进', score: 90, desc: '1 年以上封闭期可接受' }
    ]},
  { key: 'maturity', name: '到期时限', weight: 10, icon: '⏳',
    options: [
      { value: '保守', score: 10, desc: '< 30 天' },
      { value: '稳健', score: 30, desc: '30 - 90 天' },
      { value: '平衡', score: 50, desc: '90 - 180 天' },
      { value: '成长', score: 70, desc: '180 - 365 天' },
      { value: '激进', score: 90, desc: '> 1 年' }
    ]},
  { key: 'leverage', name: '杠杆承受', weight: 12, icon: '⚖️',
    options: [
      { value: '保守', score: 10, desc: '不接受任何杠杆' },
      { value: '稳健', score: 30, desc: '最多 1:1 杠杆' },
      { value: '平衡', score: 50, desc: '可接受 1:2 杠杆' },
      { value: '成长', score: 70, desc: '可接受 1:5 杠杆' },
      { value: '激进', score: 90, desc: '可接受 1:10+ 高杠杆' }
    ]},
  { key: 'structural_complexity', name: '结构复杂性', weight: 10, icon: '🧩',
    options: [
      { value: '保守', score: 10, desc: '只看标准产品 (存款/货基)' },
      { value: '稳健', score: 30, desc: '可看普通理财/债基' },
      { value: '平衡', score: 50, desc: '可看混合型/股债混合' },
      { value: '成长', score: 70, desc: '可看衍生品/分级/ETF 杠杆' },
      { value: '激进', score: 90, desc: '可看结构化/对冲基金/PE/VC' }
    ]},
  { key: 'min_amount', name: '最低金额', weight: 6, icon: '💰',
    options: [
      { value: '保守', score: 10, desc: '1 元起' },
      { value: '稳健', score: 30, desc: '1000 元起' },
      { value: '平衡', score: 50, desc: '1 万元起' },
      { value: '成长', score: 70, desc: '10 万元起' },
      { value: '激进', score: 90, desc: '100 万元起' }
    ]},
  { key: 'investment_direction', name: '投资方向', weight: 15, icon: '🎯',
    options: [
      { value: '保守', score: 10, desc: '存款/国债/政策性金融债' },
      { value: '稳健', score: 30, desc: '信用债/高等级企业债' },
      { value: '平衡', score: 50, desc: '股债混合/混合型基金' },
      { value: '成长', score: 70, desc: '股票/股基/行业 ETF' },
      { value: '激进', score: 90, desc: '衍生品/私募/海外资产' }
    ]},
  { key: 'offering_method', name: '募集方式', weight: 6, icon: '📢',
    options: [
      { value: '保守', score: 10, desc: '仅公募' },
      { value: '稳健', score: 30, desc: '公募 + 银行间' },
      { value: '平衡', score: 50, desc: '可看私募 (合格投资者)' },
      { value: '成长', score: 70, desc: '私募 + 跨境' },
      { value: '激进', score: 90, desc: '私募 + 跨境 + 离岸' }
    ]},
  { key: 'issuer_credit', name: '发行人信用', weight: 12, icon: '🏛️',
    options: [
      { value: '保守', score: 10, desc: '只接受 AAA / 政策性银行' },
      { value: '稳健', score: 30, desc: 'AA+ 及以上' },
      { value: '平衡', score: 50, desc: 'AA 及以上' },
      { value: '成长', score: 70, desc: 'A 及以上' },
      { value: '激进', score: 90, desc: 'BBB 及以下 (含高收益债)' }
    ]},
  { key: 'historical_performance', name: '同类业绩', weight: 14, icon: '📈',
    options: [
      { value: '保守', score: 10, desc: '业绩 < 5%' },
      { value: '稳健', score: 30, desc: '5% - 15%' },
      { value: '平衡', score: 50, desc: '15% - 30%' },
      { value: '成长', score: 70, desc: '30% - 50%' },
      { value: '激进', score: 90, desc: '50%+, 高弹性' }
    ]}
]

const customerIdHash = ref('cust-hash-001')
const answers = reactive<Record<string, string>>({})
const submitting = ref(false)
const result = ref<any>(null)

const completedCount = computed(() => Object.keys(answers).length)
const totalFactors = FACTORS.length
const completion = computed(() => Math.round((completedCount.value / totalFactors) * 100))

const currentScore = computed(() => {
  let weighted = 0
  let weightSum = 0
  for (const f of FACTORS) {
    const ans = answers[f.key]
    if (ans) {
      const opt = f.options.find(o => o.value === ans)
      if (opt) {
        weighted += opt.score * f.weight
        weightSum += f.weight
      }
    }
  }
  return weightSum > 0 ? Math.round(weighted / weightSum) : 0
})

const currentLevel = computed(() => {
  const s = currentScore.value
  if (s < 20) return { code: 'C1', name: '保守型', color: 'success' }
  if (s < 40) return { code: 'C2', name: '稳健型', color: 'info' }
  if (s < 60) return { code: 'C3', name: '平衡型', color: 'warning' }
  if (s < 80) return { code: 'C4', name: '成长型', color: 'warning' }
  return { code: 'C5', name: '激进型', color: 'danger' }
})

const scoreColor = computed(() => {
  const s = currentScore.value
  if (s < 20) return '#10b981'
  if (s < 40) return '#3b82f6'
  if (s < 60) return '#f59e0b'
  if (s < 80) return '#ea580c'
  return '#ef4444'
})

const scorePercent = computed(() => currentScore.value)

function selectOption(key: string, value: string) {
  answers[key] = value
}

async function submit() {
  if (completedCount.value < totalFactors) {
    ElMessage.warning(`还有 ${totalFactors - completedCount.value} 个维度未完成`)
    return
  }
  if (!customerIdHash.value) {
    ElMessage.warning('请输入客户 ID')
    return
  }
  submitting.value = true
  try {
    const res = await riskApi.submit(customerIdHash.value, answers)
    result.value = res
    ElMessage.success(`评估完成: ${res.riskLevel} (${res.overallScore}分)`)
  } catch (e: any) {
    // mock 响应
    result.value = {
      id: 'ra-' + Date.now(),
      customerIdHash: customerIdHash.value,
      riskLevel: currentLevel.value.code,
      overallScore: currentScore.value,
      assessmentId: 'ASSESS-' + Date.now(),
      answers: { ...answers }
    }
    ElMessage.success(`评估完成 (本地): ${currentLevel.value.code}`)
  } finally {
    submitting.value = false
  }
}

function reset() {
  Object.keys(answers).forEach(k => delete answers[k])
  result.value = null
}

function optionSelected(key: string, value: string) {
  return answers[key] === value
}
</script>

<template>
  <div>
    <!-- ============ 顶部: 客户 + 进度 + 实时分数 ============ -->
    <div class="risk-grid mb-16">
      <!-- 客户输入 -->
      <div class="card customer-card">
        <h3 class="card-title"><span>👤 客户信息</span></h3>
        <div class="form-row">
          <label>客户 ID Hash</label>
          <input
            v-model="customerIdHash"
            class="form-input"
            placeholder="cust-hash-xxx"
          />
        </div>
        <div class="form-row">
          <label>完成度</label>
          <div class="progress-row">
            <div class="progress-bar">
              <div class="progress-fill" :style="{ width: completion + '%' }"></div>
            </div>
            <span class="mono font-bold">{{ completion }}%</span>
          </div>
        </div>
        <div class="action-row">
          <button class="btn btn-ghost" @click="reset">重置</button>
          <button class="btn btn-primary" :disabled="submitting || completedCount < totalFactors" @click="submit">
            {{ submitting ? '提交中...' : '提交评估' }}
            <el-icon v-if="!submitting"><ArrowRight /></el-icon>
          </button>
        </div>
      </div>

      <!-- 实时分数 -->
      <div class="card score-card" :style="{ '--score-color': scoreColor }">
        <h3 class="card-title"><span>🎯 实时评分</span></h3>
        <div class="score-display">
          <div class="score-circle">
            <svg viewBox="0 0 100 100" class="score-svg">
              <circle cx="50" cy="50" r="42" fill="none" stroke="var(--line)" stroke-width="6" />
              <circle
                cx="50" cy="50" r="42" fill="none"
                :stroke="scoreColor" stroke-width="6"
                stroke-linecap="round"
                :stroke-dasharray="`${scorePercent * 2.64} 264`"
                transform="rotate(-90 50 50)"
                style="transition: stroke-dasharray 0.5s, stroke 0.5s;"
              />
            </svg>
            <div class="score-text">
              <div class="score-num mono">{{ currentScore }}</div>
              <div class="score-label">分</div>
            </div>
          </div>
          <div class="level-display">
            <div :class="['level-badge', `level-${currentLevel.color}`]">
              {{ currentLevel.code }}
            </div>
            <div class="level-name">{{ currentLevel.name }}</div>
            <div class="level-desc text-sm text-muted">
              <span v-if="currentLevel.code === 'C1'">适合 R1 存款/国债</span>
              <span v-else-if="currentLevel.code === 'C2'">适合 R1-R2 货基/普通理财</span>
              <span v-else-if="currentLevel.code === 'C3'">适合 R2-R3 混合基金</span>
              <span v-else-if="currentLevel.code === 'C4'">适合 R3-R4 股票基金</span>
              <span v-else>适合 R4-R5 衍生品/私募</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- ============ 评估结果 ============ -->
    <div v-if="result" class="card result-card mb-16">
      <h3 class="card-title">
        <span>✅ 评估结果</span>
        <span class="state-badge primary">{{ result.assessmentId }}</span>
      </h3>
      <div class="result-grid">
        <div class="result-item">
          <div class="result-label">客户 ID</div>
          <div class="result-value mono">{{ result.customerIdHash }}</div>
        </div>
        <div class="result-item">
          <div class="result-label">风险等级</div>
          <div class="result-value">
            <span :class="['level-badge', `level-${currentLevel.color}`]">{{ result.riskLevel }}</span>
            <span style="margin-left: 8px;">{{ currentLevel.name }}</span>
          </div>
        </div>
        <div class="result-item">
          <div class="result-label">综合得分</div>
          <div class="result-value mono font-bold" :style="{ color: scoreColor }">{{ result.overallScore }}</div>
        </div>
        <div class="result-item">
          <div class="result-label">评估 ID</div>
          <div class="result-value mono text-sm">{{ result.id }}</div>
        </div>
      </div>
    </div>

    <!-- ============ 9 维评估问卷 ============ -->
    <h3 class="section-title">📋 9 维风险评估因子</h3>
    <div class="factor-grid">
      <div v-for="f in FACTORS" :key="f.key" class="factor-card" :class="answers[f.key] && 'completed'">
        <div class="factor-header">
          <div class="factor-icon">{{ f.icon }}</div>
          <div class="factor-info">
            <div class="factor-name">{{ f.name }}</div>
            <div class="factor-weight text-sm text-muted">权重 {{ f.weight }}%</div>
          </div>
          <div v-if="answers[f.key]" class="factor-check"><el-icon><Check /></el-icon></div>
        </div>
        <div class="option-list">
          <div
            v-for="opt in f.options"
            :key="opt.value"
            :class="['option', optionSelected(f.key, opt.value) && 'selected']"
            @click="selectOption(f.key, opt.value)"
          >
            <div class="option-radio">
              <span v-if="optionSelected(f.key, opt.value)"></span>
            </div>
            <div class="option-body">
              <div class="option-row">
                <span class="option-value">{{ opt.value }}</span>
                <span class="option-score mono">{{ opt.score }}分</span>
              </div>
              <div class="option-desc text-sm text-muted">{{ opt.desc }}</div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.mb-16 { margin-bottom: 16px; }

/* ============ 顶部布局 ============ */
.risk-grid {
  display: grid;
  grid-template-columns: 360px 1fr;
  gap: 16px;
}
@media (max-width: 900px) { .risk-grid { grid-template-columns: 1fr; } }

.customer-card { padding: 24px; }
.score-card { padding: 24px; }

/* ============ 表单 ============ */
.form-row { margin-bottom: 16px; }
.form-row label {
  display: block;
  font-size: 11px;
  color: var(--ink-3);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  font-weight: 600;
  margin-bottom: 6px;
}
.form-input {
  width: 100%;
  padding: 10px 14px;
  background: var(--bg-2);
  border: 1px solid var(--line);
  border-radius: var(--radius);
  font-size: 13px;
  font-family: 'JetBrains Mono', monospace;
  color: var(--ink);
  transition: all 0.2s;
  &:hover { background: white; }
  &:focus {
    outline: none;
    background: white;
    border-color: var(--accent);
    box-shadow: 0 0 0 3px rgba(192, 133, 82, 0.12);
  }
}
.progress-row {
  display: flex;
  align-items: center;
  gap: 8px;
  & > .progress-bar { flex: 1; }
  & > .mono { font-size: 12px; color: var(--ink-2); min-width: 36px; }
}
.action-row { display: flex; gap: 8px; justify-content: flex-end; margin-top: 20px; }

/* ============ 分数环 ============ */
.score-display {
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 8px 0;
}
.score-circle {
  position: relative;
  width: 140px; height: 140px;
  flex-shrink: 0;
}
.score-svg { width: 100%; height: 100%; }
.score-text {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}
.score-num {
  font-size: 36px;
  font-weight: 700;
  color: var(--ink);
  line-height: 1;
  letter-spacing: -1px;
  transition: color 0.3s;
}
.score-label { font-size: 11px; color: var(--ink-3); margin-top: 2px; }

.level-display { flex: 1; }
.level-badge {
  display: inline-block;
  padding: 4px 12px;
  border-radius: 999px;
  font-size: 14px;
  font-weight: 700;
  font-family: 'JetBrains Mono', monospace;
  margin-bottom: 8px;
}
.level-badge.level-success { background: var(--green-light); color: #047857; }
.level-badge.level-info { background: var(--blue-light); color: #1d4ed8; }
.level-badge.level-warning { background: var(--orange-light); color: var(--orange); }
.level-badge.level-danger { background: var(--red-light); color: var(--red); }
.level-name {
  font-size: 18px;
  font-weight: 700;
  color: var(--ink);
  margin-bottom: 4px;
}

/* ============ 按钮 ============ */
.btn { display: inline-flex; align-items: center; gap: 6px; padding: 9px 16px; border: 1px solid transparent; border-radius: var(--radius); font-size: 13px; font-weight: 500; cursor: pointer; transition: all 0.2s; }
.btn-primary {
  background: var(--primary-gradient);
  color: white;
  box-shadow: 0 2px 4px rgba(30, 42, 71, 0.15);
  &:hover:not(:disabled) { box-shadow: var(--shadow-primary); transform: translateY(-1px); }
  &:disabled { opacity: 0.4; cursor: not-allowed; }
}
.btn-ghost {
  background: white;
  color: var(--ink-2);
  border-color: var(--line);
  &:hover { background: var(--bg-2); }
}

/* ============ 结果卡 ============ */
.result-card {
  background: linear-gradient(135deg, var(--bg-accent) 0%, white 100%);
  border-color: var(--line-accent);
  .card-title { color: var(--accent-2); }
}
.result-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-top: 12px;
}
@media (max-width: 800px) { .result-grid { grid-template-columns: 1fr 1fr; } }
.result-item {
  padding: 12px 14px;
  background: rgba(255, 255, 255, 0.6);
  border-radius: var(--radius);
  border: 1px solid var(--line-2);
}
.result-label {
  font-size: 10px;
  color: var(--ink-3);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  font-weight: 600;
  margin-bottom: 4px;
}
.result-value { font-size: 14px; color: var(--ink); display: flex; align-items: center; }

/* ============ 评估因子 ============ */
.section-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--ink-2);
  margin: 24px 0 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--line);
}
.factor-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 16px;
}
@media (max-width: 1000px) { .factor-grid { grid-template-columns: 1fr; } }

.factor-card {
  background: var(--card);
  border: 1px solid var(--line-2);
  border-radius: var(--radius-lg);
  padding: 18px 20px;
  transition: all 0.25s;
  &:hover { box-shadow: var(--shadow); border-color: var(--line); }
  &.completed {
    border-color: var(--line-accent);
    background: linear-gradient(180deg, var(--bg-accent) 0%, white 30%);
  }
}
.factor-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}
.factor-icon {
  width: 40px; height: 40px;
  background: var(--bg-2);
  border-radius: var(--radius);
  display: flex; align-items: center; justify-content: center;
  font-size: 20px;
  flex-shrink: 0;
}
.factor-info { flex: 1; }
.factor-name { font-size: 14px; font-weight: 600; color: var(--ink); }
.factor-weight { margin-top: 2px; }
.factor-check {
  width: 24px; height: 24px;
  background: var(--green);
  color: white;
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-size: 14px;
  font-weight: 700;
}

.option-list { display: flex; flex-direction: column; gap: 4px; }
.option {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 12px;
  background: var(--bg-2);
  border: 1px solid transparent;
  border-radius: var(--radius);
  cursor: pointer;
  transition: all 0.15s;
  &:hover { background: white; border-color: var(--line); }
  &.selected {
    background: white;
    border-color: var(--accent);
    box-shadow: 0 0 0 1px var(--accent) inset, 0 2px 6px rgba(192, 133, 82, 0.1);
    .option-radio { border-color: var(--accent); & > span { background: var(--accent); } }
    .option-value { color: var(--accent-2); }
  }
}
.option-radio {
  width: 16px; height: 16px;
  border: 2px solid var(--line);
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  flex-shrink: 0;
  margin-top: 2px;
  transition: all 0.15s;
  & > span {
    width: 8px; height: 8px;
    border-radius: 50%;
    transition: all 0.15s;
  }
}
.option-body { flex: 1; min-width: 0; }
.option-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 2px;
}
.option-value {
  font-size: 13px;
  font-weight: 600;
  color: var(--ink-2);
  transition: color 0.15s;
}
.option-score {
  font-size: 11px;
  color: var(--ink-3);
  background: white;
  padding: 1px 6px;
  border-radius: 999px;
  border: 1px solid var(--line-2);
}
.option-desc { line-height: 1.5; }
</style>
