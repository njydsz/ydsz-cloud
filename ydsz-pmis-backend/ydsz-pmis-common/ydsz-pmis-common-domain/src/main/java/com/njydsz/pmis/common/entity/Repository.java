package com.njydsz.pmis.common.entity;

import java.util.List;
import java.util.Optional;

/**
 * 领域仓库接口（Repository Pattern）
 *
 * <p>DDD 中 Repository 的抽象基接口，业务模块的 Repository 继承此接口。
 * 提供基本的 CRUD 操作约定，具体实现由 infra 层的 Mapper 完成。
 *
 * @param <T>  聚合根类型
 * @param <ID> 主键类型
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
public interface Repository<T extends BaseEntity, ID> {

    /**
     * 保存（新增或更新）
     *
     * @param entity 实体
     * @return 保存后的实体（含生成的主键）
     */
    T save(T entity);

    /**
     * 批量保存
     *
     * @param entities 实体列表
     * @return 保存后的实体列表
     */
    List<T> saveAll(List<T> entities);

    /**
     * 根据 ID 查询
     *
     * @param id 主键
     * @return 实体（Optional 包装）
     */
    Optional<T> findById(ID id);

    /**
     * 查询全部
     *
     * @return 实体列表
     */
    List<T> findAll();

    /**
     * 根据 ID 删除
     *
     * @param id 主键
     */
    void deleteById(ID id);

    /**
     * 删除实体
     *
     * @param entity 实体
     */
    void delete(T entity);

    /**
     * 根据 ID 判断是否存在
     *
     * @param id 主键
     * @return true 表示存在
     */
    boolean existsById(ID id);

    /**
     * 统计总数
     *
     * @return 总数
     */
    long count();
}
