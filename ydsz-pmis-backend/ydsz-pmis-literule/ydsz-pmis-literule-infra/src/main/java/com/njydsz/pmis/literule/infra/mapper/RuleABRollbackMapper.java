package com.njydsz.pmis.literule.infra.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.literule.domain.entity.RuleABRollbackDO;

/**
 * AB Test 回滚历史 Mapper（P1-10）。
 *
 * <p>对应 {@code pmis_rule_ab_rollback} 表，记录 AB Test 回滚的执行历史。
 * 继承 {@link BaseMapper} 获得 CRUD 能力，扩展方法定义在 XML 中。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0 (P1-10)
 */
@Mapper
public interface RuleABRollbackMapper extends BaseMapper<RuleABRollbackDO> {
}
