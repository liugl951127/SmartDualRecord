package com.minimax.dualrecord.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 录像访问审计日志 (v1.2 录像合规)
 *
 * 监管要求: 任何录像访问/下载/导出/截图/录屏 操作都必须留痕
 * 不可篡改: 仅追加, 不允许 update/delete
 *
 * 用户角色:
 *  - CUSTOMER   客户本人
 *  - SELLER     理财经理
 *  - AUDITOR    内部审计
 *  - REGULATOR  监管 (人民银行/银保监)
 *  - ADMIN      运维管理员
 *
 * 操作类型:
 *  - PLAYBACK   回放
 *  - DOWNLOAD   下载
 *  - SCREENSHOT 截图
 *  - EXPORT     导出 (打包给监管/法院)
 *  - PRESERVE   司法保全
 */
@Data
@NoArgsConstructor
@TableName("tb_recording_access_log")
public class RecordingAccessLog {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    @TableField("rec_id")
    private String recId;

    @TableField("business_id")
    private String businessId;

    @TableField("user_id")
    private String userId;

    @TableField("user_role")
    private String userRole;

    @TableField("access_type")
    private String accessType;

    @TableField("duration_sec")
    private Integer durationSec;

    @TableField("ip_address")
    private String ipAddress;

    @TableField("access_token")
    private String accessToken;

    @TableField("accessed_at")
    private LocalDateTime accessedAt;

    @TableField("deleted")
    private Integer deleted;
}
