package top.aameow.system.domain;

import lombok.Data;
import lombok.EqualsAndHashCode;
import top.aameow.common.core.domain.BaseEntity;

/**
 * 文件信息表 sys_file
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SysFilePlus extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /** 文件ID */
    private Long fileId;

    /** 原始文件名（含扩展名） */
    private String fileName;

    /** 服务器相对路径（如 a1/b2/a1b2...pdf） */
    private String filePath;

    /** 访问URL */
    private String fileUrl;

    /** SHA-256 哈希 */
    private String fileHash;

    /** 文件大小（字节） */
    private Long fileSize;

    /** MIME 类型 */
    private String fileType;

    /** 扩展名（如 pdf、jpg） */
    private String fileExt;

    /** 业务类型（如 contract、engineer） */
    private String bizType;

    /** 业务主键ID */
    private Long bizId;

    /** 删除标志（0存在 2删除） */
    private String delFlag;
}