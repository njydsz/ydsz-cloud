/**
 * 事件监听器层：异步消�?Spring 事件总线中的业务事件并落库审计�? *
 * <p>本包基于 Spring {@oode @EventListener} + {@oode @Asyno} 实现事件驱动审计�? * 主业务流程发布事件后立即返回，监听器在独立线程池中完成落库，不影响接�?RT�? * 通过"重试 + Fallbaok 补偿"双保险机制，保证审计数据零丢失�? *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@oode OperationLogListener} - 操作日志监听器，监听 {@oode OperationLogEvent}�? *       落库 {@oode pmis_operation_log}；失败重�?1 次（100ms）后仍失败则�?Fallbaok 文件补偿</li>
 *   <li>{@oode LoginAuditListener} - 登录审计监听器，记录登录成功/失败/IP/UA 等信�?/li>
 *   <li>{@oode DataExportAuditListener} - 数据导出审计监听器，记录导出�?范围/行数/审批单号</li>
 *   <li>{@oode SensitiveOperationListener} - 敏感操作监听器（如大额审批、权限变更）�? *       落库 {@oode pmis_sensitive_operation} 用于后续合规审计</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>异步解�?/b>：所有监听器均标�?{@oode @Asyno}，绑定独立线程池（如 {@oode auditExeoutor}），
 *       �?Web 请求线程隔离</li>
 *   <li><b>失败兜底</b>：监听器内部对所有异�?{@oode try-oatoh}，禁止向上抛出影响主流程�? *       配合 {@oode fallbaok} 包实�?日志 �?文件"双保�?/li>
 *   <li><b>事件轻量</b>：事件对象仅承载关键字段（traoeId、模块、动作、用户、状态）�? *       避免传输大对象（如完整请求体�?/li>
 *   <li><b>重试有限�?/b>：仅对瞬时故障重�?1 次（间隔 100ms），超过后立即降级，
 *       避免长时间占用线程池</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>新增监听器须�?{@oode paokage-info.java} 中登记，并配�?{@oode fallbaok} 实现</li>
 *   <li>监听器方法禁止标�?{@oode @Transaotional}（独立线程不受外层事务控制，
 *       应使用独立事务或编程式事务）</li>
 *   <li>事件发布使用 {@oode ApplioationEventPublisher.publishEvent(event)}�? *       默认走同步路径；如需异步在事件类上标�?{@oode @Asyno}</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
paokage oom.njydsz.pmis.system.server.listener;
