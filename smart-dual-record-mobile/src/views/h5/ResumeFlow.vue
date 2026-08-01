<template>
  <div class="resume-flow">
    <div class="resume-banner">
      <div class="banner-icon">🔄</div>
      <h1>跨渠道继续双录</h1>
      <p>线下失败 · 线上继续</p>
    </div>

    <div v-if="!loaded" class="loading">
      <div class="spinner"></div>
      <p>正在查询补录信息...</p>
    </div>

    <div v-else-if="error" class="error-state">
      <div class="error-icon">❌</div>
      <h2>链接无效</h2>
      <p>{{ error }}</p>
    </div>

    <div v-else class="resume-card">
      <div class="card-section">
        <h3>📋 业务信息</h3>
        <ul class="info-list">
          <li><span class="lbl">业务ID</span><span class="val">{{ info.businessId }}</span></li>
          <li><span class="lbl">产品</span><span class="val">{{ info.productName }}</span></li>
          <li><span class="lbl">金额</span><span class="val">¥{{ info.amount.toLocaleString() }}</span></li>
          <li><span class="lbl">起始渠道</span><span class="val">{{ channelLabel(info.startedChannel) }}</span></li>
        </ul>
      </div>

      <div class="card-section failed-node">
        <h3>⚠️ 失败节点</h3>
        <div class="failed-info">
          <div class="fn-label">{{ info.failedAtNode }}</div>
          <div class="fn-reason">原因: {{ reasonLabel(info.failedReason) }}</div>
          <div v-if="info.failedDetail" class="fn-detail">{{ info.failedDetail }}</div>
        </div>
      </div>

      <div class="card-section">
        <h3>📍 继续节点</h3>
        <div class="next-node">
          从第 <span class="nn-num">{{ currentNodeIdx + 1 }}</span> 节点继续
        </div>
      </div>

      <van-button
        block
        round
        size="large"
        type="primary"
        @click="onResume"
      >
        继续双录
      </van-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { recordingApi } from '@/api'

const route = useRoute()
const router = useRouter()
const token = route.params.token as string

const loaded = ref(false)
const error = ref('')
const info = ref<any>({})
const currentNodeIdx = ref(0)

onMounted(async () => {
  try {
    const res: any = await recordingApi.getResumeInfo(token)
    info.value = res
    // 根据 failedAtNode 推算继续位置
    const map: any = {
      '01-IDENTITY': 1, '02-DISCLOSURE': 2, '03-PRODUCT': 3,
      '04-RIGHTS': 4, '05-TRUTH_TELL': 5, '06-AFFIRMATIVE': 6,
      '07-SIGN': 7, '08-FOLLOWUP': 8
    }
    currentNodeIdx.value = (map[res.failedAtNode] || 1) - 1
    loaded.value = true
  } catch (e: any) {
    error.value = e.message || '链接无效或已过期'
    loaded.value = true
  }
})

function channelLabel(c: string) {
  return ({ OFFLINE: '线下柜面', REMOTE_VIDEO: '远程视频', SELF_AI: '数字人', INTERNET_TEXT: '互联网' } as Record<string, string>)[c] || c
}

function reasonLabel(r: string) {
  return ({ FORBIDDEN_PHRASE: '触发禁播词', NO_AFFIRMATIVE: '未明确肯定', BLACK_FRAME: '黑屏超时', FACE_MISSING: '客户离屏', OTHER: '其他' } as Record<string, string>)[r] || r
}

function onResume() {
  router.push(`/h5/record/${info.value.businessId}`)
}
</script>

<style lang="scss" scoped>
.resume-flow { min-height: 100vh; background: var(--bg); }

.resume-banner {
  background: linear-gradient(135deg, #1e2a47 0%, #2c3a5c 100%);
  color: white;
  padding: 48px 24px;
  text-align: center;
}
.banner-icon { font-size: 48px; margin-bottom: 12px; }
.resume-banner h1 { font-size: 22px; margin: 0; font-weight: 600; }
.resume-banner p { font-size: 13px; opacity: 0.85; margin: 8px 0 0; }

.loading, .error-state {
  text-align: center;
  padding: 80px 20px;
  color: var(--text-3);
}
.spinner {
  width: 32px; height: 32px;
  border: 3px solid var(--border);
  border-top-color: var(--accent);
  border-radius: 50%;
  margin: 0 auto 12px;
  animation: spin 1s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

.error-icon { font-size: 48px; margin-bottom: 12px; }

.resume-card {
  background: var(--card);
  margin: 12px;
  border-radius: 12px;
  padding: 16px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.05);
}
.card-section {
  margin-bottom: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--border);
  &:last-of-type { border-bottom: none; }
  h3 { font-size: 15px; margin: 0 0 12px; font-weight: 600; }
}

.info-list { list-style: none; padding: 0; margin: 0; }
.info-list li {
  display: flex; justify-content: space-between;
  padding: 8px 0;
  font-size: 13px;
}
.lbl { color: var(--text-3); }
.val { font-weight: 500; }

.failed-node .failed-info {
  background: rgba(238,10,36,0.05);
  border: 1px solid rgba(238,10,36,0.2);
  border-radius: 8px;
  padding: 12px;
}
.fn-label { font-size: 14px; font-weight: 600; color: var(--danger); }
.fn-reason { font-size: 13px; color: var(--text-2); margin-top: 4px; }
.fn-detail { font-size: 12px; color: var(--text-3); margin-top: 4px; }

.next-node {
  text-align: center;
  padding: 16px;
  font-size: 14px;
  color: var(--text-2);
}
.nn-num { font-size: 24px; font-weight: 700; color: var(--accent); margin: 0 4px; }
</style>
