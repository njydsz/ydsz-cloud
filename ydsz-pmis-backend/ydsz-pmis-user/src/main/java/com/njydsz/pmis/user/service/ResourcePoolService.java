package com.njydsz.pmis.user.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.user.dto.ResourcePoolCreateDTO;
import com.njydsz.pmis.user.entity.ResourcePoolDO;

import java.util.List;

/**
 * 资源池服务
 */
public interface ResourcePoolService {

    Long create(ResourcePoolCreateDTO dto);

    void update(Long id, ResourcePoolCreateDTO dto);

    void delete(Long id);

    ResourcePoolDO getById(Long id);

    List<ResourcePoolDO> listByType(String poolType);

    List<ResourcePoolDO> listByDept(Long departmentId);

    Page<ResourcePoolDO> page(int page, int size, String poolType, String status);
}
