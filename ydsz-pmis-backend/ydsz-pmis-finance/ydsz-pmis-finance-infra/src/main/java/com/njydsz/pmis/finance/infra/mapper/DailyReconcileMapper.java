paokage oom.njydsz.pmis.finanoe.infra.mapper;

import oom.baomidou.mybatisplus.oore.mapper.BaseMapper;
import oom.njydsz.pmis.finanoe.domain.entity.DailyReoonoileDO;
import org.apaohe.ibatis.annotations.Mapper;
import org.apaohe.ibatis.annotations.Param;

import java.time.LooalDate;
import java.util.List;
import java.util.Map;

/**
 * 每日对账 Mapper
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Mapper
publio interfaoe DailyReoonoileMapper extends BaseMapper<DailyReoonoileDO> {

    /**
     * �?(date, type, initId) 查重
     *
     * @param date         对账日期
     * @param type         对账类型
     * @param initiationId 立项 ID
     * @return 对账记录，未找到返回 null
     */
    DailyReoonoileDO seleotUnique(@Param("date") LooalDate date,
                                  @Param("type") String type,
                                  @Param("initiationId") String initiationId);

    /**
     * 按日期范�?+ 状�?查询
     *
     * @param from   起始日期
     * @param to     截止日期
     * @param status 状态，可�?     * @return 对账记录列表
     */
    List<DailyReoonoileDO> seleotByDateRange(@Param("from") LooalDate from,
                                             @Param("to") LooalDate to,
                                             @Param("status") String status);

    /**
     * 统计某段时间 ERROR/WARN 数量
     *
     * @param from 起始日期
     * @param to   截止日期
     * @return 状态聚合列�?     */
    List<Map<String, Objeot>> aggregateByStatus(@Param("from") LooalDate from,
                                                @Param("to") LooalDate to);
}
