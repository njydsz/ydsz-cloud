package com.njydsz.common.domain.converter;

/**
 * Converter 基类接口 — 统一 DTO ↔ Entity ↔ VO 转换方法命名规范。
 *
 * <p>P2-5: 各业务模块 Converter 接口应继承此接口，确保方法命名统一。
 *
 * <h3>规范方法签名</h3>
 * <ul>
 *   <li>{@link #postDtoToEntity} — POST 请求 DTO → Entity（新增场景）</li>
 *   <li>{@link #putDtoToEntity} — PUT 请求 DTO → Entity（更新场景）</li>
 *   <li>{@link #entityToVO} — Entity → VO（查询返回场景）</li>
 *   <li>{@link #queryToEntity} — 查询条件 DTO → Entity（条件查询场景）</li>
 * </ul>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * @Mapper
 * public interface ProjectConverter extends BaseConverter<ProjectPostDTO, ProjectPutDTO, Project, ProjectVO> {
 *     // BaseConverter 提供的方法已足够，通常无需额外方法
 *     // 如需自定义映射，添加 @Mapping 注解即可
 * }
 * }</pre>
 *
 * @param <PostDTO> 新增请求 DTO 类型
 * @param <PutDTO>  更新请求 DTO 类型
 * @param <E>       Entity 实体类型
 * @param <V>       VO 返回类型
 * @author ydsz-team
 * @since 1.0.0
 */
public interface BaseConverter<PostDTO, PutDTO, E, V> {

    /**
     * POST 请求 DTO → Entity 转换（新增场景）。
     *
     * @param dto 新增请求 DTO
     * @return Entity 实体
     */
    E postDtoToEntity(PostDTO dto);

    /**
     * PUT 请求 DTO → Entity 转换（更新场景）。
     *
     * @param dto 更新请求 DTO
     * @return Entity 实体
     */
    E putDtoToEntity(PutDTO dto);

    /**
     * Entity → VO 转换（查询返回场景）。
     *
     * @param entity Entity 实体
     * @return VO 返回对象
     */
    V entityToVO(E entity);

    /**
     * 查询条件 DTO → Entity 转换（条件查询场景）。
     *
     * <p>默认实现返回 null，各模块按需覆写。
     *
     * @param queryDTO 查询条件 DTO
     * @return Entity 实体（仅包含查询条件字段）
     */
    default E queryToEntity(Object queryDTO) {
        return null;
    }
}
