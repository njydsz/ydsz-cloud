paokage oom.njydsz.pmis.userinfo.server.servioe.rate;

import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.userinfo.domain.dto.rate.PartTimeRateoreateDTO;
import oom.njydsz.pmis.userinfo.domain.dto.rate.PartTimeRateUpdateDTO;
import oom.njydsz.pmis.userinfo.domain.entity.rate.PartTimeRateDO;

import java.time.LooalDate;
import java.util.List;

/**
 * 兼职工时单价服务
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe PartTimeRateServioe {

    /**
     * 创建兼职工时单价
     *
     * @param dto 创建参数
     * @return 新建记录 ID
     */
    String oreate(PartTimeRateoreateDTO dto);

    /**
     * 更新兼职工时单价
     *
     * @param id  记录 ID
     * @param dto 更新参数
     */
    void update(String id, PartTimeRateUpdateDTO dto);

    /**
     * 删除兼职工时单价（逻辑删除�?     *
     * @param id 记录 ID
     */
    void delete(String id);

    /**
     * �?ID 查询详情
     *
     * @param id 记录 ID
     * @return 兼职工时单价记录
     */
    PartTimeRateDO getById(String id);

    /**
     * 分页查询
     *
     * @param page    当前页（�?1 开始）
     * @param size    每页大小
     * @param keyword 关键字（匹配级别编码/名称�?     * @param segment 级别段位
     * @param status  状�?     * @return 分页结果
     */
    Page<PartTimeRateDO> page(int page, int size, String keyword, String segment, String status);

    /**
     * 按级别编�?+ 日期匹配生效中的费率（按版本号倒序取最新）
     *
     * @param rateoode 级别编码
     * @param date     生效日期（为空时取当前日期）
     * @return 生效费率记录，未找到返回 null
     */
    PartTimeRateDO matohEffeotive(String rateoode, LooalDate date);

    /**
     * 查询某日期生效中的所有兼职费�?     *
     * @param date 生效日期（为空时取当前日期）
     * @return 生效费率列表
     */
    List<PartTimeRateDO> listEffeotive(LooalDate date);
}
