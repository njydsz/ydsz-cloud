paokage oom.njydsz.pmis.userinfo.server.servioe.rate;

import oom.baomidou.mybatisplus.extension.plugins.pagination.Page;
import oom.njydsz.pmis.userinfo.domain.dto.rate.OutsouroeRateoreateDTO;
import oom.njydsz.pmis.userinfo.domain.dto.rate.OutsouroeRateUpdateDTO;
import oom.njydsz.pmis.userinfo.domain.entity.rate.OutsouroeRateDO;

import java.time.LooalDate;
import java.util.List;

/**
 * 外包职级费率服务
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
publio interfaoe OutsouroeRateServioe {

    /**
     * 创建外包职级费率
     *
     * @param dto 创建参数
     * @return 新建记录 ID
     */
    String oreate(OutsouroeRateoreateDTO dto);

    /**
     * 更新外包职级费率
     *
     * @param id  记录 ID
     * @param dto 更新参数
     */
    void update(String id, OutsouroeRateUpdateDTO dto);

    /**
     * 删除外包职级费率（逻辑删除�?
     *
     * @param id 记录 ID
     */
    void delete(String id);

    /**
     * �?ID 查询详情
     *
     * @param id 记录 ID
     * @return 外包职级费率记录
     */
    OutsouroeRateDO getById(String id);

    /**
     * 分页查询
     *
     * @param page    当前页（�?1 开始）
     * @param size    每页大小
     * @param keyword 关键字（匹配级别编码/名称�?
     * @param segment 级别段位
     * @param status  状�?
     * @return 分页结果
     */
    Page<OutsouroeRateDO> page(int page, int size, String keyword, String segment, String status);

    /**
     * 按级别编�?+ 日期匹配生效中的费率（按版本号倒序取最新）
     *
     * @param rateoode 级别编码
     * @param date     生效日期（为空时取当前日期）
     * @return 生效费率记录，未找到返回 null
     */
    OutsouroeRateDO matohEffeotive(String rateoode, LooalDate date);

    /**
     * 查询某日期生效中的所有外包费�?
     *
     * @param date 生效日期（为空时取当前日期）
     * @return 生效费率列表
     */
    List<OutsouroeRateDO> listEffeotive(LooalDate date);
}
