# ADR-009：公共能力重复实现收敛决策（重建自丢失的 ADR-2026-09-12）

**状态：** 已接受
**日期：** 2026-09-14（原决议 2026-09-12，本文档为重建）
**决策者：** ydsz-team
**重建原因：** 原文档 `docs/ADR-2026-09-12_公共能力重复实现收敛决策.md` 经 git 全历史检索确认从未入库且已丢失；4 处源码 Javadoc 引用该路径悬空。本文档依据源码内 Javadoc 与 2026-09-14 深度检查报告反推重建。

---

## 背景

2026-09-12 复用深度检查发现若干"业务模块与 common 平行实现"双轨问题，会议形成 ADR-1/ADR-3/ADR-5 三项决议并写入源码 Javadoc，但决议全文未入库。本文档重建三项决议全文，作为后续评审与守护脚本（`scripts/check-common-reuse.py`）的判定依据。

## 决议全文

### ADR-1：短信签名能力收敛（common-notify vs ydsz-message）

**背景：** common-notify 与 message 存在四对同名能力类（SmsProvider / AliyunSmsProvider / TemplateEngine / TemplateVariableValidator），能力重叠。

**决议：**
1. **message 侧为短信发送管线的权威实现**（通道编排、重试、DLQ 由 message 统一承担）；
2. **阿里云签名算法逻辑待下沉 common-notify**（`AliyunSmsSigner`），下沉后 message 改为组合调用，不再自持签名实现；
3. 收敛完成前，`AliyunSmsProvider` 的 Javadoc 须引用本 ADR 而非丢失的旧文档。

**执行状态（2026-09-14）：** 未落地，列入第三批路线（SmsSigner 下沉）。

### ADR-3：线程池管理定界（cronjob 裸 ThreadPoolExecutor vs common-thread）

**背景：** cronjob `ThreadPoolHotUpdateListener` 直接管理 JDK 裸 `ThreadPoolExecutor`（支持运行时参数热更新），与 common-thread 的执行器封装并存。

**决议：**
1. **cronjob 场景保留自有实现**——定时任务需要"运行时核心/最大线程数 + 队列容量热更新 + 变更监听"，common-thread 静态封装不覆盖该场景，属"场景决定的必要实现"；
2. **决议 2：`SystemMetricsCollector` 应复用 common-sentry 的指标采集能力、删除自有 JMX 实现**——决议已定，执行列入后续整改；
3. common-thread 与 cronjob 自有执行器并存为合法状态，新增线程池场景优先 common-thread。

**执行状态（2026-09-14）：** 决议 1 已生效；决议 2 未执行（TODO）。

### ADR-4：网关层与 common 能力定界（gateway 过滤器）

**背景：** gateway 的 `W3CTraceContextFilter` 与 `SqlInjectionFilter` 与 common-util / common-jdbc 的对应能力疑似重叠。

**决议：**
1. **W3C Trace 能力下沉计划**：W3C Trace Context（`traceparent`）解析/传播是全平台需求，common-util 已有 `TracerUtils#parseTraceparent`；网关过滤器承载的协议解析逻辑后续应下沉至 common-util TracerUtils（计划项，未执行）；
2. **SQL 注入防御为纵深分层，非重复建设**：common-jdbc `SqlFirewallInnerInterceptor` 在 JDBC 层深度防护，网关过滤器在入口层拦截，二者互补；网关为响应式栈无法复用 Servlet 端实现（终局定界，守护脚本命中时豁免）。

**执行状态（2026-09-14）：** 决议 2 已生效；决议 1 下沉计划未执行（TODO）。

### ADR-5：RAG 分块与文档预处理定界（ydsz-agent vs common-docs）

**背景：** agent `TextChunker` 与 common-docs 的文档解析/分块能力疑似重叠。

**决议：**
1. **不合并。** common-docs 面向"文档格式解析与导出"，agent `TextChunker` 面向"RAG 检索优化的语义分块"（滑动窗口、重叠率、嵌入长度约束），二者目标函数不同；
2. agent 侧 `DocumentFormat`（输出格式枚举）与 common-docs 解析格式枚举为同名不同物，逐步重命名消歧；
3. 该定界为终局决议，后续守护脚本 E1 命中此两类时按本决议豁免。

**执行状态（2026-09-14）：** 决议生效，脚本豁免清单可引用本 ADR。

## 附带修正

以下 6 处源码 Javadoc 引用了丢失的旧文档路径 `docs/ADR-2026-09-12_公共能力重复实现收敛决策.md`，已统一修正为引用本 ADR：

1. `ydsz-message/.../channel/sms/AliyunSmsProvider.java` → ADR-1
2. `ydsz-agent/.../domain/rag/TextChunker.java` → ADR-5
3. `ydsz-cronjob/.../core/config/ThreadPoolHotUpdateListener.java` → ADR-3
4. `ydsz-cronjob/.../core/metrics/SystemMetricsCollector.java` → ADR-3
5. `ydsz-gateway/.../filter/W3CTraceContextFilter.java` → ADR-4
6. `ydsz-gateway/.../filter/SqlInjectionFilter.java` → ADR-4

## 关联

* 守护规则：`scripts/check-common-reuse.py`（2026-09-14 重建，第四次丢失后入库）
* 规范章节：《云顶编码规范》§33.7、§22
* ADR-007（Bean 拷贝收敛）、ADR-008（ID 生成边界）
