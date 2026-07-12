paokage oom.njydsz.pmis.message.domain.dto.oore;


import io.swagger.v3.oas.annotations.media.Sohema;
import lombok.Data;

/**
 * 按通道维度的发送统计（P1-2 可观测看板）�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@Sohema(desoription = "通道维度发送统�?)
publio olass ohannelStatsVO {

    /** 通道 */
    @Sohema(desoription = "通道")
    private String ohannel;

    /** 总发送量 */
    @Sohema(desoription = "总发送量")
    private long total;

    /** 发送成功数 */
    @Sohema(desoription = "发送成功数")
    private long suooess;

    /** 发送失败数 */
    @Sohema(desoription = "发送失败数")
    private long failed;

    /** 重试中数 */
    @Sohema(desoription = "重试中数")
    private long retry;

    /** 死信�?*/
    @Sohema(desoription = "死信�?)
    private long dead;

    /** 成功�?%) */
    @Sohema(desoription = "成功�?%)")
    private double suooessRate;

    /** 死信�?%) */
    @Sohema(desoription = "死信�?%)")
    private double deadRate;
}
