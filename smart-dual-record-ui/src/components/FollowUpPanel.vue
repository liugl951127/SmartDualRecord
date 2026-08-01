<script setup lang="ts">
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

/**
 * 犹豫期智能回访面板 (v1.2)
 *
 * 监管要求: 15 天犹豫期内 3 次主动触达
 *  - D+1  保单摘要 + 答疑入口
 *  - D+7  疑问询问 + 客户调研
 *  - D+14 到期提醒 + 最后答疑
 *
 * 数据来源: tb_event 表的 SCHEDULED_FOLLOW_UP + FOLLOW_UP_EXECUTED + CUSTOMER_WANTS_TO_CANCEL
 *
 * 演示模式: 内存中模拟一笔业务走完 15 天犹豫期的 3 次回访时间线
 */

interface FollowUpEvent {
  type: 'SCHEDULED' | 'EXECUTED' | 'CANCEL_REQUEST' | 'CANCEL_HANDLED'
  phase: 'D+1' | 'D+7' | 'D+14' | null
  at: string          // 时间偏移 / 实际时间
  message: string
  channel: 'SMS' | 'WECHAT' | 'APP_PUSH' | 'CUSTOMER_REPLY' | 'AGENT'
  operatorId?: string
}

const businessId = ref('BNK-20260801-0001')
const businessStatus = ref<'in_progress' | 'completed' | 'expired'>('in_progress')
const customerMobile = ref('138****8000')  // 脱敏
const customerId = ref('cust-001')

// 当前时间
const now = ref(new Date('2026-08-01T10:00:00'))
// 用 setInterval 推进 1 秒 = 模拟 1 小时 (演示用, 3600x 加速)
const accelFactor = ref(3600)
const ticking = ref(false)
let timer: any = null

// 业务时间线
const createdAt = ref(new Date('2026-07-20T10:00:00'))
const signedAt = ref(new Date('2026-07-20T11:00:00'))
const retentionUntil = computed(() => {
  const d = new Date(signedAt.value)
  d.setDate(d.getDate() + 15)
  return d
})

// 3 次回访模板
const TEMPLATES: Array<{
  phase: 'D+1' | 'D+7' | 'D+14'
  dayOffset: number
  title: string
  content: string
  channel: 'SMS' | 'WECHAT'
  icon: string
}> = [
  {
    phase: 'D+1',
    dayOffset: 1,
    title: '保单摘要推送',
    content: `您昨日购买的保险产品已进入 15 天犹豫期, 摘要已推送到您 APP, 有任何疑问可一键联系您的理财经理或我行客服。`,
    channel: 'WECHAT',
    icon: 'Bell'
  },
  {
    phase: 'D+7',
    dayOffset: 7,
    title: '疑问询问',
    content: `您购买的保险产品已过 7 天, 对条款有任何疑问吗? 若想退保请回复 1, 我行 24h 内联系您。`,
    channel: 'WECHAT',
    icon: 'QuestionFilled'
  },
  {
    phase: 'D+14',
    dayOffset: 14,
    title: '到期提醒',
    content: `您的犹豫期将于明天结束(还剩 1 天), 确认继续持有本保单? 若想退保, 明天 24:00 前可办, 工本费不超过 10 元。`,
    channel: 'SMS',
    icon: 'Clock'
  }
]

const events = ref<FollowUpEvent[]>([])

function pushEvent(e: FollowUpEvent) {
  events.value.unshift(e)
}

function scheduleAll() {
  for (const t of TEMPLATES) {
    const at = new Date(signedAt.value)
    at.setDate(at.getDate() + t.dayOffset)
    pushEvent({
      type: 'SCHEDULED',
      phase: t.phase,
      at: at.toISOString().slice(0, 16).replace('T', ' '),
      message: `${t.title} 已排程 → ${t.channel}`,
      channel: t.channel as any
    })
  }
  ElMessage.success(`✓ 已为业务 ${businessId.value} 排程 3 次回访 (D+1/D+7/D+14)`)
}

