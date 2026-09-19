package com.njydsz.example.feign.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.feign.actuator.FeignHealthSnapshot;
import com.njydsz.example.feign.client.UserClient;
import com.njydsz.example.feign.vo.CreateUserRequest;
import com.njydsz.example.feign.vo.UserVO;

/**
 * 演示控制器（Quickstart 示例）。
 *
 * <p>演示 UserClient 的使用方式及 YdszResponse 自动解包能力。
 *
 * @author ydsz-team
 * @since 26.09.19
 */
@RestController
@RequestMapping("/demo")
public class DemoController {

  @Autowired private UserClient userClient;

  @Autowired private FeignHealthSnapshot feignHealthSnapshot;

  /**
   * 查询用户（演示 YdszResponse 自动解包）。
   *
   * <p>Feign 服务端返回 {@code YdszResponse<UserVO>}，UserClient 接口声明返回 UserVO， 由 ResponseUnwrapDecoder 自动提取 data 字段。
   *
   * @param id 用户 ID
   * @return UserVO
   */
  @GetMapping("/user/{id}")
  public UserVO getUser(@PathVariable Long id) {
    return userClient.getUser(id);
  }

  /**
   * 创建用户（演示返回完整 YdszResponse）。
   *
   * <p>UserClient.createUser 返回 {@code YdszResponse<Long>}，调用方可获取 code/msg 信息。
   *
   * @param request 创建用户请求
   * @return YdszResponse
   */
  @PostMapping("/user")
  public com.njydsz.common.core.response.YdszResponse<Long> createUser(
      @RequestBody CreateUserRequest request) {
    return userClient.createUser(request);
  }

  /**
   * 获取 Feign 模块健康快照。
   *
   * @return 健康快照的 details Map
   */
  @GetMapping("/health/feign")
  public java.util.Map<String, Object> feignHealthSnapshot() {
    return feignHealthSnapshot.getDetails();
  }
}
