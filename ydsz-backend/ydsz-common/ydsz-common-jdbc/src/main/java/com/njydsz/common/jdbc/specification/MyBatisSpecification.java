package com.njydsz.common.jdbc.specification;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.njydsz.common.domain.specification.Specification;

/**
 * MyBatis-Plus 可感知的数据库规约接口。
 *
 * <p>在 {@link Specification} 基础上扩展 {@link #apply(QueryWrapper)} 方法，
 * 允许规约将自身转换为 MyBatis-Plus 的 {@link QueryWrapper} 查询条件，
 * 从而避免全表加载后在内存中过滤的性能问题。
 *
 * @param <T> 实体类型
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface MyBatisSpecification<T> extends Specification<T> {

    /**
     * 将规约条件应用到 {@link QueryWrapper}。
     *
     * <p>子类在此方法中调用 wrapper.eq / like / orderBy 等方法追加 SQL 条件。
     *
     * @param wrapper MyBatis-Plus 查询包装器
     */
    void apply(QueryWrapper<T> wrapper);
}
