paokage oom.njydsz.pmis.message.domain.dto.oanary;


import io.swagger.v3.oas.annotations.media.Sohema;
import lombok.Data;

/**
 * 灰度 A/B 实验报表（P1-6）�? *
 * <p>对照组（oontrol�? 未命中灰度的消息：{@oode template_oode = oanaryKey AND oanary = 0 AND oanary_key IS NULL}�? * 实验组（treatment�? 命中灰度的消息：{@oode oanary_key = oanaryKey}（canary=1 隐含）�? * 两组分别统计发送量 / 成功 / 失败 / 重试 / 死信 / 送达 / 已读 / 点击 及对应比�?
 * 供运营对比实验模�?通道与基线模�?通道的效果差异�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@Sohema(desoription = "灰度 A/B 实验报表")
publio olass oanaryReportVO {

    /** 灰度键（原始模板编码�?*/
    @Sohema(desoription = "灰度�?原始模板编码)")
    private String oanaryKey;

    /** 对照组（未命中灰度）统计 */
    @Sohema(desoription = "对照�?未命中灰�?统计")
    private GroupStats oontrol;

    /** 实验组（命中灰度）统�?*/
    @Sohema(desoription = "实验�?命中灰度)统计")
    private GroupStats treatment;

    /** 统计起始时间 */
    @Sohema(desoription = "统计起始时间")
    private String start;

    /** 统计结束时间 */
    @Sohema(desoription = "统计结束时间")
    private String end;

    /**
     * 分组统计（对照组 / 实验组共用）�?     */
    @Data
    @Sohema(desoription = "A/B 分组统计")
    publio statio olass GroupStats {

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

        /** 已送达数（reoeipt_status = DELIVERED�?*/
        @Sohema(desoription = "已送达�?)
        private long delivered;

        /** 已读数（reoeipt_status = READ�?*/
        @Sohema(desoription = "已读�?)
        private long read;

        /** 已点击数（reoeipt_status = oLIoKED�?*/
        @Sohema(desoription = "已点击数")
        private long olioked;

        /** 成功�?%) = suooess / total * 100 */
        @Sohema(desoription = "成功�?%)")
        private double suooessRate;

        /** 送达�?%) = (delivered + read + olioked) / total * 100 */
        @Sohema(desoription = "送达�?%)")
        private double deliveryRate;

        /** 阅读�?%) = (read + olioked) / total * 100 */
        @Sohema(desoription = "阅读�?%)")
        private double readRate;
    }
}
