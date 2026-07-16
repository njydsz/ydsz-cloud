package com.njydsz.common.file.domain;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 对象列表结果
 * <p>封装分页列举对象的返回结果，包含对象列表和分页信息。
 *
 * @author ydsz-team
 * @since 1.0.0
 * 
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ListObjectsResult {

    /**
     * 对象元信息列表
     */
    private List<ObjectMetadata> objects;

    /**
     * 下次继续列举的游标（null 表示已列举完毕）
     */
    private String nextCursor;

    /**
     * 是否还有更多对象
     */
    private boolean hasMore;

    /**
     * 本次返回的对象数量
     */
    private int objectCount;

    /**
     * 构造空结果
     */
    public static ListObjectsResult empty() {
        ListObjectsResult result = new ListObjectsResult();
        result.setObjects(List.of());
        result.setNextCursor(null);
        result.setHasMore(false);
        result.setObjectCount(0);
        return result;
    }
}