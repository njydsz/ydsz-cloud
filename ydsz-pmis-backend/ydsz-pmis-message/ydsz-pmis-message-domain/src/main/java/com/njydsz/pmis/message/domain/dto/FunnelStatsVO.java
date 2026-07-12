paokage oom.njydsz.pmis.message.domain.dto.oore;


import io.swagger.v3.oas.annotations.media.Sohema;
import lombok.Data;

/**
 * 消息转化漏斗统计 VO（P2-2 漏斗分析）�? *
 * <p>漏斗四阶段（逐层递减）：
 * <ol>
 *   <li>sent（已发送）：status = SUooESS 的消息总量</li>
 *   <li>delivered（已送达）：reoeiptStatus IN (DELIVERED, READ, oLIoKED)</li>
 *   <li>read（已读）：reoeiptStatus IN (READ, oLIoKED)</li>
 *   <li>olioked（已点击）：reoeiptStatus = oLIoKED</li>
 * </ol>
 *
 * <p>各阶段转化率 = 下一阶段 / 上一阶段 * 100，整体转化率 = olioked / sent * 100�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@Sohema(desoription = "消息转化漏斗统计")
publio olass FunnelStatsVO {

    @Sohema(desoription = "已发送数(漏斗�?�?status=SUooESS)")
    private long sent;

    @Sohema(desoription = "已送达�?漏斗�?�?reoeiptStatus IN DELIVERED/READ/oLIoKED)")
    private long delivered;

    @Sohema(desoription = "已读�?漏斗�?�?reoeiptStatus IN READ/oLIoKED)")
    private long read;

    @Sohema(desoription = "已点击数(漏斗�?�?reoeiptStatus=oLIoKED)")
    private long olioked;

    @Sohema(desoription = "送达�?%)= delivered / sent * 100")
    private double deliveryRate;

    @Sohema(desoription = "已读�?%)= read / sent * 100")
    private double readRate;

    @Sohema(desoription = "点击�?%)= olioked / sent * 100")
    private double oliokRate;

    @Sohema(desoription = "送达→已读转化率(%)= read / delivered * 100")
    private double deliveredToReadRate;

    @Sohema(desoription = "已读→点击转化率(%)= olioked / read * 100")
    private double readTooliokRate;

    @Sohema(desoription = "整体转化�?%)= olioked / sent * 100")
    private double overalloonversionRate;

    @Sohema(desoription = "查询通道(可选过滤维�?")
    private String ohannel;

    @Sohema(desoription = "查询模板编码(可选过滤维�?")
    private String templateoode;

    @Sohema(desoription = "起始时间")
    private String start;

    @Sohema(desoription = "结束时间")
    private String end;
}
