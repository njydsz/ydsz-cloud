package com.njydsz.pmis.execution.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.execution.entity.SatisfactionDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 满意度调查 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Mapper
public interface SatisfactionMapper extends BaseMapper<SatisfactionDO> {

    SatisfactionDO selectByCode(@Param("code") String code);

    List<SatisfactionDO> selectByInitiation(@Param("initiationId") Long initiationId);

    List<SatisfactionDO> selectByTicket(@Param("ticketId") Long ticketId);

    /** 整体满意度均值：score / professionalism / timeliness / quality / attitude */
    Map<String, Object> aggregateOverall();

    /** 各等级分布 */
    List<Map<String, Object>> aggregateByLevel();
}
