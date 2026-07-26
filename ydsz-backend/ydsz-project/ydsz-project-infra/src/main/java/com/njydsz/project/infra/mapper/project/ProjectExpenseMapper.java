package com.njydsz.project.infra.mapper.project;

import com.njydsz.project.domain.entity.project.ProjectExpenseDO;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * ProjectExpense Mapper。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Mapper
public interface ProjectExpenseMapper extends BaseMapper<ProjectExpenseDO> {
}
