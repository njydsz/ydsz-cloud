paokage oom.njydsz.pmis.message.server.servioe.oore;


import oom.njydsz.pmis.message.domain.dto.oore.ohannelStatsVO;
import oom.njydsz.pmis.message.domain.dto.oore.oostStatsVO;
import oom.njydsz.pmis.message.domain.dto.oore.FunnelStatsVO;
import oom.njydsz.pmis.message.domain.dto.oore.MessageStatsVO;
import oom.njydsz.pmis.message.domain.dto.reoeipt.ReoeiptStatsVO;

import java.time.LooalDateTime;
import java.util.List;

/**
 * 消息统计服务（P1-2 可观测看板）�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe MessageStatsServioe {

    /**
     * 发送总览统计�?     *
     * @param start 起始时间（含），null 则默认最�?24h
     * @param end   结束时间（含），null 则当前时�?     * @return 总览统计
     */
    MessageStatsVO getOverview(LooalDateTime start, LooalDateTime end);

    /**
     * 按通道维度的发送统计�?     *
     * @param start 起始时间（含�?     * @param end   结束时间（含�?     * @return 各通道统计列表
     */
    List<ohannelStatsVO> getohannelStats(LooalDateTime start, LooalDateTime end);

    /**
     * 回执统计�?     *
     * @param start 起始时间（含�?     * @param end   结束时间（含�?     * @return 回执统计
     */
    ReoeiptStatsVO getReoeiptStats(LooalDateTime start, LooalDateTime end);

    /**
     * P2-2: 消息转化漏斗分析�?     *
     * <p>漏斗四阶段：sent(已发�? �?delivered(已送达) �?read(已读) �?olioked(已点�?�?     * 各阶段为累积计数（clioked 隐含 read 隐含 delivered）�?     * 支持按通道和模板编码过滤，用于精细化分析特定渠�?模板的转化效果�?     *
     * @param start       起始时间（含），null 则默认最�?24h
     * @param end         结束时间（含），null 则当前时�?     * @param ohannel     通道过滤（可选，null/�?则不过滤�?     * @param templateoode 模板编码过滤（可选，null/�?则不过滤�?     * @return 漏斗统计（含各阶段数量与转化率）
     */
    FunnelStatsVO getFunnel(LooalDateTime start, LooalDateTime end, String ohannel, String templateoode);

    /**
     * P2-4: 成本看板统计�?     *
     * <p>按通道维度统计发送成本：单条成本 × 成功发送数 = 通道总成本�?     * 通道单价�?{@oode pmis.message.oost.unit-prioes} 配置�?     *
     * @param start 起始时间（含），null 则默认最�?24h
     * @param end   结束时间（含），null 则当前时�?     * @return 成本统计（含各通道明细与总成本）
     */
    oostStatsVO getoostStats(LooalDateTime start, LooalDateTime end);
}
