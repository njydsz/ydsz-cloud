package com.njydsz.pmis.sales.server.service;

import com.njydsz.pmis.sales.server.dto.InitiationCreateDTO;

/**
 * 立项服务接口（本地接口，用于商机转立项）
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface InitiationService {

    /**
     * 创建立项
     *
     * @param dto 立项创建参数
     * @return 立项 ID
     */
    String create(InitiationCreateDTO dto);
}