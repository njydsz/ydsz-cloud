package com.njydsz.system.web.controller;

import com.njydsz.common.core.response.BaseResponse;
import com.njydsz.common.core.response.PageResponse;

/**
 * 系统模块统一 Controller 基类
 *
 * <p>封装通用 CRUD 模板代码，消除各 Controller 重复的 pageSize 截断、分页参数规范化等样板代码。
 *
 * <p><b>核心能力：</b>
 *
 * <ul>
 *   <li>pageSize 服务端硬上限截断（防止深度分页 OOM）
 *   <li>pageNum/pageSize 参数规范化
 *   <li>统一的成功响应包装
 * </ul>
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * @RestController
 * @RequestMapping("/api/v1/config")
 * public class ConfigController extends BaseController {
 *
 *   @GetMapping("/page")
 *   public PageResponse<List<ConfigVO>> page(@RequestParam int pageNum, @RequestParam int pageSize) {
 *     return service.page(normalizePageNum(pageNum), normalizePageSize(pageSize));
 *   }
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public abstract class BaseController {

  /** 分页安全上限：防止 pageSize=999999 导致深度分页 OOM */
  protected static final int MAX_PAGE_SIZE = 500;

  /** 默认页码 */
  protected static final int DEFAULT_PAGE_NUM = 1;

  /** 默认每页条数 */
  protected static final int DEFAULT_PAGE_SIZE = 10;

  /**
   * 规范化 pageSize，确保在 [1, MAX_PAGE_SIZE] 范围内
   *
   * <p>防止客户端传入过大 pageSize 导致深度分页 OOM。
   *
   * @param pageSize 原始每页条数
   * @return 规范化后的每页条数
   */
  protected int normalizePageSize(int pageSize) {
    return Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE);
  }

  /**
   * 规范化 pageNum，确保不小于 1
   *
   * @param pageNum 原始页码
   * @return 规范化后的页码
   */
  protected int normalizePageNum(int pageNum) {
    return Math.max(pageNum, 1);
  }

  /**
   * 构建成功响应
   *
   * @param data 响应数据
   * @param <T> 数据类型
   * @return 成功响应
   */
  protected <T> BaseResponse<T> success(T data) {
    return BaseResponse.success(data);
  }

  /**
   * 构建无数据成功响应
   *
   * @return 成功响应
   */
  protected BaseResponse<Void> success() {
    return BaseResponse.success();
  }

  /**
   * 构建分页成功响应
   *
   * @param data 分页数据
   * @param <T> 数据类型
   * @return 分页成功响应
   */
  protected <T> PageResponse<T> successPage(T data) {
    return PageResponse.success(data);
  }
}
