import { createRouter, createWebHashHistory } from 'vue-router'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    {
      path: '/',
      component: () => import('@/views/home/Index.vue'),
      meta: { title: '智能双录' }
    },
    {
      path: '/login',
      component: () => import('@/views/auth/Login.vue'),
      meta: { title: '登录' }
    },
    // ==================== H5 客户端 ====================
    {
      path: '/h5',
      component: () => import('@/views/h5/Layout.vue'),
      meta: { title: '客户中心' },
      children: [
        { path: '', redirect: '/h5/home' },
        { path: 'home', component: () => import('@/views/h5/Home.vue'), meta: { title: '我的财富' } },
        { path: 'products', component: () => import('@/views/h5/ProductList.vue'), meta: { title: '产品超市' } },
        { path: 'product/:id', component: () => import('@/views/h5/ProductDetail.vue'), meta: { title: '产品详情' } },
        { path: 'risk', component: () => import('@/views/h5/RiskAssessment.vue'), meta: { title: '风险评估' } },
        { path: 'order', component: () => import('@/views/h5/OrderConfirm.vue'), meta: { title: '订单确认' } },
        { path: 'record/:businessId', component: () => import('@/views/h5/RecordFlow.vue'), meta: { title: '双录' } },
        { path: 'orders', component: () => import('@/views/h5/MyOrders.vue'), meta: { title: '我的订单' } },
        { path: 'order/:id', component: () => import('@/views/h5/OrderDetail.vue'), meta: { title: '订单详情' } },
        { path: 'files', component: () => import('@/views/h5/PushedFiles.vue'), meta: { title: '待签文件' } },
        { path: 'profile', component: () => import('@/views/h5/Profile.vue'), meta: { title: '我的' } }
      ]
    },
    // ==================== PC 坐席端 ====================
    {
      path: '/pc',
      component: () => import('@/views/pc/Layout.vue'),
      meta: { title: '坐席工作台' },
      children: [
        { path: '', redirect: '/pc/dashboard' },
        { path: 'dashboard', component: () => import('@/views/pc/Dashboard.vue'), meta: { title: '工作台' } },
        { path: 'customers', component: () => import('@/views/pc/CustomerList.vue'), meta: { title: '客户列表' } },
        { path: 'record/:businessId', component: () => import('@/views/pc/RecordWorkbench.vue'), meta: { title: '双录工作台' } },
        { path: 'filepush', component: () => import('@/views/pc/FilePush.vue'), meta: { title: '文件推送' } },
        { path: 'advisor', component: () => import('@/views/pc/Advisor.vue'), meta: { title: '理财经理' } }
      ]
    },
    // ==================== 补录通道 ====================
    {
      path: '/resume/:token',
      component: () => import('@/views/h5/ResumeFlow.vue'),
      meta: { title: '继续双录' }
    }
  ]
})

router.beforeEach((to, _from, next) => {
  if (to.meta.title) {
    document.title = `${to.meta.title} · 智能双录`
  }
  // 登录保护
  const token = localStorage.getItem('token')
  if (to.path.startsWith('/h5') || to.path.startsWith('/pc')) {
    if (!token && !to.path.startsWith('/resume')) {
      next('/login?redirect=' + encodeURIComponent(to.fullPath))
      return
    }
  }
  next()
})

export default router
