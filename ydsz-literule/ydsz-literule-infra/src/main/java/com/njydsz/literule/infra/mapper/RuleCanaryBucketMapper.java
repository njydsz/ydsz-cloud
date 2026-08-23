package com.njydsz.literule.infra.mapper;

import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.njydsz.literule.infra.entity.RuleCanaryBucketDO;

/**
 * 规则灰度桶 Mapper
 *
 * <p>对应数据表 <code>ydsz_rule_canary_bucket</code>。
 *
 * <p>灰度桶按用户 ID 哈希/百分位定义灰度受众，规则灰度发布时按用户命中桶决定是否启用新规则。
 *
 * <p><b>主要索引：</b>
 *
 * <ul>
 *   <li>uk_bucket_key — (规则版本+桶标识) 唯一索引
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see RuleCanaryBucketDO 灰度桶实体
 * @see com.njydsz.literule.server.service.RuleCanaryBucketService 灰度桶 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface RuleCanaryBucketMapper extends BaseMapper<RuleCanaryBucketDO> {

  /**
   * 查询某条规则在指定时间窗口内的分桶统计。
   *
   * @param ruleCode 规则编码
   * @param since 起始时间（含）
   * @return 分桶统计列表
   */
  List<RuleCanaryBucketDO> selectByRuleCodeSince(
      @Param("ruleCode") String ruleCode, @Param("since") LocalDateTime since);
}
