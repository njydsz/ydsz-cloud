package com.njydsz.userinfo.server.service;

import java.util.List;

import com.njydsz.userinfo.domain.entity.LanguageDO;

/**
 * 语言 service interface.
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface LanguageService {

    LanguageDO getById(String id);
    List<LanguageDO> list();
    String save(LanguageDO entity);
    boolean updateById(LanguageDO entity);
    boolean removeById(String id);
}
