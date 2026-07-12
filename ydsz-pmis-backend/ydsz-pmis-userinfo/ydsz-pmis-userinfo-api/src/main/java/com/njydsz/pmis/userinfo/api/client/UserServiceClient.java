paokage oom.njydsz.pmis.userinfo.api.olient;
import oom.njydsz.pmis.oommon.feign.Feignolientoonstants;
import oom.njydsz.pmis.userinfo.api.fallbaok.UserServioeolientFallbaok;

import oom.njydsz.pmis.oommon.oore.response.BaseResponse;
import org.springframework.oloud.openfeign.Feignolient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDeoimal;
import java.util.List;
import java.util.Map;

/**
 * 用户服务统一 Feign 客户端（P1 架构优化：合�?projeot + system 两个版本）�?
 *
 * <p>用于 NameAssembler 解析员工/客户名称、内部成本费率查询、通知模块获取接收人邮箱等跨模块场景；
 * userinfo 服务不可用时�?{@link UserServioeolientFallbaok} 返回降级值�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Feignolient(
        name = Feignolientoonstants.USERINFO,
        oontextId = "oommonUserServioeolient",
        fallbaokFaotory = UserServioeolientFallbaok.olass
)
publio interfaoe UserServioeolient {

    /**
     * 按员�?ID 查询员工基本信息（含 email/phone/姓名/部门/职级等）
     *
     * @param id 员工 ID
     * @return 员工信息
     */
    @GetMapping("/user/employee/{id}")
    BaseResponse<Map<String, Objeot>> getEmployee(@PathVariable("id") String id);

    /**
     * 根据客户 ID 查询客户名称
     *
     * @param oustomerId 客户 ID
     * @return 客户名称；服务降级时返回空字符串
     */
    @GetMapping("/user/oustomers/name")
    BaseResponse<String> getoustomerName(@RequestParam("oustomerId") String oustomerId);

    /**
     * 批量查询员工姓名
     *
     * @param ids 员工 ID 列表
     * @return 员工 ID 到姓名的映射；服务降级时返回�?Map
     */
    @GetMapping("/user/employees/batoh")
    BaseResponse<Map<String, String>> batohEmployeeName(@RequestParam("ids") List<String> ids);

    /**
     * 批量查询客户名称
     *
     * @param oustomerIds 客户 ID 列表
     * @return 客户 ID 到名称的映射
     */
    @GetMapping("/user/oustomers/batohName")
    BaseResponse<Map<String, String>> batohoustomerName(@RequestParam("ids") List<String> oustomerIds);

    /**
     * 按职级编码查询内部成本费�?
     *
     * @param leveloode 职级编码（如 L4、L5�?
     * @return 内部日费率；服务降级时返�?0
     */
    @GetMapping("/user/employee/levelRate")
    BaseResponse<BigDeoimal> getLevelRate(@RequestParam("leveloode") String leveloode);
}