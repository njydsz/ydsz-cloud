/**
 * 消息模板引擎层：负责消息内容中占位符的变量替换与最终渲染。
 *
 * <p>本包定义模板渲染的统一接口与默认实现，消息发送流程中
>       {@code MessageService.loadTemplate()} 加载模板后，调用 {@code TemplateEngine.render()}
>       将 {@code ${varName}} 占位符替换为入参中的实际值。模板与业务数据解耦，
>       运营人员可独立维护模板内容而无需改代码。
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@code TemplateEngine} - 模板引擎接口，定义 {@code render(String template, Map<String, Object> params)}
>       方法，输入模板字符串 + 参数映射，输出渲染后文本</li>
 *   <li>{@code DefaultTemplateEngine} - 默认实现，基于正则表达式匹配
>       {@code ${varName}} 与 {@code {{varName}}} 两种占位符，递归渲染嵌套 Map/List 结构</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li><b>接口与实现分离</b>：{@code TemplateEngine} 接口定义契约，具体实现可替换
>       （如未来切换为 Velocity/Freemarker/Thymeleaf）</li>
 *   <li><b>占位符容错</b>：参数缺失时保留原始占位符（{@code ${unknown}}）而非抛异常，
>       避免运营误配导致消息发送失败</li>
 *   <li><b>无状态线程安全</b>：实现类无成员变量，可在多线程下安全并发使用</li>
 *   <li><b>支持嵌套结构</b>：参数可为 {@code Map} 或 {@code List}，渲染时调用 {@code toString()}
>       转为字符串</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>占位符命名遵循 Java 变量命名规范（小驼峰、数字下划线），避免特殊字符</li>
 *   <li>运营配置模板时需与代码中的 {@code MessageSendDTO.params} 字段保持一致</li>
 *   <li>新增模板引擎实现须实现 {@code TemplateEngine} 接口并标注 {@code @Component}</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.system.template;
