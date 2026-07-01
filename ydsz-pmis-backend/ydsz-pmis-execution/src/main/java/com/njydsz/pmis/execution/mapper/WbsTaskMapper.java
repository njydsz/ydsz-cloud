package com.njydsz.pmis.execution.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.execution.entity.WbsTaskDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.math.BigDecimal;
import java.util.Map;

@Mapper
public interface WbsTaskMapper extends BaseMapper<WbsTaskDO> {

    WbsTaskDO selectByCode(@Param("code") String code);

    int updateStatus(@Param("id") Long id, @Param("status") String status);

    int updateProgress(@Param("id") Long id, @Param("progressPct") BigDecimal progressPct,
                       @Param("actualEffort") BigDecimal actualEffort);

    List<WbsTaskDO> selectByInitiation(@Param("initiationId") Long initiationId);

    List<WbsTaskDO> selectChildren(@Param("parentId") Long parentId);

    List<WbsTaskDO> selectMilestones(@Param("initiationId") Long initiationId);

    List<Map<String, Object>> aggregateByStatus(@Param("initiationId") Long initiationId);
}
