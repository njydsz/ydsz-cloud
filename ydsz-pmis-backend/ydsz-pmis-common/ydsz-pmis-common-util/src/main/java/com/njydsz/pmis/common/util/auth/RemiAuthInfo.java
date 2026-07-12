package com.njydsz.pmis.common.util.auth;

import com.njydsz.pmis.common.core.enums.ServiceType;
import com.njydsz.pmis.common.core.enums.DataScopeType;
import com.njydsz.pmis.common.core.enums.IdentityType;
import lombok.Data;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * 鐟炵背绯荤粺缁熶竴璁よ瘉涓婁笅鏂囦俊鎭娊璞″熀绫汇€?
 *
 * <p>鎵胯浇璇锋眰缁村害鐨勫叏閲忚韩浠戒笌鏉冮檺鏁版嵁锛屽湪 {@link com.njydsz.pmis.common.web.filter.WebAuthFilter}
 * / {@link com.njydsz.pmis.common.app.filter.AppAuthFilter} 瑙ｆ瀽璇锋眰澶村悗鍐欏叆 {@link RequestHolder}锛?
 * 渚涗笅娓搁摼璺紙SQL 鎷︽埅鍣ㄣ€丗eign 閫忎紶銆佹暟鎹潈闄愬垏闈㈢瓑锛夐殢鏃惰幏鍙栥€?
 *
 * <p>璁捐璇存槑锛?
 * <ul>
 *   <li>韬唤绫诲瀷鍥哄畾涓?{@link IdentityType#COMPANY}锛堝叕鍙哥骇锛夛紝涓嶆敮鎸佺户鎵挎墿灞?/li>
 *   <li>鏈嶅姟绫诲瀷鐢卞瓙绫婚€氳繃 {@link #getServiceTypeCode()} 瀹炵幇鍖哄垎锛圵EB_SERVICE / APP_SERVICE锛?/li>
 *   <li>鎵€鏈夐泦鍚堢被鍨嬪瓧娈典娇鐢ㄤ笉鍙彉绌洪泦鍚堝垵濮嬪寲锛岄槻姝?NPE</li>
 *   <li>琛岀骇鏉冮檺缁村害锛坈ompanyIds / deptIds / projectIds / regionIds锛夋敮鎸佸鍊?CSV 鏍煎紡</li>
 *   <li>鍒楁潈闄愶紙visibleColumnsByTable / editableColumnsByTable锛夋牸寮忎负 {@code tableName:col1,col2;tableName2:col3}</li>
 * </ul>
 *
 * <p>涓庤姹傚ご鐨勫搴斿叧绯伙細
 * <table border="1">
 *   <tr><th>瀛楁</th><th>瀵瑰簲璇锋眰澶?/th></tr>
 *   <tr><td>userLanguage</td><td>X-User-Language</td></tr>
 *   <tr><td>uniqueId</td><td>X-Unique-Id</td></tr>
 *   <tr><td>accessToken</td><td>X-Access-Token</td></tr>
 *   <tr><td>dataScope</td><td>X-Data-Scope</td></tr>
 *   <tr><td>hasPermissionCompanyIds</td><td>X-Company-Ids</td></tr>
 *   <tr><td>hasPermissionDeptIds</td><td>X-Dept-Ids</td></tr>
 *   <tr><td>hasPermissionProjectIds</td><td>X-Project-Ids</td></tr>
 *   <tr><td>hasPermissionRegionIds</td><td>X-Region-Ids</td></tr>
 *   <tr><td>tenantId</td><td>X-Tenant-Id</td></tr>
 *   <tr><td>distinctId</td><td>X-Distinct-Id</td></tr>
 *   <tr><td>requestSource</td><td>X-Request-Source</td></tr>
 *   <tr><td>visibleColumnsByTable</td><td>X-Visible-Columns</td></tr>
 *   <tr><td>editableColumnsByTable</td><td>X-Editable-Columns</td></tr>
 * </table>
 *
 * @see com.njydsz.pmis.common.web.auth.WebAuthInfo
 * @see com.njydsz.pmis.common.app.auth.AppAuthInfo
 * @see RequestHolder
 * @see AuthInfoUtils
  *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
@Data
public abstract class RemiAuthInfo implements AuthInfo {

    /**
     * 鐢ㄦ埛绯荤粺璇█銆?
     *
     * <p>鏍煎紡绀轰緥锛歿@code zh-CN}銆亄@code en-US}銆?
     * 鐢ㄤ簬鍓嶇鍥介檯鍖栧睍绀轰笌鍚庣杩斿洖鏁版嵁鏍煎紡閫傞厤銆?
      *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
    private String userLanguage;

    /**
     * 鐢ㄦ埛鍞竴鏍囪瘑銆?
     *
     * <p>瀵瑰簲骞冲彴鐢ㄦ埛浣撶郴涓殑涓婚敭 ID锛岄潪 Token銆?
      *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
    private String uniqueId;

    /**
     * 鐢ㄦ埛閴存潈 Token銆?
     *
     * <p>姣忔鐧诲綍鍚庣敱璁よ瘉鏈嶅姟绛惧彂锛岀敤浜庝笅娓告湇鍔″疄鏃堕獙璇併€?
      *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
    private String accessToken;

    /**
     * 鏁版嵁鏉冮檺鑼冨洿绫诲瀷銆?
     *
     * <p>鐢ㄤ簬鏍囪瘑褰撳墠璇锋眰鐨勬暟鎹潈闄愮矑搴︼紙濡傦細鍏ㄩ儴銆佹湰浜恒€佹湰閮ㄩ棬绛夛級銆?
     *
     * @see DataScopeType
      *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
    private DataScopeType dataScope;

    /**
     * 鏈夋潈闄愯闂殑鍏徃 ID 闆嗗悎銆?
     *
     * <p>澶氬€兼椂浠?CSV 鏍煎紡瀛樺偍锛坽@code id1,id2,id3}锛夈€?
     * 鐢ㄤ簬 SQL 鎷︽埅鍣ㄨ嚜鍔ㄦ敼鍐?WHERE 鏉′欢銆?
      *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
    private Set<String> hasPermissionCompanyIds;

    /**
     * 鏈夋潈闄愯闂殑閮ㄩ棬 ID 闆嗗悎銆?
     *
     * <p>澶氬€兼椂浠?CSV 鏍煎紡瀛樺偍銆?
     * 涓?companyIds 鍏卞悓鏋勬垚缁勭粐缁村害鏉冮檺杩囨护鏉′欢銆?
      *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
    private Set<String> hasPermissionDeptIds;

    /**
     * 鏈夋潈闄愯闂殑椤圭洰 ID 闆嗗悎銆?
     *
     * <p>澶氬€兼椂浠?CSV 鏍煎紡瀛樺偍銆?
     * 椤圭洰绾ф暟鎹殧绂诲満鏅娇鐢ㄣ€?
      *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
    private Set<String> hasPermissionProjectIds;

    /**
     * 鏈夋潈闄愯闂殑鍖哄煙 ID 闆嗗悎銆?
     *
     * <p>澶氬€兼椂浠?CSV 鏍煎紡瀛樺偍銆?
     * 鍖哄煙绾ф暟鎹殧绂诲満鏅娇鐢ㄣ€?
      *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
    private Set<String> hasPermissionRegionIds;

    /**
     * 绉熸埛鍞竴鏍囪瘑銆?
     *
     * <p>鐢ㄤ簬澶氱鎴峰満鏅笅鐨勬暟鎹殧绂汇€?
      *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
    private String tenantId;

    /**
     * 璁惧鍞竴鏍囪瘑銆?
     *
     * <p>鐢ㄤ簬璁惧杩借釜銆佸煁鐐瑰垎鏋愮瓑鍦烘櫙銆?
      *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
    private String distinctId;

    /**
     * 璇锋眰鏉ユ簮鏍囪瘑銆?
     *
     * <p>璁板綍鍙戣捣璇锋眰鐨勬潵婧愮郴缁熸垨妯″潡銆?
      *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
    private String requestSource;

    /**
     * 琛ㄧ骇鍒楀彲瑙佽鍒欍€?
     *
     * <p>鏍煎紡锛歿@code tableName:col1,col2;tableName2:col3}
     * <ul>
     *   <li>key锛氳〃鍚嶏紙涓嶅尯鍒嗗ぇ灏忓啓锛岀粺涓€杞皬鍐欏瓨鍌級</li>
     *   <li>value锛氬厑璁告煡鐪嬬殑鍒楀悕闆嗗悎锛堜笉鍖哄垎澶у皬鍐欙級</li>
     * </ul>
     *
     * @see <a href="https://confluence.njydsz.pmis.com.cn/pages/viewpage.action?pageId=123456">鍒楁潈闄愯璁℃枃妗?/a>
      *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
    private Map<String, Set<String>> visibleColumnsByTable = Collections.emptyMap();

    /**
     * 琛ㄧ骇鍒楀彲缂栬緫瑙勫垯銆?
     *
     * <p>鏍煎紡鍚?{@link #visibleColumnsByTable}銆?
     * 浠呮帶鍒跺垪鏄惁鍙紪杈戯紝涓庡彲瑙佹€х嫭绔嬨€?
      *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
    private Map<String, Set<String>> editableColumnsByTable = Collections.emptyMap();

    /**
     * 杩斿洖韬唤绫诲瀷涓哄叕鍙哥敤鎴枫€?
     *
     * @return {@link IdentityType#COMPANY}
      *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
    @Override
    public IdentityType getIdentityTypeEnum() {
        return IdentityType.COMPANY;
    }

    /**
     * 杩斿洖鏈嶅姟绫诲瀷鐮侊紝鐢卞瓙绫诲疄鐜般€?
     *
     * <p>鐢ㄤ簬鍖哄垎璇锋眰鏉ユ簮缁堢锛?
     * <ul>
     *   <li>{@link ServiceType#WEB_SERVICE} 鈫?PC Web</li>
     *   <li>{@link ServiceType#APP_SERVICE} 鈫?绉诲姩绔?H5/App</li>
     * </ul>
     *
     * @return 鏈嶅姟绫诲瀷鐮侊紝闈炵┖瀛楃涓?
      *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
    @Override
    public abstract String getServiceTypeCode();

    /**
     * 鑾峰彇琛ㄧ骇鍒楀彲瑙佽鍒欍€?
     *
     * @return 琛ㄥ悕鈫掑垪闆嗗悎鐨勬槧灏勶紝鑻ユ棤瑙勫垯杩斿洖绌?Map
      *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
    @Override
    public Map<String, Set<String>> getVisibleColumnsByTable() {
        return visibleColumnsByTable;
    }

    /**
     * 鑾峰彇琛ㄧ骇鍒楀彲缂栬緫瑙勫垯銆?
     *
     * @return 琛ㄥ悕鈫掑垪闆嗗悎鐨勬槧灏勶紝鑻ユ棤瑙勫垯杩斿洖绌?Map
      *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
    @Override
    public Map<String, Set<String>> getEditableColumnsByTable() {
        return editableColumnsByTable;
    }
}
