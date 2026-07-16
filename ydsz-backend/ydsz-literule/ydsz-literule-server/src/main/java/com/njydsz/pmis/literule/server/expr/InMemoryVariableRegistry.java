package com.njydsz.literule.server.expr;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存变量注册表
 *
 * <p>基于 {@link ConcurrentHashMap} 的编程式实现，支持 {@link #register} 动态注册。
 * 主要用于：
 * <ul>
 *   <li>单元测试：构造临时变量空间验证校验逻辑</li>
 *   <li>启动时静态注册：在 {@code @PostConstruct} 中批量注册内置变量</li>
 *   <li>降级方案：当数据库实现不可用时回退到内存实现</li>
 * </ul>
 *
 * <p>线程安全：所有读写操作基于 {@link ConcurrentHashMap}，{@link #listAll()} 返回快照副本。
 *
 * @since 1.4.0
 */
public class InMemoryVariableRegistry implements VariableRegistry {

    private final Map<String, VariableDefinition> store = new ConcurrentHashMap<>();

    public InMemoryVariableRegistry() {
    }

    public InMemoryVariableRegistry(Collection<VariableDefinition> initial) {
        if (initial != null) {
            initial.forEach(this::register);
        }
    }

    @Override
    public VariableDefinition lookup(String name) {
        if (name == null) return null;
        return store.get(name);
    }

    @Override
    public boolean contains(String name) {
        return name != null && store.containsKey(name);
    }

    @Override
    public List<VariableDefinition> listAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public void register(VariableDefinition definition) {
        if (definition == null || definition.getName() == null) {
            throw new IllegalArgumentException("变量定义及 name 不能为空");
        }
        store.put(definition.getName(), definition);
    }

    @Override
    public void registerAll(Collection<VariableDefinition> definitions) {
        if (definitions == null) return;
        definitions.forEach(this::register);
    }

    @Override
    public boolean isEmpty() {
        return store.isEmpty();
    }

    /**
     * 清空所有已注册变量
     */
    public void clear() {
        store.clear();
    }

    /**
     * 已注册变量数量
     *
     * @return 变量数量
     */
    public int size() {
        return store.size();
    }
}
