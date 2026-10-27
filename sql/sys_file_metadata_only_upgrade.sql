-- 已有 sys_file 表的兼容升级；先备份再执行。
-- 只扩大文本字段容量，允许缺少原文件的历史记录使用 NULL 表示未知元数据。
ALTER TABLE sys_file
  MODIFY file_name varchar(512) NOT NULL,
  MODIFY file_path varchar(512) NOT NULL,
  MODIFY file_url varchar(600) NOT NULL,
  MODIFY file_hash varchar(64) DEFAULT NULL COMMENT 'SHA-256，NULL表示尚未核实',
  MODIFY file_size bigint DEFAULT NULL COMMENT '字节数，NULL表示尚未核实',
  MODIFY file_type varchar(255) DEFAULT NULL,
  MODIFY biz_type varchar(64) DEFAULT NULL;
