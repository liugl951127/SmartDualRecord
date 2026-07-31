# 智能双录一体化中台 · Smart Dual Record

> **Monorepo 仓库** · Spring Boot 3 后端 + Vue 3 前端

一个中台 · 两条跑道 · 8 节点状态机 · Saga 模式 · 4 渠道同步

## 仓库结构

```
SmartDualRecord/
├── src/                          # Spring Boot 3 后端
│   ├── main/java/com/minimax/dualrecord/
│   └── main/resources/
│       ├── application*.yml
│       ├── db/                   # 8 张核心表 schema
│       └── scripts/              # 3 套话术 YAML
├── pom.xml                       # Maven 配置
└── smart-dual-record-ui/         # Vue 3 前端工作台
    ├── src/
    │   ├── api/                  # 21 个后端 API 封装
    │   ├── components/           # 5 个核心组件
    │   ├── stores/               # Pinia
    │   └── utils/
    ├── package.json
    └── vite.config.ts
```

## 快速开始

### 1. 启动后端（端口 8080）

```bash
# 沙箱模式：H2 内存库 + Mock AI 网关
mvn spring-boot:run -Dspring-boot.run.profiles=sandbox
```

启动后：
- API 文档: http://localhost:8080/swagger-ui.html
- H2 控制台: http://localhost:8080/h2-console
- 健康检查: http://localhost:8080/api/v1/health

### 2. 启动前端（端口 5173）

```bash
cd smart-dual-record-ui

# 安装依赖
npm install

# 开发模式
npm run dev
# 访问 http://localhost:5173

# 生产构建
npm run build
```

## 核心特性

| 模块 | 文件 | 关键设计 |
|---|---|---|
| 8 节点状态机 | 后端 `statemachine/RecordingStateMachine.java` | 16 状态 + 显式转移表 + 非法抛异常 |
| Saga 模式 | 后端 `saga/RecordingSagaCoordinator.java` | 失败自动补偿 + 事件溯源 |
| 录制主服务 | 后端 `service/RecordingService.java` | 串联 7 步：start → script → risk → record → 8 nodes → final qa → sign |
| AI 网关 | 后端 `ai/LlmGateway.java` | 抽象接口 + Mock 实现可换阿里/DeepSeek/OpenAI |
| 通用话术 | 后端 `service/ScriptService.java` | 三层叠加：产品专属 → 产品族 → 全局默认 |
| 录制工作台 | 前端 `components/RecordingWorkbench.vue` | 8 节点可视化 + 实时禁播词扫描 |

## 监管合规

- **金发〔2026〕8 号** · 银行业保险业人工智能安全开发应用指导意见
- **保险销售行为可回溯管理暂行办法** · 8 节点法定
- **金融机构产品适当性管理办法** · C1-C5 适当性 + 10 年留存
- **商业银行理财业务监督管理办法**
- **基金募集机构投资者适当性管理实施指引**
- **宁波保险销售行为远程同步录音录像管理暂行办法**

## 技术栈

### 后端
- Spring Boot 3.2.5
- JDK 17
- MyBatis-Plus 3.5.7
- H2 (沙箱) / MariaDB (生产)
- SnakeYAML
- SpringDoc OpenAPI
- JUnit 5

### 前端
- Vue 3.5 (Composition API)
- Vite 5
- TypeScript 5
- Element Plus 2.x
- Pinia 2
- Axios 1

## 验证状态

| 维度 | 结果 |
|---|---|
| 后端编译 | 71 文件 / 4,284 行 |
| 前端类型检查 | ✓ 0 错误 |
| 前端生产构建 | ✓ 1718 modules, 1.2 MB JS gzipped 402 KB |
| 前端 dev server | ✓ HTTP 200 |
| 跨页链接 | ✓ 7 份页面互链 |
| HTML 结构 | ✓ 0 失衡, 0 \u 残留 |
