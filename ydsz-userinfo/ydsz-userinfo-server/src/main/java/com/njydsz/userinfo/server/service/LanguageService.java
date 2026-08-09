package com.njydsz.userinfo.server.service;

import java.util.List;

import com.njydsz.common.domain.query.PageResponse;
import com.njydsz.userinfo.domain.dto.post.LanguagePostDTO;
import com.njydsz.userinfo.domain.dto.put.LanguagePutDTO;
import com.njydsz.userinfo.domain.query.LanguagePageQuery;
import com.njydsz.userinfo.domain.vo.LanguageVO;

/**
 * 语言 Service 接口
 *
 * <p>封装语言的完整业务逻辑：CRUD、默认语言唯一性管理。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface LanguageService {

    /**
     * 分页查询语言列表。
     *
     * @param query 分页查询参数
     * @return 分页结果
     */
    PageResponse<LanguageVO> page(LanguagePageQuery query);

    /**
     * 按 ID 查询语言详情。
     *
     * @param id 主键 ID
     * @return 语言 VO
     */
    LanguageVO getById(String id);

    /**
     * 查询全部未删除语言列表。
     *
     * @return 语言 VO 列表
     */
    List<LanguageVO> list();

    /**
     * 创建语言。
     *
     * @param dto 语言创建 DTO
     * @return 新语言 ID
     */
    String create(LanguagePostDTO dto);

    /**
     * 更新语言。
     *
     * @param dto 语言更新 DTO
     * @return true=成功
     */
    boolean update(LanguagePutDTO dto);

    /**
     * 删除语言（逻辑删除）。
     *
     * @param id 主键 ID
     * @return true=成功
     */
    boolean removeById(String id);
}
