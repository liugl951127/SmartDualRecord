<template>
  <div class="pc-layout">
    <!-- ============ 侧边栏 ============ -->
    <aside class="sidebar" :class="{ collapsed: sidebarCollapsed }">
      <div class="brand">
        <div class="brand-logo">🏦</div>
        <div v-if="!sidebarCollapsed" class="brand-text">
          <div class="brand-name">智能双录</div>
          <div class="brand-tag">坐席工作台</div>
        </div>
      </div>

      <div class="nav-section" v-if="!sidebarCollapsed">
        <div class="nav-label">主导航</div>
      </div>
      <nav class="nav">
        <router-link
          v-for="(m, i) in mainMenus"
          :key="m.path"
          :to="m.path"
          :class="['nav-item', isActive(m.path) && 'active']"
          :title="m.label"
        >
          <span class="ni">{{ m.icon }}</span>
          <span v-if="!sidebarCollapsed" class="nt">{{ m.label }}</span>
          <span v-if="!sidebarCollapsed && m.shortcut" class="kbd">{{ m.shortcut }}</span>
          <span v-if="m.badge" :class="['nav-badge', m.badgeCls]">{{ m.badge }}</span>
        </router-link>
      </nav>

      <div class="nav-section" v-if="!sidebarCollapsed">
        <div class="nav-label">工具</div>
      </div>
      <nav class="nav" v-if="!sidebarCollapsed">
        <a class="nav-item" @click="showShortcuts = true">
          <span class="ni">⌨️</span>
          <span class="nt">快捷键</span>
          <span class="kbd">?</span>
        </a>
        <a class="nav-item" @click="showSettings = true">
          <span class="ni">⚙️</span>
          <span class="nt">设置</span>
        </a>
      </nav>

      <div class="sidebar-footer">
        <button class="collapse-btn" @click="sidebarCollapsed = !sidebarCollapsed">
          <span v-if="sidebarCollapsed">»</span>
          <span v-else>«</span>
          <span v-if="!sidebarCollapsed" class="ml-1">收起</span>
        </button>
        <div class="user-card" v-if="!sidebarCollapsed">
          <div class="u-avatar">{{ avatar }}</div>
          <div class="u-info">
            <div class="u-name">{{ auth.user?.name }}</div>
            <div class="u-role">
              <span class="status-dot" :class="onlineStatus.cls"></span>
              {{ roleLabel }} · {{ onlineStatus.label }}
            </div>
          </div>
          <button class="u-action" @click="onLogout" title="退出">↪</button>
        </div>
      </div>
    </aside>

    <!-- ============ 主内容区 ============ -->
    <div class="main-wrap">
      <!-- 顶栏 -->
      <header class="topbar">
        <div class="topbar-left">
          <div class="breadcrumb">
            <span class="bc-item">{{ currentMenu?.label || '工作台' }}</span>
          </div>
        </div>

        <div class="topbar-center">
          <div class="search-box" @click="searchOpen = true">
            <span class="search-icon">🔍</span>
            <input
              v-model="searchKey"
              class="search-input"
              placeholder="搜索客户/业务ID/订单号... (Ctrl+K)"
              @keyup.enter="onSearch"
            />
            <span class="kbd-hint">⌘K</span>
          </div>
        </div>

        <div class="topbar-right">
          <div class="status-pill" :class="onlineStatus.cls">
            <span class="status-dot"></span>
            {{ onlineStatus.label }}
          </div>

          <button class="icon-btn" @click="showShortcuts = true" title="快捷键 (?)">
            ⌨️
          </button>

          <button class="icon-btn notif" @click="notifOpen = !notifOpen" title="通知">
            🔔
            <span v-if="unreadCount > 0" class="notif-badge">{{ unreadCount }}</span>
          </button>

          <div class="user-chip">
            <div class="u-avatar small">{{ avatar }}</div>
            <span class="u-name-short">{{ auth.user?.name }}</span>
          </div>
        </div>

        <!-- 通知下拉 -->
        <transition name="dropdown">
          <div v-if="notifOpen" class="notif-panel" @click.stop>
            <div class="notif-header">
              <h4>通知中心</h4>
              <button class="btn btn-text btn-sm" @click="markAllRead">全部已读</button>
            </div>
            <div class="notif-tabs">
              <div
                v-for="t in notifTabs"
                :key="t.value"
                :class="['ntab', notifTab === t.value && 'active']"
                @click="notifTab = t.value"
              >
                {{ t.label }}
                <span v-if="t.count > 0" class="ntab-count">{{ t.count }}</span>
              </div>
            </div>
            <div class="notif-list">
              <div
                v-for="n in filteredNotifs"
                :key="n.id"
                :class="['notif', `n-${n.type}`, !n.read && 'unread']"
                @click="onNotifClick(n)"
              >
                <div class="notif-icon">{{ n.icon }}</div>
                <div class="notif-body">
                  <div class="notif-title">{{ n.title }}</div>
                  <div class="notif-desc">{{ n.desc }}</div>
                  <div class="notif-time">{{ n.time }}</div>
                </div>
                <div v-if="!n.read" class="notif-dot"></div>
              </div>
              <div v-if="!filteredNotifs.length" class="empty">
                <div class="empty-icon">📭</div>
                <p>暂无通知</p>
              </div>
            </div>
          </div>
        </transition>
      </header>

      <!-- 路由内容 -->
      <main class="content" @click="notifOpen = false">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>

      <!-- 状态栏 -->
      <footer class="statusbar">
        <div class="sb-left">
          <span class="sb-item">
            <span class="sb-dot" :class="onlineStatus.cls"></span>
            {{ onlineStatus.label }}
          </span>
          <span class="sb-item">📞 客服 95588</span>
          <span class="sb-item">📦 业务: {{ totalActive }}</span>
        </div>
        <div class="sb-right">
          <span class="sb-item">{{ currentTime }}</span>
          <span class="sb-item">v1.5.0</span>
        </div>
      </footer>
    </div>

    <!-- 快捷键面板 -->
    <transition name="modal">
      <div v-if="showShortcuts" class="modal-overlay" @click="showShortcuts = false">
        <div class="modal" @click.stop>
          <div class="modal-header">
            <h3>⌨️ 快捷键</h3>
            <button class="modal-close" @click="showShortcuts = false">×</button>
          </div>
          <div class="modal-body">
            <div class="shortcut-group">
              <div class="sg-title">导航</div>
              <div class="sg-list">
                <div class="sg-item" v-for="s in shortcuts.nav" :key="s.label">
                  <span class="sg-label">{{ s.label }}</span>
                  <div>
                    <kbd v-for="k in s.keys" :key="k" class="kbd">{{ k }}</kbd>
                  </div>
                </div>
              </div>
            </div>
            <div class="shortcut-group">
              <div class="sg-title">操作</div>
              <div class="sg-list">
                <div class="sg-item" v-for="s in shortcuts.action" :key="s.label">
                  <span class="sg-label">{{ s.label }}</span>
                  <div>
                    <kbd v-for="k in s.keys" :key="k" class="kbd">{{ k }}</kbd>
                  </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </transition>

    <!-- 全局搜索 -->
    <transition name="modal">
      <div v-if="searchOpen" class="modal-overlay" @click="searchOpen = false">
        <div class="search-modal" @click.stop>
          <div class="search-modal-input">
            <span class="search-icon">🔍</span>
            <input
              v-model="searchKey"
              class="search-modal-field"
              placeholder="输入客户姓名 / 业务ID / 手机号 / 订单号"
              autofocus
              @keyup.enter="onSearch"
              @keyup.escape="searchOpen = false"
            />
            <span class="kbd">ESC</span>
          </div>
          <div class="search-modal-results">
            <div v-if="!searchKey" class="search-tips">
              <div class="tip-title">💡 搜索提示</div>
              <div class="tip-item">支持搜索客户、业务ID、订单号、手机号</div>
              <div class="tip-item">按 Enter 跳转到第一个结果</div>
            </div>
            <div v-else-if="!searchResults.length" class="empty">
              <div class="empty-icon">🔍</div>
              <p>没有匹配结果</p>
            </div>
            <div v-else>
              <div
                v-for="r in searchResults"
                :key="r.id"
                :class="['result-item', r.type]"
                @click="onResultClick(r)"
              >
                <span class="r-icon">{{ r.icon }}</span>
                <div class="r-info">
                  <div class="r-title">{{ r.title }}</div>
                  <div class="r-desc">{{ r.desc }}</div>
                </div>
                <span class="r-type">{{ r.typeLabel }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </transition>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

// ============ 状态 ============
const sidebarCollapsed = ref(false)
const searchOpen = ref(false)
const searchKey = ref('')
const notifOpen = ref(false)
const notifTab = ref('all')
const showShortcuts = ref(false)
const showSettings = ref(false)
const currentTime = ref('')
const onlineStatus = ref({ cls: 'online', label: '在线' })

let timeTimer: any = null

// ============ 菜单 ============
const roleLabel = computed(() => {
  const m: any = { AGENT: '坐席', ADVISOR: '理财经理', ADMIN: '管理员' }
  return m[auth.user?.role || ''] || '客户'
})
const avatar = computed(() => {
  const m: any = { AGENT: '💼', ADVISOR: '💎', ADMIN: '👑', CUSTOMER: '👤' }
  return m[auth.user?.role || ''] || '👤'
})

const allMenus = [
  { path: '/pc/dashboard', label: '工作台', icon: '📊', shortcut: '1', group: 'main', badge: 3, badgeCls: 'danger' },
  { path: '/pc/customers', label: '客户管理', icon: '👥', shortcut: '2', group: 'main', badge: 12, badgeCls: 'primary' },
  { path: '/pc/bilateral', label: '双边录制', icon: '📞', shortcut: '3', group: 'main' },
  { path: '/pc/filepush', label: '文件推送', icon: '📤', shortcut: '4', group: 'main' },
  { path: '/pc/advisor', label: '理财经理', icon: '💎', shortcut: '5', group: 'main' }
]
const mainMenus = computed(() => {
  if (auth.user?.role === 'ADVISOR') {
    return [
      allMenus[0],
      allMenus[2], // bilateral
      allMenus[4]  // advisor
    ]
  }
  return allMenus
})

const currentMenu = computed(() => mainMenus.value.find(m => isActive(m.path)))

function isActive(p: string) {
  if (p === '/pc/dashboard') return route.path === p
  return route.path.startsWith(p)
}

// ============ 通知 ============
const notifications = ref([
  { id: 1, type: 'urgent', icon: '🚨', title: '紧急: 客户触发禁播词',
    desc: 'FND20260801-900004 - "保证收益" 触发', time: '2 分钟前', read: false, bizId: 'FND20260801-900004' },
  { id: 2, type: 'business', icon: '🆕', title: '新业务进线',
    desc: '客户 张志强 申请购买 固收理财', time: '5 分钟前', read: false, bizId: 'BNK20260801-900005' },
  { id: 3, type: 'risk', icon: '⚠️', title: '风险等级预警',
    desc: '客户 C5 想购买 R1 理财, 严重错配', time: '12 分钟前', read: false, bizId: 'LIC20260801-900001' },
  { id: 4, type: 'task', icon: '✍️', title: '签字待确认',
    desc: '客户 王明华 待电子签字', time: '18 分钟前', read: false, bizId: 'BNK20260801-900003' },
  { id: 5, type: 'transfer', icon: '🔄', title: '转接请求',
    desc: '客户 赵晓东 请求转接理财经理', time: '25 分钟前', read: true },
  { id: 6, type: 'system', icon: '⚙️', title: '系统通知',
    desc: '话术模板 BNK-FIN-2026Q3-001 已更新', time: '1 小时前', read: true }
])

const unreadCount = computed(() => notifications.value.filter(n => !n.read).length)

const notifTabs = computed(() => [
  { label: '全部', value: 'all', count: notifications.value.length },
  { label: '紧急', value: 'urgent', count: notifications.value.filter(n => n.type === 'urgent' && !n.read).length },
  { label: '业务', value: 'business', count: notifications.value.filter(n => n.type === 'business' && !n.read).length },
  { label: '系统', value: 'system', count: notifications.value.filter(n => n.type === 'system' && !n.read).length }
])

const filteredNotifs = computed(() => {
  if (notifTab.value === 'all') return notifications.value
  if (notifTab.value === 'urgent') return notifications.value.filter(n => n.type === 'urgent')
  if (notifTab.value === 'business') return notifications.value.filter(n => n.type === 'business' || n.type === 'task')
  if (notifTab.value === 'system') return notifications.value.filter(n => n.type === 'system' || n.type === 'transfer' || n.type === 'risk')
  return notifications.value
})

const totalActive = computed(() => 12) // mock

// ============ 搜索 ============
const searchResults = ref<any[]>([])

// mock 搜索
function onSearch() {
  if (!searchKey.value) {
    searchResults.value = []
    return
  }
  // 模拟搜索
  searchResults.value = [
    { id: '1', type: 'customer', icon: '👤', title: '张志强', desc: 'cust-hash-001 · C1 · 3 个产品', typeLabel: '客户' },
    { id: '2', type: 'business', icon: '💼', title: searchKey.value, desc: 'OFFLINE · ¥50,000 · ARCHIVED', typeLabel: '业务' },
    { id: '3', type: 'order', icon: '📋', title: 'BNK-FIN-2026Q3-001', desc: '稳赢系列 · R2 · 3.6%', typeLabel: '产品' }
  ]
}

function onResultClick(r: any) {
  searchOpen.value = false
  if (r.type === 'customer') router.push('/pc/customers')
  else if (r.type === 'business') router.push(`/pc/record/${r.title}`)
  else if (r.type === 'order') router.push(`/pc/filepush`)
}

// ============ 操作 ============
function onNotifClick(n: any) {
  n.read = true
  notifOpen.value = false
  if (n.bizId) {
    router.push(`/pc/record/${n.bizId}`)
  }
}
function markAllRead() {
  notifications.value.forEach(n => n.read = true)
}
function onLogout() {
  auth.logout()
  router.replace('/')
}

// ============ 快捷键 ============
const shortcuts = {
  nav: [
    { label: '工作台', keys: ['1'] },
    { label: '客户管理', keys: ['2'] },
    { label: '双边录制', keys: ['3'] },
    { label: '文件推送', keys: ['4'] },
    { label: '理财经理', keys: ['5'] }
  ],
  action: [
    { label: '全局搜索', keys: ['⌘', 'K'] },
    { label: '显示快捷键', keys: ['?'] },
    { label: '关闭弹窗', keys: ['ESC'] },
    { label: '收起侧边栏', keys: ['['] }
  ]
}

function onKeydown(e: KeyboardEvent) {
  // 数字 1-5 切 tab
  if (!e.metaKey && !e.ctrlKey && !e.altKey) {
    const target = e.target as HTMLElement
    if (target.tagName === 'INPUT' || target.tagName === 'TEXTAREA') return
    if (e.key >= '1' && e.key <= '5') {
      const idx = parseInt(e.key) - 1
      if (mainMenus.value[idx]) {
        router.push(mainMenus.value[idx].path)
      }
    }
  }
  // Cmd/Ctrl + K
  if ((e.metaKey || e.ctrlKey) && e.key === 'k') {
    e.preventDefault()
    searchOpen.value = !searchOpen.value
  }
  // ?
  if (e.key === '?' && !searchOpen.value) {
    e.preventDefault()
    showShortcuts.value = true
  }
  // ESC
  if (e.key === 'Escape') {
    searchOpen.value = false
    showShortcuts.value = false
    notifOpen.value = false
  }
  // [
  if (e.key === '[') {
    sidebarCollapsed.value = !sidebarCollapsed.value
  }
}

// ============ 时间更新 ============
function updateTime() {
  const now = new Date()
  currentTime.value = now.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

onMounted(() => {
  updateTime()
  timeTimer = setInterval(updateTime, 1000)
  // 模拟 WebSocket: 每 10s 可能收到新通知
  window.addEventListener('keydown', onKeydown)
  // 模拟在线状态 (无操作 30s 后切换)
  setTimeout(() => {
    if (document.visibilityState === 'visible') {
      onlineStatus.value = { cls: 'busy', label: '繁忙' }
    }
  }, 30000)
})

onUnmounted(() => {
  if (timeTimer) clearInterval(timeTimer)
  window.removeEventListener('keydown', onKeydown)
})
</script>

<style lang="scss" scoped>
@import '@/styles/agent-theme.scss';

.pc-layout {
  display: flex;
  height: 100vh;
  background: var(--bg);
  overflow: hidden;
}

// ============ 侧边栏 ============
.sidebar {
  width: 232px;
  background: var(--primary-gradient);
  color: white;
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  transition: width 0.2s;
  position: relative;
  z-index: 100;
  &.collapsed { width: 64px; }
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 18px 20px;
  border-bottom: 1px solid rgba(255,255,255,0.08);
  flex-shrink: 0;
}
.brand-logo {
  width: 36px; height: 36px;
  background: rgba(184, 134, 11, 0.2);
  border-radius: var(--radius);
  display: flex; align-items: center; justify-content: center;
  font-size: 20px;
  flex-shrink: 0;
}
.brand-text { min-width: 0; }
.brand-name { font-size: 16px; font-weight: 700; }
.brand-tag { font-size: 11px; opacity: 0.6; }

.nav-section { padding: 12px 20px 4px; }
.nav-label {
  font-size: 10px;
  text-transform: uppercase;
  letter-spacing: 1px;
  color: rgba(255,255,255,0.4);
  font-weight: 600;
}

.nav {
  flex: 1;
  overflow-y: auto;
  padding: 0 12px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.nav-item {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 12px;
  color: rgba(255,255,255,0.7);
  text-decoration: none;
  font-size: 13px;
  border-radius: var(--radius);
  cursor: pointer;
  transition: all 0.15s;
  &:hover {
    color: white;
    background: rgba(255,255,255,0.06);
  }
  &.active {
    color: white;
    background: rgba(184, 134, 11, 0.2);
    box-shadow: 0 2px 8px rgba(184, 134, 11, 0.15);
    &::before {
      content: '';
      position: absolute;
      left: 0; top: 8px; bottom: 8px;
      width: 3px;
      background: var(--accent-light);
      border-radius: 0 2px 2px 0;
    }
  }
}
.ni { font-size: 18px; flex-shrink: 0; }
.nt { flex: 1; min-width: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.kbd {
  font-size: 10px;
  padding: 1px 6px;
  background: rgba(255,255,255,0.1);
  border-radius: 3px;
  font-family: 'JetBrains Mono', monospace;
  color: rgba(255,255,255,0.6);
}
.nav-badge {
  position: absolute;
  top: 4px; right: 4px;
  min-width: 18px; height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  font-size: 10px;
  font-weight: 700;
  display: flex; align-items: center; justify-content: center;
  &.danger { background: var(--danger); color: white; }
  &.primary { background: var(--info); color: white; }
}

.sidebar-footer {
  border-top: 1px solid rgba(255,255,255,0.08);
  padding: 8px;
}
.collapse-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  padding: 6px;
  background: rgba(255,255,255,0.05);
  border: 1px solid rgba(255,255,255,0.1);
  border-radius: var(--radius);
  color: rgba(255,255,255,0.7);
  cursor: pointer;
  font-size: 12px;
  margin-bottom: 8px;
  &:hover { background: rgba(255,255,255,0.1); }
}
.ml-1 { margin-left: 4px; }

.user-card {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px;
  background: rgba(255,255,255,0.05);
  border-radius: var(--radius);
}
.u-avatar {
  width: 32px; height: 32px;
  background: rgba(184, 134, 11, 0.2);
  border-radius: 50%;
  display: flex; align-items: center; justify-content: center;
  font-size: 16px;
  flex-shrink: 0;
  &.small { width: 28px; height: 28px; font-size: 14px; }
}
.u-info { flex: 1; min-width: 0; }
.u-name {
  font-size: 12px; font-weight: 600;
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.u-role {
  font-size: 10px;
  opacity: 0.7;
  display: flex; align-items: center; gap: 4px;
}
.status-dot {
  width: 6px; height: 6px;
  border-radius: 50%;
  background: var(--success);
  &.online { background: var(--success); }
  &.busy { background: var(--warning); }
  &.offline { background: var(--text-3); }
}
.u-action {
  width: 24px; height: 24px;
  background: rgba(255,255,255,0.05);
  border: none;
  border-radius: var(--radius-sm);
  color: rgba(255,255,255,0.7);
  cursor: pointer;
  &:hover { color: white; background: rgba(255,255,255,0.15); }
}

// ============ 主内容 ============
.main-wrap {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  position: relative;
}

.topbar {
  background: white;
  border-bottom: 1px solid var(--border);
  padding: 12px 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-shrink: 0;
  position: relative;
  z-index: 50;
}
.topbar-left { flex-shrink: 0; }
.breadcrumb {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-1);
}
.bc-item {
  display: flex;
  align-items: center;
  gap: 4px;
  &::before {
    content: '📍';
    font-size: 12px;
  }
}

.topbar-center { flex: 1; max-width: 480px; }
.search-box {
  display: flex;
  align-items: center;
  gap: 8px;
  background: var(--bg);
  border: 1px solid transparent;
  border-radius: var(--radius);
  padding: 6px 12px;
  cursor: text;
  transition: all 0.15s;
  &:hover { background: white; border-color: var(--border); }
  &:focus-within { background: white; border-color: var(--accent); box-shadow: 0 0 0 3px rgba(184, 134, 11, 0.1); }
}
.search-icon { color: var(--text-3); font-size: 14px; }
.search-input {
  flex: 1;
  background: transparent;
  border: none;
  outline: none;
  font-size: 13px;
  font-family: inherit;
  color: var(--text-1);
  &::placeholder { color: var(--text-3); }
}
.kbd-hint {
  font-size: 10px;
  padding: 1px 5px;
  background: var(--bg);
  border: 1px solid var(--border);
  border-radius: 3px;
  color: var(--text-3);
  font-family: 'JetBrains Mono', monospace;
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}
.status-pill {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  background: var(--success-light);
  color: var(--success);
  border-radius: var(--radius-full);
  font-size: 11px;
  font-weight: 600;
  &.busy { background: var(--warning-light); color: var(--warning); }
  .status-dot { background: currentColor; }
}

.icon-btn {
  position: relative;
  width: 36px; height: 36px;
  background: var(--bg);
  border: 1px solid transparent;
  border-radius: var(--radius);
  cursor: pointer;
  font-size: 16px;
  color: var(--text-2);
  transition: all 0.15s;
  &:hover { background: var(--bg-secondary); color: var(--text-1); }
  &.notif { font-size: 16px; }
}
.notif-badge {
  position: absolute;
  top: -2px; right: -2px;
  min-width: 16px; height: 16px;
  padding: 0 4px;
  background: var(--danger);
  color: white;
  border-radius: 8px;
  font-size: 10px;
  font-weight: 700;
  display: flex; align-items: center; justify-content: center;
  box-shadow: 0 0 0 2px white;
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 4px 8px 4px 4px;
  background: var(--bg);
  border-radius: var(--radius-full);
}
.u-name-short { font-size: 12px; font-weight: 500; color: var(--text-1); }

// 通知下拉
.notif-panel {
  position: absolute;
  top: calc(100% + 8px);
  right: 24px;
  width: 400px;
  max-height: 600px;
  background: white;
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xl);
  border: 1px solid var(--border);
  z-index: 100;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}
.notif-header {
  padding: 12px 16px;
  border-bottom: 1px solid var(--border-light);
  display: flex;
  justify-content: space-between;
  align-items: center;
  h4 { font-size: 14px; font-weight: 600; margin: 0; }
}
.notif-tabs {
  display: flex;
  padding: 4px 8px;
  border-bottom: 1px solid var(--border-light);
  background: var(--bg);
  gap: 4px;
}
.ntab {
  padding: 6px 10px;
  font-size: 12px;
  color: var(--text-3);
  border-radius: var(--radius-sm);
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 4px;
  &.active { background: white; color: var(--accent); font-weight: 600; }
}
.ntab-count {
  font-size: 10px;
  padding: 0 5px;
  background: var(--danger);
  color: white;
  border-radius: 8px;
}
.notif-list {
  flex: 1;
  overflow-y: auto;
  max-height: 440px;
}
.notif {
  display: flex;
  gap: 12px;
  padding: 12px 16px;
  cursor: pointer;
  border-bottom: 1px solid var(--border-light);
  position: relative;
  transition: background 0.15s;
  &:hover { background: var(--bg); }
  &.unread { background: rgba(184, 134, 11, 0.02); }
  &.unread::before {
    content: '';
    position: absolute;
    left: 0; top: 0; bottom: 0;
    width: 3px;
    background: var(--accent);
  }
}
.notif-icon {
  width: 32px; height: 32px;
  border-radius: var(--radius);
  display: flex; align-items: center; justify-content: center;
  font-size: 16px;
  flex-shrink: 0;
  background: var(--bg);
}
.notif.n-urgent .notif-icon { background: var(--danger-light); }
.notif.n-business .notif-icon { background: var(--info-light); }
.notif.n-risk .notif-icon { background: var(--warning-light); }
.notif.n-task .notif-icon { background: var(--accent); color: white; }
.notif.n-system .notif-icon { background: var(--bg-secondary); }
.notif-body { flex: 1; min-width: 0; }
.notif-title { font-size: 13px; font-weight: 600; color: var(--text-1); }
.notif-desc { font-size: 12px; color: var(--text-2); margin-top: 2px; }
.notif-time { font-size: 11px; color: var(--text-3); margin-top: 4px; }
.notif-dot {
  position: absolute;
  top: 16px; right: 16px;
  width: 8px; height: 8px;
  border-radius: 50%;
  background: var(--accent);
}

.content {
  flex: 1;
  overflow-y: auto;
}

// ============ 状态栏 ============
.statusbar {
  background: white;
  border-top: 1px solid var(--border);
  padding: 6px 24px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 11px;
  color: var(--text-3);
  flex-shrink: 0;
}
.sb-left, .sb-right { display: flex; gap: 16px; }
.sb-item { display: flex; align-items: center; gap: 4px; }
.sb-dot {
  width: 6px; height: 6px;
  border-radius: 50%;
  background: var(--success);
  &.busy { background: var(--warning); }
}

// ============ 弹窗 ============
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(15, 23, 42, 0.5);
  backdrop-filter: blur(4px);
  z-index: 200;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

.modal {
  background: white;
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xl);
  max-width: 720px;
  width: 100%;
  max-height: 80vh;
  display: flex;
  flex-direction: column;
}
.modal-header {
  padding: 16px 20px;
  border-bottom: 1px solid var(--border);
  display: flex;
  justify-content: space-between;
  align-items: center;
  h3 { margin: 0; font-size: 16px; font-weight: 600; }
}
.modal-close {
  width: 28px; height: 28px;
  background: var(--bg);
  border: none;
  border-radius: var(--radius);
  cursor: pointer;
  font-size: 18px;
  color: var(--text-2);
  &:hover { background: var(--bg-secondary); }
}
.modal-body {
  padding: 20px;
  overflow-y: auto;
}

.shortcut-group { margin-bottom: 20px; }
.sg-title {
  font-size: 12px;
  color: var(--text-3);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  font-weight: 600;
  margin-bottom: 8px;
}
.sg-list { display: flex; flex-direction: column; gap: 4px; }
.sg-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 12px;
  background: var(--bg);
  border-radius: var(--radius);
  font-size: 13px;
  color: var(--text-1);
}

