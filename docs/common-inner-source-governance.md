# Common 模块内源治理指南（Inner Source Governance）

> **文档版本**：1.0.0
> **生效日期**：2026-09-25
> **适用范围**：ydsz-cloud 全部 ydzz-common-* 子模块及相关业务模块
> **关联规范**：《云顶编码规范》§22.5（common 模块能力复用规范）、§23.4（模块拆分约束）、§23.5（业务模块优先使用 common 模块能力）
> **配套工具**：`scripts/check-common-reuse.py`、`docs/capability-matrix.md`、`docs/common-module-usage-analysis.md`

---

## 1. 概述与目标

### 1.1 战略定位

`ydsz-common` 是 ydsz-cloud 平台的**公共能力基座**，采用严格分层架构（L1 工具层 → L2 基础层 → L4 数据层 → L5 服务层 → L6 应用层），覆盖 30+ 个子模块、100+ SPI 扩展点，服务 11 个业务模块。其战略定位是"平台能力统一供给中心"——所有跨模块通用的技术能力、横切关注点、基础设施抽象，均由 common 模块统一封装并提供标准化 API，业务模块通过 Maven 依赖引用而无需自行实现。

### 1.2 内源模式目标

本规范引入 **Inner Source（内源）** 模式，将开源社区成熟的协作实践（CODEOWNER、PR 评审、SemVer、CHANGELOG、架构守护等）应用到 common 模块的治理中。核心目标如下：

1. **减少重复造轮子**：杜绝业务模块自行实现已被 common 封装的能力（§22.5.3 底线要求）。通过 `check-common-reuse.py` 静态检测 + CI 卡点，将复用率从当前基线 85% 提升至 95% 以上。
2. **提升复用率与质量**：common 模块的能力经过多业务场景验证，质量和稳定性优于各模块自建的一次性实现。统一的封装也降低了下游学习成本和接入风险。
3. **可持续性演进**：通过清晰的贡献流程、契约测试保护和半年复审机制，确保 common 模块健康演进——能力持续沉淀、债务及时清理、接口兼容可控。

### 1.3 内源与传统共有的差异

| 维度 | 传统共有库 | 内源模式（本文定义） |
|------|-----------|-------------------|
| 协作权限 | 仅平台团队可修改 | 任何开发者均可提交 PR |
| 变更评审 | 负责人一言堂 | CODEOWNER + ARB 双重评审 |
| 版本管理 | 跟随主仓库发布 | 子模块独立 SemVer + CHANGELOG |
| 兼容性 | 口头承诺 | ArchUnit 守护 + 契约测试 |
| 反馈渠道 | 线下沟通 | Issue 模板 + 报备机制 + 半年复审 |

---

## 2. 角色与职责矩阵

| 角色 | 成员构成 | 核心职责 | 决策权限 |
|------|---------|---------|---------|
| **平台团队（Common 维护者）** | common 模块 CODEOWNER（见第 4 节） | 变更评审、API 兼容性承诺、CHANGELOG 维护、SemVer 发布、CI 卡点配置 | 日常 PR 合并权、minor/patch 版本发布权 |
| **业务开发者** | 全部 ydsz-* 业务模块开发者 | 使用 common 能力、提交 enhancement PR、能力不足时提 Issue、参与半年复审 | PR 提交权、Issue 发起权、方案建议权 |
| **架构评审委员会（ARB）** | 技术负责人 + 高级架构师（≥3 人） | 架构变更（ACC）审批、重大 API 破坏性变更评审、特殊豁免终审 | ACC 审批权、major 版本发布审批权 |

### 2.1 平台团队详细职责

- **CODEOWNER**：每个 `ydzs-common-*` 子模块指定 1-2 名 CODEOWNER，负责该模块的 PR 评审与合并。CODEOWNER 请假时需提前在 `.github/CODEOWNERS` 中指定代理人。
- **变更评审**：PR 评审重点关注以下方面：
  - 是否符合分层约束（L1 不含业务依赖，L2+ 禁止反向依赖）。
  - 是否引入不必要的新依赖（优先复用已有第三方库）。
  - 是否包含完整单元测试（覆盖率 ≥ 现有基线）。
  - 是否同步更新 CHANGELOG 与 Javadoc。
  - 是否影响下游模块已声明的 Spring Boot 自动装配。
