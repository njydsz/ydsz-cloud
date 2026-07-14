# ydsz-pmis 全仓库去 Ydsz 品牌化方案

> **状态**：方案规划稿，待用户决策目标品牌名后启动
> **范围**：ydsz-pmis 整个仓库（后端 24 个 Maven 模块 + 前端 + 部署 + 脚本）
> **当前进度**：ydsz-pmis-common-json 模块已完成去 Ydsz 化（详见上文 P0/P1 阶段）

---

## 一、摸底数据（来源：scripts/audit-brand-residue.py）

### 1.1 全仓库规模

| 维度 | 数值 |
|---|---|
| 含 Ydsz 标识的文件总数 | 3,561 |
| 受影响模块数 | 11（10 个 ydsz-pmis-* 模块 + deploy + 其它） |
| `njydsz` 字面出现次数 | 12,435（groupId / package 路径） |
| `ydsz-pmis` 字面出现次数 | 6,683（artifactId / 文件目录） |
| Java 源文件中 `package com.njydsz.pmis.*` | 2,919 |
| pom.xml 中 `groupId` 含 `com.njydsz` | 690 处 / 122 个文件 |
| pom.xml 中 `artifactId` 含 `ydsz-` | 786 处 / 122 个文件 |
| Java 类名含 Ydsz 前缀 | 17 处 / 15 个文件（其他 5 个 common 子模块） |
| `Ydsz*.java` 文件名残留 | **0**（本次 P1-1 阶段已清零） |

### 1.2 模块分布 Top 5

| 模块 | 含品牌文件数 | 说明 |
|---|---|---|
| ydsz-pmis-backend | 3,241 | 24 个后端 Maven 模块 |
| deploy | 147 | K8s / Helm / Docker / SQL / 脚本 |
| ydsz-pmis-frontend | 144 | Vue 3.5 + Element Plus |
| .github | 5 | GitHub Actions workflow |
| .trae | 3 | Trae 规则文件 |

### 1.3 其他 5 个 common 子模块残留的 Ydsz 类（**模块外残留**）

| 模块 | 类 |
|---|---|
| ydsz-pmis-common-cache | `YdszCacheProperties` / `YdszCacheManager` / `YdszCacheAutoConfiguration` |
| ydsz-pmis-common-redis | `YdszCacheableAspect` / `@YdszCacheable` |
| ydsz-pmis-common-feign | `YdszFeignLogger` / `YdszFeignErrorDecoder` |
| ydsz-pmis-common-exception | `YdszTimeoutException` / `YdszSecurityException` / `AbstractYdszException` |
| ydsz-pmis-common-lock | `YdszDistributedLockAspect` / `@YdszDistributedLock` |

---

## 二、品牌策略选项

> **请先回答决策点 1，再启动大规模改造**

### 选项 A：完全去 Ydsz + 去 njydsz（彻底去品牌化）

- **目标**：`Ydsz*` → `*`；`com.njydsz.pmis` → `com.pmis`；`ydsz-pmis-*` → `pmis-*`
- **优点**：
  - 命名最简洁，最易记忆
  - 与 Spring 生态（`com.fasterxml.jackson`）风格一致
  - 重构后无任何品牌痕迹
- **缺点**：
  - 改动量最大（~12,000 处 import 路径、~700 处 groupId、~800 处 artifactId、~3,500 处目录/文件名）
  - groupId 修改后旧依赖地址失效，需要发布到新 Maven 仓库坐标
  - package 路径修改触发类加载器变更，需要重新打 Docker 镜像

### 选项 B：保留 Pmis/PMIS 品牌（推荐）

- **目标**：`Ydsz*` → `Pmis*`；`com.njydsz.pmis` → `com.pmis`；`ydsz-pmis-*` → `pmis-*`
- **优点**：
  - 保留项目身份（"PMIS" 业务品牌），去 Ydsz 旧公司前缀
  - 改动可控，分批滚动替换
  - 与现有部署架构（K8s namespace、Helm release）逐步迁移
- **缺点**：
  - 仍有"pmis"品牌烙印，对外开源化时需进一步清理

### 选项 C：保守分步（最小变更）

- **目标**：仅清理 Java 类名/文件名 Ydsz 前缀（其他 5 个 common 模块的 15 个 Ydsz* 类），保留 `com.njydsz.pmis` / `ydsz-pmis-*` 不动
- **优点**：
  - 改动量最小（仅 15 个类 + 引用方 ~50 处）
  - 无破坏性 API 变更
  - 与 ydsz-pmis-common-json 模块已完成的去 Ydsz 化对齐
