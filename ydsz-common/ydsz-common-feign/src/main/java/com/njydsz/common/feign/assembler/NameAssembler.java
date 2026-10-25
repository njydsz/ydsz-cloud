package com.njydsz.common.feign.assembler;

import java.util.Collection;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * ID → 名称富化组件接口。
 *
 * <p>跨服务解析业务对象 ID 为用户可读名称，用于 VO 场景中的 createdBy/assignee 等字段富化。 该接口由具体业务域实现（如 userinfo 模块通过
 * OrgQueryClient 实现）， common-feign 提供 {@link NoOpNameAssembler} 兜底。
 *
 * <p>使用方通过 {@code @Autowired} 注入后调用：
 *
 * <pre>{@code
 * nameAssembler.enrich(voList,
 *         MyVO::getUserId,
 *         MyVO::setUserName,
 *         NameType.USER);
 * }</pre>
 *
 * TODO: 通用 ID→名称富化能力不属于 Feign 职责，应迁移至 ydsz-common-core。当前保留以兼容
 *     ydsz-workflow 调用方。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
public interface NameAssembler extends com.njydsz.common.core.assembler.NameAssembler {
}
