package com.njydsz.pmis.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.pmis.project.entity.RuleCanaryBucketDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 规则灰度分桶统计 Mapper
 */
@Mapper
public interface RuleCanaryBucketMapper extends BaseMapper<RuleCanaryBucketDO> {

    /**
     * 查询某条规则在指定时间窗口内的分桶统计
     */
    List<RuleCanaryBucketDO> selectByRuleCodeSince(
            @Param("ruleCode") String ruleCode,
            @Param("since") LocalDateTime since);
}
