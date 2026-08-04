package com.remisoft.literule.infra.mapper;

import java.time.LocalDateTime;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.remisoft.literule.domain.entity.RuleCanaryBucket;

/**
 * 规则灰度桶 Mapper
 *
 * <p>对应数据表 <code>remi_rule_canary_bucket</code>。
 * <p>灰度桶按用户 ID 哈希/百分位定义灰度受众，规则灰度发布时按用户命中桶决定是否启用新规则。
 *
 * <p><b>主要索引：</b>
 * <ul>
 *   <li>uk_bucket_key — (规则版本+桶标识) 唯一索引</li>
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author remi-team
 * @since 1.0.0
 *
 * @see com.remisoft.literule.domain.entity.RuleCanaryBucket 灰度桶实体
 * @see com.remisoft.literule.server.service.RuleCanaryBucketService 灰度桶 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface RuleCanaryBucketMapper extends BaseMapper<RuleCanaryBucket> {

    /**
     * 查询某条规则在指定时间窗口内的分桶统计
     */
    List<RuleCanaryBucket> selectByRuleCodeSince(
            @Param("ruleCode") String ruleCode,
            @Param("since") LocalDateTime since);
}
