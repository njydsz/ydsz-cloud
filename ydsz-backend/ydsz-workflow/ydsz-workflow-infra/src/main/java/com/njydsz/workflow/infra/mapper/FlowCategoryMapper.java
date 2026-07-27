package com.njydsz.workflow.infra.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.workflow.domain.entity.FlowCategory;

/**
 * 流程分类 Mapper
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface FlowCategoryMapper extends BaseMapper<FlowCategory> {
}
