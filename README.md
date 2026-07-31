# 双录一体化中台 · Spring Boot 3 服务

> 一个中台 · 两条跑道 · 8 节点状态机 · Saga 模式 · 4 渠道同步

## 业务概述

把线下面对面双录、远程视频双录、自助 AI 数字人双录、互联网纯文本 4 渠道
整合到统一的流程编排、质检规则、存证链路中。严格满足
**金发〔2026〕8 号《关于银行业保险业人工智能安全开发应用的指导意见》**及
**《保险销售行为可回溯管理暂行办法》**等监管要求。

## 核心架构

```
┌──────────────────────────────────────────────────────────┐
│  接入层: 4 渠道                                              │
│  OFFLINE / REMOTE_VIDEO / SELF_AI / INTERNET_TEXT         │
└──────────────────────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────┐
│  流程编排: 8 节点状态机 + @Transactional                   │
│  INIT → IDENTITY → RISK → SCRIPT → RECORDING →           │
│  RECORDED → AI_QA → SIGNED → ARCHIVED                     │
│                                                            │
│  (事务边界 = 每个状态变更方法, 失败回滚到上一致状态)        │
└──────────────────────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────┐
│  业务服务层                                                  │
│  RecordingService / ScriptService / QaService /          │
│  RiskAssessmentService / ComplianceService                │
└──────────────────────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────┐
│  AI 网关抽象                                                 │
│  LlmGateway / AsrService / DeepfakeDetector              │
│  沙箱模式: Mock 实现  |  生产模式: 阿里/DeepSeek/OpenAI  │
└──────────────────────────────────────────────────────────┘
                          ↓
┌──────────────────────────────────────────────────────────┐
│  持久层: 8 张表 (MyBatis-Plus + H2/MariaDB)                 │
│  tb_business / tb_recording / tb_rec_node / tb_qa_result │
│  tb_event / tb_script_template / tb_risk_assessment /     │
│  tb_forbidden_phrase                                        │
└──────────────────────────────────────────────────────────┘
```

## 技术栈

- **Spring Boot** 3.2.5 (JDK 17)
- **MyBatis-Plus** 3.5.7
- **H2** (沙箱) / **MariaDB** (生产)
- **SnakeYAML** (话术模板加载)
- **WebSocket** (实时 ASR + AI 告警推送)
- **SpringDoc OpenAPI** (API 文档)
- **Lombok** (样板代码)
- **JUnit 5** (单元测试 + 集成测试)

## 快速开始

### 1. 沙箱模式（默认，离线可跑）

```bash
# 编译
mvn clean compile

# 跑测试
mvn test

# 启动服务（H2 内存数据库 + Mock AI）
mvn spring-boot:run -Dspring-boot.run.profiles=sandbox

# 访问
# API:        http://localhost:8080/api/v1/...
# Swagger:    http://localhost:8080/swagger-ui.html
# H2 Console: http://localhost:8080/h2-console
# Health:     http://localhost:8080/api/v1/health
```

### 2. 生产模式（连接 MariaDB）

```bash
# 1. 创建数据库
mysql -uroot -e "CREATE DATABASE dualrecord DEFAULT CHARSET utf8mb4;"

# 2. 初始化 schema（手动执行）
mysql -uroot dualrecord < src/main/resources/db/schema.sql

# 3. 启动
DB_URL=jdbc:mariadb://localhost:3306/dualrecord \
DB_USER=dualrecord \
DB_PASSWORD=yourpass \
ALIYUN_LLM_ENDPOINT=https://dashscope.aliyuncs.com/api/v1/... \
mvn spring-boot:run -Dspring-boot.run.profiles=prod
```

## 关键 API

### 创建双录业务
```bash
curl -X POST http://localhost:8080/api/v1/recording/start \
  -H "Content-Type: application/json" \
  -d '{
    "businessType": "WEALTH",
    "productId": "BNK-FIN-2026Q3-001",
    "customerIdHash": "cust-001",
    "channel": "OFFLINE",
    "sellerType": "HUMAN",
    "amount": 50000
  }'
```

