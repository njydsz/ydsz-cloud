package com.remisoft.common.feign.assembler;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

import lombok.extern.slf4j.Slf4j;

/**
 * NameAssembler 的 NoOp 实现（空操作降级）。
 *
 * <p>当业务模块未引入任何具体 NameAssembler 实现时（如纯网关、定时任务模块），
 * 由 {@link NameAssemblerAutoConfiguration} 兜底注册，避免 {@code @Autowired NameAssembler}
 * 注入失败导致启动异常。
 *
 * <p>所有方法均返回空 Map / null，不进行任何 Feign 调用，也不修改对象。
 * 若业务方需要真实富化能力，应引入 {@code remi-userinfo-api} 模块（含 {@code UserInfoNameAssembler}）。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Slf4j
public class NoOpNameAssembler implements NameAssembler {

    @Override
    public Map<String, String> batchResolveNames(NameType type, Collection<String> ids) {
        return Collections.emptyMap();
    }

    @Override
    public String resolveName(NameType type, String id) {
        return null;
    }

    @Override
    public <T> void enrich(Collection<T> objects,
                           Function<T, String> idGetter,
                           BiConsumer<T, String> nameSetter,
                           NameType type) {
        // NoOp：不进行任何富化
    }

    @Override
    public <T> void enrichOne(T obj,
                              Function<T, String> idGetter,
                              BiConsumer<T, String> nameSetter,
                              NameType type) {
        // NoOp：不进行任何富化
    }
}
