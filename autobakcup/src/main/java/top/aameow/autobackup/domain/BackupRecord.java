package top.aameow.autobackup.domain;

import lombok.Data;

@Data
public class BackupRecord {
    private String fileName;
    private String fileSHA256;
    private String uploadTime;
}
