<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { healthApi, stateMachineApi } from '@/api'
import BusinessCreate from '@/components/BusinessCreate.vue'
import RecordingWorkbench from '@/components/RecordingWorkbench.vue'
import OverviewPanel from '@/components/OverviewPanel.vue'
import ScriptManager from '@/components/ScriptManager.vue'
import ScriptConfigWorkbench from '@/components/ScriptConfigWorkbench.vue'
import ClientPortal from '@/components/ClientPortal.vue'
import OfflineDualRecord from '@/components/OfflineDualRecord.vue'
import VideoAICheck from '@/components/VideoAICheck.vue'
import AgentFilePush from '@/components/AgentFilePush.vue'
import AdvisorPanel from '@/components/AdvisorPanel.vue'
import StateMachineViewer from '@/components/StateMachineViewer.vue'
import RiskAssessmentPanel from '@/components/RiskAssessmentPanel.vue'
import RecordingCompliancePanel from '@/components/RecordingCompliancePanel.vue'
import FollowUpPanel from '@/components/FollowUpPanel.vue'
import EvidencePreservationPanel from '@/components/EvidencePreservationPanel.vue'
import IntegrityPanel from '@/components/IntegrityPanel.vue'

const activeTab = ref('workbench')
const backendOnline = ref(false)
const transitions = ref<Record<string, string[]>>({})
const currentTime = ref('')
const sidebarCollapsed = ref(false)
const searchKey = ref('')
const searchOpen = ref(false)

let timeTimer: any = null

// ============ 导航分组 ============
const navGroups = [
  {
    label: '主流程',
    items: [
      { name: 'workbench', label: '录制工作台', icon: 'Microphone' },
      { name: 'create', label: '业务创建', icon: 'Plus' },
      { name: 'risk', label: '风险评估', icon: 'DataLine' },
      { name: 'compliance', label: '录像合规', icon: 'VideoCamera' }
    ]
  },
  {
    label: '业务支撑',
    items: [
      { name: 'scripts', label: '话术管理', icon: 'Document' },
      { name: 'config', label: '话术配置', icon: 'Tools' },
      { name: 'filepush', label: '文件推送', icon: 'Share' },
      { name: 'advisor', label: '理财经理', icon: 'Service' }
    ]
  },
  {
    label: '数据安全',
    items: [
      { name: 'integrity', label: '防篡改 + 血缘', icon: 'Lock' }
    ]
  },
  {
    label: '全景洞察',
    items: [
      { name: 'overview', label: '全景查询', icon: 'DataAnalysis' },
      { name: 'followup', label: '犹豫期回访', icon: 'Bell' },
      { name: 'preservation', label: '证据保全', icon: 'Lock' },
      { name: 'state', label: '状态机', icon: 'Connection' }
    ]
  },
  {
    label: '客户终端',
    items: [
      { name: 'client', label: '客户进线 (H5)', icon: 'UserFilled' },
      { name: 'offline', label: '线下双录 (PC)', icon: 'Bank' },
      { name: 'videoai', label: '视频AI检测', icon: 'Cpu' }
    ]
  }
]

const allTabs = computed(() => navGroups.flatMap(g => g.items))
const currentTab = computed(() => allTabs.value.find(t => t.name === activeTab.value))

function selectTab(name: string) {
  activeTab.value = name
}

// ============ 快捷键 ============
function onKeydown(e: KeyboardEvent) {
  const target = e.target as HTMLElement
  if (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA') return
  // Cmd/Ctrl + K
  if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
    e.preventDefault()
    searchOpen.value = !searchOpen.value
  }
  // 数字 1-9 切 tab
  if (!e.metaKey && !e.ctrlKey && !e.altKey) {
    if (e.key >= '1' && e.key <= '9') {
      const idx = parseInt(e.key) - 1
      if (allTabs.value[idx]) selectTab(allTabs.value[idx].name)
    }
  }
  // ESC
  if (e.key === 'Escape') {
    searchOpen.value = false
  }
  // [
  if (e.key === '[') {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }
}

