/**
 * Seata 全局事务切面与反射桥接器。
 *
 * <p>本包是 ydzs-common-seata 中唯一允许（间接）调用 Seata 原生 API 的层，
 * 集中所有对 {@code io.seata.tm.api.GlobalTransaction} /
 * {@code io.seata.core.context.RootContext} 的反射调用
 * （收口于 {@link com.njydsz.common.seata.aspect.SeataReflector}），
 * 供 ArchUnit（YDIZ-COMMON-003）约束"反射调用 Seata API 的代码仅允许
 * 出现在 ydzs-common-seata 模块内部"。
 *
 * @since ACC-1
 */
package com.njydsz.common.seata.aspect;
