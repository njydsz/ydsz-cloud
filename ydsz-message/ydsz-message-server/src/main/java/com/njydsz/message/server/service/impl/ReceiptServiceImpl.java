package com.njydsz.message.server.service.impl.receipt;

import java.time.LocalDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.queue.trace.MessageTracer;
import com.njydsz.common.tenant.TenantContextHolder;
import com.njydsz.message.domain.dto.ReceiptCallbackDTO;
import com.njydsz.message.domain.query.MsgReceiptQuery;
import com.njydsz.message.domain.repository.MsgReceiptRepository;
import com.njydsz.message.domain.vo.MsgReceiptVO;
import com.njydsz.message.server.service.core.MessageLogService;
import com.njydsz.message.server.service.receipt.ReceiptService;

/**
 * 回执服务实现。
 *
 * <p>管理消息送达/已读/点击回执 ({@code ydsz_msg_receipt})，包括 IM 渠道 Webhook 接收、
 *
 * <p>Email 追踪像素/链接跳转、App Push 回调。
 *
 * <p>回执更新触发 {@code OperationLog} 异步落库与前端实时通知。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReceiptServiceImpl implements ReceiptService {

  /** 消息回执 Repository */
  private final MsgReceiptRepository msgReceiptRepository;

  /** 消息日志服务（联动更新回执状态） */
  private final MessageLogService messageLogService;

  /**
   * 处理渠道回执回调（送达/已读/点击）。
   *
   * <p>将回执落库 {@code ydsz_msg_receipt} 并联动更新消息日志回执状态；进入追踪上下文（外部回调无原始 traceId 时自动生成）。
   * 日志不存在仅告警不抛异常，保证回执可靠落库。dto 或 logId 为空抛 BAD_REQUEST。
   *
   * @param dto 回执回调数据（含 logId/回执类型/渠道信息等）
   * @throws com.njydsz.common.exception.custom.SysException dto 或关联 logId 为空时
   */
  @Override
  public void callback(ReceiptCallbackDTO dto) {
    if (dto == null || !StringUtils.hasText(dto.getLogId())) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("回执关联日志 ID 不能为空")
          .build();
    }
    // P1-3: 回执回调进入追踪上下文（外部回调无原始 traceId，自动生成）
    try (MessageTracer.MessageTraceScope scope = MessageTracer.enter(null)) {
      MsgReceiptVO entity = new MsgReceiptVO();
      entity.setLogId(dto.getLogId());
      entity.setProviderTraceId(dto.getProviderTraceId());
      entity.setReceiptType(dto.getReceiptType());
      entity.setReceiptTime(LocalDateTime.now());
      entity.setProviderCode(dto.getProviderCode());
      entity.setProviderMsg(dto.getProviderMsg());
      entity.setRawResponse(dto.getRawResponse());
      entity.setTenantId(TenantContextHolder.getTenantId());
      msgReceiptRepository.save(entity);
      // 联动更新日志回执状态
      try {
        messageLogService.updateReceipt(
            dto.getLogId(), dto.getReceiptType(), entity.getReceiptTime());
      } catch (Exception e) {
        // 日志不存在时仅记录，不影响回执落库
        log.warn("[Receipt] 更新日志回执失败: logId={} err={}", dto.getLogId(), e.getMessage(), e);
      }
      log.info("[Receipt] 回执落库: logId={} type={}", dto.getLogId(), dto.getReceiptType());
    }
  }

  /**
   * 按消息日志 ID 查询回执列表（按回执时间倒序）。
   *
   * <p>用于前端展示某条消息的送达/已读/点击回执轨迹；logId 为空返回空列表。
   *
   * @param logId 消息日志 ID
   * @return 回执列表（按 receiptTime 降序）；无结果返回空列表
   */
  @Override
  public List<MsgReceiptVO> listByLogId(String logId) {
    if (!StringUtils.hasText(logId)) {
      return List.of();
    }
    MsgReceiptQuery query = new MsgReceiptQuery();
    query.setLogId(logId);
    return msgReceiptRepository.findList(query);
  }
}
