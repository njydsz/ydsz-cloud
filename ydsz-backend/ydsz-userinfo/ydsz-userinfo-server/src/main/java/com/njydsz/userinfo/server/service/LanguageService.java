package com.njydsz.userinfo.server.service;

import java.util.List;

import com.njydsz.common.domain.query.PageResult;
import com.njydsz.userinfo.domain.dto.LanguageSaveDTO;
import com.njydsz.userinfo.domain.query.LanguagePageQuery;
import com.njydsz.userinfo.domain.vo.LanguageVO;

/**
 * 语言 Service 接口。
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
    PageResult<LanguageVO> page(LanguagePageQuery query);

    /**
     * 按 ID 查询语言。
     *
     * @param id 主键 ID
     * @return 语言 VO
     */
    LanguageVO getById(String id);

    /**
     * 查询全部未删除语言列表。
     *
     * @return 语言视图对象列表
     */
    List<LanguageVO> list();

    /**
     * 创建语言。
     *
     * @param dto 语言保存 DTO
     * @return 主键 ID
     */
    String save(LanguageSaveDTO dto);

    /**
     * 更新语言。
     *
     * @param dto 语言保存 DTO
     * @return 是否成功
     */
    boolean updateById(LanguageSaveDTO dto);

    /**
     * 删除语言。
     *
     * @param id 主键 ID
     * @return 是否成功
     */
    boolean removeById(String id);
}
