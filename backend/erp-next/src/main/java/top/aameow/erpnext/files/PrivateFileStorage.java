package top.aameow.erpnext.files;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

@Service
public class PrivateFileStorage {
    private static final long UPLOAD_LIMIT = 25L * 1024 * 1024;
    private static final long GENERATED_LIMIT = 250L * 1024 * 1024;
    private static final Set<String> ALLOWED = Set.of(
        "application/pdf", "image/jpeg", "image/png", "text/plain",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );
    private final Path root;

    public PrivateFileStorage(@Value("${erp.files.root:/var/lib/erp-next/files}") String root) {
        this.root=Paths.get(root).toAbsolutePath().normalize();
    }

    public StoredFile store(MultipartFile file) {
        if(file==null||file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请选择非空文件");
        if(file.getSize()>UPLOAD_LIMIT) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"单个上传文件不能超过 25 MB");
        try {
            return storeStream(file.getInputStream(),file.getSize(),file.getOriginalFilename(),file.getContentType(),UPLOAD_LIMIT);
        } catch(IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"读取上传文件失败",e);
        }
    }

    /** Store a server-generated artifact (for example, an edited DOCX) without routing it through a fake upload. */
    public StoredFile storeGenerated(byte[] bytes,String originalName,String mediaType) {
        if(bytes==null||bytes.length==0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"生成文件为空");
        return storeStream(new ByteArrayInputStream(bytes),bytes.length,originalName,mediaType,GENERATED_LIMIT);
    }

    private StoredFile storeStream(InputStream source,long size,String suppliedName,String suppliedMedia,long limit) {
        if(size>limit) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"文件超过允许大小");
        String media=suppliedMedia==null?"application/octet-stream":suppliedMedia.split(";")[0].trim().toLowerCase();
        if(!ALLOWED.contains(media)) throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,"不支持的文件类型");
        String supplied=suppliedName==null?"upload":suppliedName.replace('\\','/').replaceAll("[\\p{Cntrl}]","_");
        String original=Paths.get(supplied).getFileName().toString().replaceAll("[\\p{Cntrl}]","_").trim();
        if(original.isBlank()) original="upload";
        if(original.length()>240) original=original.substring(original.length()-240);
        String key=UUID.randomUUID().toString();
        Path target=null;
        try(InputStream in=source) {
            byte[] prefix=in.readNBytes(8);
            if(!validSignature(media,prefix)) throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,"文件内容与声明的类型不匹配");
            Files.createDirectories(root);
            target=root.resolve(key).normalize();
            if(!target.getParent().equals(root)) throw new IllegalStateException("Invalid storage key");
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            try(var out=Files.newOutputStream(target,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)) {
                digest.update(prefix); out.write(prefix);
                byte[] buffer=new byte[8192]; int n; long written=prefix.length;
                while((n=in.read(buffer))!=-1) {
                    written+=n;
                    if(written>limit) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"文件超过允许大小");
                    digest.update(buffer,0,n); out.write(buffer,0,n);
                }
                if(written!=size) throw new IOException("File size changed while storing");
            }
            return new StoredFile(key,original,media,size,HexFormat.of().formatHex(digest.digest()),target);
        } catch(ResponseStatusException e) {
            if(target!=null) deleteQuietly(key);
            throw e;
        } catch(Exception e) {
            if(target!=null) deleteQuietly(key);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"文件保存失败",e);
        }
    }

    private boolean validSignature(String media,byte[] b) {
        return switch(media) {
            case "application/pdf" -> starts(b,0x25,0x50,0x44,0x46,0x2d);
            case "image/png" -> starts(b,0x89,0x50,0x4e,0x47,0x0d,0x0a,0x1a,0x0a);
            case "image/jpeg" -> starts(b,0xff,0xd8,0xff);
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> starts(b,0x50,0x4b,0x03,0x04);
            case "text/plain" -> !containsZero(b);
            default -> false;
        };
    }
    private boolean starts(byte[] b,int... values){if(b.length<values.length)return false;for(int i=0;i<values.length;i++)if((b[i]&255)!=values[i])return false;return true;}
    private boolean containsZero(byte[] b){for(byte x:b)if(x==0)return true;return false;}

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