- **缺点**：
  - 项目身份仍带 Ydsz（仅去 Ydsz 公开类名）

### 选项 D：保留现状（Ydsz 是项目品牌而非公司品牌）

- **目标**：把 Ydsz 视为品牌名称（类似"Apache"、"Spring"），仅修复 P0 阻断性问题，不做大规模去品牌化
- **优点**：
  - 0 改动
- **缺点**：
  - 与 ydsz-pmis-common-json 已完成的工作方向不一致
  - 长期看仍存在混淆（"Ydsz" 看起来像公司名而非项目名）

---

## 三、改造范围（按选项 B 推荐方案）

### 3.1 改动维度与预估工作量

| 维度 | 涉及数量 | 改动方式 | 风险等级 |
|---|---|---|---|
| Java 类名 / 文件名 | 15 个 Ydsz* 类（其他 5 个 common 模块） | 批量 sed + IDE 重命名 | 🟢 低 |
| Java import 路径 | ~72 处 | sed 全局替换 | 🟢 低 |
| Maven groupId | 122 个 pom.xml，690 处 | 批量改 + 同步 settings.xml | 🟡 中 |
| Maven artifactId | 122 个 pom.xml，786 处 | 批量改 + 同步 ydsz-pmis-* 目录 | 🟡 中 |
| package 声明 | 2,919 处 | 批量改 + 同步 import 路径 | 🟠 高 |
| 目录/文件名（Maven 模块） | ~24 个 ydsz-pmis-* 顶层目录 | 移动 + 改 pom.xml parent | 🟠 高 |
| K8s 资源 | ~50 个 YAML（deploy/k8s + helm + argo） | sed 替换 namespace + service name | 🟡 中 |
| Docker 镜像 tag | Dockerfile + image 引用 | sed 替换 | 🟡 中 |
| Nacos dataId / group | 若干 bootstrap.yml | sed 替换 namespace | 🟡 中 |
| 前端 | 144 个文件 | sed 替换 import path + vite.config | 🟠 高 |
| GitHub Actions | 5 个 workflow | sed 替换 | 🟢 低 |
| .trae 规则 | 3 个 | sed 替换 | 🟢 低 |

### 3.2 风险评估

| 风险点 | 影响 | 缓解措施 |
|---|---|---|
| `package com.njydsz.pmis` 变更导致 Java 类加载失败 | 整个后端不可用 | 1) 一次性提交 + 全量回归 2) 保留 `com.njydsz.pmis` 包名作为兼容期（用 Pmis* 替代 Ydsz* 类名，包名不变） |
| `spring.application.name` 变更导致服务注册失败 | Nacos 注册不上 | 1) 同步更新 Nacos 配置 2) 灰度滚动重启 |
| `bootstrap.yml` 配置文件中的 `namespace` 变更 | Nacos 配置读不到 | 1) 同步迁移 Nacos namespace 数据 2) 保留旧 namespace 作为只读 |
| Docker image tag 变更导致 K8s 拉不到镜像 | 服务启动失败 | 1) 同时 push 旧/新 tag 2) K8s 滚动升级用新 tag |
| 前端 `import` 路径变更 | 编译失败 | 1) IDE 自动跟随 2) 全量 `pnpm build` 验证 |
| Git 历史可读性 | code review 困难 | 1) 使用 `git log --follow` 跟踪重命名 2) 提交信息明确说明 |

---

## 四、执行计划（按选项 B，分 4 个阶段）

### 阶段 1：代码层去 Ydsz（其他 5 个 common 模块）

**目标**：把 15 个 Ydsz* 类重命名为 Pmis* / 纯名
**风险**：🟢 低
**改动量**：~50 个文件
**预计耗时**：2-3 小时

```
1. 列出 15 个 Ydsz* 类的全部引用方
2. 编写 Python 脚本批量重命名（rename-ydsz-common-modules.py）
3. mvn -pl ydsz-pmis-common -am clean install 验证
4. 更新 ydsz-pmis-common-json 的 check-brand-consistency.sh 扩展到 common 全模块
```

### 阶段 2：Maven 坐标重命名

**目标**：`ydsz-pmis-*` → `pmis-*`（artifactId）+ 保持 groupId 不动
**风险**：🟡 中
**改动量**：~122 个 pom.xml
**预计耗时**：4-6 小时

