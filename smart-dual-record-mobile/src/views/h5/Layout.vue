<template>
  <div class="h5-layout">
    <router-view v-slot="{ Component }">
      <transition name="slide" mode="out-in">
        <component :is="Component" />
      </transition>
    </router-view>

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
.slide-enter-active, .slide-leave-active {
  transition: all 0.25s;
}
.slide-enter-from { transform: translateX(20px); opacity: 0; }
.slide-leave-to { transform: translateX(-20px); opacity: 0; }
</style>
