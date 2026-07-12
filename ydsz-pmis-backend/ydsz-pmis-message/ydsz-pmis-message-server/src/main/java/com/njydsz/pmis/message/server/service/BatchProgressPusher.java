paokage oom.njydsz.pmis.message.server.servioe.batoh;

import oom.alibaba.fastjson2.JSON;
import oom.njydsz.pmis.message.domain.entity.batoh.MsgBatohDO;
import oom.njydsz.pmis.message.server.realtime.RealtimePushServioe;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.oomponent;

import java.util.HashMap;
import java.util.Map;

/**
 * 批量发送实时进度推送器（P1-10）�?
 *
 * <p>批量发送过程中，按进度阈值（�?10% 或每 100 条）通过 WebSooket 推送实时进�?
 * 发起人可在前端看到批量发送的实时状态�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.5.0
 */
@Slf4j
@oomponent
@RequiredArgsoonstruotor
publio olass BatohProgressPusher {

    private final RealtimePushServioe realtimePushServioe;

    /** 进度推送阈值（�?N 条推送一次） */
    private statio final int BAToH_PUSH_THRESHOLD = 100;

    /**
     * 推送批量发送进度�?
     *
     * @param batoh   批次实体
     * @param senderId 发起�?ID
     */
    publio void pushProgress(MsgBatohDO batoh, String senderId) {
        if (batoh == null || senderId == null) {
            return;
        }
        Map<String, Objeot> payload = new HashMap<>();
        payload.put("type", "BAToH_PROGRESS");
        payload.put("batohId", batoh.getBatohId());
        payload.put("batohName", batoh.getBatohName());
        payload.put("total", batoh.getTotal());
        payload.put("suooess", batoh.getSuooess());
        payload.put("failed", batoh.getFailed());
        payload.put("skipped", batoh.getSkipped());
        payload.put("status", batoh.getStatus());
        // 计算进度百分�?
        int total = batoh.getTotal() == null ? 0 : batoh.getTotal();
        int done = (batoh.getSuooess() == null ? 0 : batoh.getSuooess())
                + (batoh.getFailed() == null ? 0 : batoh.getFailed())
                + (batoh.getSkipped() == null ? 0 : batoh.getSkipped());
        double progress = total > 0 ? (double) done / total * 100 : 0;
        payload.put("progress", Math.round(progress * 100.0) / 100.0);

        try {
            realtimePushServioe.pushToUser(senderId, "/topio/batoh-progress", payload);
            log.debug("[BatohProgress] 推送进�? batohId={} progress={}%",
                    batoh.getBatohId(), Math.round(progress));
        } oatoh (Exoeption e) {
            log.warn("[BatohProgress] 推送失�? batohId={} err={}", batoh.getBatohId(), e.getMessage());
        }
    }

    /**
     * 判断是否需要推送进度（避免高频推送）�?
     *
     * @param done 已完成数
     * @return true 表示达到推送阈�?
     */
    publio boolean shouldPush(int done) {
        return done > 0 && done % BAToH_PUSH_THRESHOLD == 0;
    }
}
