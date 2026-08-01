<template>
  <div class="pc-layout">
    <aside class="sidebar">
      <div class="logo">
        <div class="logo-icon">🏦</div>
        <div class="logo-text">智能双录</div>
      </div>
      <nav class="nav">
        <router-link
          v-for="m in menus"
          :key="m.path"
          :to="m.path"
          :class="['nav-item', isActive(m.path) && 'active']"
        >
          <span class="ni">{{ m.icon }}</span>
          <span class="nt">{{ m.label }}</span>
        </router-link>
      </nav>
      <div class="user-area">
        <div class="u-avatar">{{ avatar }}</div>
        <div class="u-info">
          <div class="u-name">{{ auth.user?.name }}</div>
          <div class="u-role">{{ roleLabel }}</div>
        </div>
        <div class="u-logout" @click="onLogout">↪</div>
      </div>
    </aside>

    <main class="main">
      <router-view v-slot="{ Component }">
        <transition name="fade" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </main>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const roleLabel = computed(() => {
  const m: any = { AGENT: '坐席', ADVISOR: '理财经理', ADMIN: '管理员' }
  return m[auth.user?.role || ''] || '客户'
})
const avatar = computed(() => {
  const m: any = { AGENT: '💼', ADVISOR: '💎', ADMIN: '👑', CUSTOMER: '👤' }
  return m[auth.user?.role || ''] || '👤'
})

const menus = computed(() => {
  if (auth.user?.role === 'ADVISOR') {
    return [
      { path: '/pc/dashboard', label: '工作台', icon: '📊' },
      { path: '/pc/bilateral', label: '双边录制', icon: '📞' },
      { path: '/pc/advisor', label: '理财经理', icon: '💎' }
    ]
  }
  return [
    { path: '/pc/dashboard', label: '工作台', icon: '📊' },
    { path: '/pc/customers', label: '客户管理', icon: '👥' },
    { path: '/pc/bilateral', label: '双边录制', icon: '📞' },
    { path: '/pc/filepush', label: '文件推送', icon: '📤' }
  ]
})

function isActive(p: string) {
  if (p === '/pc/dashboard') return route.path === p
  return route.path.startsWith(p)
}

function onLogout() {
  auth.logout()
  router.replace('/')
}
</script>

<style lang="scss" scoped>
.pc-layout {
  display: flex;
  min-height: 100vh;
  background: var(--bg);
}

.sidebar {
  width: 220px;
  background: linear-gradient(180deg, #1e2a47 0%, #2c3a5c 100%);
  color: white;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
}
.logo {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 24px 20px;
  border-bottom: 1px solid rgba(255,255,255,0.1);
}
.logo-icon { font-size: 28px; }
.logo-text { font-size: 16px; font-weight: 600; }

.nav { flex: 1; padding: 12px 0; }
.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 20px;
  color: rgba(255,255,255,0.7);
  text-decoration: none;
  font-size: 14px;
  cursor: pointer;
  border-left: 3px solid transparent;
  &:hover { color: white; background: rgba(255,255,255,0.05); }
  &.active {
    color: white;
    background: rgba(184,134,11,0.15);
    border-left-color: var(--accent);
  }
}
.ni { font-size: 18px; }

.user-area {
  padding: 16px 20px;
  border-top: 1px solid rgba(255,255,255,0.1);
  display: flex;
  align-items: center;
  gap: 10px;
}
.u-avatar {
  width: 36px; height: 36px;
  background: rgba(255,255,255,0.15);
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-size: 18px;
}
.u-info { flex: 1; min-width: 0; }
.u-name { font-size: 13px; font-weight: 500; overflow: hidden; text-overflow: ellipsis; }
.u-role { font-size: 11px; opacity: 0.7; }
.u-logout { color: rgba(255,255,255,0.5); cursor: pointer; font-size: 18px; &:hover { color: white; } }

.main { flex: 1; overflow: auto; }

.fade-enter-active, .fade-leave-active { transition: opacity 0.2s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>
