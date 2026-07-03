package com.njydsz.pmis.iam.dto;

import com.njydsz.pmis.common.api.PageResult;
import com.njydsz.pmis.iam.vo.UserVO;

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
     *
     * @param id 用户 ID
     * @return 用户视图对象，不存在时返回 null
     */
    UserVO getById(Long id);

    /**
     * 批量查询用户
     *
     * @param ids 用户 ID 列表
     * @return 用户视图对象列表
     */
    List<UserVO> listByIds(List<Long> ids);

    /**
     * 分页查询用户
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<UserVO> page(UserQueryDTO query);
}
