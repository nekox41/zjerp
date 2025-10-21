package top.aameow.autobackup.mapper;


import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import top.aameow.autobackup.domain.BackupRecord;

@Mapper
public interface CloudBackupMapper {
    @Insert("INSERT INTO autobackup_record(file_name, file_sha256, upload_time) VALUES (#{fileName}, #{fileSHA256}, #{uploadTime})")
    boolean insertRecord(BackupRecord record);

    // TODO 从数据库获取记录
    @Select("SELECT file_name as fileName, file_sha256 as fileSHA256, upload_time as uploadTime FROM autobackup_record WHERE file_sha256 = #{sha256}")
    BackupRecord getRecord(String sha256);
}
