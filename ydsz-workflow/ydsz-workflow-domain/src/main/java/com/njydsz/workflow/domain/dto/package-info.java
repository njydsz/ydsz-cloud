/**
 * 流程业务 DTO 数据传输对象包。
 *
 * <p>包含流程引擎各模块的数据传输对象（DTO），用于 Controller 与 Service 层之间的数据传输。
 *
 * <p><b>DTO 命名规范：</b>
 *
 * <ul>
 *   <li>{@code FlowXxxDTO} — 通用数据传输对象
 *   <li>{@code FlowXxxCreateDTO} — 创建操作专用
 *   <li>{@code FlowXxxPostDTO} — HTTP POST 请求专用
 *   <li>{@code FlowXxxPutDTO} — HTTP PUT 请求专用
 * </ul>
 *
 * <p><b>设计原则：</b>DTO 仅包含数据字段和必要的验证注解，不包含业务逻辑。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
package com.njydsz.workflow.domain.dto;
