package com.minimax.dualrecord.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 坐席推送文件记录 (v1.5)
 *
 * 场景: 线上双录中, 坐席实时向客户推送
 *  - 产品说明书 (PDF)
 *  - 风险揭示书 (PDF)
 *  - 合同文本 (PDF)
 *  - 宣传图片 (PNG/JPG)
 *  - 短视频 (MP4)
 *
 * 客户行为轨迹:
 *  - PUSHED → 客户收到推送通知
 *  - VIEWED → 客户已查看
 *  - SIGNED → 客户已电子签署
 *  - REJECTED → 客户已拒签
 */
@Data
@TableName("tb_pushed_file")
public class PushedFile {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    @TableField("business_id")
    private String businessId;

    /** 文件 ID (系统内唯一, 用于客户端请求文件内容) */
    @TableField("file_id")
    private String fileId;

    /** 文件名 (含扩展名) */
    @TableField("file_name")
    private String fileName;

    /** 文件类型 (PDF / PNG / JPG / MP4 / TXT) */
    @TableField("file_type")
    private String fileType;

    /** 文件 URL (相对路径或 CDN) */
    @TableField("file_url")
    private String fileUrl;

    /** 文件大小 (字节) */
    @TableField("file_size")
    private Long fileSize;

    /** 文件分类 (BROCHURE / DISCLOSURE / CONTRACT / ID_CARD / OTHER) */
    @TableField("file_category")
    private String fileCategory;

    /** 推送坐席 ID */
    @TableField("pushed_by")
    private String pushedBy;

    @TableField("pushed_at")
    private LocalDateTime pushedAt;

    /** 客户查看时间 */
    @TableField("viewed_at")
    private LocalDateTime viewedAt;

    /** 客户签署时间 */
    @TableField("signed_at")
    private LocalDateTime signedAt;

    /** 客户拒签时间 */
    @TableField("rejected_at")
    private LocalDateTime rejectedAt;

    /** 状态: PUSHED / VIEWED / SIGNED / REJECTED */
    @TableField("status")
    private String status;

    /** 签字 base64 (PNG) */
    @TableField("signature_data")
    private String signatureData;

    /** 备注 (坐席填的推送原因) */
    @TableField("remark")
    private String remark;

    @TableField("deleted")
    private Integer deleted;
}
