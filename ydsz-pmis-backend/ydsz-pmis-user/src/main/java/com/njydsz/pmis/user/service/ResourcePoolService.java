package com.njydsz.pmis.user.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.user.dto.ResourcePoolCreateDTO;
import com.njydsz.pmis.user.entity.ResourcePoolDO;

import java.util.List;

/**
 * 资源池服务
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface ResourcePoolService {

    /**
     * 创建资源池
     *
     * @param dto 资源池表单
     * @return 新建资源池 ID
     */
    Long create(ResourcePoolCreateDTO dto);

    /**
     * 更新资源池
     *
     * @param id  资源池 ID
     * @param dto 资源池表单
     */
    void update(Long id, ResourcePoolCreateDTO dto);

    /**
     * 删除资源池
     *
     * @param id 资源池 ID
     */
    void delete(Long id);

    /**
     * 根据 ID 查询资源池
     *
     * @param id 资源池 ID
     * @return 资源池实体，不存在时返回 null
     */
    ResourcePoolDO getById(Long id);

    /**
     * 按池类型查询资源池列表
     *
     * @param poolType 池类型（PoolType.code）
     * @return 资源池列表
     */
    List<ResourcePoolDO> listByType(String poolType);

    /**
     * 按部门查询资源池列表
     *
     * @param departmentId 部门 ID
     * @return 资源池列表
     */
    List<ResourcePoolDO> listByDept(Long departmentId);

    /**
     * 分页查询资源池
     *
     * @param page     页码
     * @param size     每页条数
     * @param poolType 池类型（可空）
     * @param status   状态（可空）
     * @return 分页结果
     */
    Page<ResourcePoolDO> page(int page, int size, String poolType, String status);
}
