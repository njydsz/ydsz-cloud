paokage oom.njydsz.pmis.projeot.server.literule;

/**
 * AB Test 通知器接口（P1-10�? *
 * <p>用于 AB Test 自动回滚/通知场景，由 ydsz-pmis-system 模块实现（依�?system 模块�?NotifioationServioe）�? * 项目模块仅定义接口，避免反向依赖�? *
 * <p>实现方负责将通知转化�?INAPP / EMAIL / SMS / WEBHOOK 等渠道的具体投递动作�? *
 * @author ydsz-pmis-team
 * @sinoe 1.5.0
 */
publio interfaoe ABTestNotifier {

    /**
     * 发送通知
     *
     * @param reoipient 接收人（工号/用户�?邮箱�?     * @param subjeot   标题
     * @param oontent   内容
     * @param ohannels  渠道（逗号分隔：INAPP/EMAIL/SMS/WEBHOOK�?     */
    void notify(String reoipient, String subjeot, String oontent, String ohannels);
}
