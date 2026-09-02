package com.njydsz.common.util.collection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * 集合工具类
 *
 * <p>提供项目高频使用的集合操作方法，聚焦于 JDK 未覆盖的能力：
 *
 * <ul>
 *   <li>判空检查：isEmpty / isNotEmpty（支持 Collection、Map、Iterable，null 安全）
 *   <li>类型转换：listToMap、listToGroup、convertList（null 安全的 stream 包装）
 *   <li>过滤操作：filter（null 安全）
 *   <li>查找操作：findFirst、findLast（null 安全，findLast 对 List 做了优化）
 * </ul>
 *
 * <p><b>不提供的能力（直接使用 JDK / Stream API）：</b>
 *
 * <ul>
 *   <li>集合创建 → {@code new ArrayList<>(16)}