- **兼容性承诺**：遵循 SemVer 规范——patch 版本保证二进制兼容，minor 版本保证源码兼容，major 版本可引入破坏性变更但需提供迁移指南。

### 2.2 业务开发者详细职责

- **使用者**：优先查阅 `docs/capability-matrix.md` 确认所需能力是否已被 common 覆盖，使用前阅读对应 README 和 architecture-rules。
- **贡献者**：当发现 common 缺陷或缺失通用能力时，按照第 3 节流程提交 PR。
- **反馈者**：在 `check-common-reuse.py` 发现违规时积极配合整改；参与半年复审时提供自建模块的通用性证据。

### 2.3 ARB（架构评审委员会）详细职责

- 审批新子模块创建请求（ACC 流程，§23.4）。
- 审批重大 API 破坏性变更（如删除/重命名公共类或方法）。
- 终审特殊豁免申请（§3.2报备机制）。
- 每半年审查 common 模块健康度报告，决策模块归档/拆分/合并。

---

## 3. 开发流程

### 3.1 能力查找流程

业务模块开发者在实现任何通用能力前，**必须**执行以下查找流程：

```
┌──────────────────────────────────────────────────────────┐
│  Step 1: 查阅 docs/capability-matrix.md                   │
│          确认能力域 -> 对应 common 核心 API               │
│                         ↓                                 │
│  Step 2: 查阅 docs/capability-matrix.md                   │
│          确认能力域 -> 对应 SPI 是否可覆盖差异化需求       │
│                         ↓                                 │
│  Step 3: 在体系内 Git "搜一搜"                             │
│          grep "capability-name"                          │
│                         ↓                                 │
│  Step 4: 向 common 提 Issue                               │
│          说明场景 + 能力缺口                              │
│                         ↓                                 │
│  Step 5: 评估确认"必须自建"                               │
│          遵循 §3.3 报备机制                                │
└──────────────────────────────────────────────────────────┘
```

**Pre-PR 自查清单**（提交 PR 前逐项确认）：

- [ ] 本次实现的功能是否已有 common 模块提供能力？（参考 §20.5.5）
- [ ] `pom.xml` 中新增的依赖是否真的在本模块代码中被实际引用？
- [ ] 如引用了 common 模块，是否使用的是最新版本而非过时 API？
- [ ] Javadoc 中引用的 common 类/接口是否真实存在？
- [ ] 是否自建了通用工具而非首先在 common 中寻找或补充能力？
- [ ] 如必须自建，是否在 PR 描述中写明原因和对应的 common issue 链接？

### 3.2 贡献提交流程

```
Fork / 分支  →  编码  →  单测  →  PR 描述模板  →  评审  →  合并
```

**Step 1：分支命名**：

- 功能分支：`feature/common-<子模块>[-功能简述]`
- 修复分支：`fix/common-<子模块>[-问题简述]`
- 示例：`feature/common-lock-redlock`、`fix/common-redis-npe-guard`

**Step 2：编码规范**：

- 严格遵守《云顶编码规范》全文，尤其是 §23.4 模块拆分约束。
- 新增公共方法必须包含完整 Javadoc（`@param`/`@return`/`@throws` 三要素齐全）。
- SPI 接口需标记 `@FunctionalInterface` 并在 Javadoc 中说明扩展契约。

**Step 3：单测要求**：

- 新增代码单元测试覆盖率 ≥ 80%，核心路径（快乐路径 + 边界 + 异常）全覆盖。
- 涉及自动装配的代码需编写 `@SpringBootTest` 集成测试。
- 兼容性变更需新增契约测试用例（见第 6 节）。

**Step 4：PR 描述模板**：

```markdown
## 变更类型
<!-- 请勾选 -->
- [ ] Feature（新增能力，minor 版本 +1）
- [ ] Bugfix（缺陷修复，patch 版本 +1）
- [ ] Refactor（重构，patch 版本 +1）
- [ ] Breaking（破坏性变更，major 版本 +1）
- [ ] Docs（文档/注释更新）

## 变更范围
<!-- 涉及的子模块 -->
- ydzs-common-xxx

## 变更描述
<!-- 清晰描述动机、方案、影响 -->

## 自查清单
- [ ] 单元测试已通过（`mvn test -pl ydzs-common-xxx`）
- [ ] Checkstyle 已通过（`mvn checkstyle:check`）
- [ ] CHANGELOG 已更新
- [ ] 无新增 ArchUnit 违规

## 下游影响
<!-- 对使用者的影响及迁移指引 -->

## 关联 Issue
Closes #xxx
```

