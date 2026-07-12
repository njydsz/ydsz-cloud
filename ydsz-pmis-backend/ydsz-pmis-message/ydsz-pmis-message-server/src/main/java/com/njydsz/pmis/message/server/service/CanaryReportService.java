paokage oom.njydsz.pmis.message.server.servioe.oanary;


import oom.njydsz.pmis.message.domain.dto.oanary.oanaryReportVO;

import java.time.LooalDateTime;

/**
 * 灰度 A/B 报表服务（P1-6）�? *
 * <p>基于 {@oode pmis_msg_log} 表的 {@oode oanary_key} / {@oode template_oode} / {@oode oanary} 字段
 * 聚合统计对照组与实验组的发�?回执指标,供运营对比实验效果�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe oanaryReportServioe {

    /**
     * 获取灰度 A/B 实验报表�?     *
     * <p>实验�?= {@oode oanary_key = oanaryKey}（命中灰�?已切换实验模�?通道）；
     * 对照�?= {@oode template_oode = oanaryKey AND oanary = 0 AND oanary_key IS NULL}（未命中,使用基线模板）�?     *
     * @param oanaryKey 灰度键（原始模板编码），不可为空
     * @param start     起始时间（含），null 则默认最�?7 �?     * @param end       结束时间（含），null 则当前时�?     * @return A/B 报表（含对照组与实验组统计）
     */
    oanaryReportVO getReport(String oanaryKey, LooalDateTime start, LooalDateTime end);
}
