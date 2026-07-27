package com.njydsz.message.server.service.batch;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.njydsz.message.domain.entity.batch.MsgBatch;
import com.njydsz.message.server.realtime.RealtimePushService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 批量发送实时进度推送器（P1-10）。
 *
 * <p>批量发送过程中，按进度阈值（每 10% 或每 100 条）通过 WebSocket 推送实时进度,
 * 发起人可在前端看到批量发送的实时状态。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BatchProgressPusher {

    private final RealtimePushService realtimePushService;

    /** 进度推送阈值（每 N 条推送一次） */
    private static final int BATCH_PUSH_THRESHOLD = 100;

    /**
     * 推送批量发送进度。
     *
     * @param batch   批次实体
     * @param senderId 发起人 ID
     */
    public void pushProgress(MsgBatch batch, String senderId) {
        if (batch == null || senderId == null) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "BATCH_PROGRESS");
        payload.put("batchId", batch.getBatchId());
        payload.put("batchName", batch.getBatchName());
        payload.put("total", batch.getTotal());
        payload.put("success", batch.getSuccess());
        payload.put("failed", batch.getFailed());
        payload.put("skipped", batch.getSkipped());
        payload.put("status", batch.getStatus());
        // 计算进度百分比
        int total = batch.getTotal() == null ? 0 : batch.getTotal();
        int done = (batch.getSuccess() == null ? 0 : batch.getSuccess())
                + (batch.getFailed() == null ? 0 : batch.getFailed())
                + (batch.getSkipped() == null ? 0 : batch.getSkipped());
        double progress = total > 0 ? (double) done / total * 100 : 0;
        payload.put("progress", Math.round(progress * 100.0) / 100.0);

        try {
            realtimePushService.pushToUser(senderId, "/topic/batch-progress", payload);
            log.debug("[BatchProgress] 推送进度: batchId={} progress={}%",
                    batch.getBatchId(), Math.round(progress));
        } catch (Exception e) {
            log.warn("[BatchProgress] 推送失败: batchId={} err={}", batch.getBatchId(), e.getMessage(), e);
        }
    }

    /**
     * 判断是否需要推送进度（避免高频推送）。
     *
     * @param done 已完成数
     * @return true 表示达到推送阈值
     */
    public boolean shouldPush(int done) {
        return done > 0 && done % BATCH_PUSH_THRESHOLD == 0;
    }
}
