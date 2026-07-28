package com.njydsz.project.server.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.project.domain.entity.execution.ExecutionDeliveryStandard;
/**
 * 交付标准 Service
 *
 * <p>管理项目交付标准（{@code ydsz_execution_delivery_standard}）的维护。</p>
 * <p>交付标准定义了项目交付物的质量门槛（如代码规范/测试覆盖率/文档完整性/性能指标），</p>
 * <p>是验收环节的硬性指标，确保项目交付质量。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD：getById / page / save / updateById / removeById</b></li>
 *   <li><b>按项目类型配置：不同类型项目（开发/集成/咨询）有不同标准</b></li>
 *   <li><b>标准版本：标准变更保留历史</b></li>
 * </ul>
 *
 * <p><b>标准维度：</b>代码规范 / 测试覆盖率 / 文档完整性 / 性能指标 / 安全性。
 *
 * <p><b>事务：</b>所有写操作开启 {@code @Transactional(rollbackFor = Exception.class)}。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.project.domain.entity.execution.ExecutionDeliveryStandard 交付标准实体
 * @see ExecutionDeliveryItemService 交付物 Service(交付物对照标准)
 */
public interface ExecutionDeliveryStandardService {
    ExecutionDeliveryStandard getById(String id);
    IPage<ExecutionDeliveryStandard> page(int pageNum, int pageSize);
    boolean save(ExecutionDeliveryStandard entity);
    boolean updateById(ExecutionDeliveryStandard entity);
    boolean removeById(String id);
}
