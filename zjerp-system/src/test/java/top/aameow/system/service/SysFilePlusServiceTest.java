package top.aameow.system.service;

import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import top.aameow.common.config.RuoYiConfig;
import top.aameow.common.core.domain.entity.SysUser;
import top.aameow.common.core.domain.model.LoginUser;
import top.aameow.common.exception.ServiceException;
import top.aameow.system.domain.SysFilePlus;
import top.aameow.system.mapper.SysFilePlusMapper;
import top.aameow.system.service.impl.SysFilePlusServiceImpl;

/** 无额外依赖的服务检查，由 scripts/verify-file-storage.sh 执行。 */
public class SysFilePlusServiceTest
{
    public static void main(String[] args) throws Exception
    {
        Path directory = Files.createTempDirectory("zjerp-file-check-");
        try { verify(directory); }
        finally
        {
            SecurityContextHolder.clearContext();
            if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
            try (var paths = Files.walk(directory))
            {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private static void verify(Path directory) throws Exception
    {
        new RuoYiConfig().setProfile(directory.toString());
        SysUser user = new SysUser(); user.setUserName("tester");
        LoginUser login = new LoginUser(2L, 1L, user, Collections.emptySet());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(login, null));
        List<String> calls = new ArrayList<>();
        List<SysFilePlus> existing = new ArrayList<>();
        SysFilePlusMapper mapper = (SysFilePlusMapper) Proxy.newProxyInstance(SysFilePlusMapper.class.getClassLoader(), new Class<?>[] {SysFilePlusMapper.class}, (proxy, method, params) -> {
            calls.add(method.getName());
            if (method.getName().equals("selectByBiz")) return existing;
            if (method.getName().equals("insert")) ((SysFilePlus) params[0]).setFileId((long) calls.size());
            return method.getReturnType() == int.class ? 1 : null;
        });
        SysFilePlusServiceImpl service = new SysFilePlusServiceImpl();
        Field field = SysFilePlusServiceImpl.class.getDeclaredField("mapper"); field.setAccessible(true); field.set(service, mapper);

        String name = "合同,#%;+ 空格中文.pdf";
        SysFilePlus record = service.upload(file(name), "contract", 123L);
        check(name.equals(record.getFileName()), "原始文件名保留");
        check(record.getFilePath().matches("files/[a-f0-9]{2}/[a-f0-9]{32}\\.pdf"), "磁盘路径仅使用标准字符");
        check(Arrays.equals(new byte[] {1, 2, 3}, Files.readAllBytes(directory.resolve(record.getFilePath()))), "磁盘内容完整");
        check(record.getBizId() == null, "上传不能直接关联业务");
        System.out.println("PASS 特殊字符文件名与标准存储路径");

        SysFilePlus second = service.upload(file(name), null, null);
        check(second.getFileHash().equals(record.getFileHash()) && !second.getFilePath().equals(record.getFilePath()), "相同内容独立路径");
        System.out.println("PASS 相同内容上传互不覆盖");

        TransactionSynchronizationManager.initSynchronization();
        SysFilePlus rollback = service.upload(file(name), null, null);
        for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
        TransactionSynchronizationManager.clearSynchronization();
        check(!Files.exists(directory.resolve(rollback.getFilePath())), "事务回滚清理文件");
        System.out.println("PASS 事务回滚清理磁盘文件");

        boolean rejected = false;
        try { service.registerLegacy("/profile/%2e%2e/outside.pdf", "contract", 1L); }
        catch (ServiceException e) { rejected = true; }
        check(rejected, "拒绝编码的目录越界");
        System.out.println("PASS 编码路径越界被拒绝");

        Path old = directory.resolve("旧文件,#%+ 空格.pdf"); Files.write(old, new byte[] {9, 8});
        SysFilePlus migrated = service.registerLegacy("/profile/" + old.getFileName(), "contract", 1L);
        check(Files.exists(old) && Arrays.equals(Files.readAllBytes(old), Files.readAllBytes(directory.resolve(migrated.getFilePath()))), "迁移复制完整并保留原文件");
        System.out.println("PASS 旧文件特殊字符迁移保留原件");

        existing.add(record); calls.clear();
        service.syncBiz("", "contract", 1L);
        check(calls.contains("deleteById") && Files.exists(directory.resolve(record.getFilePath())), "解除关联仅删除元数据");
        System.out.println("PASS 解除关联保留磁盘文件");

        existing.clear();
        boolean missingRejected = false;
        try { service.syncBiz("/profile/files/aa/deleted.pdf", "contract", 1L); }
        catch (ServiceException e) { missingRejected = true; }
        check(missingRejected, "已删除的新存储文件不能通过历史迁移复活");
        System.out.println("PASS 拒绝重新关联已删除文件");

        calls.clear(); service.syncBiz(null, "contract", 1L);
        check(calls.isEmpty(), "未传附件时不修改关联");
        System.out.println("PASS 未传附件字段时保持关联");
    }

    private static void check(boolean condition, String message)
    {
        if (!condition) throw new AssertionError(message);
    }

    private static MultipartFile file(String name)
    {
        return new MultipartFile()
        {
            public String getName() { return "file"; }
            public String getOriginalFilename() { return name; }
            public String getContentType() { return "application/pdf"; }
            public boolean isEmpty() { return false; }
            public long getSize() { return 3; }
            public byte[] getBytes() { return new byte[] {1, 2, 3}; }
            public InputStream getInputStream() { return new ByteArrayInputStream(getBytes()); }
            public void transferTo(File target) throws IOException { Files.write(target.toPath(), getBytes()); }
        };
    }
}
