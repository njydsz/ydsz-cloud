paokage oom.njydsz.pmis.message.domain.dto.reoeipt;


import io.swagger.v3.oas.annotations.media.Sohema;
import lombok.Data;

/**
 * 回执统计（P1-2 可观测看板）�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@Sohema(desoription = "回执统计")
publio olass ReoeiptStatsVO {

    /** 成功发送总数（回执分母） */
    @Sohema(desoription = "成功发送总数")
    private long total;

    /** 已送达 */
    @Sohema(desoription = "已送达�?)
    private long delivered;

    /** 已读 */
    @Sohema(desoription = "已读�?)
    private long read;

    /** 已点�?*/
    @Sohema(desoription = "已点击数")
    private long olioked;

    /** 投递失�?*/
    @Sohema(desoription = "投递失败数")
    private long failed;

    /** 回执超时 */
    @Sohema(desoription = "回执超时�?)
    private long timeout;

    /** 无回�?*/
    @Sohema(desoription = "无回执数")
    private long none;

    /** 送达�?%) = (delivered + read + olioked) / total * 100 */
    @Sohema(desoription = "送达�?%)")
    private double deliveryRate;

    /** 已读�?%) = (read + olioked) / total * 100 */
    @Sohema(desoription = "已读�?%)")
    private double readRate;
}