```
1. 重命名所有 Maven 顶层目录：ydsz-pmis-backend/ ydsz-pmis-frontend/
2. 重命名所有子模块目录：ydsz-pmis-common/ ydsz-pmis-system/ ...
3. 批量替换所有 pom.xml 中的 <artifactId>
4. 批量替换所有 pom.xml 中的 <parent><artifactId> 引用
5. 批量替换所有 settings.xml / .mvn/maven.config
6. mvn validate 验证
```

### 阶段 3：Java package 路径重命名

**目标**：`com.njydsz.pmis.*` → `com.pmis.*`（或保持 com.njydsz.pmis 不动）
**风险**：🟠 高
**改动量**：~3,000 个文件
**预计耗时**：1-2 天

```
1. 用 OpenRewrite 脚本批量重命名 package + import
2. 同步更新 META-INF/services 文件中的 FQN
3. 同步更新 resources/ 下的 *.json / *.xml 中硬编码的 FQN
4. mvn clean install 验证
5. 启动各 server 子模块验证
```

### 阶段 4：部署/运维/前端

**目标**：K8s / Helm / Docker / Nacos / 前端 import path 全部对齐
**风险**：🟠 高
**改动量**：~300 个文件
**预计耗时**：2-3 天

```
1. K8s namespace 替换
2. Helm release name 替换
3. Docker image tag 替换
4. Nacos namespace / dataId 替换
5. 前端 import path / vite.config.ts base path 替换
6. Argo Workflow / Chaos Mesh CRD 替换
7. GitHub Actions workflow 替换
8. 全链路 e2e 验证（smoke-* 脚本）
```

---

## 五、回滚预案

每个阶段都需具备：
1. **Git tag 锚点**：每个阶段开始前打 `git tag pre-phase-N-debrand`
2. **分支隔离**：每个阶段一个独立 feature 分支
3. **灰度回滚**：如果 K8s 滚动升级失败，立即回滚到旧 image tag
4. **Nacos 双 namespace**：新 namespace 数据先同步，待切流量后再删旧 namespace

---

## 六、CI 防退化扩展

将 [check-brand-consistency.sh](file:///d:/Code/ydsz/ydsz-pmis/deploy/scripts/check-brand-consistency.sh) 从「仅 ydsz-pmis-common-json」扩展到：

```
1. 所有后端模块的 Ydsz* 类名/文件名检测
2. 所有 pom.xml 的 groupId / artifactId 检测
3. 所有 Java 源文件的 package 声明检测
4. 所有 *.yml / *.yaml / *.json 中的 K8s/Nacos 资源检测
5. 前端 src/ 下的 import path 检测
```

预计扩展后 12 项检查门禁（与 P1-4 现有 6 项合并）。

---

## 七、待用户决策

1. **品牌策略**：A（彻底去品牌） / B（推荐：去 Ydsz 保留 Pmis） / C（保守分步） / D（保留现状）？
2. **执行节奏**：一次性全量（风险高） / 分阶段滚动（推荐） / 仅本模块（ydsz-pmis-common-json，已完成）？
3. **改造起点**：本任务仅 ydsz-pmis-common-json 模块已完成，其他 5 个 common 模块是否一并纳入本轮优化？

---

## 八、本任务（ydsz-pmis-common-json）阶段完成清单

| 任务 | 状态 | 验证方式 |
|---|---|---|
| P0-1 AutoConfiguration.imports 类名修复 | ✅ | bash check-brand-consistency.sh |
| P0-2 native-image.json 反射类名修复 | ✅ | mvn clean package |
| P0-3 pom.xml description 修复 | ✅ | pom.xml diff |
| P0-4 4 个 Ydsz*Engine/Provider 类重命名 | ✅ | mvn compile |
| P0-5 README.md 重写 | ✅ | README.md diff |
| P0-6 编译 + 打包验证 | ✅ | mvn clean package BUILD SUCCESS |
| P1-1 22 个 Ydsz*.java 文件名批量重命名 | ✅ | check-brand-consistency.sh |
| P1-2 Javadoc @author 清理 | ✅ | check-brand-consistency.sh |
| P1-3 spring-configuration-metadata.json | ✅ | mvn compile + IDE 提示 |
| P1-4 check-brand-consistency.sh 门禁 | ✅ | 6 项检查全 PASS |
| P1-5 mvn clean package 完整验证 | ✅ | BUILD SUCCESS |
| P2-1 全仓库 Ydsz 残留摸底 | ✅ | scripts/brand-residue-report.json |
| P2-2 全仓库去 Ydsz 化方案 | ✅ | 本文档 |
| P2-3 写入 project_memory.md | ⏳ | 待本任务收尾 |
