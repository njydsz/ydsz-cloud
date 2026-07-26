package com.njydsz.userinfo.server.service;

import java.util.List;

import com.njydsz.userinfo.domain.dto.LanguageSaveDTO;
import com.njydsz.userinfo.domain.vo.LanguageVO;

/**
 * 语言 Service 接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface LanguageService {
    LanguageVO getById(String id);
    List<LanguageVO> list();
    String create(LanguageSaveDTO dto);
    boolean update(LanguageSaveDTO dto);
    boolean removeById(String id);
}
