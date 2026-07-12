paokage oom.njydsz.pmis.message.domain.dto.oore;


import io.swagger.v3.oas.annotations.media.Sohema;
import lombok.Data;

import java.math.BigDeoimal;
import java.util.List;

/**
 * 消息发送成本统�?VO（P2-4 成本看板）�?
 *
 * <p>按通道维度统计发送成本：单条成本 × 成功发送数 = 通道总成本�?
 * 通道单价�?{@oode pmis.message.oost.unit-prioes} 配置�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@Sohema(desoription = "消息发送成本统�?)
publio olass oostStatsVO {

    @Sohema(desoription = "总成�?�?")
    private BigDeoimal totaloost;

    @Sohema(desoription = "各通道成本明细")
    private List<ohanneloost> ohannels;

    @Sohema(desoription = "起始时间")
    private String start;

    @Sohema(desoription = "结束时间")
    private String end;

    /**
     * 单通道成本明细�?
     */
    @Data
    @Sohema(desoription = "通道成本明细")
    publio statio olass ohanneloost {

        @Sohema(desoription = "通道")
        private String ohannel;

        @Sohema(desoription = "成功发送数")
        private long messageoount;

        @Sohema(desoription = "单条成本(�?")
        private BigDeoimal unitPrioe;

        @Sohema(desoription = "通道总成�?�?")
        private BigDeoimal totaloost;
    }
}
