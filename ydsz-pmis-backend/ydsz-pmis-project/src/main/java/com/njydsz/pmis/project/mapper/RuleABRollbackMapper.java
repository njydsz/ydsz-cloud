package com.njydsz.pmis.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.project.entity.RuleABRollbackDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * AB Test 回滚历史 Mapper（P1-10）
 */
@Mapper
public interface RuleABRollbackMapper extends BaseMapper<RuleABRollbackDO> {
}
