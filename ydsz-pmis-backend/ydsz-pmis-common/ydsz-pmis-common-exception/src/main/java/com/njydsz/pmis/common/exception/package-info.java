/**
 * 全局异常处理层。
 *
 * <p>所有 Controller 抛出的异常统一由
 * {@link com.njydsz.pmis.common.exception.GlobalExceptionHandler} 拦截，
 * 转换为 {@code Result} 格式返回，避免堆栈信息泄漏给前端。
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>业务异常（{@link com.njydsz.pmis.common.exception.BizException}）携带业务错误码，
 *       映射到对应 HTTP 状态码</li>
 *   <li>系统异常（{@code Exception}）一律返回 500 + 脱敏后的 TraceId，便于排查但不暴露细节</li>
 *   <li>i18n 支持：通过 {@code Accept-Language} 头解析本地化消息</li>
 *   <li>数据库异常分类处理：唯一键冲突 / 完整性约束 / 乐观锁 / 查询超时 / 连接失败分别映射到不同错误码</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.common.exception;
