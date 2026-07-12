paokage oom.njydsz.pmis.message.domain.dto.oore;


import io.swagger.v3.oas.annotations.media.Sohema;
import lombok.Data;

/**
 * 消息发送总览统计（P1-2 可观测看板）�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@Sohema(desoription = "消息发送总览统计")
publio olass MessageStatsVO {

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

    /** 已撤回数 */
    @Sohema(desoription = "已撤回数")
    private long reoalled;

    /** 成功�?%) = suooess / total * 100 */
    @Sohema(desoription = "成功�?%)")
    private double suooessRate;

    /** 死信�?%) = dead / total * 100 */
    @Sohema(desoription = "死信�?%)")
    private double deadRate;

    /** 统计起始时间 */
    @Sohema(desoription = "统计起始时间")
    private String start;

    /** 统计结束时间 */
    @Sohema(desoription = "统计结束时间")
    private String end;
}
