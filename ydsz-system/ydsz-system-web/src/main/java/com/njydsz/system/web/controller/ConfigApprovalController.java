package com.njydsz.system.web.controller;

import java.util.List;
import java.util.stream.Collectors;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.audit.annotation.Audit;
import com.njydsz.common.audit.enums.AuditAction;
import com.njydsz.common.audit.enums.AuditType;
import com.njydsz.common.auth.util.SecurityUtils;
import com.njydsz.common.base.api.ApiVersion;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.system.domain.approval.ConfigApprovalQuery;
import com.njydsz.system.domain.approval.ConfigApprovalSubmitDTO;
import com.njydsz.system.domain.approval.ConfigApprovalVO;
import com.njydsz.system.server.converter.ConfigApprovalConverter;
import com.njydsz.system.server.service.ConfigApprovalService;

/**
 * 配置变更审批 Controller。
 *
 * <p>提供配置/字典/变量变更审批流的完整 REST 接口：提交审批、审批操作（通过/拒绝/撤回）、列表查询和详情查看。
 *
 * @author ydsz-team
 * @since 26.09.08
 */
@ApiVersion("26.09.01")
@Tag(name = "配置变更审批", description = "配置/字典/变量变更审批流")
@Slf4j
@RestController
@RequestMapping("/api/config/approval")
@RequiredArgsConstructor
public class ConfigApprovalController {

  private final ConfigApprovalService approvalService;
  private final ConfigApprovalConverter approvalConverter;

  /**
   * 查询待当前用户审批的审批单列表。
   *
   * @param query 分页 + 筛选参数
   * @return 分页结果
   */
  @GetMapping("/pending")
  @Operation(summary = "查询待审批列表")
  public YdszResponse<PageResponse<List<ConfigApprovalVO>>> listPending(ConfigApprovalQuery query) {
    String currentUserId = SecurityUtils.getCurrentUserId();
    List<ConfigApprovalVO> items = approvalService.findPendingByApprover(currentUserId, query).stream()
        .map(approvalConverter::toVO)
        .collect(Collectors.toList());
    PageResponse<List<ConfigApprovalVO>> pageResponse = new PageResponse<>();
    pageResponse.setData(items);
    pageResponse.setTotal((long) items.size());
    pageResponse.setPageNum((long) query.getPageNum());
    pageResponse.setPageSize((long) query.getPageSize());
    return YdszResponse.success(pageResponse);
  }

  /**
   * 查询当前用户发起的审批单列表。
   *
   * @param query 分页 + 筛选参数
   * @return 分页结果
   */
  @GetMapping("/submitted")
  @Operation(summary = "查询已发起审批列表")
  public YdszResponse<PageResponse<List<ConfigApprovalVO>>> listSubmitted(ConfigApprovalQuery query) {
    String currentUserId = SecurityUtils.getCurrentUserId();
    List<ConfigApprovalVO> items = approvalService.findBySubmitter(currentUserId, query).stream()
        .map(approvalConverter::toVO)
        .collect(Collectors.toList());
    PageResponse<List<ConfigApprovalVO>> pageResponse = new PageResponse<>();
    pageResponse.setData(items);
    pageResponse.setTotal((long) items.size());
    pageResponse.setPageNum((long) query.getPageNum());
    pageResponse.setPageSize((long) query.getPageSize());
    return YdszResponse.success(pageResponse);
  }

  /**
   * 查询所有审批单（管理视角）。
   *
   * @param query 分页 + 筛选参数
   * @return 分页结果
   */
  @GetMapping("/list")
  @Operation(summary = "查询全部审批单（管理视角）")
  public YdszResponse<PageResponse<List<ConfigApprovalVO>>> listAll(ConfigApprovalQuery query) {
    List<ConfigApprovalVO> items = approvalService.findAll(query).stream()
        .map(approvalConverter::toVO)
        .collect(Collectors.toList());
    PageResponse<List<ConfigApprovalVO>> pageResponse = new PageResponse<>();
    pageResponse.setData(items);
    pageResponse.setTotal((long) items.size());
    pageResponse.setPageNum((long) query.getPageNum());
    pageResponse.setPageSize((long) query.getPageSize());
    return YdszResponse.success(pageResponse);
  }

  /**
   * 提交配置变更审批。
   *
   * @param dto 提交参数
   * @return 审批单 ID
   */
  @Audit(
      module = "配置变更审批",
      type = AuditType.OPERATION,
      action = AuditAction.CREATE,
      content = "'提交配置变更审批: ' + #dto.resourceKey")
  @PostMapping("/submit")
  @Operation(summary = "提交配置变更审批")
  public YdszResponse<String> submit(@RequestBody ConfigApprovalSubmitDTO dto) {
    String userId = SecurityUtils.getCurrentUserId();
    String userName = SecurityUtils.getCurrentUserName();
    String approvalId = approvalService.submit(userId, userName, dto);
    return YdszResponse.success(approvalId);
  }

  /**
   * 通过审批单。
   *
   * @param id 审批单 ID
   * @param body 审批意见（可选，含 comment 字段）
   * @return 操作结果
   */
  @Audit(
      module = "配置变更审批",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'通过审批单: ' + #id")
  @PostMapping("/{id}/approve")
  @Operation(summary = "通过审批单")
  public YdszResponse<Boolean> approve(@PathVariable String id, @RequestBody(required = false) ApproveBody body) {
    String currentUserId = SecurityUtils.getCurrentUserId();
    String comment = body != null ? body.getComment() : null;
    approvalService.approve(id, currentUserId, comment);
    return YdszResponse.success(true);
  }

  /**
   * 拒绝审批单。
   *
   * @param id 审批单 ID
   * @param body 必填拒绝原因
   * @return 操作结果
   */
  @Audit(
      module = "配置变更审批",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'拒绝审批单: ' + #id")
  @PostMapping("/{id}/reject")
  @Operation(summary = "拒绝审批单")
  public YdszResponse<Boolean> reject(@PathVariable String id, @RequestBody RejectBody body) {
    String currentUserId = SecurityUtils.getCurrentUserId();
    approvalService.reject(id, currentUserId, body.getReason());
    return YdszResponse.success(true);
  }

  /**
   * 撤回审批单。
   *
   * @param id 审批单 ID
   * @return 操作结果
   */
  @Audit(
      module = "配置变更审批",
      type = AuditType.OPERATION,
      action = AuditAction.UPDATE,
      content = "'撤回审批单: ' + #id")
  @PostMapping("/{id}/withdraw")
  @Operation(summary = "撤回审批单")
  public YdszResponse<Boolean> withdraw(@PathVariable String id) {
    String currentUserId = SecurityUtils.getCurrentUserId();
    approvalService.withdraw(id, currentUserId);
    return YdszResponse.success(true);
  }

  /**
   * 查询审批单详情。
   *
   * @param id 审批单 ID
   * @return 审批单详情
   */
  @GetMapping("/{id}")
  @Operation(summary = "查询审批单详情")
  public YdszResponse<ConfigApprovalVO> detail(@PathVariable String id) {
    return YdszResponse.success(approvalConverter.toVO(approvalService.findById(id)));
  }

  /**
   * 审批通过请求体。
   */
  @lombok.Data
  public static class ApproveBody {
    /** 审批意见（可选） */
    private String comment;
  }

  /**
   * 拒绝审批请求体。
   */
  @lombok.Data
  public static class RejectBody {
    /** 拒绝原因（必填） */
    private String reason;
  }
}
