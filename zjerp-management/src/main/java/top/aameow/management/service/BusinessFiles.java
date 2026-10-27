package top.aameow.management.service;

import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import top.aameow.common.exception.ServiceException;
import top.aameow.system.domain.SysFilePlus;
import top.aameow.system.service.ISysFilePlusService;

/** 保留旧 API 的字符串字段，附件实际存储在 sys_file；旧列仅用于升级过渡。 */
@Component
public class BusinessFiles
{
    @Autowired
    private ISysFilePlusService files;

    public String read(String type, Long id, String legacy)
    {
        String urls = files.listByBiz(type, id).stream().map(SysFilePlus::getFileUrl).collect(Collectors.joining(","));
        return urls.isEmpty() && legacy != null ? legacy : urls;
    }

    public void save(String type, Long id, String urls)
    {
        try { files.syncBiz(urls, type, id); }
        catch (Exception e) { throw new ServiceException("附件保存失败: " + e.getMessage()); }
    }
}
