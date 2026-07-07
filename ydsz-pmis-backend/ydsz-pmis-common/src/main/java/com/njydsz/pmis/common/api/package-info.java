/**
 * 统一 API 契约层。
 *
 * <p>定义所有 Controller 共用的响应包装、错误码、分页结构等。
 * 业务模块不应自行实现 Result / PageResult 包装类，所有接口统一返回
 * {@link com.njydsz.pmis.common.api.Result}，便于前端拦截器统一处理。
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@link com.njydsz.pmis.common.api.Result}        - 统一响应封装（code / message / data / traceId / timestamp）</li>
 *   <li>{@link com.njydsz.pmis.common.api.PageResult}    - 统一分页结果（与 MyBatis-Plus Page 互转）</li>
 *   <li>{@link com.njydsz.pmis.common.api.BizErrorCode}  - 业务错误码枚举（按 0/1xxxx/.../9xxxx 段位划分），
 *                                                          同时承担 {@code code → HttpStatus} 映射职责</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>所有 API 必须返回 {@code Result<T>}，禁止直接返回裸对象</li>
 *   <li>分页查询必须返回 {@code PageResult<T>}，禁止返回 MyBatis-Plus Page</li>
 *   <li>错误码统一从 {@code BizErrorCode} 选取，禁止使用魔法数字</li>
 *   <li>错误码同时约定 {@code code → HttpStatus} 映射，便于前端 / 网关 / 监控系统基于 HTTP 状态码分支</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.common.api;
