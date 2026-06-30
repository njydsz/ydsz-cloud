package com.njydsz.pmis.user.dto;

import com.njydsz.pmis.common.api.PageResult;
import com.njydsz.pmis.user.vo.UserVO;

import java.util.List;

/**
 * 用户服务 Feign 接口
 *
 * <p>供其他微服务调用，远程获取用户信息
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface UserFeignClient {

    /**
     * 根据 ID 查询用户
     */
    UserVO getById(Long id);

    /**
     * 批量查询
     */
    List<UserVO> listByIds(List<Long> ids);

    /**
     * 分页查询
     */
    PageResult<UserVO> page(UserQueryDTO query);
}
