# ydsz 全仓库去 pmis 品牌化方案

> **状态**：执行中（2026-07-16 启动，用户决策：立即全量重构）
> **品牌定位**：项目品牌标识是 **ydsz**（不是 pmis）。pmis 是遗留产品代号，须从全仓库移除。
> **方向反转**：2026-07-15 的「去 Ydsz 化」（`YdszJson*` → `YdszJson*`、`YdszCache*` → `YdszCache*`）方向错误，本次回退为 `Ydsz` 前缀。
> **范围**：ydsz 整个仓库（后端 Maven 模块 + 前端 + 部署 + 脚本 + 规则 + 文档）

---

## 一、替换策略（混合）

| 层级 | 旧 | 新 | 示例 |
|---|---|---|---|
| Java 包路径 | `com.njydsz.{module}.*` | `com.njydsz.{module}.*` | `com.njydsz.project` → `com.njydsz.project` |
| Maven 模块名/目录 | `ydsz-*` | `ydsz-*` | `ydsz-backend` → `ydsz-backend` |
| SQL 表前缀 | `pmis_*` | `ydsz_*` | `pmis_job` → `ydsz_job` |
| Java 类名 | `PmisXxx` | `YdszXxx` | `PmisWorkflowFacade` → `YdszWorkflowFacade` |
| json/cache 模块类名（回退） | `YdszJson*` / `YdszCache*` | `YdszJson*` / `YdszCache*` | `YdszJson` → `YdszJson`、`YdszCache` → `YdszCache` |
| 配置键 | `pmis.*` | `ydsz.*` | `pmis.message.receipt-timeout-minutes` → `ydsz.message.receipt-timeout-minutes` |
| 分布式锁 key / 权限码 | `pmis:*` | `ydsz:*` | `pmis:msg:receipt:pull:lock` → `ydsz:msg:receipt:pull:lock` |
| Helm / K8s 资源 | `ydsz` | `ydsz` | `deploy/helm/ydsz/` → `deploy/helm/ydsz/` |

### 不替换（例外）

1. **第三方依赖版本**：pom.xml / package.json 中的 dependencies 版本号。
2. **协议规范版本**：OpenAPI Spec 3.0.3、W3C Trace Context "00"。
3. **SQL 脚本文件名前缀**：`V1.0.0_{module}.sql` 的 `V1.0.0` 是初始化脚本标识。
4. **任务批次编号**：`P1.3.0 重构` 等开发任务批次编号。
5. **字符串字面量中的 FQN**：反射类名等（但包路径段 `pmis` 仍需删除）。

---

## 二、摸底数据

### 2.1 全仓库规模（2026-07-16 统计）

| 维度 | 数值 | 来源 |
|---|---|---|
| `com.njydsz` 出现次数 | 604+（文件列表截断，实际 2,919 package 声明） | Grep |
| SQL 中 `pmis_` 出现次数 | 8,687 处 / 15 文件 | Grep `deploy/sql` |
| `ydsz-` 出现次数 | 10,625 处 / 100+ 文件 | Grep 全仓库 |
| `Pmis*.java` 类文件 | 2 个 | Glob |

### 2.2 Pmis* 类清单

| 类 | 路径 | 新名 |
|---|---|---|
| `PmisWorkflowFacade` | `ydsz-workflow/.../facade/` | `YdszWorkflowFacade` |
| `PmisBusinessMetricsJob` | `ydsz-project/.../metrics/` | `YdszBusinessMetricsJob` |

### 2.3 json/cache 模块回退清单

| 模块 | 当前类名（2026-07-15 重命名后） | 回退为 |
|---|---|---|
| common-json | `YdszJson`（24 个公开类） | `YdszJson*` |
| common-json | `SerializerEngine` / `DeserializerEngine` / `SerializationProvider` / `DeserializationProvider` | `YdszSerializerEngine` 等 |
| common-cache | `YdszCache` / `YdszCacheManager` / `YdszCacheProperties` / `YdszCacheAutoConfiguration` / `SpringYdszCache` | `YdszCache` / `YdszCacheManager` / `YdszCacheProperties` / `YdszCacheAutoConfiguration` / `SpringYdszCache` |

---

## 三、执行步骤

### 步骤 1：批量文本替换（Python 脚本）

脚本：`scripts/debrand-pmis-fullrepo.py`

按以下顺序替换文件**内容**（不动文件路径）：

1. `com.njydsz.` → `com.njydsz.`（包路径、import、字符串字面量）
2. `com\njydsz\pmis\` → `com\njydsz\`（Windows 路径形式，resources 中的 FQN）
3. `ydsz-` → `ydsz-`（artifactId、目录引用、配置）
4. `pmis_` → `ydsz_`（SQL 文件 + Java `@TableName` 注解）
5. `pmis.` → `ydsz.`（配置键，仅 yml/yaml/properties 文件）
6. `pmis:` → `ydsz:`（分布式锁 key、权限码命名空间）
7. 类名 `Pmis` → `Ydsz`（词边界匹配，仅 Java 文件）

### 步骤 2：物理目录迁移

1. `com/njydsz/` → `com/njydsz/`（移动 Java 源文件目录树）
2. `ydsz-backend` → `ydsz-backend`（顶层模块目录）
3. `ydsz-frontend` → `ydsz-frontend`
4. 所有 `ydsz-*` 子模块目录 → `ydsz-*`
5. `deploy/helm/ydsz/` → `deploy/helm/ydsz/`

### 步骤 3：类文件重命名

1. `PmisWorkflowFacade.java` → `YdszWorkflowFacade.java`
2. `PmisBusinessMetricsJob.java` → `YdszBusinessMetricsJob.java`
3. json 模块：`YdszJson*.java` → `YdszJson*.java`（回退）
4. cache 模块：`YdszCache*.java` → `YdszCache*.java`（回退）
5. 同步更新 `AutoConfiguration.imports`、`native-image.json`、`spring-configuration-metadata.json`

### 步骤 4：验证

1. `mvn clean compile`（后端编译）
2. `pnpm build`（前端编译）
3. `bash deploy/scripts/check-brand-consistency.sh`（门禁检测 pmis 残留）

---

## 四、回滚预案

1. **Git tag 锚点**：重构前打 `git tag pre-debrand-pmis`。
2. **分支隔离**：在独立 feature 分支执行。
3. **失败回滚**：`git reset --hard pre-debrand-pmis`。

---

## 五、CI 防退化

[deploy/scripts/check-brand-consistency.sh](file:///d:/Code/ydsz/ydsz/deploy/scripts/check-brand-consistency.sh) 改为检测 **pmis 残留**：

1. Java 源文件中 `com.njydsz` 包路径残留
2. pom.xml 中 `ydsz-*` artifactId 残留
3. SQL 文件中 `pmis_` 表前缀残留
4. Java 类名 `Pmis*` 残留
5. 配置文件中 `pmis.` 配置键残留
6. 分布式锁 key `pmis:*` 残留

命中任一项即阻断提交。
