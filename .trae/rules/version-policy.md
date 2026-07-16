# 项目版本号统一为 1.0.0

> **项目工程规范（强制）** — 适用于 ydsz 整个仓库的所有源码、构建、部署、文档文件，不可豁免。

## 规则定义

**ydsz 项目当前处于开发阶段未上线，所有代码文件的版本号统一为 `1.0.0`**。禁止出现 `1.3.0` / `2.0.0` / `3.5.0` 等任何非 `1.0.0` 的项目自身版本号。项目正式上线后，由产品/架构组统一决策升级版本号，在此之前任何人都不得擅自变更版本号基线。

## 版本号基线（单一来源）

| 位置 | 版本号 | 说明 |
|------|--------|------|
| Maven 后端 `pom.xml`（parent + 所有子模块） | `1.0.0-SNAPSHOT` | Maven 开发阶段约定，用 `${revision}` 统一管理 |
| 前端 `package.json` | `1.0.0` | npm 无 SNAPSHOT 概念，直接用正式版号 |
| Helm `Chart.yaml` | `version: 1.0.0` / `appVersion: "1.0.0"` | Chart 自身版本与应用版本对齐 |
| Helm `values.yaml`（全局 imageTag） | `v1.0.0` | 镜像 tag 加 `v` 前缀 |
| Helm `values-dev.yaml` | `v1.0.0-SNAPSHOT` | dev 环境用 SNAPSHOT |
| Helm `values-sit.yaml` | `v1.0.0-rc.1` | SIT 用 release candidate |
| Helm `values-uat.yaml` | `v1.0.0-rc.2` | UAT 用 release candidate |
| Helm `values-prod.yaml` | `v1.0.0` | 生产用正式版 |
| K8s `overlays/{dev,sit,uat,prod}/kustomization.yaml` | 与对应 Helm values 一致 | newTag 必须与 imageTag 同步 |
| 构建脚本 `build-images.sh` / `build-images.ps1` 默认 tag | `v1.0.0-SNAPSHOT` | 与 dev 环境一致 |
| Java Javadoc `@since` | `1.0.0` | 标注类/方法引入版本 |
| Java `@Deprecated(since = "1.0.0")` | `1.0.0` | 标注废弃版本 |
| OpenAPI `Info.version`（SpringDoc / Swagger） | `1.0.0` | API 文档版本与项目版本对齐 |
| Dockerfile 注释中的镜像 tag 示例 | `v1.0.0` | 文档示例与实际一致 |
| 项目 README / 模块 README 中的版本号 | `v1.0.0-SNAPSHOT`（开发阶段）或 `1.0.0` | 与 Maven / npm 版本一致 |

## 适用范围

- **所有 Java 后端模块**（`ydsz-backend/ydsz-*`，含 api / domain / infra / server / web 五层子模块 + `ydsz-common` 公共依赖库）
- **所有前端模块**（`ydsz-frontend`）的 `package.json`、源码、Dockerfile
- **所有部署配置**（`deploy/helm/`、`deploy/k8s/`、`deploy/scripts/`、`deploy/docker/`）
- **所有 CI 流水线**（Jenkinsfile、GitHub Actions、`.gitlab-ci.yml`）
- **所有文档**（项目根 `README.md`、各模块 `README.md`、`deploy/docs/`）
- **所有源码中的版本号字面量**（Java `@since`、`@Deprecated(since=...)`、OpenAPI `Info.version`、前端硬编码版本号等）

## 例外（不受本规则约束）

以下版本号是**第三方依赖或协议规范版本**，不属于项目自身版本号，**不得**误改：

1. **第三方库版本**：`pom.xml` 中的 `<spring-boot.version>`、`<mybatis-plus.version>`、`<redisson.version>` 等；`package.json` 中的 `dependencies` / `devDependencies` 版本；`pnpm-lock.yaml` / `package-lock.json` 中的锁定版本。
2. **协议规范版本**：OpenAPI 规范版本（`DocConstants.OPENAPI_VERSION = "3.0.3"`，指 Swagger/OpenAPI Specification 3.0.3）；W3C Trace Context 版本（`TRACEPARENT_VERSION = "00"`）。
3. **SQL 脚本文件名前缀**：`deploy/sql/V1.0.0.sql` / `deploy/sql/modules/V1.0.0_{module}.sql` 的 `V1.0.0` 是初始化脚本版本标识，与项目版本号同值但语义不同（见 `ignore-unit-test-coverage.md` 中关于禁止增量脚本的约束）。
4. **任务批次编号**：注释中的 `P1.3.0 重构` 等是开发任务批次编号（Priority + 子版本），不是项目版本号。

## 违规案例（已修复）

以下写法在本项目中出现过并已修复为 `1.0.0`，严禁再次出现：

```yaml
# ❌ Helm Chart.yaml 使用了 1.3.0
version: 1.3.0
appVersion: "1.3.0"

# ❌ Helm values.yaml 使用了 v1.3.0
global:
  imageTag: v1.3.0

# ❌ K8s kustomization.yaml 使用了 v1.3.0-SNAPSHOT
images:
  - name: ydsz/gateway
    newTag: v1.3.0-SNAPSHOT
```

