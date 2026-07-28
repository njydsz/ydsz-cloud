package com.njydsz.literule.infra.mapper;

import org.apache.ibatis.annotations.Mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.literule.domain.entity.RuleTestCaseDO;

/**
 * 规则测试用例 Mapper
 *
 * <p>对应数据表 <code>ydsz_rule_test_case</code>。
 * <p>测试用例用于规则发布前回归（输入→预期输出对比），保证规则变更不破坏既有业务。
 *
 * <p><b>主要索引：</b>
 * <ul>
 *   <li>uk_case_name — (规则+用例名) 唯一索引</li>
 *   <li>idx_result — 用例结果过滤索引（PASS/FAIL）</li>
 * </ul>
 *
 * <p><b>多租户：</b>由 MyBatis 拦截器自动注入 {@code tenant_id} 过滤条件，本接口不感知。
 *
 * <p><b>逻辑删除：</b>{@code deleted} 字段标识，所有查询自动过滤已删除记录。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.literule.domain.entity.RuleTestCaseDO 规则测试用例实体
 * @see com.njydsz.literule.server.service.RuleTestCaseService 测试用例 Service
 * @see com.baomidou.mybatisplus.core.mapper.BaseMapper MyBatis-Plus 通用 Mapper
 */
@Mapper
public interface RuleTestCaseMapper extends BaseMapper<RuleTestCaseDO> {
}