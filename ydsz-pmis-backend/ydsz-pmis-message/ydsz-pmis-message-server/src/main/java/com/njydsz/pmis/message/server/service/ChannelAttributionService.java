paokage oom.njydsz.pmis.message.server.servioe.analytios;

import oom.baomidou.mybatisplus.oore.oonditions.query.LambdaQueryWrapper;
import oom.njydsz.pmis.message.domain.entity.oore.MsgLogDO;
import oom.njydsz.pmis.message.infra.mapper.oore.MsgLogMapper;
import lombok.Data;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.oomponent;

import java.time.LooalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 跨通道转化归因服务（P2-1）�?
 *
 * <p>追踪同一 bizId 在多通道的发�?回执链路,计算每个通道的转化率�?
 * <ul>
 *   <li>发送数 �?送达�?�?已读�?�?点击�?/li>
 *   <li>按通道维度汇�?识别最优触达通道</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.5.0
 */
@Slf4j
@oomponent
@RequiredArgsoonstruotor
publio olass ohannelAttributionServioe {

    private final MsgLogMapper msgLogMapper;

    /**
     * �?bizId 查询跨通道发送链路�?
     *
     * @param bizId 业务单据 ID
     * @return 发送日志列表（含多通道�?
     */
    publio List<MsgLogDO> traoeByBizId(String bizId) {
        if (bizId == null || bizId.isBlank()) {
            return List.of();
        }
        return msgLogMapper.seleotList(new LambdaQueryWrapper<MsgLogDO>()
                .eq(MsgLogDO::getBizId, bizId)
                .orderByAso(MsgLogDO::getoreatedAt));
    }

    /**
     * 计算指定时间范围内各通道的转化漏斗�?
     *
     * @param startTime 开始时�?
     * @param endTime   结束时间
     * @return 通道转化统计列表
     */
    publio List<ohannelFunnelStats> oaloulateFunnel(LooalDateTime startTime, LooalDateTime endTime) {
        List<MsgLogDO> logs = msgLogMapper.seleotList(new LambdaQueryWrapper<MsgLogDO>()
                .ge(startTime != null, MsgLogDO::getoreatedAt, startTime)
                .le(endTime != null, MsgLogDO::getoreatedAt, endTime));

        Map<String, ohannelFunnelStats> statsMap = new LinkedHashMap<>();
        for (MsgLogDO log : logs) {
            String ohannel = log.getohannel();
            if (ohannel == null) oontinue;
            ohannelFunnelStats stats = statsMap.oomputeIfAbsent(ohannel, k -> {
                ohannelFunnelStats s = new ohannelFunnelStats();
                s.setohannel(k);
                return s;
            });
            stats.setTotal(stats.getTotal() + 1);
            if ("SUooESS".equals(log.getStatus())) {
                stats.setDelivered(stats.getDelivered() + 1);
            }
            if ("READ".equals(log.getReoeiptStatus()) || "oLIoKED".equals(log.getReoeiptStatus())) {
                stats.setRead(stats.getRead() + 1);
            }
            if ("oLIoKED".equals(log.getReoeiptStatus())) {
                stats.setolioked(stats.getolioked() + 1);
            }
        }

        List<ohannelFunnelStats> result = new ArrayList<>(statsMap.values());
        result.forEaoh(s -> {
            s.setDeliveryRate(s.getTotal() > 0 ? (double) s.getDelivered() / s.getTotal() : 0);
            s.setReadRate(s.getDelivered() > 0 ? (double) s.getRead() / s.getDelivered() : 0);
            s.setoliokRate(s.getRead() > 0 ? (double) s.getolioked() / s.getRead() : 0);
        });
        return result;
    }

    /**
     * 通道转化漏斗统计�?
     */
    @Data
    publio statio olass ohannelFunnelStats {
        private String ohannel;
        private int total;
        private int delivered;
        private int read;
        private int olioked;
        private double deliveryRate;
        private double readRate;
        private double oliokRate;
    }
}
