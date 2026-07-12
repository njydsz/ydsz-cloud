/**
 * 业务服务实现层：实现 {@code system.service} 包中定义的核心业务接口。
 *
 * <p>本包是 Service 接口的具体落地，使用 {@code @Service} 标注并由 Spring 容器管理。
>       业务编排、事务控制、跨实体协作、第三方调用等逻辑均在本包完成。
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@code ConfigServiceImpl} - 动态配置服务实现，支持按 namespace/key 查询、监听变更</li>
 *   <li>{@code FileServiceImpl} - 文件服务基础实现（上传/下载/秒传/删除）</li>
 *   <li>{@code FileEnhanceServiceImpl} - 文件增强服务实现（分片上传、断点续传、秒传校验）</li>
 *   <li>{@code NotificationServiceImpl} - 通知服务实现，集成消息服务与实时推送，
>       邮件投递失败时回退为站内通知</li>
 *   <li>{@code LoginAuditServiceImpl} - 登录审计服务实现，记录登录/登出/失败事件</li>
 *   <li>{@code DataExportAuditServiceImpl} - 数据导出审计服务实现，记录导出人/范围/审批单</li>
 *   <li>{@code FeatureFlagServiceImpl} - 特性开关服务实现，支持按用户/租户灰度</li>
 *   <li>{@code SensitiveOperationServiceImpl} - 敏感操作服务实现（二次鉴权/审批流）</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>实现与接口一一对应</b>：每个 Service 接口在 {@code service.impl} 子包中均有同名
>       {@code XxxServiceImpl} 实现，便于按包扫描与依赖定位</li>
 *   <li><b>构造器注入</b>：使用 Lombok {@code @RequiredArgsConstructor} 注入依赖，
>       字段声明为 {@code final} 增强不可变性</li>
 *   <li><b>事务显式声明</b>：写方法标注 {@code @Transactional(rollbackFor = Exception.class)}，
>       读方法标注 {@code @Transactional(readOnly = true)}</li>
 *   <li><b>日志规范</b>：业务关键路径（发送/删除/审批）使用 INFO 级别记录摘要；
>       异常使用 ERROR 级别并附上下文（业务 ID/用户 ID/错误原因）</li>
 *   <li><b>异常透传</b>：Service 内部不吞异常，校验失败抛 {@code SysException}，
>       第三方调用异常上抛由 Controller 统一捕获</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>禁止在 Impl 中定义 public 方法（仅实现接口方法 + 私有辅助方法）</li>
 *   <li>Impl 之间的依赖通过接口注入，禁止循环依赖</li>
 *   <li>新增 Impl 须在本 {@code package-info.java} 中登记，并保持命名风格一致</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.system.server.service.impl;