**Step 5：评审规则**：

- 至少 1 名 CODEOWNER approve 后方可合并。
- 涉及破坏性变更需 ARB 审批 + 全量下游通知。
- 评审周期：普通 PR ≤ 2 工作日，hotfix PR ≤ 1 小时。

**Step 6：合并与发布**：

- 合并采用 **Squash Merge**，commit message 作为 CHANGELOG 自动采集源。
- 平台团队按统一节奏发布版本，发布后自动创建下游升级 PR（见第 6.3 节）。

### 3.3 报备机制

如评估后确认**必须在业务模块自建**（极特殊情况，参考 §20.5.3），需满足以下条件：

1. **提交 common Issue**：在 ydsz-cloud 仓库提交 `capability-request` 类型 Issue，说明业务场景、能力缺口、期望接入时间、重复实现的范围。
2. **PR 描述中明确声明**：业务模块的 PR 描述模板中必须包含以下声明块。
3. **半年复审承诺**：自建能力纳入半年复审清单，达到通用阈值（≥3 个模块使用）后必须迁移至 common。

报备声明块模板：

```markdown
## 自建报备声明
- **common Issue 链接**：#xxx
- **自建理由**：ydsz-common-xxx 的 DistributedLocker 无 RLock WatchDog 选举语义，
  且 common 团队评估后认为短期内无法提供等价能力（Issue #xxx 方案讨论结论）。
- **自建范围**：仅限 ydsz-cronjob 的 RedissonLeaderElector 一个类。
- **预期迁移时间**：待 common-lock 补充 LeaderElector 封装后立即迁移。
- **P 级**：P1-2
```

已闭环的报备案例：

| 模块 | 自建能力 | 报备结论 | 后续计划 |
|------|---------|---------|---------|
|ydsz-cronjob | RedissonLeaderElector (RLock 直用) | 批准，common-lock 无等价语义 | 待 common-lock 补充后迁移 |
|ydsz-gateway | IpAccessControlFilter (响应式版) | 批准，common-safe 为阻塞式 | 待 common-safe 响应式扩展 |

### 3.4 SemVer 版本管理策略

common 模块版本号遵循 **语义化版本 2.0.0**（SemVer）规范，格式为 `MAJOR.MINOR.PATCH`：

| 版本位 | 含义 | 何时递增 | 兼容性承诺 |
|--------|------|---------|-----------|
| **MAJOR** | 主版本 | 引入破坏性 API 变更（删除/重命名公共类、修改方法签名、移除自动装配） | 不兼容，需提供迁移指南 |
| **MINOR** | 次版本 | 新增向后兼容的功能（新 API、新 SPI 实现、新自动装配） | 源码兼容，二进制兼容 |
| **PATCH** | 补丁版本 | Bug 修复、性能优化、安全补丁（不改变公共 API 行为） | 完全兼容 |

**版本号示例与演进**：

```
1.0.0  →  首个 GA 版本
1.1.0  →  新增 RedisHashSetOps（新功能，兼容）
1.1.1  →  修复 RedisHashSetOps 空值处理 Bug
2.0.0  →  移除已废弃的 RedisService 门面类（破坏性变更）
```

**与 CalVer 的关系**：

common 模块内部版本遵循 SemVer，发布到 Maven 仓库时使用 SemVer + CalVer 双标签：

```xml
<!-- pom.xml 发布版本 -->
<version>1.2.3</version>

<!-- Git Tag（双标签） -->
git tag -a v1.2.3          <!-- SemVer 标签 -->
git tag -a 26.09.25        <!-- CalVer 标签（与项目发布日对齐） -->
```

**预发布版本**：

| 标签 | 含义 | 示例 |
|------|------|------|
| `-alpha.N` | 内部测试阶段 | `2.0.0-alpha.1` |
| `-beta.N` | 功能冻结，进入公开验证 | `2.0.0-beta.1` |
| `-rc.N` | 候选发布版本，仅修复 Blocker | `2.0.0-rc.1` |

---

## 4. CODEOWNERS 模板

