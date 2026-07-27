package com.njydsz.userinfo.server.service;

import java.util.List;

import com.njydsz.common.domain.service.BaseCrudService;
import com.njydsz.userinfo.domain.dto.LanguageSaveDTO;
import com.njydsz.userinfo.domain.entity.LanguageDO;
import com.njydsz.userinfo.domain.query.LanguagePageQuery;
import com.njydsz.userinfo.domain.vo.LanguageVO;

/**
 * 语言 Service 接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface LanguageService extends BaseCrudService<LanguageDO, LanguageSaveDTO, LanguageVO, LanguagePageQuery, String> {

    /**
     * 查询全部未删除语言列表。
     *
     * @return 语言视图对象列表
     */
    List<LanguageVO> list();
}