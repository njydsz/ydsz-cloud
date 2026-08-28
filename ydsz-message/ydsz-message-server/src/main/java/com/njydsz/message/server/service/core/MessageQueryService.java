package com.njydsz.message.server.service.core;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.message.domain.dto.MessageLogQueryDTO;
import com.njydsz.message.domain.repository.MsgLogRepository;
import com.njydsz.message.domain.vo.MsgLogVO;

/**
 * 消息查询服务。
 *
 * <p>负责消息发送日志的分页查询、统计聚合等只读操作。 从 {@link MessageServiceImpl}（原 God Class）中提取，与发送职责解耦。
 *
 * <p>TODO: 导出（Excel / CSV）、聚合统计（ChannelStats / CostStats / FunnelStats）待从 Controller 层下沉。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageQueryService {

  private final MsgLogRepository msgLogRepository;

  /**
   * 分页查询消息发送日志。
   *
   * @param query 查询参数（pageNum / pageSize / 多条件）
   * @return 分页结果
   */
  public PageResponse<List<MsgLogVO>> pageLog(MessageLogQueryDTO query) {
    return msgLogRepository.findPage(query);
  }
}