在 `.github/CODEOWNERS` 中为每个 `ydsz-common-*` 子模块指定 CODEOWNER：

```ini
# ydzs-cloud CODEOWNERS
# 格式：<路径模式> @<用户或团队>
# CODEOWNER 负责该路径下的 PR 评审与合并

# ============================================================
# L1 工具层
# ============================================================
/ydsz-common/ydsz-common-json/       @platform-lead @json-expert
/ydsz-common/ydsz-common-cache/      @platform-lead @cache-expert
/ydsz-common/ydsz-common-excel/      @platform-lead @excel-expert
/ydsz-common/ydsz-common-util/       @platform-lead

# ============================================================
# L2-L3 基础层
# ============================================================
/ydsz-common/ydsz-common-core/       @platform-lead
/ydsz-common/ydsz-common-locales/    @platform-lead @i18n-expert
/ydsz-common/ydsz-common-domain/     @platform-lead
/ydsz-common/ydsz-common-exception/  @platform-lead

# ============================================================
# L4 数据层
# ============================================================
/ydsz-common/ydsz-common-jdbc/       @platform-lead @db-expert
/ydsz-common/ydsz-common-redis/      @platform-lead @redis-expert
/ydsz-common/ydsz-common-lock/       @platform-lead @lock-expert
/ydsz-common/ydsz-common-thread/     @platform-lead
/ydsz-common/ydsz-common-tenant/     @platform-lead @tenant-expert

# ============================================================
# L5 服务层
# ============================================================
/ydsz-common/ydsz-common-auth/       @platform-lead @auth-expert
/ydsz-common/ydsz-common-safe/       @platform-lead @security-expert
/ydsz-common/ydsz-common-feign/      @platform-lead @feign-expert
/ydsz-common/ydsz-common-audit/      @platform-lead @audit-expert
/ydsz-common/ydsz-common-notify/     @platform-lead @notify-expert
/ydsz-common/ydsz-common-socket/     @platform-lead @socket-expert
/ydsz-common/ydsz-common-queue/      @platform-lead @mq-expert
/ydsz-common/ydsz-common-event/      @platform-lead @event-expert
/ydsz-common/ydsz-common-search/     @platform-lead @search-expert
/ydsz-common/ydsz-common-file/       @platform-lead @file-expert
/ydsz-common/ydsz-common-netty/      @platform-lead @netty-expert
/ydsz-common/ydsz-common-sentry/     @platform-lead @obs-expert
/ydsz-common/ydsz-common-config/     @platform-lead @config-expert

# ============================================================
# L6 应用层
# ============================================================
/ydsz-common/ydsz-common-base/       @platform-lead
/ydsz-common/ydsz-common-app/        @platform-lead
/ydsz-common/ydsz-common-web/        @platform-lead

# ============================================================
# 文档与脚本
# ============================================================
/docs/capability-matrix.md          @platform-lead @doc-writer
/scripts/check-common-reuse.py       @platform-lead @qa-lead
```

**CODEOWNER 管理规则**：

1. 每个子模块必须指定 ≥1 名主 CODEOWNER + ≥1 名备选 CODEOWNER（交叉 backup）。
2. CODEOWNER 请假超过 3 工作日时需在 CODEOWNERS 中临时委托代理。
3. CODEOWNER 名单每季度由 ARB 复审一次，确保与实际在岗人员一致。

---

## 5. CHANGELOG 要求

每个 `ydsz-common-*` 子模块的 release 必须在模块根目录包含 `CHANGELOG.md` 文件。

### 5.1 文件格式

采用 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/) 标准格式：

```markdown
# ydzz-common-redis 变更日志

本模块的所有显著变更均记录在本文件中。

格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [Unreleased]

### Added
- 新增 `RedisHashOps.hcompareAndSet()` 原子cas操作

### Changed
- `RedisStringOps.setIfAbsent()` 内部实现由 SETNX 切换为 SET NX PX 原子命令

### Fixed
- 修复 `RedissonClientFactory` 非集群模式下哨兵配置泄露问题（#142）

---

## [1.2.0] - 2026-09-20

### Added
- 新增 `RedisGeoOps` 地理空间操作组件（GEOADD / GEORADIUS / GEODIST）
- 新增 `RedisStreamOps.xclaimAuto()` 自动认领Pending Entry便捷方法

### Changed
- `RedisAutoConfiguration` 支持 `ydsz.redis.customizer-bean-name` 自定义回调（#128）

### Deprecated
- `RedisService.hashKeys()` 标记为 `@Deprecated`，请迁移至 `RedisHashOps.keys()`（#135）

### Security
- 升级 Redisson 至 3.37.1，修复 CVE-2026-XXXX 反序列化风险

---

## [1.1.0] - 2026-08-15

### Added
- 初始 GA 版本，提供 String/Hash/Set/ZSet/List 五类原子操作封装
- 集成 Redisson 看门狗自动续约
```

