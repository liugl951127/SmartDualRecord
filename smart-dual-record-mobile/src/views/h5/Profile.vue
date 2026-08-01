<template>
  <div class="profile">
    <div class="user-banner">
      <div class="avatar">👤</div>
      <div class="user-info">
        <div class="name">{{ auth.user?.name || '游客' }}</div>
        <div class="phone">{{ maskedPhone }}</div>
      </div>
    </div>

    <div class="quick-grid">
      <div class="quick" @click="$router.push('/h5/risk')">
        <div class="qi">📋</div>
        <div class="qn">风险评估</div>
      </div>
      <div class="quick" @click="$router.push('/h5/orders')">
        <div class="qi">📦</div>
        <div class="qn">我的订单</div>
      </div>
      <div class="quick" @click="$router.push('/h5/files')">
        <div class="qi">📄</div>
        <div class="qn">待签文件</div>
      </div>
      <div class="quick" @click="onTransfer">
        <div class="qi">💎</div>
        <div class="qn">理财经理</div>
      </div>
    </div>

    <div class="card section">
      <div class="menu-list">
        <div class="menu-item" @click="onCall('95588')">
          <span class="mi">📞</span>
          <span class="ml">客服热线 95588</span>
          <span class="ma">›</span>
        </div>
        <div class="menu-item" @click="onComplaint">
          <span class="mi">📝</span>
          <span class="ml">投诉与申诉</span>
          <span class="ma">›</span>
        </div>
        <div class="menu-item" @click="onPrivacy">
          <span class="mi">🔒</span>
          <span class="ml">隐私政策</span>
          <span class="ma">›</span>
        </div>
        <div class="menu-item" @click="onAgreement">
          <span class="mi">📄</span>
          <span class="ml">用户协议</span>
          <span class="ma">›</span>
        </div>
        <div class="menu-item" @click="onAbout">
          <span class="mi">ℹ️</span>
          <span class="ml">关于 v1.0.0</span>
          <span class="ma">›</span>
        </div>
      </div>
    </div>

    <div class="logout-btn" @click="onLogout">退出登录</div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { showDialog, showToast } from 'vant'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()

const maskedPhone = computed(() => {
  if (!auth.user?.phone) return '未登录'
  return auth.user.phone.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2')
})

function onCall(num: string) {
  showToast(`正在拨打 ${num}`)
}
function onComplaint() {
  showDialog({ title: '投诉', message: '投诉电话 95588 转 9, 1-3 个工作日处理' })
}
function onPrivacy() { showToast('隐私政策') }
function onAgreement() { showToast('用户协议') }
function onAbout() { showToast('智能双录 v1.0.0') }

function onTransfer() {
  showDialog({
    title: '转接理财经理',
    message: '转接后将由专业理财经理为您提供服务，是否继续？',
    showCancelButton: true
  }).then(() => {
    showToast('正在为您转接...')
  }).catch(() => {})
}

function onLogout() {
  showDialog({ title: '退出登录', message: '确认退出当前账号？', showCancelButton: true })
    .then(() => {
      auth.logout()
      router.replace('/')
    })
    .catch(() => {})
}
</script>

<style lang="scss" scoped>
.profile { min-height: 100vh; }

.user-banner {
  background: linear-gradient(135deg, #1e2a47 0%, #2c3a5c 100%);
  color: white;
  padding: 40px 24px;
  display: flex;
  align-items: center;
  gap: 16px;
}
.avatar {
  width: 64px; height: 64px;
  background: rgba(255,255,255,0.2);
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-size: 32px;
}
.user-info .name { font-size: 18px; font-weight: 600; }
.user-info .phone { font-size: 13px; opacity: 0.7; margin-top: 4px; }

.quick-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  background: white;
  margin: -20px 12px 0;
  border-radius: 12px;
  padding: 16px 8px;
  box-shadow: 0 2px 8px rgba(0,0,0,0.08);
  position: relative;
}
.quick { text-align: center; cursor: pointer; &:active { opacity: 0.6; } }
.qi { font-size: 28px; margin-bottom: 4px; }
.qn { font-size: 11px; color: var(--text-2); }

.card { background: var(--card); border-radius: 12px; padding: 8px 0; margin: 12px; box-shadow: 0 1px 4px rgba(0,0,0,0.05); }
.menu-list { }
.menu-item {
  display: flex;
  align-items: center;
  padding: 14px 16px;
  border-bottom: 1px solid var(--border);
  &:last-child { border-bottom: none; }
  &:active { background: var(--bg); }
}
.mi { font-size: 20px; width: 32px; }
.ml { flex: 1; font-size: 14px; }
.ma { color: var(--text-3); }

.logout-btn {
  margin: 24px 16px;
  text-align: center;
  padding: 14px;
  background: white;
  border-radius: 8px;
  color: var(--danger);
  font-size: 15px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.05);
  &:active { opacity: 0.7; }
}
</style>
