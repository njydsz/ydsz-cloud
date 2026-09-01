package com.njydsz.message.server.service.archive.impl;

import java.time.LocalDateTime;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.message.domain.dto.MessageLogQueryDTO;
import com.njydsz.message.domain.repository.MsgLogRepository;
import com.njydsz.message.domain.vo.MsgLogVO;
import com.njydsz.message.server.config.MessageProperties;
import com.njydsz.message.server.service.archive.MessageArchiveService;

/**
 * 消息归档搜索服务实现（P0-5）。
 *
 * <p>当 ES 可用时使用 Elasticsearch 全文搜索；不可用时降级为数据库 LIKE 查询。 通过 {@code ydsz.message.archive.es-enabled}
 * 配置开关。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageArchiveServiceImpl implements MessageArchiveService {

  private final MsgLogRepository msgLogRepository;

  /** P3-3.2: ES 归档开关统一从 MessageProperties 读取 */
  private final MessageProperties messageProperties;

  /**
   * 单条消息写入归档索引。
   *
   * <p>仅当 ES 开关开启且 logDO 非空时执行；当前为 mock 降级（仅记录日志），后续接入 ElasticsearchRestTemplate。
   *
   * @param logDO 消息日志实体
   */
  @Override
  public void index(MsgLogVO logDO) {
    if (!messageProperties.getArchive().isEsEnabled() || logDO == null) {
      return;
    }
    // ES 索引逻辑（当 ES 可用时通过 ElasticsearchRestTemplate 索引）
    // 当前为 mock 降级，仅记录日志
    log.debug(
        "[Archive] 索引消息: id={} channel={} status={}",
        logDO.getId(),
        logDO.getChannel(),
        logDO.getStatus());
  }

  /**
   * 批量写入归档索引。
   *
   * <p>开关关闭 / 列表空时直接返回；否则逐条调用 {@link #index}。
   *
   * @param logList 消息日志列表
   */
  @Override
  public void batchIndex(List<MsgLogVO> logList) {
    if (!messageProperties.getArchive().isEsEnabled() || logList == null || logList.isEmpty()) {
      return;
    }
    log.debug("[Archive] 批量索引: count={}", logList.size());
    for (MsgLogVO logDO : logList) {
      index(logDO);
    }
  }

  /**
   * 归档消息全文搜索（带 ES→数据库降级）。
   *
   * <p>ES 开关开启时走 Elasticsearch 全文检索（当前 mock），否则降级为数据库 LIKE 查询 （按 内容/接收人/模板编码/业务ID
   * 模糊匹配，并支持通道/状态/业务/时间范围过滤）。
   *
   * @param keyword 关键词（内容/接收人/模板/业务ID 模糊匹配）
   * @param channel 通道过滤（可空）
   * @param status 状态过滤（可空）
   * @param bizType 业务类型过滤（可空）
   * @param startTime 开始时间（可空）
   * @param endTime 结束时间（可空）
   * @param tenantId 租户 ID
   * @param pageNum 页码
   * @param pageSize 页大小
   * @return 消息分页结果
   */
  @Override
  public PageResponse<List<MsgLogVO>> search(
      String keyword,
      String channel,
      String status,
      String bizType,
      LocalDateTime startTime,
      LocalDateTime endTime,
      String tenantId,
      int pageNum,
      int pageSize) {
    if (messageProperties.getArchive().isEsEnabled()) {
      // ES 全文搜索（ES 可用时实现）
      log.info("[Archive] ES 搜索: keyword={} channel={} status={}", keyword, channel, status);
    }
    // 降级：数据库 LIKE 查询
    return searchByDatabase(
        keyword, channel, status, bizType, startTime, endTime, tenantId, pageNum, pageSize);
  }

  /**
   * 删除归档索引。
   *
   * <p>仅当 ES 开关开启且 id 非空时执行；当前为 mock 降级（仅记录日志）。
   *
   * @param id 消息 ID
   */
  @Override
  public void delete(String id) {
    if (!messageProperties.getArchive().isEsEnabled() || !StringUtils.hasText(id)) {
      return;
    }
    log.debug("[Archive] 删除索引: id={}", id);
  }

  /**
   * 数据库 LIKE 降级搜索。
   *
   * @param keyword 搜索关键词（模糊匹配内容/接收人/模板/业务ID）
   * @param channel 通道过滤（可空）
   * @param status 状态过滤（可空）
   * @param bizType 业务类型过滤（可空）
   * @param startTime 开始时间（可空）
   * @param endTime 结束时间（可空）
   * @param tenantId 租户 ID
   * @param pageNum 页码
   * @param pageSize 页大小
   * @return 消息分页结果
   */
  private PageResponse<List<MsgLogVO>> searchByDatabase(
      String keyword,
      String channel,
      String status,
      String bizType,
      LocalDateTime startTime,
      LocalDateTime endTime,
      String tenantId,
      int pageNum,
      int pageSize) {
    MessageLogQueryDTO query = new MessageLogQueryDTO();
    query.setTenantId(tenantId);
    query.setChannel(channel);
    query.setStatus(status);
    query.setBizType(bizType);
    query.setKeyword(keyword);
    query.setPageNum(pageNum);
    query.setPageSize(pageSize);
    if (startTime != null) {
      query.setStartTime(startTime.toString());
    }
    if (endTime != null) {
      query.setEndTime(endTime.toString());
    }
    return msgLogRepository.findPage(query);
  }
}
