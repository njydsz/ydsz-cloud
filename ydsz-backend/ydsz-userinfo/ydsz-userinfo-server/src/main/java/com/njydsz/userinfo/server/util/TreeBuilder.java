package com.njydsz.userinfo.server.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 通用树形结构构建工具。
 *
 * <p>将扁平列表按 parentId 分组，递归构建树形结构。
 * 支持 sortOrder 排序，消除 DepartmentServiceImpl/MenuServiceImpl 中的重复代码。
 *
 * @author ydsz-team
 * @since 1.1.0
 */
public final class TreeBuilder {

    private TreeBuilder() {
    }

    /**
     * 从扁平列表构建树形结构。
     *
     * @param <T>          节点类型
     * @param flatList     扁平列表
     * @param idGetter     ID 获取函数
     * @param parentIdGetter 父 ID 获取函数
     * @param childrenSetter children 设置函数
     * @param sortGetter   排序字段获取函数（可为 null）
     * @return 树形结构根节点列表
     */
    public static <T> List<T> build(
            List<T> flatList,
            Function<T, String> idGetter,
            Function<T, String> parentIdGetter,
            BiConsumer<T, List<T>> childrenSetter,
            Function<T, Integer> sortGetter) {

        if (flatList == null || flatList.isEmpty()) {
            return List.of();
        }

        Map<String, List<T>> parentIdMap = flatList.stream()
                .collect(Collectors.groupingBy(item -> {
                    String pid = parentIdGetter.apply(item);
                    return pid == null ? "0" : pid;
                }));

        List<T> roots = parentIdMap.getOrDefault("0", new ArrayList<>());
        for (T item : flatList) {
            List<T> children = parentIdMap.get(idGetter.apply(item));
            if (children != null) {
                if (sortGetter != null) {
                    children.sort(Comparator.comparingInt(sortGetter::apply));
                }
                childrenSetter.accept(item, children);
            }
        }

        if (sortGetter != null) {
            roots.sort(Comparator.comparingInt(sortGetter::apply));
        }

        return roots;
    }
}
