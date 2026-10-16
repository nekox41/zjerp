package top.aameow.system.service;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;
import top.aameow.system.domain.SysFilePlus;

public interface ISysFilePlusService {

    /** 上传文件，返回带 fileId 的完整记录 */
    SysFilePlus upload(MultipartFile file, String bizType, Long bizId) throws Exception;

    /** 按 fileId 查询 */
    SysFilePlus getById(Long fileId);

    /** 按业务查询 */
    List<SysFilePlus> listByBiz(String bizType, Long bizId);

    /** 按 ID 列表批量查询 */
    List<SysFilePlus> listByIds(List<Long> fileIds);

    /** 逻辑删除 + 按 hash 决定是否删物理文件 */
    void delete(Long fileId);

    /** 业务保存后回填 biz_id */
    void bindBiz(List<Long> fileIds, String bizType, Long bizId);
}