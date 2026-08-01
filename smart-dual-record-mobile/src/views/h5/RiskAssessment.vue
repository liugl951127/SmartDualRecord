<template>
  <div class="risk-assessment">
    <div class="page-header">
      <h1 class="page-title">风险评估</h1>
      <p class="page-subtitle">根据您的实际情况，9 个维度了解您的风险承受能力</p>
    </div>

    <div class="progress-bar">
      <div class="progress-fill" :style="{ width: `${(currentIdx + 1) / questions.length * 100}%` }"></div>
      <div class="progress-text">{{ currentIdx + 1 }} / {{ questions.length }}</div>
    </div>

    <div v-if="!finished" class="question-card">
      <h2 class="q-title">问题 {{ currentIdx + 1 }}</h2>
      <p class="q-text">{{ currentQ.text }}</p>
      <div class="options">
        <div
          v-for="(opt, i) in currentQ.options"
          :key="i"
          :class="['option', answers[currentQ.id] === opt.value && 'selected']"
          @click="select(opt.value)"
        >
          <div class="option-radio"></div>
          <div class="option-text">
            <div class="option-label">{{ opt.label }}</div>
            <div v-if="opt.desc" class="option-desc">{{ opt.desc }}</div>
          </div>
        </div>
      </div>
    </div>

    <div v-else class="result-card">
      <div class="result-icon">{{ levelInfo.icon }}</div>
      <h2 class="result-title">您的风险等级</h2>
      <div class="result-level">{{ levelInfo.label }}</div>
      <div class="result-score">综合得分: {{ score }} 分</div>
      <p class="result-desc">{{ levelInfo.desc }}</p>
      <div class="result-actions">
        <van-button block round type="primary" @click="onSubmit">提交评估</van-button>
        <van-button block round plain type="primary" @click="onRetake" style="margin-top: 8px;">重新评估</van-button>
      </div>
    </div>

    <div v-if="!finished" class="footer">
      <van-button
        block
        round
        type="primary"
        :disabled="answers[currentQ.id] === undefined"
        @click="next"
      >
        {{ currentIdx === questions.length - 1 ? '查看结果' : '下一题' }}
      </van-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { showToast } from 'vant'

const router = useRouter()

// 9 维问卷
const questions = [
  {
    id: 'age',
    text: '您的年龄段是？',
    options: [
      { value: 1, label: '18-30 岁', desc: '年轻人, 投资期限长' },
      { value: 2, label: '31-50 岁', desc: '中年, 收入稳定' },
      { value: 3, label: '51-65 岁', desc: '临近退休, 稳健为主' },
      { value: 4, label: '65 岁以上', desc: '以保本为主' }
    ]
  },
  {
    id: 'income',
    text: '您的年收入水平是？',
    options: [
      { value: 1, label: '10 万以下' },
      { value: 2, label: '10-30 万' },
      { value: 3, label: '30-100 万' },
      { value: 4, label: '100 万以上' }
    ]
  },
  {
    id: 'experience',
    text: '您的投资经验是？',
    options: [
      { value: 1, label: '无经验' },
      { value: 2, label: '1-3 年' },
      { value: 3, label: '3-10 年' },
      { value: 4, label: '10 年以上' }
    ]
  },
  {
    id: 'loss_tolerance',
    text: '能承受的最大亏损比例是？',
    options: [
      { value: 1, label: '无法接受亏损', desc: '保本为主' },
      { value: 2, label: '10% 以内' },
      { value: 3, label: '20% 以内' },
      { value: 4, label: '30% 以上', desc: '高风险高收益' }
    ]
  },
  {
    id: 'horizon',
    text: '投资期限偏好？',
    options: [
      { value: 1, label: '3 个月内' },
      { value: 2, label: '3 个月-1 年' },
      { value: 3, label: '1-3 年' },
      { value: 4, label: '3 年以上' }
    ]
  },
  {
    id: 'liquidity',
    text: '资金流动性需求？',
    options: [
      { value: 1, label: '随时需要' },
      { value: 2, label: '偶尔需要' },
      { value: 3, label: '很少动用' },
      { value: 4, label: '闲置资金' }
    ]
  },
  {
    id: 'leverage',
    text: '是否接受杠杆产品？',
    options: [
      { value: 1, label: '不接受' },
      { value: 2, label: '低杠杆 (1:2 以内)' },
      { value: 3, label: '中等杠杆 (1:5)' },
      { value: 4, label: '高杠杆 (1:10+)' }
    ]
  },
  {
    id: 'complexity',
    text: '对复杂产品 (衍生品/PE/海外) 的接受度？',
    options: [
      { value: 1, label: '只接受简单产品' },
      { value: 2, label: '简单 + 少量混合' },
      { value: 3, label: '可接受结构化产品' },
      { value: 4, label: '可接受所有复杂产品' }
    ]
  },
  {
    id: 'target',
    text: '主要投资目标？',
    options: [
      { value: 1, label: '保本保值' },
      { value: 2, label: '跑赢通胀' },
      { value: 3, label: '资产增值' },
      { value: 4, label: '财富最大化' }
    ]
  }
]