### 加载话术
```bash
curl -X POST "http://localhost:8080/api/v1/recording/script/load?businessId=BNK20260801-000001&productId=BNK-FIN-2026Q3-001"
```

### 风险评估
```bash
curl -X POST "http://localhost:8080/api/v1/recording/risk/assess?businessId=BNK20260801-000001&customerIdHash=cust-001"
```

### 启动录制
```bash
curl -X POST "http://localhost:8080/api/v1/recording/begin?businessId=BNK20260801-000001"
```

### 完成节点
```bash
curl -X POST http://localhost:8080/api/v1/recording/node/complete \
  -H "Content-Type: application/json" \
  -d '{
    "businessId": "BNK20260801-000001",
    "recId": "REC20260801-000001",
    "node": "NODE_06_CONFIRM",
    "asrText": "是的，我清楚了。"
  }'
```

### 终检 + 签字
```bash
curl -X POST "http://localhost:8080/api/v1/recording/finalize?businessId=BNK20260801-000001&recId=REC20260801-000001&fullAsrText=..."

curl -X POST "http://localhost:8080/api/v1/recording/sign?businessId=BNK20260801-000001"
```

### 查询业务全景
```bash
curl http://localhost:8080/api/v1/recording/overview/BNK20260801-000001
```

### 查询状态机
```bash
# 所有合法转移
curl http://localhost:8080/api/v1/statemachine/transitions

# 检查两个状态之间是否合法
curl "http://localhost:8080/api/v1/statemachine/can-transition?from=INIT&to=RECORDING"
```

### 禁播词扫描
```bash
curl -X POST "http://localhost:8080/api/v1/compliance/scan?text=这个产品保证收益"
```

## 8 节点状态机

```
INIT
  ↓ IDENTITY_OK
IDENTITY_VERIFIED
  ↓ RISK_OK
RISK_ASSESSED
  ↓ SCRIPT_LOAD_OK
SCRIPT_LOADED
  ↓ START_RECORDING
RECORDING
  ├─ NODE_01_IDENTITY
  ├─ NODE_02_DISCLOSURE     ← 风险揭示（必播 4 条）
  ├─ NODE_03_PRODUCT        ← 产品展示
  ├─ NODE_04_RIGHTS         ← 权利义务
  ├─ NODE_05_TRUTH_TELL     ← 如实告知
  ├─ NODE_06_CONFIRM        ← ★ 关键节点：ASR 肯定词 + 人工双签
  ├─ NODE_07_SIGN           ← 签署文件
  └─ NODE_08_FOLLOWUP       ← 补充询问
  ↓ ALL_NODES_OK
RECORDED
  ↓
AI_QA
  ├─ PASS → AI_QA_PASSED → SIGNED → ARCHIVED
  ├─ PASS_WITH_FINDINGS → AI_QA_FLAGGED → HUMAN_REVIEW → SIGNED
  └─ FAIL → AI_QA_FLAGGED → FAILED (事务回滚 + 标 FAIL)

## 事务管理（@Transactional 模式）

每个 public 状态变更方法 = 1 个 Spring 事务:
- 业务表更新
- 事件日志追加
- 录像表 / 节点表更新

任意步骤失败 → 整个事务回滚 → 业务停留在上一致状态。

**失败兜底机制**:
- 状态机非法转移 → IllegalStateTransitionException (409)
- 业务异常（禁播词 / 风险失效） → BusinessException (400)
- 系统异常 → 事务回滚 (500)
- 孤儿业务（30 分钟无更新） → StaleBusinessDetector 定时标 FAILED
- 人工介入 → POST /api/v1/recording/manual-fail
```

## 3 套话术模板

| 产品族 | 产品 | 风险等级 | 文件 |
|---|---|---|---|
| 保险 | 投连险稳健账户 | P5 | `scripts/insurance/investment-linked-2026Q3.yaml` |
| 银行理财 | 稳健型封闭式理财 | R2 | `scripts/wealth/stable-bond-2026Q3.yaml` |
| 基金 | 纯债债券型基金 | R2 | `scripts/fund/bond-fund-2026Q3.yaml` |

每套模板含：
- `product_id` / `risk_level` / `target_customer`
- 8 节点定义（每个节点的必播项 + 必问问题）
- 禁播词列表
- 4 渠道差分（channel_overrides）
- 监管依据

