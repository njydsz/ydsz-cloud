# YDSZ PMIS · 项目运营管理系统

> 南京云顶数字科技有限公司 · 软件定制 + 人力外包 双业态 · 业财一体化精细化运营平台
>
> **当前版本**: `v1.3.0-SNAPSHOT` · **最近更新**: 2026-07-03 · **构建状态**: 后端 7 服务 + 2 库 `mvn test` 全部通过 / 前端 `vitest` 54 文件 / `vue-tsc` 0 错

[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0.7-6DB33F?logo=springboot)]()
[![Spring Cloud](https://img.shields.io/badge/Spring%20Cloud-2025.1.1-6DB33F?logo=spring)]()
[![SCA](https://img.shields.io/badge/Spring%20Cloud%20Alibaba-2025.1.0.0-FF6A00)]()
[![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk)]()
[![Vue](https://img.shields.io/badge/Vue-3.5-4FC08D?logo=vuedotjs)]()
[![TS](https://img.shields.io/badge/TypeScript-5.x-3178C6?logo=typescript)]()
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-18-336791?logo=postgresql)]()
[![Redis](https://img.shields.io/badge/Redis-7-DC382D?logo=redis)]()
[![License](https://img.shields.io/badge/license-Proprietary-red)]()

---

## 目录

- [一、一句话定位](#一一句话定位)
- [二、核心数据](#二核心数据)
- [三、核心特性](#三核心特性)
- [四、技术架构](#四技术架构)
- [五、快速开始](#五快速开始)
- [六、仓库结构](#六仓库结构)
- [七、核心业务规则速查](#七核心业务规则速查)
- [八、批次交付总览](#八批次交付总览)
- [九、质量与可观测性](#九质量与可观测性)
- [十、文档导航](#十文档导航)
- [十一、团队](#十一团队)
- [十二、版本与许可](#十二版本与许可)

---

## 一、一句话定位

以 **WBS 业财一体化锚点** 为底座，以 **EVM 挣值管理** 为预警引擎，以 **双费率（对外报价 + 对内成本）** 为利润核算基础，串联「**商机 → 立项 → 合同 → 执行 → 回款 → 结项 → 售后**」全生命周期的项目运营管理系统。

对标华为 IPD 阶段评审 + 用友 BIP 业财一体化 + 飞书项目组合 + Wrike 专业服务交付 + 集客云 PSA 等大厂标准，提炼差异化的业财一体化能力。

## 二、核心数据

| 维度 | 数字 | 说明 |
|---|---|---|
| 后端微服务 | **9 模块（7 部署 + 2 库）** | gateway / iam / workflow / project / agent / system / scheduler（部署）+ common / literule（库）+ 父 pom |
| 后端 Java 源文件 | **870+** | 业务代码 + DTO/VO/Mapper/Test |
| 后端测试 | **229 测试类 / 1500+ 用例** | `mvn test` BUILD SUCCESS 跨 9 模块 |
| 前端页面 | **57 个** | 业务页面 + 设计器 + 监控中心 |
| 前端测试 | **54 文件 / 470+ 用例** | vitest 单元 + 组件 + Playwright 4 E2E |
| Controller 数 | **80+** | project 41 + iam 15 + workflow 3 + 其余 21+ |
| 业务 Service | **130+** | project 38 + iam 29 + agent 12 + workflow 8 + 其余 40+ |
| 业务枚举 | **80+** | 状态机 / 业务码 / 审批流 / 编排模式 |
| 权限码 | **240+** | 前后端 `PermissionCodes` 一一对应 |
| 状态机 | **35+** | 覆盖商机 / 立项 / 合同 / 变更 / 发票 / 回款 / 工单 / 售后等 |
| 计算引擎 | **15+** | EVM / 双费率 / 信用 / 风险 / 阶段门径 / 准入 / 模拟 / 规则链 / ... |
| AI Agent | **5 + 4 编排** | RiskWarning / ResourceRecommend / ProfitForecast / WinRatePredict / TimesheetAnomaly · 4 策略（SEQUENTIAL/PARALLEL/VOTING/CASCADE） |
| LLM Provider | **5** | Mock / DashScope（通义千问）/ Qianfan（文心）/ SpringAI / LlmProviderRouter |
| SQL Flyway 脚本 | **43 个** | V1.0.0_001 ~ V1.0.0_041（批次 28 增） |
| 聚合 SQL 视图 | **5 张** | `pmis_view_initiation_revenue_cost` / `pmis_view_initiation_evm` / `pmis_view_cockpit_overview` / `pmis_view_risk_dashboard` / `pmis_view_employee_utilization` |
| 批次交付 | **28 批次** | 批次 1-28 已完成 · 等保测评 / 多租户改造 待评估 |

## 三、核心特性

### 3.1 业务能力

| 模块 | 关键能力 |
|---|---|
| **项目全生命周期** | 商机 A/B/C 分级 → 立项（WBS 预算） → 合同（模板/补充/变更/风险） → 执行（WBS/工时/采购/费用） → 成本归集 → 收入确认（终验/里程碑/月） → 利润核算 → 开票/回款 → 变更管理 → 项目结项 → 售后（质保/工单/满意度） |
| **EVM 挣值管理** | PV/EV/AC 三量 + CPI/SPI 偏差指数 + EAC/VAC 预测 + 红/黄/绿阈值告警 + 5 阶段聚合视图 |
| **双费率利润** | 对外 Rate Card（职级 × 技术栈 × 客户三元组） + 对内 RateInternal（职级 × 部门） + 双率对比 + 多版本模拟 |
| **资源池与 Bench**