/*
 Navicat Premium Dump SQL

 Source Server         : 192.168.1.115
 Source Server Type    : MySQL
 Source Server Version : 80042 (8.0.42-0ubuntu0.24.04.1)
 Source Host           : 192.168.1.115:3306
 Source Schema         : server

 Target Server Type    : MySQL
 Target Server Version : 80042 (8.0.42-0ubuntu0.24.04.1)
 File Encoding         : 65001

 Date: 29/05/2025 14:08:53
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for gen_table
-- ----------------------------
DROP TABLE IF EXISTS `gen_table`;
CREATE TABLE `gen_table`  (
  `table_id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `table_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '表名称',
  `table_comment` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '表描述',
  `sub_table_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '关联子表的表名',
  `sub_table_fk_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '子表关联的外键名',
  `class_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '实体类名称',
  `tpl_category` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'crud' COMMENT '使用的模板（crud单表操作 tree树表操作）',
  `tpl_web_type` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '前端模板类型（element-ui模版 element-plus模版）',
  `package_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '生成包路径',
  `module_name` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '生成模块名',
  `business_name` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '生成业务名',
  `function_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '生成功能名',
  `function_author` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '生成功能作者',
  `gen_type` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '生成代码方式（0zip压缩包 1自定义路径）',
  `gen_path` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '/' COMMENT '生成路径（不填默认项目路径）',
  `options` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '其它生成选项',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`table_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '代码生成业务表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of gen_table
-- ----------------------------
INSERT INTO `gen_table` VALUES (2, 'zj_contract', '合同表', NULL, NULL, 'ZjContract', 'crud', 'element-plus', 'top.aameow.management', 'management', 'contract', '合同管理', 'ruoyi', '0', '/', '{\"parentMenuId\":1}', 'admin', '2025-05-09 15:07:37', '', '2025-05-13 11:15:51', NULL);
INSERT INTO `gen_table` VALUES (3, 'zj_report', '报告表', NULL, NULL, 'ZjReport', 'crud', 'element-plus', 'top.aameow.management', 'management', 'report', '报告管理', 'aa', '0', '/', '{\"parentMenuId\":2006}', 'admin', '2025-05-13 16:21:41', '', '2025-05-16 01:33:43', NULL);

-- ----------------------------
-- Table structure for gen_table_column
-- ----------------------------
DROP TABLE IF EXISTS `gen_table_column`;
CREATE TABLE `gen_table_column`  (
  `column_id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `table_id` bigint NULL DEFAULT NULL COMMENT '归属表编号',
  `column_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '列名称',
  `column_comment` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '列描述',
  `column_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '列类型',
  `java_type` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'JAVA类型',
  `java_field` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'JAVA字段名',
  `is_pk` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '是否主键（1是）',
  `is_increment` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '是否自增（1是）',
  `is_required` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '是否必填（1是）',
  `is_insert` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '是否为插入字段（1是）',
  `is_edit` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '是否编辑字段（1是）',
  `is_list` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '是否列表字段（1是）',
  `is_query` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '是否查询字段（1是）',
  `query_type` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'EQ' COMMENT '查询方式（等于、不等于、大于、小于、范围）',
  `html_type` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '显示类型（文本框、文本域、下拉框、复选框、单选框、日期控件）',
  `dict_type` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '字典类型',
  `sort` int NULL DEFAULT NULL COMMENT '排序',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`column_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 35 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '代码生成业务表字段' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of gen_table_column
-- ----------------------------
INSERT INTO `gen_table_column` VALUES (9, 2, 'id', '', 'int(11)', 'Long', 'id', '1', '1', '0', '0', NULL, NULL, NULL, 'EQ', 'input', '', 1, 'admin', '2025-05-09 15:07:37', '', '2025-05-13 11:15:51');
INSERT INTO `gen_table_column` VALUES (10, 2, 'contract_id', '合同编号', 'varchar(255)', 'String', 'contractId', '0', '0', '1', '0', '1', '1', '1', 'EQ', 'input', '', 2, 'admin', '2025-05-09 15:07:37', '', '2025-05-13 11:15:51');
INSERT INTO `gen_table_column` VALUES (11, 2, 'contract_type', '合同类型', 'int(2)', 'Integer', 'contractType', '0', '0', '1', '1', '1', '1', '1', 'EQ', 'select', 'contract_type', 3, 'admin', '2025-05-09 15:07:37', '', '2025-05-13 11:15:51');
INSERT INTO `gen_table_column` VALUES (12, 2, 'contract_name', '合同名称', 'varchar(255)', 'String', 'contractName', '0', '0', '1', '1', '1', '1', '1', 'LIKE', 'input', '', 4, 'admin', '2025-05-09 15:07:37', '', '2025-05-13 11:15:51');
INSERT INTO `gen_table_column` VALUES (13, 2, 'start_time', '开始时间', 'datetime', 'Date', 'startTime', '0', '0', '1', '1', '1', '1', '0', 'EQ', 'datetime', '', 5, 'admin', '2025-05-09 15:07:37', '', '2025-05-13 11:15:51');
INSERT INTO `gen_table_column` VALUES (14, 2, 'end_time', '结束时间', 'datetime', 'Date', 'endTime', '0', '0', '1', '1', '1', '1', '0', 'EQ', 'datetime', '', 6, 'admin', '2025-05-09 15:07:37', '', '2025-05-13 11:15:51');
INSERT INTO `gen_table_column` VALUES (15, 2, 'manager', '合同负责人', 'varchar(255)', 'String', 'manager', '0', '0', '1', '1', '1', '1', '1', 'EQ', 'input', '', 7, 'admin', '2025-05-09 15:07:37', '', '2025-05-13 11:15:51');
INSERT INTO `gen_table_column` VALUES (16, 2, 'amount', '合同金额', 'int(11)', 'Long', 'amount', '0', '0', '1', '1', '1', '1', '0', 'EQ', 'input', '', 8, 'admin', '2025-05-09 15:07:37', '', '2025-05-13 11:15:51');
INSERT INTO `gen_table_column` VALUES (17, 2, 'create_by', '创建人', 'varchar(255)', 'String', 'createBy', '0', '0', '0', '0', NULL, '0', NULL, 'EQ', 'input', '', 9, '', '2025-05-09 15:08:54', '', '2025-05-13 11:15:51');
INSERT INTO `gen_table_column` VALUES (18, 2, 'create_time', '创建时间', 'datetime', 'Date', 'createTime', '0', '0', '0', '0', NULL, '0', NULL, 'EQ', 'datetime', '', 10, '', '2025-05-09 15:08:54', '', '2025-05-13 11:15:51');
INSERT INTO `gen_table_column` VALUES (19, 2, 'update_by', '更新人', 'varchar(255)', 'String', 'updateBy', '0', '0', '0', '0', '0', '0', NULL, 'EQ', 'input', '', 11, '', '2025-05-09 15:08:54', '', '2025-05-13 11:15:51');
INSERT INTO `gen_table_column` VALUES (20, 2, 'update_time', '更新时间', 'datetime', 'Date', 'updateTime', '0', '0', '0', '0', '0', '0', NULL, 'EQ', 'datetime', '', 12, '', '2025-05-09 15:08:54', '', '2025-05-13 11:15:51');
INSERT INTO `gen_table_column` VALUES (21, 2, 'remark', '备注', 'varchar(255)', 'String', 'remark', '0', '0', '0', '0', '0', '0', NULL, 'EQ', 'input', '', 13, '', '2025-05-09 15:08:54', '', '2025-05-13 11:15:51');
INSERT INTO `gen_table_column` VALUES (22, 2, 'contract_file', '合同附件', 'varchar(255)', 'String', 'contractFile', '0', '0', '0', '1', '1', '1', '0', 'EQ', 'fileUpload', '', 14, '', '2025-05-09 17:17:44', '', '2025-05-13 11:15:51');
INSERT INTO `gen_table_column` VALUES (23, 3, 'id', '', 'int(11)', 'Long', 'id', '1', '1', '0', '1', NULL, NULL, NULL, 'EQ', 'input', '', 1, 'admin', '2025-05-13 16:21:41', '', '2025-05-16 01:58:18');
INSERT INTO `gen_table_column` VALUES (24, 3, 'report_id', '报告编号', 'varchar(64)', 'String', 'reportId', '0', '0', '0', '1', '1', '1', '1', 'LIKE', 'input', '', 2, 'admin', '2025-05-13 16:21:41', '', '2025-05-16 01:58:18');
INSERT INTO `gen_table_column` VALUES (25, 3, 'report_name', '名称', 'varchar(255)', 'String', 'reportName', '0', '0', '1', '1', '1', '1', '1', 'LIKE', 'input', '', 3, 'admin', '2025-05-13 16:21:41', '', '2025-05-16 01:58:18');
INSERT INTO `gen_table_column` VALUES (26, 3, 'report_file', '报告文件', 'varchar(255)', 'String', 'reportFile', '0', '0', '1', '1', '1', '1', '1', 'EQ', 'fileUpload', '', 4, 'admin', '2025-05-13 16:21:41', '', '2025-05-16 01:58:18');
INSERT INTO `gen_table_column` VALUES (27, 3, 'report_type', '报告类型', 'varchar(4)', 'String', 'reportType', '0', '0', '0', '1', '1', '1', '1', 'EQ', 'select', 'report_type', 5, 'admin', '2025-05-13 16:21:41', '', '2025-05-16 01:58:18');
INSERT INTO `gen_table_column` VALUES (28, 3, 'report_isfinal', '最终版本', 'varchar(4)', 'String', 'reportIsfinal', '0', '0', '0', '1', '1', '1', '1', 'EQ', 'select', 'report_isfinal', 6, 'admin', '2025-05-13 16:21:41', '', '2025-05-16 01:58:18');
INSERT INTO `gen_table_column` VALUES (29, 3, 'create_by', '创建者', 'varchar(128)', 'String', 'createBy', '0', '0', '0', '1', NULL, '1', '1', 'EQ', 'input', '', 9, 'admin', '2025-05-13 16:21:41', '', '2025-05-16 01:58:18');
INSERT INTO `gen_table_column` VALUES (30, 3, 'create_time', '创建时间', 'datetime', 'Date', 'createTime', '0', '0', '0', '1', NULL, '1', NULL, 'EQ', 'datetime', '', 10, 'admin', '2025-05-13 16:21:41', '', '2025-05-16 01:58:18');
INSERT INTO `gen_table_column` VALUES (31, 3, 'update_by', '更新者', 'varchar(128)', 'String', 'updateBy', '0', '0', '0', '1', '1', '1', '1', 'EQ', 'input', '', 11, 'admin', '2025-05-13 16:21:41', '', '2025-05-16 01:58:18');
INSERT INTO `gen_table_column` VALUES (32, 3, 'update_time', '更新时间', 'datetime', 'Date', 'updateTime', '0', '0', '0', '1', '1', '1', NULL, 'EQ', 'datetime', '', 12, 'admin', '2025-05-13 16:21:41', '', '2025-05-16 01:58:18');
INSERT INTO `gen_table_column` VALUES (33, 3, 'report_manager', '报告归属', 'varchar(64)', 'String', 'reportManager', '0', '0', '0', '1', '1', '1', '1', 'EQ', 'input', '', 7, '', '2025-05-16 01:31:46', '', '2025-05-16 01:58:18');
INSERT INTO `gen_table_column` VALUES (34, 3, 'report_area', '项目面积', 'varchar(64)', 'String', 'reportArea', '0', '0', '0', '1', '1', '1', '1', 'EQ', 'input', '', 8, '', '2025-05-16 01:58:18', '', NULL);

-- ----------------------------
-- Table structure for qrtz_blob_triggers
-- ----------------------------
DROP TABLE IF EXISTS `qrtz_blob_triggers`;
CREATE TABLE `qrtz_blob_triggers`  (
  `sched_name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
  `trigger_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_name的外键',
  `trigger_group` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_group的外键',
  `blob_data` blob NULL COMMENT '存放持久化Trigger对象',
  PRIMARY KEY (`sched_name`, `trigger_name`, `trigger_group`) USING BTREE,
  CONSTRAINT `qrtz_blob_triggers_ibfk_1` FOREIGN KEY (`sched_name`, `trigger_name`, `trigger_group`) REFERENCES `qrtz_triggers` (`sched_name`, `trigger_name`, `trigger_group`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'Blob类型的触发器表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of qrtz_blob_triggers
-- ----------------------------

-- ----------------------------
-- Table structure for qrtz_calendars
-- ----------------------------
DROP TABLE IF EXISTS `qrtz_calendars`;
CREATE TABLE `qrtz_calendars`  (
  `sched_name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
  `calendar_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '日历名称',
  `calendar` blob NOT NULL COMMENT '存放持久化calendar对象',
  PRIMARY KEY (`sched_name`, `calendar_name`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '日历信息表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of qrtz_calendars
-- ----------------------------

-- ----------------------------
-- Table structure for qrtz_cron_triggers
-- ----------------------------
DROP TABLE IF EXISTS `qrtz_cron_triggers`;
CREATE TABLE `qrtz_cron_triggers`  (
  `sched_name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
  `trigger_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_name的外键',
  `trigger_group` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_group的外键',
  `cron_expression` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'cron表达式',
  `time_zone_id` varchar(80) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '时区',
  PRIMARY KEY (`sched_name`, `trigger_name`, `trigger_group`) USING BTREE,
  CONSTRAINT `qrtz_cron_triggers_ibfk_1` FOREIGN KEY (`sched_name`, `trigger_name`, `trigger_group`) REFERENCES `qrtz_triggers` (`sched_name`, `trigger_name`, `trigger_group`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'Cron类型的触发器表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of qrtz_cron_triggers
-- ----------------------------

-- ----------------------------
-- Table structure for qrtz_fired_triggers
-- ----------------------------
DROP TABLE IF EXISTS `qrtz_fired_triggers`;
CREATE TABLE `qrtz_fired_triggers`  (
  `sched_name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
  `entry_id` varchar(95) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度器实例id',
  `trigger_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_name的外键',
  `trigger_group` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_group的外键',
  `instance_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度器实例名',
  `fired_time` bigint NOT NULL COMMENT '触发的时间',
  `sched_time` bigint NOT NULL COMMENT '定时器制定的时间',
  `priority` int NOT NULL COMMENT '优先级',
  `state` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '状态',
  `job_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '任务名称',
  `job_group` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '任务组名',
  `is_nonconcurrent` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '是否并发',
  `requests_recovery` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '是否接受恢复执行',
  PRIMARY KEY (`sched_name`, `entry_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '已触发的触发器表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of qrtz_fired_triggers
-- ----------------------------

-- ----------------------------
-- Table structure for qrtz_job_details
-- ----------------------------
DROP TABLE IF EXISTS `qrtz_job_details`;
CREATE TABLE `qrtz_job_details`  (
  `sched_name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
  `job_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务名称',
  `job_group` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务组名',
  `description` varchar(250) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '相关介绍',
  `job_class_name` varchar(250) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '执行任务类名称',
  `is_durable` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '是否持久化',
  `is_nonconcurrent` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '是否并发',
  `is_update_data` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '是否更新数据',
  `requests_recovery` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '是否接受恢复执行',
  `job_data` blob NULL COMMENT '存放持久化job对象',
  PRIMARY KEY (`sched_name`, `job_name`, `job_group`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '任务详细信息表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of qrtz_job_details
-- ----------------------------

-- ----------------------------
-- Table structure for qrtz_locks
-- ----------------------------
DROP TABLE IF EXISTS `qrtz_locks`;
CREATE TABLE `qrtz_locks`  (
  `sched_name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
  `lock_name` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '悲观锁名称',
  PRIMARY KEY (`sched_name`, `lock_name`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '存储的悲观锁信息表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of qrtz_locks
-- ----------------------------

-- ----------------------------
-- Table structure for qrtz_paused_trigger_grps
-- ----------------------------
DROP TABLE IF EXISTS `qrtz_paused_trigger_grps`;
CREATE TABLE `qrtz_paused_trigger_grps`  (
  `sched_name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
  `trigger_group` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_group的外键',
  PRIMARY KEY (`sched_name`, `trigger_group`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '暂停的触发器表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of qrtz_paused_trigger_grps
-- ----------------------------

-- ----------------------------
-- Table structure for qrtz_scheduler_state
-- ----------------------------
DROP TABLE IF EXISTS `qrtz_scheduler_state`;
CREATE TABLE `qrtz_scheduler_state`  (
  `sched_name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
  `instance_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '实例名称',
  `last_checkin_time` bigint NOT NULL COMMENT '上次检查时间',
  `checkin_interval` bigint NOT NULL COMMENT '检查间隔时间',
  PRIMARY KEY (`sched_name`, `instance_name`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '调度器状态表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of qrtz_scheduler_state
-- ----------------------------

-- ----------------------------
-- Table structure for qrtz_simple_triggers
-- ----------------------------
DROP TABLE IF EXISTS `qrtz_simple_triggers`;
CREATE TABLE `qrtz_simple_triggers`  (
  `sched_name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
  `trigger_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_name的外键',
  `trigger_group` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_group的外键',
  `repeat_count` bigint NOT NULL COMMENT '重复的次数统计',
  `repeat_interval` bigint NOT NULL COMMENT '重复的间隔时间',
  `times_triggered` bigint NOT NULL COMMENT '已经触发的次数',
  PRIMARY KEY (`sched_name`, `trigger_name`, `trigger_group`) USING BTREE,
  CONSTRAINT `qrtz_simple_triggers_ibfk_1` FOREIGN KEY (`sched_name`, `trigger_name`, `trigger_group`) REFERENCES `qrtz_triggers` (`sched_name`, `trigger_name`, `trigger_group`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '简单触发器的信息表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of qrtz_simple_triggers
-- ----------------------------

-- ----------------------------
-- Table structure for qrtz_simprop_triggers
-- ----------------------------
DROP TABLE IF EXISTS `qrtz_simprop_triggers`;
CREATE TABLE `qrtz_simprop_triggers`  (
  `sched_name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
  `trigger_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_name的外键',
  `trigger_group` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_triggers表trigger_group的外键',
  `str_prop_1` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'String类型的trigger的第一个参数',
  `str_prop_2` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'String类型的trigger的第二个参数',
  `str_prop_3` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'String类型的trigger的第三个参数',
  `int_prop_1` int NULL DEFAULT NULL COMMENT 'int类型的trigger的第一个参数',
  `int_prop_2` int NULL DEFAULT NULL COMMENT 'int类型的trigger的第二个参数',
  `long_prop_1` bigint NULL DEFAULT NULL COMMENT 'long类型的trigger的第一个参数',
  `long_prop_2` bigint NULL DEFAULT NULL COMMENT 'long类型的trigger的第二个参数',
  `dec_prop_1` decimal(13, 4) NULL DEFAULT NULL COMMENT 'decimal类型的trigger的第一个参数',
  `dec_prop_2` decimal(13, 4) NULL DEFAULT NULL COMMENT 'decimal类型的trigger的第二个参数',
  `bool_prop_1` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'Boolean类型的trigger的第一个参数',
  `bool_prop_2` varchar(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT 'Boolean类型的trigger的第二个参数',
  PRIMARY KEY (`sched_name`, `trigger_name`, `trigger_group`) USING BTREE,
  CONSTRAINT `qrtz_simprop_triggers_ibfk_1` FOREIGN KEY (`sched_name`, `trigger_name`, `trigger_group`) REFERENCES `qrtz_triggers` (`sched_name`, `trigger_name`, `trigger_group`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '同步机制的行锁表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of qrtz_simprop_triggers
-- ----------------------------

-- ----------------------------
-- Table structure for qrtz_triggers
-- ----------------------------
DROP TABLE IF EXISTS `qrtz_triggers`;
CREATE TABLE `qrtz_triggers`  (
  `sched_name` varchar(120) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '调度名称',
  `trigger_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '触发器的名字',
  `trigger_group` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '触发器所属组的名字',
  `job_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_job_details表job_name的外键',
  `job_group` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'qrtz_job_details表job_group的外键',
  `description` varchar(250) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '相关介绍',
  `next_fire_time` bigint NULL DEFAULT NULL COMMENT '上一次触发时间（毫秒）',
  `prev_fire_time` bigint NULL DEFAULT NULL COMMENT '下一次触发时间（默认为-1表示不触发）',
  `priority` int NULL DEFAULT NULL COMMENT '优先级',
  `trigger_state` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '触发器状态',
  `trigger_type` varchar(8) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '触发器的类型',
  `start_time` bigint NOT NULL COMMENT '开始时间',
  `end_time` bigint NULL DEFAULT NULL COMMENT '结束时间',
  `calendar_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '日程表名称',
  `misfire_instr` smallint NULL DEFAULT NULL COMMENT '补偿执行的策略',
  `job_data` blob NULL COMMENT '存放持久化job对象',
  PRIMARY KEY (`sched_name`, `trigger_name`, `trigger_group`) USING BTREE,
  INDEX `sched_name`(`sched_name` ASC, `job_name` ASC, `job_group` ASC) USING BTREE,
  CONSTRAINT `qrtz_triggers_ibfk_1` FOREIGN KEY (`sched_name`, `job_name`, `job_group`) REFERENCES `qrtz_job_details` (`sched_name`, `job_name`, `job_group`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '触发器详细信息表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of qrtz_triggers
-- ----------------------------

-- ----------------------------
-- Table structure for sys_config
-- ----------------------------
DROP TABLE IF EXISTS `sys_config`;
CREATE TABLE `sys_config`  (
  `config_id` int NOT NULL AUTO_INCREMENT COMMENT '参数主键',
  `config_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '参数名称',
  `config_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '参数键名',
  `config_value` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '参数键值',
  `config_type` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'N' COMMENT '系统内置（Y是 N否）',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`config_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 7 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '参数配置表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_config
-- ----------------------------
INSERT INTO `sys_config` VALUES (1, '主框架页-默认皮肤样式名称', 'sys.index.skinName', 'skin-blue', 'Y', 'admin', '2025-05-09 14:44:19', '', NULL, '蓝色 skin-blue、绿色 skin-green、紫色 skin-purple、红色 skin-red、黄色 skin-yellow');
INSERT INTO `sys_config` VALUES (2, '用户管理-账号初始密码', 'sys.user.initPassword', '123456', 'Y', 'admin', '2025-05-09 14:44:19', '', NULL, '初始化密码 123456');
INSERT INTO `sys_config` VALUES (3, '主框架页-侧边栏主题', 'sys.index.sideTheme', 'theme-dark', 'Y', 'admin', '2025-05-09 14:44:19', '', NULL, '深色主题theme-dark，浅色主题theme-light');
INSERT INTO `sys_config` VALUES (4, '账号自助-验证码开关', 'sys.account.captchaEnabled', 'false', 'Y', 'admin', '2025-05-09 14:44:19', 'admin', '2025-05-19 01:07:46', '是否开启验证码功能（true开启，false关闭）');
INSERT INTO `sys_config` VALUES (5, '账号自助-是否开启用户注册功能', 'sys.account.registerUser', 'true', 'Y', 'admin', '2025-05-09 14:44:19', 'admin', '2025-05-19 01:07:41', '是否开启注册用户功能（true开启，false关闭）');
INSERT INTO `sys_config` VALUES (6, '用户登录-黑名单列表', 'sys.login.blackIPList', '', 'Y', 'admin', '2025-05-09 14:44:19', '', NULL, '设置登录IP黑名单限制，多个匹配项以;分隔，支持匹配（*通配、网段）');

-- ----------------------------
-- Table structure for sys_dept
-- ----------------------------
DROP TABLE IF EXISTS `sys_dept`;
CREATE TABLE `sys_dept`  (
  `dept_id` bigint NOT NULL AUTO_INCREMENT COMMENT '部门id',
  `parent_id` bigint NULL DEFAULT 0 COMMENT '父部门id',
  `ancestors` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '祖级列表',
  `dept_name` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '部门名称',
  `order_num` int NULL DEFAULT 0 COMMENT '显示顺序',
  `leader` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '负责人',
  `phone` varchar(11) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '联系电话',
  `email` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '邮箱',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '部门状态（0正常 1停用）',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`dept_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 202 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '部门表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_dept
-- ----------------------------
INSERT INTO `sys_dept` VALUES (100, 0, '0', '众基消防', 0, '', '', '', '0', '0', 'admin', '2025-05-09 14:44:19', 'admin', '2025-05-09 15:00:46');
INSERT INTO `sys_dept` VALUES (101, 100, '0,100', '管理部门', 1, '', '', '', '0', '0', 'admin', '2025-05-09 14:44:19', 'admin', '2025-05-09 15:01:03');
INSERT INTO `sys_dept` VALUES (102, 100, '0,100', '财务部门', 2, '', '', '', '0', '0', 'admin', '2025-05-09 14:44:19', 'admin', '2025-05-09 15:01:25');
INSERT INTO `sys_dept` VALUES (103, 101, '0,100,101', '研发部门', 1, '若依', '15888888888', 'ry@qq.com', '0', '2', 'admin', '2025-05-09 14:44:19', '', NULL);
INSERT INTO `sys_dept` VALUES (104, 101, '0,100,101', '市场部门', 2, '若依', '15888888888', 'ry@qq.com', '0', '2', 'admin', '2025-05-09 14:44:19', '', NULL);
INSERT INTO `sys_dept` VALUES (105, 101, '0,100,101', '测试部门', 3, '若依', '15888888888', 'ry@qq.com', '0', '2', 'admin', '2025-05-09 14:44:19', '', NULL);
INSERT INTO `sys_dept` VALUES (106, 101, '0,100,101', '财务部门', 4, '若依', '15888888888', 'ry@qq.com', '0', '2', 'admin', '2025-05-09 14:44:19', '', NULL);
INSERT INTO `sys_dept` VALUES (107, 101, '0,100,101', '运维部门', 5, '若依', '15888888888', 'ry@qq.com', '0', '2', 'admin', '2025-05-09 14:44:19', '', NULL);
INSERT INTO `sys_dept` VALUES (108, 102, '0,100,102', '市场部门', 1, '若依', '15888888888', 'ry@qq.com', '0', '2', 'admin', '2025-05-09 14:44:19', '', NULL);
INSERT INTO `sys_dept` VALUES (109, 102, '0,100,102', '财务部门', 2, '若依', '15888888888', 'ry@qq.com', '0', '2', 'admin', '2025-05-09 14:44:19', '', NULL);
INSERT INTO `sys_dept` VALUES (200, 100, '0,100', '技术部门', 3, NULL, NULL, NULL, '0', '0', 'admin', '2025-05-09 15:01:46', '', NULL);
INSERT INTO `sys_dept` VALUES (201, 100, '0,100', '工程部门', 4, NULL, NULL, NULL, '0', '0', 'admin', '2025-05-09 15:01:56', '', NULL);

-- ----------------------------
-- Table structure for sys_dict_data
-- ----------------------------
DROP TABLE IF EXISTS `sys_dict_data`;
CREATE TABLE `sys_dict_data`  (
  `dict_code` bigint NOT NULL AUTO_INCREMENT COMMENT '字典编码',
  `dict_sort` int NULL DEFAULT 0 COMMENT '字典排序',
  `dict_label` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '字典标签',
  `dict_value` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '字典键值',
  `dict_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '字典类型',
  `css_class` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '样式属性（其他样式扩展）',
  `list_class` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '表格回显样式',
  `is_default` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT 'N' COMMENT '是否默认（Y是 N否）',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`dict_code`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 109 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '字典数据表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_dict_data
-- ----------------------------
INSERT INTO `sys_dict_data` VALUES (1, 1, '男', '0', 'sys_user_sex', '', '', 'Y', '0', 'admin', '2025-05-09 14:44:19', '', NULL, '性别男');
INSERT INTO `sys_dict_data` VALUES (2, 2, '女', '1', 'sys_user_sex', '', '', 'N', '0', 'admin', '2025-05-09 14:44:19', '', NULL, '性别女');
INSERT INTO `sys_dict_data` VALUES (3, 3, '未知', '2', 'sys_user_sex', '', '', 'N', '0', 'admin', '2025-05-09 14:44:19', '', NULL, '性别未知');
INSERT INTO `sys_dict_data` VALUES (4, 1, '显示', '0', 'sys_show_hide', '', 'primary', 'Y', '0', 'admin', '2025-05-09 14:44:19', '', NULL, '显示菜单');
INSERT INTO `sys_dict_data` VALUES (5, 2, '隐藏', '1', 'sys_show_hide', '', 'danger', 'N', '0', 'admin', '2025-05-09 14:44:19', '', NULL, '隐藏菜单');
INSERT INTO `sys_dict_data` VALUES (6, 1, '正常', '0', 'sys_normal_disable', '', 'primary', 'Y', '0', 'admin', '2025-05-09 14:44:19', '', NULL, '正常状态');
INSERT INTO `sys_dict_data` VALUES (7, 2, '停用', '1', 'sys_normal_disable', '', 'danger', 'N', '0', 'admin', '2025-05-09 14:44:19', '', NULL, '停用状态');
INSERT INTO `sys_dict_data` VALUES (8, 1, '正常', '0', 'sys_job_status', '', 'primary', 'Y', '0', 'admin', '2025-05-09 14:44:19', '', NULL, '正常状态');
INSERT INTO `sys_dict_data` VALUES (9, 2, '暂停', '1', 'sys_job_status', '', 'danger', 'N', '0', 'admin', '2025-05-09 14:44:19', '', NULL, '停用状态');
INSERT INTO `sys_dict_data` VALUES (10, 1, '默认', 'DEFAULT', 'sys_job_group', '', '', 'Y', '0', 'admin', '2025-05-09 14:44:19', '', NULL, '默认分组');
INSERT INTO `sys_dict_data` VALUES (11, 2, '系统', 'SYSTEM', 'sys_job_group', '', '', 'N', '0', 'admin', '2025-05-09 14:44:19', '', NULL, '系统分组');
INSERT INTO `sys_dict_data` VALUES (12, 1, '是', 'Y', 'sys_yes_no', '', 'primary', 'Y', '0', 'admin', '2025-05-09 14:44:19', '', NULL, '系统默认是');
INSERT INTO `sys_dict_data` VALUES (13, 2, '否', 'N', 'sys_yes_no', '', 'danger', 'N', '0', 'admin', '2025-05-09 14:44:19', '', NULL, '系统默认否');
INSERT INTO `sys_dict_data` VALUES (14, 1, '通知', '1', 'sys_notice_type', '', 'warning', 'Y', '0', 'admin', '2025-05-09 14:44:19', '', NULL, '通知');
INSERT INTO `sys_dict_data` VALUES (15, 2, '公告', '2', 'sys_notice_type', '', 'success', 'N', '0', 'admin', '2025-05-09 14:44:19', '', NULL, '公告');
INSERT INTO `sys_dict_data` VALUES (16, 1, '正常', '0', 'sys_notice_status', '', 'primary', 'Y', '0', 'admin', '2025-05-09 14:44:19', '', NULL, '正常状态');
INSERT INTO `sys_dict_data` VALUES (17, 2, '关闭', '1', 'sys_notice_status', '', 'danger', 'N', '0', 'admin', '2025-05-09 14:44:19', '', NULL, '关闭状态');
INSERT INTO `sys_dict_data` VALUES (18, 99, '其他', '0', 'sys_oper_type', '', 'info', 'N', '0', 'admin', '2025-05-09 14:44:19', '', NULL, '其他操作');
INSERT INTO `sys_dict_data` VALUES (19, 1, '新增', '1', 'sys_oper_type', '', 'info', 'N', '0', 'admin', '2025-05-09 14:44:19', '', NULL, '新增操作');
INSERT INTO `sys_dict_data` VALUES (20, 2, '修改', '2', 'sys_oper_type', '', 'info', 'N', '0', 'admin', '2025-05-09 14:44:19', '', NULL, '修改操作');
INSERT INTO `sys_dict_data` VALUES (21, 3, '删除', '3', 'sys_oper_type', '', 'danger', 'N', '0', 'admin', '2025-05-09 14:44:19', '', NULL, '删除操作');
INSERT INTO `sys_dict_data` VALUES (22, 4, '授权', '4', 'sys_oper_type', '', 'primary', 'N', '0', 'admin', '2025-05-09 14:44:19', '', NULL, '授权操作');
INSERT INTO `sys_dict_data` VALUES (23, 5, '导出', '5', 'sys_oper_type', '', 'warning', 'N', '0', 'admin', '2025-05-09 14:44:19', '', NULL, '导出操作');
INSERT INTO `sys_dict_data` VALUES (24, 6, '导入', '6', 'sys_oper_type', '', 'warning', 'N', '0', 'admin', '2025-05-09 14:44:19', '', NULL, '导入操作');
INSERT INTO `sys_dict_data` VALUES (25, 7, '强退', '7', 'sys_oper_type', '', 'danger', 'N', '0', 'admin', '2025-05-09 14:44:19', '', NULL, '强退操作');
INSERT INTO `sys_dict_data` VALUES (26, 8, '生成代码', '8', 'sys_oper_type', '', 'warning', 'N', '0', 'admin', '2025-05-09 14:44:19', '', NULL, '生成操作');
INSERT INTO `sys_dict_data` VALUES (27, 9, '清空数据', '9', 'sys_oper_type', '', 'danger', 'N', '0', 'admin', '2025-05-09 14:44:19', '', NULL, '清空操作');
INSERT INTO `sys_dict_data` VALUES (28, 1, '成功', '0', 'sys_common_status', '', 'primary', 'N', '0', 'admin', '2025-05-09 14:44:19', '', NULL, '正常状态');
INSERT INTO `sys_dict_data` VALUES (29, 2, '失败', '1', 'sys_common_status', '', 'danger', 'N', '0', 'admin', '2025-05-09 14:44:19', '', NULL, '停用状态');
INSERT INTO `sys_dict_data` VALUES (100, 0, '维保', '0', 'contract_type', NULL, 'default', 'N', '0', 'admin', '2025-05-09 15:05:37', '', NULL, NULL);
INSERT INTO `sys_dict_data` VALUES (101, 1, '检测', '1', 'contract_type', NULL, 'default', 'N', '0', 'admin', '2025-05-09 15:05:47', '', NULL, NULL);
INSERT INTO `sys_dict_data` VALUES (102, 2, '评估', '2', 'contract_type', NULL, 'default', 'N', '0', 'admin', '2025-05-09 15:06:18', '', NULL, NULL);
INSERT INTO `sys_dict_data` VALUES (103, 0, '是', 'Y', 'report_isfinal', NULL, 'success', 'N', '0', 'admin', '2025-05-13 16:18:00', '', NULL, '是最终版');
INSERT INTO `sys_dict_data` VALUES (104, 1, '否', 'N', 'report_isfinal', NULL, 'warning', 'N', '0', 'admin', '2025-05-13 16:18:35', '', NULL, '不是最终版本');
INSERT INTO `sys_dict_data` VALUES (105, 0, '检测', 'JC', 'report_type', NULL, 'default', 'N', '0', 'admin', '2025-05-13 16:20:04', '', NULL, '检测报告');
INSERT INTO `sys_dict_data` VALUES (106, 1, '评定', 'PD', 'report_type', NULL, 'default', 'N', '0', 'admin', '2025-05-13 16:21:21', '', NULL, '评定报告');
INSERT INTO `sys_dict_data` VALUES (107, 3, '评估', 'PG', 'report_type', NULL, 'default', 'N', '0', 'admin', '2025-05-16 06:49:10', 'admin', '2025-05-16 06:49:24', '检测评估');
INSERT INTO `sys_dict_data` VALUES (108, 3, '评定', 'PD', 'contract_type', NULL, 'default', 'N', '0', 'admin', '2025-05-26 09:26:45', '', NULL, NULL);

-- ----------------------------
-- Table structure for sys_dict_type
-- ----------------------------
DROP TABLE IF EXISTS `sys_dict_type`;
CREATE TABLE `sys_dict_type`  (
  `dict_id` bigint NOT NULL AUTO_INCREMENT COMMENT '字典主键',
  `dict_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '字典名称',
  `dict_type` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '字典类型',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`dict_id`) USING BTREE,
  UNIQUE INDEX `dict_type`(`dict_type` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 103 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '字典类型表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_dict_type
-- ----------------------------
INSERT INTO `sys_dict_type` VALUES (1, '用户性别', 'sys_user_sex', '0', 'admin', '2025-05-09 14:44:19', '', NULL, '用户性别列表');
INSERT INTO `sys_dict_type` VALUES (2, '菜单状态', 'sys_show_hide', '0', 'admin', '2025-05-09 14:44:19', '', NULL, '菜单状态列表');
INSERT INTO `sys_dict_type` VALUES (3, '系统开关', 'sys_normal_disable', '0', 'admin', '2025-05-09 14:44:19', '', NULL, '系统开关列表');
INSERT INTO `sys_dict_type` VALUES (4, '任务状态', 'sys_job_status', '0', 'admin', '2025-05-09 14:44:19', '', NULL, '任务状态列表');
INSERT INTO `sys_dict_type` VALUES (5, '任务分组', 'sys_job_group', '0', 'admin', '2025-05-09 14:44:19', '', NULL, '任务分组列表');
INSERT INTO `sys_dict_type` VALUES (6, '系统是否', 'sys_yes_no', '0', 'admin', '2025-05-09 14:44:19', '', NULL, '系统是否列表');
INSERT INTO `sys_dict_type` VALUES (7, '通知类型', 'sys_notice_type', '0', 'admin', '2025-05-09 14:44:19', '', NULL, '通知类型列表');
INSERT INTO `sys_dict_type` VALUES (8, '通知状态', 'sys_notice_status', '0', 'admin', '2025-05-09 14:44:19', '', NULL, '通知状态列表');
INSERT INTO `sys_dict_type` VALUES (9, '操作类型', 'sys_oper_type', '0', 'admin', '2025-05-09 14:44:19', '', NULL, '操作类型列表');
INSERT INTO `sys_dict_type` VALUES (10, '系统状态', 'sys_common_status', '0', 'admin', '2025-05-09 14:44:19', '', NULL, '登录状态列表');
INSERT INTO `sys_dict_type` VALUES (100, '合同类型', 'contract_type', '0', 'admin', '2025-05-09 15:05:05', '', NULL, NULL);
INSERT INTO `sys_dict_type` VALUES (101, '报告状态', 'report_isfinal', '0', 'admin', '2025-05-13 16:17:22', 'admin', '2025-05-13 16:17:28', '报告是否最终版');
INSERT INTO `sys_dict_type` VALUES (102, '报告类型', 'report_type', '0', 'admin', '2025-05-13 16:19:32', '', NULL, '报告类型');

-- ----------------------------
-- Table structure for sys_job
-- ----------------------------
DROP TABLE IF EXISTS `sys_job`;
CREATE TABLE `sys_job`  (
  `job_id` bigint NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  `job_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT '' COMMENT '任务名称',
  `job_group` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL DEFAULT 'DEFAULT' COMMENT '任务组名',
  `invoke_target` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '调用目标字符串',
  `cron_expression` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT 'cron执行表达式',
  `misfire_policy` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '3' COMMENT '计划执行错误策略（1立即执行 2执行一次 3放弃执行）',
  `concurrent` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '1' COMMENT '是否并发执行（0允许 1禁止）',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '状态（0正常 1暂停）',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '备注信息',
  PRIMARY KEY (`job_id`, `job_name`, `job_group`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 4 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '定时任务调度表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_job
-- ----------------------------
INSERT INTO `sys_job` VALUES (1, '系统默认（无参）', 'DEFAULT', 'ryTask.ryNoParams', '0/10 * * * * ?', '3', '1', '1', 'admin', '2025-05-09 14:44:19', '', NULL, '');
INSERT INTO `sys_job` VALUES (2, '系统默认（有参）', 'DEFAULT', 'ryTask.ryParams(\'ry\')', '0/15 * * * * ?', '3', '1', '1', 'admin', '2025-05-09 14:44:19', '', NULL, '');
INSERT INTO `sys_job` VALUES (3, '系统默认（多参）', 'DEFAULT', 'ryTask.ryMultipleParams(\'ry\', true, 2000L, 316.50D, 100)', '0/20 * * * * ?', '3', '1', '1', 'admin', '2025-05-09 14:44:19', '', NULL, '');

-- ----------------------------
-- Table structure for sys_job_log
-- ----------------------------
DROP TABLE IF EXISTS `sys_job_log`;
CREATE TABLE `sys_job_log`  (
  `job_log_id` bigint NOT NULL AUTO_INCREMENT COMMENT '任务日志ID',
  `job_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务名称',
  `job_group` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '任务组名',
  `invoke_target` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '调用目标字符串',
  `job_message` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '日志信息',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '执行状态（0正常 1失败）',
  `exception_info` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '异常信息',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`job_log_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '定时任务调度日志表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_job_log
-- ----------------------------

-- ----------------------------
-- Table structure for sys_logininfor
-- ----------------------------
DROP TABLE IF EXISTS `sys_logininfor`;
CREATE TABLE `sys_logininfor`  (
  `info_id` bigint NOT NULL AUTO_INCREMENT COMMENT '访问ID',
  `user_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '用户账号',
  `ipaddr` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '登录IP地址',
  `login_location` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '登录地点',
  `browser` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '浏览器类型',
  `os` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '操作系统',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '登录状态（0成功 1失败）',
  `msg` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '提示消息',
  `login_time` datetime NULL DEFAULT NULL COMMENT '访问时间',
  PRIMARY KEY (`info_id`) USING BTREE,
  INDEX `idx_sys_logininfor_s`(`status` ASC) USING BTREE,
  INDEX `idx_sys_logininfor_lt`(`login_time` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 218 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '系统访问记录' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_logininfor
-- ----------------------------
INSERT INTO `sys_logininfor` VALUES (100, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-05-09 14:50:11');
INSERT INTO `sys_logininfor` VALUES (101, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-05-09 16:42:14');
INSERT INTO `sys_logininfor` VALUES (102, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-05-13 11:08:52');
INSERT INTO `sys_logininfor` VALUES (103, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-05-13 13:46:31');
INSERT INTO `sys_logininfor` VALUES (104, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-05-13 16:05:00');
INSERT INTO `sys_logininfor` VALUES (105, 'admin', '127.0.0.1', '内网IP', 'Chrome 10', 'Windows 7', '0', '登录成功', '2025-05-13 16:08:15');
INSERT INTO `sys_logininfor` VALUES (106, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '1', '验证码错误', '2025-05-13 16:08:33');
INSERT INTO `sys_logininfor` VALUES (107, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-05-13 16:08:37');
INSERT INTO `sys_logininfor` VALUES (108, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '退出成功', '2025-05-13 17:03:03');
INSERT INTO `sys_logininfor` VALUES (109, 'xiaoyu', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-05-13 17:03:10');
INSERT INTO `sys_logininfor` VALUES (110, 'xiaoyu', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '退出成功', '2025-05-13 17:04:50');
INSERT INTO `sys_logininfor` VALUES (111, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '1', '验证码错误', '2025-05-13 17:04:56');
INSERT INTO `sys_logininfor` VALUES (112, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-05-13 17:04:59');
INSERT INTO `sys_logininfor` VALUES (113, 'admin', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '退出成功', '2025-05-13 17:05:22');
INSERT INTO `sys_logininfor` VALUES (114, 'xiaoyu', '127.0.0.1', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-05-13 17:05:31');
INSERT INTO `sys_logininfor` VALUES (115, 'xiaoyu', '192.168.1.108', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-05-15 09:08:18');
INSERT INTO `sys_logininfor` VALUES (116, 'xiaoyu', '192.168.1.119', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-05-15 09:52:50');
INSERT INTO `sys_logininfor` VALUES (117, 'xiaoyu', '192.168.1.119', '内网IP', 'Chrome 13', 'Windows 10', '0', '退出成功', '2025-05-15 09:53:22');
INSERT INTO `sys_logininfor` VALUES (118, 'xiaoyu', '192.168.1.119', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-05-15 09:53:26');
INSERT INTO `sys_logininfor` VALUES (119, 'xiaoyu', '192.168.1.119', '内网IP', 'Chrome 13', 'Windows 10', '0', '退出成功', '2025-05-15 09:53:31');
INSERT INTO `sys_logininfor` VALUES (120, 'admin', '192.168.1.119', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-05-15 09:53:36');
INSERT INTO `sys_logininfor` VALUES (121, 'admin', '192.168.1.119', '内网IP', 'Chrome 13', 'Windows 10', '0', '退出成功', '2025-05-15 10:28:55');
INSERT INTO `sys_logininfor` VALUES (122, 'xiaoyu', '192.168.1.119', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-05-15 10:29:10');
INSERT INTO `sys_logininfor` VALUES (123, 'xiaoyu', '192.168.1.113', '内网IP', 'Chrome 12', 'Windows 7', '0', '登录成功', '2025-05-15 10:39:13');
INSERT INTO `sys_logininfor` VALUES (124, 'xiaoyu', '192.168.1.113', '内网IP', 'Chrome 12', 'Windows 7', '0', '登录成功', '2025-05-15 12:13:22');
INSERT INTO `sys_logininfor` VALUES (125, 'admin', '192.168.1.119', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-05-16 00:53:09');
INSERT INTO `sys_logininfor` VALUES (126, 'admin', '192.168.1.119', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-05-16 01:05:43');
INSERT INTO `sys_logininfor` VALUES (127, 'xiaoyu', '192.168.1.109', '内网IP', 'Chrome 12', 'Windows 7', '0', '登录成功', '2025-05-16 02:35:55');
INSERT INTO `sys_logininfor` VALUES (128, 'xiaoyu', '192.168.1.109', '内网IP', 'Chrome 12', 'Windows 7', '1', '验证码已失效', '2025-05-16 03:38:24');
INSERT INTO `sys_logininfor` VALUES (129, 'xiaoyu', '192.168.1.109', '内网IP', 'Chrome 12', 'Windows 7', '0', '登录成功', '2025-05-16 03:38:34');
INSERT INTO `sys_logininfor` VALUES (130, 'admin', '192.168.1.119', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-05-16 03:47:33');
INSERT INTO `sys_logininfor` VALUES (131, 'admin', '192.168.1.119', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-05-16 06:12:34');
INSERT INTO `sys_logininfor` VALUES (132, 'liya', '192.168.1.113', '内网IP', 'Chrome 10', 'Windows 7', '0', '登录成功', '2025-05-16 06:14:58');
INSERT INTO `sys_logininfor` VALUES (133, 'xiaoyu', '192.168.1.109', '内网IP', 'Chrome 12', 'Windows 7', '1', '验证码错误', '2025-05-16 06:39:34');
INSERT INTO `sys_logininfor` VALUES (134, 'xiaoyu', '192.168.1.109', '内网IP', 'Chrome 12', 'Windows 7', '0', '登录成功', '2025-05-16 06:39:41');
INSERT INTO `sys_logininfor` VALUES (135, 'admin', '192.168.1.119', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-05-16 06:46:28');
INSERT INTO `sys_logininfor` VALUES (136, 'liya', '192.168.1.113', '内网IP', 'Chrome 10', 'Windows 7', '0', '登录成功', '2025-05-16 07:01:34');
INSERT INTO `sys_logininfor` VALUES (137, 'admin', '192.168.1.119', '内网IP', 'Chrome 13', 'Windows 10', '1', '验证码错误', '2025-05-16 07:33:22');
INSERT INTO `sys_logininfor` VALUES (138, 'admin', '192.168.1.119', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-05-16 07:33:26');
INSERT INTO `sys_logininfor` VALUES (139, 'admin', '192.168.1.119', '内网IP', 'Chrome 13', 'Windows 10', '0', '退出成功', '2025-05-16 07:46:21');
INSERT INTO `sys_logininfor` VALUES (140, 'sun', '192.168.1.119', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-05-16 07:46:26');
INSERT INTO `sys_logininfor` VALUES (141, 'sun', '192.168.1.119', '内网IP', 'Chrome 13', 'Windows 10', '0', '退出成功', '2025-05-16 07:47:02');
INSERT INTO `sys_logininfor` VALUES (142, 'admin', '192.168.1.119', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-05-16 07:47:09');
INSERT INTO `sys_logininfor` VALUES (143, 'xiaoyu', '192.168.1.109', '内网IP', 'Chrome 12', 'Windows 7', '1', '验证码错误', '2025-05-16 07:47:12');
INSERT INTO `sys_logininfor` VALUES (144, 'xiaoyu', '192.168.1.109', '内网IP', 'Chrome 12', 'Windows 7', '0', '登录成功', '2025-05-16 07:47:23');
INSERT INTO `sys_logininfor` VALUES (145, 'admin', '192.168.1.119', '内网IP', 'Chrome 13', 'Windows 10', '0', '退出成功', '2025-05-16 07:49:06');
INSERT INTO `sys_logininfor` VALUES (146, 'jinyan', '192.168.1.119', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-05-16 07:49:14');
INSERT INTO `sys_logininfor` VALUES (147, 'jinyan', '192.168.1.119', '内网IP', 'Chrome 13', 'Windows 10', '0', '退出成功', '2025-05-16 07:49:20');
INSERT INTO `sys_logininfor` VALUES (148, 'sun', '192.168.1.119', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-05-16 07:49:28');
INSERT INTO `sys_logininfor` VALUES (149, 'sun', '192.168.1.119', '内网IP', 'Chrome 13', 'Windows 10', '0', '退出成功', '2025-05-16 07:49:46');
INSERT INTO `sys_logininfor` VALUES (150, 'admin', '192.168.1.119', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-05-16 07:49:50');
INSERT INTO `sys_logininfor` VALUES (151, 'admin', '192.168.1.119', '内网IP', 'Chrome 13', 'Windows 10', '0', '退出成功', '2025-05-16 07:50:06');
INSERT INTO `sys_logininfor` VALUES (152, 'sun', '192.168.1.119', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-05-16 07:50:16');
INSERT INTO `sys_logininfor` VALUES (153, 'xiaoyu', '192.168.1.109', '内网IP', 'Chrome 12', 'Windows 7', '0', '登录成功', '2025-05-16 09:38:58');
INSERT INTO `sys_logininfor` VALUES (154, 'admin', '192.168.1.119', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-05-16 09:57:13');
INSERT INTO `sys_logininfor` VALUES (155, 'liya', '192.168.1.107', '内网IP', 'Chrome 10', 'Windows 7', '0', '登录成功', '2025-05-17 00:43:13');
INSERT INTO `sys_logininfor` VALUES (156, 'xiaoyu', '192.168.1.113', '内网IP', 'Chrome 12', 'Windows 7', '0', '登录成功', '2025-05-17 01:02:16');
INSERT INTO `sys_logininfor` VALUES (157, 'admin', '192.168.1.112', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-05-17 01:02:51');
INSERT INTO `sys_logininfor` VALUES (158, 'admin', '192.168.1.112', '内网IP', 'Chrome 13', 'Windows 10', '0', '退出成功', '2025-05-17 01:03:29');
INSERT INTO `sys_logininfor` VALUES (159, 'wangyufeng', '192.168.1.112', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-05-17 01:03:36');
INSERT INTO `sys_logininfor` VALUES (160, 'wangyufeng', '192.168.1.112', '内网IP', 'Chrome 13', 'Windows 10', '0', '退出成功', '2025-05-17 01:04:29');
INSERT INTO `sys_logininfor` VALUES (161, 'admin', '192.168.1.112', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-05-17 01:04:36');
INSERT INTO `sys_logininfor` VALUES (162, 'wangyufeng', '192.168.1.116', '内网IP', 'Chrome 12', 'Windows 10', '0', '登录成功', '2025-05-17 01:04:44');
INSERT INTO `sys_logininfor` VALUES (163, 'admin', '192.168.1.112', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-05-17 02:45:16');
INSERT INTO `sys_logininfor` VALUES (164, 'liya', '192.168.1.107', '内网IP', 'Chrome 10', 'Windows 7', '0', '登录成功', '2025-05-17 06:37:58');
INSERT INTO `sys_logininfor` VALUES (165, 'admin', '192.168.1.112', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-05-17 06:38:19');
INSERT INTO `sys_logininfor` VALUES (166, 'liya', '192.168.1.107', '内网IP', 'Chrome 10', 'Windows 7', '0', '登录成功', '2025-05-17 07:26:49');
INSERT INTO `sys_logininfor` VALUES (167, 'liya', '192.168.1.107', '内网IP', 'Chrome 10', 'Windows 7', '0', '登录成功', '2025-05-17 07:27:47');
INSERT INTO `sys_logininfor` VALUES (168, 'admin', '103.151.172.32', 'XX XX', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-05-17 10:28:15');
INSERT INTO `sys_logininfor` VALUES (169, 'admin', '192.168.1.112', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-05-19 01:07:08');
INSERT INTO `sys_logininfor` VALUES (170, 'admin', '192.168.1.112', '内网IP', 'Chrome 13', 'Windows 10', '0', '退出成功', '2025-05-19 01:07:52');
INSERT INTO `sys_logininfor` VALUES (171, 'admin', '192.168.1.112', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-05-19 01:07:58');
INSERT INTO `sys_logininfor` VALUES (172, 'admin', '192.168.1.112', '内网IP', 'Chrome 13', 'Windows 10', '0', '退出成功', '2025-05-19 01:08:21');
INSERT INTO `sys_logininfor` VALUES (173, 'admin', '192.168.1.112', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-05-19 01:08:26');
INSERT INTO `sys_logininfor` VALUES (174, 'admin', '192.168.1.112', '内网IP', 'Chrome 13', 'Windows 10', '0', '退出成功', '2025-05-19 01:08:49');
INSERT INTO `sys_logininfor` VALUES (175, 'admin', '192.168.1.112', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-05-19 01:08:53');
INSERT INTO `sys_logininfor` VALUES (176, 'admin', '192.168.1.112', '内网IP', 'Chrome 13', 'Windows 10', '0', '退出成功', '2025-05-19 01:09:51');
INSERT INTO `sys_logininfor` VALUES (177, 'guoshasha', '192.168.1.112', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-05-19 01:09:55');
INSERT INTO `sys_logininfor` VALUES (178, 'guoshasha', '192.168.1.112', '内网IP', 'Chrome 13', 'Windows 10', '0', '退出成功', '2025-05-19 01:10:02');
INSERT INTO `sys_logininfor` VALUES (179, 'liuxiaosu', '192.168.1.112', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-05-19 01:10:08');
INSERT INTO `sys_logininfor` VALUES (180, 'liuxiaosu', '192.168.1.112', '内网IP', 'Chrome 13', 'Windows 10', '0', '退出成功', '2025-05-19 01:10:13');
INSERT INTO `sys_logininfor` VALUES (181, 'admin', '192.168.1.112', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-05-19 01:10:18');
INSERT INTO `sys_logininfor` VALUES (182, 'xiaoyu', '192.168.1.107', '内网IP', 'Chrome 13', 'Windows 7', '0', '登录成功', '2025-05-19 01:15:02');
INSERT INTO `sys_logininfor` VALUES (183, 'guoshasha', '192.168.1.123', '内网IP', 'Chrome 12', 'Windows 10', '0', '登录成功', '2025-05-19 01:15:13');
INSERT INTO `sys_logininfor` VALUES (184, 'admin', '192.168.1.112', '内网IP', 'Chrome 13', 'Windows 10', '0', '退出成功', '2025-05-19 01:15:50');
INSERT INTO `sys_logininfor` VALUES (185, 'sunmengjie', '192.168.1.112', '内网IP', 'Chrome 13', 'Windows 10', '1', '用户不存在/密码错误', '2025-05-19 01:15:57');
INSERT INTO `sys_logininfor` VALUES (186, 'admin', '192.168.1.112', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-05-19 01:16:09');
INSERT INTO `sys_logininfor` VALUES (187, 'admin', '192.168.1.112', '内网IP', 'Chrome 13', 'Windows 10', '0', '退出成功', '2025-05-19 01:16:22');
INSERT INTO `sys_logininfor` VALUES (188, 'sun', '192.168.1.112', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-05-19 01:16:26');
INSERT INTO `sys_logininfor` VALUES (189, 'liuxiaosu', '192.168.1.116', '内网IP', 'Chrome 12', 'Windows 10', '0', '登录成功', '2025-05-19 01:18:49');
INSERT INTO `sys_logininfor` VALUES (190, 'sun', '192.168.1.112', '内网IP', 'Chrome 13', 'Windows 10', '0', '退出成功', '2025-05-19 06:12:18');
INSERT INTO `sys_logininfor` VALUES (191, 'sun', '192.168.1.112', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-05-19 06:12:20');
INSERT INTO `sys_logininfor` VALUES (192, 'liuxiaosu', '192.168.1.116', '内网IP', 'Chrome 13', 'Windows 7', '0', '登录成功', '2025-05-19 06:55:31');
INSERT INTO `sys_logininfor` VALUES (193, 'sun', '192.168.1.102', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-05-20 01:27:31');
INSERT INTO `sys_logininfor` VALUES (194, 'xiaoyu', '192.168.1.106', '内网IP', 'Chrome 13', 'Windows 7', '0', '登录成功', '2025-05-21 01:14:22');
INSERT INTO `sys_logininfor` VALUES (195, 'guoshasha', '192.168.1.123', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-05-21 01:28:04');
INSERT INTO `sys_logininfor` VALUES (196, 'sun', '192.168.1.104', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-05-21 02:04:31');
INSERT INTO `sys_logininfor` VALUES (197, 'guoshasha', '192.168.1.123', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-05-21 06:15:55');
INSERT INTO `sys_logininfor` VALUES (198, 'xiaoyu', '192.168.1.106', '内网IP', 'Chrome 13', 'Windows 7', '0', '登录成功', '2025-05-21 09:07:13');
INSERT INTO `sys_logininfor` VALUES (199, 'guoshasha', '192.168.1.123', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-05-22 00:56:49');
INSERT INTO `sys_logininfor` VALUES (200, 'liuxiaosu', '192.168.1.114', '内网IP', 'Chrome 13', 'Windows 7', '0', '登录成功', '2025-05-22 01:52:07');
INSERT INTO `sys_logininfor` VALUES (201, 'sun', '192.168.1.106', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-05-22 06:39:35');
INSERT INTO `sys_logininfor` VALUES (202, 'guoshasha', '192.168.1.123', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-05-22 10:57:36');
INSERT INTO `sys_logininfor` VALUES (203, 'guoshasha', '192.168.1.128', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-05-23 09:00:47');
INSERT INTO `sys_logininfor` VALUES (204, 'liuxiaosu', '192.168.1.113', '内网IP', 'Chrome 13', 'Windows 7', '0', '登录成功', '2025-05-24 02:20:06');
INSERT INTO `sys_logininfor` VALUES (205, 'sun', '192.168.1.102', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-05-26 02:05:39');
INSERT INTO `sys_logininfor` VALUES (206, 'xiaoyu', '192.168.1.112', '内网IP', 'Chrome 13', 'Windows 7', '0', '登录成功', '2025-05-26 07:08:26');
INSERT INTO `sys_logininfor` VALUES (207, 'sun', '192.168.1.102', '内网IP', 'Chrome 13', 'Windows 10', '0', '退出成功', '2025-05-26 09:26:02');
INSERT INTO `sys_logininfor` VALUES (208, 'admin', '192.168.1.102', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-05-26 09:26:07');
INSERT INTO `sys_logininfor` VALUES (209, 'xiaoyu', '192.168.1.105', '内网IP', 'Chrome 13', 'Windows 7', '0', '登录成功', '2025-05-27 04:28:22');
INSERT INTO `sys_logininfor` VALUES (210, 'admin', '192.168.1.103', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-05-27 04:36:26');
INSERT INTO `sys_logininfor` VALUES (211, 'liuxiaosu', '192.168.1.107', '内网IP', 'Chrome 13', 'Windows 7', '0', '登录成功', '2025-05-27 07:12:09');
INSERT INTO `sys_logininfor` VALUES (212, 'liuxiaosu', '192.168.1.107', '内网IP', 'Chrome 13', 'Windows 7', '0', '登录成功', '2025-05-27 09:10:55');
INSERT INTO `sys_logininfor` VALUES (213, 'xiaoyu', '192.168.1.105', '内网IP', 'Chrome 10', 'Windows 10', '0', '登录成功', '2025-05-27 10:25:01');
INSERT INTO `sys_logininfor` VALUES (214, 'liuxiaosu', '192.168.1.105', '内网IP', 'Chrome 13', 'Windows 7', '0', '登录成功', '2025-05-28 01:09:15');
INSERT INTO `sys_logininfor` VALUES (215, 'admin', '192.168.1.103', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-05-28 01:29:24');
INSERT INTO `sys_logininfor` VALUES (216, 'xiaoyu', '192.168.1.106', '内网IP', 'Chrome 13', 'Windows 7', '0', '登录成功', '2025-05-28 08:17:26');
INSERT INTO `sys_logininfor` VALUES (217, 'sun', '192.168.1.109', '内网IP', 'Chrome 13', 'Windows 10', '0', '登录成功', '2025-05-29 00:56:39');

-- ----------------------------
-- Table structure for sys_menu
-- ----------------------------
DROP TABLE IF EXISTS `sys_menu`;
CREATE TABLE `sys_menu`  (
  `menu_id` bigint NOT NULL AUTO_INCREMENT COMMENT '菜单ID',
  `menu_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '菜单名称',
  `parent_id` bigint NULL DEFAULT 0 COMMENT '父菜单ID',
  `order_num` int NULL DEFAULT 0 COMMENT '显示顺序',
  `path` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '路由地址',
  `component` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '组件路径',
  `query` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '路由参数',
  `route_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '路由名称',
  `is_frame` int NULL DEFAULT 1 COMMENT '是否为外链（0是 1否）',
  `is_cache` int NULL DEFAULT 0 COMMENT '是否缓存（0缓存 1不缓存）',
  `menu_type` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '菜单类型（M目录 C菜单 F按钮）',
  `visible` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '菜单状态（0显示 1隐藏）',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '菜单状态（0正常 1停用）',
  `perms` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '权限标识',
  `icon` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '#' COMMENT '菜单图标',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '备注',
  PRIMARY KEY (`menu_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2013 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '菜单权限表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_menu
-- ----------------------------
INSERT INTO `sys_menu` VALUES (1, '系统管理', 0, 1, 'system', NULL, '', '', 1, 0, 'M', '0', '0', '', 'system', 'admin', '2025-05-09 14:44:19', '', NULL, '系统管理目录');
INSERT INTO `sys_menu` VALUES (2, '系统监控', 0, 2, 'monitor', NULL, '', '', 1, 0, 'M', '0', '0', '', 'monitor', 'admin', '2025-05-09 14:44:19', '', NULL, '系统监控目录');
INSERT INTO `sys_menu` VALUES (3, '系统工具', 0, 3, 'tool', NULL, '', '', 1, 0, 'M', '0', '0', '', 'tool', 'admin', '2025-05-09 14:44:19', '', NULL, '系统工具目录');
INSERT INTO `sys_menu` VALUES (4, '若依官网', 0, 4, 'http://ruoyi.vip', NULL, '', '', 0, 0, 'M', '0', '0', '', 'guide', 'admin', '2025-05-09 14:44:19', '', NULL, '若依官网地址');
INSERT INTO `sys_menu` VALUES (100, '用户管理', 1, 1, 'user', 'system/user/index', '', '', 1, 0, 'C', '0', '0', 'system:user:list', 'user', 'admin', '2025-05-09 14:44:19', '', NULL, '用户管理菜单');
INSERT INTO `sys_menu` VALUES (101, '角色管理', 1, 2, 'role', 'system/role/index', '', '', 1, 0, 'C', '0', '0', 'system:role:list', 'peoples', 'admin', '2025-05-09 14:44:19', '', NULL, '角色管理菜单');
INSERT INTO `sys_menu` VALUES (102, '菜单管理', 1, 3, 'menu', 'system/menu/index', '', '', 1, 0, 'C', '0', '0', 'system:menu:list', 'tree-table', 'admin', '2025-05-09 14:44:19', '', NULL, '菜单管理菜单');
INSERT INTO `sys_menu` VALUES (103, '部门管理', 1, 4, 'dept', 'system/dept/index', '', '', 1, 0, 'C', '0', '0', 'system:dept:list', 'tree', 'admin', '2025-05-09 14:44:19', '', NULL, '部门管理菜单');
INSERT INTO `sys_menu` VALUES (104, '岗位管理', 1, 5, 'post', 'system/post/index', '', '', 1, 0, 'C', '0', '0', 'system:post:list', 'post', 'admin', '2025-05-09 14:44:19', '', NULL, '岗位管理菜单');
INSERT INTO `sys_menu` VALUES (105, '字典管理', 1, 6, 'dict', 'system/dict/index', '', '', 1, 0, 'C', '0', '0', 'system:dict:list', 'dict', 'admin', '2025-05-09 14:44:19', '', NULL, '字典管理菜单');
INSERT INTO `sys_menu` VALUES (106, '参数设置', 1, 7, 'config', 'system/config/index', '', '', 1, 0, 'C', '0', '0', 'system:config:list', 'edit', 'admin', '2025-05-09 14:44:19', '', NULL, '参数设置菜单');
INSERT INTO `sys_menu` VALUES (107, '通知公告', 1, 8, 'notice', 'system/notice/index', '', '', 1, 0, 'C', '0', '0', 'system:notice:list', 'message', 'admin', '2025-05-09 14:44:19', '', NULL, '通知公告菜单');
INSERT INTO `sys_menu` VALUES (108, '日志管理', 1, 9, 'log', '', '', '', 1, 0, 'M', '0', '0', '', 'log', 'admin', '2025-05-09 14:44:19', '', NULL, '日志管理菜单');
INSERT INTO `sys_menu` VALUES (109, '在线用户', 2, 1, 'online', 'monitor/online/index', '', '', 1, 0, 'C', '0', '0', 'monitor:online:list', 'online', 'admin', '2025-05-09 14:44:19', '', NULL, '在线用户菜单');
INSERT INTO `sys_menu` VALUES (110, '定时任务', 2, 2, 'job', 'monitor/job/index', '', '', 1, 0, 'C', '0', '0', 'monitor:job:list', 'job', 'admin', '2025-05-09 14:44:19', '', NULL, '定时任务菜单');
INSERT INTO `sys_menu` VALUES (111, '数据监控', 2, 3, 'druid', 'monitor/druid/index', '', '', 1, 0, 'C', '0', '0', 'monitor:druid:list', 'druid', 'admin', '2025-05-09 14:44:19', '', NULL, '数据监控菜单');
INSERT INTO `sys_menu` VALUES (112, '服务监控', 2, 4, 'server', 'monitor/server/index', '', '', 1, 0, 'C', '0', '0', 'monitor:server:list', 'server', 'admin', '2025-05-09 14:44:19', '', NULL, '服务监控菜单');
INSERT INTO `sys_menu` VALUES (113, '缓存监控', 2, 5, 'cache', 'monitor/cache/index', '', '', 1, 0, 'C', '0', '0', 'monitor:cache:list', 'redis', 'admin', '2025-05-09 14:44:19', '', NULL, '缓存监控菜单');
INSERT INTO `sys_menu` VALUES (114, '缓存列表', 2, 6, 'cacheList', 'monitor/cache/list', '', '', 1, 0, 'C', '0', '0', 'monitor:cache:list', 'redis-list', 'admin', '2025-05-09 14:44:19', '', NULL, '缓存列表菜单');
INSERT INTO `sys_menu` VALUES (115, '表单构建', 3, 1, 'build', 'tool/build/index', '', '', 1, 0, 'C', '0', '0', 'tool:build:list', 'build', 'admin', '2025-05-09 14:44:19', '', NULL, '表单构建菜单');
INSERT INTO `sys_menu` VALUES (116, '代码生成', 3, 2, 'gen', 'tool/gen/index', '', '', 1, 0, 'C', '0', '0', 'tool:gen:list', 'code', 'admin', '2025-05-09 14:44:19', '', NULL, '代码生成菜单');
INSERT INTO `sys_menu` VALUES (117, '系统接口', 3, 3, 'swagger', 'tool/swagger/index', '', '', 1, 0, 'C', '0', '0', 'tool:swagger:list', 'swagger', 'admin', '2025-05-09 14:44:19', '', NULL, '系统接口菜单');
INSERT INTO `sys_menu` VALUES (500, '操作日志', 108, 1, 'operlog', 'monitor/operlog/index', '', '', 1, 0, 'C', '0', '0', 'monitor:operlog:list', 'form', 'admin', '2025-05-09 14:44:19', '', NULL, '操作日志菜单');
INSERT INTO `sys_menu` VALUES (501, '登录日志', 108, 2, 'logininfor', 'monitor/logininfor/index', '', '', 1, 0, 'C', '0', '0', 'monitor:logininfor:list', 'logininfor', 'admin', '2025-05-09 14:44:19', '', NULL, '登录日志菜单');
INSERT INTO `sys_menu` VALUES (1000, '用户查询', 100, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:query', '#', 'admin', '2025-05-09 14:44:19', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1001, '用户新增', 100, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:add', '#', 'admin', '2025-05-09 14:44:19', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1002, '用户修改', 100, 3, '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:edit', '#', 'admin', '2025-05-09 14:44:19', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1003, '用户删除', 100, 4, '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:remove', '#', 'admin', '2025-05-09 14:44:19', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1004, '用户导出', 100, 5, '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:export', '#', 'admin', '2025-05-09 14:44:19', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1005, '用户导入', 100, 6, '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:import', '#', 'admin', '2025-05-09 14:44:19', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1006, '重置密码', 100, 7, '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:resetPwd', '#', 'admin', '2025-05-09 14:44:19', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1007, '角色查询', 101, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:query', '#', 'admin', '2025-05-09 14:44:19', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1008, '角色新增', 101, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:add', '#', 'admin', '2025-05-09 14:44:19', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1009, '角色修改', 101, 3, '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:edit', '#', 'admin', '2025-05-09 14:44:19', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1010, '角色删除', 101, 4, '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:remove', '#', 'admin', '2025-05-09 14:44:19', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1011, '角色导出', 101, 5, '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:export', '#', 'admin', '2025-05-09 14:44:19', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1012, '菜单查询', 102, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'system:menu:query', '#', 'admin', '2025-05-09 14:44:19', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1013, '菜单新增', 102, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'system:menu:add', '#', 'admin', '2025-05-09 14:44:19', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1014, '菜单修改', 102, 3, '', '', '', '', 1, 0, 'F', '0', '0', 'system:menu:edit', '#', 'admin', '2025-05-09 14:44:19', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1015, '菜单删除', 102, 4, '', '', '', '', 1, 0, 'F', '0', '0', 'system:menu:remove', '#', 'admin', '2025-05-09 14:44:19', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1016, '部门查询', 103, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'system:dept:query', '#', 'admin', '2025-05-09 14:44:19', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1017, '部门新增', 103, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'system:dept:add', '#', 'admin', '2025-05-09 14:44:19', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1018, '部门修改', 103, 3, '', '', '', '', 1, 0, 'F', '0', '0', 'system:dept:edit', '#', 'admin', '2025-05-09 14:44:19', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1019, '部门删除', 103, 4, '', '', '', '', 1, 0, 'F', '0', '0', 'system:dept:remove', '#', 'admin', '2025-05-09 14:44:19', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1020, '岗位查询', 104, 1, '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:query', '#', 'admin', '2025-05-09 14:44:19', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1021, '岗位新增', 104, 2, '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:add', '#', 'admin', '2025-05-09 14:44:19', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1022, '岗位修改', 104, 3, '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:edit', '#', 'admin', '2025-05-09 14:44:19', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1023, '岗位删除', 104, 4, '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:remove', '#', 'admin', '2025-05-09 14:44:19', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1024, '岗位导出', 104, 5, '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:export', '#', 'admin', '2025-05-09 14:44:19', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1025, '字典查询', 105, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:query', '#', 'admin', '2025-05-09 14:44:19', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1026, '字典新增', 105, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:add', '#', 'admin', '2025-05-09 14:44:19', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1027, '字典修改', 105, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:edit', '#', 'admin', '2025-05-09 14:44:19', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1028, '字典删除', 105, 4, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:remove', '#', 'admin', '2025-05-09 14:44:19', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1029, '字典导出', 105, 5, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:export', '#', 'admin', '2025-05-09 14:44:19', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1030, '参数查询', 106, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:query', '#', 'admin', '2025-05-09 14:44:19', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1031, '参数新增', 106, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:add', '#', 'admin', '2025-05-09 14:44:19', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1032, '参数修改', 106, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:edit', '#', 'admin', '2025-05-09 14:44:19', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1033, '参数删除', 106, 4, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:remove', '#', 'admin', '2025-05-09 14:44:19', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1034, '参数导出', 106, 5, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:export', '#', 'admin', '2025-05-09 14:44:19', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1035, '公告查询', 107, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:notice:query', '#', 'admin', '2025-05-09 14:44:19', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1036, '公告新增', 107, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:notice:add', '#', 'admin', '2025-05-09 14:44:19', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1037, '公告修改', 107, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:notice:edit', '#', 'admin', '2025-05-09 14:44:19', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1038, '公告删除', 107, 4, '#', '', '', '', 1, 0, 'F', '0', '0', 'system:notice:remove', '#', 'admin', '2025-05-09 14:44:19', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1039, '操作查询', 500, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:operlog:query', '#', 'admin', '2025-05-09 14:44:19', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1040, '操作删除', 500, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:operlog:remove', '#', 'admin', '2025-05-09 14:44:19', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1041, '日志导出', 500, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:operlog:export', '#', 'admin', '2025-05-09 14:44:19', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1042, '登录查询', 501, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:logininfor:query', '#', 'admin', '2025-05-09 14:44:19', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1043, '登录删除', 501, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:logininfor:remove', '#', 'admin', '2025-05-09 14:44:19', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1044, '日志导出', 501, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:logininfor:export', '#', 'admin', '2025-05-09 14:44:19', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1045, '账户解锁', 501, 4, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:logininfor:unlock', '#', 'admin', '2025-05-09 14:44:19', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1046, '在线查询', 109, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:online:query', '#', 'admin', '2025-05-09 14:44:19', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1047, '批量强退', 109, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:online:batchLogout', '#', 'admin', '2025-05-09 14:44:19', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1048, '单条强退', 109, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:online:forceLogout', '#', 'admin', '2025-05-09 14:44:19', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1049, '任务查询', 110, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:query', '#', 'admin', '2025-05-09 14:44:19', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1050, '任务新增', 110, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:add', '#', 'admin', '2025-05-09 14:44:19', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1051, '任务修改', 110, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:edit', '#', 'admin', '2025-05-09 14:44:19', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1052, '任务删除', 110, 4, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:remove', '#', 'admin', '2025-05-09 14:44:19', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1053, '状态修改', 110, 5, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:changeStatus', '#', 'admin', '2025-05-09 14:44:19', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1054, '任务导出', 110, 6, '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:export', '#', 'admin', '2025-05-09 14:44:19', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1055, '生成查询', 116, 1, '#', '', '', '', 1, 0, 'F', '0', '0', 'tool:gen:query', '#', 'admin', '2025-05-09 14:44:19', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1056, '生成修改', 116, 2, '#', '', '', '', 1, 0, 'F', '0', '0', 'tool:gen:edit', '#', 'admin', '2025-05-09 14:44:19', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1057, '生成删除', 116, 3, '#', '', '', '', 1, 0, 'F', '0', '0', 'tool:gen:remove', '#', 'admin', '2025-05-09 14:44:19', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1058, '导入代码', 116, 4, '#', '', '', '', 1, 0, 'F', '0', '0', 'tool:gen:import', '#', 'admin', '2025-05-09 14:44:19', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1059, '预览代码', 116, 5, '#', '', '', '', 1, 0, 'F', '0', '0', 'tool:gen:preview', '#', 'admin', '2025-05-09 14:44:19', '', NULL, '');
INSERT INTO `sys_menu` VALUES (1060, '生成代码', 116, 6, '#', '', '', '', 1, 0, 'F', '0', '0', 'tool:gen:code', '#', 'admin', '2025-05-09 14:44:19', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2000, '合同管理', 0, 5, 'contract', 'management/contract/index', NULL, '', 1, 0, 'C', '0', '0', 'management:contract:list', 'documentation', 'admin', '2025-05-09 16:39:36', 'admin', '2025-05-19 01:10:32', '合同管理菜单');
INSERT INTO `sys_menu` VALUES (2001, '合同管理查询', 2000, 1, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'management:contract:query', '#', 'admin', '2025-05-09 16:39:36', 'admin', '2025-05-09 16:55:19', '');
INSERT INTO `sys_menu` VALUES (2002, '合同管理新增', 2000, 2, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'management:contract:add', '#', 'admin', '2025-05-09 16:39:36', 'admin', '2025-05-09 16:55:22', '');
INSERT INTO `sys_menu` VALUES (2003, '合同管理修改', 2000, 3, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'management:contract:edit', '#', 'admin', '2025-05-09 16:39:36', 'admin', '2025-05-09 16:55:25', '');
INSERT INTO `sys_menu` VALUES (2004, '合同管理删除', 2000, 4, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'management:contract:remove', '#', 'admin', '2025-05-09 16:39:36', 'admin', '2025-05-09 16:55:28', '');
INSERT INTO `sys_menu` VALUES (2005, '合同管理导出', 2000, 5, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'management:contract:export', '#', 'admin', '2025-05-09 16:39:36', 'admin', '2025-05-09 16:55:32', '');
INSERT INTO `sys_menu` VALUES (2007, '报告管理', 0, 6, 'report', 'management/report/index', NULL, '', 1, 0, 'C', '0', '0', 'management:report:list', 'documentation', 'admin', '2025-05-13 16:47:26', 'admin', '2025-05-19 01:10:40', '报告管理菜单');
INSERT INTO `sys_menu` VALUES (2008, '报告管理查询', 2007, 1, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'management:report:query', '#', 'admin', '2025-05-13 16:47:26', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2009, '报告管理新增', 2007, 2, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'management:report:add', '#', 'admin', '2025-05-13 16:47:26', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2010, '报告管理修改', 2007, 3, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'management:report:edit', '#', 'admin', '2025-05-13 16:47:26', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2011, '报告管理删除', 2007, 4, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'management:report:remove', '#', 'admin', '2025-05-13 16:47:26', '', NULL, '');
INSERT INTO `sys_menu` VALUES (2012, '报告管理导出', 2007, 5, '#', '', NULL, '', 1, 0, 'F', '0', '0', 'management:report:export', '#', 'admin', '2025-05-13 16:47:26', '', NULL, '');

-- ----------------------------
-- Table structure for sys_notice
-- ----------------------------
DROP TABLE IF EXISTS `sys_notice`;
CREATE TABLE `sys_notice`  (
  `notice_id` int NOT NULL AUTO_INCREMENT COMMENT '公告ID',
  `notice_title` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '公告标题',
  `notice_type` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '公告类型（1通知 2公告）',
  `notice_content` longblob NULL COMMENT '公告内容',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '公告状态（0正常 1关闭）',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`notice_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 3 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '通知公告表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_notice
-- ----------------------------
INSERT INTO `sys_notice` VALUES (1, '温馨提醒：2018-07-01 若依新版本发布啦', '2', 0xE696B0E78988E69CACE58685E5AEB9, '0', 'admin', '2025-05-09 14:44:19', '', NULL, '管理员');
INSERT INTO `sys_notice` VALUES (2, '维护通知：2018-07-01 若依系统凌晨维护', '1', 0xE7BBB4E68AA4E58685E5AEB9, '0', 'admin', '2025-05-09 14:44:19', '', NULL, '管理员');

-- ----------------------------
-- Table structure for sys_oper_log
-- ----------------------------
DROP TABLE IF EXISTS `sys_oper_log`;
CREATE TABLE `sys_oper_log`  (
  `oper_id` bigint NOT NULL AUTO_INCREMENT COMMENT '日志主键',
  `title` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '模块标题',
  `business_type` int NULL DEFAULT 0 COMMENT '业务类型（0其它 1新增 2修改 3删除）',
  `method` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '方法名称',
  `request_method` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '请求方式',
  `operator_type` int NULL DEFAULT 0 COMMENT '操作类别（0其它 1后台用户 2手机端用户）',
  `oper_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '操作人员',
  `dept_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '部门名称',
  `oper_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '请求URL',
  `oper_ip` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '主机地址',
  `oper_location` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '操作地点',
  `oper_param` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '请求参数',
  `json_result` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '返回参数',
  `status` int NULL DEFAULT 0 COMMENT '操作状态（0正常 1异常）',
  `error_msg` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '错误消息',
  `oper_time` datetime NULL DEFAULT NULL COMMENT '操作时间',
  `cost_time` bigint NULL DEFAULT 0 COMMENT '消耗时间',
  PRIMARY KEY (`oper_id`) USING BTREE,
  INDEX `idx_sys_oper_log_bt`(`business_type` ASC) USING BTREE,
  INDEX `idx_sys_oper_log_s`(`status` ASC) USING BTREE,
  INDEX `idx_sys_oper_log_ot`(`oper_time` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 282 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '操作日志记录' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_oper_log
-- ----------------------------
INSERT INTO `sys_oper_log` VALUES (100, '代码生成', 6, 'top.aameow.generator.controller.GenController.importTableSave()', 'POST', 1, 'admin', '研发部门', '/tool/gen/importTable', '127.0.0.1', '内网IP', '{\"tables\":\"zj_contract\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-09 14:59:24', 91);
INSERT INTO `sys_oper_log` VALUES (101, '部门管理', 2, 'top.aameow.web.controller.system.SysDeptController.edit()', 'PUT', 1, 'admin', '研发部门', '/system/dept', '127.0.0.1', '内网IP', '{\"ancestors\":\"0\",\"children\":[],\"deptId\":100,\"deptName\":\"众基消防\",\"email\":\"\",\"leader\":\"\",\"orderNum\":0,\"params\":{},\"parentId\":0,\"phone\":\"\",\"status\":\"0\",\"updateBy\":\"admin\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-09 15:00:46', 18);
INSERT INTO `sys_oper_log` VALUES (102, '部门管理', 2, 'top.aameow.web.controller.system.SysDeptController.edit()', 'PUT', 1, 'admin', '研发部门', '/system/dept', '127.0.0.1', '内网IP', '{\"ancestors\":\"0,100\",\"children\":[],\"deptId\":101,\"deptName\":\"管理部门\",\"email\":\"\",\"leader\":\"\",\"orderNum\":1,\"params\":{},\"parentId\":100,\"parentName\":\"众基消防\",\"phone\":\"\",\"status\":\"0\",\"updateBy\":\"admin\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-09 15:01:03', 28);
INSERT INTO `sys_oper_log` VALUES (103, '部门管理', 3, 'top.aameow.web.controller.system.SysDeptController.remove()', 'DELETE', 1, 'admin', '研发部门', '/system/dept/103', '127.0.0.1', '内网IP', '103', '{\"msg\":\"部门存在用户,不允许删除\",\"code\":601}', 0, NULL, '2025-05-09 15:01:05', 18);
INSERT INTO `sys_oper_log` VALUES (104, '部门管理', 3, 'top.aameow.web.controller.system.SysDeptController.remove()', 'DELETE', 1, 'admin', '研发部门', '/system/dept/103', '127.0.0.1', '内网IP', '103', '{\"msg\":\"部门存在用户,不允许删除\",\"code\":601}', 0, NULL, '2025-05-09 15:01:06', 4);
INSERT INTO `sys_oper_log` VALUES (105, '部门管理', 3, 'top.aameow.web.controller.system.SysDeptController.remove()', 'DELETE', 1, 'admin', '研发部门', '/system/dept/107', '127.0.0.1', '内网IP', '107', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-09 15:01:11', 20);
INSERT INTO `sys_oper_log` VALUES (106, '部门管理', 3, 'top.aameow.web.controller.system.SysDeptController.remove()', 'DELETE', 1, 'admin', '研发部门', '/system/dept/106', '127.0.0.1', '内网IP', '106', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-09 15:01:12', 13);
INSERT INTO `sys_oper_log` VALUES (107, '部门管理', 3, 'top.aameow.web.controller.system.SysDeptController.remove()', 'DELETE', 1, 'admin', '研发部门', '/system/dept/105', '127.0.0.1', '内网IP', '105', '{\"msg\":\"部门存在用户,不允许删除\",\"code\":601}', 0, NULL, '2025-05-09 15:01:12', 6);
INSERT INTO `sys_oper_log` VALUES (108, '部门管理', 2, 'top.aameow.web.controller.system.SysDeptController.edit()', 'PUT', 1, 'admin', '研发部门', '/system/dept', '127.0.0.1', '内网IP', '{\"ancestors\":\"0,100\",\"children\":[],\"deptId\":102,\"deptName\":\"财务部门\",\"email\":\"\",\"leader\":\"\",\"orderNum\":2,\"params\":{},\"parentId\":100,\"parentName\":\"众基消防\",\"phone\":\"\",\"status\":\"0\",\"updateBy\":\"admin\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-09 15:01:25', 31);
INSERT INTO `sys_oper_log` VALUES (109, '部门管理', 3, 'top.aameow.web.controller.system.SysDeptController.remove()', 'DELETE', 1, 'admin', '研发部门', '/system/dept/109', '127.0.0.1', '内网IP', '109', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-09 15:01:29', 9);
INSERT INTO `sys_oper_log` VALUES (110, '部门管理', 3, 'top.aameow.web.controller.system.SysDeptController.remove()', 'DELETE', 1, 'admin', '研发部门', '/system/dept/108', '127.0.0.1', '内网IP', '108', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-09 15:01:30', 18);
INSERT INTO `sys_oper_log` VALUES (111, '部门管理', 1, 'top.aameow.web.controller.system.SysDeptController.add()', 'POST', 1, 'admin', '研发部门', '/system/dept', '127.0.0.1', '内网IP', '{\"ancestors\":\"0,100\",\"children\":[],\"createBy\":\"admin\",\"deptName\":\"技术部门\",\"orderNum\":3,\"params\":{},\"parentId\":100,\"status\":\"0\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-09 15:01:46', 12);
INSERT INTO `sys_oper_log` VALUES (112, '部门管理', 1, 'top.aameow.web.controller.system.SysDeptController.add()', 'POST', 1, 'admin', '研发部门', '/system/dept', '127.0.0.1', '内网IP', '{\"ancestors\":\"0,100\",\"children\":[],\"createBy\":\"admin\",\"deptName\":\"工程部门\",\"orderNum\":4,\"params\":{},\"parentId\":100,\"status\":\"0\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-09 15:01:56', 10);
INSERT INTO `sys_oper_log` VALUES (113, '用户管理', 2, 'top.aameow.web.controller.system.SysUserController.edit()', 'PUT', 1, 'admin', '研发部门', '/system/user', '127.0.0.1', '内网IP', '{\"admin\":true,\"avatar\":\"\",\"createBy\":\"admin\",\"createTime\":\"2025-05-09 14:44:19\",\"delFlag\":\"0\",\"dept\":{\"ancestors\":\"0,100,101\",\"children\":[],\"deptId\":103,\"deptName\":\"研发部门\",\"leader\":\"若依\",\"orderNum\":1,\"params\":{},\"parentId\":101,\"status\":\"0\"},\"deptId\":100,\"email\":\"\",\"loginDate\":\"2025-05-09 14:50:11\",\"loginIp\":\"127.0.0.1\",\"nickName\":\"开发账号\",\"params\":{},\"phonenumber\":\"\",\"postIds\":[1],\"remark\":\"\",\"roleIds\":[1],\"roles\":[{\"admin\":true,\"dataScope\":\"1\",\"deptCheckStrictly\":false,\"flag\":false,\"menuCheckStrictly\":false,\"params\":{},\"roleId\":1,\"roleKey\":\"admin\",\"roleName\":\"超级管理员\",\"roleSort\":1,\"status\":\"0\"}],\"sex\":\"1\",\"status\":\"0\",\"userId\":1,\"userName\":\"admin\"}', NULL, 1, '不允许操作超级管理员用户', '2025-05-09 15:02:56', 4);
INSERT INTO `sys_oper_log` VALUES (114, '用户管理', 2, 'top.aameow.web.controller.system.SysUserController.edit()', 'PUT', 1, 'admin', '研发部门', '/system/user', '127.0.0.1', '内网IP', '{\"admin\":true,\"avatar\":\"\",\"createBy\":\"admin\",\"createTime\":\"2025-05-09 14:44:19\",\"delFlag\":\"0\",\"dept\":{\"ancestors\":\"0,100,101\",\"children\":[],\"deptId\":103,\"deptName\":\"研发部门\",\"leader\":\"若依\",\"orderNum\":1,\"params\":{},\"parentId\":101,\"status\":\"0\"},\"deptId\":100,\"email\":\"\",\"loginDate\":\"2025-05-09 14:50:11\",\"loginIp\":\"127.0.0.1\",\"nickName\":\"开发账号\",\"params\":{},\"phonenumber\":\"\",\"postIds\":[1],\"remark\":\"\",\"roleIds\":[],\"roles\":[{\"admin\":true,\"dataScope\":\"1\",\"deptCheckStrictly\":false,\"flag\":false,\"menuCheckStrictly\":false,\"params\":{},\"roleId\":1,\"roleKey\":\"admin\",\"roleName\":\"超级管理员\",\"roleSort\":1,\"status\":\"0\"}],\"sex\":\"1\",\"status\":\"0\",\"userId\":1,\"userName\":\"admin\"}', NULL, 1, '不允许操作超级管理员用户', '2025-05-09 15:02:59', 1);
INSERT INTO `sys_oper_log` VALUES (115, '用户管理', 3, 'top.aameow.web.controller.system.SysUserController.remove()', 'DELETE', 1, 'admin', '研发部门', '/system/user/2', '127.0.0.1', '内网IP', '[2]', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-09 15:04:20', 19);
INSERT INTO `sys_oper_log` VALUES (116, '部门管理', 3, 'top.aameow.web.controller.system.SysDeptController.remove()', 'DELETE', 1, 'admin', '研发部门', '/system/dept/105', '127.0.0.1', '内网IP', '105', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-09 15:04:35', 16);
INSERT INTO `sys_oper_log` VALUES (117, '部门管理', 3, 'top.aameow.web.controller.system.SysDeptController.remove()', 'DELETE', 1, 'admin', '研发部门', '/system/dept/104', '127.0.0.1', '内网IP', '104', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-09 15:04:36', 18);
INSERT INTO `sys_oper_log` VALUES (118, '部门管理', 3, 'top.aameow.web.controller.system.SysDeptController.remove()', 'DELETE', 1, 'admin', '研发部门', '/system/dept/103', '127.0.0.1', '内网IP', '103', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-09 15:04:37', 19);
INSERT INTO `sys_oper_log` VALUES (119, '字典类型', 1, 'top.aameow.web.controller.system.SysDictTypeController.add()', 'POST', 1, 'admin', '研发部门', '/system/dict/type', '127.0.0.1', '内网IP', '{\"createBy\":\"admin\",\"dictName\":\"合同类型\",\"dictType\":\"contract_type\",\"params\":{},\"status\":\"0\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-09 15:05:05', 22);
INSERT INTO `sys_oper_log` VALUES (120, '字典数据', 1, 'top.aameow.web.controller.system.SysDictDataController.add()', 'POST', 1, 'admin', '研发部门', '/system/dict/data', '127.0.0.1', '内网IP', '{\"createBy\":\"admin\",\"default\":false,\"dictLabel\":\"维保\",\"dictSort\":0,\"dictType\":\"contract_type\",\"dictValue\":\"0\",\"listClass\":\"default\",\"params\":{},\"status\":\"0\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-09 15:05:37', 11);
INSERT INTO `sys_oper_log` VALUES (121, '字典数据', 1, 'top.aameow.web.controller.system.SysDictDataController.add()', 'POST', 1, 'admin', '研发部门', '/system/dict/data', '127.0.0.1', '内网IP', '{\"createBy\":\"admin\",\"default\":false,\"dictLabel\":\"检测\",\"dictSort\":1,\"dictType\":\"contract_type\",\"dictValue\":\"1\",\"listClass\":\"default\",\"params\":{},\"status\":\"0\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-09 15:05:47', 8);
INSERT INTO `sys_oper_log` VALUES (122, '字典数据', 1, 'top.aameow.web.controller.system.SysDictDataController.add()', 'POST', 1, 'admin', '研发部门', '/system/dict/data', '127.0.0.1', '内网IP', '{\"createBy\":\"admin\",\"default\":false,\"dictLabel\":\"评估\",\"dictSort\":2,\"dictType\":\"contract_type\",\"dictValue\":\"2\",\"listClass\":\"default\",\"params\":{},\"status\":\"0\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-09 15:06:18', 7);
INSERT INTO `sys_oper_log` VALUES (123, '代码生成', 3, 'top.aameow.generator.controller.GenController.remove()', 'DELETE', 1, 'admin', '研发部门', '/tool/gen/1', '127.0.0.1', '内网IP', '[1]', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-09 15:07:18', 24);
INSERT INTO `sys_oper_log` VALUES (124, '代码生成', 6, 'top.aameow.generator.controller.GenController.importTableSave()', 'POST', 1, 'admin', '研发部门', '/tool/gen/importTable', '127.0.0.1', '内网IP', '{\"tables\":\"zj_contract\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-09 15:07:37', 30);
INSERT INTO `sys_oper_log` VALUES (125, '代码生成', 2, 'top.aameow.generator.controller.GenController.synchDb()', 'GET', 1, 'admin', '研发部门', '/tool/gen/synchDb/zj_contract', '127.0.0.1', '内网IP', '{}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-09 15:08:54', 46);
INSERT INTO `sys_oper_log` VALUES (126, '代码生成', 2, 'top.aameow.generator.controller.GenController.editSave()', 'PUT', 1, 'admin', '研发部门', '/tool/gen', '127.0.0.1', '内网IP', '{\"businessName\":\"contract\",\"className\":\"ZjContract\",\"columns\":[{\"capJavaField\":\"Id\",\"columnComment\":\"\",\"columnId\":9,\"columnName\":\"id\",\"columnType\":\"int(11)\",\"createBy\":\"admin\",\"createTime\":\"2025-05-09 15:07:37\",\"dictType\":\"\",\"edit\":false,\"htmlType\":\"input\",\"increment\":true,\"insert\":false,\"isIncrement\":\"1\",\"isInsert\":\"0\",\"isPk\":\"1\",\"isRequired\":\"0\",\"javaField\":\"id\",\"javaType\":\"Long\",\"list\":false,\"params\":{},\"pk\":true,\"query\":false,\"queryType\":\"EQ\",\"required\":false,\"sort\":1,\"superColumn\":false,\"tableId\":2,\"updateBy\":\"\",\"updateTime\":\"2025-05-09 15:08:54\",\"usableColumn\":false},{\"capJavaField\":\"ContractId\",\"columnComment\":\"合同ID（类型-编号）\",\"columnId\":10,\"columnName\":\"contract_id\",\"columnType\":\"varchar(255)\",\"createBy\":\"admin\",\"createTime\":\"2025-05-09 15:07:37\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"input\",\"increment\":false,\"insert\":true,\"isEdit\":\"1\",\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isList\":\"1\",\"isPk\":\"0\",\"isQuery\":\"1\",\"isRequired\":\"1\",\"javaField\":\"contractId\",\"javaType\":\"String\",\"list\":true,\"params\":{},\"pk\":false,\"query\":true,\"queryType\":\"EQ\",\"required\":true,\"sort\":2,\"superColumn\":false,\"tableId\":2,\"updateBy\":\"\",\"updateTime\":\"2025-05-09 15:08:54\",\"usableColumn\":false},{\"capJavaField\":\"ContractType\",\"columnComment\":\"合同类型（字典）\",\"columnId\":11,\"columnName\":\"contract_type\",\"columnType\":\"int(2)\",\"createBy\":\"admin\",\"createTime\":\"2025-05-09 15:07:37\",\"dictType\":\"contract_type\",\"edit\":true,\"htmlType\":\"select\",\"increment\":false,\"insert\":true,\"isEdit\":\"1\",\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isList\":\"1\",\"isPk\":\"0\",\"isQuery\":\"1\",\"isRequired\":\"1\",\"javaField\":\"contractType\",\"javaType\":\"Integer\",\"list\":true,\"params\":{},\"pk\":false,\"query\":true,\"queryType\":\"EQ\",\"required\":true,\"sort\":3,\"superColumn\":false,\"tableId\":2,\"updateBy\":\"\",\"updateTime\":\"2025-05-09 15:08:54\",\"usableColumn\":false},{\"capJavaField\":\"ContractName\",\"columnComment\":\"合同名称\",\"columnId\":12,\"columnName\":\"contract_name\",\"columnType\":\"varchar(255)\",\"createBy\":\"admin\",\"createTime\":\"2025-05-09 15:07:37\",\"dictType\":\"\",\"edit\":true,\"htmlTy', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-09 15:25:55', 68);
INSERT INTO `sys_oper_log` VALUES (127, '代码生成', 8, 'top.aameow.generator.controller.GenController.genCode()', 'GET', 1, 'admin', '研发部门', '/tool/gen/genCode/zj_contract', '127.0.0.1', '内网IP', '{}', '{\"msg\":\"【系统预设】不允许生成文件覆盖到本地\",\"code\":500}', 0, NULL, '2025-05-09 15:25:57', 0);
INSERT INTO `sys_oper_log` VALUES (128, '代码生成', 2, 'top.aameow.generator.controller.GenController.editSave()', 'PUT', 1, 'admin', '研发部门', '/tool/gen', '127.0.0.1', '内网IP', '{\"businessName\":\"contract\",\"className\":\"ZjContract\",\"columns\":[{\"capJavaField\":\"Id\",\"columnComment\":\"\",\"columnId\":9,\"columnName\":\"id\",\"columnType\":\"int(11)\",\"createBy\":\"admin\",\"createTime\":\"2025-05-09 15:07:37\",\"dictType\":\"\",\"edit\":false,\"htmlType\":\"input\",\"increment\":true,\"insert\":false,\"isIncrement\":\"1\",\"isInsert\":\"0\",\"isPk\":\"1\",\"isRequired\":\"0\",\"javaField\":\"id\",\"javaType\":\"Long\",\"list\":false,\"params\":{},\"pk\":true,\"query\":false,\"queryType\":\"EQ\",\"required\":false,\"sort\":1,\"superColumn\":false,\"tableId\":2,\"updateBy\":\"\",\"updateTime\":\"2025-05-09 15:25:55\",\"usableColumn\":false},{\"capJavaField\":\"ContractId\",\"columnComment\":\"合同ID（类型-编号）\",\"columnId\":10,\"columnName\":\"contract_id\",\"columnType\":\"varchar(255)\",\"createBy\":\"admin\",\"createTime\":\"2025-05-09 15:07:37\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"input\",\"increment\":false,\"insert\":true,\"isEdit\":\"1\",\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isList\":\"1\",\"isPk\":\"0\",\"isQuery\":\"1\",\"isRequired\":\"1\",\"javaField\":\"contractId\",\"javaType\":\"String\",\"list\":true,\"params\":{},\"pk\":false,\"query\":true,\"queryType\":\"EQ\",\"required\":true,\"sort\":2,\"superColumn\":false,\"tableId\":2,\"updateBy\":\"\",\"updateTime\":\"2025-05-09 15:25:55\",\"usableColumn\":false},{\"capJavaField\":\"ContractType\",\"columnComment\":\"合同类型（字典）\",\"columnId\":11,\"columnName\":\"contract_type\",\"columnType\":\"int(2)\",\"createBy\":\"admin\",\"createTime\":\"2025-05-09 15:07:37\",\"dictType\":\"contract_type\",\"edit\":true,\"htmlType\":\"select\",\"increment\":false,\"insert\":true,\"isEdit\":\"1\",\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isList\":\"1\",\"isPk\":\"0\",\"isQuery\":\"1\",\"isRequired\":\"1\",\"javaField\":\"contractType\",\"javaType\":\"Integer\",\"list\":true,\"params\":{},\"pk\":false,\"query\":true,\"queryType\":\"EQ\",\"required\":true,\"sort\":3,\"superColumn\":false,\"tableId\":2,\"updateBy\":\"\",\"updateTime\":\"2025-05-09 15:25:55\",\"usableColumn\":false},{\"capJavaField\":\"ContractName\",\"columnComment\":\"合同名称\",\"columnId\":12,\"columnName\":\"contract_name\",\"columnType\":\"varchar(255)\",\"createBy\":\"admin\",\"createTime\":\"2025-05-09 15:07:37\",\"dictType\":\"\",\"edit\":true,\"htmlTy', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-09 15:26:12', 35);
INSERT INTO `sys_oper_log` VALUES (129, '代码生成', 8, 'top.aameow.generator.controller.GenController.batchGenCode()', 'GET', 1, 'admin', '研发部门', '/tool/gen/batchGenCode', '127.0.0.1', '内网IP', '{\"tables\":\"zj_contract\"}', NULL, 0, NULL, '2025-05-09 15:26:16', 208);
INSERT INTO `sys_oper_log` VALUES (130, '代码生成', 2, 'top.aameow.generator.controller.GenController.editSave()', 'PUT', 1, 'admin', '众基消防', '/tool/gen', '127.0.0.1', '内网IP', '{\"businessName\":\"contract\",\"className\":\"ZjContract\",\"columns\":[{\"capJavaField\":\"Id\",\"columnComment\":\"\",\"columnId\":9,\"columnName\":\"id\",\"columnType\":\"int(11)\",\"createBy\":\"admin\",\"createTime\":\"2025-05-09 15:07:37\",\"dictType\":\"\",\"edit\":false,\"htmlType\":\"input\",\"increment\":true,\"insert\":false,\"isIncrement\":\"1\",\"isInsert\":\"0\",\"isPk\":\"1\",\"isRequired\":\"0\",\"javaField\":\"id\",\"javaType\":\"Long\",\"list\":false,\"params\":{},\"pk\":true,\"query\":false,\"queryType\":\"EQ\",\"required\":false,\"sort\":1,\"superColumn\":false,\"tableId\":2,\"updateBy\":\"\",\"updateTime\":\"2025-05-09 15:26:12\",\"usableColumn\":false},{\"capJavaField\":\"ContractId\",\"columnComment\":\"合同ID（类型-编号）\",\"columnId\":10,\"columnName\":\"contract_id\",\"columnType\":\"varchar(255)\",\"createBy\":\"admin\",\"createTime\":\"2025-05-09 15:07:37\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"input\",\"increment\":false,\"insert\":true,\"isEdit\":\"1\",\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isList\":\"1\",\"isPk\":\"0\",\"isQuery\":\"1\",\"isRequired\":\"1\",\"javaField\":\"contractId\",\"javaType\":\"String\",\"list\":true,\"params\":{},\"pk\":false,\"query\":true,\"queryType\":\"EQ\",\"required\":true,\"sort\":2,\"superColumn\":false,\"tableId\":2,\"updateBy\":\"\",\"updateTime\":\"2025-05-09 15:26:12\",\"usableColumn\":false},{\"capJavaField\":\"ContractType\",\"columnComment\":\"合同类型（字典）\",\"columnId\":11,\"columnName\":\"contract_type\",\"columnType\":\"int(2)\",\"createBy\":\"admin\",\"createTime\":\"2025-05-09 15:07:37\",\"dictType\":\"contract_type\",\"edit\":true,\"htmlType\":\"select\",\"increment\":false,\"insert\":true,\"isEdit\":\"1\",\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isList\":\"1\",\"isPk\":\"0\",\"isQuery\":\"1\",\"isRequired\":\"1\",\"javaField\":\"contractType\",\"javaType\":\"Integer\",\"list\":true,\"params\":{},\"pk\":false,\"query\":true,\"queryType\":\"EQ\",\"required\":true,\"sort\":3,\"superColumn\":false,\"tableId\":2,\"updateBy\":\"\",\"updateTime\":\"2025-05-09 15:26:12\",\"usableColumn\":false},{\"capJavaField\":\"ContractName\",\"columnComment\":\"合同名称\",\"columnId\":12,\"columnName\":\"contract_name\",\"columnType\":\"varchar(255)\",\"createBy\":\"admin\",\"createTime\":\"2025-05-09 15:07:37\",\"dictType\":\"\",\"edit\":true,\"htmlTy', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-09 16:46:32', 74);
INSERT INTO `sys_oper_log` VALUES (131, '代码生成', 8, 'top.aameow.generator.controller.GenController.batchGenCode()', 'GET', 1, 'admin', '众基消防', '/tool/gen/batchGenCode', '127.0.0.1', '内网IP', '{\"tables\":\"zj_contract\"}', NULL, 0, NULL, '2025-05-09 16:46:39', 109);
INSERT INTO `sys_oper_log` VALUES (132, '菜单管理', 2, 'top.aameow.web.controller.system.SysMenuController.edit()', 'PUT', 1, 'admin', '众基消防', '/system/menu', '127.0.0.1', '内网IP', '{\"children\":[],\"component\":\"management/contract/index\",\"createTime\":\"2025-05-09 16:39:36\",\"icon\":\"#\",\"isCache\":\"0\",\"isFrame\":\"1\",\"menuId\":2000,\"menuName\":\"合同管理\",\"menuType\":\"C\",\"orderNum\":1,\"params\":{},\"parentId\":0,\"path\":\"contract\",\"perms\":\"management:contract:list\",\"routeName\":\"\",\"status\":\"0\",\"updateBy\":\"admin\",\"visible\":\"0\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-09 16:52:13', 40);
INSERT INTO `sys_oper_log` VALUES (133, '菜单管理', 2, 'top.aameow.web.controller.system.SysMenuController.edit()', 'PUT', 1, 'admin', '众基消防', '/system/menu', '127.0.0.1', '内网IP', '{\"children\":[],\"component\":\"\",\"createTime\":\"2025-05-09 16:39:36\",\"icon\":\"#\",\"isCache\":\"0\",\"isFrame\":\"1\",\"menuId\":2001,\"menuName\":\"合同管理查询\",\"menuType\":\"F\",\"orderNum\":1,\"params\":{},\"parentId\":2000,\"path\":\"#\",\"perms\":\"management:contract:query\",\"routeName\":\"\",\"status\":\"0\",\"updateBy\":\"admin\",\"visible\":\"0\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-09 16:55:19', 46);
INSERT INTO `sys_oper_log` VALUES (134, '菜单管理', 2, 'top.aameow.web.controller.system.SysMenuController.edit()', 'PUT', 1, 'admin', '众基消防', '/system/menu', '127.0.0.1', '内网IP', '{\"children\":[],\"component\":\"\",\"createTime\":\"2025-05-09 16:39:36\",\"icon\":\"#\",\"isCache\":\"0\",\"isFrame\":\"1\",\"menuId\":2002,\"menuName\":\"合同管理新增\",\"menuType\":\"F\",\"orderNum\":2,\"params\":{},\"parentId\":2000,\"path\":\"#\",\"perms\":\"management:contract:add\",\"routeName\":\"\",\"status\":\"0\",\"updateBy\":\"admin\",\"visible\":\"0\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-09 16:55:22', 20);
INSERT INTO `sys_oper_log` VALUES (135, '菜单管理', 2, 'top.aameow.web.controller.system.SysMenuController.edit()', 'PUT', 1, 'admin', '众基消防', '/system/menu', '127.0.0.1', '内网IP', '{\"children\":[],\"component\":\"\",\"createTime\":\"2025-05-09 16:39:36\",\"icon\":\"#\",\"isCache\":\"0\",\"isFrame\":\"1\",\"menuId\":2003,\"menuName\":\"合同管理修改\",\"menuType\":\"F\",\"orderNum\":3,\"params\":{},\"parentId\":2000,\"path\":\"#\",\"perms\":\"management:contract:edit\",\"routeName\":\"\",\"status\":\"0\",\"updateBy\":\"admin\",\"visible\":\"0\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-09 16:55:25', 10);
INSERT INTO `sys_oper_log` VALUES (136, '菜单管理', 2, 'top.aameow.web.controller.system.SysMenuController.edit()', 'PUT', 1, 'admin', '众基消防', '/system/menu', '127.0.0.1', '内网IP', '{\"children\":[],\"component\":\"\",\"createTime\":\"2025-05-09 16:39:36\",\"icon\":\"#\",\"isCache\":\"0\",\"isFrame\":\"1\",\"menuId\":2004,\"menuName\":\"合同管理删除\",\"menuType\":\"F\",\"orderNum\":4,\"params\":{},\"parentId\":2000,\"path\":\"#\",\"perms\":\"management:contract:remove\",\"routeName\":\"\",\"status\":\"0\",\"updateBy\":\"admin\",\"visible\":\"0\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-09 16:55:28', 11);
INSERT INTO `sys_oper_log` VALUES (137, '菜单管理', 2, 'top.aameow.web.controller.system.SysMenuController.edit()', 'PUT', 1, 'admin', '众基消防', '/system/menu', '127.0.0.1', '内网IP', '{\"children\":[],\"component\":\"\",\"createTime\":\"2025-05-09 16:39:36\",\"icon\":\"#\",\"isCache\":\"0\",\"isFrame\":\"1\",\"menuId\":2005,\"menuName\":\"合同管理导出\",\"menuType\":\"F\",\"orderNum\":5,\"params\":{},\"parentId\":2000,\"path\":\"#\",\"perms\":\"management:contract:export\",\"routeName\":\"\",\"status\":\"0\",\"updateBy\":\"admin\",\"visible\":\"0\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-09 16:55:32', 9);
INSERT INTO `sys_oper_log` VALUES (138, '代码生成', 2, 'top.aameow.generator.controller.GenController.synchDb()', 'GET', 1, 'admin', '众基消防', '/tool/gen/synchDb/zj_contract', '127.0.0.1', '内网IP', '{}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-09 17:17:44', 204);
INSERT INTO `sys_oper_log` VALUES (139, '菜单管理', 2, 'top.aameow.web.controller.system.SysMenuController.edit()', 'PUT', 1, 'admin', '众基消防', '/system/menu', '127.0.0.1', '内网IP', '{\"children\":[],\"component\":\"management/contract/index\",\"createTime\":\"2025-05-09 16:39:36\",\"icon\":\"documentation\",\"isCache\":\"0\",\"isFrame\":\"1\",\"menuId\":2000,\"menuName\":\"合同管理\",\"menuType\":\"C\",\"orderNum\":1,\"params\":{},\"parentId\":0,\"path\":\"contract\",\"perms\":\"management:contract:list\",\"routeName\":\"\",\"status\":\"0\",\"updateBy\":\"admin\",\"visible\":\"0\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-13 11:12:43', 32);
INSERT INTO `sys_oper_log` VALUES (140, '代码生成', 2, 'top.aameow.generator.controller.GenController.editSave()', 'PUT', 1, 'admin', '众基消防', '/tool/gen', '127.0.0.1', '内网IP', '{\"businessName\":\"contract\",\"className\":\"ZjContract\",\"columns\":[{\"capJavaField\":\"Id\",\"columnComment\":\"\",\"columnId\":9,\"columnName\":\"id\",\"columnType\":\"int(11)\",\"createBy\":\"admin\",\"createTime\":\"2025-05-09 15:07:37\",\"dictType\":\"\",\"edit\":false,\"htmlType\":\"input\",\"increment\":true,\"insert\":false,\"isIncrement\":\"1\",\"isInsert\":\"0\",\"isPk\":\"1\",\"isRequired\":\"0\",\"javaField\":\"id\",\"javaType\":\"Long\",\"list\":false,\"params\":{},\"pk\":true,\"query\":false,\"queryType\":\"EQ\",\"required\":false,\"sort\":1,\"superColumn\":false,\"tableId\":2,\"updateBy\":\"\",\"updateTime\":\"2025-05-09 17:17:44\",\"usableColumn\":false},{\"capJavaField\":\"ContractId\",\"columnComment\":\"合同编号\",\"columnId\":10,\"columnName\":\"contract_id\",\"columnType\":\"varchar(255)\",\"createBy\":\"admin\",\"createTime\":\"2025-05-09 15:07:37\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"input\",\"increment\":false,\"insert\":false,\"isEdit\":\"1\",\"isIncrement\":\"0\",\"isInsert\":\"0\",\"isList\":\"1\",\"isPk\":\"0\",\"isQuery\":\"1\",\"isRequired\":\"1\",\"javaField\":\"contractId\",\"javaType\":\"String\",\"list\":true,\"params\":{},\"pk\":false,\"query\":true,\"queryType\":\"EQ\",\"required\":true,\"sort\":2,\"superColumn\":false,\"tableId\":2,\"updateBy\":\"\",\"updateTime\":\"2025-05-09 17:17:44\",\"usableColumn\":false},{\"capJavaField\":\"ContractType\",\"columnComment\":\"合同类型\",\"columnId\":11,\"columnName\":\"contract_type\",\"columnType\":\"int(2)\",\"createBy\":\"admin\",\"createTime\":\"2025-05-09 15:07:37\",\"dictType\":\"contract_type\",\"edit\":true,\"htmlType\":\"select\",\"increment\":false,\"insert\":true,\"isEdit\":\"1\",\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isList\":\"1\",\"isPk\":\"0\",\"isQuery\":\"1\",\"isRequired\":\"1\",\"javaField\":\"contractType\",\"javaType\":\"Integer\",\"list\":true,\"params\":{},\"pk\":false,\"query\":true,\"queryType\":\"EQ\",\"required\":true,\"sort\":3,\"superColumn\":false,\"tableId\":2,\"updateBy\":\"\",\"updateTime\":\"2025-05-09 17:17:44\",\"usableColumn\":false},{\"capJavaField\":\"ContractName\",\"columnComment\":\"合同名称\",\"columnId\":12,\"columnName\":\"contract_name\",\"columnType\":\"varchar(255)\",\"createBy\":\"admin\",\"createTime\":\"2025-05-09 15:07:37\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"input', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-13 11:15:51', 70);
INSERT INTO `sys_oper_log` VALUES (141, '合同管理', 1, 'top.aameow.management.controller.ZjContractController.add()', 'POST', 1, 'admin', '众基消防', '/management/contract', '127.0.0.1', '内网IP', '{\"amount\":1000,\"contractId\":\"JC-1\",\"contractName\":\"测试合同\",\"contractType\":1,\"createTime\":\"2025-05-13 14:11:03\",\"endTime\":\"2025-05-31\",\"manager\":\"李四\",\"params\":{},\"startTime\":\"2025-05-01\"}', NULL, 1, 'org.mybatis.spring.MyBatisSystemException\r\n	at org.mybatis.spring.MyBatisExceptionTranslator.translateExceptionIfPossible(MyBatisExceptionTranslator.java:97)\r\n	at org.mybatis.spring.SqlSessionTemplate$SqlSessionInterceptor.invoke(SqlSessionTemplate.java:439)\r\n	at jdk.proxy2/jdk.proxy2.$Proxy103.insert(Unknown Source)\r\n	at org.mybatis.spring.SqlSessionTemplate.insert(SqlSessionTemplate.java:272)\r\n	at org.apache.ibatis.binding.MapperMethod.execute(MapperMethod.java:62)\r\n	at org.apache.ibatis.binding.MapperProxy$PlainMethodInvoker.invoke(MapperProxy.java:141)\r\n	at org.apache.ibatis.binding.MapperProxy.invoke(MapperProxy.java:86)\r\n	at jdk.proxy3/jdk.proxy3.$Proxy169.insertZjContract(Unknown Source)\r\n	at top.aameow.management.service.impl.ZjContractServiceImpl.insertZjContract(ZjContractServiceImpl.java:57)\r\n	at top.aameow.management.controller.ZjContractController.add(ZjContractController.java:81)\r\n	at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(DirectMethodHandleAccessor.java:103)\r\n	at java.base/java.lang.reflect.Method.invoke(Method.java:580)\r\n	at org.springframework.aop.support.AopUtils.invokeJoinpointUsingReflection(AopUtils.java:355)\r\n	at org.springframework.aop.framework.ReflectiveMethodInvocation.invokeJoinpoint(ReflectiveMethodInvocation.java:196)\r\n	at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:163)\r\n	at org.springframework.aop.framework.CglibAopProxy$CglibMethodInvocation.proceed(CglibAopProxy.java:768)\r\n	at org.springframework.aop.aspectj.AspectJAfterThrowingAdvice.invoke(AspectJAfterThrowingAdvice.java:64)\r\n	at org.springframework.aop.framework.ReflectiveMethodInvocation.proceed(ReflectiveMethodInvocation.java:173)\r\n	at org.springframework.aop.framework.CglibAopProxy$CglibMethodInvocation.proceed(CglibAopProxy.java:768)\r\n	at org.springframework.aop.framework.adapter.AfterReturningAdviceInterceptor.invoke(AfterReturningAdviceInterceptor.java:57)\r\n	at org.springframework.aop.framewor', '2025-05-13 14:11:04', 23);
INSERT INTO `sys_oper_log` VALUES (142, '合同管理', 1, 'top.aameow.management.controller.ZjContractController.add()', 'POST', 1, 'admin', '众基消防', '/management/contract', '127.0.0.1', '内网IP', '{\"amount\":1000,\"contractFile\":\"/profile/upload/2025/05/13/WB-1_测试合同_2025-05-01到2025-05-31_1000元(李四)_20250513141402A001.pdf\",\"contractId\":\"WB-1\",\"contractName\":\"测试合同\",\"contractType\":0,\"createTime\":\"2025-05-13 14:14:13\",\"endTime\":\"2025-05-31\",\"id\":1,\"manager\":\"李四\",\"params\":{},\"startTime\":\"2025-05-01\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-13 14:14:13', 148);
INSERT INTO `sys_oper_log` VALUES (143, '合同管理', 2, 'top.aameow.management.controller.ZjContractController.edit()', 'PUT', 1, 'admin', '众基消防', '/management/contract', '127.0.0.1', '内网IP', '{\"amount\":1000,\"contractFile\":\"\",\"contractId\":\"WB-1\",\"contractName\":\"测试合同\",\"contractType\":0,\"createTime\":\"2025-05-13 14:14:13\",\"endTime\":\"2025-05-31\",\"id\":1,\"manager\":\"李四\",\"params\":{},\"startTime\":\"2025-05-01\",\"updateTime\":\"2025-05-13 15:01:29\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-13 15:01:30', 40);
INSERT INTO `sys_oper_log` VALUES (144, '合同管理', 2, 'top.aameow.management.controller.ZjContractController.edit()', 'PUT', 1, 'admin', '众基消防', '/management/contract', '127.0.0.1', '内网IP', '{\"amount\":1000,\"contractFile\":\"/profile/upload/2025/05/13/WB-1_测试合同_2025-05-01到2025-05-31_1000元(李四)_20250513151239A003.pdf\",\"contractId\":\"WB-1\",\"contractName\":\"测试合同\",\"contractType\":0,\"createTime\":\"2025-05-13 14:14:13\",\"endTime\":\"2025-05-31\",\"id\":1,\"manager\":\"李四\",\"params\":{},\"startTime\":\"2025-05-01\",\"updateTime\":\"2025-05-13 15:12:41\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-13 15:12:41', 17);
INSERT INTO `sys_oper_log` VALUES (145, '菜单管理', 1, 'top.aameow.web.controller.system.SysMenuController.add()', 'POST', 1, 'admin', '众基消防', '/system/menu', '127.0.0.1', '内网IP', '{\"children\":[],\"createBy\":\"admin\",\"icon\":\"list\",\"isCache\":\"0\",\"isFrame\":\"1\",\"menuName\":\"文件管理\",\"menuType\":\"M\",\"orderNum\":5,\"params\":{},\"parentId\":0,\"path\":\"file\",\"status\":\"0\",\"visible\":\"0\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-13 16:11:13', 33);
INSERT INTO `sys_oper_log` VALUES (146, '菜单管理', 2, 'top.aameow.web.controller.system.SysMenuController.edit()', 'PUT', 1, 'admin', '众基消防', '/system/menu', '127.0.0.1', '内网IP', '{\"children\":[],\"component\":\"management/contract/index\",\"createTime\":\"2025-05-09 16:39:36\",\"icon\":\"documentation\",\"isCache\":\"0\",\"isFrame\":\"1\",\"menuId\":2000,\"menuName\":\"合同管理\",\"menuType\":\"C\",\"orderNum\":1,\"params\":{},\"parentId\":2006,\"path\":\"contract\",\"perms\":\"management:contract:list\",\"routeName\":\"\",\"status\":\"0\",\"updateBy\":\"admin\",\"visible\":\"0\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-13 16:11:23', 18);
INSERT INTO `sys_oper_log` VALUES (147, '字典类型', 1, 'top.aameow.web.controller.system.SysDictTypeController.add()', 'POST', 1, 'admin', '众基消防', '/system/dict/type', '127.0.0.1', '内网IP', '{\"createBy\":\"admin\",\"dictName\":\"报告状态\",\"dictType\":\"zjerp_report_isfinal\",\"params\":{},\"remark\":\"报告是否最终版\",\"status\":\"0\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-13 16:17:22', 13);
INSERT INTO `sys_oper_log` VALUES (148, '字典类型', 2, 'top.aameow.web.controller.system.SysDictTypeController.edit()', 'PUT', 1, 'admin', '众基消防', '/system/dict/type', '127.0.0.1', '内网IP', '{\"createBy\":\"admin\",\"createTime\":\"2025-05-13 16:17:22\",\"dictId\":101,\"dictName\":\"报告状态\",\"dictType\":\"report_isfinal\",\"params\":{},\"remark\":\"报告是否最终版\",\"status\":\"0\",\"updateBy\":\"admin\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-13 16:17:28', 39);
INSERT INTO `sys_oper_log` VALUES (149, '字典数据', 1, 'top.aameow.web.controller.system.SysDictDataController.add()', 'POST', 1, 'admin', '众基消防', '/system/dict/data', '127.0.0.1', '内网IP', '{\"createBy\":\"admin\",\"default\":false,\"dictLabel\":\"是\",\"dictSort\":0,\"dictType\":\"report_isfinal\",\"dictValue\":\"Y\",\"listClass\":\"success\",\"params\":{},\"remark\":\"是最终版\",\"status\":\"0\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-13 16:18:00', 19);
INSERT INTO `sys_oper_log` VALUES (150, '字典数据', 1, 'top.aameow.web.controller.system.SysDictDataController.add()', 'POST', 1, 'admin', '众基消防', '/system/dict/data', '127.0.0.1', '内网IP', '{\"createBy\":\"admin\",\"default\":false,\"dictLabel\":\"否\",\"dictSort\":1,\"dictType\":\"report_isfinal\",\"dictValue\":\"N\",\"listClass\":\"warning\",\"params\":{},\"remark\":\"不是最终版本\",\"status\":\"0\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-13 16:18:35', 17);
INSERT INTO `sys_oper_log` VALUES (151, '字典类型', 1, 'top.aameow.web.controller.system.SysDictTypeController.add()', 'POST', 1, 'admin', '众基消防', '/system/dict/type', '127.0.0.1', '内网IP', '{\"createBy\":\"admin\",\"dictName\":\"报告类型\",\"dictType\":\"report_type\",\"params\":{},\"remark\":\"报告类型\",\"status\":\"0\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-13 16:19:32', 17);
INSERT INTO `sys_oper_log` VALUES (152, '字典数据', 1, 'top.aameow.web.controller.system.SysDictDataController.add()', 'POST', 1, 'admin', '众基消防', '/system/dict/data', '127.0.0.1', '内网IP', '{\"createBy\":\"admin\",\"default\":false,\"dictLabel\":\"检测\",\"dictSort\":0,\"dictType\":\"report_type\",\"dictValue\":\"JC\",\"listClass\":\"default\",\"params\":{},\"remark\":\"检测报告\",\"status\":\"0\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-13 16:20:04', 19);
INSERT INTO `sys_oper_log` VALUES (153, '字典数据', 1, 'top.aameow.web.controller.system.SysDictDataController.add()', 'POST', 1, 'admin', '众基消防', '/system/dict/data', '127.0.0.1', '内网IP', '{\"createBy\":\"admin\",\"default\":false,\"dictLabel\":\"评定\",\"dictSort\":1,\"dictType\":\"report_type\",\"dictValue\":\"PD\",\"listClass\":\"default\",\"params\":{},\"remark\":\"评定报告\",\"status\":\"0\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-13 16:21:21', 9);
INSERT INTO `sys_oper_log` VALUES (154, '代码生成', 6, 'top.aameow.generator.controller.GenController.importTableSave()', 'POST', 1, 'admin', '众基消防', '/tool/gen/importTable', '127.0.0.1', '内网IP', '{\"tables\":\"zj_report\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-13 16:21:41', 70);
INSERT INTO `sys_oper_log` VALUES (155, '代码生成', 2, 'top.aameow.generator.controller.GenController.editSave()', 'PUT', 1, 'admin', '众基消防', '/tool/gen', '127.0.0.1', '内网IP', '{\"businessName\":\"report\",\"className\":\"ZjReport\",\"columns\":[{\"capJavaField\":\"Id\",\"columnId\":23,\"columnName\":\"id\",\"columnType\":\"int(11)\",\"createBy\":\"admin\",\"createTime\":\"2025-05-13 16:21:41\",\"dictType\":\"\",\"edit\":false,\"htmlType\":\"input\",\"increment\":true,\"insert\":false,\"isIncrement\":\"1\",\"isInsert\":\"0\",\"isPk\":\"1\",\"isRequired\":\"0\",\"javaField\":\"id\",\"javaType\":\"Long\",\"list\":false,\"params\":{},\"pk\":true,\"query\":false,\"queryType\":\"EQ\",\"required\":false,\"sort\":1,\"superColumn\":false,\"tableId\":3,\"updateBy\":\"\",\"usableColumn\":false},{\"capJavaField\":\"ReportId\",\"columnComment\":\"报告编号\",\"columnId\":24,\"columnName\":\"report_id\",\"columnType\":\"varchar(64)\",\"createBy\":\"admin\",\"createTime\":\"2025-05-13 16:21:41\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"input\",\"increment\":false,\"insert\":true,\"isEdit\":\"1\",\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isList\":\"1\",\"isPk\":\"0\",\"isQuery\":\"1\",\"isRequired\":\"0\",\"javaField\":\"reportId\",\"javaType\":\"String\",\"list\":true,\"params\":{},\"pk\":false,\"query\":true,\"queryType\":\"LIKE\",\"required\":false,\"sort\":2,\"superColumn\":false,\"tableId\":3,\"updateBy\":\"\",\"usableColumn\":false},{\"capJavaField\":\"ReportName\",\"columnComment\":\"名称\",\"columnId\":25,\"columnName\":\"report_name\",\"columnType\":\"varchar(255)\",\"createBy\":\"admin\",\"createTime\":\"2025-05-13 16:21:41\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"input\",\"increment\":false,\"insert\":true,\"isEdit\":\"1\",\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isList\":\"1\",\"isPk\":\"0\",\"isQuery\":\"1\",\"isRequired\":\"1\",\"javaField\":\"reportName\",\"javaType\":\"String\",\"list\":true,\"params\":{},\"pk\":false,\"query\":true,\"queryType\":\"LIKE\",\"required\":true,\"sort\":3,\"superColumn\":false,\"tableId\":3,\"updateBy\":\"\",\"usableColumn\":false},{\"capJavaField\":\"ReportFile\",\"columnComment\":\"报告文件\",\"columnId\":26,\"columnName\":\"report_file\",\"columnType\":\"varchar(255)\",\"createBy\":\"admin\",\"createTime\":\"2025-05-13 16:21:41\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"fileUpload\",\"increment\":false,\"insert\":true,\"isEdit\":\"1\",\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isList\":\"1\",\"isPk\":\"0\",\"isQuery\":\"0\",\"isRequired\":\"1\",\"javaField\"', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-13 16:28:39', 39);
INSERT INTO `sys_oper_log` VALUES (156, '代码生成', 2, 'top.aameow.generator.controller.GenController.editSave()', 'PUT', 1, 'admin', '众基消防', '/tool/gen', '127.0.0.1', '内网IP', '{\"businessName\":\"report\",\"className\":\"ZjReport\",\"columns\":[{\"capJavaField\":\"Id\",\"columnId\":23,\"columnName\":\"id\",\"columnType\":\"int(11)\",\"createBy\":\"admin\",\"createTime\":\"2025-05-13 16:21:41\",\"dictType\":\"\",\"edit\":false,\"htmlType\":\"input\",\"increment\":true,\"insert\":false,\"isIncrement\":\"1\",\"isInsert\":\"0\",\"isPk\":\"1\",\"isRequired\":\"0\",\"javaField\":\"id\",\"javaType\":\"Long\",\"list\":false,\"params\":{},\"pk\":true,\"query\":false,\"queryType\":\"EQ\",\"required\":false,\"sort\":1,\"superColumn\":false,\"tableId\":3,\"updateBy\":\"\",\"updateTime\":\"2025-05-13 16:28:39\",\"usableColumn\":false},{\"capJavaField\":\"ReportId\",\"columnComment\":\"报告编号\",\"columnId\":24,\"columnName\":\"report_id\",\"columnType\":\"varchar(64)\",\"createBy\":\"admin\",\"createTime\":\"2025-05-13 16:21:41\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"input\",\"increment\":false,\"insert\":true,\"isEdit\":\"1\",\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isList\":\"1\",\"isPk\":\"0\",\"isQuery\":\"1\",\"isRequired\":\"0\",\"javaField\":\"reportId\",\"javaType\":\"String\",\"list\":true,\"params\":{},\"pk\":false,\"query\":true,\"queryType\":\"LIKE\",\"required\":false,\"sort\":2,\"superColumn\":false,\"tableId\":3,\"updateBy\":\"\",\"updateTime\":\"2025-05-13 16:28:39\",\"usableColumn\":false},{\"capJavaField\":\"ReportName\",\"columnComment\":\"名称\",\"columnId\":25,\"columnName\":\"report_name\",\"columnType\":\"varchar(255)\",\"createBy\":\"admin\",\"createTime\":\"2025-05-13 16:21:41\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"input\",\"increment\":false,\"insert\":true,\"isEdit\":\"1\",\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isList\":\"1\",\"isPk\":\"0\",\"isQuery\":\"1\",\"isRequired\":\"1\",\"javaField\":\"reportName\",\"javaType\":\"String\",\"list\":true,\"params\":{},\"pk\":false,\"query\":true,\"queryType\":\"LIKE\",\"required\":true,\"sort\":3,\"superColumn\":false,\"tableId\":3,\"updateBy\":\"\",\"updateTime\":\"2025-05-13 16:28:39\",\"usableColumn\":false},{\"capJavaField\":\"ReportFile\",\"columnComment\":\"报告文件\",\"columnId\":26,\"columnName\":\"report_file\",\"columnType\":\"varchar(255)\",\"createBy\":\"admin\",\"createTime\":\"2025-05-13 16:21:41\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"fileUpload\",\"increment\":false,\"insert\":true,\"isEdit', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-13 16:29:35', 34);
INSERT INTO `sys_oper_log` VALUES (157, '代码生成', 8, 'top.aameow.generator.controller.GenController.batchGenCode()', 'GET', 1, 'admin', '众基消防', '/tool/gen/batchGenCode', '127.0.0.1', '内网IP', '{\"tables\":\"zj_report\"}', NULL, 0, NULL, '2025-05-13 16:29:42', 261);
INSERT INTO `sys_oper_log` VALUES (158, '菜单管理', 2, 'top.aameow.web.controller.system.SysMenuController.edit()', 'PUT', 1, 'admin', '众基消防', '/system/menu', '127.0.0.1', '内网IP', '{\"children\":[],\"component\":\"management/report/index\",\"createTime\":\"2025-05-13 16:47:26\",\"icon\":\"documentation\",\"isCache\":\"0\",\"isFrame\":\"1\",\"menuId\":2007,\"menuName\":\"报告管理\",\"menuType\":\"C\",\"orderNum\":1,\"params\":{},\"parentId\":2006,\"path\":\"report\",\"perms\":\"management:report:list\",\"routeName\":\"\",\"status\":\"0\",\"updateBy\":\"admin\",\"visible\":\"0\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-13 16:49:46', 33);
INSERT INTO `sys_oper_log` VALUES (159, '角色管理', 1, 'top.aameow.web.controller.system.SysRoleController.add()', 'POST', 1, 'admin', '众基消防', '/system/role', '127.0.0.1', '内网IP', '{\"admin\":false,\"createBy\":\"admin\",\"deptCheckStrictly\":true,\"deptIds\":[],\"flag\":false,\"menuCheckStrictly\":false,\"menuIds\":[2006,2000,2007],\"params\":{},\"roleId\":3,\"roleKey\":\"tech\",\"roleName\":\"技术人员\",\"roleSort\":3,\"status\":\"0\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-13 16:58:56', 40);
INSERT INTO `sys_oper_log` VALUES (160, '用户管理', 1, 'top.aameow.web.controller.system.SysUserController.add()', 'POST', 1, 'admin', '众基消防', '/system/user', '127.0.0.1', '内网IP', '{\"admin\":false,\"createBy\":\"admin\",\"deptId\":101,\"nickName\":\"小雨\",\"params\":{},\"postIds\":[],\"roleIds\":[3,2],\"sex\":\"1\",\"status\":\"0\",\"userId\":3,\"userName\":\"xiaoyu\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-13 17:01:57', 116);
INSERT INTO `sys_oper_log` VALUES (161, '角色管理', 2, 'top.aameow.web.controller.system.SysRoleController.edit()', 'PUT', 1, 'admin', '众基消防', '/system/role', '127.0.0.1', '内网IP', '{\"admin\":false,\"createTime\":\"2025-05-09 14:44:19\",\"dataScope\":\"2\",\"delFlag\":\"0\",\"deptCheckStrictly\":true,\"flag\":false,\"menuCheckStrictly\":true,\"menuIds\":[],\"params\":{},\"remark\":\"普通角色\",\"roleId\":2,\"roleKey\":\"common\",\"roleName\":\"普通角色\",\"roleSort\":2,\"status\":\"0\",\"updateBy\":\"admin\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-13 17:05:15', 40);
INSERT INTO `sys_oper_log` VALUES (162, '合同管理', 2, 'top.aameow.management.controller.ZjContractController.edit()', 'PUT', 1, 'admin', '众基消防', '/management/contract', '192.168.1.119', '内网IP', '{\"amount\":20000,\"contractFile\":\"/profile/upload/2025/05/15/WB-1_谷麦光电消防设施维护保养_2025-05-01到2026-04-30_20000元(anyone)_20250515101830A002.pdf\",\"contractId\":\"WB-1\",\"contractName\":\"谷麦光电消防设施维护保养\",\"contractType\":0,\"createTime\":\"2025-05-13 06:14:13\",\"endTime\":\"2026-04-30\",\"id\":1,\"manager\":\"anyone\",\"params\":{},\"startTime\":\"2025-05-01\",\"updateTime\":\"2025-05-15 10:18:35\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-15 10:18:35', 115);
INSERT INTO `sys_oper_log` VALUES (163, '角色管理', 2, 'top.aameow.web.controller.system.SysRoleController.edit()', 'PUT', 1, 'admin', '众基消防', '/system/role', '192.168.1.119', '内网IP', '{\"admin\":false,\"createTime\":\"2025-05-13 08:58:56\",\"dataScope\":\"1\",\"delFlag\":\"0\",\"deptCheckStrictly\":true,\"flag\":false,\"menuCheckStrictly\":false,\"menuIds\":[2006,2000,2001,2002,2003,2004,2005,2007,2008,2009,2010,2011,2012],\"params\":{},\"roleId\":3,\"roleKey\":\"tech\",\"roleName\":\"技术人员\",\"roleSort\":3,\"status\":\"0\",\"updateBy\":\"admin\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-15 10:28:51', 94);
INSERT INTO `sys_oper_log` VALUES (164, '合同管理', 2, 'top.aameow.management.controller.ZjContractController.edit()', 'PUT', 1, 'xiaoyu', '管理部门', '/management/contract', '192.168.1.113', '内网IP', '{\"amount\":20000,\"contractFile\":\"/profile/upload/2025/05/15/WB-1_谷麦光电消防设施维护保养_2025-05-01到2026-04-30_20000元(anyone)_20250515101830A002.pdf\",\"contractId\":\"WB-1\",\"contractName\":\"谷麦光电消防设施维护保养\",\"contractType\":0,\"createTime\":\"2025-05-13 06:14:13\",\"endTime\":\"2026-04-30\",\"id\":1,\"manager\":\"郭长亮\",\"params\":{},\"startTime\":\"2025-05-01\",\"updateTime\":\"2025-05-15 10:40:01\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-15 10:40:01', 98);
INSERT INTO `sys_oper_log` VALUES (165, '报告管理', 1, 'top.aameow.management.controller.ZjReportController.add()', 'POST', 1, 'xiaoyu', '管理部门', '/management/report', '192.168.1.113', '内网IP', '{\"createTime\":\"2025-05-15 12:13:52\",\"id\":1,\"params\":{},\"reportFile\":\"/profile/upload/2025/05/15/武汉武铁机辆装备有限公司资产运营分公司信阳站点_20250515121344A003.doc\",\"reportId\":\"ZJJCXY20250514078\",\"reportIsfinal\":\"Y\",\"reportName\":\"武汉武铁机辆装备有限公司资产运营分公司信阳站点\",\"reportType\":\"JC\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-15 12:13:52', 97);
INSERT INTO `sys_oper_log` VALUES (166, '报告管理', 5, 'top.aameow.management.controller.ZjReportController.export()', 'POST', 1, 'xiaoyu', '管理部门', '/management/report/export', '192.168.1.113', '内网IP', '{\"pageSize\":\"10\",\"pageNum\":\"1\"}', NULL, 0, NULL, '2025-05-15 12:16:50', 763);
INSERT INTO `sys_oper_log` VALUES (167, '代码生成', 2, 'top.aameow.generator.controller.GenController.synchDb()', 'GET', 1, 'admin', '众基消防', '/tool/gen/synchDb/zj_report', '192.168.1.119', '内网IP', '{}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-16 01:31:46', 163);
INSERT INTO `sys_oper_log` VALUES (168, '代码生成', 2, 'top.aameow.generator.controller.GenController.editSave()', 'PUT', 1, 'admin', '众基消防', '/tool/gen', '192.168.1.119', '内网IP', '{\"businessName\":\"report\",\"className\":\"ZjReport\",\"columns\":[{\"capJavaField\":\"Id\",\"columnComment\":\"\",\"columnId\":23,\"columnName\":\"id\",\"columnType\":\"int(11)\",\"createBy\":\"admin\",\"createTime\":\"2025-05-13 08:21:41\",\"dictType\":\"\",\"edit\":false,\"htmlType\":\"input\",\"increment\":true,\"insert\":false,\"isIncrement\":\"1\",\"isInsert\":\"0\",\"isPk\":\"1\",\"isRequired\":\"0\",\"javaField\":\"id\",\"javaType\":\"Long\",\"list\":false,\"params\":{},\"pk\":true,\"query\":false,\"queryType\":\"EQ\",\"required\":false,\"sort\":1,\"superColumn\":false,\"tableId\":3,\"updateBy\":\"\",\"updateTime\":\"2025-05-15 17:31:46\",\"usableColumn\":false},{\"capJavaField\":\"ReportId\",\"columnComment\":\"报告编号\",\"columnId\":24,\"columnName\":\"report_id\",\"columnType\":\"varchar(64)\",\"createBy\":\"admin\",\"createTime\":\"2025-05-13 08:21:41\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"input\",\"increment\":false,\"insert\":true,\"isEdit\":\"1\",\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isList\":\"1\",\"isPk\":\"0\",\"isQuery\":\"1\",\"isRequired\":\"0\",\"javaField\":\"reportId\",\"javaType\":\"String\",\"list\":true,\"params\":{},\"pk\":false,\"query\":true,\"queryType\":\"LIKE\",\"required\":false,\"sort\":2,\"superColumn\":false,\"tableId\":3,\"updateBy\":\"\",\"updateTime\":\"2025-05-15 17:31:46\",\"usableColumn\":false},{\"capJavaField\":\"ReportName\",\"columnComment\":\"名称\",\"columnId\":25,\"columnName\":\"report_name\",\"columnType\":\"varchar(255)\",\"createBy\":\"admin\",\"createTime\":\"2025-05-13 08:21:41\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"input\",\"increment\":false,\"insert\":true,\"isEdit\":\"1\",\"isIncrement\":\"0\",\"isInsert\":\"1\",\"isList\":\"1\",\"isPk\":\"0\",\"isQuery\":\"1\",\"isRequired\":\"1\",\"javaField\":\"reportName\",\"javaType\":\"String\",\"list\":true,\"params\":{},\"pk\":false,\"query\":true,\"queryType\":\"LIKE\",\"required\":true,\"sort\":3,\"superColumn\":false,\"tableId\":3,\"updateBy\":\"\",\"updateTime\":\"2025-05-15 17:31:46\",\"usableColumn\":false},{\"capJavaField\":\"ReportFile\",\"columnComment\":\"报告文件\",\"columnId\":26,\"columnName\":\"report_file\",\"columnType\":\"varchar(255)\",\"createBy\":\"admin\",\"createTime\":\"2025-05-13 08:21:41\",\"dictType\":\"\",\"edit\":true,\"htmlType\":\"fileUpload\",\"increment\":false,\"i', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-16 01:33:43', 182);
INSERT INTO `sys_oper_log` VALUES (169, '代码生成', 2, 'top.aameow.generator.controller.GenController.synchDb()', 'GET', 1, 'admin', '众基消防', '/tool/gen/synchDb/zj_report', '192.168.1.119', '内网IP', '{}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-16 01:58:18', 338);
INSERT INTO `sys_oper_log` VALUES (170, '报告管理', 2, 'top.aameow.management.controller.ZjReportController.edit()', 'PUT', 1, 'xiaoyu', '管理部门', '/management/report', '192.168.1.109', '内网IP', '{\"createTime\":\"2025-05-15 12:13:52\",\"id\":1,\"params\":{},\"reportFile\":\"/profile/upload/2025/05/15/武汉武铁机辆装备有限公司资产运营分公司信阳站点_20250515121344A003.doc\",\"reportId\":\"ZJJCXY20250514078\",\"reportIsfinal\":\"Y\",\"reportManager\":\"郭长亮\",\"reportName\":\"武汉武铁机辆装备有限公司资产运营分公司信阳站点\",\"reportType\":\"JC\",\"updateTime\":\"2025-05-16 02:36:25\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-16 02:36:25', 144);
INSERT INTO `sys_oper_log` VALUES (171, '报告管理', 2, 'top.aameow.management.controller.ZjReportController.edit()', 'PUT', 1, 'admin', '众基消防', '/management/report', '192.168.1.119', '内网IP', '{\"createTime\":\"2025-05-15 12:13:52\",\"id\":1,\"params\":{},\"reportArea\":\"10000\",\"reportFile\":\"/profile/upload/2025/05/15/武汉武铁机辆装备有限公司资产运营分公司信阳站点_20250515121344A003.doc\",\"reportId\":\"ZJJCXY20250514078\",\"reportIsfinal\":\"Y\",\"reportManager\":\"郭长亮\",\"reportName\":\"武汉武铁机辆装备有限公司资产运营分公司信阳站点\",\"reportType\":\"JC\",\"updateBy\":\"admin\",\"updateTime\":\"2025-05-16 03:55:25\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-16 03:55:25', 118);
INSERT INTO `sys_oper_log` VALUES (172, '报告管理', 2, 'top.aameow.management.controller.ZjReportController.edit()', 'PUT', 1, 'xiaoyu', '管理部门', '/management/report', '192.168.1.109', '内网IP', '{\"createTime\":\"2025-05-15 12:13:52\",\"id\":1,\"params\":{},\"reportArea\":\"/\",\"reportFile\":\"/profile/upload/2025/05/15/武汉武铁机辆装备有限公司资产运营分公司信阳站点_20250515121344A003.doc\",\"reportId\":\"ZJJCXY20250514078\",\"reportIsfinal\":\"Y\",\"reportManager\":\"郭长亮\",\"reportName\":\"武汉武铁机辆装备有限公司资产运营分公司信阳站点\",\"reportType\":\"JC\",\"updateBy\":\"xiaoyu\",\"updateTime\":\"2025-05-16 03:56:59\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-16 03:56:59', 294);
INSERT INTO `sys_oper_log` VALUES (173, '用户管理', 1, 'top.aameow.web.controller.system.SysUserController.add()', 'POST', 1, 'admin', '众基消防', '/system/user', '192.168.1.119', '内网IP', '{\"admin\":false,\"createBy\":\"admin\",\"nickName\":\"李亚\",\"params\":{},\"postIds\":[4],\"roleIds\":[2,3],\"sex\":\"0\",\"status\":\"0\",\"userId\":4,\"userName\":\"liya\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-16 06:13:08', 223);
INSERT INTO `sys_oper_log` VALUES (174, '角色管理', 1, 'top.aameow.web.controller.system.SysRoleController.add()', 'POST', 1, 'admin', '众基消防', '/system/role', '192.168.1.119', '内网IP', '{\"admin\":false,\"createBy\":\"admin\",\"deptCheckStrictly\":true,\"deptIds\":[],\"flag\":false,\"menuCheckStrictly\":true,\"menuIds\":[2006,2000,2001,2002,2003,2004,2005],\"params\":{},\"roleId\":4,\"roleKey\":\"manager\",\"roleName\":\"管理人员\",\"roleSort\":4,\"status\":\"0\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-16 06:13:40', 176);
INSERT INTO `sys_oper_log` VALUES (175, '角色管理', 2, 'top.aameow.web.controller.system.SysRoleController.edit()', 'PUT', 1, 'admin', '众基消防', '/system/role', '192.168.1.119', '内网IP', '{\"admin\":false,\"createTime\":\"2025-05-13 08:58:56\",\"dataScope\":\"1\",\"delFlag\":\"0\",\"deptCheckStrictly\":true,\"flag\":false,\"menuCheckStrictly\":false,\"menuIds\":[2006,2007,2008,2009,2010,2011,2012],\"params\":{},\"roleId\":3,\"roleKey\":\"tech\",\"roleName\":\"技术人员\",\"roleSort\":3,\"status\":\"0\",\"updateBy\":\"admin\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-16 06:13:50', 194);
INSERT INTO `sys_oper_log` VALUES (176, '用户管理', 2, 'top.aameow.web.controller.system.SysUserController.edit()', 'PUT', 1, 'admin', '众基消防', '/system/user', '192.168.1.119', '内网IP', '{\"admin\":false,\"avatar\":\"\",\"createBy\":\"admin\",\"createTime\":\"2025-05-13 09:01:57\",\"delFlag\":\"0\",\"dept\":{\"ancestors\":\"0,100\",\"children\":[],\"deptId\":101,\"deptName\":\"管理部门\",\"leader\":\"\",\"orderNum\":1,\"params\":{},\"parentId\":100,\"status\":\"0\"},\"deptId\":101,\"email\":\"\",\"loginDate\":\"2025-05-16 03:38:35\",\"loginIp\":\"192.168.1.109\",\"nickName\":\"孙静雨\",\"params\":{},\"phonenumber\":\"\",\"postIds\":[],\"roleIds\":[3,4],\"roles\":[{\"admin\":false,\"dataScope\":\"2\",\"deptCheckStrictly\":false,\"flag\":false,\"menuCheckStrictly\":false,\"params\":{},\"roleId\":2,\"roleKey\":\"common\",\"roleName\":\"普通角色\",\"roleSort\":2,\"status\":\"0\"},{\"admin\":false,\"dataScope\":\"1\",\"deptCheckStrictly\":false,\"flag\":false,\"menuCheckStrictly\":false,\"params\":{},\"roleId\":3,\"roleKey\":\"tech\",\"roleName\":\"技术人员\",\"roleSort\":3,\"status\":\"0\"}],\"sex\":\"1\",\"status\":\"0\",\"updateBy\":\"admin\",\"userId\":3,\"userName\":\"xiaoyu\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-16 06:14:19', 136);
INSERT INTO `sys_oper_log` VALUES (177, '用户管理', 2, 'top.aameow.web.controller.system.SysUserController.edit()', 'PUT', 1, 'admin', '众基消防', '/system/user', '192.168.1.119', '内网IP', '{\"admin\":false,\"avatar\":\"\",\"createBy\":\"admin\",\"createTime\":\"2025-05-15 22:13:08\",\"delFlag\":\"0\",\"deptId\":200,\"email\":\"\",\"loginIp\":\"\",\"nickName\":\"李亚\",\"params\":{},\"phonenumber\":\"\",\"postIds\":[4],\"roleIds\":[3],\"roles\":[{\"admin\":false,\"dataScope\":\"2\",\"deptCheckStrictly\":false,\"flag\":false,\"menuCheckStrictly\":false,\"params\":{},\"roleId\":2,\"roleKey\":\"common\",\"roleName\":\"普通角色\",\"roleSort\":2,\"status\":\"0\"},{\"admin\":false,\"dataScope\":\"1\",\"deptCheckStrictly\":false,\"flag\":false,\"menuCheckStrictly\":false,\"params\":{},\"roleId\":3,\"roleKey\":\"tech\",\"roleName\":\"技术人员\",\"roleSort\":3,\"status\":\"0\"}],\"sex\":\"0\",\"status\":\"0\",\"updateBy\":\"admin\",\"userId\":4,\"userName\":\"liya\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-16 06:14:35', 199);
INSERT INTO `sys_oper_log` VALUES (178, '报告管理', 1, 'top.aameow.management.controller.ZjReportController.add()', 'POST', 1, 'liya', '技术部门', '/management/report', '192.168.1.113', '内网IP', '{\"createBy\":\"liya\",\"createTime\":\"2025-05-16 06:31:35\",\"id\":2,\"params\":{},\"reportArea\":\"6802.55\",\"reportFile\":\"/profile/upload/2025/05/16/小河镇卫生院_(非最终版).doc\",\"reportId\":\"ZJJC2023-1107-116\",\"reportIsfinal\":\"Y\",\"reportManager\":\"刘涛\",\"reportName\":\"小河镇卫生院\",\"reportType\":\"JC\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-16 06:31:36', 265);
INSERT INTO `sys_oper_log` VALUES (179, '用户头像', 2, 'top.aameow.web.controller.system.SysProfileController.avatar()', 'POST', 1, 'xiaoyu', '管理部门', '/system/user/profile/avatar', '192.168.1.109', '内网IP', '', '{\"msg\":\"操作成功\",\"imgUrl\":\"/profile/avatar/2025/05/16/MYXJ_20210102220240291_fast_20250516064204A001.jpg\",\"code\":200}', 0, NULL, '2025-05-16 06:42:05', 103);
INSERT INTO `sys_oper_log` VALUES (180, '用户头像', 2, 'top.aameow.web.controller.system.SysProfileController.avatar()', 'POST', 1, 'xiaoyu', '管理部门', '/system/user/profile/avatar', '192.168.1.109', '内网IP', '', '{\"msg\":\"操作成功\",\"imgUrl\":\"/profile/avatar/2025/05/16/retouch_2021071015591833_20250516064340A002.jpg\",\"code\":200}', 0, NULL, '2025-05-16 06:43:40', 103);
INSERT INTO `sys_oper_log` VALUES (181, '字典数据', 1, 'top.aameow.web.controller.system.SysDictDataController.add()', 'POST', 1, 'admin', '众基消防', '/system/dict/data', '192.168.1.119', '内网IP', '{\"createBy\":\"admin\",\"default\":false,\"dictLabel\":\"评估\",\"dictSort\":3,\"dictType\":\"report_type\",\"dictValue\":\"PG\",\"listClass\":\"default\",\"params\":{},\"status\":\"0\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-16 06:49:11', 188);
INSERT INTO `sys_oper_log` VALUES (182, '字典数据', 2, 'top.aameow.web.controller.system.SysDictDataController.edit()', 'PUT', 1, 'admin', '众基消防', '/system/dict/data', '192.168.1.119', '内网IP', '{\"createBy\":\"admin\",\"createTime\":\"2025-05-15 22:49:10\",\"default\":false,\"dictCode\":107,\"dictLabel\":\"评估\",\"dictSort\":3,\"dictType\":\"report_type\",\"dictValue\":\"PG\",\"isDefault\":\"N\",\"listClass\":\"default\",\"params\":{},\"remark\":\"检测评估\",\"status\":\"0\",\"updateBy\":\"admin\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-16 06:49:24', 261);
INSERT INTO `sys_oper_log` VALUES (183, '报告管理', 2, 'top.aameow.management.controller.ZjReportController.edit()', 'PUT', 1, 'xiaoyu', '管理部门', '/management/report', '192.168.1.109', '内网IP', '{\"createBy\":\"liya\",\"createTime\":\"2025-05-16 06:31:36\",\"id\":2,\"params\":{},\"reportArea\":\"6802.55\",\"reportFile\":\"/profile/upload/2025/05/16/小河镇卫生院_(非最终版).doc\",\"reportId\":\"ZJJCHB20250516080\",\"reportIsfinal\":\"Y\",\"reportManager\":\"刘涛\",\"reportName\":\"小河镇卫生院\",\"reportType\":\"JC\",\"updateBy\":\"xiaoyu\",\"updateTime\":\"2025-05-16 06:51:22\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-16 06:51:23', 120);
INSERT INTO `sys_oper_log` VALUES (184, '报告管理', 2, 'top.aameow.management.controller.ZjReportController.edit()', 'PUT', 1, 'liya', '技术部门', '/management/report', '192.168.1.113', '内网IP', '{\"createBy\":\"liya\",\"createTime\":\"2025-05-16 06:31:36\",\"id\":2,\"params\":{},\"reportArea\":\"6802.55\",\"reportFile\":\"/profile/upload/2025/05/16/小河镇卫生院_(非最终版).doc\",\"reportId\":\"ZJJCHB20250516080\",\"reportIsfinal\":\"N\",\"reportManager\":\"刘涛\",\"reportName\":\"小河镇卫生院\",\"reportType\":\"JC\",\"updateBy\":\"liya\",\"updateTime\":\"2025-05-16 07:01:56\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-16 07:01:56', 101);
INSERT INTO `sys_oper_log` VALUES (185, '报告管理', 1, 'top.aameow.management.controller.ZjReportController.add()', 'POST', 1, 'xiaoyu', '管理部门', '/management/report', '192.168.1.109', '内网IP', '{\"createBy\":\"xiaoyu\",\"createTime\":\"2025-05-16 07:10:09\",\"id\":3,\"params\":{},\"reportArea\":\"673.45\",\"reportFile\":\"/profile/upload/2025/05/16/卫贤镇敬老院转型区域养老服务中心_(非最终版).doc\",\"reportId\":\"ZJJCHB20250514075\",\"reportIsfinal\":\"Y\",\"reportManager\":\"刘涛\",\"reportName\":\"卫贤镇敬老院转型区域养老服务中心\",\"reportType\":\"JC\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-16 07:10:12', 3312);
INSERT INTO `sys_oper_log` VALUES (186, '报告管理', 2, 'top.aameow.management.controller.ZjReportController.edit()', 'PUT', 1, 'admin', '众基消防', '/management/report', '192.168.1.119', '内网IP', '{\"createBy\":\"liya\",\"createTime\":\"2025-05-16 06:31:36\",\"id\":2,\"params\":{},\"reportArea\":\"6802.55\",\"reportFile\":\"/profile/upload/2025/05/16/小河镇卫生院_(非最终版).doc\",\"reportId\":\"ZJJCHB20250516080\",\"reportIsfinal\":\"Y\",\"reportManager\":\"刘涛\",\"reportName\":\"小河镇卫生院\",\"reportType\":\"JC\",\"updateBy\":\"admin\",\"updateTime\":\"2025-05-16 07:33:37\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-16 07:33:37', 77);
INSERT INTO `sys_oper_log` VALUES (187, '用户管理', 1, 'top.aameow.web.controller.system.SysUserController.add()', 'POST', 1, 'admin', '众基消防', '/system/user', '192.168.1.119', '内网IP', '{\"admin\":false,\"createBy\":\"admin\",\"deptId\":200,\"nickName\":\"孙梦杰\",\"params\":{},\"postIds\":[4],\"roleIds\":[3],\"status\":\"0\",\"userId\":5,\"userName\":\"sun\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-16 07:46:13', 353);
INSERT INTO `sys_oper_log` VALUES (188, '用户管理', 1, 'top.aameow.web.controller.system.SysUserController.add()', 'POST', 1, 'admin', '众基消防', '/system/user', '192.168.1.119', '内网IP', '{\"admin\":false,\"createBy\":\"admin\",\"nickName\":\"金燕\",\"params\":{},\"postIds\":[4],\"roleIds\":[4,3],\"status\":\"0\",\"userId\":6,\"userName\":\"jinyan\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-16 07:47:51', 234);
INSERT INTO `sys_oper_log` VALUES (189, '用户管理', 2, 'top.aameow.web.controller.system.SysUserController.edit()', 'PUT', 1, 'admin', '众基消防', '/system/user', '192.168.1.119', '内网IP', '{\"admin\":false,\"avatar\":\"\",\"createBy\":\"admin\",\"createTime\":\"2025-05-15 23:47:51\",\"delFlag\":\"0\",\"deptId\":102,\"email\":\"\",\"loginIp\":\"\",\"nickName\":\"金燕\",\"params\":{},\"phonenumber\":\"\",\"postIds\":[4],\"roleIds\":[3,4],\"roles\":[{\"admin\":false,\"dataScope\":\"1\",\"deptCheckStrictly\":false,\"flag\":false,\"menuCheckStrictly\":false,\"params\":{},\"roleId\":3,\"roleKey\":\"tech\",\"roleName\":\"技术人员\",\"roleSort\":3,\"status\":\"0\"},{\"admin\":false,\"dataScope\":\"1\",\"deptCheckStrictly\":false,\"flag\":false,\"menuCheckStrictly\":false,\"params\":{},\"roleId\":4,\"roleKey\":\"manager\",\"roleName\":\"管理人员\",\"roleSort\":4,\"status\":\"0\"}],\"sex\":\"0\",\"status\":\"0\",\"updateBy\":\"admin\",\"userId\":6,\"userName\":\"jinyan\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-16 07:48:09', 127);
INSERT INTO `sys_oper_log` VALUES (190, '用户管理', 2, 'top.aameow.web.controller.system.SysUserController.edit()', 'PUT', 1, 'admin', '众基消防', '/system/user', '192.168.1.119', '内网IP', '{\"admin\":false,\"avatar\":\"\",\"createBy\":\"admin\",\"createTime\":\"2025-05-15 23:47:51\",\"delFlag\":\"0\",\"dept\":{\"ancestors\":\"0,100\",\"children\":[],\"deptId\":102,\"deptName\":\"财务部门\",\"leader\":\"\",\"orderNum\":2,\"params\":{},\"parentId\":100,\"status\":\"0\"},\"deptId\":102,\"email\":\"\",\"loginIp\":\"\",\"nickName\":\"金燕\",\"params\":{},\"phonenumber\":\"\",\"postIds\":[4],\"roleIds\":[3,4],\"roles\":[{\"admin\":false,\"dataScope\":\"1\",\"deptCheckStrictly\":false,\"flag\":false,\"menuCheckStrictly\":false,\"params\":{},\"roleId\":3,\"roleKey\":\"tech\",\"roleName\":\"技术人员\",\"roleSort\":3,\"status\":\"0\"},{\"admin\":false,\"dataScope\":\"1\",\"deptCheckStrictly\":false,\"flag\":false,\"menuCheckStrictly\":false,\"params\":{},\"roleId\":4,\"roleKey\":\"manager\",\"roleName\":\"管理人员\",\"roleSort\":4,\"status\":\"0\"}],\"sex\":\"1\",\"status\":\"0\",\"updateBy\":\"admin\",\"userId\":6,\"userName\":\"jinyan\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-16 07:48:14', 112);
INSERT INTO `sys_oper_log` VALUES (191, '用户管理', 2, 'top.aameow.web.controller.system.SysUserController.edit()', 'PUT', 1, 'admin', '众基消防', '/system/user', '192.168.1.119', '内网IP', '{\"admin\":false,\"avatar\":\"\",\"createBy\":\"admin\",\"createTime\":\"2025-05-15 23:46:13\",\"delFlag\":\"0\",\"dept\":{\"ancestors\":\"0,100\",\"children\":[],\"deptId\":200,\"deptName\":\"技术部门\",\"orderNum\":3,\"params\":{},\"parentId\":100,\"status\":\"0\"},\"deptId\":200,\"email\":\"\",\"loginDate\":\"2025-05-16 07:49:28\",\"loginIp\":\"192.168.1.119\",\"nickName\":\"孙梦杰\",\"params\":{},\"phonenumber\":\"\",\"postIds\":[4],\"roleIds\":[3,4],\"roles\":[{\"admin\":false,\"dataScope\":\"1\",\"deptCheckStrictly\":false,\"flag\":false,\"menuCheckStrictly\":false,\"params\":{},\"roleId\":3,\"roleKey\":\"tech\",\"roleName\":\"技术人员\",\"roleSort\":3,\"status\":\"0\"}],\"sex\":\"0\",\"status\":\"0\",\"updateBy\":\"admin\",\"userId\":5,\"userName\":\"sun\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-16 07:50:03', 141);
INSERT INTO `sys_oper_log` VALUES (192, '合同管理', 1, 'top.aameow.management.controller.ZjContractController.add()', 'POST', 1, 'sun', '技术部门', '/management/contract', '192.168.1.119', '内网IP', '{\"amount\":8000,\"contractFile\":\"/profile/upload/2025/05/16/WB-2_兰考糖果歌城_2025-05-01到2026-07-31_8000元(韩飞)_20250516075234A001.pdf\",\"contractId\":\"WB-2\",\"contractName\":\"兰考糖果歌城\",\"contractType\":0,\"createBy\":\"孙梦杰\",\"createTime\":\"2025-05-16 07:52:39\",\"endTime\":\"2026-07-31\",\"id\":2,\"manager\":\"韩飞\",\"params\":{},\"startTime\":\"2025-05-01\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-16 07:52:39', 153);
INSERT INTO `sys_oper_log` VALUES (193, '合同管理', 2, 'top.aameow.management.controller.ZjContractController.edit()', 'PUT', 1, 'sun', '技术部门', '/management/contract', '192.168.1.119', '内网IP', '{\"amount\":20000,\"contractFile\":\"/profile/upload/2025/05/15/WB-1_谷麦光电消防设施维护保养_2025-05-01到2026-04-30_20000元(anyone)_20250515101830A002.pdf\",\"contractId\":\"WB-100\",\"contractName\":\"谷麦光电消防设施维护保养\",\"contractType\":0,\"createTime\":\"2025-05-13 06:14:13\",\"endTime\":\"2026-04-30\",\"id\":1,\"manager\":\"郭长亮\",\"params\":{},\"startTime\":\"2025-05-01\",\"updateBy\":\"孙梦杰\",\"updateTime\":\"2025-05-16 08:03:50\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-16 08:03:50', 153);
INSERT INTO `sys_oper_log` VALUES (194, '报告管理', 2, 'top.aameow.management.controller.ZjReportController.edit()', 'PUT', 1, 'sun', '技术部门', '/management/report', '192.168.1.119', '内网IP', '{\"createBy\":\"李亚\",\"createTime\":\"2025-05-16 06:31:36\",\"id\":2,\"params\":{},\"reportArea\":\"6802.55\",\"reportFile\":\"/profile/upload/2025/05/16/小河镇卫生院_(最终版).doc\",\"reportId\":\"ZJJCHB20250516080\",\"reportIsfinal\":\"Y\",\"reportManager\":\"刘涛\",\"reportName\":\"小河镇卫生院\",\"reportType\":\"JC\",\"updateBy\":\"孙梦杰\",\"updateTime\":\"2025-05-16 08:14:54\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-16 08:14:55', 142);
INSERT INTO `sys_oper_log` VALUES (195, '报告管理', 2, 'top.aameow.management.controller.ZjReportController.edit()', 'PUT', 1, 'xiaoyu', '管理部门', '/management/report', '192.168.1.109', '内网IP', '{\"createBy\":\"李亚\",\"createTime\":\"2025-05-16 06:31:36\",\"id\":2,\"params\":{},\"reportArea\":\"6802.55\",\"reportFile\":\"/profile/upload/2025/05/16/小河镇卫生院_(非最终版)_(最终版).doc\",\"reportId\":\"ZJJCHB20250516080\",\"reportIsfinal\":\"Y\",\"reportManager\":\"刘涛\",\"reportName\":\"小河镇卫生院\",\"reportType\":\"JC\",\"updateBy\":\"孙静雨\",\"updateTime\":\"2025-05-16 09:39:55\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-16 09:39:55', 71);
INSERT INTO `sys_oper_log` VALUES (196, '报告管理', 2, 'top.aameow.management.controller.ZjReportController.edit()', 'PUT', 1, 'xiaoyu', '管理部门', '/management/report', '192.168.1.109', '内网IP', '{\"createBy\":\"李亚\",\"createTime\":\"2025-05-16 06:31:36\",\"id\":2,\"params\":{},\"reportArea\":\"6802.55\",\"reportFile\":\"/profile/upload/2025/05/16/小河镇卫生院_(非最终版)_(最终版).doc\",\"reportId\":\"ZJJCHB20250516080\",\"reportIsfinal\":\"N\",\"reportManager\":\"刘涛\",\"reportName\":\"小河镇卫生院\",\"reportType\":\"JC\",\"updateBy\":\"孙静雨\",\"updateTime\":\"2025-05-16 09:39:59\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-16 09:39:59', 76);
INSERT INTO `sys_oper_log` VALUES (197, '报告管理', 2, 'top.aameow.management.controller.ZjReportController.edit()', 'PUT', 1, 'xiaoyu', '管理部门', '/management/report', '192.168.1.109', '内网IP', '{\"createBy\":\"李亚\",\"createTime\":\"2025-05-16 06:31:36\",\"id\":2,\"params\":{},\"reportArea\":\"6802.55\",\"reportFile\":\"/profile/upload/2025/05/16/小河镇卫生院_(非最终版)_(非最终版).doc\",\"reportId\":\"ZJJCHB20250516080\",\"reportIsfinal\":\"N\",\"reportManager\":\"刘涛\",\"reportName\":\"小河镇卫生院\",\"reportType\":\"JC\",\"updateBy\":\"孙静雨\",\"updateTime\":\"2025-05-16 09:46:53\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-16 09:46:53', 66);
INSERT INTO `sys_oper_log` VALUES (198, '合同管理', 1, 'top.aameow.management.controller.ZjContractController.add()', 'POST', 1, 'xiaoyu', '管理部门', '/management/contract', '192.168.1.109', '内网IP', '{\"amount\":5000,\"contractFile\":\"/profile/upload/2025/05/16/ZJJC-2025-022_濮阳市华龙区濮东街道社区卫生服务中心年度检测项目_2025-05-01到2026-04-30_5000元(鲁玉钦)_20250516094904A002.pdf\",\"contractId\":\"ZJJC-2025-022\",\"contractName\":\"濮阳市华龙区濮东街道社区卫生服务中心年度检测项目\",\"contractType\":1,\"createBy\":\"孙静雨\",\"createTime\":\"2025-05-16 09:49:19\",\"endTime\":\"2026-04-30\",\"id\":3,\"manager\":\"鲁玉钦\",\"params\":{},\"startTime\":\"2025-05-01\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-16 09:49:19', 72);
INSERT INTO `sys_oper_log` VALUES (199, '合同管理', 2, 'top.aameow.management.controller.ZjContractController.edit()', 'PUT', 1, 'xiaoyu', '管理部门', '/management/contract', '192.168.1.109', '内网IP', '{\"amount\":8000,\"contractFile\":\"/profile/upload/2025/05/16/WB-2_兰考糖果歌城_2025-05-01到2026-07-31_8000元(韩飞)_20250516075234A001.pdf\",\"contractId\":\"/\",\"contractName\":\"兰考糖果歌城\",\"contractType\":0,\"createBy\":\"孙梦杰\",\"createTime\":\"2025-05-16 07:52:39\",\"endTime\":\"2026-07-31\",\"id\":2,\"manager\":\"韩飞\",\"params\":{},\"startTime\":\"2025-05-01\",\"updateBy\":\"孙静雨\",\"updateTime\":\"2025-05-16 09:49:36\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-16 09:49:36', 95);
INSERT INTO `sys_oper_log` VALUES (200, '用户管理', 1, 'top.aameow.web.controller.system.SysUserController.add()', 'POST', 1, 'admin', '众基消防', '/system/user', '192.168.1.112', '内网IP', '{\"admin\":false,\"createBy\":\"admin\",\"deptId\":201,\"nickName\":\"王宇丰\",\"params\":{},\"postIds\":[],\"roleIds\":[3],\"status\":\"0\",\"userId\":7,\"userName\":\"wangyufeng\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-17 01:03:21', 330);
INSERT INTO `sys_oper_log` VALUES (201, '报告管理', 1, 'top.aameow.management.controller.ZjReportController.add()', 'POST', 1, 'xiaoyu', '管理部门', '/management/report', '192.168.1.113', '内网IP', '{\"createBy\":\"孙静雨\",\"createTime\":\"2025-05-17 01:03:51\",\"id\":4,\"params\":{},\"reportFile\":\"/profile/upload/2025/05/17/小河镇卫生院_(非最终版)_(非最终版).doc,/profile/upload/2025/05/17/可降解材料_(非最终版).zip\",\"reportId\":\"ZJJCHB20250516080\",\"reportIsfinal\":\"N\",\"reportManager\":\"刘涛\",\"reportName\":\"可降解材料\",\"reportType\":\"JC\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-17 01:03:51', 63);
INSERT INTO `sys_oper_log` VALUES (202, '报告管理', 2, 'top.aameow.management.controller.ZjReportController.edit()', 'PUT', 1, 'xiaoyu', '管理部门', '/management/report', '192.168.1.113', '内网IP', '{\"createBy\":\"小雨\",\"createTime\":\"2025-05-16 07:10:10\",\"id\":3,\"params\":{},\"reportArea\":\"673.45\",\"reportFile\":\"/profile/upload/2025/05/16/卫贤镇敬老院转型区域养老服务中心_(非最终版).doc,/profile/upload/2025/05/17/浚县卫贤镇敬老院消防提升项目_(最终版).zip\",\"reportId\":\"ZJJCHB20250514075\",\"reportIsfinal\":\"N\",\"reportManager\":\"刘涛\",\"reportName\":\"卫贤镇敬老院转型区域养老服务中心\",\"reportType\":\"JC\",\"updateBy\":\"孙静雨\",\"updateTime\":\"2025-05-17 01:10:06\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-17 01:10:06', 100);
INSERT INTO `sys_oper_log` VALUES (203, '报告管理', 1, 'top.aameow.management.controller.ZjReportController.add()', 'POST', 1, 'liya', '技术部门', '/management/report', '192.168.1.107', '内网IP', '{\"createBy\":\"李亚\",\"createTime\":\"2025-05-17 06:38:46\",\"id\":5,\"params\":{},\"reportFile\":\"/profile/upload/2025/05/17/宏建建材检测报告_(非最终版).doc\",\"reportIsfinal\":\"N\",\"reportName\":\"浚县宏建建材\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-17 06:38:46', 66);
INSERT INTO `sys_oper_log` VALUES (204, '报告管理', 2, 'top.aameow.management.controller.ZjReportController.edit()', 'PUT', 1, 'liya', '技术部门', '/management/report', '192.168.1.107', '内网IP', '{\"createBy\":\"李亚\",\"createTime\":\"2025-05-17 06:38:46\",\"id\":5,\"params\":{},\"reportFile\":\"/profile/upload/2025/05/17/宏建建材检测报告_(非最终版).doc\",\"reportIsfinal\":\"N\",\"reportManager\":\"刘涛\",\"reportName\":\"浚县宏建建材\",\"reportType\":\"JC\",\"updateBy\":\"李亚\",\"updateTime\":\"2025-05-17 06:39:50\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-17 06:39:50', 90);
INSERT INTO `sys_oper_log` VALUES (205, '报告管理', 2, 'top.aameow.management.controller.ZjReportController.edit()', 'PUT', 1, 'liya', '技术部门', '/management/report', '192.168.1.107', '内网IP', '{\"createBy\":\"李亚\",\"createTime\":\"2025-05-16 06:31:36\",\"id\":2,\"params\":{},\"reportArea\":\"6802.55\",\"reportFile\":\"/profile/upload/2025/05/17/小河镇卫生院_(非最终版)_(非最终版)_(非最终版).doc\",\"reportId\":\"ZJJCHB20250516080\",\"reportIsfinal\":\"N\",\"reportManager\":\"刘涛\",\"reportName\":\"小河镇卫生院\",\"reportType\":\"JC\",\"updateBy\":\"李亚\",\"updateTime\":\"2025-05-17 06:41:04\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-17 06:41:04', 87);
INSERT INTO `sys_oper_log` VALUES (206, '参数管理', 2, 'top.aameow.web.controller.system.SysConfigController.edit()', 'PUT', 1, 'admin', '众基消防', '/system/config', '192.168.1.112', '内网IP', '{\"configId\":4,\"configKey\":\"sys.account.captchaEnabled\",\"configName\":\"账号自助-验证码开关\",\"configType\":\"N\",\"configValue\":\"true\",\"createBy\":\"admin\",\"createTime\":\"2025-05-09 06:44:19\",\"params\":{},\"remark\":\"是否开启验证码功能（true开启，false关闭）\",\"updateBy\":\"admin\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-19 01:07:22', 170);
INSERT INTO `sys_oper_log` VALUES (207, '参数管理', 2, 'top.aameow.web.controller.system.SysConfigController.edit()', 'PUT', 1, 'admin', '众基消防', '/system/config', '192.168.1.112', '内网IP', '{\"configId\":5,\"configKey\":\"sys.account.registerUser\",\"configName\":\"账号自助-是否开启用户注册功能\",\"configType\":\"Y\",\"configValue\":\"false\",\"createBy\":\"admin\",\"createTime\":\"2025-05-09 06:44:19\",\"params\":{},\"remark\":\"是否开启注册用户功能（true开启，false关闭）\",\"updateBy\":\"admin\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-19 01:07:28', 96);
INSERT INTO `sys_oper_log` VALUES (208, '参数管理', 2, 'top.aameow.web.controller.system.SysConfigController.edit()', 'PUT', 1, 'admin', '众基消防', '/system/config', '192.168.1.112', '内网IP', '{\"configId\":4,\"configKey\":\"sys.account.captchaEnabled\",\"configName\":\"账号自助-验证码开关\",\"configType\":\"Y\",\"configValue\":\"true\",\"createBy\":\"admin\",\"createTime\":\"2025-05-09 06:44:19\",\"params\":{},\"remark\":\"是否开启验证码功能（true开启，false关闭）\",\"updateBy\":\"admin\",\"updateTime\":\"2025-05-18 17:07:22\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-19 01:07:36', 128);
INSERT INTO `sys_oper_log` VALUES (209, '参数管理', 2, 'top.aameow.web.controller.system.SysConfigController.edit()', 'PUT', 1, 'admin', '众基消防', '/system/config', '192.168.1.112', '内网IP', '{\"configId\":5,\"configKey\":\"sys.account.registerUser\",\"configName\":\"账号自助-是否开启用户注册功能\",\"configType\":\"Y\",\"configValue\":\"true\",\"createBy\":\"admin\",\"createTime\":\"2025-05-09 06:44:19\",\"params\":{},\"remark\":\"是否开启注册用户功能（true开启，false关闭）\",\"updateBy\":\"admin\",\"updateTime\":\"2025-05-18 17:07:28\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-19 01:07:41', 89);
INSERT INTO `sys_oper_log` VALUES (210, '参数管理', 2, 'top.aameow.web.controller.system.SysConfigController.edit()', 'PUT', 1, 'admin', '众基消防', '/system/config', '192.168.1.112', '内网IP', '{\"configId\":4,\"configKey\":\"sys.account.captchaEnabled\",\"configName\":\"账号自助-验证码开关\",\"configType\":\"Y\",\"configValue\":\"false\",\"createBy\":\"admin\",\"createTime\":\"2025-05-09 06:44:19\",\"params\":{},\"remark\":\"是否开启验证码功能（true开启，false关闭）\",\"updateBy\":\"admin\",\"updateTime\":\"2025-05-18 17:07:36\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-19 01:07:46', 102);
INSERT INTO `sys_oper_log` VALUES (211, '参数管理', 9, 'top.aameow.web.controller.system.SysConfigController.refreshCache()', 'DELETE', 1, 'admin', '众基消防', '/system/config/refreshCache', '192.168.1.112', '内网IP', '', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-19 01:08:46', 42);
INSERT INTO `sys_oper_log` VALUES (212, '用户管理', 1, 'top.aameow.web.controller.system.SysUserController.add()', 'POST', 1, 'admin', '众基消防', '/system/user', '192.168.1.112', '内网IP', '{\"admin\":false,\"createBy\":\"admin\",\"nickName\":\"刘晓苏\",\"params\":{},\"postIds\":[],\"roleIds\":[3],\"status\":\"0\",\"userId\":8,\"userName\":\"liuxiaosu\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-19 01:09:23', 332);
INSERT INTO `sys_oper_log` VALUES (213, '用户管理', 1, 'top.aameow.web.controller.system.SysUserController.add()', 'POST', 1, 'admin', '众基消防', '/system/user', '192.168.1.112', '内网IP', '{\"admin\":false,\"createBy\":\"admin\",\"nickName\":\"郭沙沙\",\"params\":{},\"postIds\":[],\"roleIds\":[3],\"status\":\"0\",\"userId\":9,\"userName\":\"guoshasha\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-19 01:09:46', 206);
INSERT INTO `sys_oper_log` VALUES (214, '菜单管理', 2, 'top.aameow.web.controller.system.SysMenuController.edit()', 'PUT', 1, 'admin', '众基消防', '/system/menu', '192.168.1.112', '内网IP', '{\"children\":[],\"component\":\"management/contract/index\",\"createTime\":\"2025-05-09 08:39:36\",\"icon\":\"documentation\",\"isCache\":\"0\",\"isFrame\":\"1\",\"menuId\":2000,\"menuName\":\"合同管理\",\"menuType\":\"C\",\"orderNum\":5,\"params\":{},\"parentId\":0,\"path\":\"contract\",\"perms\":\"management:contract:list\",\"routeName\":\"\",\"status\":\"0\",\"updateBy\":\"admin\",\"visible\":\"0\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-19 01:10:32', 121);
INSERT INTO `sys_oper_log` VALUES (215, '菜单管理', 2, 'top.aameow.web.controller.system.SysMenuController.edit()', 'PUT', 1, 'admin', '众基消防', '/system/menu', '192.168.1.112', '内网IP', '{\"children\":[],\"component\":\"management/report/index\",\"createTime\":\"2025-05-13 08:47:26\",\"icon\":\"documentation\",\"isCache\":\"0\",\"isFrame\":\"1\",\"menuId\":2007,\"menuName\":\"报告管理\",\"menuType\":\"C\",\"orderNum\":6,\"params\":{},\"parentId\":0,\"path\":\"report\",\"perms\":\"management:report:list\",\"routeName\":\"\",\"status\":\"0\",\"updateBy\":\"admin\",\"visible\":\"0\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-19 01:10:40', 112);
INSERT INTO `sys_oper_log` VALUES (216, '菜单管理', 3, 'top.aameow.web.controller.system.SysMenuController.remove()', 'DELETE', 1, 'admin', '众基消防', '/system/menu/2006', '192.168.1.112', '内网IP', '2006', '{\"msg\":\"菜单已分配,不允许删除\",\"code\":601}', 0, NULL, '2025-05-19 01:10:45', 16);
INSERT INTO `sys_oper_log` VALUES (217, '角色管理', 2, 'top.aameow.web.controller.system.SysRoleController.edit()', 'PUT', 1, 'admin', '众基消防', '/system/role', '192.168.1.112', '内网IP', '{\"admin\":false,\"createTime\":\"2025-05-13 08:58:56\",\"dataScope\":\"1\",\"delFlag\":\"0\",\"deptCheckStrictly\":true,\"flag\":false,\"menuCheckStrictly\":false,\"menuIds\":[2007,2008,2009,2010,2011,2012],\"params\":{},\"roleId\":3,\"roleKey\":\"tech\",\"roleName\":\"技术人员\",\"roleSort\":3,\"status\":\"0\",\"updateBy\":\"admin\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-19 01:11:05', 129);
INSERT INTO `sys_oper_log` VALUES (218, '角色管理', 2, 'top.aameow.web.controller.system.SysRoleController.edit()', 'PUT', 1, 'admin', '众基消防', '/system/role', '192.168.1.112', '内网IP', '{\"admin\":false,\"createTime\":\"2025-05-15 22:13:40\",\"dataScope\":\"1\",\"delFlag\":\"0\",\"deptCheckStrictly\":true,\"flag\":false,\"menuCheckStrictly\":true,\"menuIds\":[2000,2001,2002,2003,2004,2005],\"params\":{},\"roleId\":4,\"roleKey\":\"manager\",\"roleName\":\"管理人员\",\"roleSort\":4,\"status\":\"0\",\"updateBy\":\"admin\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-19 01:11:15', 174);
INSERT INTO `sys_oper_log` VALUES (219, '菜单管理', 3, 'top.aameow.web.controller.system.SysMenuController.remove()', 'DELETE', 1, 'admin', '众基消防', '/system/menu/2006', '192.168.1.112', '内网IP', '2006', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-19 01:11:20', 158);
INSERT INTO `sys_oper_log` VALUES (220, '报告管理', 2, 'top.aameow.management.controller.ZjReportController.edit()', 'PUT', 1, 'xiaoyu', '管理部门', '/management/report', '192.168.1.107', '内网IP', '{\"createBy\":\"小雨\",\"createTime\":\"2025-05-16 07:10:10\",\"id\":3,\"params\":{},\"reportArea\":\"673.45\",\"reportFile\":\"/profile/upload/2025/05/16/卫贤镇敬老院转型区域养老服务中心_(非最终版).doc,/profile/upload/2025/05/17/浚县卫贤镇敬老院消防提升项目_(最终版).zip\",\"reportId\":\"ZJJCHB20250514075\",\"reportIsfinal\":\"Y\",\"reportManager\":\"刘涛\",\"reportName\":\"卫贤镇敬老院转型区域养老服务中心\",\"reportType\":\"JC\",\"updateBy\":\"孙静雨\",\"updateTime\":\"2025-05-19 01:16:58\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-19 01:16:58', 113);
INSERT INTO `sys_oper_log` VALUES (221, '报告管理', 2, 'top.aameow.management.controller.ZjReportController.edit()', 'PUT', 1, 'xiaoyu', '管理部门', '/management/report', '192.168.1.107', '内网IP', '{\"createBy\":\"李亚\",\"createTime\":\"2025-05-16 06:31:36\",\"id\":2,\"params\":{},\"reportArea\":\"6802.55\",\"reportFile\":\"/profile/upload/2025/05/17/小河镇卫生院_(非最终版)_(非最终版)_(非最终版).doc\",\"reportId\":\"ZJJCHB20250516081\",\"reportIsfinal\":\"N\",\"reportManager\":\"刘涛\",\"reportName\":\"小河镇卫生院\",\"reportType\":\"JC\",\"updateBy\":\"孙静雨\",\"updateTime\":\"2025-05-19 01:23:02\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-19 01:23:02', 153);
INSERT INTO `sys_oper_log` VALUES (222, '上传报告', 0, 'top.aameow.management.controller.ZjReportController.uploadFile()', 'POST', 1, 'liuxiaosu', NULL, '/management/report/upload', '192.168.1.116', '内网IP', '{\"isFinal\":\"false\"}', '{\"msg\":\"操作成功\",\"fileName\":\"/profile/upload/2025/05/19/浚县小河镇卫生院灾后恢复重建项目(病房楼)_(非最终版)_20250519093141.doc\",\"code\":200,\"newFileName\":\"浚县小河镇卫生院灾后恢复重建项目(病房楼)_(非最终版)_20250519093141.doc\",\"url\":\"http://192.168.1.115/profile/upload/2025/05/19/浚县小河镇卫生院灾后恢复重建项目(病房楼)_(非最终版)_20250519093141.doc\",\"originalFilename\":\"浚县小河镇卫生院灾后恢复重建项目(病房楼).doc\"}', 0, NULL, '2025-05-19 09:31:41', 281);
INSERT INTO `sys_oper_log` VALUES (223, '报告管理', 2, 'top.aameow.management.controller.ZjReportController.edit()', 'PUT', 1, 'liuxiaosu', NULL, '/management/report', '192.168.1.116', '内网IP', '{\"createBy\":\"李亚\",\"createTime\":\"2025-05-16 06:31:36\",\"id\":2,\"params\":{},\"reportArea\":\"6802.55\",\"reportFile\":\"/profile/upload/2025/05/19/浚县小河镇卫生院灾后恢复重建项目(病房楼)_(非最终版)_20250519093141.doc\",\"reportId\":\"ZJJCHB20250516081\",\"reportIsfinal\":\"N\",\"reportManager\":\"刘涛\",\"reportName\":\"小河镇卫生院\",\"reportType\":\"JC\",\"updateBy\":\"刘晓苏\",\"updateTime\":\"2025-05-19 09:32:01\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-19 09:32:01', 226);
INSERT INTO `sys_oper_log` VALUES (224, '报告管理', 2, 'top.aameow.management.controller.ZjReportController.edit()', 'PUT', 1, 'liuxiaosu', NULL, '/management/report', '192.168.1.116', '内网IP', '{\"createBy\":\"李亚\",\"createTime\":\"2025-05-16 06:31:36\",\"id\":2,\"params\":{},\"reportArea\":\"6802.55\",\"reportFile\":\"/profile/upload/2025/05/19/浚县小河镇卫生院灾后恢复重建项目(病房楼)_(非最终版)_20250519093141.doc\",\"reportId\":\"ZJJCHB20250516081\",\"reportIsfinal\":\"N\",\"reportManager\":\"刘涛\",\"reportName\":\"小河镇卫生院\",\"reportType\":\"JC\",\"updateBy\":\"刘晓苏\",\"updateTime\":\"2025-05-19 09:33:19\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-19 09:33:19', 107);
INSERT INTO `sys_oper_log` VALUES (225, '上传报告', 0, 'top.aameow.management.controller.ZjReportController.uploadFile()', 'POST', 1, 'liuxiaosu', NULL, '/management/report/upload', '192.168.1.116', '内网IP', '{\"isFinal\":\"false\"}', '{\"msg\":\"操作成功\",\"fileName\":\"/profile/upload/2025/05/19/宏建建材检测报告_(非最终版)_20250519095709.doc\",\"code\":200,\"newFileName\":\"宏建建材检测报告_(非最终版)_20250519095709.doc\",\"url\":\"http://192.168.1.115/profile/upload/2025/05/19/宏建建材检测报告_(非最终版)_20250519095709.doc\",\"originalFilename\":\"宏建建材检测报告_(非最终版).doc\"}', 0, NULL, '2025-05-19 09:57:09', 8);
INSERT INTO `sys_oper_log` VALUES (226, '报告管理', 2, 'top.aameow.management.controller.ZjReportController.edit()', 'PUT', 1, 'liuxiaosu', NULL, '/management/report', '192.168.1.116', '内网IP', '{\"createBy\":\"李亚\",\"createTime\":\"2025-05-17 06:38:46\",\"id\":5,\"params\":{},\"reportFile\":\"/profile/upload/2025/05/17/宏建建材检测报告_(非最终版).doc,/profile/upload/2025/05/19/宏建建材检测报告_(非最终版)_20250519095709.doc\",\"reportIsfinal\":\"N\",\"reportManager\":\"刘涛\",\"reportName\":\"浚县宏建建材\",\"reportType\":\"JC\",\"updateBy\":\"刘晓苏\",\"updateTime\":\"2025-05-19 09:57:39\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-19 09:57:39', 77);
INSERT INTO `sys_oper_log` VALUES (227, '上传报告', 0, 'top.aameow.management.controller.ZjReportController.uploadFile()', 'POST', 1, 'guoshasha', NULL, '/management/report/upload', '192.168.1.123', '内网IP', '{\"isFinal\":\"false\"}', '{\"msg\":\"操作成功\",\"fileName\":\"/profile/upload/2025/05/19/可降解材料(非最终版) (2)_(非最终版)_20250519095823.doc\",\"code\":200,\"newFileName\":\"可降解材料(非最终版) (2)_(非最终版)_20250519095823.doc\",\"url\":\"http://192.168.1.115/profile/upload/2025/05/19/可降解材料(非最终版) (2)_(非最终版)_20250519095823.doc\",\"originalFilename\":\"可降解材料(非最终版) (2).doc\"}', 0, NULL, '2025-05-19 09:58:23', 44);
INSERT INTO `sys_oper_log` VALUES (228, '报告管理', 2, 'top.aameow.management.controller.ZjReportController.edit()', 'PUT', 1, 'guoshasha', NULL, '/management/report', '192.168.1.123', '内网IP', '{\"createBy\":\"孙静雨\",\"createTime\":\"2025-05-17 01:03:52\",\"id\":4,\"params\":{},\"reportAttach\":\"/profile/upload/2025/05/17/可降解材料_(非最终版).zip\",\"reportFile\":\"/profile/upload/2025/05/17/小河镇卫生院_(非最终版)_(非最终版).doc,/profile/upload/2025/05/19/可降解材料(非最终版) (2)_(非最终版)_20250519095823.doc\",\"reportId\":\"ZJJCHB20250516080\",\"reportIsfinal\":\"N\",\"reportManager\":\"刘涛\",\"reportName\":\"可降解材料\",\"reportType\":\"JC\",\"updateBy\":\"郭沙沙\",\"updateTime\":\"2025-05-19 09:59:57\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-19 09:59:57', 183);
INSERT INTO `sys_oper_log` VALUES (229, '合同管理', 1, 'top.aameow.management.controller.ZjContractController.add()', 'POST', 1, 'sun', '技术部门', '/management/contract', '192.168.1.102', '内网IP', '{\"amount\":12000,\"contractFile\":\"/profile/upload/2025/05/20/ZJWB-2025-105_世纪华联超市维护保养_2025-05-01到2026-04-30_12000元(张功品)_20250520012909A001.pdf\",\"contractId\":\"ZJWB-2025-105\",\"contractName\":\"世纪华联超市维护保养\",\"contractType\":0,\"createBy\":\"孙梦杰\",\"createTime\":\"2025-05-20 01:29:17\",\"endTime\":\"2026-04-30\",\"id\":4,\"manager\":\"张功品\",\"params\":{},\"startTime\":\"2025-05-01\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-20 01:29:17', 116);
INSERT INTO `sys_oper_log` VALUES (230, '报告管理', 2, 'top.aameow.management.controller.ZjReportController.edit()', 'PUT', 1, 'sun', '技术部门', '/management/report', '192.168.1.102', '内网IP', '{\"createBy\":\"李亚\",\"createTime\":\"2025-05-17 06:38:46\",\"id\":5,\"params\":{},\"reportFile\":\"/profile/upload/2025/05/20/宏建建材检测报告_(非最终版)_20250519095709_20250520041957A001.doc\",\"reportIsfinal\":\"N\",\"reportManager\":\"刘涛\",\"reportName\":\"浚县宏建建材\",\"reportType\":\"JC\",\"updateBy\":\"孙梦杰\",\"updateTime\":\"2025-05-20 04:20:01\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-20 04:20:01', 130);
INSERT INTO `sys_oper_log` VALUES (231, '报告管理', 1, 'top.aameow.management.controller.ZjReportController.add()', 'POST', 1, 'xiaoyu', '管理部门', '/management/report', '192.168.1.106', '内网IP', '{\"createBy\":\"孙静雨\",\"createTime\":\"2025-05-21 01:15:44\",\"id\":6,\"params\":{},\"reportArea\":\"10761.3\",\"reportFile\":\"/profile/upload/2025/05/21/白云商务服务中心检测报告_20250521011541A002.doc\",\"reportId\":\"ZJJCZMD20250429021\",\"reportIsfinal\":\"Y\",\"reportManager\":\"董耀东\",\"reportName\":\"西平县白云商务服务中心\",\"reportType\":\"JC\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-21 01:15:44', 100);
INSERT INTO `sys_oper_log` VALUES (232, '合同管理', 1, 'top.aameow.management.controller.ZjContractController.add()', 'POST', 1, 'sun', '技术部门', '/management/contract', '192.168.1.104', '内网IP', '{\"amount\":10000,\"contractFile\":\"/profile/upload/2025/05/21/ZJWB-2025-054_宣化镇敬老院微型消防设施维护保养_2025-05-12到2026-05-11_10000元(不知道)_20250521020608A003.pdf\",\"contractId\":\"ZJWB-2025-054\",\"contractName\":\"宣化镇敬老院微型消防设施维护保养\",\"contractType\":0,\"createBy\":\"孙梦杰\",\"createTime\":\"2025-05-21 02:06:10\",\"endTime\":\"2026-05-11\",\"id\":5,\"manager\":\"不知道\",\"params\":{},\"startTime\":\"2025-05-12\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-21 02:06:10', 129);
INSERT INTO `sys_oper_log` VALUES (233, '报告管理', 2, 'top.aameow.management.controller.ZjReportController.edit()', 'PUT', 1, 'guoshasha', NULL, '/management/report', '192.168.1.123', '内网IP', '{\"createBy\":\"小雨\",\"createTime\":\"2025-05-16 07:10:10\",\"id\":3,\"params\":{},\"reportArea\":\"673.45\",\"reportAttach\":\"/profile/upload/2025/05/17/浚县卫贤镇敬老院消防提升项目_(最终版).zip\",\"reportFile\":\"/profile/upload/2025/05/21/卫贤镇敬老院转型区域养老服务中心_(最终版)_20250521071547A004.doc\",\"reportId\":\"ZJJCHB20250514075\",\"reportIsfinal\":\"Y\",\"reportManager\":\"刘涛\",\"reportName\":\"卫贤镇敬老院转型区域养老服务中心\",\"reportType\":\"JC\",\"updateBy\":\"郭沙沙\",\"updateTime\":\"2025-05-21 07:16:02\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-21 07:16:02', 126);
INSERT INTO `sys_oper_log` VALUES (234, '报告管理', 2, 'top.aameow.management.controller.ZjReportController.edit()', 'PUT', 1, 'guoshasha', NULL, '/management/report', '192.168.1.123', '内网IP', '{\"createBy\":\"孙静雨\",\"createTime\":\"2025-05-21 01:15:44\",\"id\":6,\"params\":{},\"reportArea\":\"10761.3\",\"reportAttach\":\"/profile/upload/2025/05/21/白云商务服务中心_20250521072006A005.zip\",\"reportFile\":\"/profile/upload/2025/05/21/白云商务服务中心检测报告_20250521011541A002_20250521072151A006.doc\",\"reportId\":\"ZJJCZMD20250429021\",\"reportIsfinal\":\"Y\",\"reportManager\":\"董耀东\",\"reportName\":\"西平县白云商务服务中心\",\"reportType\":\"JC\",\"updateBy\":\"郭沙沙\",\"updateTime\":\"2025-05-21 07:22:01\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-21 07:22:01', 90);
INSERT INTO `sys_oper_log` VALUES (235, '用户头像', 2, 'top.aameow.web.controller.system.SysProfileController.avatar()', 'POST', 1, 'xiaoyu', '管理部门', '/system/user/profile/avatar', '192.168.1.106', '内网IP', '', '{\"msg\":\"操作成功\",\"imgUrl\":\"/profile/avatar/2025/05/21/IMG_20201121_232807_20250521090904A007.jpg\",\"code\":200}', 0, NULL, '2025-05-21 09:09:04', 86);
INSERT INTO `sys_oper_log` VALUES (236, '合同管理', 2, 'top.aameow.management.controller.ZjContractController.edit()', 'PUT', 1, 'sun', '技术部门', '/management/contract', '192.168.1.106', '内网IP', '{\"amount\":10000,\"contractFile\":\"/profile/upload/2025/05/22/ZJWB-2025-054_宣化镇敬老院微型消防设施维护保养_2025-05-12到2026-05-11_10000元(不知道)_20250522064003A008.pdf\",\"contractId\":\"ZJWB-2025-054\",\"contractName\":\"宣化镇敬老院微型消防设施维护保养\",\"contractType\":0,\"createBy\":\"孙梦杰\",\"createTime\":\"2025-05-21 02:06:10\",\"endTime\":\"2026-05-11\",\"id\":5,\"manager\":\"不知道\",\"params\":{},\"startTime\":\"2025-05-12\",\"updateBy\":\"孙梦杰\",\"updateTime\":\"2025-05-22 06:40:06\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-22 06:40:06', 86);
INSERT INTO `sys_oper_log` VALUES (237, '合同管理', 1, 'top.aameow.management.controller.ZjContractController.add()', 'POST', 1, 'sun', '技术部门', '/management/contract', '192.168.1.106', '内网IP', '{\"amount\":23845,\"contractFile\":\"/profile/upload/2025/05/22/ZJWB-2025-117_西平县恒康亿鑫养老院维护保养_2025-05-01到2026-04-30_23845元(张功品)_20250522064124A009.pdf\",\"contractId\":\"ZJWB-2025-117\",\"contractName\":\"西平县恒康亿鑫养老院维护保养\",\"contractType\":0,\"createBy\":\"孙梦杰\",\"createTime\":\"2025-05-22 06:41:25\",\"endTime\":\"2026-04-30\",\"id\":6,\"manager\":\"张功品\",\"params\":{},\"startTime\":\"2025-05-01\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-22 06:41:25', 73);
INSERT INTO `sys_oper_log` VALUES (238, '合同管理', 1, 'top.aameow.management.controller.ZjContractController.add()', 'POST', 1, 'sun', '技术部门', '/management/contract', '192.168.1.106', '内网IP', '{\"amount\":15450,\"contractFile\":\"/profile/upload/2025/05/22/ZJWB-2025-116_西平县社会福利中心维护保养_2025-05-09到2026-05-08_15450元(张功品)_20250522064329A010.pdf\",\"contractId\":\"ZJWB-2025-116\",\"contractName\":\"西平县社会福利中心维护保养\",\"contractType\":0,\"createBy\":\"孙梦杰\",\"createTime\":\"2025-05-22 06:43:31\",\"endTime\":\"2026-05-08\",\"id\":7,\"manager\":\"张功品\",\"params\":{},\"startTime\":\"2025-05-09\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-22 06:43:31', 85);
INSERT INTO `sys_oper_log` VALUES (239, '合同管理', 2, 'top.aameow.management.controller.ZjContractController.edit()', 'PUT', 1, 'sun', '技术部门', '/management/contract', '192.168.1.106', '内网IP', '{\"amount\":15450,\"contractFile\":\"/profile/upload/2025/05/22/ZJWB-2025-116_西平县社会福利中心维护保养_2025-05-09到2026-05-08_15450元(张功品)_20250522064401A011.pdf\",\"contractId\":\"ZJWB-2025-116\",\"contractName\":\"西平县社会福利中心维护保养\",\"contractType\":0,\"createBy\":\"孙梦杰\",\"createTime\":\"2025-05-22 06:43:31\",\"endTime\":\"2026-05-08\",\"id\":7,\"manager\":\"张功品\",\"params\":{},\"startTime\":\"2025-05-09\",\"updateBy\":\"孙梦杰\",\"updateTime\":\"2025-05-22 06:44:03\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-22 06:44:03', 178);
INSERT INTO `sys_oper_log` VALUES (240, '合同管理', 2, 'top.aameow.management.controller.ZjContractController.edit()', 'PUT', 1, 'sun', '技术部门', '/management/contract', '192.168.1.106', '内网IP', '{\"amount\":10000,\"contractFile\":\"/profile/upload/2025/05/22/ZJWB-2025-054_宣化镇敬老院微型消防设施维护保养_2025-05-12到2026-05-11_10000元(不知道)_20250522064003A008.pdf\",\"contractId\":\"ZJWB-2025-054\",\"contractName\":\"宣化镇敬老院微型消防设施维护保养\",\"contractType\":0,\"createBy\":\"孙梦杰\",\"createTime\":\"2025-05-21 02:06:10\",\"endTime\":\"2026-05-11\",\"id\":5,\"manager\":\"田洋洋\",\"params\":{},\"startTime\":\"2025-05-12\",\"updateBy\":\"孙梦杰\",\"updateTime\":\"2025-05-22 06:56:56\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-22 06:56:56', 75);
INSERT INTO `sys_oper_log` VALUES (241, '报告管理', 2, 'top.aameow.management.controller.ZjReportController.edit()', 'PUT', 1, 'guoshasha', NULL, '/management/report', '192.168.1.123', '内网IP', '{\"createBy\":\"孙静雨\",\"createTime\":\"2025-05-21 01:15:44\",\"id\":6,\"params\":{},\"reportArea\":\"10761.3\",\"reportAttach\":\"/profile/upload/2025/05/21/白云商务服务中心_20250521072006A005.zip\",\"reportFile\":\"/profile/upload/2025/05/22/白云商务服务中心检测报告_20250522105947A013.doc\",\"reportId\":\"ZJJCZMD20250429021\",\"reportIsfinal\":\"Y\",\"reportManager\":\"董耀东\",\"reportName\":\"西平县白云商务服务中心\",\"reportType\":\"JC\",\"updateBy\":\"郭沙沙\",\"updateTime\":\"2025-05-22 10:59:51\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-22 10:59:51', 163);
INSERT INTO `sys_oper_log` VALUES (242, '报告管理', 2, 'top.aameow.management.controller.ZjReportController.edit()', 'PUT', 1, 'liuxiaosu', NULL, '/management/report', '192.168.1.113', '内网IP', '{\"createBy\":\"李亚\",\"createTime\":\"2025-05-17 06:38:46\",\"id\":5,\"params\":{},\"reportFile\":\"/profile/upload/2025/05/24/宏建建材检测报告_(非最终版)_20250524022216A014.doc\",\"reportIsfinal\":\"N\",\"reportManager\":\"刘涛\",\"reportName\":\"浚县宏建建材\",\"reportType\":\"JC\",\"updateBy\":\"刘晓苏\",\"updateTime\":\"2025-05-24 02:22:24\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-24 02:22:24', 68);
INSERT INTO `sys_oper_log` VALUES (243, '报告管理', 1, 'top.aameow.management.controller.ZjReportController.add()', 'POST', 1, 'liuxiaosu', NULL, '/management/report', '192.168.1.113', '内网IP', '{\"createBy\":\"刘晓苏\",\"createTime\":\"2025-05-24 07:22:14\",\"id\":7,\"params\":{},\"reportArea\":\"960㎡\",\"reportFile\":\"/profile/upload/2025/05/24/平顶山市高新区遵化镇敬老院消防设施检测报告_20250524072212A015.doc\",\"reportId\":\"ZJJCPDS20250523083\",\"reportIsfinal\":\"Y\",\"reportName\":\"平顶山市高新区遵化镇敬老院消防设施检测项目（电系统））\",\"reportType\":\"JC\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-24 07:22:14', 246);
INSERT INTO `sys_oper_log` VALUES (244, '报告管理', 1, 'top.aameow.management.controller.ZjReportController.add()', 'POST', 1, 'liuxiaosu', NULL, '/management/report', '192.168.1.113', '内网IP', '{\"createBy\":\"刘晓苏\",\"createTime\":\"2025-05-24 07:23:28\",\"id\":8,\"params\":{},\"reportArea\":\"960㎡\",\"reportFile\":\"/profile/upload/2025/05/24/平顶山市高新区遵化镇敬老院消防设施检测报告(系统全)_20250524072320A016.doc\",\"reportId\":\"ZJJCPDS20250523083\",\"reportIsfinal\":\"Y\",\"reportName\":\"平顶山市高新区遵化镇敬老院消防设施检测项目（系统全））\",\"reportType\":\"JC\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-24 07:23:28', 118);
INSERT INTO `sys_oper_log` VALUES (245, '报告管理', 2, 'top.aameow.management.controller.ZjReportController.edit()', 'PUT', 1, 'liuxiaosu', NULL, '/management/report', '192.168.1.113', '内网IP', '{\"createBy\":\"孙静雨\",\"createTime\":\"2025-05-17 01:03:52\",\"id\":4,\"params\":{},\"reportAttach\":\"/profile/upload/2025/05/17/可降解材料_(非最终版).zip\",\"reportFile\":\"/profile/upload/2025/05/24/可降解材料_20250524092411A017.doc\",\"reportId\":\"ZJJCHB20250516080\",\"reportIsfinal\":\"N\",\"reportManager\":\"刘涛\",\"reportName\":\"可降解材料\",\"reportType\":\"JC\",\"updateBy\":\"刘晓苏\",\"updateTime\":\"2025-05-24 09:24:45\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-24 09:24:46', 107);
INSERT INTO `sys_oper_log` VALUES (246, '合同管理', 1, 'top.aameow.management.controller.ZjContractController.add()', 'POST', 1, 'sun', '技术部门', '/management/contract', '192.168.1.102', '内网IP', '{\"amount\":11100,\"contractFile\":\"/profile/upload/2025/05/26/ZJWB-2025-133_河南康润医药物流有限公司维护保养_2025-05-01到2026-04-30_11100元(刘涛)_20250526020835A018.pdf\",\"contractId\":\"ZJWB-2025-133\",\"contractName\":\"河南康润医药物流有限公司维护保养\",\"contractType\":0,\"createBy\":\"孙梦杰\",\"createTime\":\"2025-05-26 02:08:46\",\"endTime\":\"2026-04-30\",\"id\":8,\"manager\":\"刘涛\",\"params\":{},\"startTime\":\"2025-05-01\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-26 02:08:46', 76);
INSERT INTO `sys_oper_log` VALUES (247, '合同管理', 2, 'top.aameow.management.controller.ZjContractController.edit()', 'PUT', 1, 'sun', '技术部门', '/management/contract', '192.168.1.102', '内网IP', '{\"amount\":11100,\"contractFile\":\"/profile/upload/2025/05/26/ZJWB-2025-133_河南康润医药物流有限公司维护保养_2025-05-01到2026-04-30_11100元(刘涛)_20250526065658A019.pdf\",\"contractId\":\"ZJWB-2025-133\",\"contractName\":\"河南康润医药物流有限公司维护保养\",\"contractType\":0,\"createBy\":\"孙梦杰\",\"createTime\":\"2025-05-26 02:08:47\",\"endTime\":\"2026-04-30\",\"id\":8,\"manager\":\"刘涛\",\"params\":{},\"startTime\":\"2025-05-01\",\"updateBy\":\"孙梦杰\",\"updateTime\":\"2025-05-26 06:57:04\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-26 06:57:04', 115);
INSERT INTO `sys_oper_log` VALUES (248, '合同管理', 1, 'top.aameow.management.controller.ZjContractController.add()', 'POST', 1, 'sun', '技术部门', '/management/contract', '192.168.1.102', '内网IP', '{\"amount\":5000,\"contractFile\":\"/profile/upload/2025/05/26/ZJWB-2025-134_浚县幸福苑养老服务中心_2025-05-01到2026-04-30_5000元(刘涛)_20250526065810A020.pdf\",\"contractId\":\"ZJWB-2025-134\",\"contractName\":\"浚县幸福苑养老服务中心\",\"contractType\":0,\"createBy\":\"孙梦杰\",\"createTime\":\"2025-05-26 06:58:11\",\"endTime\":\"2026-04-30\",\"id\":9,\"manager\":\"刘涛\",\"params\":{},\"startTime\":\"2025-05-01\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-26 06:58:11', 113);
INSERT INTO `sys_oper_log` VALUES (249, '合同管理', 1, 'top.aameow.management.controller.ZjContractController.add()', 'POST', 1, 'sun', '技术部门', '/management/contract', '192.168.1.102', '内网IP', '{\"amount\":5000,\"contractFile\":\"/profile/upload/2025/05/26/ZJWB-2025-132_浚县清霞养老服务中心维护保养_2025-05-01到2026-04-30_5000元(刘涛)_20250526065929A021.pdf\",\"contractId\":\"ZJWB-2025-132\",\"contractName\":\"浚县清霞养老服务中心维护保养\",\"contractType\":0,\"createBy\":\"孙梦杰\",\"createTime\":\"2025-05-26 06:59:30\",\"endTime\":\"2026-04-30\",\"id\":10,\"manager\":\"刘涛\",\"params\":{},\"startTime\":\"2025-05-01\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-26 06:59:30', 84);
INSERT INTO `sys_oper_log` VALUES (250, '报告管理', 2, 'top.aameow.management.controller.ZjReportController.edit()', 'PUT', 1, 'xiaoyu', '管理部门', '/management/report', '192.168.1.112', '内网IP', '{\"createBy\":\"刘晓苏\",\"createTime\":\"2025-05-24 07:23:29\",\"id\":8,\"params\":{},\"reportArea\":\"960㎡\",\"reportFile\":\"/profile/upload/2025/05/24/平顶山市高新区遵化镇敬老院消防设施检测报告(系统全)_20250524072320A016.doc\",\"reportId\":\"ZJJCPDS20250523083\",\"reportIsfinal\":\"Y\",\"reportManager\":\"周圣崴\",\"reportName\":\"平顶山市高新区遵化镇敬老院消防设施检测项目（系统全））\",\"reportType\":\"JC\",\"updateBy\":\"孙静雨\",\"updateTime\":\"2025-05-26 07:08:59\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-26 07:08:59', 61);
INSERT INTO `sys_oper_log` VALUES (251, '报告管理', 2, 'top.aameow.management.controller.ZjReportController.edit()', 'PUT', 1, 'xiaoyu', '管理部门', '/management/report', '192.168.1.112', '内网IP', '{\"createBy\":\"刘晓苏\",\"createTime\":\"2025-05-24 07:22:15\",\"id\":7,\"params\":{},\"reportArea\":\"960㎡\",\"reportFile\":\"/profile/upload/2025/05/24/平顶山市高新区遵化镇敬老院消防设施检测报告_20250524072212A015.doc\",\"reportId\":\"ZJJCPDS20250523083\",\"reportIsfinal\":\"Y\",\"reportManager\":\"周圣崴\",\"reportName\":\"平顶山市高新区遵化镇敬老院消防设施检测项目（电系统））\",\"reportType\":\"JC\",\"updateBy\":\"孙静雨\",\"updateTime\":\"2025-05-26 07:09:03\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-26 07:09:03', 67);
INSERT INTO `sys_oper_log` VALUES (252, '用户头像', 2, 'top.aameow.web.controller.system.SysProfileController.avatar()', 'POST', 1, 'xiaoyu', '管理部门', '/system/user/profile/avatar', '192.168.1.112', '内网IP', '', '{\"msg\":\"操作成功\",\"imgUrl\":\"/profile/avatar/2025/05/26/retouch_20210405174307_20250526085931A022.jpg\",\"code\":200}', 0, NULL, '2025-05-26 08:59:31', 102);
INSERT INTO `sys_oper_log` VALUES (253, '合同管理', 1, 'top.aameow.management.controller.ZjContractController.add()', 'POST', 1, 'xiaoyu', '管理部门', '/management/contract', '192.168.1.112', '内网IP', '{\"amount\":7000,\"contractFile\":\"/profile/upload/2025/05/26/ZJWB-2025-130_浚县君悦餐饮服务有限公司_2025-05-01到2026-04-30_7000元(刘涛)_20250526090228A023.pdf\",\"contractId\":\"ZJWB-2025-130\",\"contractName\":\"浚县君悦餐饮服务有限公司\",\"contractType\":0,\"createBy\":\"孙静雨\",\"createTime\":\"2025-05-26 09:02:33\",\"endTime\":\"2026-04-30\",\"id\":11,\"manager\":\"刘涛\",\"params\":{},\"startTime\":\"2025-05-01\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-26 09:02:33', 87);
INSERT INTO `sys_oper_log` VALUES (254, '合同管理', 1, 'top.aameow.management.controller.ZjContractController.add()', 'POST', 1, 'xiaoyu', '管理部门', '/management/contract', '192.168.1.112', '内网IP', '{\"amount\":89212,\"contractFile\":\"/profile/upload/2025/05/26/ZJWB-2025-106_信阳天润广场消防设施维护保养_2025-05-01到2026-04-30_89212元(郭长亮)_20250526090520A024.pdf\",\"contractId\":\"ZJWB-2025-106\",\"contractName\":\"信阳天润广场消防设施维护保养\",\"contractType\":0,\"createBy\":\"孙静雨\",\"createTime\":\"2025-05-26 09:05:24\",\"endTime\":\"2026-04-30\",\"id\":12,\"manager\":\"郭长亮\",\"params\":{},\"startTime\":\"2025-05-01\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-26 09:05:24', 110);
INSERT INTO `sys_oper_log` VALUES (255, '合同管理', 2, 'top.aameow.management.controller.ZjContractController.edit()', 'PUT', 1, 'sun', '技术部门', '/management/contract', '192.168.1.102', '内网IP', '{\"amount\":20000,\"contractFile\":\"/profile/upload/2025/05/15/WB-1_谷麦光电消防设施维护保养_2025-05-01到2026-04-30_20000元(anyone)_20250515101830A002.pdf\",\"contractId\":\"/\",\"contractName\":\"谷麦光电消防设施维护保养\",\"contractType\":0,\"createTime\":\"2025-05-13 06:14:13\",\"endTime\":\"2026-04-30\",\"id\":1,\"manager\":\"郭长亮\",\"params\":{},\"startTime\":\"2025-05-01\",\"updateBy\":\"孙梦杰\",\"updateTime\":\"2025-05-26 09:06:40\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-26 09:06:40', 73);
INSERT INTO `sys_oper_log` VALUES (256, '合同管理', 1, 'top.aameow.management.controller.ZjContractController.add()', 'POST', 1, 'xiaoyu', '管理部门', '/management/contract', '192.168.1.112', '内网IP', '{\"amount\":10000,\"contractFile\":\"/profile/upload/2025/05/26/ZJWB-2025-LS0501_罗山县宝城衣世界服装商场消防设施维护保养_2025-05-01到2026-04-30_10000元(郭长亮)_20250526090903A025.pdf\",\"contractId\":\"ZJWB-2025-LS0501\",\"contractName\":\"罗山县宝城衣世界服装商场消防设施维护保养\",\"contractType\":0,\"createBy\":\"孙静雨\",\"createTime\":\"2025-05-26 09:09:04\",\"endTime\":\"2026-04-30\",\"id\":13,\"manager\":\"郭长亮\",\"params\":{},\"startTime\":\"2025-05-01\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-26 09:09:04', 123);
INSERT INTO `sys_oper_log` VALUES (257, '字典数据', 1, 'top.aameow.web.controller.system.SysDictDataController.add()', 'POST', 1, 'admin', '众基消防', '/system/dict/data', '192.168.1.102', '内网IP', '{\"createBy\":\"admin\",\"default\":false,\"dictLabel\":\"评定\",\"dictSort\":3,\"dictType\":\"contract_type\",\"dictValue\":\"PD\",\"listClass\":\"default\",\"params\":{},\"status\":\"0\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-26 09:26:45', 104);
INSERT INTO `sys_oper_log` VALUES (258, '合同管理', 1, 'top.aameow.management.controller.ZjContractController.add()', 'POST', 1, 'xiaoyu', '管理部门', '/management/contract', '192.168.1.105', '内网IP', '{\"amount\":10000,\"contractFile\":\"/profile/upload/2025/05/27/ZJWB-2025-131_郑州润和商务酒店有限公司维护保养2025.5.6-2026.5.5_2025-05-06到2026-05-05_10000元(公司)_20250527043015A026.pdf\",\"contractId\":\"ZJWB-2025-131\",\"contractName\":\"郑州润和商务酒店有限公司维护保养\",\"contractType\":0,\"createBy\":\"孙静雨\",\"createTime\":\"2025-05-27 04:30:44\",\"endTime\":\"2026-05-05\",\"id\":14,\"manager\":\"公司\",\"params\":{},\"startTime\":\"2025-05-06\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-27 04:30:44', 167);
INSERT INTO `sys_oper_log` VALUES (259, '合同管理', 1, 'top.aameow.management.controller.ZjContractController.add()', 'POST', 1, 'xiaoyu', '管理部门', '/management/contract', '192.168.1.105', '内网IP', '{\"amount\":15000,\"contractFile\":\"/profile/upload/2025/05/27/（BW）CG01-20250001_信阳农林学院消防设施维保服务项目2025.5.1-2025.9.30_2025-05-01到2025-09-30_15000元(刘瀚文)_20250527043156A027.pdf\",\"contractId\":\"（BW）CG01-20250001\",\"contractName\":\"信阳农林学院消防设施维保服务项目2025.5.1-2025.9.30\",\"contractType\":0,\"createBy\":\"孙静雨\",\"createTime\":\"2025-05-27 04:32:04\",\"endTime\":\"2025-09-30\",\"id\":15,\"manager\":\"刘瀚文\",\"params\":{},\"startTime\":\"2025-05-01\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-27 04:32:04', 110);
INSERT INTO `sys_oper_log` VALUES (260, '合同管理', 1, 'top.aameow.management.controller.ZjContractController.add()', 'POST', 1, 'xiaoyu', '管理部门', '/management/contract', '192.168.1.105', '内网IP', '{\"amount\":2000,\"contractFile\":\"/profile/upload/2025/05/27/ZJJC-2025-025_平顶山市高新区遵化镇敬老院消防设施检测_2025-05-20到2025-05-20_2000元(周圣崴)_20250527043617A028.pdf\",\"contractId\":\"ZJJC-2025-025\",\"contractName\":\"平顶山市高新区遵化镇敬老院消防设施检测\",\"contractType\":1,\"createBy\":\"孙静雨\",\"createTime\":\"2025-05-27 04:36:19\",\"endTime\":\"2025-05-20\",\"id\":16,\"manager\":\"周圣崴\",\"params\":{},\"startTime\":\"2025-05-20\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-27 04:36:19', 72);
INSERT INTO `sys_oper_log` VALUES (261, '合同管理', 1, 'top.aameow.management.controller.ZjContractController.add()', 'POST', 1, 'admin', '众基消防', '/management/contract', '192.168.1.103', '内网IP', '{\"amount\":1,\"contractId\":\"1\",\"contractName\":\"1\",\"contractType\":0,\"createBy\":\"开发账号\",\"createTime\":\"2025-05-27 04:36:50\",\"id\":17,\"manager\":\"1\",\"params\":{},\"startTime\":\"2025-05-01\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-27 04:36:50', 56);
INSERT INTO `sys_oper_log` VALUES (262, '合同管理', 3, 'top.aameow.management.controller.ZjContractController.remove()', 'DELETE', 1, 'admin', '众基消防', '/management/contract/17', '192.168.1.103', '内网IP', '[17]', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-27 04:37:09', 77);
INSERT INTO `sys_oper_log` VALUES (263, '合同管理', 2, 'top.aameow.management.controller.ZjContractController.edit()', 'PUT', 1, 'admin', '众基消防', '/management/contract', '192.168.1.103', '内网IP', '{\"amount\":2000,\"contractFile\":\"/profile/upload/2025/05/27/ZJJC-2025-025_平顶山市高新区遵化镇敬老院消防设施检测_2025-05-20到2025-05-20_2000元(周圣崴)_20250527043617A028.pdf\",\"contractId\":\"ZJJC-2025-025\",\"contractName\":\"平顶山市高新区遵化镇敬老院消防设施检测\",\"contractType\":1,\"createBy\":\"孙静雨\",\"createTime\":\"2025-05-27 04:36:20\",\"id\":16,\"manager\":\"周圣崴\",\"params\":{},\"startTime\":\"2025-05-20\",\"updateBy\":\"开发账号\",\"updateTime\":\"2025-05-27 04:37:13\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-27 04:37:13', 77);
INSERT INTO `sys_oper_log` VALUES (264, '合同管理', 3, 'top.aameow.management.controller.ZjContractController.remove()', 'DELETE', 1, 'admin', '众基消防', '/management/contract/16', '192.168.1.103', '内网IP', '[16]', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-27 04:37:40', 71);
INSERT INTO `sys_oper_log` VALUES (265, '合同管理', 1, 'top.aameow.management.controller.ZjContractController.add()', 'POST', 1, 'admin', '众基消防', '/management/contract', '192.168.1.103', '内网IP', '{\"amount\":2000,\"contractFile\":\"/profile/upload/2025/05/27/ZJJC-2025-025_平顶山市高新区遵化镇敬老院消防设施检测_2025-05-20_2000元(周圣崴)_20250527045845A029.pdf\",\"contractId\":\"ZJJC-2025-025\",\"contractName\":\"平顶山市高新区遵化镇敬老院消防设施检测\",\"contractType\":1,\"createBy\":\"开发账号\",\"createTime\":\"2025-05-27 04:58:46\",\"id\":18,\"manager\":\"周圣崴\",\"params\":{},\"startTime\":\"2025-05-20\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-27 04:58:46', 142);
INSERT INTO `sys_oper_log` VALUES (266, '用户管理', 1, 'top.aameow.web.controller.system.SysUserController.add()', 'POST', 1, 'admin', '众基消防', '/system/user', '192.168.1.103', '内网IP', '{\"admin\":false,\"createBy\":\"admin\",\"nickName\":\"huanglei\",\"params\":{},\"postIds\":[1],\"roleIds\":[4,3],\"status\":\"0\",\"userId\":10,\"userName\":\"黄磊\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-27 05:05:30', 398);
INSERT INTO `sys_oper_log` VALUES (267, '用户管理', 3, 'top.aameow.web.controller.system.SysUserController.remove()', 'DELETE', 1, 'admin', '众基消防', '/system/user/10', '192.168.1.103', '内网IP', '[10]', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-27 05:05:54', 151);
INSERT INTO `sys_oper_log` VALUES (268, '用户管理', 1, 'top.aameow.web.controller.system.SysUserController.add()', 'POST', 1, 'admin', '众基消防', '/system/user', '192.168.1.103', '内网IP', '{\"admin\":false,\"createBy\":\"admin\",\"deptId\":100,\"nickName\":\"黄磊\",\"params\":{},\"postIds\":[1],\"roleIds\":[3,4],\"status\":\"0\",\"userId\":11,\"userName\":\"huanglei\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-27 05:06:16', 334);
INSERT INTO `sys_oper_log` VALUES (269, '报告管理', 1, 'top.aameow.management.controller.ZjReportController.add()', 'POST', 1, 'liuxiaosu', NULL, '/management/report', '192.168.1.107', '内网IP', '{\"createBy\":\"刘晓苏\",\"createTime\":\"2025-05-27 07:14:24\",\"id\":9,\"params\":{},\"reportArea\":\"99980\",\"reportAttach\":\"/profile/upload/2025/05/27/蔡州医院年度检测照片_20250527071418A031.zip\",\"reportFile\":\"/profile/upload/2025/05/27/上蔡县蔡州中心医院有限公司消防检测_20250527071350A030.doc\",\"reportId\":\"N0:ZJJCSC2025052701\",\"reportIsfinal\":\"Y\",\"reportManager\":\"张功品\",\"reportName\":\"上蔡县蔡州中心医院有限公司\",\"reportType\":\"JC\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-27 07:14:24', 124);
INSERT INTO `sys_oper_log` VALUES (270, '报告管理', 1, 'top.aameow.management.controller.ZjReportController.add()', 'POST', 1, 'liuxiaosu', NULL, '/management/report', '192.168.1.107', '内网IP', '{\"createBy\":\"刘晓苏\",\"createTime\":\"2025-05-27 09:15:16\",\"id\":10,\"params\":{},\"reportArea\":\"15760.53㎡\",\"reportFile\":\"/profile/upload/2025/05/27/米河镇高庙村灾后重建集中安置区1#-5#楼建设项目检测报告_20250527091509A032.doc\",\"reportId\":\"ZJJCGY20250527084 \",\"reportIsfinal\":\"Y\",\"reportManager\":\"史振建\",\"reportName\":\"米河镇高庙村灾后重建集中安置区1#-5#楼建设项目\",\"reportType\":\"JC\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-27 09:15:16', 62);
INSERT INTO `sys_oper_log` VALUES (271, '报告管理', 2, 'top.aameow.management.controller.ZjReportController.edit()', 'PUT', 1, 'xiaoyu', '管理部门', '/management/report', '192.168.1.105', '内网IP', '{\"createBy\":\"刘晓苏\",\"createTime\":\"2025-05-27 07:14:24\",\"id\":9,\"params\":{},\"reportArea\":\"99980\",\"reportAttach\":\"/profile/upload/2025/05/27/蔡州医院年度检测照片_20250527071418A031.zip,/profile/upload/2025/05/27/报告签字_20250527112412A033.zip\",\"reportFile\":\"/profile/upload/2025/05/27/上蔡县蔡州中心医院有限公司消防检测_20250527071350A030.doc\",\"reportId\":\"N0:ZJJCSC2025052701\",\"reportIsfinal\":\"Y\",\"reportManager\":\"张功品\",\"reportName\":\"上蔡县蔡州中心医院有限公司\",\"reportType\":\"JC\",\"updateBy\":\"孙静雨\",\"updateTime\":\"2025-05-27 11:24:18\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-27 11:24:18', 206);
INSERT INTO `sys_oper_log` VALUES (272, '报告管理', 2, 'top.aameow.management.controller.ZjReportController.edit()', 'PUT', 1, 'liuxiaosu', NULL, '/management/report', '192.168.1.105', '内网IP', '{\"createBy\":\"刘晓苏\",\"createTime\":\"2025-05-24 07:23:29\",\"id\":8,\"params\":{},\"reportArea\":\"960㎡\",\"reportFile\":\"/profile/upload/2025/05/28/平顶山市高新区遵化镇敬老院消防设施检测报告(系统全)_20250524072320A016_20250528014820A034.doc\",\"reportId\":\"ZJJCPDS20250523083\",\"reportIsfinal\":\"Y\",\"reportManager\":\"周圣崴\",\"reportName\":\"平顶山市高新区遵化镇敬老院消防设施检测项目（系统全））\",\"reportType\":\"JC\",\"updateBy\":\"刘晓苏\",\"updateTime\":\"2025-05-28 01:48:22\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-28 01:48:22', 97);
INSERT INTO `sys_oper_log` VALUES (273, '报告管理', 1, 'top.aameow.management.controller.ZjReportController.add()', 'POST', 1, 'liuxiaosu', NULL, '/management/report', '192.168.1.105', '内网IP', '{\"createBy\":\"刘晓苏\",\"createTime\":\"2025-05-28 02:56:42\",\"id\":11,\"params\":{},\"reportArea\":\"30687.89㎡\",\"reportFile\":\"/profile/upload/2025/05/28/登封三一智能制造消防检测报告_20250528025639A035.doc\",\"reportId\":\"ZJJCDF20250206084\",\"reportIsfinal\":\"Y\",\"reportManager\":\"郭长亮、田洋洋\",\"reportName\":\"登封三一智能制造产业园项目\",\"reportType\":\"JC\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-28 02:56:42', 96);
INSERT INTO `sys_oper_log` VALUES (274, '报告管理', 1, 'top.aameow.management.controller.ZjReportController.add()', 'POST', 1, 'liuxiaosu', NULL, '/management/report', '192.168.1.105', '内网IP', '{\"createBy\":\"刘晓苏\",\"createTime\":\"2025-05-28 06:25:27\",\"id\":12,\"params\":{},\"reportArea\":\"30687.89㎡\",\"reportFile\":\"/profile/upload/2025/05/28/登封三一智能制造消防检测报告_20250528062520A036.doc\",\"reportId\":\"ZJJCDF20250206084\",\"reportIsfinal\":\"Y\",\"reportManager\":\"郭长亮、田洋洋\",\"reportName\":\"登封三一智能制造产业园项目\",\"reportType\":\"JC\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-28 06:25:27', 133);
INSERT INTO `sys_oper_log` VALUES (275, '报告管理', 3, 'top.aameow.management.controller.ZjReportController.remove()', 'DELETE', 1, 'liuxiaosu', NULL, '/management/report/11', '192.168.1.105', '内网IP', '[11]', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-28 06:25:55', 69);
INSERT INTO `sys_oper_log` VALUES (276, '报告管理', 2, 'top.aameow.management.controller.ZjReportController.edit()', 'PUT', 1, 'xiaoyu', '管理部门', '/management/report', '192.168.1.106', '内网IP', '{\"createBy\":\"小雨\",\"createTime\":\"2025-05-16 07:10:10\",\"id\":3,\"params\":{},\"reportArea\":\"673.45\",\"reportAttach\":\"/profile/upload/2025/05/17/浚县卫贤镇敬老院消防提升项目_(最终版).zip\",\"reportFile\":\"/profile/upload/2025/05/28/卫贤镇敬老院转型区域养老服务中心_20250528090251A037.doc\",\"reportId\":\"ZJJCHB20250325075\",\"reportIsfinal\":\"Y\",\"reportManager\":\"刘涛\",\"reportName\":\"卫贤镇敬老院转型区域养老服务中心\",\"reportType\":\"JC\",\"updateBy\":\"孙静雨\",\"updateTime\":\"2025-05-28 09:03:00\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-28 09:03:00', 45);
INSERT INTO `sys_oper_log` VALUES (277, '报告管理', 1, 'top.aameow.management.controller.ZjReportController.add()', 'POST', 1, 'liuxiaosu', NULL, '/management/report', '192.168.1.105', '内网IP', '{\"createBy\":\"刘晓苏\",\"createTime\":\"2025-05-28 09:18:33\",\"id\":13,\"params\":{},\"reportArea\":\"82072.05㎡\",\"reportFile\":\"/profile/upload/2025/05/28/河南省实验中学金水北校区(鸿兴路中学)升级改造项目一期工程消防未完部分续建工程_20250528091829A038.doc\",\"reportId\":\"ZJJCZZ20250528085\",\"reportIsfinal\":\"Y\",\"reportManager\":\"史振建\",\"reportName\":\"河南省实验中学金水北校区(鸿兴路中学)升级改造项目一期工程消防未完部分续建工程\",\"reportType\":\"JC\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-28 09:18:33', 174);
INSERT INTO `sys_oper_log` VALUES (278, '报告管理', 2, 'top.aameow.management.controller.ZjReportController.edit()', 'PUT', 1, 'sun', '技术部门', '/management/report', '192.168.1.109', '内网IP', '{\"createBy\":\"刘晓苏\",\"createTime\":\"2025-05-28 09:18:33\",\"id\":13,\"params\":{},\"reportArea\":\"82072.05㎡\",\"reportFile\":\"/profile/upload/2025/05/29/河南省实验中学金水北校区(鸿兴路中学)升级改造项目一期工程消防未完部分续建工程_20250529010951A040.doc\",\"reportId\":\"ZJJCZZ20250528085\",\"reportIsfinal\":\"Y\",\"reportManager\":\"史振建\",\"reportName\":\"河南省实验中学金水北校区(鸿兴路中学)升级改造项目一期工程消防未完部分续建工程\",\"reportType\":\"JC\",\"updateBy\":\"孙梦杰\",\"updateTime\":\"2025-05-29 01:09:54\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-29 01:09:54', 141);
INSERT INTO `sys_oper_log` VALUES (279, '合同管理', 1, 'top.aameow.management.controller.ZjContractController.add()', 'POST', 1, 'sun', '技术部门', '/management/contract', '192.168.1.109', '内网IP', '{\"amount\":30000,\"contractFile\":\"/profile/upload/2025/05/29/ZJWB-2025-058_尚城名都二期维护保养_2025-05-01到2026-04-30_30000元(未知)_20250529015233A041.pdf\",\"contractId\":\"ZJWB-2025-058\",\"contractName\":\"尚城名都二期维护保养\",\"contractType\":0,\"createBy\":\"孙梦杰\",\"createTime\":\"2025-05-29 01:52:34\",\"endTime\":\"2026-04-30\",\"id\":19,\"manager\":\"未知\",\"params\":{},\"startTime\":\"2025-05-01\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-29 01:52:34', 114);
INSERT INTO `sys_oper_log` VALUES (280, '合同管理', 2, 'top.aameow.management.controller.ZjContractController.edit()', 'PUT', 1, 'sun', '技术部门', '/management/contract', '192.168.1.109', '内网IP', '{\"amount\":30000,\"contractFile\":\"/profile/upload/2025/05/29/ZJWB-2025-058_尚城名都二期维护保养_2025-05-01到2026-04-30_30000元(未知)_20250529015233A041.pdf\",\"contractId\":\"ZJWB-2025-058\",\"contractName\":\"尚城名都二期维护保养（照片）\",\"contractType\":0,\"createBy\":\"孙梦杰\",\"createTime\":\"2025-05-29 01:52:35\",\"endTime\":\"2026-04-30\",\"id\":19,\"manager\":\"未知\",\"params\":{},\"startTime\":\"2025-05-01\",\"updateBy\":\"孙梦杰\",\"updateTime\":\"2025-05-29 04:29:41\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-29 04:29:41', 76);
INSERT INTO `sys_oper_log` VALUES (281, '合同管理', 1, 'top.aameow.management.controller.ZjContractController.add()', 'POST', 1, 'sun', '技术部门', '/management/contract', '192.168.1.109', '内网IP', '{\"amount\":3500,\"contractFile\":\"/profile/upload/2025/05/29/ZJWB-2025-053_登封市光影电影放映有限公司_2025-05-29到2026-04-30_3500元(未知)_20250529043047A042.pdf\",\"contractId\":\"ZJWB-2025-053\",\"contractName\":\"登封市光影电影放映有限公司\",\"contractType\":0,\"createBy\":\"孙梦杰\",\"createTime\":\"2025-05-29 04:30:48\",\"endTime\":\"2026-04-30\",\"id\":20,\"manager\":\"未知\",\"params\":{},\"startTime\":\"2025-05-29\"}', '{\"msg\":\"操作成功\",\"code\":200}', 0, NULL, '2025-05-29 04:30:49', 84);

-- ----------------------------
-- Table structure for sys_post
-- ----------------------------
DROP TABLE IF EXISTS `sys_post`;
CREATE TABLE `sys_post`  (
  `post_id` bigint NOT NULL AUTO_INCREMENT COMMENT '岗位ID',
  `post_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '岗位编码',
  `post_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '岗位名称',
  `post_sort` int NOT NULL COMMENT '显示顺序',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '状态（0正常 1停用）',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`post_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '岗位信息表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_post
-- ----------------------------
INSERT INTO `sys_post` VALUES (1, 'ceo', '董事长', 1, '0', 'admin', '2025-05-09 14:44:19', '', NULL, '');
INSERT INTO `sys_post` VALUES (2, 'se', '项目经理', 2, '0', 'admin', '2025-05-09 14:44:19', '', NULL, '');
INSERT INTO `sys_post` VALUES (3, 'hr', '人力资源', 3, '0', 'admin', '2025-05-09 14:44:19', '', NULL, '');
INSERT INTO `sys_post` VALUES (4, 'user', '普通员工', 4, '0', 'admin', '2025-05-09 14:44:19', '', NULL, '');

-- ----------------------------
-- Table structure for sys_role
-- ----------------------------
DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role`  (
  `role_id` bigint NOT NULL AUTO_INCREMENT COMMENT '角色ID',
  `role_name` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '角色名称',
  `role_key` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '角色权限字符串',
  `role_sort` int NOT NULL COMMENT '显示顺序',
  `data_scope` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '1' COMMENT '数据范围（1：全部数据权限 2：自定数据权限 3：本部门数据权限 4：本部门及以下数据权限）',
  `menu_check_strictly` tinyint(1) NULL DEFAULT 1 COMMENT '菜单树选择项是否关联显示',
  `dept_check_strictly` tinyint(1) NULL DEFAULT 1 COMMENT '部门树选择项是否关联显示',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '角色状态（0正常 1停用）',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`role_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 5 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '角色信息表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_role
-- ----------------------------
INSERT INTO `sys_role` VALUES (1, '超级管理员', 'admin', 1, '1', 1, 1, '0', '0', 'admin', '2025-05-09 14:44:19', '', NULL, '超级管理员');
INSERT INTO `sys_role` VALUES (2, '普通角色', 'common', 2, '2', 1, 1, '0', '0', 'admin', '2025-05-09 14:44:19', 'admin', '2025-05-13 17:05:15', '普通角色');
INSERT INTO `sys_role` VALUES (3, '技术人员', 'tech', 3, '1', 0, 1, '0', '0', 'admin', '2025-05-13 16:58:56', 'admin', '2025-05-19 01:11:05', NULL);
INSERT INTO `sys_role` VALUES (4, '管理人员', 'manager', 4, '1', 1, 1, '0', '0', 'admin', '2025-05-16 06:13:40', 'admin', '2025-05-19 01:11:14', NULL);

-- ----------------------------
-- Table structure for sys_role_dept
-- ----------------------------
DROP TABLE IF EXISTS `sys_role_dept`;
CREATE TABLE `sys_role_dept`  (
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `dept_id` bigint NOT NULL COMMENT '部门ID',
  PRIMARY KEY (`role_id`, `dept_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '角色和部门关联表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_role_dept
-- ----------------------------
INSERT INTO `sys_role_dept` VALUES (2, 100);
INSERT INTO `sys_role_dept` VALUES (2, 101);
INSERT INTO `sys_role_dept` VALUES (2, 105);

-- ----------------------------
-- Table structure for sys_role_menu
-- ----------------------------
DROP TABLE IF EXISTS `sys_role_menu`;
CREATE TABLE `sys_role_menu`  (
  `role_id` bigint NOT NULL COMMENT '角色ID',
  `menu_id` bigint NOT NULL COMMENT '菜单ID',
  PRIMARY KEY (`role_id`, `menu_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '角色和菜单关联表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_role_menu
-- ----------------------------
INSERT INTO `sys_role_menu` VALUES (3, 2007);
INSERT INTO `sys_role_menu` VALUES (3, 2008);
INSERT INTO `sys_role_menu` VALUES (3, 2009);
INSERT INTO `sys_role_menu` VALUES (3, 2010);
INSERT INTO `sys_role_menu` VALUES (3, 2011);
INSERT INTO `sys_role_menu` VALUES (3, 2012);
INSERT INTO `sys_role_menu` VALUES (4, 2000);
INSERT INTO `sys_role_menu` VALUES (4, 2001);
INSERT INTO `sys_role_menu` VALUES (4, 2002);
INSERT INTO `sys_role_menu` VALUES (4, 2003);
INSERT INTO `sys_role_menu` VALUES (4, 2004);
INSERT INTO `sys_role_menu` VALUES (4, 2005);

-- ----------------------------
-- Table structure for sys_user
-- ----------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user`  (
  `user_id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `dept_id` bigint NULL DEFAULT NULL COMMENT '部门ID',
  `user_name` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '用户账号',
  `nick_name` varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '用户昵称',
  `user_type` varchar(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '00' COMMENT '用户类型（00系统用户）',
  `email` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '用户邮箱',
  `phonenumber` varchar(11) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '手机号码',
  `sex` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '用户性别（0男 1女 2未知）',
  `avatar` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '头像地址',
  `password` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '密码',
  `status` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '账号状态（0正常 1停用）',
  `del_flag` char(1) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
  `login_ip` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '最后登录IP',
  `login_date` datetime NULL DEFAULT NULL COMMENT '最后登录时间',
  `create_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`user_id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 12 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '用户信息表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_user
-- ----------------------------
INSERT INTO `sys_user` VALUES (1, 100, 'admin', '开发账号', '00', '', '', '1', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '192.168.1.103', '2025-05-28 09:29:24', 'admin', '2025-05-09 14:44:19', '', '2025-05-28 01:29:24', '管理员');
INSERT INTO `sys_user` VALUES (2, 105, 'ry', '若依', '00', 'ry@qq.com', '15666666666', '1', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '2', '127.0.0.1', '2025-05-09 14:44:19', 'admin', '2025-05-09 14:44:19', '', NULL, '测试员');
INSERT INTO `sys_user` VALUES (3, 101, 'xiaoyu', '孙静雨', '00', '', '', '1', '/profile/avatar/2025/05/26/retouch_20210405174307_20250526085931A022.jpg', '$2a$10$xYTss0OlnRSUuz89WdNUpeDFLdbQVfyLuKmUJvrhApJ/7MaUjsAW6', '0', '0', '192.168.1.106', '2025-05-28 16:17:27', 'admin', '2025-05-13 17:01:57', 'admin', '2025-05-28 08:17:26', NULL);
INSERT INTO `sys_user` VALUES (4, 200, 'liya', '李亚', '00', '', '', '0', '', '$2a$10$Hz4N.lS0I9JE89j/W8EHkeJIq.KDjDkeWqa5.VZwjUcEbiyvipU0u', '0', '0', '192.168.1.107', '2025-05-17 15:27:47', 'admin', '2025-05-16 06:13:08', 'admin', '2025-05-17 07:27:47', NULL);
INSERT INTO `sys_user` VALUES (5, 200, 'sun', '孙梦杰', '00', '', '', '0', '', '$2a$10$49lVvC7gOpk4FuwUAKaWAOFgJ8PrTv.EPBAGN.cVLBPZkCR/R4cL.', '0', '0', '192.168.1.109', '2025-05-29 08:56:40', 'admin', '2025-05-16 07:46:13', 'admin', '2025-05-29 00:56:39', NULL);
INSERT INTO `sys_user` VALUES (6, 102, 'jinyan', '金燕', '00', '', '', '1', '', '$2a$10$ujeglXGYNcNaJVP8WNFc4OJ3v63mj/9KLRvVoZVZszCm9AkrIgqHa', '0', '0', '192.168.1.119', '2025-05-16 15:49:15', 'admin', '2025-05-16 07:47:51', 'admin', '2025-05-16 07:49:14', NULL);
INSERT INTO `sys_user` VALUES (7, 201, 'wangyufeng', '王宇丰', '00', '', '', '0', '', '$2a$10$nfJOillXWWxSHRUwUnZyM.vCBzu6t.Uc6N51W.lFJXrXDTZIJQ2QS', '0', '0', '192.168.1.116', '2025-05-17 09:04:45', 'admin', '2025-05-17 01:03:21', '', '2025-05-17 01:04:44', NULL);
INSERT INTO `sys_user` VALUES (8, NULL, 'liuxiaosu', '刘晓苏', '00', '', '', '0', '', '$2a$10$gSgiLiSbA6u1Ds9ITj7m7ub6qbY.0boPah6e.oaL8MRuJp95OG0.6', '0', '0', '192.168.1.105', '2025-05-28 09:09:16', 'admin', '2025-05-19 01:09:23', '', '2025-05-28 01:09:15', NULL);
INSERT INTO `sys_user` VALUES (9, NULL, 'guoshasha', '郭沙沙', '00', '', '', '0', '', '$2a$10$Ap6eGfNzgPxchv9v8D6LgOUgg0bwHu4KZ5gryc7Agc00HGxNna/NK', '0', '0', '192.168.1.128', '2025-05-23 17:00:47', 'admin', '2025-05-19 01:09:46', '', '2025-05-23 09:00:47', NULL);
INSERT INTO `sys_user` VALUES (10, NULL, '黄磊', 'huanglei', '00', '', '', '0', '', '$2a$10$WfMWy6U9eYjRpKXV55A32eEeZJWBjbRHQOYkr6eNouHwm6sq4e0oC', '0', '2', '', NULL, 'admin', '2025-05-27 05:05:30', '', NULL, NULL);
INSERT INTO `sys_user` VALUES (11, 100, 'huanglei', '黄磊', '00', '', '', '0', '', '$2a$10$CRITun9cBF9/kHD9G.S5rOi7Dk4osGM3lQb6/QpTStz7GMgghcfMK', '0', '0', '', NULL, 'admin', '2025-05-27 05:06:16', '', NULL, NULL);

-- ----------------------------
-- Table structure for sys_user_post
-- ----------------------------
DROP TABLE IF EXISTS `sys_user_post`;
CREATE TABLE `sys_user_post`  (
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `post_id` bigint NOT NULL COMMENT '岗位ID',
  PRIMARY KEY (`user_id`, `post_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '用户与岗位关联表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_user_post
-- ----------------------------
INSERT INTO `sys_user_post` VALUES (1, 1);
INSERT INTO `sys_user_post` VALUES (4, 4);
INSERT INTO `sys_user_post` VALUES (5, 4);
INSERT INTO `sys_user_post` VALUES (6, 4);
INSERT INTO `sys_user_post` VALUES (11, 1);

-- ----------------------------
-- Table structure for sys_user_role
-- ----------------------------
DROP TABLE IF EXISTS `sys_user_role`;
CREATE TABLE `sys_user_role`  (
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `role_id` bigint NOT NULL COMMENT '角色ID',
  PRIMARY KEY (`user_id`, `role_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '用户和角色关联表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of sys_user_role
-- ----------------------------
INSERT INTO `sys_user_role` VALUES (1, 1);
INSERT INTO `sys_user_role` VALUES (3, 3);
INSERT INTO `sys_user_role` VALUES (3, 4);
INSERT INTO `sys_user_role` VALUES (4, 3);
INSERT INTO `sys_user_role` VALUES (5, 3);
INSERT INTO `sys_user_role` VALUES (5, 4);
INSERT INTO `sys_user_role` VALUES (6, 3);
INSERT INTO `sys_user_role` VALUES (6, 4);
INSERT INTO `sys_user_role` VALUES (7, 3);
INSERT INTO `sys_user_role` VALUES (8, 3);
INSERT INTO `sys_user_role` VALUES (9, 3);
INSERT INTO `sys_user_role` VALUES (11, 3);
INSERT INTO `sys_user_role` VALUES (11, 4);

-- ----------------------------
-- Table structure for zj_contract
-- ----------------------------
DROP TABLE IF EXISTS `zj_contract`;
CREATE TABLE `zj_contract`  (
  `id` int NOT NULL AUTO_INCREMENT,
  `contract_id` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '合同编号（自动生成，也可以手动修改）',
  `contract_type` int NULL DEFAULT NULL COMMENT '合同类型（字典）',
  `contract_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '合同名称',
  `start_time` date NULL DEFAULT NULL COMMENT '合同开始时间',
  `end_time` date NULL DEFAULT NULL COMMENT '合同到期时间',
  `manager` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '合同归属',
  `amount` int NULL DEFAULT NULL COMMENT '合同金额',
  `create_by` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建人',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '更新人',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '备注',
  `contract_file` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '合同扫描件',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 21 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '合同表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of zj_contract
-- ----------------------------
INSERT INTO `zj_contract` VALUES (1, '/', 0, '谷麦光电消防设施维护保养', '2025-05-01', '2026-04-30', '郭长亮', 20000, NULL, '2025-05-13 14:14:13', '孙梦杰', '2025-05-26 17:06:40', NULL, '/profile/upload/2025/05/15/WB-1_谷麦光电消防设施维护保养_2025-05-01到2026-04-30_20000元(anyone)_20250515101830A002.pdf');
INSERT INTO `zj_contract` VALUES (2, '/', 0, '兰考糖果歌城', '2025-05-01', '2026-07-31', '韩飞', 8000, '孙梦杰', '2025-05-16 15:52:39', '孙静雨', '2025-05-16 17:49:37', NULL, '/profile/upload/2025/05/16/WB-2_兰考糖果歌城_2025-05-01到2026-07-31_8000元(韩飞)_20250516075234A001.pdf');
INSERT INTO `zj_contract` VALUES (3, 'ZJJC-2025-022', 1, '濮阳市华龙区濮东街道社区卫生服务中心年度检测项目', '2025-05-01', '2026-04-30', '鲁玉钦', 5000, '孙静雨', '2025-05-16 17:49:19', NULL, NULL, NULL, '/profile/upload/2025/05/16/ZJJC-2025-022_濮阳市华龙区濮东街道社区卫生服务中心年度检测项目_2025-05-01到2026-04-30_5000元(鲁玉钦)_20250516094904A002.pdf');
INSERT INTO `zj_contract` VALUES (4, 'ZJWB-2025-105', 0, '世纪华联超市维护保养', '2025-05-01', '2026-04-30', '张功品', 12000, '孙梦杰', '2025-05-20 09:29:17', NULL, NULL, NULL, '/profile/upload/2025/05/20/ZJWB-2025-105_世纪华联超市维护保养_2025-05-01到2026-04-30_12000元(张功品)_20250520012909A001.pdf');
INSERT INTO `zj_contract` VALUES (5, 'ZJWB-2025-054', 0, '宣化镇敬老院微型消防设施维护保养', '2025-05-12', '2026-05-11', '田洋洋', 10000, '孙梦杰', '2025-05-21 10:06:10', '孙梦杰', '2025-05-22 14:56:57', NULL, '/profile/upload/2025/05/22/ZJWB-2025-054_宣化镇敬老院微型消防设施维护保养_2025-05-12到2026-05-11_10000元(不知道)_20250522064003A008.pdf');
INSERT INTO `zj_contract` VALUES (6, 'ZJWB-2025-117', 0, '西平县恒康亿鑫养老院维护保养', '2025-05-01', '2026-04-30', '张功品', 23845, '孙梦杰', '2025-05-22 14:41:26', NULL, NULL, NULL, '/profile/upload/2025/05/22/ZJWB-2025-117_西平县恒康亿鑫养老院维护保养_2025-05-01到2026-04-30_23845元(张功品)_20250522064124A009.pdf');
INSERT INTO `zj_contract` VALUES (7, 'ZJWB-2025-116', 0, '西平县社会福利中心维护保养', '2025-05-09', '2026-05-08', '张功品', 15450, '孙梦杰', '2025-05-22 14:43:31', '孙梦杰', '2025-05-22 14:44:04', NULL, '/profile/upload/2025/05/22/ZJWB-2025-116_西平县社会福利中心维护保养_2025-05-09到2026-05-08_15450元(张功品)_20250522064401A011.pdf');
INSERT INTO `zj_contract` VALUES (8, 'ZJWB-2025-133', 0, '河南康润医药物流有限公司维护保养', '2025-05-01', '2026-04-30', '刘涛', 11100, '孙梦杰', '2025-05-26 10:08:47', '孙梦杰', '2025-05-26 14:57:04', NULL, '/profile/upload/2025/05/26/ZJWB-2025-133_河南康润医药物流有限公司维护保养_2025-05-01到2026-04-30_11100元(刘涛)_20250526065658A019.pdf');
INSERT INTO `zj_contract` VALUES (9, 'ZJWB-2025-134', 0, '浚县幸福苑养老服务中心', '2025-05-01', '2026-04-30', '刘涛', 5000, '孙梦杰', '2025-05-26 14:58:12', NULL, NULL, NULL, '/profile/upload/2025/05/26/ZJWB-2025-134_浚县幸福苑养老服务中心_2025-05-01到2026-04-30_5000元(刘涛)_20250526065810A020.pdf');
INSERT INTO `zj_contract` VALUES (10, 'ZJWB-2025-132', 0, '浚县清霞养老服务中心维护保养', '2025-05-01', '2026-04-30', '刘涛', 5000, '孙梦杰', '2025-05-26 14:59:30', NULL, NULL, NULL, '/profile/upload/2025/05/26/ZJWB-2025-132_浚县清霞养老服务中心维护保养_2025-05-01到2026-04-30_5000元(刘涛)_20250526065929A021.pdf');
INSERT INTO `zj_contract` VALUES (11, 'ZJWB-2025-130', 0, '浚县君悦餐饮服务有限公司', '2025-05-01', '2026-04-30', '刘涛', 7000, '孙静雨', '2025-05-26 17:02:33', NULL, NULL, NULL, '/profile/upload/2025/05/26/ZJWB-2025-130_浚县君悦餐饮服务有限公司_2025-05-01到2026-04-30_7000元(刘涛)_20250526090228A023.pdf');
INSERT INTO `zj_contract` VALUES (12, 'ZJWB-2025-106', 0, '信阳天润广场消防设施维护保养', '2025-05-01', '2026-04-30', '郭长亮', 89212, '孙静雨', '2025-05-26 17:05:25', NULL, NULL, NULL, '/profile/upload/2025/05/26/ZJWB-2025-106_信阳天润广场消防设施维护保养_2025-05-01到2026-04-30_89212元(郭长亮)_20250526090520A024.pdf');
INSERT INTO `zj_contract` VALUES (13, 'ZJWB-2025-LS0501', 0, '罗山县宝城衣世界服装商场消防设施维护保养', '2025-05-01', '2026-04-30', '郭长亮', 10000, '孙静雨', '2025-05-26 17:09:05', NULL, NULL, NULL, '/profile/upload/2025/05/26/ZJWB-2025-LS0501_罗山县宝城衣世界服装商场消防设施维护保养_2025-05-01到2026-04-30_10000元(郭长亮)_20250526090903A025.pdf');
INSERT INTO `zj_contract` VALUES (14, 'ZJWB-2025-131', 0, '郑州润和商务酒店有限公司维护保养', '2025-05-06', '2026-05-05', '公司', 10000, '孙静雨', '2025-05-27 12:30:45', NULL, NULL, NULL, '/profile/upload/2025/05/27/ZJWB-2025-131_郑州润和商务酒店有限公司维护保养2025.5.6-2026.5.5_2025-05-06到2026-05-05_10000元(公司)_20250527043015A026.pdf');
INSERT INTO `zj_contract` VALUES (15, '（BW）CG01-20250001', 0, '信阳农林学院消防设施维保服务项目2025.5.1-2025.9.30', '2025-05-01', '2025-09-30', '刘瀚文', 15000, '孙静雨', '2025-05-27 12:32:05', NULL, NULL, NULL, '/profile/upload/2025/05/27/（BW）CG01-20250001_信阳农林学院消防设施维保服务项目2025.5.1-2025.9.30_2025-05-01到2025-09-30_15000元(刘瀚文)_20250527043156A027.pdf');
INSERT INTO `zj_contract` VALUES (18, 'ZJJC-2025-025', 1, '平顶山市高新区遵化镇敬老院消防设施检测', '2025-05-20', NULL, '周圣崴', 2000, '开发账号', '2025-05-27 12:58:46', NULL, NULL, NULL, '/profile/upload/2025/05/27/ZJJC-2025-025_平顶山市高新区遵化镇敬老院消防设施检测_2025-05-20_2000元(周圣崴)_20250527045845A029.pdf');
INSERT INTO `zj_contract` VALUES (19, 'ZJWB-2025-058', 0, '尚城名都二期维护保养（照片）', '2025-05-01', '2026-04-30', '未知', 30000, '孙梦杰', '2025-05-29 09:52:35', '孙梦杰', '2025-05-29 12:29:41', NULL, '/profile/upload/2025/05/29/ZJWB-2025-058_尚城名都二期维护保养_2025-05-01到2026-04-30_30000元(未知)_20250529015233A041.pdf');
INSERT INTO `zj_contract` VALUES (20, 'ZJWB-2025-053', 0, '登封市光影电影放映有限公司', '2025-05-29', '2026-04-30', '未知', 3500, '孙梦杰', '2025-05-29 12:30:49', NULL, NULL, NULL, '/profile/upload/2025/05/29/ZJWB-2025-053_登封市光影电影放映有限公司_2025-05-29到2026-04-30_3500元(未知)_20250529043047A042.pdf');

-- ----------------------------
-- Table structure for zj_customer
-- ----------------------------
DROP TABLE IF EXISTS `zj_customer`;
CREATE TABLE `zj_customer`  (
  `id` int NOT NULL COMMENT 'ID',
  `invoice_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '开票代码',
  `customer_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '客户名称',
  `contact` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '联系人',
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '职位',
  `phone_number` int NULL DEFAULT NULL COMMENT '联系电话',
  `addr` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '联系地址',
  `customer_fullname` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '客户全称',
  `social_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '社会信用代码',
  `bank_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '开户行',
  `bank_code` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '银行账号',
  `create_by` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人员',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人员',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '客户表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of zj_customer
-- ----------------------------

-- ----------------------------
-- Table structure for zj_invoice
-- ----------------------------
DROP TABLE IF EXISTS `zj_invoice`;
CREATE TABLE `zj_invoice`  (
  `id` int NOT NULL,
  `customer_id` int NULL DEFAULT NULL,
  `contract_id` int NULL DEFAULT NULL,
  `invoice_date` datetime NULL DEFAULT NULL COMMENT '开票日期',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  `invoice_detail` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `invoice_quota` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `invoice_percent` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `invoice_type` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `manager` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `create_by` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人员',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人员',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '发票表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of zj_invoice
-- ----------------------------

-- ----------------------------
-- Table structure for zj_pay
-- ----------------------------
DROP TABLE IF EXISTS `zj_pay`;
CREATE TABLE `zj_pay`  (
  `id` int NOT NULL,
  `pay_date` datetime NULL DEFAULT NULL COMMENT '付款日期',
  `contract_id` int NULL DEFAULT NULL COMMENT '合同ID',
  `customer_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '客户名称',
  `invoice_id` int NULL DEFAULT NULL COMMENT '发票ID',
  `taxes` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '税费',
  `manage_cast` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '管理费',
  `pay_method` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '付款方法',
  `real_wages` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '实际收款',
  `refund` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '返款金额',
  `refund_receiver` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '返款人',
  `refund_date` datetime NULL DEFAULT NULL COMMENT '返款日期',
  `create_by` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '创建人员',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '更新人员',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  `remark` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of zj_pay
-- ----------------------------

-- ----------------------------
-- Table structure for zj_report
-- ----------------------------
DROP TABLE IF EXISTS `zj_report`;
CREATE TABLE `zj_report`  (
  `id` int NOT NULL AUTO_INCREMENT,
  `report_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '报告编号',
  `report_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '名称',
  `report_file` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '报告文件',
  `report_attach` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '附件',
  `report_type` varchar(4) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '报告类型',
  `report_isfinal` varchar(4) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '最终版本',
  `report_manager` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '报告归属',
  `report_area` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '项目面积',
  `create_by` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '创建者',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_by` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '更新者',
  `update_time` datetime NULL DEFAULT NULL COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 14 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '报告表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of zj_report
-- ----------------------------
INSERT INTO `zj_report` VALUES (1, 'ZJJCXY20250514078', '武汉武铁机辆装备有限公司资产运营分公司信阳站点', '/profile/upload/2025/05/15/武汉武铁机辆装备有限公司资产运营分公司信阳站点_20250515121344A003.doc', NULL, 'JC', 'Y', '郭长亮', '/', '小雨', '2025-05-15 20:13:52', 'xiaoyu', '2025-05-16 11:56:59');
INSERT INTO `zj_report` VALUES (2, 'ZJJCHB20250516081', '小河镇卫生院', '/profile/upload/2025/05/19/浚县小河镇卫生院灾后恢复重建项目(病房楼)_(非最终版)_20250519093141.doc', NULL, 'JC', 'N', '刘涛', '6802.55', '李亚', '2025-05-16 14:31:36', '刘晓苏', '2025-05-19 17:33:19');
INSERT INTO `zj_report` VALUES (3, 'ZJJCHB20250325075', '卫贤镇敬老院转型区域养老服务中心', '/profile/upload/2025/05/28/卫贤镇敬老院转型区域养老服务中心_20250528090251A037.doc', '/profile/upload/2025/05/17/浚县卫贤镇敬老院消防提升项目_(最终版).zip', 'JC', 'Y', '刘涛', '673.45', '小雨', '2025-05-16 15:10:10', '孙静雨', '2025-05-28 17:03:00');
INSERT INTO `zj_report` VALUES (4, 'ZJJCHB20250516080', '可降解材料', '/profile/upload/2025/05/24/可降解材料_20250524092411A017.doc', '/profile/upload/2025/05/17/可降解材料_(非最终版).zip', 'JC', 'N', '刘涛', NULL, '孙静雨', '2025-05-17 09:03:52', '刘晓苏', '2025-05-24 17:24:46');
INSERT INTO `zj_report` VALUES (5, NULL, '浚县宏建建材', '/profile/upload/2025/05/24/宏建建材检测报告_(非最终版)_20250524022216A014.doc', NULL, 'JC', 'N', '刘涛', NULL, '李亚', '2025-05-17 14:38:46', '刘晓苏', '2025-05-24 10:22:24');
INSERT INTO `zj_report` VALUES (6, 'ZJJCZMD20250429021', '西平县白云商务服务中心', '/profile/upload/2025/05/22/白云商务服务中心检测报告_20250522105947A013.doc', '/profile/upload/2025/05/21/白云商务服务中心_20250521072006A005.zip', 'JC', 'Y', '董耀东', '10761.3', '孙静雨', '2025-05-21 09:15:44', '郭沙沙', '2025-05-22 18:59:51');
INSERT INTO `zj_report` VALUES (7, 'ZJJCPDS20250523083', '平顶山市高新区遵化镇敬老院消防设施检测项目（电系统））', '/profile/upload/2025/05/24/平顶山市高新区遵化镇敬老院消防设施检测报告_20250524072212A015.doc', NULL, 'JC', 'Y', '周圣崴', '960㎡', '刘晓苏', '2025-05-24 15:22:15', '孙静雨', '2025-05-26 15:09:04');
INSERT INTO `zj_report` VALUES (8, 'ZJJCPDS20250523083', '平顶山市高新区遵化镇敬老院消防设施检测项目（系统全））', '/profile/upload/2025/05/28/平顶山市高新区遵化镇敬老院消防设施检测报告(系统全)_20250524072320A016_20250528014820A034.doc', NULL, 'JC', 'Y', '周圣崴', '960㎡', '刘晓苏', '2025-05-24 15:23:29', '刘晓苏', '2025-05-28 09:48:22');
INSERT INTO `zj_report` VALUES (9, 'N0:ZJJCSC2025052701', '上蔡县蔡州中心医院有限公司', '/profile/upload/2025/05/27/上蔡县蔡州中心医院有限公司消防检测_20250527071350A030.doc', '/profile/upload/2025/05/27/蔡州医院年度检测照片_20250527071418A031.zip,/profile/upload/2025/05/27/报告签字_20250527112412A033.zip', 'JC', 'Y', '张功品', '99980', '刘晓苏', '2025-05-27 15:14:24', '孙静雨', '2025-05-27 19:24:18');
INSERT INTO `zj_report` VALUES (10, 'ZJJCGY20250527084 ', '米河镇高庙村灾后重建集中安置区1#-5#楼建设项目', '/profile/upload/2025/05/27/米河镇高庙村灾后重建集中安置区1#-5#楼建设项目检测报告_20250527091509A032.doc', NULL, 'JC', 'Y', '史振建', '15760.53㎡', '刘晓苏', '2025-05-27 17:15:16', NULL, NULL);
INSERT INTO `zj_report` VALUES (12, 'ZJJCDF20250206084', '登封三一智能制造产业园项目', '/profile/upload/2025/05/28/登封三一智能制造消防检测报告_20250528062520A036.doc', NULL, 'JC', 'Y', '郭长亮、田洋洋', '30687.89㎡', '刘晓苏', '2025-05-28 14:25:27', NULL, NULL);
INSERT INTO `zj_report` VALUES (13, 'ZJJCZZ20250528085', '河南省实验中学金水北校区(鸿兴路中学)升级改造项目一期工程消防未完部分续建工程', '/profile/upload/2025/05/29/河南省实验中学金水北校区(鸿兴路中学)升级改造项目一期工程消防未完部分续建工程_20250529010951A040.doc', NULL, 'JC', 'Y', '史振建', '82072.05㎡', '刘晓苏', '2025-05-28 17:18:33', '孙梦杰', '2025-05-29 09:09:54');

SET FOREIGN_KEY_CHECKS = 1;
