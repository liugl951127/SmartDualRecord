<script setup lang="ts">
import { ref, onMounted } from 'vue'
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

const activeTab = ref('workbench')
const backendOnline = ref(false)
const transitions = ref<Record<string, string[]>>({})

onMounted(async () => {
  try {
    await healthApi.check()
    backendOnline.value = true
    transitions.value = await stateMachineApi.transitions()
  } catch (e) {
    backendOnline.value = false
  }
})

const tabs = [
  { name: 'workbench', label: '录制工作台', icon: 'Microphone' },
  { name: 'create', label: '业务创建', icon: 'Plus' },
  { name: 'risk', label: '风险评估', icon: 'DataLine' },
  { name: 'compliance', label: '录像合规', icon: 'VideoCamera' },
  { name: 'followup', label: '犹豫期回访', icon: 'Bell' },
  { name: 'preservation', label: '证据保全', icon: 'Lock' },
  { name: 'overview', label: '全景查询', icon: 'DataAnalysis' },
  { name: 'scripts', label: '话术管理', icon: 'Document' },
  { name: 'config', label: '话术配置', icon: 'Tools' },
  { name: 'client', label: '客户进线 (H5)', icon: 'UserFilled' },
  { name: 'offline', label: '线下双录 (PC)', icon: 'Bank' },
  { name: 'videoai', label: '视频AI检测', icon: 'Cpu' },
  { name: 'filepush', label: '文件推送 (PC坐席)', icon: 'Share' },
  { name: 'advisor', label: '理财经理 (PC)', icon: 'Service' },
  { name: 'state', label: '状态机', icon: 'Connection' }
]
</script>

<template>
  <div class="app-layout">
    <!-- 顶栏 -->
    <header class="app-header">
      <div>
        <h1>智能双录工作台</h1>
        <div class="subtitle">SMART DUAL RECORD WORKBENCH · 8 节点状态机 + Saga + AI 实时质检</div>
      </div>
      <div class="badges">
        <span class="badge" :class="{ offline: !backendOnline }">
          <span class="dot"></span>
          {{ backendOnline ? '后端已连接' : '后端离线' }}
        </span>
        <span class="badge">v1.2.0</span>
        <span class="badge">Spring Boot 3 + Vue 3</span>
      </div>
    </header>

    <!-- 主内容 -->
    <main class="app-main">
      <el-tabs v-model="activeTab" type="border-card">
        <el-tab-pane
          v-for="t in tabs"
          :key="t.name"
          :name="t.name"
        >
          <template #label>
            <span style="display: inline-flex; align-items: center; gap: 6px;">
              <el-icon><component :is="t.icon" /></el-icon>
              {{ t.label }}
            </span>
          </template>

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
        </el-tab-pane>
      </el-tabs>
    </main>

    <footer class="app-footer">
      双录一体化中台 · v1.0 · 2026 · 一个中台 · 两条跑道
    </footer>
  </div>
</template>
