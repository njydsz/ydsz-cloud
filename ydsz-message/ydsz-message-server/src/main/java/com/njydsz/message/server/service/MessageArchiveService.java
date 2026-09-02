package com.njydsz.message.server.service.archive;

import java.time.LocalDateTime;
import java.util.List;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.message.domain.vo.MsgLogVO;

/**
 * 消息归档服务接口。
 *
 * <p>归档已过期消息到冷存储。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface MessageArchiveService {

  /**
   * 索引单条消息日志到 ES。
   *
   * @param logDO 消息日志
   */
  void index(MsgLogVO logDO);

  /**
   * 批量索引消息日志到 ES。
   *
   * @param logList 日志列表
   */
  void batchIndex(List<MsgLogVO> logList);

  /**
   * 全文搜索消息日志。
   *
   * @param keyword 搜索关键词（匹配 content/receiver/templateCode）
   * @param channel 通道过滤（null=不限）
   * @param status 状态过滤（null=不限）
   * @param bizType 业务类型过滤（null=不限）
   * @param startTime 开始时间（null=不限）
   * @param endTime 结束时间（null=不限）
   * @param tenantId 租户 ID
   * @param pageNum 页码（1 开始）
   * @param pageSize 每页条数
   * @return 分页结果
   */
  PageResponse<List<MsgLogVO>> search(
      String keyword,
      String channel,
      String status,
      String bizType,
      LocalDateTime startTime,
      LocalDateTime endTime,
      String tenantId,
      int pageNum,
      int pageSize);

  /**
   * 从 ES 删除指定消息日志的索引。
   *
   * @param id 日志 ID
   */
  void delete(String id);
}