function executeDue() {
  let count = 0
  for (const t of TEMPLATES) {
    const at = new Date(signedAt.value)
    at.setDate(at.getDate() + t.dayOffset)
    if (now.value >= at) {
      // 检查是否已执行
      const existed = events.value.find(e => e.phase === t.phase && e.type === 'EXECUTED')
      if (existed) continue
      pushEvent({
        type: 'EXECUTED',
        phase: t.phase,
        at: now.value.toISOString().slice(0, 16).replace('T', ' '),
        message: `已执行: ${t.title} (${t.channel})`,
        channel: t.channel as any
      })
      count++
    }
  }
  if (count === 0) ElMessage.info('当前没有到期的回访任务')
  else ElMessage.success(`✓ 已执行 ${count} 个回访任务`)
}

async function customerWantsToCancel() {
  try {
    await ElMessageBox.confirm(
      '客户回复"想退保", 是否转人工?',
      '客户回访 · 退保申请',
      { confirmButtonText: '转人工', cancelButtonText: '保留', type: 'warning' }
    )
    pushEvent({
      type: 'CANCEL_REQUEST',
      phase: null,
      at: now.value.toISOString().slice(0, 16).replace('T', ' '),
      message: `客户 ${customerMobile.value} 回复"想退保" → 已转人工`,
      channel: 'CUSTOMER_REPLY',
      operatorId: 'system-auto'
    })
    pushEvent({
      type: 'CANCEL_HANDLED',
      phase: null,
      at: now.value.toISOString().slice(0, 16).replace('T', ' '),
      message: '已分配理财经理: 王经理 (24h 内联系客户)',
      channel: 'AGENT',
      operatorId: 'mgr-wang-001'
    })
    ElMessage.success('✓ 退保申请已转人工')
  } catch {
    // 用户取消
  }
}

function reset() {
  events.value = []
  now.value = new Date('2026-08-01T10:00:00')
  ElMessage.info('已重置时间线')
}

function startClock() {
  if (timer) return
  ticking.value = true
  timer = setInterval(() => {
    // 演示模式: 1 秒 = 1 小时 (accelFactor)
    now.value = new Date(now.value.getTime() + 1000 * accelFactor.value)
    // 自动执行到期回访
    for (const t of TEMPLATES) {
      const at = new Date(signedAt.value)
      at.setDate(at.getDate() + t.dayOffset)
      if (now.value >= at) {
        const existed = events.value.find(e => e.phase === t.phase && e.type === 'EXECUTED')
        if (!existed) {
          pushEvent({
            type: 'EXECUTED',
            phase: t.phase,
            at: now.value.toISOString().slice(0, 16).replace('T', ' '),
            message: `已执行: ${t.title} (${t.channel})`,
            channel: t.channel as any
          })
        }
      }
    }
    // 犹豫期结束后自动停
    if (now.value > retentionUntil.value) {
      stopClock()
      businessStatus.value = 'expired'
    }
  }, 1000)
}

function stopClock() {
  if (timer) clearInterval(timer)
  timer = null
  ticking.value = false
}

function speedUp() {
  accelFactor.value *= 10
  ElMessage.info(`加速: 1 秒 = ${accelFactor.value / 3600} 小时`)
}

const totalEvents = computed(() => events.value.length)
const scheduledCount = computed(() => events.value.filter(e => e.type === 'SCHEDULED').length)
const executedCount = computed(() => events.value.filter(e => e.type === 'EXECUTED').length)
const cancelCount = computed(() => events.value.filter(e => e.type === 'CANCEL_REQUEST').length)

const currentPhase = computed(() => {
  const elapsed = (now.value.getTime() - signedAt.value.getTime()) / (1000 * 60 * 60 * 24)
  if (elapsed < 1) return 'D+1 待触发'
  if (elapsed < 7) return 'D+1 已完成'
  if (elapsed < 14) return 'D+7 已完成'
  if (elapsed < 15) return 'D+14 已完成'
  return '犹豫期已结束'
})

onMounted(() => {
  // 默认排程
  scheduleAll()
})

onUnmounted(() => stopClock())
</script>

