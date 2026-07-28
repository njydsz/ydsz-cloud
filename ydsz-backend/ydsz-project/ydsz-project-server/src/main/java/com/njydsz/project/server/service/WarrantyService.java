package com.njydsz.project.server.service;

import com.njydsz.project.domain.entity.warranty.Warranty;

import com.baomidou.mybatisplus.core.metadata.IPage;
/**
 * 质保金 Service
 *
 * <p>管理项目质保金（{@code ydsz_warranty}）的扣留、释放、扣款。
 * 质保金是合同结算时按比例扣留的部分，在质保期满后退还或扣款。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #page} / {@link #save} / {@link #updateById} / {@link #removeById}</li>
 *   <li><b>扣留</b>：合同结算时按比例扣留质保金</li>
 *   <li><b>释放</b>：质保期满且无质量问题时释放</li>
 *   <li><b>扣款</b>：质保期内出现质量问题时扣款冲抵维修费</li>
 * </ul>
 *
 * <p><b>事务：</b>所有写操作开启 {@code @Transactional(rollbackFor = Exception.class)}。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.project.domain.entity.warranty.Warranty 质保金实体
 * @see ProjectContractService 合同 Service(扣留时引用合同)
 */
public interface WarrantyService {
    Warranty getById(String id);
    IPage<Warranty> page(int pageNum, int pageSize);
    boolean save(Warranty entity);
    boolean updateById(Warranty entity);
    boolean removeById(String id);
}
