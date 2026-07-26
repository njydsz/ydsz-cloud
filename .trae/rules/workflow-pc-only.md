# ydsz-workflow 永远不适配移动端

> **项目工程规范（强制）** — 适用于 ydsz-workflow 模块的所有源码、构建、部署、文档文件，不可豁免。

## 规则定义

`ydsz-workflow`（自研工作流 v2 / `ydsz_flow_*` 表 / BPMN 2.0 解析 / 设计器 / 审批中心 / 流程监控）**永远不适配移动端 App 或独立 H5 应用**。前端流程设计器（bpmn-js）、审批中心、流程模板、流程监控、流程图渲染等所有工作流相关 UI 仅面向 PC Web 端（`ydsz-frontend`，Vue 3.5 + Element Plus）。

如需在移动端使用审批/流程能力，必须通过独立的「轻审批 H5」或对接企业微信/钉钉/飞书（已在 `thirdparty` 中实现签名工具）等 IM 通道实现，**绝不允许**把工作流模块代码直接运行在移动端/H5 容器中。

## 禁止事项

`ydsz-workflow` 模块下**禁止**出现以下任何一项：

1. **移动端专属 Controller**：类名包含 `Mobile`、`MApp`、`H5`、`Applet`、`MiniApp` 等字样的 Controller，例如 ❌ `FlowMobileController` / `FlowH5Controller` / `FlowAppController`。
2. **移动端专属路径**：`@RequestMapping` 路径包含 `/mobile/` / `/m/` / `/h5/` / `/app/` / `/applet/` 等移动端前缀。
3. **移动端专属 VO/DTO**：类名包含 `Mobile*VO` / `Mobile*DTO` / `App*VO` / `H5*VO` 等移动端专属数据传输对象。
4. **移动端专属 Swagger Tag**：`@Tag(name = "workflow-mobile")` / `@Tag(name = "workflow-h5")` 等移动端专属分组。
5. **移动端 UA 识别逻辑**：在 Controller / Service / Filter / Interceptor 中通过 `User-Agent` 识别移动端并返回不同响应。
6. **移动端 SDK 依赖**：在 `pom.xml` 中引入移动端 SDK（如 `weixin-java-miniapp`、`dingtalk-app-sdk` 等移动端推送 SDK；`thirdparty` 中的钉钉/飞书/企微审批同步签名工具不属于此范围）。

## 例外（不属于违规）

以下场景**不属于**违规：

1. **内嵌审批**（`FlowEmbeddedApprovalService`）：业务系统在 PC 浏览器中以 iframe / Web Component 嵌入审批面板，仍属 PC Web 范畴。
2. **第三方审批同步**（`FlowThirdPartySyncService` / `FlowThirdPartyApprovalController`）：通过 webhook 与钉钉/飞书/企微服务端通信，**不涉及移动端 UI**，仅是服务端到服务端的审批状态同步。
3. **通知推送**：通过 `NotificationClient` 推送到 IM（用户在 PC 或移动端 IM 收到通知），不属于工作流模块自身适配移动端。

## 处置流程

### 1. 发现违规立即删除

一旦发现 `*Mobile*Controller` / `*H5*Controller` / `*App*Controller`（移动端语义）/ `/mobile/*` / `/h5/*` 路径、移动端专属 VO，**立即删除**，不得保留注释或 `@Deprecated` 占位。

### 2. 规则强化

- **IDE 规则**：本规则文件 `alwaysApply: true`，AI 代码生成与审查阶段自动遵守。
- **Code Review**：PR 审查中如发现移动端适配代码，即打回。
- **CI 检测**：可在 `deploy/scripts/check-quality-gate.sh` 中增加 grep 检测：
  ```bash
  # 检测 ydsz-workflow 模块下的移动端适配代码
  if grep -rE "(Mobile|H5|Applet).*Controller|/mobile/|/h5/" ydsz-backend/ydsz-workflow/ --include="*.java"; then
    echo "❌ ydsz-workflow 模块禁止移动端适配，详见 .trae/rules/workflow-pc-only.md"
    exit 1
  fi
  ```

## 历史违规记录

- 2026-07-26：`FlowMobileController.java`（`ydsz-workflow-web`）违反本规则，已删除。该文件提供了 `/workflow/mobile/home`、`/todo`、`/task/{taskId}/quickPass`、`/task/{taskId}/quickReject`、`/task/batchPass`、`/instance/{instanceId}/timeline` 共 6 个移动端专属接口和 `MobileTodoVO` / `MobileTaskDetailVO` / `MobileTimelineVO` 3 个移动端专属 VO。

## 相关文件

- 项目记忆：`c:\Users\Marvin\.trae-cn\memory\projects\-d-Code-ydsz-ydsz-pmis\project_memory.md` → Hard Constraints → "Workflow engine is PC-only"
- 内嵌审批合规示例：[FlowEmbeddedApprovalService.java](file:///d:/Code/ydsz/ydsz-pmis/ydsz-backend/ydsz-workflow/ydsz-workflow-server/src/main/java/com/njydsz/workflow/server/service/FlowEmbeddedApprovalService.java)
- 第三方审批同步合规示例：[FlowThirdPartyApprovalController.java](file:///d:/Code/ydsz/ydsz-pmis/ydsz-backend/ydsz-workflow/ydsz-workflow-web/src/main/java/com/njydsz/workflow/web/controller/FlowThirdPartyApprovalController.java)