<template>
  <div>
    <!-- 顶部：业务概况 + 控制 -->
    <div class="card">
      <h3 class="card-title">
        <span>犹豫期智能回访 · 3 次触达 (D+1 / D+7 / D+14)</span>
        <span class="actions">
          <el-tag size="small" :type="businessStatus === 'in_progress' ? 'success' : 'info'">
            {{ currentPhase }}
          </el-tag>
        </span>
      </h3>
      <el-row :gutter="16">
        <el-col :span="6">
          <div class="info-cell">
            <div class="label">业务 ID</div>
            <div class="value mono">{{ businessId }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="info-cell">
            <div class="label">客户 (脱敏)</div>
            <div class="value mono">{{ customerMobile }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="info-cell">
            <div class="label">签字时间</div>
            <div class="value">{{ signedAt.toISOString().slice(0, 16).replace('T', ' ') }}</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="info-cell">
            <div class="label">犹豫期至</div>
            <div class="value">{{ retentionUntil.toISOString().slice(0, 10) }}</div>
          </div>
        </el-col>
      </el-row>

      <el-row :gutter="16" style="margin-top: 12px;">
        <el-col :span="24">
          <div style="display: flex; gap: 8px; flex-wrap: wrap;">
            <el-button @click="scheduleAll" type="primary" :disabled="scheduledCount > 0">
              <el-icon><Plus /></el-icon>
              排程 3 次回访
            </el-button>
            <el-button @click="executeDue" :disabled="scheduledCount === 0">
              <el-icon><VideoPlay /></el-icon>
              立即执行到期任务
            </el-button>
            <el-button @click="customerWantsToCancel" type="warning" :disabled="executedCount === 0">
              <el-icon><Warning /></el-icon>
              客户回复"想退保"
            </el-button>
            <el-button @click="startClock" :disabled="ticking" type="success">
              <el-icon><Clock /></el-icon>
              {{ ticking ? '时钟运行中' : '启动时钟' }}
            </el-button>
            <el-button @click="stopClock" :disabled="!ticking" plain>
              暂停
            </el-button>
            <el-button @click="speedUp" plain>
              ×10 加速
            </el-button>
            <el-button @click="reset" plain>
              <el-icon><Refresh /></el-icon>
              重置
            </el-button>
            <div style="margin-left: auto; font-size: 12px; color: var(--ink-3);">
              演示当前: <span class="mono">{{ now.toISOString().slice(0, 16).replace('T', ' ') }}</span>
            </div>
          </div>
        </el-col>
      </el-row>
    </div>

    <!-- 统计 -->
    <el-row :gutter="16">
      <el-col :span="6"><div class="metric-box"><div class="metric-label">总事件</div><div class="metric-value">{{ totalEvents }}</div></div></el-col>
      <el-col :span="6"><div class="metric-box"><div class="metric-label">已排程</div><div class="metric-value" style="color: var(--blue);">{{ scheduledCount }} / 3</div></div></el-col>
      <el-col :span="6"><div class="metric-box"><div class="metric-label">已执行</div><div class="metric-value" style="color: var(--green);">{{ executedCount }} / 3</div></div></el-col>
      <el-col :span="6"><div class="metric-box"><div class="metric-label">退保转人工</div><div class="metric-value" style="color: var(--accent-2);">{{ cancelCount }}</div></div></el-col>
    </el-row>

    <el-row :gutter="16" style="margin-top: 16px;">
      <!-- 3 次回访模板 -->
      <el-col :span="14">
        <div class="card">
          <h3 class="card-title">3 次回访模板 (合规要求)</h3>
          <el-table :data="TEMPLATES" stripe>
            <el-table-column prop="phase" label="触达点" width="80">
              <template #default="{ row }">
                <el-tag size="small" type="primary">{{ row.phase }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="title" label="主题" width="120" />
            <el-table-column prop="content" label="话术" />
            <el-table-column prop="channel" label="渠道" width="100">
              <template #default="{ row }">
                <el-tag size="small">{{ row.channel }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag v-if="events.find(e => e.phase === row.phase && e.type === 'EXECUTED')" type="success" size="small">已执行</el-tag>
                <el-tag v-else-if="events.find(e => e.phase === row.phase && e.type === 'SCHEDULED')" type="info" size="small">已排程</el-tag>
                <el-tag v-else size="small" type="warning">未排</el-tag>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </el-col>

      <!-- 实时事件流 -->
      <el-col :span="10">
        <div class="card">
          <h3 class="card-title">
            <span>事件流</span>
            <span class="actions">
              <el-tag size="small">{{ events.length }}</el-tag>
            </span>
          </h3>
          <div class="event-list">
            <div v-if="events.length === 0" style="text-align: center; color: var(--ink-3); padding: 16px; font-size: 12px;">
              暂无事件
            </div>
            <div
              v-for="(e, i) in events.slice(0, 30)"
              :key="i"
              class="event-item"
              :class="e.type"
            >
              <div class="event-time">{{ e.at }}</div>
              <div class="event-content">
                <el-tag size="small" :type="e.type === 'EXECUTED' ? 'success' :
                                          e.type === 'CANCEL_REQUEST' ? 'danger' :
                                          e.type === 'CANCEL_HANDLED' ? 'warning' : 'info'">
                  {{ e.phase || e.type }}
                </el-tag>
                <span style="margin-left: 6px;">{{ e.message }}</span>
              </div>
              <el-tag size="small" type="info" plain>{{ e.channel }}</el-tag>
            </div>
          </div>
        </div>
      </el-col>
    </el-row>

    <!-- 流程图 -->
    <div class="card">
      <h3 class="card-title">犹豫期 15 天回访时间线</h3>
      <div class="timeline">
        <div class="timeline-axis">
          <div v-for="i in 16" :key="i" class="timeline-mark" :class="{ today: i === Math.ceil((now.getTime() - signedAt.getTime()) / (1000 * 60 * 60 * 24)) }">
            <div class="day">D+{{ i - 1 }}</div>
          </div>
        </div>
        <div class="timeline-event d1" :class="{ active: executedCount >= 1 }">
          <div class="dot"></div>
          <div class="event-name">D+1<br>摘要推送</div>
        </div>
        <div class="timeline-event d7" :class="{ active: executedCount >= 2 }">
          <div class="dot"></div>
          <div class="event-name">D+7<br>疑问询问</div>
        </div>
        <div class="timeline-event d14" :class="{ active: executedCount >= 3 }">
          <div class="dot"></div>
          <div class="event-name">D+14<br>到期提醒</div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.info-cell { background: var(--bg-2); padding: 10px 14px; border-radius: 6px; }
.info-cell .label { font-size: 10px; color: var(--ink-3); letter-spacing: 0.5px; text-transform: uppercase; margin-bottom: 4px; }
.info-cell .value { font-size: 13px; font-weight: 500; color: var(--ink); }
.metric-box { background: var(--card); border: 1px solid var(--line); padding: 12px; border-radius: 8px; text-align: center; }
.metric-label { font-size: 11px; color: var(--ink-3); text-transform: uppercase; letter-spacing: 0.5px; }
.metric-value { font-size: 24px; font-weight: 700; margin-top: 4px; color: var(--ink); }
.event-list { display: flex; flex-direction: column; gap: 6px; max-height: 400px; overflow-y: auto; }
.event-item { display: flex; align-items: center; gap: 8px; padding: 6px 8px; background: var(--bg-2); border-radius: 4px; font-size: 12px; }
.event-time { color: var(--ink-3); font-family: 'JetBrains Mono', monospace; font-size: 10px; min-width: 110px; }
.event-content { flex: 1; }
.event-item.CANCEL_REQUEST { background: rgba(193, 69, 58, 0.06); border-left: 3px solid var(--accent-2); }
.event-item.CANCEL_HANDLED { background: rgba(184, 134, 11, 0.06); border-left: 3px solid var(--accent); }
.timeline { position: relative; padding: 40px 20px 60px; }
.timeline-axis { display: flex; justify-content: space-between; }
.timeline-mark { flex: 1; text-align: center; font-size: 10px; color: var(--ink-3); }
.timeline-mark .day { padding: 4px 6px; border-radius: 4px; }
.timeline-mark.today .day { background: var(--accent); color: #fff; font-weight: 700; }
.timeline-event { position: absolute; display: flex; flex-direction: column; align-items: center; }
.timeline-event .dot { width: 16px; height: 16px; border-radius: 50%; background: var(--line); border: 2px solid #fff; box-shadow: 0 0 0 1px var(--line); }
.timeline-event.active .dot { background: var(--green); box-shadow: 0 0 0 2px var(--green); }
.timeline-event .event-name { font-size: 11px; margin-top: 4px; color: var(--ink-3); text-align: center; }
.timeline-event.active .event-name { color: var(--green); font-weight: 600; }
.timeline-event.d1 { left: 6.6%; top: 12px; }
.timeline-event.d7 { left: 46.6%; top: 12px; }
.timeline-event.d14 { left: 93.3%; top: 12px; }
</style>
