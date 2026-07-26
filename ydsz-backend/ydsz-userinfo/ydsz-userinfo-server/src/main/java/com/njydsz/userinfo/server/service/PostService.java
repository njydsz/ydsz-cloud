package com.njydsz.userinfo.server.service;

import java.util.List;

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
}
