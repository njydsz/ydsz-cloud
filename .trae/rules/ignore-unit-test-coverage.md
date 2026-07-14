# 忽略单元测试覆盖率检查

> **项目工程规范（强制）** — 适用于 ydsz-pmis 整个仓库的 Java 后端 + 前端，不分模块、不分阶段。

## 规则定义

**ydsz-pmis 项目不把单元测试覆盖率（Unit Test Coverage）作为门禁 / 阻断性校验指标**。JaCoCo 插件的 `check` 目标（`jacoco-maven-plugin` 的 `check-coverage` 执行）**永远不参与 CI 卡点**，仅保留 `prepare-agent` + `report` 两步用于本地报告输出。

## 适用范围

- 所有 Java 后端模块（`ydsz-pmis-backend/ydsz-pmis-*`），包括 `api` / `domain` / `infra` / `server` / `web` 五层子模块。
- 所有前端模块（`ydsz-pmis-frontend`）的 Vue/TS 单元测试。
- 所有 CI 流水线（Jenkins / GitHub Actions / GitLab CI / 自研 pipeline）。
- 所有本地开发命令（`mvn verify`、`mvn test`、`mvn package`）。

## 原因

1. **项目当前未上线，仍处于开发阶段**：研发节奏优先于覆盖率指标，强制阈值会拖慢 P0/P1/P2 优化节奏。
2. **测试基础设施不完整**：各 `server` 子模块此前缺少 `spring-boot-starter-test` 测试依赖；覆盖率指标在「没补齐测试基建」之前没有意义。
3. **大厂标准中覆盖率是「参考指标」而非「门禁指标」**：真正决定线上质量的是契约测试、集成测试、灰度监控、混沌演练、Code Review，而不是单纯的 line/branch 覆盖率数字。
4. **避免「为测而测」反模式**：一旦把覆盖率作为门禁，会催生出大量无断言、无边界、无异常的占位单测，反而拉低代码可维护性。

## 实施约束

### 1. JaCoCo 配置保持现状

- `ydsz-pmis-backend/pom.xml` 中 `<skipJacocoCheck>true</skipJacocoCheck>` 保持为 `true`，**禁止改为 `false`**（除非产品/架构组明确重新开启）。
- `<jacoco.line.coverage>0.60</jacoco.line.coverage>` / `<jacoco.branch.coverage>0.50</jacoco.branch.coverage>` 阈值参数可保留但**仅作占位**。
- 允许保留 `prepare-agent`（采集）+ `report`（生成 `target/site/jacoco` 报告）两步，让开发者本地 `mvn verify` 仍能看到覆盖率走势；**只是不把阈值作为门禁**。

### 2. CI 脚本中禁止启用覆盖率检查

禁止在任何 CI / 部署脚本中添加 `-DskipJacocoCheck=false` 或类似参数：

- `deploy/scripts/*.sh`
- `deploy/k8s/*.yaml`（pipeline / Jenkins Agent / Tekton Task 配置段）
- `deploy/helm/ydsz-pmis/templates/*.yaml`（helm hook、Argo Workflow pipeline 配置）
- `Jenkinsfile` / `.github/workflows/*.yml` / `.gitlab-ci.yml`
- 任何 `Makefile` / `task` / `nx` / `turbo` 中的 coverage check 步骤

### 3. 代码审查 / AI 辅助生成

- **不得**因「单测覆盖率不足」打回 PR 或拒绝合入。
- **不得**为了凑覆盖率而批量生成低质量单测（无断言、无边界、无异常的「为测而测」代码）。
- AI 生成代码时，**不要**主动建议「补齐单测至 X% 覆盖率」，**不要**主动生成仅为通过覆盖率阈值的占位测试。
- 如确需生成测试，仅在以下场景生成：① 修复 P0/P1 缺陷时附回归用例；② 关键算法（规则引擎、计费、EVM、利润分摊等）核心方法；③ 公共工具类（util / helper）的边界条件。

### 4. 真正需要守住的质量底线

覆盖率不是唯一指标，**取代覆盖率作为质量门禁的是以下四点**：

1. **核心业务链路必须具备端到端冒烟用例**：见 `deploy/scripts/smoke-*`。
2. **P0 / P1 修复必须附回归用例**（单元或集成测试），单文件可附在 PR 内，跨文件改动放 `*IntegrationTest.java`。
3. **关键算法的核心方法仍鼓励写单测**，但不强制覆盖率：规则引擎 / 计费 / EVM / 利润分摊 / 分布式锁 / 幂等 / 通知回执 / 审计事件等。
4. **Code Review + 静态检查 + 混沌演练**作为真正的质量守门员：见 `.trae/rules/` 下的 FQN 规则、`@SuppressWarnings` 规则、Chaos Mesh 实验配置。

## 例外

如未来产品 / 架构组**明确**重新启用覆盖率门禁，需同时满足以下条件并更新本规则文件：

1. 各 `server` 子模块已补齐 `spring-boot-starter-test` 测试依赖（参考 `ydsz-pmis-message` 等已完成补齐的模块）。
2. CI 基础设施具备 JaCoCo merge / SonarQube 集成能力，能跨模块聚合报告。
3. 已在团队周会 / 文档公告，并更新 `ydsz-pmis-backend/pom.xml` 注释 + 本规则文件。

## 执行机制

- **IDE 规则**：Trae / CatPaw 规则文件 `alwaysApply: true`，AI 代码生成与审查阶段自动遵守。
- **Code Review**：PR 审查中如发现由覆盖率阈值阻断 / 批量低质量单测 / `-DskipJacocoCheck=false` CI 启用等情况，即打回。
- **CI 检测**：可选在 `deploy/scripts/check-quality-gate.sh` 中检测 `mvn verify` 命令是否包含 `-DskipJacocoCheck=false`，命中则阻断。

## 相关文件

- [ydsz-pmis-backend/pom.xml](file:///d:/Code/ydsz/ydsz-pmis/ydsz-pmis-backend/pom.xml#L118-L122) — JaCoCo 属性定义
- [ydsz-pmis-backend/pom.xml](file:///d:/Code/ydsz/ydsz-pmis/ydsz-pmis-backend/pom.xml#L1325-L1379) — JaCoCo `check-coverage` execution
- [ydsz-pmis-backend/pom.xml](file:///d:/Code/ydsz/ydsz-pmis/ydsz-pmis-backend/pom.xml#L1406-L1413) — JaCoCo 默认启用（仅 prepare-agent + report）
- 项目记忆：c:\Users\Marvin\.trae-cn\memory\projects\-d-Code-ydsz-ydsz-pmis\project_memory.md → Hard Constraints → "整个项目忽略单元测试覆盖率检查"
