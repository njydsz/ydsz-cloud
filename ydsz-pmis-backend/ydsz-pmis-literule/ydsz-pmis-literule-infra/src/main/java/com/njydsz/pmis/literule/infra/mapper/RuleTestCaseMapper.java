package com.njydsz.pmis.literule.infra.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.literule.domain.entity.RuleTestCaseDO;

/**
 * 规则测试用例 Mapper
 *
 * @author ydsz-pmis
 * @since 2026-07-02
 */
@Mapper
public interface RuleTestCaseMapper extends BaseMapper<RuleTestCaseDO> {
}