# 智能双录移动端 / 客户服务中心 (smart-dual-record-mobile)

> 双录一体化中台 · 独立移动端 + PC 坐席前端服务
> Vue 3 + Vite + TypeScript + Vant 4 + Pinia

## ✨ 项目定位

`smart-dual-record-mobile` 是与 `smart-dual-record-ui` (后台管理工作台) **完全独立**的前端服务, 专注于:

| 端 | 角色 | 设备 |
|---|---|---|
| **H5 端** | 客户 | 手机 / 移动浏览器 |
| **PC 端** | 坐席 / 理财经理 | PC 浏览器 |

**核心业务闭环**:
- 客户选产品 → 风险评估 → 订单确认 → 双录 8 节点 → 签字 → 15 天犹豫期
- 坐席管理客户 → 推文件 → 双录工作台 → 接单 → 服务
- 线下失败 → 线上继续 (跨渠道补录)

## 📁 项目结构

```
smart-dual-record-mobile/
├── src/
│   ├── api/index.ts              # 11 个 API 组 (65+ endpoints)
│   ├── stores/                   # Pinia 状态管理
│   │   ├── auth.ts               # 登录态 / 用户角色
│   │   ├── recording.ts          # 8 节点状态机
│   │   └── ws.ts                 # WebSocket (实时通知)
│   ├── router/index.ts           # 路由 (hash mode)
│   ├── views/
│   │   ├── home/                 # 入口选择页
│   │   ├── auth/Login.vue        # 登录
│   │   ├── h5/                   # H5 客户端 (10 页面)
│   │   │   ├── Layout.vue        # 底部 tabbar
│   │   │   ├── Home.vue          # 我的财富
│   │   │   ├── ProductList.vue   # 产品超市
│   │   │   ├── ProductDetail.vue # 产品详情
│   │   │   ├── RiskAssessment.vue# 9 维风险评估
│   │   │   ├── OrderConfirm.vue  # 订单确认
│   │   │   ├── RecordFlow.vue    # 双录流程 ⭐
│   │   │   ├── MyOrders.vue      # 我的订单
│   │   │   ├── OrderDetail.vue   # 订单详情
│   │   │   ├── PushedFiles.vue   # 待签文件
│   │   │   ├── Profile.vue       # 个人中心
│   │   │   └── ResumeFlow.vue    # 跨渠道补录
│   │   └── pc/                   # PC 坐席端 (5 页面)
│   │       ├── Layout.vue        # 侧边栏导航
│   │       ├── Dashboard.vue     # 工作台 (含 ECharts)
│   │       ├── CustomerList.vue  # 客户管理
│   │       ├── RecordWorkbench.vue# 双录工作台 (双摄像头)
│   │       ├── FilePush.vue      # 文件推送
│   │       └── Advisor.vue       # 理财经理
│   └── styles/main.scss          # 主题色 + 通用样式
├── vite.config.ts                # Vite + 代理
├── package.json
└── tsconfig.json
```

## 🚀 快速开始

```bash
npm install
npm run dev          # 启动 (默认 5174 端口)
npm run build        # 生产构建
npm run type-check   # TypeScript 检查
```

## 📱 H5 端 (移动客户)

- 入口: 首页 4 个角色卡片 (客户/坐席/理财经理/预约回访)
- 5 个 tabbar: 首页 / 产品 / 订单 / 文件 / 我的
- 双录流程: 8 节点时间轴 + 摄像头实时预览 + Canvas 水印 + 手写签名
- 风险评估: 9 维问卷 → 自动计算 C1-C5
- 风险匹配: 弹窗警告 (客户 vs 产品)

## 💼 PC 端 (坐席)

- 侧边栏: 工作台 / 客户管理 / 文件推送 (+ 理财经理)
- 工作台: 4 统计盒 + ECharts 趋势 + 实时告警 + 今日业务
- 客户管理: 搜索/筛选/双录入口
- 双录工作台: 双摄像头布局 + 8 节点 + 实时检查
- 文件推送: 5 模板 + 自定义上传 + 推送历史
- 理财经理: 待接单 + 聊天 (WebSocket)

## 🎨 主题色

- 米色 `#f5f3ec` / 深蓝 `#1e2a47` / 金色 `#b8860b`
- 字体: System / Outfit / JetBrains Mono
- Vant 4 移动端 UI 组件库

## 🔌 后端依赖

- 主后端: `dual-record-llm-service` (Spring Boot 3, 端口 9000)
- 65+ REST endpoints
- WebSocket: `/ws/recording?businessId=xxx`

## 📊 构建产物

- 总大小: ~1.8MB (gzipped: ~580KB)
- 代码分割: vue-vendor / vant-vendor / echarts-vendor
- 18 个页面 chunk
- 0 TypeScript 错误

## 🔗 GitHub

https://github.com/liugl951127/SmartDualRecord
路径: `smart-dual-record-mobile/`
