package top.aameow.system.service;

import java.util.List;
import org.springframework.web.multipart.MultipartFile;
import top.aameow.system.domain.SysFilePlus;

public interface ISysFilePlusService {

    /** 上传文件，返回带 fileId 的完整记录 */
    SysFilePlus upload(MultipartFile file, String bizType, Long bizId) throws Exception;

    SysFilePlus uploadInvoice(MultipartFile file, Long reimbursementId, String creator) throws Exception;

    /** 按 fileId 查询 */
    SysFilePlus getById(Long fileId);

    SysFilePlus getByUrl(String url);

    /** 按业务查询 */
    List<SysFilePlus> listByBiz(String bizType, Long bizId);

    /** 按 ID 列表批量查询 */
    List<SysFilePlus> listByIds(List<Long> fileIds);

    /** 仅允许逻辑删除暂存文件；磁盘文件保留 */
    void delete(Long fileId);

    /** 旧入口仅用于返回兼容性提示；不允许直接绑定 */
    void bindBiz(List<Long> fileIds, String bizType, Long bizId);

    void syncBiz(String urls, String bizType, Long bizId) throws Exception;

    /** 登记已有文件（老接口上传的文件补录到新表） */
    SysFilePlus registerLegacy(String url, String bizType, Long bizId) throws Exception;
}