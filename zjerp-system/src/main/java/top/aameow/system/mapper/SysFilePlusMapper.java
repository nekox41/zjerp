package top.aameow.system.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import top.aameow.system.domain.SysFilePlus;

public interface SysFilePlusMapper {

    List<SysFilePlus> selectList(SysFilePlus query);

    List<SysFilePlus> selectByUrls(@Param("urls") List<String> urls);

    SysFilePlus selectById(Long fileId);

    SysFilePlus selectByUrl(String fileUrl);

    SysFilePlus selectByHash(String fileHash);

    int countByHash(String fileHash);

    List<SysFilePlus> selectByBiz(@Param("bizType") String bizType, @Param("bizId") Long bizId);

    List<SysFilePlus> selectByIds(@Param("fileIds") List<Long> fileIds);

    int insert(SysFilePlus SysFilePlus);

    int deleteById(Long fileId);

    /** 业务保存后回填 biz_id */
    int updateBizId(@Param("fileIds") List<Long> fileIds,
                    @Param("bizType") String bizType,
                    @Param("bizId") Long bizId);
}