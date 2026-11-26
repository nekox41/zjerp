package top.aameow.erpnext.files;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;

@Service
public class PrivateFileStorage {
    public static final long MAX_DOCX_BYTES=100L*1024*1024;
    private static final long MAX_OTHER_UPLOAD_BYTES=25L*1024*1024;
    private static final String DOCX="application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private final Path root;
    public PrivateFileStorage(@Value("${erp.files.root:/var/lib/erp-next/files}") String root) {
        this.root=Paths.get(root).toAbsolutePath().normalize();
    }
    public StoredFile store(MultipartFile file) {
        if(file==null||file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请选择非空文件");
        String media=file.getContentType()==null?"application/octet-stream":file.getContentType().split(";")[0].trim().toLowerCase(java.util.Locale.ROOT);
        long max=DOCX.equals(media)?MAX_DOCX_BYTES:MAX_OTHER_UPLOAD_BYTES;
        if(file.getSize()>max)throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"DOCX 上限100MB，其他上传文件上限25MB");
        if(!Set.of("application/pdf","image/jpeg","image/png","text/plain","application/vnd.openxmlformats-officedocument.wordprocessingml.document").contains(media))
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,"仅支持 PDF、JPG、PNG、TXT、DOCX 文件");
        String supplied=file.getOriginalFilename()==null?"upload":file.getOriginalFilename().replace('\\','/').replaceAll("[\\p{Cntrl}]","_");
        String original=Paths.get(supplied).getFileName().toString().replaceAll("[\\p{Cntrl}]","_").trim();
        if(original.isBlank()) original="upload";
        if(original.length()>240) original=original.substring(original.length()-240);
        String key=UUID.randomUUID().toString();
        Path written=null;
        try {
            byte[] signature;
            try(var in=file.getInputStream()){signature=in.readNBytes(8);}
            boolean valid=switch(media) {
                case "application/pdf" -> starts(signature,0x25,0x50,0x44,0x46,0x2d);
                case "image/png" -> starts(signature,0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a);
                case "image/jpeg" -> starts(signature,0xff,0xd8,0xff);
                case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> starts(signature,0x50,0x4b,0x03,0x04);
                case "text/plain" -> !containsZero(signature);
                default -> false;
            };
            if(!valid) throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,"文件内容与声明的类型不匹配");
            Files.createDirectories(root);
            Path target=root.resolve(key).normalize();
            if(!target.getParent().equals(root)) throw new IllegalStateException("Invalid storage key");
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            try(var in=file.getInputStream();var out=Files.newOutputStream(target,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)) {
                written=target;
                byte[] buf=new byte[8192]; int n;
                while((n=in.read(buf))!=-1){digest.update(buf,0,n);out.write(buf,0,n);}
            }
            return new StoredFile(key,original,media,file.getSize(),HexFormat.of().formatHex(digest.digest()),target);
        } catch(ResponseStatusException e) { throw e; }
        catch(Exception e) { if(written!=null)deleteQuietly(key);throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"文件保存失败",e); }
    }
    public StoredFile storeBytes(byte[] bytes,String filename,String mediaType) {
        if(bytes==null||bytes.length==0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"文件内容为空");
        String media=mediaType==null?"application/octet-stream":mediaType.split(";")[0].trim().toLowerCase(java.util.Locale.ROOT);
        if(bytes.length>(DOCX.equals(media)?MAX_DOCX_BYTES:250L*1024*1024))throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"生成文件超出上限；DOCX 不得超过100MB，请减少照片或调整质量");
        if(!Set.of("application/pdf","image/jpeg","image/png","text/plain","application/vnd.openxmlformats-officedocument.wordprocessingml.document").contains(media))
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,"不支持的文件类型");
        String original=filename==null?"generated":filename.replace('\\','/').replaceAll("[\\p{Cntrl}]","_");
        original=Paths.get(original).getFileName().toString().trim();
        if(original.isBlank()) original="generated";
        if(original.length()>240) original=original.substring(original.length()-240);
        String key=UUID.randomUUID().toString();
        Path written=null;
        try {
            byte[] signature=Arrays.copyOf(bytes,Math.min(bytes.length,8));
            boolean valid=switch(media) {
                case "application/pdf" -> starts(signature,0x25,0x50,0x44,0x46,0x2d);
                case "image/png" -> starts(signature,0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a);
                case "image/jpeg" -> starts(signature,0xff,0xd8,0xff);
                case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> starts(signature,0x50,0x4b,0x03,0x04);
                case "text/plain" -> !containsZero(signature);
                default -> false;
            };
            if(!valid) throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,"生成文件内容与类型不匹配");
            Files.createDirectories(root);
            Path target=root.resolve(key).normalize();
            if(!target.getParent().equals(root)) throw new IllegalStateException("Invalid storage key");
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            digest.update(bytes);
            try(var out=Files.newOutputStream(target,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){
                written=target;out.write(bytes);
            }
            return new StoredFile(key,original,media,bytes.length,HexFormat.of().formatHex(digest.digest()),target);
        } catch(ResponseStatusException e) { throw e; }
        catch(Exception e) { if(written!=null)deleteQuietly(key);throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"文件保存失败",e); }
    }
    private boolean starts(byte[] b,int... values){if(b.length<values.length)return false;for(int i=0;i<values.length;i++)if((b[i]&255)!=values[i])return false;return true;}
    private boolean containsZero(byte[] b){for(byte x:b)if(x==0)return true;return false;}
    public Path resolve(String key) {
        try {
            UUID.fromString(key);
            Path p=root.resolve(key).normalize();
            if(!p.getParent().equals(root)||!Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"文件不存在");
            return p;
        } catch(IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.NOT_FOUND,"文件不存在"); }
    }
    public void deleteQuietly(String key) { try { if(!UUID.fromString(key).toString().equals(key))return;Path p=root.resolve(key).normalize();if(p.getParent().equals(root))Files.deleteIfExists(p); } catch(IOException|IllegalArgumentException ignored) { } }
    public record StoredFile(String key,String originalName,String mediaType,long size,String sha256,Path path) {}
}