### 5.2 分类说明

| 分类 | 含义 | 对应的 SemVer 递增 | 必填说明 |
|------|------|-------------------|---------|
| `Added` | 新方法、新类、新 SPI、新自动装配 | minor | 说明能力入口和典型使用场景 |
| `Changed` | 已有功能的行为变更（非 Breaking） | minor/patch | 对比更改前后差异 |
| `Fixed` | Bug 修复 | patch | 附 Issue 编号和根因 |
| `Removed` | 移除已废弃的功能 | major | 标注废弃起始版本和迁移指引 |
| `Deprecated` | 标记即将移除的功能 | minor | 标注迁移方案和目标移除版本 |
| `Security` | 安全补丁/漏洞修复 | patch | 附 CVE 编号或安全公告 |
| `Performance` | 性能优化（吞吐量/延迟/内存） | patch | 标注 benchmark 数据 |

### 5.3 自动化采集

使用 `.github/workflows/changelog-release.yml` 在 release 时自动将 `[Unreleased]` 内容切割为新版本条目，并验证格式：

```yaml
# .github/workflows/changelog-release.yml 片段
- name: Verify CHANGELOG updated
  run: |
    for module in ydzs-common/*/; do
      if [ -f "$module/CHANGELOG.md" ]; then
        # 验证 [Unreleased] 段非空（有变动但未发布的情况可豁免）
        if grep -q "## \[Unreleased\]" "$module/CHANGELOG.md"; then
          echo "✓ $module CHANGELOG 格式合规"
        fi
      else
        echo "✗ $module 缺少 CHANGELOG.md"
        exit 1
      fi
    done
```

---

## 6. 契约测试策略

### 6.1 公共模块接口的兼容性保证

common 模块公共 API 的兼容性是下游业务模块稳定运行的基石。定义如下兼容性等级：

| 等级 | 定义 | 保证级别 | 检测方式 |
|------|------|---------|---------|
| **Stable** | 已被 ≥2 个业务模块引用的公共接口 | MAJOR 版本内不得破坏 | ArchUnit + 契约测试 |
| **Beta** | 发布不超过 3 个月的全新接口 | MINOR 版本内不得破坏 | 契约测试 |
| **Experimental** | 明确标注 `@Experimental` 的接口 | 无承诺，随时可移除 | 注释标注 |
| **Internal** | 包可见性为 `internal` 的接口 | 不构成公共契约 | 包可见性约束 |

### 6.2 ArchUnit 守护规则示例

与现有 `ydsz-common-redis` 架构规则（`docs/architecture-rules.md`）风格对齐，每个子模块的 `src/test/` 应包含对应的 ArchUnit 测试类：

```java
package com.njydsz.common.lock.arch;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.junit5.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * ydzs-common-lock 架构守护规则
 * 对齐《云顶编码规范》§23.4 模块拆分约束
 */
public class LockArchitectureRules {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .importPackages("com.njydsz.common.lock");

    /**
     * LOCK-001: L1 工具层禁止依赖业务层模块（反向依赖检测）
     */
    @ArchTest
    static final ArchRule NO_REVERSE_DEPENDENCY_ON_BIZ_MODULES =
        noClasses()
            .that().resideInAPackage("..lock..")
            .should().dependOnClassesThat()
            .resideInAPackage("..com.njydsz.biz..")
            .because("ydsz-common-lock（L4层）禁止反向依赖业务模块.");

    /**
     * LOCK-002: 模块间禁止循环依赖
     */
    @ArchTest
    static final ArchRule NO_CIRCULAR_DEPENDENCIES =
        slices().matching("com.njydsz.common.lock.(*)..")
            .should().beFreeOfCycles()
            .because("ydsz-common-lock 子包之间禁止循环依赖.");

    /**
     * LOCK-003: DistributedLocker 公开接口必须来自 core 包
     */
    @ArchTest
    static final ArchRule PUBLIC_API_MUST_RESIDE_IN_CORE =
        classes().that().arePublic()
            .and().haveSimpleNameEndingWith("Locker")
            .should().resideInAPackage("..core..")
            .because("分布式锁的公共 API 必须收敛在 core 包.");

    /**
     * LOCK-004: Spring 集成代码不得独立成 *-spring 子模块（§23.4 约束）
     */
    @ArchTest
    static final ArchRule NO_SPRING_SUBMODULE =
        noClasses()
            .should().resideInAPackage("..lock.spring..")
            .because("禁止拆分 *-spring 子模块，Spring 代码应内联在原模块（§23.4）.");
}
```

