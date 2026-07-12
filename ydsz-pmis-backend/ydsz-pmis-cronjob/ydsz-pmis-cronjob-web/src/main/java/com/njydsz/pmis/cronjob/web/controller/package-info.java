/**
 * 定时任务模块 - 控制器层。
 *
 * <p>对外暴露任务管理接口：
 * <ul>
 *   <li>查询任务列表 / 任务运行历史</li>
 *   <li>手动触发任务执行（立即跑一次）</li>
 *   <li>终止正在运行的任务</li>
 *   <li>查询任务执行日志</li>
 * </ul>
 *
 * <h3>权限</h3>
 * <ul>
 *   <li>所有接口需 {@code @AuthApiPermission(apiCodes = "cronjob:*")} 权限码</li>
 *   <li>手动触发 / 终止任务需 {@code @RequireReAuth} 二次认证</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.cronjob.web.controller;
