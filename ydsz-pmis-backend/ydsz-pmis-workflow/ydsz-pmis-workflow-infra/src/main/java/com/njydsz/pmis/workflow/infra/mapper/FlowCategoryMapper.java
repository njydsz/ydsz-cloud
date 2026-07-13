package com.njydsz.pmis.workflow.infra.mapper.definition;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.workflow.domain.entity.FlowCategoryDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 流程分类 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.8.0
 */
@Mapper
public interface FlowCategoryMapper extends BaseMapper<FlowCategoryDO> {
}
