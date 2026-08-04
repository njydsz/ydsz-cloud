# 依赖治理状态

> 跟踪项目依赖版本收敛状态、enforcer exclude 清理进度、已知技术债。
> 与根 pom.xml 的 `<exclude>` 列表和 ArchUnit R29 规则对齐。

---

## Enforcer Exclude 清单

### requireUpperBoundDeps / dependencyConvergence 排除项

| # | 排除坐标 | 类型 | 根因 | 解除条件 | 状态 |
|---|---|---|---|---|---|
| 1 | `io.github.resilience4j:resilience4j-annotations` | BOM 版本冻结 | resilience4j-bom 2.4.0 将 annotations 冻结在 2.3.0，而 core 模块 POM 声明依赖上界 2.4.0 | 等待 resilience4j 2.5.x BOM 修复，或降级全项目 resilience4j 到 2.3.0 | ⏳ BLOCKED |
| 2 | `io.github.resilience4j:resilience4j-circularbuffer` | BOM 版本冻结 | 同上（annotations 的同级模块） | 同上 | ⏳ BLOCKED |
| 3 | `com.squareup.okio:okio-jvm` | 跨大版本共存 | opentelemetry-exporter-sender-okhttp 传递依赖 okhttp-jvm 5.x 要求 okio ≥ 3.16.4，而项目锁定 3.8.0（与 okhttp 4.12.0 兼容） | 迁移到 okhttp 5.x 后统一 okio 版本 | ⏳ BLOCKED |

### bannedDependencies 违反项（已豁免的合理场景）

| 坐标 | 豁免原因 | 约束 |
|---|---|---|
| `com.fasterxml.jackson.core:jackson-databind` | 公共层（ydsz-common-json）用于内部 Schema 序列化、第三方 SDK 集成 | ArchUnit R29 约束业务代码不得 import `com.fasterxml.jackson.*` |
| `cn.hutool:hutool-all` | nextwiki / common-util 的 crypto.digest 合理使用 | 仅 `hutool-json` 子模块被禁 |

---

## 依赖版本锁定（DM）一览

通过 `<dependencyManagement>` 统一锁定的高风险依赖：

| 依赖 | 锁定版本 | 锁定原因 |
|---|---|---|
| Guava | 33.5.0-jre | 消除 33.4.8-jre vs 32.0.1-jre 多版本冲突 |
| OkHttp | 4.12.0 | 与 Redisson 4.6.1 / MinIO 8.5.11 兼容 |
| Okio | 3.8.0 | 与 OkHttp 4.12.0 兼容（okhttp 5.x 需要 3.16.4+） |
| Gson | 2.13.2 | Spring Boot BOM 管理版本显式声明 |
| gRPC | 1.80.0 | OTel Collector OTLP gRPC 通信 |
| Netty (mdns/resolver) | Spring Boot 管理 | 消除 gRPC vs Reactor Netty 版本冲突 |

---

## JSON 库对齐状态

> ArchUnit R29 + Enforcer bannedDependencies 双重约束

| 竞品库 | 状态 | 说明 |
|---|---|---|
| `com.alibaba:fastjson` (<2.x) | ⛔ BANNED | 历史安全漏洞，enforcer 全局禁用 |
| `com.google.code.gson:gson` | ⛔ BANNED（直接声明） | 统一使用 ydsz-common-json (YdszJson) |
| `cn.hutool:hutool-json` | ⛔ BANNED | 防止 JSON 能力泄漏 |
| `org.json:json` | ⛔ BANNED | 统一使用 YdszJson |
| `com.googlecode.json-simple:json-simple` | ⛔ BANNED | 统一使用 YdszJson |
| `com.fasterxml.jackson.core:jackson-databind` | ⚠️ EXEMPT（公共层） | 需在 ydsz-common 层声明 optional，ArchUnit R29 约束业务层 |

---

## 清理路线图

| 阶段 | 行动 | 预计周期 | 依赖条件 |
|---|---|---|---|
| Phase 1 | SpotBugs 路径耦合修复（${maven.multiModuleProjectDirectory} → ${project.basedir}） | ✅ Complete | - |
| Phase 2 | OkHttp 5.x 评估与迁移 | Q3 2026 | Spring Boot 4.1 官方推荐 OkHttp 5 |
| Phase 3 | Okio 版本统一（解除 okio-jvm exclude） | Q3 2026 | okhttp 5 迁移完成 |
| Phase 4 | Resilience4j BOM 升级或全模块降级至 2.3.0 | Q4 2026 | 等待上游修复或团队决策 |

---

*最后更新：2026-08-04*
