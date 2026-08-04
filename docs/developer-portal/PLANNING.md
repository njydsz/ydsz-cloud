# 开发者门户/内部文档站规划

> P3-建议：建设统一的内部开发协作与知识沉淀平台

## 目录

- [背景与目标](#背景与目标)
- [功能架构](#功能架构)
- [模块详细设计](#模块详细设计)
- [技术选型建议](#技术选型建议)
- [与现有系统集成](#与现有系统集成)
- [部署与运维](#部署与运维)
- [实施路线图](#实施路线图)

---

## 背景与目标

### 现状痛点

| 问题 | 影响 | 紧急度 |
|------|------|--------|
| API 文档散落各服务 | 新人上手慢，跨团队协作困难 | 高 |
| 架构决策无沉淀 | 重复踩坑，设计反复推翻 | 高 |
| 运维手册 Markdown 分散 | 故障时找不到关键操作 | 中 |
| 新人 Onboarding 材料缺失 | 入职培训成本高 | 中 |
| 组件库无统一入口 | 重复开发，UI 不一致 | 低 |

### 目标

1. **一站式入口**：API 文档 + 架构文档 + 运维手册 + 新人指南集中管理
2. **自动化集成**：CI/CD 发布自动更新，与 SpringDoc / 代码库联动
3. **搜索驱动**：全文搜索 + 语义化检索（结合 ydz-agent RAG）
4. **权限分级**：公开文档 / 内部文档 / 敏感运维 SOP 三档权限

---

## 功能架构

```
┌────────────────────────────────────────────────────────────────────────┐
│                         开发者门户 (DevPortal)                         │
│                                                                        │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐  ┌────────────┐      │
│  │  API 文档   │  │  架构文档   │  │  运维手册   │  │  新人指南   │      │
│  └─────┬──────┘  └─────┬──────┘  └─────┬──────┘  └─────┬──────┘      │
│        │               │               │               │              │
│        ▼               ▼               ▼               ▼              │
│  ┌─────────────────────────────────────────────────────────────┐      │
│  │                    内容管理层                                │      │
│  │  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐        │      │
│  │  │ 全文搜索 │  │ 版本管理 │  │ 权限控制 │  │ 评论/反馈│        │      │
│  │  └─────────┘  └─────────┘  └─────────┘  └─────────┘        │      │
│  └─────────────────────────────────────────────────────────────┘      │
│        │               │               │               │              │
│        ▼               ▼               ▼               ▼              │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐  ┌────────────┐      │
│  │ 静态站点   │  │  Wiki 中心  │  │ 知识图谱   │  │ 智能问答   │      │
│  └────────────┘  └────────────┘  └────────────┘  └────────────┘      │
│                                                                        │
└────────────────────────────────────────────────────────────────────────┘
```

---

## 模块详细设计

### 1. API 文档中心

**核心功能**：
- 网关聚合的 OpenAPI 3.0 规范自动导入
- 版本对比查看（v1 vs v2）
- 在线调试接口（类似 Swagger UI）
- SDK 自动下载

**数据来源**：
```yaml
# 从网关 Nacos 配置自动拉取
sources:
  type: nacos
  gateway_config_ids:
    - ydsz-common-springdoc.yaml
  refresh_interval: 5m       # 5 分钟自动同步
```

**页面结构**：
```
/api-docs/
├── overview/              # API 总览 + 快速开始
├── user-service/          # 用户服务 API
├── project-service/       # 项目服务 API
├── flow-service/          # 流程图引擎 API
├── ...                    # 其他服务
├── changelog/            # API 变更历史
└── sdk/                  # 客户端 SDK
```

---

### 2. 架构文档中心（ADR）

**Architecture Decision Record 模板**：

```markdown
# ADR-001: 采用 BPMN 2.0 作为工作流标准

* **状态**：已接受
* **日期**：2026-08-01
* **决策者**：架构组

## 背景
需要统一的流程引擎标准以支持未来多业务线接入。

## 决策
采用 BPMN 2.0 作为流程建模标准...

## 原因
1. 行业标准，工具链成熟
2. 可视化建模降低沟通成本
3. 大厂（阿里/字节/美团）普遍采用

## 后果
- 正面：兼容性、可维护性提升
- 负面：学习成本、XML 冗长
```

**目录结构**：
```
/architecture/
├── overview/              # 全局架构图（C4 Model）
├── adr/                   # 架构决策记录
│   ├── 001-bpmn-standard.md
│   ├── 002-microservice-split.md
│   └── ...
├── data-flow/            # 数据流图
├── domain-model/         # 领域模型
└── tech-stack/           # 技术栈清单
```

---

### 3. 运维手册中心

**内容来源**（从 docs/runbooks 自动导入）：

| 文档 | 权限 | 触发场景 |
|------|------|----------|
| 部署手册 | 研发 | 日常发布 |
| 故障处理 SOP | 研发 + SRE | P0/P1 故障 |
| 容量规划 | 架构组 | 季度评估 |
| 备份恢复 | DBA | 灾备演练 |
| 安全应急 | 安全组 | 安全事件 |

**智能检索增强**（结合 ydz-agent）：

```
用户提问："Nacos 挂掉后怎么快速恢复？"
         │
         ▼
    ┌─────────────────┐
    │  RAG 检索引擎   │──── nacos-unavailable.md (相似度 0.95)
    │  pgran 向量库   │──── gateway-503.md (相似度 0.72)
    └────────┬────────┘
             │
             ▼
    "根据文档，Nacos 宕机恢复步骤：
     1. 检查 Nacos 容器状态: kubectl get pods -n ydsz
     2. 查看详细日志: kubectl logs -f deploy/nacos -n ydsz
     3. 重启: kubectl rollout restart deploy/nacos -n ydsz
     4. 验证: curl http://nacos:8848/nacos/v1/console/health
     ..."
```

---

### 4. 新人 Onboarding

**学习路径地图**：

```
Week 1: 环境搭建 + 基础概念
  ├── Day 1-2: 开发环境准备（docker-compose 一键启动）
  ├── Day 3: DDD 五层架构理解 + 代码走读
  └── Day 4-5: 完成第一个 Hello World 接口

Week 2: 核心模块 + 开发规范
  ├── Day 1-2: 用户/认证模块
  ├── Day 3: 网关 + 限流 + 监控
  └── Day 4-5: 代码规范 + 单测要求

Week 3: 进阶主题
  ├── 工作流引擎使用
  ├── ydz-agent 集成
  └── 故障排查最佳实践
```

---

## 技术选型建议

### 方案对比

| 方案 | 优势 | 劣势 | 推荐度 |
|------|------|------|--------|
| Docusaurus | React 生态、SEO 好、版本管理 | 定制性中等 | ⭐⭐⭐⭐⭐ |
| VitePress | 性能极佳、Vue 生态 | 社区较小 | ⭐⭐⭐⭐⭐ |
| NextWiki | 自托管、Wiki 功能全 | 功能较老 | ⭐⭐⭐⭐ |
| Confluence | 功能成熟、企业常用 | 付费、笨重 | ⭐⭐⭐ |
| GitBook | Markdown 友好、SaaS | 数据外流 | ⭐⭐ |

### 推荐方案：Docusaurus + 插件体系

```javascript
// docusaurus.config.js 核心配置
module.exports = {
  title: 'YDSZ Developer Portal',
  url: 'https://dev.ydsz.internal',
  baseUrl: '/',
  
  // 多实例：API 文档 + 架构文档 + 维基
  presets: [
    [
      '@docusaurus/preset-classic',
      {
        docs: {
          sidebarPath: require.resolve('./sidebars.js'),
          editUrl: 'https://github.com/njydsz/ydsz-pmis/tree/main/docs',
          // API OpenAPI 集成
          docItemComponent: require.resolve('./src/components/ApiItem'),
        },
        theme: {
          customCss: require.resolve('./src/css/custom.css'),
        },
      },
    ],
  ],
  
  // OpenAPI 插件（自动从网关同步 API 文档）
  plugins: [
    [
      'docusaurus-plugin-openapi',
      {
        path: 'src/openapi',        // Nacos 同步后落盘路径
        routePath: '/api-docs',
       Yaml Files From Gateway: true,
      },
    ],
  ],
  
  // i18n 国际化
  i18n: {
    defaultLocale: 'zh-CN',
    locales: ['zh-CN'],
  },
};
```

---

## 与现有系统集成

### CI/CD 集成

```yaml
# .github/workflows/update-dev-portal.yml
name: Update Developer Portal

on:
  push:
    branches: [main]
    paths: ['docs/**', '**/src/main/resources/**/openapi.yaml']

jobs:
  update:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      
      # 1. 从网关拉取最新 OpenAPI
      - name: Sync OpenAPI from Nacos
        run: |
          curl -s http://nacos:8848/nacos/v1/cs/configs \
            -d "dataId=ydsz-gateway-openapi.yaml&group=DEFAULT_GROUP" \
            -o docs/developer-portal/openapi/gateway.yaml
      
      # 2. 构建 Docusaurus 站点
      - name: Build Site
        run: |
          cd docs/developer-portal
          npm ci
          npm run build
      
      # 3. 部署到内部 Nginx / OSS / K8s
      - name: Deploy
        run: |
          kubectl apply -f deploy/k8s/dev-portal/
```

### Nacos 集成

```yaml
# 在 Nacos 中添加文档相关的配置
data-id: ydsz-dev-portal.yaml
group: DEFAULT_GROUP
content:
  # API 文档同步配置
  docs:
    api:
      sources:
        - gateway      # YDSZ API 网关聚合
        - user-service # ydz-user
        - project-service # ydz-project
        # ...
      refresh-interval: PT5M
    
    # 运维手册索引
    runbooks:
      path: /docs/runbooks
      search-enabled: true
    
    # 架构 ADR
    adr:
      path: /docs/architecture/adr
      template: /templates/adr.md
```

### ydz-agent 集成（智能搜索）

```java
// 将开发者门户的文档索引到 ydz-agent RAG
@Service
public class DevPortalIndexer {
    
    @Scheduled(cron = "0 0 2 * * ?")  // 每日凌晨 2 点全量索引
    public void reindex() {
        // 1. 扫描 docs/ 目录
        Path docsPath = Paths.get("/data/docs");
        
        // 2. 分块 + 向量化
        List<Document> docs = documentLoader.loadDocuments(docsPath);
        List<Document> chunks = textSplitter.apply(docs);
        
        // 3. 写入 pgvector
        embeddingStore.add(chunks);
    }
}
```

---

## 部署与运维

### Docker Compose 配置

```yaml
# docker-compose.dev-portal.yml
version: '3.8'

services:
  dev-portal:
    build: ./docs/developer-portal
    ports:
      - "3000:3000"
    environment:
      - NODE_ENV=production
      - SEARCH_API_URL=http://dev-portal-search:8080
      - NACOS_SERVER=nacos:8848
    depends_on:
      - dev-portal-search
      - nacos
    
  # 全文搜索引擎（基于 Meilisearch）
  dev-portal-search:
    image: getmeili/meilisearch:v1.7
    ports:
      - "7700:7700"
    volumes:
      - dev-portal-search-data:/meili_data
    environment:
      - MEILI_ENV=production
      - MEILI_MASTER_KEY=${SEARCH_API_KEY}

volumes:
  dev-portal-search-data:
```

### K8s 部署

```yaml
# deploy/k8s/dev-portal/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: dev-portal
  namespace: ydsz
spec:
  replicas: 2
  selector:
    matchLabels:
      app: dev-portal
  template:
    metadata:
      labels:
        app: dev-portal
    spec:
      containers:
        - name: docusaurus
          image: ydsz/dev-portal:${VERSION}
          ports:
            - containerPort: 3000
          resources:
            requests:
              cpu: 200m
              memory: 256Mi
            limits:
              cpu: 500m
              memory: 512Mi
```

### 监控指标

| 指标 | 类型 | 告警阈值 |
|------|------|----------|
| `devportal_http_requests_total` | Counter | - |
| `devportal_http_duration_seconds` | Histogram | P99 > 500ms |
| `devportal_search_latency` | Gauge | > 200ms |
| `devportal_docs_sync_total` | Counter | - |
| `devportal_docs_sync_errors` | Counter | > 0 |

---

## 实施路线图

### Phase 1：MVP（2 周）

- [ ] Docusaurus 基础站点搭建
- [ ] 现有 docs/ 目录导入
- [ ] 网关 OpenAPI 自动同步
- [ ] 基本权限控制（内网访问）
- [ ] Docker Compose 一键启动

### Phase 2：能力完善（4 周）

- [ ] ADR 架构决策记录模块
- [ ] 运维手册结构化 + 搜索
- [ ] 新人 Onboarding 路径
- [ ] CI/CD 自动更新集成
- [ ] 评论/反馈系统

### Phase 3：智能化（4 周）

- [ ] ydz-agent 集成（智能搜索 + 问答）
- [ ] 全文检索引擎接入（Meilisearch）
- [ ] 变更订阅通知
- [ ] 使用统计分析

### Phase 4：推广运营（持续）

- [ ] 部门级文档贡献激励
- [ ] 季度最佳文档评选
- [ ] 与代码评审联动（强制 ADR）
- [ ] API 版本变更自动通知

---

## 成本估算

| 项目 | 月度成本（元） | 说明 |
|------|---------------|------|
| 部署资源（K8s Pod） | ~500 | 2 副本 × 256MB |
| 搜索引擎 | ~200 | Meilisearch 1GB 索引 |
| 存储 | ~50 | 文档 + 图片 |
| **总计** | **~750** | |

---

## 成功指标

| 指标 | 当前 | 目标（3 月） | 目标（6 月） |
|------|------|--------------|--------------|
| 月均 PV | 0 | 5000 | 20000 |
| 文档覆盖率 | ~30% | 70% | 90% |
| 搜索使用率 | 0 | 每周 100 次 | 每周 500 次 |
| NPS | - | 7/10 | 8.5/10 |
| API 文档查询耗时 | 手动 5min | 在线 10s | 智能 3s |

---

> 文档更新: 2026-08-04 | 维护人: ydsz-team