const currentIdx = ref(0)
const answers = reactive<Record<string, number>>({})
const finished = ref(false)
const currentQ = computed(() => questions[currentIdx.value])
const score = computed(() => {
  const total = Object.values(answers).reduce((a, b) => a + b, 0)
  return Math.round((total / (questions.length * 4)) * 100)
})

const levelInfo = computed(() => {
  if (score.value < 30) return { label: 'C1 保守型', icon: '🛡️', desc: '适合保本型产品，如存款、国债、R1 理财' }
  if (score.value < 50) return { label: 'C2 稳健型', icon: '🏦', desc: '适合低风险产品，如 R1-R2 理财、债基' }
  if (score.value < 70) return { label: 'C3 平衡型', icon: '📊', desc: '适合中等风险产品，如 R2-R3 理财、混合基金' }
  if (score.value < 85) return { label: 'C4 成长型', icon: '📈', desc: '适合中高风险产品，如 R3-R4 理财、股基' }
  return { label: 'C5 激进型', icon: '🚀', desc: '可投资高风险产品，如 R5 理财、PE、海外' }
})

function select(v: number) {
  answers[currentQ.value.id] = v
}
function next() {
  if (currentIdx.value < questions.length - 1) {
    currentIdx.value++
  } else {
    finished.value = true
  }
}
function onRetake() {
  currentIdx.value = 0
  Object.keys(answers).forEach(k => delete answers[k])
  finished.value = false
}
function onSubmit() {
  showToast('评估已提交')
  setTimeout(() => router.back(), 600)
}
</script>

<style lang="scss" scoped>
.risk-assessment { min-height: 100vh; }

.progress-bar {
  background: var(--primary);
  height: 4px;
  position: relative;
  margin-top: -16px;
}
.progress-fill {
  height: 100%;
  background: var(--accent);
  transition: width 0.3s;
}
.progress-text {
  position: absolute;
  right: 16px;
  top: -28px;
  color: white;
  font-size: 12px;
  font-family: monospace;
}

.question-card {
  background: white;
  margin: 16px;
  padding: 24px 16px;
  border-radius: 12px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.05);
}
.q-title {
  font-size: 13px;
  color: var(--text-3);
  margin: 0 0 8px;
  font-weight: normal;
}
.q-text {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-1);
  margin: 0 0 20px;
  line-height: 1.4;
}
.option {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  margin-bottom: 8px;
  border: 1px solid var(--border);
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
  &:active { background: var(--bg); }
  &.selected {
    border-color: var(--accent);
    background: rgba(184,134,11,0.05);
  }
}
.option-radio {
  width: 20px; height: 20px;
  border: 2px solid var(--border);
  border-radius: 50%;
  flex-shrink: 0;
  position: relative;
  .selected & {
    border-color: var(--accent);
    &::after {
      content: '';
      position: absolute;
      inset: 3px;
      background: var(--accent);
      border-radius: 50%;
    }
  }
}
.option-text { flex: 1; }
.option-label { font-size: 15px; font-weight: 500; }
.option-desc { font-size: 12px; color: var(--text-3); margin-top: 2px; }

.result-card {
  background: white;
  margin: 16px;
  padding: 32px 16px;
  border-radius: 12px;
  text-align: center;
  box-shadow: 0 1px 4px rgba(0,0,0,0.05);
}
.result-icon { font-size: 64px; margin-bottom: 12px; }
.result-title { font-size: 14px; color: var(--text-3); margin: 0; font-weight: normal; }
.result-level { font-size: 28px; font-weight: 700; color: var(--accent); margin: 8px 0; }
.result-score { font-size: 13px; color: var(--text-2); }
.result-desc { font-size: 13px; color: var(--text-2); line-height: 1.6; margin: 16px 0 24px; }

.footer {
  padding: 16px;
  position: fixed;
  bottom: calc(60px + env(safe-area-inset-bottom, 0px)); left: 0; right: 0;
  background: white;
  border-top: 1px solid var(--border);
}
</style>
