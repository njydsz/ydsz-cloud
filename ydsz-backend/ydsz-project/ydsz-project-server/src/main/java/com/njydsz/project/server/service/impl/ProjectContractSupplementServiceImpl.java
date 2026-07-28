package com.njydsz.project.server.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.project.domain.entity.project.ProjectContractSupplement;
import com.njydsz.project.domain.repository.project.IProjectContractSupplementRepository;
import com.njydsz.project.server.service.ProjectContractSupplementService;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 项目合同附件 / 补充协议 Service 实现
 *
 * <p>对 {@link ProjectContractSupplementService} 接口的完整实现，是「项目管理 / 合同附件管理」业务域的核心业务逻辑层。
 * 维护 {@code ydsz_project_contract_supplement} 合同附件 / 补充协议表，
 * 对标大厂 PMIS / 法务系统中的「合同附件 / 补充协议 / 备忘录」管理能力。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #page} / {@link #save} / {@link #updateById} /
 *       {@link #removeById}</li>
 *   <li><b>合同附件管理</b>：管理合同正本扫描件、合同附件、补充协议、备忘录、变更单等
 *       所有与原合同相关的法律文件</li>
 *   <li><b>文件存储集成</b>：附件文件统一上传到 {@code ydsz-common-file} 文件存储服务，
 *       本表只存储文件元数据（{@code fileId / fileName / fileSize / fileUrl}）</li>
 *   <li><b>版本追踪</b>：同一类附件可上传多个版本（如「合同正本 V1.0」「合同正本 V1.1」），
 *       通过 {@code version} 字段管理</li>
 * </ul>
 *
 * <p><b>事务边界：</b>
 * <ul>
 *   <li>所有写方法 {@code @Transactional(rollbackFor = Exception.class)}</li>
 *   <li>读方法不开启事务，依赖 MyBatis 自动提交</li>
 * </ul>
 *
 * <p><b>多租户：</b>所有方法自动按当前 {@code TenantContext} 隔离，
 * 租户过滤由 MyBatis 拦截器注入。
 *
 * <p><b>设计要点：</b>
 * <ul>
 *   <li><b>附件分类</b>：{@code supplementType} 区分附件类型
 *       （{@code MAIN_BODY} 主合同正本 / {@code SUPPLEMENT} 补充协议 /
 *       {@code MEMO} 备忘录 / {@code CHANGE_ORDER} 变更单 / {@code OTHER} 其他）</li>
 *   <li><b>敏感文件</b>：合同文件包含甲乙双方商业敏感信息，文件存储启用加密 + 签名 URL，
 *       通过 {@code ydsz-common-file} 的 {@code SecureStorage} 接口访问</li>
 *   <li><b>软删除</b>：采用<b>逻辑删除</b>（{@code deleted} 字段），
 *       合同文件是合规审计的法定依据，<b>严禁</b>物理删除</li>
 * </ul>
 *
 * <p><b>典型使用：</b>
 * <pre>{@code
 * // 1. 上传合同正本（前置：调用 ydsz-common-file 上传文件获取 fileId）
 * ProjectContractSupplement supplement = new ProjectContractSupplement();
 * supplement.setContractId("contract_123");
 * supplement.setSupplementType("MAIN_BODY");
 * supplement.setFileId("file_abc123");
 * supplement.setFileName("合同正本-V1.0.pdf");
 * supplement.setFileSize(2048000L);
 * supplement.setFileUrl("https://oss.example.com/contract/file_abc123.pdf");
 * supplement.setVersion("1.0");
 * projectContractSupplementService.save(supplement);
 *
 * // 2. 列出某合同的所有附件
 * // 走 wrapper.eq(ProjectContractSupplement::getContractId, "contract_123")
 * //      .orderByDesc(ProjectContractSupplement::getCreatedAt)
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see ProjectContractSupplementService 合同附件 Service 接口
 * @see com.njydsz.project.domain.entity.project.ProjectContractSupplement 合同附件实体
 * @see com.njydsz.project.server.service.impl.ProjectContractServiceImpl 合同主表 Service
 * @see com.njydsz.common.file.storage.SecureStorage 文件加密存储（附件实际文件）
 */
@Service
@RequiredArgsConstructor
public class ProjectContractSupplementServiceImpl implements ProjectContractSupplementService {

    /** 合同附件仓储（聚合 Mapper + 缓存 + 事件） */
    private final IProjectContractSupplementRepository repository;

    /**
     * 根据主键查询合同附件
     *
     * @param id 合同附件主键
     * @return 合同附件实体，不存在返回 null
     */
    @Override
    public ProjectContractSupplement getById(String id) {
        return repository.getById(id);
    }

    /**
     * 分页查询合同附件
     *
     * <p>通用分页接口，调用方需通过 {@code LambdaQueryWrapper} 传入业务过滤条件（如 {@code contractId}、
     * {@code supplementType} 等）。
     *
     * @param pageNum  页码（1-based）
     * @param pageSize 每页条数
     * @return 分页结果（含总条数）
     */
    @Override
    public IPage<ProjectContractSupplement> page(int pageNum, int pageSize) {
        return repository.page(new Page<>(pageNum, pageSize));
    }

    /**
     * 新增合同附件
     *
     * <p>新增前应先调用 {@code ydsz-common-file} 上传文件获取 {@code fileId}，
     * 再将文件元数据保存到本表。
     *
     * @param supplement 合同附件实体（不需携带 ID）
     * @return true=保存成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean save(ProjectContractSupplement supplement) {
        return repository.save(supplement);
    }

    /**
     * 更新合同附件
     *
     * <p>典型场景：补充附件说明、修订版本号等元数据。
     * 文件内容变更应上传新版本（{@code save}）而非直接修改本记录。
     *
     * @param supplement 合同附件实体（需携带 ID）
     * @return true=更新成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(ProjectContractSupplement supplement) {
        return repository.updateById(supplement);
    }

    /**
     * 逻辑删除合同附件
     *
     * <p>采用<b>逻辑删除</b>（{@code deleted=1}），不真正从 DB 删除。
     *
     * <p><b>注意：</b>合同附件是合规审计的法定依据，<b>严禁</b>物理删除。
     * 即使附件作废也应保留，配合 {@code status=INVALID} 标记。
     *
     * @param id 合同附件主键
     * @return true=删除成功
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) {
        return repository.removeById(id);
    }
}