## WebSocket 实时通道

路径: `ws://localhost:8080/ws/recording/{businessId}`

客户端发送：
```json
{"type": "ASR_CHUNK", "text": "客户说的话"}
```

服务端推送：
```json
{"type": "FORBIDDEN_PHRASE_HIT", "phrase": "保证收益", "severity": "HIGH"}
{"type": "DEEPFAKE_ALERT", "score": 0.96, "verdict": "SUSPECTED"}
{"type": "NODE_COMPLETED", "node": "02-disclosure", "duration": 60}
```

## 配置项

| 配置 | 默认值 | 说明 |
|---|---|---|
| `dual-record.recording.allow-pause` | `false` | 是否允许暂停（监管要求 false） |
| `dual-record.recording.timestamp-precision` | `ms` | 时间戳精度 |
| `dual-record.recording.node-timeout-sec` | `120` | 单节点超时 |
| `dual-record.evidence.encryption` | `SM4-CBC` | 加密算法 |
| `dual-record.evidence.retention-years` | `10` | 留存年限 |
| `dual-record.deepfake-detector.threshold` | `0.92` | 反深伪阈值 |
| `dual-record.qa.pre-screen-ratio` | `1.0` | AI 预筛覆盖率 |
| `dual-record.qa.human-sample-ratio` | `0.30` | 普通产品人工抽检 |
| `dual-record.qa.high-risk-sample-ratio` | `1.0` | 高风险产品人工复核 |
| `dual-record.risk-assessment.validity-months` | `12` | 风险测评有效期 |

## 测试覆盖

- 状态机单元测试（合法/非法转移/边界）
- 话术加载测试（3 套产品）
- 风险评估测试（评分/匹配）
- 端到端集成测试（完整业务流）
- 禁播词阻断测试

```bash
mvn test
```

## 项目结构

```
dual-record-llm-service/
├── pom.xml
├── README.md
├── src/
│   ├── main/
│   │   ├── java/com/minimax/dualrecord/
│   │   │   ├── DualRecordApplication.java
│   │   │   ├── config/              # 配置类
│   │   │   ├── controller/          # REST API
│   │   │   ├── service/             # 业务服务
│   │   │   ├── domain/              # 实体
│   │   │   │   └── enums/           # 枚举
│   │   │   ├── dto/                 # 请求/响应 DTO
│   │   │   ├── statemachine/        # 8 节点状态机
│   │   │   ├── saga/                # Saga 协调器
│   │   │   ├── ai/                  # AI 网关抽象 + Mock 实现
│   │   │   ├── repository/          # MyBatis-Plus Mapper
│   │   │   ├── websocket/           # WebSocket Handler
│   │   │   ├── exception/           # 全局异常
│   │   │   └── util/                # 工具类
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-sandbox.yml
│   │       ├── application-prod.yml
│   │       ├── db/schema.sql        # 8 张表
│   │       ├── db/data.sql          # 初始数据
│   │       └── scripts/             # 3 套 YAML 话术
│   └── test/
│       └── java/com/minimax/dualrecord/
│           ├── statemachine/        # 状态机单测
│           ├── service/             # 服务单测
│           └── integration/         # 端到端测试
```

## 监管合规

- **金发〔2026〕8 号** - AI 安全开发应用指导意见
- **保险销售行为可回溯管理暂行办法** - 8 节点法定
- **金融机构产品适当性管理办法** - 风险匹配 + 10 年留存
- **商业银行理财业务监督管理办法**
- **基金募集机构投资者适当性管理实施指引**
- **宁波保险销售行为远程同步录音录像管理暂行办法**

## 注意事项

- 沙箱模式用 H2 内存库 + Mock AI，<strong>数据重启即丢</strong>
- 数字人场景必须开启 `watermark_visible=1` 和 `audio_id_per_minute>=1`
- 节点 6（明确肯定答复）必须有 ASR 肯定词 + 坐席人工双签
- 任何状态变更都走 Saga 模式，失败自动补偿
- 所有禁播词命中都写入事件流（不可删除），便于事后审计
