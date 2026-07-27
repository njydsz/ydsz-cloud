package com.njydsz.common.jdbc.repository;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.common.domain.entity.AggregateRoot;
import com.njydsz.common.domain.query.PageQuery;
import com.njydsz.common.domain.query.PageResult;
import com.njydsz.common.domain.repository.AbstractRepository;
import com.njydsz.common.domain.specification.Specification;
import com.njydsz.common.jdbc.specification.MyBatisSpecification;

/**
 * 基于 MyBatis-Plus {@link BaseMapper} 的默认仓储实现。
 *
 * <p>将 {@link BaseMapper} 适配到领域层 {@link com.njydsz.common.domain.repository.Repository} 接口，
 * 使业务模块无需手写仓储实现即可获得完整 CRUD 能力。
 *
 * <p><b>使用方式：</b>
 * <pre>{@code
 * &#64;Repository
 * public class ConfigRepository extends BaseMapperRepository<ConfigDO, String> {
 *
 *     public ConfigRepository(ConfigMapper mapper) {
 *         super(mapper);
 *     }
 * }
 * }</pre>
 *
 * <p><b>规约支持：</b>
 * <ul>
 *   <li>当传入的 {@link Specification} 实现 {@link MyBatisSpecification} 时，
 *       调用其 {@link MyBatisSpecification#apply(QueryWrapper)} 方法生成 SQL 条件</li>
 *   <li>当传入普通 {@link Specification} 或为 null 时，执行全表查询（建议仅用于小数据量场景）</li>
 * </ul>
 *
 * <p><b>批量保存：</b>
 * 默认逐条调用 {@link BaseMapper#insert(Object)} 或 {@link BaseMapper#updateById(Object)}。
 * 子类可覆写 {@link #doSaveAll(Iterable)} 以对接 MyBatis-Plus 的批量 SQL 方法。
 *
 * @param <T>  实体类型，须实现 {@link AggregateRoot}
 * @param <ID> 主键类型
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public abstract class BaseMapperRepository<T extends AggregateRoot<ID>, ID extends Serializable>
        extends AbstractRepository<T, ID> {

    /** MyBatis-Plus Mapper */
    protected final BaseMapper<T> mapper;

    /**
     * 构造基于 Mapper 的仓储。
     *
     * @param mapper MyBatis-Plus Mapper
     */
    protected BaseMapperRepository(BaseMapper<T> mapper) {
        this.mapper = mapper;
    }

    @Override
    protected T doFindById(ID id) {
        return mapper.selectById(id);
    }

    @Override
    protected T doSave(T aggregate) {
        if (aggregate.isNew()) {
            mapper.insert(aggregate);
        } else {
            mapper.updateById(aggregate);
        }
        return aggregate;
    }

    @Override
    protected List<T> doSaveAll(Iterable<T> aggregates) {
        List<T> result = new ArrayList<>();
        for (T aggregate : aggregates) {
            result.add(doSave(aggregate));
        }
        return result;
    }

    @Override
    protected void doDelete(ID id) {
        mapper.deleteById(id);
    }

    @Override
    protected List<T> doFindAll(Specification<T> spec) {
        QueryWrapper<T> wrapper = toQueryWrapper(spec);
        List<T> list = mapper.selectList(wrapper);
        return postFilter(list, spec);
    }

    @Override
    protected PageResult<T> doFindPage(PageQuery query, Specification<T> spec) {
        int pageNum = query != null ? query.getEffectivePageNum() : 1;
        int pageSize = query != null ? query.getEffectivePageSize() : PageQuery.of(1).getEffectivePageSize();
        QueryWrapper<T> wrapper = toQueryWrapper(spec);
        applyOrderBy(wrapper, query);
        IPage<T> page = mapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<T> records = postFilter(page.getRecords(), spec);
        return PageResult.of(records, page.getTotal(), pageNum, pageSize);
    }

    @Override
    protected long doCount(Specification<T> spec) {
        QueryWrapper<T> wrapper = toQueryWrapper(spec);
        return mapper.selectCount(wrapper);
    }

    @Override
    protected boolean doExistsById(ID id) {
        return mapper.selectCount(new QueryWrapper<T>().eq("id", id)) > 0;
    }

    /**
     * 将规约转换为 {@link QueryWrapper}。
     *
     * <p>若规约为 {@link MyBatisSpecification}，则调用其 apply 方法；
     * 否则返回空条件的 QueryWrapper（全表查询）。
     *
     * @param spec 查询规约
     * @return MyBatis-Plus 查询包装器
     */
    protected QueryWrapper<T> toQueryWrapper(Specification<T> spec) {
        QueryWrapper<T> wrapper = new QueryWrapper<>();
        if (spec instanceof MyBatisSpecification) {
            ((MyBatisSpecification<T>) spec).apply(wrapper);
        }
        return wrapper;
    }

    /**
     * 对查询结果进行后置过滤。
     *
     * <p>当传入普通 {@link Specification}（非 {@link MyBatisSpecification}）时，
     * 在内存中对结果进行过滤。数据库条件已在 toQueryWrapper 中应用，
     * 此方法用于处理无法表达为 SQL 的复杂规约。
     *
     * @param list 数据库查询结果
     * @param spec 查询规约
     * @return 过滤后的结果
     */
    protected List<T> postFilter(List<T> list, Specification<T> spec) {
        if (spec == null || spec instanceof MyBatisSpecification || list == null || list.isEmpty()) {
            return list != null ? list : Collections.emptyList();
        }
        return list.stream().filter(spec::isSatisfiedBy).collect(Collectors.toList());
    }

    /**
     * 应用排序条件。
     *
     * <p>默认将 {@link PageQuery#getOrderSql()} 追加到 wrapper 的 orderBy 子句。
     * 子类可覆写以支持更复杂的排序逻辑。
     *
     * @param wrapper 查询包装器
     * @param query   分页查询参数
     */
    protected void applyOrderBy(QueryWrapper<T> wrapper, PageQuery query) {
        if (query == null) {
            return;
        }
        String orderSql = query.getOrderSql();
        if (orderSql == null || orderSql.isBlank()) {
            return;
        }
        // 移除 "ORDER BY " 前缀
        String orderBy = orderSql.replaceFirst("^ORDER BY\\s+", "");
        if (!orderBy.isBlank()) {
            wrapper.last("ORDER BY " + orderBy);
        }
    }
}
