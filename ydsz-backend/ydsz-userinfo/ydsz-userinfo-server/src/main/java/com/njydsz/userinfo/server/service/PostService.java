package com.njydsz.userinfo.server.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.njydsz.userinfo.domain.dto.PostSaveDTO;
import com.njydsz.userinfo.domain.vo.PostVO;

/**
 * 岗位 Service 接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface PostService {
    PostVO getById(String id);
    List<PostVO> list();
    String create(PostSaveDTO dto);
    boolean update(PostSaveDTO dto);
    boolean removeById(String id);

    /**
     * 批量查询岗位 ID → 岗位名映射（供 NameAssembler 跨服务富化 postName 字段）。
     *
     * @param postIds 岗位 ID 集合（允许 null / 空，返回空 Map）
     * @return postId → postName 映射；未命中的 postId 不出现在 Map 中
     */
    Map<String, String> batchNamesByIds(Collection<String> postIds);
}