### 6.3 变更后下游通知（自动创建升级 PR）

当 common 子模块发布 minor/patch 新版本时，CI 自动向下游业务模块创建升级 PR：

```yaml
# .github/workflows/common-downstream-bump.yml 名称：下游版本自动升级
on:
  push:
    tags:
      - 'v*'    # 监听 SemVer Tags

jobs:
  bump-downstream:
    runs-on: ubuntu-latest
    strategy:
      matrix:
        downstream: [ydsz-system, ydsz-message, ydsz-workflow, ydsz-agent, ydsz-gateway, ydsz-cronjob, ydsz-userinfo, ydsz-nextwiki, ydsz-literule]
    steps:
      - uses: actions/checkout@v4
      - name: Upgrade common dependency in ${{ matrix.downstream }}
        run: |
          # 更新 pom.xml 中对应 common 子模块版本
          mvn versions:set-property \
            -Dproperty=ydsz-common-redis.version \
            -DnewVersion=${{ github.ref_name }} \
            -pl ${{ matrix.downstream }}
      - name: Create Pull Request
        uses: peter-evans/create-pull-request@v6
        with:
          branch: "chore/bump-common-redis-${{ github.ref_name }}-${{ matrix.downstream }}"
          title: "[deps] 升级 ydzz-common-redis 至 ${{ github.ref_name }}（自动 PR）"
          body: |
            ## 自动依赖升级
            上游 `ydsz-common-redis` 发布新版本 ${{ github.ref_name }}，请检查 CHANGELOG 后合并。
            
            **本 PR 由 CI 自动生成**，常规 patch 升级可直接合并。
            
            ---
            PR 评审人：${{ matrix.downstream }} 模块负责人
```

---

## 7. 半年复审机制

### 7.1 复审范围与周期

平台团队每 **6 个月** 对以下内容执行一次系统复审：

1. **业务模块自建的非通用能力**：检查自建能力是否已达到通用阈值（≥3 个模块使用同款自建能力）。
2. **common 模块健康度**：模块间引用矩阵、僵尸依赖、废弃 API 迁移进度、测试覆盖率趋势。
3. **SPI 扩展点利用率**：评估各 SPI 的实际使用频率，删除 0 使用的 SPI。

### 7.2 通用阈值判定标准

满足以下**任一**条件即触发通用化迁移：

| 条件 | 阈值 | 证据来源 |
|------|------|---------|
| 同类自建能力被 ≥3 个业务模块出现 | ≥3 | `check-common-reuse.py` E1 规则命中数 |
| 自建能力涉及安全/数据一致等横切关注点 | — | 安全扫描或架构评审认定 |
| 自建实现存在重复 Bug/缺陷 ≥2 次 | ≥2 | Issue 追踪系统中同类缺陷数 |
| 业务模块主动请求迁移至 common | 1 | Issue 标签 `migrate-to-common` |

### 7.3 迁移流程

```
 复审报告业务模块自建能力
         ↓
 能力代码通用性审查
 ├── 高度通用（≥3模块需要）  →  平台团队移入 common，year = 1 模块使用
 ├── 领域专属（≤2模块且有差异） →  沉淀为领域公共模块（ydsz-***-share）
 └── 单一业务专属             →  维持现状，复审结束
```

### 7.4 半年复审报告模板