// ============ 全局搜索 ============
.search-modal {
  background: white;
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-xl);
  max-width: 640px;
  width: 100%;
  max-height: 70vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.search-modal-input {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;
  border-bottom: 1px solid var(--border);
}
.search-icon { font-size: 18px; color: var(--text-3); }
.search-modal-field {
  flex: 1;
  border: none;
  outline: none;
  font-size: 16px;
  font-family: inherit;
  &::placeholder { color: var(--text-3); }
}
.search-modal-results {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}
.search-tips {
  padding: 32px 20px;
  text-align: center;
}
.tip-title { font-size: 14px; font-weight: 600; color: var(--text-1); margin-bottom: 8px; }
.tip-item { font-size: 12px; color: var(--text-3); margin: 4px 0; }

.result-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-radius: var(--radius);
  cursor: pointer;
  &:hover { background: var(--bg); }
  &.selected { background: rgba(184, 134, 11, 0.08); }
}
.r-icon { font-size: 20px; }
.r-info { flex: 1; min-width: 0; }
.r-title { font-size: 14px; font-weight: 600; color: var(--text-1); }
.r-desc { font-size: 12px; color: var(--text-3); margin-top: 2px; }
.r-type {
  font-size: 10px;
  padding: 2px 8px;
  background: var(--bg);
  color: var(--text-2);
  border-radius: var(--radius-full);
  font-weight: 600;
}

// ============ 过渡 ============
.fade-enter-active, .fade-leave-active { transition: opacity 0.2s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }

.dropdown-enter-active, .dropdown-leave-active {
  transition: all 0.2s ease;
  transform-origin: top right;
}
.dropdown-enter-from, .dropdown-leave-to {
  opacity: 0;
  transform: translateY(-8px) scale(0.96);
}

.modal-enter-active, .modal-leave-active { transition: opacity 0.2s; }
.modal-enter-from, .modal-leave-to { opacity: 0; }
</style>
