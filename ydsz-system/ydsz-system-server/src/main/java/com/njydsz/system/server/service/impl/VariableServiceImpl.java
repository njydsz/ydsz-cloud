package com.njydsz.system.server.service.impl;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

import com.njydsz.common.redis.service.RedisService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;
import com.njydsz.system.domain.dto.VariableDTO;
import com.njydsz.system.domain.entity.Variable;
import com.njydsz.system.domain.vo.VariableVO;
import com.njydsz.system.infra.mapper.VariableMapper;
import com.njydsz.system.server.config.SystemProperties;
import com.njydsz.system.server.metrics.SystemMetrics;
import com.njydsz.system.server.service.VariableService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.njydsz.common.auth.annotation.DataScope;
import com.njydsz.system.domain.converter.SystemConverter;

/**
 * 系统变量 Service 实现
 *
 * <p>对 {@link VariableService} 接口的完整实现，是「系统变量中心」的核心业务逻辑层。
 * 与 {@link ConfigServiceImpl} 能力对齐，但定位不同：Variable 用于业务侧动态参数
 * （如当前生效的会计年度、最近结算月份、流水号计数器等），
 * 业务方可通过 Feign 远程查询；Config 用于系统级配置，由后端模块本地消费。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #page} / {@link #getById} / {@link #save} / {@link #updateById} /
 *       {@link #removeById}，全部走 {@code @Transactional} 事务保证</li>
 *   <li><b>按 key 查询值</b>：{@link #getVariableValue}（走 Redis 缓存 + 空值哨兵防穿透）</li>
 *   <li><b>分页 / 列表查询</b>：{@link #page} / {@link #list}，支持行级数据权限过滤
 *       （{@code @DataScope}）</li>
 *   <li><b>缓存失效</b>：写操作触发 {@link #evictCache} 主动失效</li>
 * </ul>
 *
 * <p><b>缓存设计：</b>
 * <ul>
 *   <li>缓存键：{@code system:variable:value:{variableKey}}</li>
 *   <li>TTL 取自配置 {@code ydsz.system.variable.cache-ttl-minutes}，默认 5 分钟</li>
 *   <li>空值哨兵（{@link #NULL_SENTINEL}）防缓存穿透，1 分钟 TTL</li>
 *   <li>写操作触发 {@link #evictCache} 主动失效</li>
 * </ul>
 *
 * <p><b>事务边界：</b>
 * <ul>
 *   <li>所有写方法 {@code @Transactional(rollbackFor = Exception.class)}</li>
 *   <li>读方法不开启事务，依赖 MyBatis 自动提交</li>
 *   <li>分页 / 列表查询使用 {@code @DataScope} 自动注入行级数据权限 SQL</li>
 * </ul>
 *
 * <p><b>多租户：</b>所有方法自动按当前 {@code TenantContext} 隔离，
 * 租户过滤由 MyBatis 拦截器注入。
 *
 * <p><b>与 ConfigService 的区别：</b>
 * <table>
 *   <caption>Variable vs Config 定位差异</caption>
 *   <tr><th>维度</th><th>{@link ConfigServiceImpl Config}</th><th>Variable（本类）</th></tr>
 *   <tr><td>使用方</td><td>后端模块本地消费</td><td>业务方 + 跨服务 Feign 查询</td></tr>
 *   <tr><td>典型场景</td><td>数据库连接池参数、日志级别</td><td>会计年度、流水号、动态业务参数</td></tr>
 *   <tr><td>缓存粒度</td><td>单 key + 组批量 + 公开配置</td><td>仅单 key（无组批量）</td></tr>
 *   <tr><td>变更广播</td><td>发布 {@code ConfigChangeEvent}</td><td>不广播（业务方拉取即可）</td></tr>
 *   <tr><td>行级权限</td><td>无</td><td>有（{@code @DataScope}）</td></tr>
 * </table>
 *
 * <p><b>设计要点：</b>
 * <ul>
 *   <li><b>业务参数化</b>：业务硬编码值（如「每月 1 号出账」）可改为变量，由运营灵活调整</li>
 *   <li><b>行级权限</b>：分页 / 列表查询走 {@code @DataScope}，自动按当前用户的部门 / 人员范围过滤</li>
 *   <li><b>软删除</b>：{@code ydsz_variable} 表采用 <b>逻辑删除</b>（{@code deleted} 字段）</li>
 *   <li><b>启用过滤</b>：{@link #getVariableValue} 仅返回 {@code status=ENABLED} 的变量，
 *       失效的变量视为不存在</li>
 * </ul>
 *
 * <p><b>典型使用：</b>
 * <pre>{@code
 * // 业务方远程查询（跨服务）
 * String currentYear = variableClient.getVariableValue("finance.current_fiscal_year");
 *
 * // 后端模块本地查询
 * String waterNo = variableService.getVariableValue("serial.water_no_prefix");
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see VariableService 变量 Service 接口
 * @see ConfigServiceImpl 系统配置 Service（能力对齐但定位不同）
 * @see com.njydsz.system.domain.entity.Variable 变量实体
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VariableServiceImpl implements VariableService {

    /** 变量值缓存键前缀：{@code system:variable:value:{variableKey}} */
    private static final String CACHE_KEY_PREFIX = "system:variable:value:";
    /** 空值哨兵字符串，用于防缓存穿透 */
    private static final String NULL_SENTINEL = "__NULL__";
    /** 空值哨兵 TTL（1 分钟） */
    private static final Duration NULL_SENTINEL_TTL = Duration.ofMinutes(1);

    /** 变量 Mapper（继承 {@code ydsz_variable} 表 CRUD） */
    private final VariableMapper mapper;
    /** Redis 缓存服务 */
    private final RedisService redisService;
    /** 系统配置属性（含变量缓存 TTL 配置） */
    private final SystemProperties properties;
    /** 系统监控指标采集器 */
    private final SystemMetrics metrics;

    /**
     * 根据主键查询变量（不走缓存，直接走 DB）
     *
     * <p>适用场景：管理后台「变量详情」页，单次访问无缓存需求。
     * 高频查询请使用 {@link #getVariableValue}。
     *
     * @param id 变量主键
     * @return 变量 VO，不存在返回 null
     */
    @Override
    public VariableVO getById(String id) {
        Variable entity = mapper.selectById(id);
        return SystemConverter.INSTANT.entityToVO(entity);
    }

    /**
     * 按 variableKey 查询变量值（走缓存）
     *
     * <p>执行链路：
     * <ol>
     *   <li>查 Redis 缓存（{@code system:variable:value:{variableKey}}），命中直接返回</li>
     *   <li>缓存未命中查 DB（仅 {@code status=ENABLED}），存在则写缓存（TTL 默认 5min），
     *       不存在写空值哨兵（TTL 1min）</li>
     *   <li>记录缓存命中 / 未命中指标、查询耗时指标</li>
     * </ol>
     *
     * <p>本方法是高频读入口，跨服务 Feign 调用建议走本方法，避免直连 DB。
     *
     * @param variableKey 变量键
     * @return 变量值字符串，不存在时返回 null（受空值哨兵保护，短 TTL 内不会反复穿透到 DB）
     */
    @Override
    public String getVariableValue(String variableKey) {
        long start = System.nanoTime();
        try {
            String cacheKey = CACHE_KEY_PREFIX + variableKey;
            String cached = redisService.get(cacheKey, String.class);
            if (cached != null) {
                if (NULL_SENTINEL.equals(cached)) {
                    metrics.recordVariableCacheHit();
                    return null;
                }
                metrics.recordVariableCacheHit();
                return cached;
            }
            metrics.recordVariableCacheMiss();
            QueryWrapper<Variable> wrapper = new QueryWrapper<>();
            wrapper.eq("variable_key", variableKey).eq("status", "ENABLED");
            Variable entity = mapper.selectOne(wrapper);
            if (entity != null) {
                redisService.set(cacheKey, entity.getVariableValue(), getCacheTtl());
                return entity.getVariableValue();
            }
            redisService.set(cacheKey, NULL_SENTINEL, NULL_SENTINEL_TTL);
            return null;
        } finally {
            metrics.recordVariableRead(System.nanoTime() - start);
        }
    }

    /**
     * 分页查询变量（管理后台列表页）
     *
     * <p>支持按 {@code variableKey} 模糊匹配、{@code status} 精确匹配进行过滤，
     * 按 {@code created_at} 倒序返回。
     *
     * <p><b>行级权限：</b>本方法带 {@code @DataScope} 注解，
     * 自动按当前用户的部门 / 人员范围过滤（管理员看全量）。
     *
     * @param pageNum     页码（1-based）
     * @param pageSize    每页条数
     * @param variableKey 变量键（可选，模糊匹配）
     * @param status      状态（可选过滤条件，如 {@code ENABLED/DISABLED}）
     * @return 分页结果（含总条数）
     */
    @Override
    @DataScope(deptColumn = "dept_id", userColumn = "created_by")
    public PageResponse<List<VariableVO>> page(int pageNum, int pageSize, String variableKey, String status) {
        QueryWrapper<Variable> wrapper = new QueryWrapper<>();
        if (variableKey != null && !variableKey.isBlank()) {
            wrapper.like("variable_key", variableKey);
        }
        if (status != null && !status.isBlank()) {
            wrapper.eq("status", status);
        }
        wrapper.orderByDesc("created_at");
        IPage<Variable> page = mapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<VariableVO> vos = page.getRecords().stream().map(SystemConverter.INSTANT::entityToVO).collect(Collectors.toList());
        return PageResponse.success(page.getTotal(), (long) pageNum, (long) pageSize, vos);
    }

    /**
     * 查询全部变量（不区分状态）
     *
     * <p>典型调用方：管理后台「变量选择器」下拉框。
     *
     * <p><b>行级权限：</b>本方法带 {@code @DataScope} 注解，
     * 自动按当前用户的部门 / 人员范围过滤。
     *
     * <p><b>慎用：</b>全表扫描，变量一般 < 200 条，单次查询 < 20ms。
     *
     * @return 全部变量列表（按 createdAt 倒序）
     */
    @Override
    @DataScope(deptColumn = "dept_id", userColumn = "created_by")
    public List<VariableVO> list() {
        return mapper.selectList(null).stream().map(SystemConverter.INSTANT::entityToVO).collect(Collectors.toList());
    }

    /**
     * 新增变量
     *
     * <p>执行链路：
     * <ol>
     *   <li>DTO 转 DO，默认 {@code status=ENABLED}</li>
     *   <li>插入 {@code ydsz_variable} 表</li>
     *   <li>清除该 {@code variableKey} 对应的缓存</li>
     * </ol>
     *
     * @param dto 变量数据
     * @return 新创建的变量 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String save(VariableDTO dto) {
        Variable entity = toEntity(dto);
        mapper.insert(entity);
        evictCache(entity.getVariableKey());
        return entity.getId();
    }

    /**
     * 更新变量
     *
     * <p>执行链路：
     * <ol>
     *   <li>DTO 转 DO</li>
     *   <li>更新 {@code ydsz_variable} 表</li>
     *   <li>更新成功后清除该 {@code variableKey} 对应的缓存</li>
     * </ol>
     *
     * <p><b>注意：</b>更新 {@code variableKey} 会导致所有依赖该键的下游缓存失效，
     * 调用方需主动清理相关业务缓存。
     *
     * @param dto 变量数据（需包含 {@code id}）
     * @return true=更新成功，false=记录不存在
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(VariableDTO dto) {
        Variable entity = toEntity(dto);
        boolean result = mapper.updateById(entity) > 0;
        if (result && entity.getVariableKey() != null) {
            evictCache(entity.getVariableKey());
        }
        return result;
    }

    /**
     * 逻辑删除变量
     *
     * <p>采用<b>逻辑删除</b>（{@code deleted=1} + {@code status=DISABLED}），
     * 不真正从 DB 删除，便于审计回溯。
     *
     * <p>执行链路：
     * <ol>
     *   <li>查询原实体（用于获取 variableKey）</li>
     *   <li>逻辑删除记录</li>
     *   <li>删除成功后清除该 {@code variableKey} 对应的缓存</li>
     * </ol>
     *
     * @param id 变量主键
     * @return true=删除成功，false=记录不存在
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) {
        Variable entity = mapper.selectById(id);
        boolean result = mapper.deleteById(id) > 0;
        if (result && entity != null && entity.getVariableKey() != null) {
            evictCache(entity.getVariableKey());
        }
        return result;
    }

    /**
     * 清除指定变量键的缓存（私有）
     *
     * @param variableKey 变量键（{@code null} 时跳过）
     */
    private void evictCache(String variableKey) {
        if (variableKey != null) {
            redisService.delete(CACHE_KEY_PREFIX + variableKey);
        }
    }

    /**
     * 获取变量缓存 TTL（私有）
     *
     * <p>从 {@link SystemProperties.Variable#getCacheTtlMinutes()} 读取配置，
     * 若配置值 <= 0 则降级为默认 5 分钟。
     *
     * @return 缓存 TTL Duration
     */
    private Duration getCacheTtl() {
        int minutes = properties.getVariable().getCacheTtlMinutes();
        return Duration.ofMinutes(minutes > 0 ? minutes : 5);
    }

    /**
     * DTO → DO 转换（私有）
     *
     * <p>缺省 {@code status="ENABLED"}，保证新建的变量默认可用。
     *
     * @param dto 数据传输对象
     * @return 数据库实体
     */
    private Variable toEntity(VariableDTO dto) {
        Variable entity = new Variable();
        entity.setId(dto.getId());
        entity.setVariableKey(dto.getVariableKey());
        entity.setVariableValue(dto.getVariableValue());
        entity.setValueType(dto.getValueType());
        entity.setDescription(dto.getDescription());
        entity.setStatus(dto.getStatus() != null ? dto.getStatus() : "ENABLED");
        return entity;
    }
}
