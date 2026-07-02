package com.njydsz.pmis.execution.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.execution.entity.RuleVersionHistoryDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 规则版本历史 Mapper
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Mapper
public interface RuleVersionHistoryMapper extends BaseMapper<RuleVersionHistoryDO> {

    /**
     * 根据规则编码查询版本历史（倒序）
     *
     * @param ruleCode 规则编码
     * @return 版本历史列表
     */
    List<RuleVersionHistoryDO> listByCode(@Param("ruleCode") String ruleCode);
}
