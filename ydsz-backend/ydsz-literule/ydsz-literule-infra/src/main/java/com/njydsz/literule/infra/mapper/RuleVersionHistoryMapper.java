package com.njydsz.literule.infra.mapper;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.literule.domain.entity.RuleVersionHistory;

/**
 * 规则版本历史 Mapper
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface RuleVersionHistoryMapper extends BaseMapper<RuleVersionHistory> {

    /**
     * 根据规则编码查询版本历史（倒序）
     *
     * @param ruleCode 规则编码
     * @return 版本历史列表
     */
    List<RuleVersionHistory> listByCode(@Param("ruleCode") String ruleCode);
}
