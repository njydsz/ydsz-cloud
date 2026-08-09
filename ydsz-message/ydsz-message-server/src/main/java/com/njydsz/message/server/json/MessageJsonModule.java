package com.njydsz.message.server.json;

import org.springframework.stereotype.Component;

import com.njydsz.common.json.module.JsonModule;
import com.njydsz.common.json.module.ModuleDeserializerRegistry;
import com.njydsz.common.json.module.ModuleSerializerRegistry;

/**
 * 消息模块 YdszJson SPI 注册。
 *
 * <p>通过 {@link JsonModule.SpringFactory} 机制接入 YdszJson 引擎的模块化序列化框架，
 * 由 {@code JsonAutoConfiguration} 按类型注入 {@code List<JsonModule>} 后，
 * 经 {@code JsonModuleRegistrar} 自动发现并注册到全局序列化引擎。</p>
 *
 * <h3>序列化策略说明（v1.9.3 修订）</h3>
 *
 * <p><b>不再注册抛异常的占位序列化器。</b>此前版本为消息模板（{@code MsgTemplate}）、
 * 站内通知（{@code MsgNotification}）、路由规则（{@code MsgRouteRule}）、消息日志
 * （{@code MsgLog}）等类型注册了抛出 {@link UnsupportedOperationException} 的占位实现，
 * 但引擎序列化路径（{@code SerializationProvider.serialize}）在发现已注册的自定义序列化器后
 * 会<em>直接调用</em>它而非回退默认反射序列化——这意味着任何对这些核心类型的 JSON 序列化
 * 都会在运行期抛异常，属于严重隐患。</p>
 *
 * <p>当前这些领域对象均为普通 POJO（含 {@code @JsonClass} 元数据、Lombok getter 等），
 * 引擎默认的反射序列化/反序列化链路已能正确处理，因此本模块<em>不注册任何覆盖型序列化器</em>，
 * 由引擎默认链路接管，保证序列化行为正确且稳定。</p>
 *
 * <h3>后续定制入口</h3>
 *
 * <p>当业务需要以下定制策略时，再按需在此注册真实实现（而非占位）：</p>
 * <ul>
 *   <li>枚举值驼峰化 / 自定义枚举展示（{@code status}、{@code auditStatus}、{@code channel} 等）</li>
 *   <li>敏感字段脱敏（站内通知 {@code MsgNotification} 正文中的个人身份信息）</li>
 *   <li>JSON 字符串字段自动展开（{@code variableDefs}、{@code configJson} 等）</li>
 *   <li>VO 视图裁剪（按前端契约精简字段、时间格式本地化）</li>
 *   <li>多语言字段按 {@code locale} 折叠输出</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Component
public class MessageJsonModule implements JsonModule, JsonModule.SpringFactory {

    @Override
    public String getModuleName() {
        return "messageJsonModule";
    }

    @Override
    public void setSerializers(ModuleSerializerRegistry registry) {
        // 当前策略：不注册覆盖型序列化器，由引擎默认反射序列化链路接管。
        // 业务需要定制时（枚举驼峰化 / 脱敏 / JSON 字段展开 / VO 裁剪等），
        // 在此按需注册真实 JsonSerializer 实现。
    }

    @Override
    public void setDeserializers(ModuleDeserializerRegistry registry) {
        // 当前策略：不注册覆盖型反序列化器，由引擎默认反射反序列化链路接管。
        // 典型定制场景：RPC 回调体解析、RocketMQ 消息体反序列化、Webhook payload 映射。
    }

    @Override
    public int getPriority() {
        return 10;
    }
}
