package com.njydsz.example.feign.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.feign.FeignClientConstants;
import com.njydsz.example.feign.vo.CreateUserRequest;
import com.njydsz.example.feign.vo.UserVO;

/**
 * 用户服务 Feign 客户端（Quickstart 示例）。
 *
 * <p>演示 YdszFeign 的核心能力：
 *
 * <ul>
 *   <li>服务名通过 {@link FeignClientConstants} 常量引用（{@link FeignClientConstants#USERINFO USERINFO}）
 *   <li>GET /api/user/{id} → 返回 {@link UserVO}（自动解包）
 *   <li>POST /api/user → 返回 {@link YdszResponse}（手动拆包）
 * </ul>
 *
 * <p><b>YdszResponse 解包行为：</b>
 *
 * <ul>
 *   <li>返回类型声明为业务类型 → 自动从 {@code YdszResponse.data} 提取
 *   <li>返回类型声明为 YdszResponse → 直接获取完整响应（包含 code/msg）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.19
 */
@FeignClient(name = FeignClientConstants.USERINFO, contextId = "userClient")
public interface UserClient {

  /**
   * 根据 ID 查询用户信息。
   *
   * <p>服务端返回 {@code YdszResponse<UserVO>}，客户端接口可直接声明返回 {@link UserVO}（ResponseUnwrapDecoder 自动解包）。
   *
   * @param id 用户 ID
   * @return 用户视图对象
   */
  @GetMapping(FeignClientConstants.USERINFO_PATH_USER_INFO + "/{id}")
  UserVO getUser(@PathVariable("id") Long id);

  /**
   * 创建用户。
   *
   * <p>返回 {@link YdszResponse} 包含生成的用户 ID，调用方可获取完整响应信息。
   *
   * @param request 创建用户请求
   * @return 创建结果
   */
  @PostMapping("/internal/user/create")
  YdszResponse<Long> createUser(@RequestBody CreateUserRequest request);
}