```markdown
# Common 模块半年复审报告

## 基本信息
- **复审周期**：2026-03 ~ 2026-09
- **复审日期**：2026-09-25
- **复审负责人**：@platform-lead
- **参与人员**：@arch-reviewer-1, @arch-reviewer-2

## 一、业务模块自建能力审查

| 模块 | 自建能力 | 使用模块数 | 判定 | 后续动作 |
|------|---------|-----------|------|---------|
| ydsz-cronjob | RedissonLeaderElector | 1 | 维持现状 | 待 common-lock 补充后迁移 |
| ydsz-x | XxxHelper | 4 | 迁移至 common | 26.10.01 版本移入 common-util |

## 二、common 模块健康度

| 指标 | 基线 (26.03) | 当前 (26.09) | 趋势 | 目标 |
|------|-------------|-------------|------|------|
| 子模块数 | 30 | 31 | +1 | 稳定 |
| 零引用模块 | 2 | 1 | 改善 | 0 |
| @Deprecated 迁移率 | 40% | 75% | 改善 | 95% |
| 平均测试覆盖率 | 72% | 78% | 改善 | 85% |
| ArchUnit 违规 | 5处 | 1处 | 改善 | 0 |

## 三、待定计划（Action Items）

| # | 事项 | 责任人 | 截止日期 | P 级 |
|---|------|-------|---------|-----|
| 1 | 将 xxxHelper 迁移至 common-util | @platform-dev-1 | 26.10.01 | P1 |
| 2 | ydzz-common-seata 归档至 attic | @platform-lead | 26.10.15 | P2 |

## 四、ARB 评审结论

- [ ] 通过
- [ ] 有条件通过（附条件）
- [ ] 驳回需整改后重审

评审签字：________________  日期：________________
```

---

## 8. 常见问答（FAQ）

### Q1：为什么我不能直接用 Jackson 而必须通过 `ydsz-common-json`？

**A**：《云顶编码规范》§22.4 明确禁止业务代码直接 import 第三方 JSON 库。原因有三：

1. **序列化策略统一**：`ydsz-common-json` 统一了日期格式（`yyyy-MM-dd HH:mm:ss`）、空值策略、枚举映射规则、大数精度保护等。直接使用 Jackson 会导致跨模块 API 交互时数据行为不一致。
2. **安全管控**：`ydsz-common-json` 内部集成了反序列化类型白名单，防止 Jackson 多态反序列化漏洞（如 CVE-2023-xxxxx）。直接 import 白名单不生效。
3. **可替换性**：当需要切换高性能 JSON 引擎（如 Fastjson2 / Fury）时，只需替换 `ydsz-common-json` 内部实现，业务代码零改动。

```java
// ❌ 错误
ObjectMapper mapper = new ObjectMapper();
mapper.writeValueAsString(data);

// ✅ 正确
String json = JsonUtils.toJsonString(data);
```

### Q2：`common-lock` 不支持 Redisson 的 `RLock` WatchDog 选举语义怎么办？

**A**：这是已知的已报备场景（参考 §3.3 报备案例表）。当前处置方式：

1. **临时方案**：业务模块在 PR 中按 §3.3 报备后，可临时直用 `RLock`**仅限** LeaderElector 场景。
2. **中期方案**：在 common-lock 模块提交 enhancement Issue，平台团队评估补充 `LeaderElector` 封装（原理：用 `RLock.lockWatchdogAsync()` + `isHeldByCurrentThread()` 实现）。
3. **迁移计划**：common-lock LeaderElector 封装完成后，业务代码在下一个 release 周期内完成迁移，清理报备声明。

如需紧急使用，请参照报备模板提交 PR，并附上 Issue 链接。

### Q3：上下游模块的 common 版本依赖不一致，是否允许？

**A**：**不允许**。ydsz-cloud 采用统一父 POM 管理 common 子模块版本，所有业务模块必须引用相同版本的对应子模块。原因：

- 若 ydsz-system 引用 `common-redis:1.0.0` 而 ydsz-message 引用 `common-redis:2.0.0`，同一 JVM 中通过 Spring Bean 传递的跨模块调用可能因 API 版本不一致导致运行时 `NoSuchMethodError`。

当个别业务确实需要延后升级时（评估有兼容性风险），需提交 **延期升级申请** 至 ARB 审批，明确延后期限和风险评估。

### Q4：common 模块的 SPI 机制如何扩展？业务模块自定义实现后是否会影响上游升级？

