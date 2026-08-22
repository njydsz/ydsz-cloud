package com.njydsz.cronjob.server.core.logger;

import java.util.ArrayList;
import java.util.List;

import com.lmax.disruptor.EventHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;

import com.njydsz.cronjob.domain.vo.JobLogContentVO;
import com.njydsz.cronjob.server.service.log.JobLogContentService;

/**
 * P2-3: Disruptor 日志事件消费者。
 *
 * <p>从 ring buffer 消费日志事件，批量写入 DB。 使用 {@link ObjectProvider} 延迟获取 {@link
 * JobLogContentService}，避免循环依赖。
 *
 * <h3>批量写入策略</h3>
 *
 * <ul>
 *   <li>累积到 {@link #BATCH_SIZE} 条时批量写入
 *   <li>异常时不抛出，仅记录 warn 日志（避免影响 Disruptor 消费线程）
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class DisruptorLogEventHandler implements EventHandler<DisruptorLogEvent> {

  /** 批量写入阈值 */
  private static final int BATCH_SIZE = 50;

  private final ObjectProvider<JobLogContentService> jobLogContentServiceProvider;

  /** 待写入缓冲区 */
  private final List<JobLogContentVO> buffer = new ArrayList<>(BATCH_SIZE);

  public DisruptorLogEventHandler(
      ObjectProvider<JobLogContentService> jobLogContentServiceProvider) {
    this.jobLogContentServiceProvider = jobLogContentServiceProvider;
  }

  @Override
  public void onEvent(DisruptorLogEvent event, long sequence, boolean endOfBatch) {
    try {
      buffer.add(event.toLogContent());
      // 批量写入：达到阈值或批次末尾时写入
      if (buffer.size() >= BATCH_SIZE || endOfBatch) {
        flushBuffer();
      }
    } catch (Exception e) {
      log.warn(
          "[DisruptorLog] 消费日志事件异常: logId={} lineNo={} reason={}",
          event.getLogId(),
          event.getLineNo(),
          e.getMessage());
    } finally {
      // 必须清空事件，避免数据残留影响下一次复用
      event.clear();
    }
  }

  /** 将缓冲区数据批量写入 DB */
  private void flushBuffer() {
    if (buffer.isEmpty()) {
      return;
    }
    try {
      JobLogContentService service = jobLogContentServiceProvider.getIfAvailable();
      if (service != null) {
        service.batchSave(new ArrayList<>(buffer));
      }
    } catch (Exception e) {
      log.warn("[DisruptorLog] 批量写入日志失败: count={} reason={}", buffer.size(), e.getMessage());
    } finally {
      buffer.clear();
    }
  }
}
