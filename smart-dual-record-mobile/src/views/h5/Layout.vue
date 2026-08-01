<template>
  <div class="h5-layout">
    <div class="h5-content">
      <router-view v-slot="{ Component }">
        <transition name="slide" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </div>

    <van-tabbar v-model="activeTab" route safe-area-inset-bottom>
      <van-tabbar-item to="/h5/home" icon="home-o">首页</van-tabbar-item>
      <van-tabbar-item to="/h5/products" icon="gift-card-o">产品</van-tabbar-item>
      <van-tabbar-item to="/h5/orders" icon="orders-o">订单</van-tabbar-item>
      <van-tabbar-item to="/h5/files" icon="description">文件</van-tabbar-item>
      <van-tabbar-item to="/h5/profile" icon="user-o">我的</van-tabbar-item>
    </van-tabbar>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const activeTab = ref(0)

const tabs = ['/h5/home', '/h5/products', '/h5/orders', '/h5/files', '/h5/profile']
watch(() => route.path, (p) => {
  const i = tabs.findIndex(t => p.startsWith(t))
  if (i >= 0) activeTab.value = i
}, { immediate: true })
</script>

<style lang="scss" scoped>
.h5-layout {
  min-height: 100vh;
  background: var(--bg);
}
.h5-content {
  // 给底部 tabbar 留空间 (tabbar 高度 50px + safe area)
  padding-bottom: calc(60px + env(safe-area-inset-bottom, 0px));
  min-height: 100vh;
}
.slide-enter-active, .slide-leave-active {
  transition: all 0.25s;
}
.slide-enter-from { transform: translateX(20px); opacity: 0; }
.slide-leave-to { transform: translateX(-20px); opacity: 0; }

// Vant Tabbar 主题覆盖
:deep(.van-tabbar) {
  background: rgba(255, 255, 255, 0.95);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-top: 1px solid var(--line);
  box-shadow: 0 -2px 12px rgba(60, 40, 20, 0.04);
  height: calc(60px + env(safe-area-inset-bottom, 0px));
}
:deep(.van-tabbar-item) {
  color: var(--ink-3);
  font-size: 11px;
  .van-tabbar-item__icon { font-size: 22px; }
  &.van-tabbar-item--active {
    color: var(--accent);
    background: transparent;
  }
}
:deep(.van-tabbar-item__text) {
  margin-top: 2px;
  font-weight: 500;
}
</style>
