/**
 * 流程业务 VO 视图对象包。
 *
 * <p>包含流程引擎各模块的视图对象（VO），用于 Controller 层返回给前端的响应数据。
 *
 * <p><b>VO 命名规范：</b>
 *
 * <ul>
 *   <li>{@code FlowXxxVO} — 通用视图对象
 *   <li>{@code FlowXxxTreeVO} — 树形结构视图对象
 *   <li>{@code StringVO} — 字符串视图对象
 * </ul>
 *
 * <p><b>设计原则：</b>VO 仅包含展示所需的字段，不包含敏感信息（如密码、内部 ID 等）。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
package com.njydsz.workflow.domain.vo;