```java
// ❌ Javadoc @since 使用了 1.3.0
/**
 * @since 1.3.0
 */

// ❌ @Deprecated(since = ...) 使用了 1.3.0
@Deprecated(since = "1.3.0", forRemoval = true)

// ❌ OpenAPI Info.version 使用了 3.5.0 / 2.0.0
new Info().title("...").version("3.5.0")
new Info().title("...").version("2.0.0")

// ❌ Contact.email 字段误填版本号
private String email = "1.3.0";
.email("1.3.0")
```

正确写法：

```yaml
# ✅ Helm Chart.yaml
version: 1.0.0
appVersion: "1.0.0"

# ✅ Helm values.yaml
global:
  imageTag: v1.0.0

# ✅ K8s kustomization.yaml
images:
  - name: ydsz/gateway
    newTag: v1.0.0-SNAPSHOT
```

```java
// ✅ Javadoc @since
/**
 * @since 1.0.0
 */

// ✅ @Deprecated(since = ...)
@Deprecated(since = "1.0.0", forRemoval = true)

// ✅ OpenAPI Info.version
new Info().title("...").version("1.0.0")

// ✅ Contact.email 填真实邮箱
private String email = "devops@ydsz.example.com";
.email("devops@ydsz.example.com")
```

## 实施约束

### 1. Maven 版本号单一来源

- `ydsz-backend/pom.xml` 中 `<revision>1.0.0-SNAPSHOT</revision>` 是所有后端模块版本号的**唯一来源**，子模块通过 `<parent>` 继承，**禁止**在子模块 `pom.xml` 中硬编码 `<version>`。
- 修改版本号只需改 `<revision>` 属性，**禁止**逐个模块修改。

### 2. 部署配置版本号同步

- Helm `Chart.yaml` 的 `appVersion` 必须与项目版本号一致（`1.0.0`）。
- Helm `values*.yaml` 的 `imageTag` 必须与对应环境的 K8s `kustomization.yaml` 的 `newTag` **完全一致**。
- 修改任一环境镜像 tag 时，必须**同步修改** Helm values + K8s kustomization + 构建脚本默认 tag。

### 3. 源码版本号约束

- 新增 Java 类时，Javadoc `@since` 一律写 `1.0.0`（项目当前版本）。
- 新增 `@Deprecated` 注解时，`since` 一律写 `1.0.0`。
- 新增 OpenAPI `Info.version` 时，一律写 `1.0.0`。
- **禁止**在源码中硬编码任何非 `1.0.0` 的项目版本号字面量。

### 4. 文档版本号同步

- 项目根 `README.md` 中的版本号必须与 Maven / npm 版本一致。
- 各模块 `README.md` 中的版本号必须与项目版本号一致。

## 执行机制

- **IDE 规则**：Trae / CatPaw 规则文件 `alwaysApply: true`，AI 代码生成与审查阶段自动遵守。
- **Code Review**：PR 审查必须检查版本号一致性，发现非 `1.0.0` 的项目版本号即打回。
- **CI 检测**：可选在 `deploy/scripts/check-quality-gate.sh` 中检测项目自身版本号是否统一为 `1.0.0`，命中非 `1.0.0` 的项目版本号则阻断。

## 升级流程

项目正式上线后，如需升级版本号（如 `1.0.0` → `1.1.0` / `2.0.0`），必须同时满足以下条件：

1. 产品/架构组**明确**决策升级目标版本号。
2. 统一修改 Maven `<revision>`、前端 `package.json`、Helm `Chart.yaml`、Helm `values*.yaml`、K8s `kustomization.yaml`、构建脚本、README 等所有版本号来源。
3. 在团队周会 / 文档公告，并更新本规则文件的版本号基线表格。
4. 新增类/方法的 `@since` 使用新版本号；已有类/方法的 `@since` **不得**批量修改（保留历史引入版本记录）。

## 相关文件

- [ydsz-backend/pom.xml](file:///d:/Code/ydsz/ydsz-pmis/ydsz-backend/pom.xml#L38) — Maven `<revision>` 属性（版本号唯一来源）
- [ydsz-frontend/package.json](file:///d:/Code/ydsz/ydsz-pmis/ydsz-frontend/package.json#L3) — 前端 npm 版本号
- [deploy/helm/ydsz/Chart.yaml](file:///d:/Code/ydsz/ydsz-pmis/deploy/helm/ydsz/Chart.yaml#L11) — Helm Chart 版本号
- [deploy/helm/ydsz/values.yaml](file:///d:/Code/ydsz/ydsz-pmis/deploy/helm/ydsz/values.yaml#L20) — 全局镜像 tag
- [deploy/scripts/build-images.sh](file:///d:/Code/ydsz/ydsz-pmis/deploy/scripts/build-images.sh#L22) — 构建脚本默认 tag
- 项目记忆：c:\Users\Marvin\.trae-cn\memory\projects\-d-Code-ydsz-ydsz-pmis\project_memory.md → Hard Constraints → "项目版本号统一为 1.0.0"
