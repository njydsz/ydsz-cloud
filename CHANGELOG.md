# 变更日志

本项目变更日志遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/) 规范，
版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [Unreleased] - 2026-07-11

### Added
- 新增 ydsz-pmis-sales / ydsz-pmis-execution / ydsz-pmis-finance 三个微服务模块骨架（project 模块拆分）
- 新增 common 模块单元测试：PageQueryTest / PageResultTest / SensitiveSerializerTest / MultiLevelCacheServiceTest / BloomFilterServiceTest（共 146 个测试用例）
- 新增 PostgreSQL 慢查询防护配置（statement_timeout=30s, log_min_duration_statement=3000ms）
- 新增 K8s Deployment securityContext + startupProbe 配置
- 新增前端 a11y 适配：Skip to main content 链接 + prefers-reduced-motion 适配
- 新增前端错误页品牌插画与快捷入口

### Changed
- 优化 JaCoCo 覆盖率门禁为分阶段提升策略（当前 LINE>=30%, 目标 LINE>=80%）
- 优化 Checkstyle 严格级别从 warning 改为 error，新增 MethodLength/CyclomaticComplexity/ParameterNumber 规则
- 优化 CI/CD 流水线：CD 依赖 CI 成功才触发构建（workflow_run + if 条件）
- 优化 viewModules 路径映射：修复 8 处路径错位，补全 30+ 缺失条目
- 优化工时填报交互：submitForm 增加 submitting 防重复提交，approverId 取当前登录用户
- 优化合同管理/工作流设计器 i18n：硬编码中文迁移到 locales
- 优化 Lighthouse LCP 阈值从 4000ms 收紧至 2500ms
- 优化 Spring Boot 版本号统一为 4.1.0（README badge 与 pom.xml 一致）

### Fixed
- 修复 OpportunityController/UserController update 方法缺少 {id} 路径参数
- 修复 ContractController 状态迁移使用 PUT 改为 PATCH /{id}/status
- 修复 FlowTaskController.taskDetail 缺少 @PrePermission 注解
- 修复 InvoiceServiceImpl/PaymentServiceImpl/EmployeeServiceImpl page 方法缺少 @DataScope 注解
- 修复 EmployeeDO.email/emergencyPhone、WarrantyDO.contactPhone、OpsTicketDO.reporterPhone 缺少 @Sensitive 注解
- 修复 Prometheus 告警规则 namespace 硬编码 pmis-production 改为正则 pmis.*
- 修复 README 断裂文档链接

## [1.0.0-SNAPSHOT] - 2026-07-10

### 批次交付总览

基于 README「八、批次交付总览」提取，保留最近 5 个批次（24-28）：

| 批次 | 主题 | 关键交付 |
|---|---|---|
| 24 | chaos-dashboard | 前端 4 KPI + 2 ECharts + 实验 CRUD + Dry-Run + 5s 轮询 |
| 25 | 售后管理 | 质保期 + 运维工单 P1-P4 SLA + 满意度 9 测试类 |
| 26 | v1.1 优化 | Seata + WebSocket + CI 门禁 + ES + 文件增强 + 报表 + 批量操作等 24 项 |
| 27 | v1.2 优化 | Sentry 接入 + Redis 配置补全 + BFF + 工作流事件联动 + EasyExcel + ErrorBoundary + 暗黑模式 + 限流启用 + RocketMQ + i18n 6 页面 + PWA 等 |
| 28 | v1.3 国际化与代码优化 | i18n 基础设施 + 中英文语言包 + 多模块重构 + literule 计算类迁移 + Nacos 分组统一 + 端口对齐 |

**当前状态**：批次 28 已完成；下一阶段（批次 29+）规划等保测评 / 多租户改造，按业务节奏启动。
