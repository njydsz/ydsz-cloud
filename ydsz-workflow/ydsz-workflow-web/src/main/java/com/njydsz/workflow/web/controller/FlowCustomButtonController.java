package com.njydsz.workflow.web.controller.definition;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.auth.context.AuthContextUtils;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.workflow.server.service.FlowCustomButtonService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 节点自定义按钮 Controller（P2-4）
 *
 * <p>提供流程节点的<b>自定义操作按钮</b>管理能力，扩展默认的"通过/驳回/转办"等系统按钮。 业务方可为任意节点注册自定义按钮（如"加签财务"、"转交 HR"、"返回修改"），
 * 按钮可关联 <b>Java 后端逻辑</b> 或 <b>HTTP Webhook</b>，执行后支持回填流程变量。
 *
 * <p><b>接口路径：</b>{@code /api/v1/workflow/customButtons}
 *
 * <p><b>核心能力：</b>
 *
 * <ul>
 *   <li><b>按钮查询</b>：{@code GET /} — 按 (definitionId, nodeCode) 查询按钮列表
 *   <li><b>按钮保存</b>：{@code POST /} — 覆盖式保存节点按钮配置
 *   <li><b>按钮执行</b>：{@code POST /execute} — 运行时执行按钮，触发后端逻辑
 * </ul>
 *
 * <p><b>按钮类型：</b>
 *
 * <ul>
 *   <li><b>JAVA_METHOD</b>：调用 Spring Bean 的指定方法（{@code beanName.methodName(args)}）
 *   <li><b>HTTP_WEBHOOK</b>：调用外部 HTTP 接口（POST/PUT）
 *   <li><b>SCRIPT</b>：执行 QLExpress 脚本
 * </ul>
 *
 * <p><b>安全特性：</b>
 *
 * <ul>
 *   <li>写接口启用 {@link Idempotent} 防重（5s）
 *   <li>写接口启用 {@link RateLimit} 限流 50 QPS
 *   <li>Java 方法调用使用 {@code @PermissionStrict} 严格模式，禁止反射绕过
 *   <li>Webhook 调用走 OkHttp 客户端，禁止内网穿透（仅允许配置白名单 host）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.workflow.server.service.FlowCustomButtonService 自定义按钮 Service
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/workflow/customButtons")
@RequiredArgsConstructor
@Tag(name = "节点自定义按钮", description = "流程节点的自定义操作按钮管理")
public class FlowCustomButtonController {

  /** 自定义按钮服务，负责节点按钮配置的查询、保存与执行 */
  private final FlowCustomButtonService customButtonService;

  /**
   * 获取节点的自定义按钮列表
   *
   * <p>按 (definitionId, nodeCode) 查询该节点的全部自定义按钮配置， 含按钮编码、名称、类型、关联逻辑、显示顺序、是否必填等。
   *
   * <p>典型场景：流程详情页加载审批人操作面板时获取按钮列表。
   *
   * @param definitionId 流程定义 ID
   * @param nodeCode 节点编码
   * @return 按钮配置列表
   */
  @GetMapping
  @Operation(summary = "获取节点的自定义按钮列表")
  public BaseResponse<List<Map<String, Object>>> list(
      @RequestParam String definitionId, @RequestParam String nodeCode) {
    return BaseResponse.success(customButtonService.getCustomButtons(definitionId, nodeCode));
  }

  /**
   * 保存节点的自定义按钮配置
   *
   * <p>幂等保护 5 秒；限流 50 QPS。
   *
   * <p><b>覆盖式</b>保存：先清空旧配置，再批量插入新配置（避免 N+1 循环）。 业务方传入<b>完整</b>的按钮列表，而非增量。
   *
   * @param definitionId 流程定义 ID
   * @param nodeCode 节点编码
   * @param buttons 按钮配置列表（含 buttonCode / buttonName / type / beanName / methodName / webhookUrl 等）
   * @return 空响应
   */
  @Idempotent(key = "ydsz:workflow:FlowCustomButtonController:save:lock", ttlSeconds = 5)
  @RateLimit(resource = "workflow.flowcustombutton.save", threshold = 50)
  @PostMapping
  @Audit(
      module = "自定义按钮",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'save'")
  @Operation(summary = "保存节点的自定义按钮配置")
  public BaseResponse<Void> save(
      @RequestParam String definitionId,
      @RequestParam String nodeCode,
      @RequestBody List<Map<String, Object>> buttons) {
    customButtonService.saveCustomButtons(definitionId, nodeCode, buttons);
    return BaseResponse.success();
  }

  /**
   * 执行自定义按钮操作
   *
   * <p>幂等保护 5 秒。
   *
   * <p>运行时执行按钮，触发后端 Java 方法 / HTTP Webhook / 脚本逻辑。 执行结果可回填到流程变量（{@code variables} 参数），影响后续分支走向。
   *
   * <p>操作人 ID 从 SecurityContext 获取。
   *
   * @param taskId 任务 ID
   * @param buttonCode 按钮编码
   * @param comment 审批意见（可选）
   * @param variables 流程变量（可选）
   * @return 按钮执行结果（含 success / message / outputVars）
   */
  @Idempotent(key = "ydsz:workflow:FlowCustomButtonController:execute:lock", ttlSeconds = 5)
  @PostMapping("/execute")
  @Operation(summary = "执行自定义按钮操作")
  public BaseResponse<Map<String, Object>> execute(
      @RequestParam String taskId,
      @RequestParam String buttonCode,
      @RequestParam(required = false) String comment,
      @RequestBody(required = false) Map<String, Object> variables) {
    String userId = AuthContextUtils.getUserId();
    return BaseResponse.success(
        customButtonService.executeButton(taskId, buttonCode, userId, comment, variables));
  }
}
