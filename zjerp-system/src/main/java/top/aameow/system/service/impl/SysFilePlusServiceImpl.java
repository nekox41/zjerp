package top.aameow.system.service.impl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.multipart.MultipartFile;
import top.aameow.common.config.RuoYiConfig;
import top.aameow.common.exception.ServiceException;
import top.aameow.common.utils.DateUtils;
import top.aameow.common.utils.SecurityUtils;
import top.aameow.common.utils.StringUtils;
import top.aameow.system.domain.SysFilePlus;
import top.aameow.system.mapper.SysFilePlusMapper;
import top.aameow.system.service.ISysFilePlusService;

@Service
public class SysFilePlusServiceImpl implements ISysFilePlusService {

    private static final Logger log = LoggerFactory.getLogger(SysFilePlusServiceImpl.class);

    @Autowired
    private SysFilePlusMapper SysFilePlusMapper;

    @Override
    public SysFilePlus upload(MultipartFile file, String bizType, Long bizId) throws Exception {
        if (file == null || file.isEmpty()) {
            throw new ServiceException("上传文件不能为空");
        }
        String originalName = file.getOriginalFilename();
        if (StringUtils.isEmpty(originalName)) {
            throw new ServiceException("文件名不能为空");
        }

        String ext = FilenameUtils.getExtension(originalName);
        String hash;
        try (InputStream in = file.getInputStream()) {
            hash = DigestUtils.sha256Hex(in);
        } catch (IOException e) {
            throw new ServiceException("文件哈希计算失败: " + e.getMessage());
        }

        // 秒传：如果已有相同 hash，直接复用磁盘文件
        SysFilePlus exist = SysFilePlusMapper.selectByHash(hash);
        String relativePath;
        if (exist != null) {
            relativePath = exist.getFilePath();
        } else {
            // 分两级目录: a1/b2/a1b2...hash.ext
            relativePath = hash.substring(0, 2) + "/" + hash.substring(2, 4)
                    + "/" + hash + (StringUtils.isEmpty(ext) ? "" : "." + ext);
            File dest = new File(RuoYiConfig.getProfile(), relativePath);
            // 创建父目录（如果不存在）
            File parent = dest.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            try {
                file.transferTo(dest.getAbsoluteFile());
            } catch (IOException e) {
                throw new ServiceException("文件写入失败: " + e.getMessage());
            }
        }

        // 落库
        SysFilePlus SysFilePlus = new SysFilePlus();
        SysFilePlus.setFileName(originalName);
        SysFilePlus.setFilePath(relativePath);
        SysFilePlus.setFileUrl(buildUrl(relativePath));
        SysFilePlus.setFileHash(hash);
        SysFilePlus.setFileSize(file.getSize());
        SysFilePlus.setFileType(file.getContentType());
        SysFilePlus.setFileExt(ext);
        SysFilePlus.setBizType(bizType);
        SysFilePlus.setBizId(bizId);
        SysFilePlus.setDelFlag("0");
        SysFilePlus.setCreateBy(SecurityUtils.getUsername());
        SysFilePlus.setCreateTime(DateUtils.getNowDate());
        SysFilePlusMapper.insert(SysFilePlus);

        return SysFilePlus;
    }

    @Override
    public SysFilePlus getById(Long fileId) {
        return SysFilePlusMapper.selectById(fileId);
    }

    @Override
    public List<SysFilePlus> listByBiz(String bizType, Long bizId) {
        if (StringUtils.isEmpty(bizType) || bizId == null) {
            return Collections.emptyList();
        }
        return SysFilePlusMapper.selectByBiz(bizType, bizId);
    }

    @Override
    public List<SysFilePlus> listByIds(List<Long> fileIds) {
        if (CollectionUtils.isEmpty(fileIds)) {
            return Collections.emptyList();
        }
        return SysFilePlusMapper.selectByIds(fileIds);
    }

    @Override
    public void delete(Long fileId) {
        SysFilePlus SysFilePlus = SysFilePlusMapper.selectById(fileId);
        if (SysFilePlus == null) {
            return;
        }
        SysFilePlusMapper.deleteById(fileId);

        // 判断是否还有别的记录引用同一物理文件
        int remain = SysFilePlusMapper.countByHash(SysFilePlus.getFileHash());
        if (remain == 0) {
            File f = new File(RuoYiConfig.getProfile(), SysFilePlus.getFilePath());
            if (f.exists() && !f.delete()) {
                log.warn("物理文件删除失败: {}", f.getAbsolutePath());
            }
        }
    }

    @Override
    public void bindBiz(List<Long> fileIds, String bizType, Long bizId) {
        if (CollectionUtils.isEmpty(fileIds) || StringUtils.isEmpty(bizType) || bizId == null) {
            return;
        }
        SysFilePlusMapper.updateBizId(fileIds, bizType, bizId);
    }

    private String buildUrl(String relativePath) {
        // 返回 /profile/xx/yy/hash.pdf，前端会自动补全域名
        return "/profile/" + relativePath;
    }
}