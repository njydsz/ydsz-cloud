package com.njydsz.system.server.service;

import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.system.domain.dto.AppInfoDTO;
import com.njydsz.system.domain.vo.AppInfoVO;

/**
 * 应用注册 Service。
 *
 * <p>提供应用 CRUD、密钥校验、分页查询等能力，集成 BCrypt 密钥加密和 Micrometer 指标。
 *
 * @author ydsz-team
 */
public interface AppInfoService {

    /**
     * 按 ID 查询应用。
     *
     * @param id 主键 ID
     * @return 应用 VO（不含 appSecret）
     */
    AppInfoVO getById(String id);

    /**
     * 校验应用密钥（BCrypt）。
     *
     * @param appKey    应用 Key
     * @param appSecret 应用密钥明文
     * @return 校验通过返回 true
     */
    boolean validateClient(String appKey, String appSecret);

    /**
     * 分页查询应用列表（支持搜索过滤）。
     *
     * @param pageNum  当前页码
     * @param pageSize 每页记录数
     * @param appName  应用名称模糊搜索（可选）
     * @param status   状态过滤（可选）
     * @return 分页结果（VO）
     */
    IPage<AppInfoVO> page(int pageNum, int pageSize, String appName, String status);

    /**
     * 查询全部应用（仅内部使用）。
     *
     * @return 应用列表（VO）
     */
    List<AppInfoVO> list();

    /**
     * 创建应用（密钥自动 BCrypt 加密）。
     *
     * @param dto 应用 DTO
     * @return 主键 ID
     */
    String save(AppInfoDTO dto);

    /**
     * 更新应用（密钥非空时 BCrypt 加密，为空时保留原密钥）。
     *
     * @param dto 应用 DTO
     * @return 是否成功
     */
    boolean updateById(AppInfoDTO dto);

    /**
     * 删除应用（逻辑删除）。
     *
     * @param id 主键 ID
     * @return 是否成功
     */
    boolean removeById(String id);
}
