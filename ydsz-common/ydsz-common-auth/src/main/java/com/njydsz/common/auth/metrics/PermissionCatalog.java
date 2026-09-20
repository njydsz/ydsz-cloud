package com.njydsz.common.auth.metrics;

import java.util.List;

/**
 * 权限目录不可变快照。
 *
 * <p>由 {@link PermissionCatalogRegistry#snapshot()} 生成，包含某一时刻所有已注册权限条目的不可变列表
 * 以及快照时间戳。
 *
 * @param entries 不可变权限条目列表
 * @param snapshotMillis 快照时间戳（毫秒）
 * @author ydsz-team
 * @since 26.09.01
 */
public record PermissionCatalog(List<PermissionCatalogRegistry.PermissionEntry> entries, long snapshotMillis) {}