// ============ 时间 ============
function updateTime() {
  const now = new Date()
  currentTime.value = now.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

// ============ KPI mock ============
const kpi = ref({
  businesses: 28,
  recordings: 156,
  passRate: 92.5,
  alerts: 7
})

onMounted(async () => {
  try {
    await healthApi.check()
    backendOnline.value = true
    transitions.value = await stateMachineApi.transitions()
  } catch (e) {
    backendOnline.value = false
  }
  updateTime()
  timeTimer = setInterval(updateTime, 1000)
  window.addEventListener('keydown', onKeydown)
})
onUnmounted(() => {
  if (timeTimer) clearInterval(timeTimer)
  window.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <div class="app-layout">
    <!-- ============ 侧边栏 ============ -->
    <aside class="app-sidebar" :class="{ collapsed: sidebarCollapsed }">
      <div class="app-brand">
        <div class="app-brand-logo">🏦</div>
        <div class="app-brand-text" v-if="!sidebarCollapsed">
          <div class="app-brand-name">智能双录</div>
          <div class="app-brand-tag">SMART DUAL RECORD</div>
        </div>
      </div>

      <nav class="app-nav">
        <template v-for="(g, gi) in navGroups" :key="gi">
          <div v-if="!sidebarCollapsed" class="app-nav-section">{{ g.label }}</div>
          <div
            v-for="t in g.items"
            :key="t.name"
            :class="['app-nav-item', activeTab === t.name && 'active']"
            @click="selectTab(t.name)"
            :title="sidebarCollapsed ? t.label : ''"
          >
            <el-icon class="app-nav-icon"><component :is="t.icon" /></el-icon>
            <span v-if="!sidebarCollapsed" class="app-nav-text">{{ t.label }}</span>
          </div>
        </template>
      </nav>

      <div class="app-sidebar-footer">
        <div class="app-stats-card" v-if="!sidebarCollapsed">
          <div class="app-stat-item">
            <span>今日业务</span>
            <span class="app-stat-val mono">{{ kpi.businesses }}</span>
          </div>
          <div class="app-stat-item">
            <span>通过率</span>
            <span class="app-stat-val mono">{{ kpi.passRate }}%</span>
          </div>
          <div class="app-stat-item">
            <span>预警</span>
            <span class="app-stat-val mono" style="color: var(--accent-light)">{{ kpi.alerts }}</span>
          </div>
        </div>
      </div>
    </aside>

    <!-- ============ 主内容区 ============ -->
    <div class="app-main">
      <!-- 顶栏 -->
      <header class="app-header">
        <div class="app-header-title">
          <div class="app-header-title-icon">
            <el-icon><component :is="currentTab?.icon" /></el-icon>
          </div>
          <div>
            {{ currentTab?.label || '智能双录工作台' }}
            <span class="text-sm text-muted" style="margin-left: 8px;">
              / 15 业务模块 · 8 节点状态机
            </span>
          </div>
        </div>

        <div class="app-header-badges">
          <button class="app-header-badge" @click="searchOpen = !searchOpen" title="全局搜索 (Cmd+K)">
            🔍 <span class="mono">⌘K</span>
          </button>
          <span :class="['app-header-badge', backendOnline ? 'online' : 'offline']">
            <span class="app-header-badge-dot" :class="{ pulsing: backendOnline }"></span>
            {{ backendOnline ? '后端已连接' : '后端离线' }}
          </span>
          <span class="app-header-badge accent">v1.2.0</span>
          <span class="app-header-badge">{{ currentTime }}</span>
        </div>
      </header>

      <!-- 内容区 -->
      <main class="app-content">
        <div class="app-tab-content" :key="activeTab">
          <BusinessCreate v-if="activeTab === 'create'" @created="activeTab = 'workbench'" />
          <RecordingWorkbench v-else-if="activeTab === 'workbench'" />
          <RiskAssessmentPanel v-else-if="activeTab === 'risk'" />
          <RecordingCompliancePanel v-else-if="activeTab === 'compliance'" />
          <FollowUpPanel v-else-if="activeTab === 'followup'" />
          <EvidencePreservationPanel v-else-if="activeTab === 'preservation'" />
          <OverviewPanel v-else-if="activeTab === 'overview'" />
          <ScriptManager v-else-if="activeTab === 'scripts'" />
          <ScriptConfigWorkbench v-else-if="activeTab === 'config'" />
          <ClientPortal v-else-if="activeTab === 'client'" />
          <OfflineDualRecord v-else-if="activeTab === 'offline'" />
          <VideoAICheck v-else-if="activeTab === 'videoai'" />
          <AgentFilePush v-else-if="activeTab === 'filepush'" />
          <AdvisorPanel v-else-if="activeTab === 'advisor'" />
          <StateMachineViewer v-else-if="activeTab === 'state'" :transitions="transitions" />
          <IntegrityPanel v-else-if="activeTab === 'integrity'" />
        </div>
      </main>

      <footer class="app-footer">
        双录一体化中台 · v1.0 · 2026 · 一个中台 · 两条跑道 ·
        <span class="text-accent">15 业务模块</span> ·
        <span class="text-accent">8 节点状态机</span> ·
        <span class="text-accent">16 状态</span> ·
        <span class="text-accent">SAGA + AI 实时质检</span>
      </footer>
    </div>

    <!-- 全局搜索 -->
    <transition name="modal">
      <div v-if="searchOpen" class="search-overlay" @click="searchOpen = false">
        <div class="search-modal" @click.stop>
          <div class="search-input-row">
            <el-icon style="font-size: 20px; color: var(--ink-muted)"><Search /></el-icon>
            <input
              v-model="searchKey"
              class="search-field"
              placeholder="输入业务ID/客户/话术... (ESC 关闭)"
              autofocus
            />
            <span class="kbd">ESC</span>
          </div>
          <div class="search-list">
            <div
              v-for="t in allTabs.filter(t => !searchKey || t.label.includes(searchKey))"
              :key="t.name"
              :class="['search-item', activeTab === t.name && 'active']"
              @click="selectTab(t.name); searchOpen = false"
            >
              <el-icon><component :is="t.icon" /></el-icon>
              <span>{{ t.label }}</span>
              <span class="mono text-sm text-muted">{{ t.name }}</span>
            </div>
            <div v-if="!allTabs.length" class="empty-state">没有匹配结果</div>
          </div>
          <div class="search-hint">
            <span><kbd>↑</kbd><kbd>↓</kbd> 选择</span>
            <span><kbd>Enter</kbd> 确认</span>
            <span><kbd>ESC</kbd> 关闭</span>
          </div>
        </div>
      </div>
    </transition>
  </div>
</template>

<style scoped>
.app-layout {
  min-height: 100vh;
  display: flex;
  background:
    radial-gradient(ellipse 1200px 600px at 0% 0%, rgba(192, 133, 82, 0.04) 0%, transparent 50%),
    radial-gradient(ellipse 800px 400px at 100% 100%, rgba(30, 42, 71, 0.03) 0%, transparent 50%),
    var(--bg);
}

/* ============ 侧边栏收缩 ============ */
.app-sidebar.collapsed {
  width: 72px;
}
.app-sidebar.collapsed .app-brand {
  padding: 18px 16px 14px;
  justify-content: center;
}
.app-sidebar.collapsed .app-nav { padding: 12px 8px 16px; }
.app-sidebar.collapsed .app-nav-item {
  justify-content: center;
  padding: 10px 8px;
}

/* ============ 弹窗 ============ */
.search-overlay {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.5);
  backdrop-filter: blur(4px);
  -webkit-backdrop-filter: blur(4px);
  z-index: 200;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding-top: 12vh;
}
.search-modal {
  width: 560px;
  max-width: 92vw;
  background: var(--card);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xl);
  overflow: hidden;
  border: 1px solid var(--line-2);
  animation: search-slide 0.2s ease-out;
}
@keyframes search-slide {
  from { opacity: 0; transform: translateY(-12px) scale(0.98); }
  to { opacity: 1; transform: translateY(0) scale(1); }
}
.search-input-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;
  border-bottom: 1px solid var(--line-2);
}
.search-field {
  flex: 1;
  border: none;
  outline: none;
  font-size: 16px;
  font-family: inherit;
  background: transparent;
  color: var(--ink);
  &::placeholder { color: var(--ink-muted); }
}
.kbd {
  font-size: 10px;
  padding: 2px 6px;
  background: var(--bg-2);
  border: 1px solid var(--line);
  border-radius: 4px;
  color: var(--ink-3);
  font-family: 'JetBrains Mono', monospace;
  font-weight: 600;
}
.search-list {
  max-height: 380px;
  overflow-y: auto;
  padding: 4px;
}
.search-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 16px;
  border-radius: var(--radius);
  cursor: pointer;
  font-size: 13px;
  color: var(--ink);
  transition: background 0.15s;
  &.active, &:hover { background: var(--bg-2); color: var(--primary); }
  &.active { background: linear-gradient(90deg, rgba(192, 133, 82, 0.1), transparent); }
  & > span:nth-child(2) { flex: 1; }
}
.empty-state {
  text-align: center;
  padding: 40px;
  color: var(--ink-muted);
  font-size: 13px;
}
.search-hint {
  display: flex;
  gap: 12px;
  padding: 10px 16px;
  background: var(--bg-2);
  border-top: 1px solid var(--line-2);
  font-size: 11px;
  color: var(--ink-3);
  & kbd { margin-right: 2px; }
}

.modal-enter-active, .modal-leave-active { transition: opacity 0.2s; }
.modal-enter-from, .modal-leave-to { opacity: 0; }
</style>
