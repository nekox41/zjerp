package top.aameow.erpnext.files;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class PrivateFileStorage {
    private final Path root;
    public PrivateFileStorage(@Value("${erp.files.root:/var/lib/erp-next/files}") String root) {
        this.root=Paths.get(root).toAbsolutePath().normalize();
    }
    public StoredFile store(MultipartFile file) {
        if(file==null||file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请选择非空文件");
        if(file.getSize()>25L*1024*1024) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"单个文件不能超过 25 MB");
        String media=file.getContentType()==null?"application/octet-stream":file.getContentType().split(";")[0].trim().toLowerCase();
        if(!java.util.Set.of("application/pdf","image/jpeg","image/png","text/plain","application/vnd.openxmlformats-officedocument.wordprocessingml.document").contains(media))
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,"仅支持 PDF、JPG、PNG、TXT、DOCX 文件");
        String original=file.getOriginalFilename()==null?"upload":Paths.get(file.getOriginalFilename()).getFileName().toString();
        original=original.replaceAll("[\\p{Cntrl}]","_").trim();
        if(original.isBlank()) original="upload";
        if(original.length()>240) original=original.substring(original.length()-240);
        String key=UUID.randomUUID().toString();
        try {
            Files.createDirectories(root);
            Path target=root.resolve(key).normalize();
            if(!target.getParent().equals(root)) throw new IllegalStateException("Invalid storage key");
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            try(var in=file.getInputStream();var out=Files.newOutputStream(target,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)) {
                byte[] buf=new byte[8192]; int n;
                while((n=in.read(buf))!=-1){digest.update(buf,0,n);out.write(buf,0,n);}
            }
            return new StoredFile(key,original,media,file.getSize(),HexFormat.of().formatHex(digest.digest()),target);
        } catch(Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"文件保存失败",e);
        }
    }
    public Path resolve(String key) {
        try {
            UUID.fromString(key);
            Path p=root.resolve(key).normalize();
            if(!p.getParent().equals(root)||!Files.isRegularFile(p)) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"文件不存在");
            return p;
        } catch(IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.NOT_FOUND,"文件不存在"); }
    }
    public void deleteQuietly(String key) { try { Files.deleteIfExists(root.resolve(key).normalize()); } catch(IOException ignored) { } }
    public record StoredFile(String key,String originalName,String mediaType,long size,String sha256,Path path) {}
}
