<template>
  <div class="login-page">
    <div class="login-header">
      <h1>欢迎回来</h1>
      <p>{{ roleText }}身份验证</p>
    </div>

    <div class="login-tabs">
      <div :class="['tab', method === 'phone' && 'active']" @click="method = 'phone'">手机号</div>
      <div :class="['tab', method === 'qr' && 'active']" @click="method = 'qr'">扫码</div>
    </div>

    <div v-if="method === 'phone'" class="login-form">
      <van-field
        v-model="phone"
        type="tel"
        label="手机号"
        placeholder="请输入手机号"
        maxlength="11"
        clearable
      />
      <van-field
        v-model="code"
        label="验证码"
        placeholder="6 位验证码"
        maxlength="6"
      >
        <template #button>
          <van-button size="small" type="primary" :disabled="countdown > 0" @click="sendCode">
            {{ countdown > 0 ? `${countdown}s` : '获取验证码' }}
          </van-button>
        </template>
      </van-field>
      <div v-if="role === 'AGENT' || role === 'ADVISOR'">
        <van-field
          v-model="staffNo"
          label="工号"
          placeholder="请输入员工工号"
        />
      </div>
    </div>

    <div v-else class="qr-section">
      <div class="qr-placeholder">
        <div class="qr-icon">📱</div>
        <p>请使用 App 扫描二维码登录</p>
        <p class="qr-tip">PC 端专用</p>
      </div>
    </div>

    <div class="login-actions">
      <van-button block round size="large" type="primary" :loading="loading" @click="onSubmit">
        登录
      </van-button>
      <p class="agreement">
        登录即表示同意 <a>《用户协议》</a> 和 <a>《隐私政策》</a>
      </p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { showToast } from 'vant'
import { useAuthStore, type UserRole } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const role = computed(() => (route.query.role as UserRole) || 'CUSTOMER')
const redirect = computed(() => (route.query.redirect as string) || (role.value === 'CUSTOMER' ? '/h5/home' : '/pc/dashboard'))
const roleText = computed(() => ({
  CUSTOMER: '客户',
  AGENT: '坐席',
  ADVISOR: '理财经理',
  ADMIN: '管理员'
} as Record<UserRole, string>)[role.value])

const method = ref<'phone' | 'qr'>('phone')
const phone = ref('')
const code = ref('')
const staffNo = ref('')
const loading = ref(false)
const countdown = ref(0)
let timer: any = null

function sendCode() {
  if (!/^1\d{10}$/.test(phone.value)) {
    showToast('请输入正确的手机号')
    return
  }
  showToast('验证码已发送 (123456)')
  countdown.value = 60
  timer = setInterval(() => {
    countdown.value--
    if (countdown.value <= 0 && timer) {
      clearInterval(timer)
      timer = null
    }
  }, 1000)
}

async function onSubmit() {
  if (!/^1\d{10}$/.test(phone.value)) {
    showToast('请输入正确的手机号')
    return
  }
  if (code.value !== '123456' && code.value.length < 4) {
    showToast('请输入验证码')
    return
  }
  loading.value = true
  setTimeout(() => {
    // 模拟登录
    const userInfo = {
      CUSTOMER: {
        id: 'cust-' + phone.value,
        name: `客户${phone.value.slice(-4)}`,
        phone: phone.value,
        role: 'CUSTOMER' as UserRole,
        customerIdHash: 'cust-hash-' + phone.value.slice(-4)
      },
      AGENT: {
        id: staffNo.value || 'agent-001',
        name: '坐席' + (staffNo.value || '001'),
        phone: phone.value,
        role: 'AGENT' as UserRole,
        agentId: staffNo.value || 'agent-001'
      },
      ADVISOR: {
        id: staffNo.value || 'advisor-001',
        name: '理财经理' + (staffNo.value || '001'),
        phone: phone.value,
        role: 'ADVISOR' as UserRole,
        advisorId: staffNo.value || 'advisor-001'
      },
      ADMIN: {
        id: 'admin',
        name: '管理员',
        phone: phone.value,
        role: 'ADMIN' as UserRole
      }
    }[role.value]
    auth.login('mock-token-' + Date.now(), userInfo)
    showToast('登录成功')
    setTimeout(() => router.replace(redirect.value), 300)
  }, 600)
}

onUnmounted(() => {
  if (timer) clearInterval(timer)
})
</script>

<style lang="scss" scoped>
.login-page {
  min-height: 100vh;
  background: var(--bg);
  padding: 40px 24px 24px;
}

.login-header {
  margin-bottom: 32px;
  h1 { font-size: 28px; font-weight: 700; margin: 0 0 8px; }
  p { font-size: 14px; color: var(--text-3); margin: 0; }
}

.login-tabs {
  display: flex;
  background: white;
  border-radius: 8px;
  padding: 4px;
  margin-bottom: 24px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.05);
}
.tab {
  flex: 1;
  text-align: center;
  padding: 8px;
  border-radius: 6px;
  font-size: 14px;
  color: var(--text-2);
  cursor: pointer;
  &.active {
    background: var(--primary);
    color: white;
  }
}

.login-form {
  background: white;
  border-radius: 12px;
  padding: 8px 16px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.05);
}

.qr-section {
  background: white;
  border-radius: 12px;
  padding: 60px 24px;
  text-align: center;
  box-shadow: 0 1px 4px rgba(0,0,0,0.05);
}
.qr-placeholder { color: var(--text-3); }
.qr-icon { font-size: 64px; margin-bottom: 12px; }
.qr-tip { font-size: 12px; color: var(--text-3); margin-top: 8px; }

.login-actions {
  margin-top: 32px;
}
.agreement {
  text-align: center;
  font-size: 12px;
  color: var(--text-3);
  margin-top: 16px;
  a { color: var(--accent); }
}
</style>
