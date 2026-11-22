package top.aameow.system.service.impl;

import java.io.InputStream;
import java.nio.file.*;
import java.util.*;
import org.apache.commons.codec.digest.DigestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import top.aameow.common.config.RuoYiConfig;
import top.aameow.common.exception.ServiceException;
import top.aameow.common.utils.SecurityUtils;
import top.aameow.common.utils.file.FileUploadUtils;
import top.aameow.common.utils.file.MimeTypeUtils;
import top.aameow.system.domain.SysFilePlus;
import top.aameow.system.mapper.SysFilePlusMapper;
import top.aameow.system.service.ISysFilePlusService;

@Service
public class SysFilePlusServiceImpl implements ISysFilePlusService
{
    @Autowired
    private SysFilePlusMapper mapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysFilePlus upload(MultipartFile file, String bizType, Long bizId) throws Exception
    {
        if (file == null || file.isEmpty()) throw new ServiceException("上传文件不能为空");
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank() || name.length() > 512) throw new ServiceException("文件名不能为空或超过512个字符");
        FileUploadUtils.assertAllowed(file, MimeTypeUtils.DEFAULT_ALLOWED_EXTENSION);
        String ext = FileUploadUtils.getExtension(file).toLowerCase(Locale.ROOT);
        if (!ext.matches("[a-z0-9]{1,16}")) throw new ServiceException("文件扩展名不合法");
        SysFilePlus record;
        try (InputStream in = file.getInputStream())
        {
            record = store(in, name, ext, file.getContentType());
        }
        // 上传阶段不接受业务关联；业务保存与关联必须在同一事务中完成。
        mapper.insert(record);
        return record;
    }

    @Override
    @Transactional(rollbackFor=Exception.class)
    public SysFilePlus uploadInvoice(MultipartFile file,Long reimbursementId,String creator) throws Exception {
        if(file==null||file.isEmpty()||file.getSize()>10L*1024*1024)throw new ServiceException("发票不能为空且不能超过10MB");
        if(file.getOriginalFilename()==null||file.getOriginalFilename().isBlank()||file.getOriginalFilename().length()>512)throw new ServiceException("发票文件名不能为空或超过512字");
        FileUploadUtils.assertAllowed(file,new String[]{"pdf","png","jpg","jpeg"});
        String ext=FileUploadUtils.getExtension(file).toLowerCase(Locale.ROOT);
        byte[] head;try(InputStream in=file.getInputStream()){head=in.readNBytes(8);}
        boolean pdf=head.length>=5&&new String(head,0,5,java.nio.charset.StandardCharsets.US_ASCII).equals("%PDF-");
        boolean png=head.length>=8&&head[0]==(byte)137&&head[1]==80&&head[2]==78&&head[3]==71;
        boolean jpg=head.length>=3&&head[0]==(byte)255&&head[1]==(byte)216&&head[2]==(byte)255;
        if(!((ext.equals("pdf")&&pdf)||(ext.equals("png")&&png)||((ext.equals("jpg")||ext.equals("jpeg"))&&jpg)))throw new ServiceException("发票内容与PDF/图片格式不符");
        SysFilePlus record;try(InputStream in=file.getInputStream()){record=store(in,file.getOriginalFilename(),ext,pdf?"application/pdf":png?"image/png":"image/jpeg",creator);}
        record.setBizType("reimbursement_invoice");record.setBizId(reimbursementId);mapper.insert(record);return record;
    }

    private SysFilePlus store(InputStream in, String name, String ext, String type) throws Exception
    {
        return store(in,name,ext,type,SecurityUtils.getUsername());
    }

    private SysFilePlus store(InputStream in,String name,String ext,String type,String creator) throws Exception
    {
        Path directory = Paths.get(RuoYiConfig.getProfile(), "files").toAbsolutePath().normalize();
        Files.createDirectories(directory);
        Path temp = Files.createTempFile(directory, "upload-", ".tmp");
        try
        {
            Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
            String hash;
            try (InputStream data = Files.newInputStream(temp)) { hash = DigestUtils.sha256Hex(data); }
            // 每个上传独立 UUID，避免并发去重/删除竞争；原文件名只作为元数据保存。
            String relative = "files/" + hash.substring(0, 2) + "/" + UUID.randomUUID().toString().replace("-", "") + "." + ext;
            Path target = resolve(relative);
            Files.createDirectories(target.getParent());
            Files.move(temp, target);
            if (org.springframework.transaction.support.TransactionSynchronizationManager.isSynchronizationActive())
            {
                org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                    new org.springframework.transaction.support.TransactionSynchronization()
                    {
                        @Override
                        public void afterCompletion(int status)
                        {
                            if (status != STATUS_COMMITTED)
                            {
                                try { Files.deleteIfExists(target); }
                                catch (java.io.IOException e) { org.slf4j.LoggerFactory.getLogger(SysFilePlusServiceImpl.class).warn("回滚文件清理失败: {}", target, e); }
                            }
                        }
                    });
            }
            SysFilePlus record = new SysFilePlus();
            record.setFileName(name);
            record.setFilePath(relative);
            record.setFileUrl("/profile/" + relative);
            record.setFileHash(hash);
            record.setFileSize(Files.size(target));
            record.setFileExt(ext);
            record.setFileType(type == null ? "application/octet-stream" : type);
            record.setDelFlag("0");
            record.setCreateTime(top.aameow.common.utils.DateUtils.getNowDate());
            record.setCreateBy(creator);
            return record;
        }
        finally { Files.deleteIfExists(temp); }
    }

    private Path resolve(String relative) throws Exception
    {
        Path base = Paths.get(RuoYiConfig.getProfile()).toRealPath();
        Path path = base.resolve(relative).normalize();
        if (!path.startsWith(base)) throw new ServiceException("文件路径不合法");
        Path ancestor = path;
        while (ancestor != null && !Files.exists(ancestor)) ancestor = ancestor.getParent();
        if (ancestor == null || !ancestor.toRealPath().startsWith(base)) throw new ServiceException("文件路径不合法");
        return path;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SysFilePlus registerLegacy(String url, String bizType, Long bizId) throws Exception
    {
        String resource = url;
        if (resource.startsWith("http://") || resource.startsWith("https://")) resource = java.net.URI.create(resource).getRawPath();
        if (!resource.startsWith("/profile/")) throw new ServiceException("仅允许迁移本地/profile/文件");
        String relative = resource.substring("/profile/".length());
        Path source = resolve(relative);
        // 先按原样查找，兼容文件名包含 %、+、# 的历史文件。
        if (!Files.isRegularFile(source))
        {
            relative = java.net.URLDecoder.decode(relative.replace("+", "%2B"), java.nio.charset.StandardCharsets.UTF_8);
            source = resolve(relative);
        }
        if (!Files.isRegularFile(source)) throw new ServiceException("原文件不存在: " + resource);
        String name = source.getFileName().toString();
        int dot = name.lastIndexOf('.');
        String ext = dot < 0 ? "bin" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!ext.matches("[a-z0-9]{1,16}")) ext = "bin";
        SysFilePlus record;
        try (InputStream in = Files.newInputStream(source)) { record = store(in, name, ext, Files.probeContentType(source)); }
        record.setBizType(bizType);
        record.setBizId(bizId);
        mapper.insert(record);
        return record;
    }

    @Override
    public SysFilePlus getById(Long id) { return mapper.selectById(id); }

    @Override
    public SysFilePlus getByUrl(String url) { return mapper.selectByUrl(url); }

    @Override
    public List<SysFilePlus> listByBiz(String type, Long id)
    {
        return type == null || id == null ? Collections.emptyList() : mapper.selectByBiz(type, id);
    }

    @Override
    public List<SysFilePlus> listByIds(List<Long> ids)
    {
        return ids == null || ids.isEmpty() ? Collections.emptyList() : mapper.selectByIds(ids);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id)
    {
        SysFilePlus record = getById(id);
        if (record == null) return;
        if (record.getBizId() != null) throw new ServiceException("请通过业务表单解除文件关联");
        if (!SecurityUtils.isAdmin() && !SecurityUtils.getUsername().equals(record.getCreateBy()))
            throw new ServiceException("无权删除其他用户的文件");
        // 保留磁盘文件，清理应在保留期后独立执行，避免破坏历史链接。
        mapper.deleteById(id);
    }

    @Override
    public void bindBiz(List<Long> ids, String type, Long id)
    {
        throw new ServiceException("请通过业务保存接口关联文件");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void syncBiz(String urls, String type, Long id) throws Exception
    {
        if (urls == null) return; // PATCH 未传附件字段时保持关联
        if (id == null) throw new ServiceException("业务主键不能为空");
        List<SysFilePlus> existing = mapper.selectByBiz(type, id);
        Set<Long> keep = new HashSet<>();
        for (String url : new LinkedHashSet<>(Arrays.asList(urls.split("[,;]"))))
        {
            url = url.trim();
            if (url.isEmpty()) continue;
            SysFilePlus record = null;
            for (SysFilePlus candidate : existing)
                if (url.equals(candidate.getFileUrl())) { record = candidate; break; }
            if (record == null)
            {
                record = mapper.selectByUrl(url);
                if (record != null)
                {
                    if (record.getBizId() == null && !SecurityUtils.isAdmin()
                            && !SecurityUtils.getUsername().equals(record.getCreateBy()))
                        throw new ServiceException("无权关联其他用户的临时文件");
                    if (record.getBizId() != null)
                    {
                        record.setFileId(null); // 复制元数据，各业务引用单独管理
                        record.setBizId(null);
                        mapper.insert(record);
                    }
                    mapper.updateBizId(Collections.singletonList(record.getFileId()), type, id);
                }
                else
                {
                    if (url.startsWith("/profile/files/")) throw new ServiceException("文件不存在或已删除，请重新上传");
                    record = registerLegacy(url, type, id);
                }
            }
            keep.add(record.getFileId());
        }
        for (SysFilePlus record : existing)
            if (!keep.contains(record.getFileId())) mapper.deleteById(record.getFileId());
    }
}
