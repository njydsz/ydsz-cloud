package com.njydsz.workflow.server.service.impl.integration;

import com.njydsz.workflow.domain.entity.FlowThirdPartyLog;
import com.njydsz.workflow.infra.mapper.FlowThirdPartyLogMapper;
import com.njydsz.workflow.server.service.FlowThirdPartyLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 第三方审批日志服务实现。
 *
 * <p>记录与第三方审批系统（钉钉/飞书/企微）交互的完整日志 ({@code ydsz_flow_thirdparty_log})：
 *
 * <p>请求体、响应体、耗时、状态、重试次数。供问题排查与合规审计。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowThirdPartyLogServiceImpl implements FlowThirdPartyLogService {

  /** 三方对接日志 Mapper，查询分页日志记录 */
  private final FlowThirdPartyLogMapper thirdPartyLogMapper;

  @Override
  @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
  public String savePending(FlowThirdPartyLog logEntry) {
    try {
      if (logEntry == null) {
        return null;
      }
      logEntry.setHandleStatus(STATUS_PENDING);
      if (logEntry.getCreatedAt() == null) {}
      thirdPartyLogMapper.insert(logEntry);
      return logEntry.getId();
    } catch (Exception e) {
      // 日志落库失败不阻塞回调主流程
      log.error(
          "[ThirdPartyLog] 保存 PENDING 日志失败: platform={} eventType={} err={}",
          logEntry != null ? logEntry.getPlatform() : null,
          logEntry != null ? logEntry.getEventType() : null,
          e.getMessage(),
          e);
      return null;
    }
  }

  @Override
  @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
  public void updateSuccess(String id) {
    updateStatus(id, STATUS_SUCCESS, null);
  }

  @Override
  @Transactional(rollbackFor = Exception.class, propagation = Propagation.REQUIRES_NEW)
  public void updateFailed(String id, String errorMsg) {
    updateStatus(id, STATUS_FAIL, truncate(errorMsg, 512));
  }

  /**
   * 更新日志状态
   *
   * @param id 日志 ID
   * @param status 处理状态
   * @param errorMsg 失败原因
   */
  private void updateStatus(String id, String status, String errorMsg) {
    if (id == null) {
      return;
    }
    try {
      thirdPartyLogMapper.updateStatus(id, status, errorMsg);
    } catch (Exception e) {
      log.error("[ThirdPartyLog] 更新日志状态失败: id={} status={} err={}", id, status, e.getMessage(), e);
    }
  }

  /** 截断字符串到指定长度（避免超出数据库列长度限制） */
  private String truncate(String s, int maxLen) {
    if (s == null || s.length() <= maxLen) {
      return s;
    }
    return s.substring(0, maxLen);
  }
}
