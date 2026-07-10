package com.njydsz.pmis.workflow.mapper.dmn;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.workflow.entity.dmn.FlowDmnTableDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * DMN 决策表 Mapper
 *
 * <p>P0-4: DMN 决策表引擎（对标 Camunda/Flowable DMN）。
 *
 * @author ydsz-pmis-team
 * @since 1.3.0
 */
@Mapper
public interface FlowDmnTableMapper extends BaseMapper<FlowDmnTableDO> {
}
