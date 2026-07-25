package com.njydsz.system.server.service;

import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.system.domain.dto.AppInfoDTO;
import com.njydsz.system.domain.entity.AppInfoDO;
import com.njydsz.system.domain.vo.AppInfoVO;

/**
 * 应用注册 Service。
 *
 * <p>提供应用 CRUD、密钥校验、分页查询等能力。
 *
 * @author ydsz-team
 */
public interface AppInfoService {

    AppInfoVO getById(String id);

    /**
     * 校验应用密钥（BCrypt）。
     *
     * @param appKey    应用 Key
     * @param appSecret 应用密钥明文
     * @return 校验通过返回 true
     */
    boolean validateClient(String appKey, String appSecret);

    IPage<AppInfoDO> page(int pageNum, int pageSize);

    List<AppInfoDO> list();

    String save(AppInfoDTO dto);

    boolean updateById(AppInfoDTO dto);

    boolean removeById(String id);
}
