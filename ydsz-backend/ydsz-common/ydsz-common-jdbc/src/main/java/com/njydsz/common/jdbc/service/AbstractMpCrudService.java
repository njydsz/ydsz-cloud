package com.njydsz.common.jdbc.service;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.common.domain.query.PageQuery;
import com.njydsz.common.domain.query.PageResult;
import com.njydsz.common.domain.service.BaseCrudService;
import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * MyBatis-Plus 通用 CRUD Service 抽象实现。
 *
 * <p>基于 MyBatis-Plus {@link BaseMapper} 提供标准的分页查询、按 ID 查询、列表查询、新增、修改、删除、批量操作的默认实现。
 * 子类只需实现少量抽象方法即可快速获得完整 CRUD 能力，并通过生命周期钩子扩展业务逻辑。
 *
 * <p>与 {@link com.njydsz.common.domain.service.impl.AbstractCrudService}（DDD 仓储模式）的区别：
 * 本类直接对接 MyBatis-Plus {@code BaseMapper}，适用于项目中绝大多数业务模块的实际开发模式。
 *
 * <p><b>泛型参数：</b>
 * <ul>
 *   <li>{@code T}   - 实体类型（须继承 {@link MpBaseEntity}，如 {@code ConfigDO}）</li>
 *   <li>{@code DTO} - 数据传输对象（新增/修改入参）</li>
 *   <li>{@code VO}  - 视图对象（出参）</li>
 *   <li>{@code PQ}  - 分页查询参数类型</li>
 *   <li>{@code ID}  - 主键类型</li>
 * </ul>
 *
 * <p><b>子类需实现：</b>
 * <ul>
 *   <li>{@link #getMapper()} - 返回 MyBatis-Plus {@code BaseMapper} 实例</li>
 *   <li>{@link #toVO(MpBaseEntity)} - 实体转 VO</li>
 *   <li>{@link #toEntity(Object)} - DTO 转实体</li>
 *   <li>{@link #getId(Object)} - 从 DTO 提取主键（更新/删除时）</li>
 * </ul>
 *
 * <p><b>生命周期钩子（模板方法扩展）：</b>
 * 子类可覆写以下 protected 方法在持久化前后注入业务逻辑（如缓存失效、事件发布、指标采集等）：
 * <ul>
 *   <li>{@link #doBeforeSave(Object, MpBaseEntity)} / {@link #doAfterSave(MpBaseEntity, boolean)} - 新增前后</li>
 *   <li>{@link #doBeforeUpdate(Object, MpBaseEntity)} / {@link #doAfterUpdate(MpBaseEntity, boolean)} - 更新前后</li>
 *   <li>{@link #doBeforeDelete(Serializable)} / {@link #doAfterDelete(Serializable, boolean)} - 删除前后</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * &#64;Service
 * public class ConfigServiceImpl extends AbstractMpCrudService<ConfigDO, ConfigDTO, ConfigVO, ConfigPageQuery, String>
 *         implements ConfigService {
 *
 *     private final ConfigMapper mapper;
 *     private final RedisService redisService;
 *
 *     public ConfigServiceImpl(ConfigMapper mapper, RedisService redisService) {
 *         this.mapper = mapper;
 *         this.redisService = redisService;
 *     }
 *
 *     &#64;Override
 *     protected BaseMapper<ConfigDO> getMapper() {
 *         return mapper;
 *     }
 *
 *     &#64;Override
 *     protected ConfigVO toVO(ConfigDO entity) {
 *         return entity == null ? null : new ConfigVO(entity);
 *     }
 *
 *     &#64;Override
 *     protected ConfigDO toEntity(ConfigDTO dto) {
 *         return ConfigDO.builder().configKey(dto.getConfigKey()).build();
 *     }
 *
 *     &#64;Override
 *     protected String getId(ConfigDTO dto) {
 *         return dto.getId();
 *     }
 *
 *     &#64;Override
 *     protected QueryWrapper<ConfigDO> buildQueryWrapper(ConfigPageQuery query) {
 *         QueryWrapper<ConfigDO> wrapper = new QueryWrapper<>();
 *         if (query.getConfigGroup() != null) {
 *             wrapper.eq("config_group", query.getConfigGroup());
 *         }
 *         return wrapper;
 *     }
 *
 *     &#64;Override
 *     protected void doAfterSave(ConfigDO saved, boolean isNew) {
 *         evictCache(saved.getConfigKey(), saved.getConfigGroup());
 *     }
 * }
 * }</pre>
 *
 * @param <T>   实体类型（须继承 MpBaseEntity）
 * @param <DTO> 数据传输对象
 * @param <VO>  视图对象
 * @param <PQ>  分页查询参数类型
 * @param <ID>  主键类型
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public abstract class AbstractMpCrudService<T extends MpBaseEntity<ID>, DTO, VO, PQ extends PageQuery, ID extends Serializable>
        implements BaseCrudService<T, DTO, VO, PQ, ID> {

    /**
     * 获取 MyBatis-Plus Mapper 实例。
     *
     * @return BaseMapper
     */
    protected abstract BaseMapper<T> getMapper();

    /**
     * 实体转视图对象。
     *
     * <p>实现应处理 entity 为 null 的情况，返回 null 或空对象。
     *
     * @param entity 实体
     * @return 视图对象
     */
    protected abstract VO toVO(T entity);

    /**
     * DTO 转实体。
     *
     * <p>子类负责将 DTO 字段映射为实体。新增时 ID 通常为空（由雪花算法自动生成）；
     * 更新时 DTO 中应包含 ID。
     *
     * @param dto 数据传输对象
     * @return 实体
     */
    protected abstract T toEntity(DTO dto);

    /**
     * 从 DTO 中提取主键 ID。
     *
     * <p>用于更新/删除操作时判断是否存在，以及新增后获取自增 ID。
     *
     * @param dto 数据传输对象
     * @return 主键 ID，不存在时返回 null
     */
    protected abstract ID getId(DTO dto);

    /**
     * 判断 DTO 是否为新增操作。
     *
     * <p>默认根据 {@link #getId(Object)} 是否为空判断；子类可覆写以适配业务语义。
     *
     * @param dto 数据传输对象
     * @return true 表示新增，false 表示更新
     */
    protected boolean isNew(DTO dto) {
        return getId(dto) == null;
    }

    /**
     * 构建分页查询条件。
     *
     * <p>子类根据查询参数构建 {@link QueryWrapper} 条件。
     * 默认返回空的 {@code QueryWrapper}（无条件查询）。
     *
     * @param query 分页查询参数
     * @return 查询条件包装器
     */
    protected QueryWrapper<T> buildQueryWrapper(PQ query) {
        return new QueryWrapper<>();
    }

    // ============================== 生命周期钩子 ==============================

    /**
     * 新增前钩子。
     *
     * @param dto    原始 DTO
     * @param entity 转换后的实体
     */
    protected void doBeforeSave(DTO dto, T entity) {
        // 默认空实现
    }

    /**
     * 新增后钩子。
     *
     * @param saved 保存后的实体（含自增 ID）
     * @param isNew 是否为新插入（始终为 true）
     */
    protected void doAfterSave(T saved, boolean isNew) {
        // 默认空实现
    }

    /**
     * 更新前钩子。
     *
     * @param dto    原始 DTO
     * @param entity 转换后的实体
     */
    protected void doBeforeUpdate(DTO dto, T entity) {
        // 默认空实现
    }

    /**
     * 更新后钩子。
     *
     * @param saved   保存后的实体
     * @param updated 是否实际更新成功
     */
    protected void doAfterUpdate(T saved, boolean updated) {
        // 默认空实现
    }

    /**
     * 删除前钩子。
     *
     * @param id 待删除主键
     */
    protected void doBeforeDelete(ID id) {
        // 默认空实现
    }

    /**
     * 删除后钩子。
     *
     * @param id      已删除主键
     * @param removed 是否实际删除成功
     */
    protected void doAfterDelete(ID id, boolean removed) {
        // 默认空实现
    }

    // ============================== 接口实现（查询） ==============================

    @Override
    public PageResult<VO> page(PQ query) {
        QueryWrapper<T> wrapper = buildQueryWrapper(query);
        Page<T> mpPage = new Page<>(query.getEffectivePageNum(), query.getEffectivePageSize());
        IPage<T> result = getMapper().selectPage(mpPage, wrapper);
        List<VO> vos = result.getRecords().stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return PageResult.of(vos, result.getTotal(), query.getEffectivePageNum(), query.getEffectivePageSize());
    }

    @Override
    public VO getById(ID id) {
        T entity = getMapper().selectById(id);
        return toVO(entity);
    }

    @Override
    public List<VO> list(PQ query) {
        QueryWrapper<T> wrapper = query != null ? buildQueryWrapper(query) : new QueryWrapper<>();
        List<T> entities = getMapper().selectList(wrapper);
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }
        return entities.stream().map(this::toVO).collect(Collectors.toList());
    }

    // ============================== 接口实现（单条） ==============================

    @Override
    public ID save(DTO dto) {
        T entity = toEntity(dto);
        doBeforeSave(dto, entity);
        getMapper().insert(entity);
        doAfterSave(entity, true);
        return entity.getId();
    }

    @Override
    public boolean updateById(DTO dto) {
        T entity = toEntity(dto);
        doBeforeUpdate(dto, entity);
        boolean updated = getMapper().updateById(entity) > 0;
        doAfterUpdate(entity, updated);
        return updated;
    }

    @Override
    public boolean removeById(ID id) {
        doBeforeDelete(id);
        boolean removed = getMapper().deleteById(id) > 0;
        doAfterDelete(id, removed);
        return removed;
    }

    // ============================== 接口实现（批量） ==============================

    @Override
    public List<ID> saveBatch(Collection<DTO> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return Collections.emptyList();
        }
        List<ID> ids = new ArrayList<>();
        for (DTO dto : dtos) {
            if (dto == null) {
                continue;
            }
            ids.add(save(dto));
        }
        return ids;
    }

    @Override
    public List<Boolean> updateBatch(Collection<DTO> dtos) {
        if (dtos == null || dtos.isEmpty()) {
            return Collections.emptyList();
        }
        return dtos.stream()
                .filter(Objects::nonNull)
                .map(this::updateById)
                .collect(Collectors.toList());
    }

    @Override
    public List<Boolean> removeBatch(Collection<ID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return ids.stream()
                .filter(Objects::nonNull)
                .map(this::removeById)
                .collect(Collectors.toList());
    }
}