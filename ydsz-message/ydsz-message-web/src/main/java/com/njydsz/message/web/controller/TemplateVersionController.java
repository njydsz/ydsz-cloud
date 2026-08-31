package com.njydsz.message.web.controller.template;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.auth.annotation.AuthApiPermission;
import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.feign.MessageResult;
import com.njydsz.common.lock.annotation.Idempotent;
import com.njydsz.common.lock.annotation.IdempotentExempt;
import com.njydsz.common.permission.PermissionCodes;
import com.njydsz.common.safe.ratelimit.annotation.RateLimit;
import com.njydsz.message.domain.dto.TemplatePreviewDTO;
import com.njydsz.message.domain.dto.TemplateTestSendDTO;
import com.njydsz.message.infra.entity.MsgTemplateVersion;
import com.njydsz.message.server.service.template.TemplateVersionService;

/**
 * 模板版本管理与可视化（Template Version）Controller。
 *
 * <p>提供<b>模板版本历史、回滚、预览渲染、试发</b>的 HTTP API， 是 P1-6「模板版本化」的核心入口。每个模板的每次发布都生成版本快照， 支持回滚到任意历史版本，避免发版失误。
 *
 * <p><b>接口路径：</b>{@code /api/v1/message/template/version/**}
 *
 * <p><b>核心能力：</b>
 *
 * <ul>
 *   <li><b>版本历史</b>：{@code GET /list/{templateCode}} — 查询某模板的全部历史版本（按版本号倒序）
 *   <li><b>版本回滚</b>：{@code POST /rollback} — 回滚到指定版本（生成新版本号，内容复制自历史版本）
 *   <li><b>模板预览</b>：{@code POST /preview} — 给定模板和参数预览渲染结果
 *   <li><b>模板试发</b>：{@code POST /testSend} — 向测试接收人发送模板（验证渲染效果）
 * </ul>
 *
 * <p><b>版本回滚语义：</b>回滚不会删除当前版本，而是基于指定历史版本<b>生成新版本</b>（版本号 +1）， 保留完整的版本历史，避免「回滚后无法回滚回去」的问题。
 *
 * <p><b>试发 vs 正式发送：</b>
 *
 * <ul>
 *   <li><b>试发</b>（{@code /testSend}）：仅向 {@code testReceivers} 中配置的接收人发送，不计入正式统计
 *   <li><b>正式发送</b>：通过 {@code MessageController.send} 调用，按正常发送流程
 * </ul>
 *
 * <p><b>与 TemplateController 的关系：</b>
 *
 * <ul>
 *   <li>TemplateController：模板的 CRUD + 状态机（DRAFT / PUBLISHED / OFFLINE）
 *   <li>本 Controller：模板的版本管理（历史 / 回滚 / 预览 / 试发）
 * </ul>
 *
 * <p><b>多租户隔离：</b>所有版本按 {@code tenantId} 隔离，跨租户版本不可见。
 *
 * <p><b>安全特性：</b>
 *
 * <ul>
 *   <li>写接口（rollback / testSend）启用 {@link Idempotent} 5s 防重
 *   <li>写接口启用 {@link RateLimit} 50 QPS 限流
 *   <li>写接口启用 {@link Audit} 审计日志（异步持久化）
 *   <li>读接口（preview）通过 {@link com.njydsz.common.lock.annotation.IdempotentExempt} 豁免幂等
 *   <li>权限模型：通过 {@code @AuthApiPermission} 校验 {@link PermissionCodes#NOTIF_TEMPLATE_AUDIT} 权限码
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see com.njydsz.message.server.service.template.TemplateVersionService 模板版本服务
 * @see com.njydsz.message.domain.entity.template.MsgTemplateVersion 模板版本实体
 */
@Slf4j
@Tag(name = "模板版本管理", description = "版本历史、回滚、预览、试发")
@RestController
@RequestMapping("/api/v1/message/template/version")
@RequiredArgsConstructor
public class TemplateVersionController {

  /** 模板版本管理服务 */
  private final TemplateVersionService templateVersionService;

  /**
   * 查询模板版本历史。
   *
   * @param templateCode 模板编码
   * @return 统一响应结果，包含版本列表
   */
  @Operation(summary = "查询模板版本历史")
  @AuthApiPermission(apiCodes = PermissionCodes.NOTIF_TEMPLATE_VIEW)
  @GetMapping("/list/{templateCode}")
  public YdszResponse<List<MsgTemplateVersion>> listVersions(@PathVariable String templateCode) {
    return YdszResponse.success(templateVersionService.listVersions(templateCode));
  }

  /**
   * 回滚到指定版本。
   *
   * @param templateCode 模板编码
   * @param version 目标版本号
   * @return 统一响应结果，包含新版本 ID
   */
  @Operation(summary = "回滚到指定版本")
  @AuthApiPermission(apiCodes = PermissionCodes.NOTIF_TEMPLATE_AUDIT)
  @Idempotent(key = "ydsz:message:TemplateVersionController:rollback:lock", ttlSeconds = 5)
  @Audit(
      module = "模板版本管理",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'rollback'")
  @RateLimit(resource = "message.templateversion.rollback", threshold = 50)
  @PostMapping("/rollback")
  public YdszResponse<String> rollback(
      @RequestParam String templateCode, @RequestParam int version) {
    return YdszResponse.success(templateVersionService.rollbackToVersion(templateCode, version));
  }

  /**
   * 预览模板渲染结果。
   *
   * @param dto 预览请求体
   * @return 统一响应结果，包含渲染后的内容
   */
  @Operation(summary = "预览模板渲染结果")
  @AuthApiPermission(apiCodes = PermissionCodes.NOTIF_TEMPLATE_VIEW)
  @IdempotentExempt("查询/导出/预览/模拟语义接口，无需幂等")
  @RateLimit(resource = "message.templateversion.preview", threshold = 50)
  @Idempotent(key = "ydsz:message:TemplateVersionController:preview:lock", ttlSeconds = 5)
  @PostMapping("/preview")
  public YdszResponse<String> preview(@Valid @RequestBody TemplatePreviewDTO dto) {
    if (dto == null) {
      return YdszResponse.error(YdszResultCode.BAD_REQUEST, "预览参数为空");
    }
    return YdszResponse.success(templateVersionService.preview(dto));
  }

  /**
   * 试发模板（向测试接收人发送）。
   *
   * @param dto 试发请求体
   * @return 统一响应结果，包含发送结果
   */
  @Operation(summary = "试发模板（向测试接收人发送）")
  @AuthApiPermission(apiCodes = PermissionCodes.NOTIF_TEMPLATE_AUDIT)
  @Idempotent(key = "ydsz:message:TemplateVersionController:testSend:lock", ttlSeconds = 5)
  @Audit(
      module = "模板版本管理",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'testSend'")
  @RateLimit(resource = "message.templateversion.testSend", threshold = 50)
  @PostMapping("/testSend")
  public YdszResponse<MessageResult> testSend(@Valid @RequestBody TemplateTestSendDTO dto) {
    if (dto == null) {
      return YdszResponse.error(YdszResultCode.BAD_REQUEST, "试发参数为空");
    }
    return YdszResponse.success(templateVersionService.testSend(dto));
  }
}
