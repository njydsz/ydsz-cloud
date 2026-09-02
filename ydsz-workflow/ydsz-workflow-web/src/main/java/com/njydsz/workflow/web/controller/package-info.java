/**
 * 工作流 Web 层 Controller 包。
 *
 * <p>提供流程引擎的 HTTP REST API 接口，包括流程定义、流程实例、任务管理等。
 *
 * <p><b>设计原则：</b>
 *
 * <ul>
 *   <li>仅做参数透传、权限校验、VO 转换，不包含业务逻辑</li>
 *   <li>所有业务逻辑下沉到 Service 层</li>
 *   <li>返回 VO/DTO，不直接返回 DO 实体</li>
 * </ul>
 *
 * <p><b>DDD 分层规范：</b>Controller 层只依赖 Service 层，不直接依赖 infra 层。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
package com.njydsz.workflow.web.controller;