**A**：common 模块的 SPI 扩展机制完全遵循"开闭原则"，不会影响上游升级：

1. **定义 SPI 接口**：在 common 模块中声明 `@FunctionalInterface` 并提供 `@ConditionalOnMissingBean` 默认实现。
2. **自定义覆盖**：业务模块中通过 `@Bean` + `@Primary`（或 `@ConditionalOnMissingBean`）注册自定义实现。
3. **升级兼容**：上游 common 模块升级仅影响其内部实现。只要 SPI 接口签名不变（SemVer 兼容性承诺），业务模块的自定义实现无需修改。

```java
// 业务模块自定义实现（不影响 common 升级）
@Configuration
public class MyFileStorageConfig {

    @Bean
    @Primary
    public ImageProcessor customImageProcessor() {
        return new S3WatermarkProcessor();  // 自定义实现
    }
}
```

### Q5：发现 common 模块有 Bug 或有更好的实现方案，如何提 PR？多久能合并？

**A**：欢迎贡献！流程如下：

1. **Bug 修复**：直接提交 Issue（附复现步骤、期望行为）+ PR（含回归测试）。评审时限 ≤ 2 工作日。
2. **优化改进**：先提交 RFC Issue 描述方案，CODEOWNER 评审可行性后再开发。避免无沟通的大重构 PR 被拒。
3. **新功能**：确认不在 `docs/capability-matrix.md` 已有能力范围内后，按 PR 模板提交 RFC → 开发 → PR 流程。

### Q6：common 模块的 `@Deprecated` 方法什么时候真正删除？已过 Deprecated 期但仍在使用怎么办？

**A**：删除规则如下：

1. **标记后保留至少 1 个 MAJOR 版本周期**（即同一 MAJOR 版本内不删除）。
2. **删除操作发生在 MAJOR 版本升级时**（如 `1.x → 2.0.0`）。
3. 删除前须提供迁移指南（在 CHANGELOG `Removed` 段落中给出替换方案）。

如果业务模块无法在 MAJOR 版本升级时及时迁移，可在升级 PR 中标注"延期迁移"并由 ARB 审批延期（最长不超过 1 个季度）。但延期不等于豁免——延期期间该业务模块自行承担潜在的兼容性风险。

---

## 附录 A：相关文档索引

| 文档 | 路径 | 说明 |
|------|------|------|
| 能力矩阵 | `docs/capability-matrix.md` | 31 个公共子模块的核心能力与 SPI 索引 |
| 复用检查报告 | `scripts/check-common-reuse.py` | 静态检测脚本与复用矩阵 |
| 使用分析 | `docs/common-module-usage-analysis.md` | 各模块接入情况与待迁移清单 |
| Redis 架构规则 | `ydsz-common/ydsz-common-redis/docs/architecture-rules.md` | ArchUnit 守护规则参考 |
| 编码规范 | `docs/云顶编码规范.md` | §22.5 / §23.4 / §23.5 等关联条款 |
| 版本规范 | `docs/云顶版本规范.md` | CalVer 版本号与发布流程 |

## 附录 B：Issue 与 PR 标签体系

| 标签 | 用途 | 适用角色 |
|------|------|---------|
| `common-enhancement` | common 模块能力增强请求 | 全部 |
| `common-bug` | common 模块缺陷报告 | 全部 |
| `capability-request` | 新能力报备/自建申请 | 业务开发者 |
| `migrate-to-common` | 标记自建能力需要迁移至 common | 平台团队 |
| `arch-review` | 触发 ARB 架构评审 | 平台团队 |
| `breaking-change` | 破坏性变更（SemVer Major +1） | 全部 |
| `needs-changelog` | 提醒 PR 作者补充 CHANGELOG | CI 自动标注 |

## 附录 C：版本速查表

| 关键时间节点 | 事项 |
|-------------|------|
| 每月底 | common 子模块 minor/patch 发布窗口 |
| 每半年 | 半年复审（3 月 / 9 月） |
| 每年 12 月 | 次年 major 版本规划评审 |
| 随时 | common 安全补丁（patch 版本即时发布，< 24h 内通知下游升级） |

---

> **文档维护**：本指南由平台团队维护，每半年随复审同步更新。如有疑问或修改建议，请提 Issue 标注 `common-governance` 标签。
