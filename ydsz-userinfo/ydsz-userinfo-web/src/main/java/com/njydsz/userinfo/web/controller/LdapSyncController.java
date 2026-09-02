package com.njydsz.userinfo.web.controller;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.userinfo.server.auth.LdapOrgSyncService;
import com.njydsz.userinfo.server.auth.LdapOrgSyncService.SyncResult;

/**
 * LDAP/AD 组织架构同步管理端 API。
 *
 * <p>提供手动触发同步、查询同步状态、查询同步历史的能力，供管理员在后台管理系统中使用。
 *
 * <p><b>接口路径：</b>{@code /api/v1/admin/ldap/sync}
 *
 * <p><b>启用条件：</b>{@code ydsz.userinfo.ldap.sync.enabled=true}。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/ldap/sync")
@ConditionalOnProperty(prefix = "ydsz.userinfo.ldap.sync", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Tag(name = "LDAP 同步管理", description = "LDAP/AD 组织架构同步触发、状态查询、历史记录")
public class LdapSyncController {

  /** 最大保留的同步历史记录数。 */
  private static final int MAX_HISTORY_SIZE = 50;

  private final LdapOrgSyncService ldapOrgSyncService;

  /** 同步历史记录（线程安全）。 */
  private final List<SyncLogVO> syncHistory = new CopyOnWriteArrayList<>();

  /** 当前同步状态。 */
  private volatile SyncStatusVO currentStatus = new SyncStatusVO("IDLE", null, null);

  /**
   * 手动触发 LDAP 同步。
   *
   * <p>执行完整的部门 + 用户同步流程。同步过程中会通过分布式锁防止并发执行。
   *
   * @return 同步结果（包含处理总数、新增、更新、停用、失败数）
   */
  @PostMapping
  @Operation(summary = "手动触发 LDAP 同步")
  public YdszResponse<SyncResultVO> triggerSync() {
    log.info("Manual LDAP sync triggered via API");
    currentStatus = new SyncStatusVO("RUNNING", LocalDateTime.now(), null);

    try {
      SyncResult result = ldapOrgSyncService.syncAll();
      SyncResultVO resultVO = new SyncResultVO(
          result.totalProcessed(),
          result.created(),
          result.updated(),
          result.deactivated(),
          result.failed(),
          result.errors());

      // 记录同步历史
      addSyncLog("SUCCESS", resultVO);
      currentStatus = new SyncStatusVO("COMPLETED", null, LocalDateTime.now());

      return YdszResponse.success(resultVO);
    } catch (Exception e) {
      log.error("Manual LDAP sync failed: {}", e.getMessage(), e);
      currentStatus = new SyncStatusVO("FAILED", null, LocalDateTime.now());
      addSyncLog("FAILED", null);
      throw e;
    }
  }

  /**
   * 查询当前同步状态。
   *
   * @return 同步状态（IDLE/RUNNING/COMPLETED/FAILED + 时间戳）
   */
  @GetMapping("/status")
  @Operation(summary = "查询同步状态")
  public YdszResponse<SyncStatusVO> getStatus() {
    return YdszResponse.success(currentStatus);
  }

  /**
   * 查询同步历史记录。
   *
   * <p>返回最近 {@value #MAX_HISTORY_SIZE} 次同步的记录，按时间倒序排列。
   *
   * @return 同步历史列表
   */
  @GetMapping("/logs")
  @Operation(summary = "查询同步历史")
  public YdszResponse<List<SyncLogVO>> getLogs() {
    return YdszResponse.success(new ArrayList<>(syncHistory));
  }

  /**
   * 添加同步历史记录。
   *
   * @param status 同步状态
   * @param result 同步结果（可能为 null）
   */
  private void addSyncLog(String status, SyncResultVO result) {
    SyncLogVO log = new SyncLogVO(LocalDateTime.now(), status, result);
    syncHistory.add(0, log);
    // 限制历史记录数量
    if (syncHistory.size() > MAX_HISTORY_SIZE) {
      syncHistory.remove(syncHistory.size() - 1);
    }
  }

  /**
   * 同步状态 VO。
   *
   * @param status 状态：IDLE/RUNNING/COMPLETED/FAILED
   * @param startTime 开始时间
   * @param endTime 结束时间
   */
  public record SyncStatusVO(String status, LocalDateTime startTime, LocalDateTime endTime) {}

  /**
   * 同步结果 VO。
   *
   * @param totalProcessed 处理总数
   * @param created 新增数
   * @param updated 更新数
   * @param deactivated 停用数
   * @param failed 失败数
   * @param errors 错误详情
   */
  public record SyncResultVO(
      int totalProcessed,
      int created,
      int updated,
      int deactivated,
      int failed,
      List<String> errors) {}

  /**
   * 同步历史记录 VO。
   *
   * @param timestamp 执行时间
   * @param status 同步状态
   * @param result 同步结果
   */
  public record SyncLogVO(LocalDateTime timestamp, String status, SyncResultVO result) {}
}
