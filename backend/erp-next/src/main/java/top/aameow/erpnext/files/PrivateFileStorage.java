package top.aameow.erpnext.files;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.io.File;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Set;
import java.util.UUID;

@Service
public class PrivateFileStorage {
    private final Path root;
    public PrivateFileStorage(@Value("${erp.files.root:/var/lib/erp-next/files}") String root) {
        this.root=Paths.get(root).toAbsolutePath().normalize();
    }
    public StoredFile store(MultipartFile file) { return store(file,25L*1024*1024); }
    private StoredFile store(MultipartFile file,long maxBytes) {
        if(file==null||file.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请选择非空文件");
        if(file.getSize()>maxBytes) throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,"文件超过允许大小");
        String media=file.getContentType()==null?"application/octet-stream":file.getContentType().split(";")[0].trim().toLowerCase();
        if(!Set.of("application/pdf","image/jpeg","image/png","text/plain","application/vnd.openxmlformats-officedocument.wordprocessingml.document").contains(media))
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,"仅支持 PDF、JPG、PNG、TXT、DOCX 文件");
        String supplied=file.getOriginalFilename()==null?"upload":file.getOriginalFilename().replace('\\','/').replaceAll("[\\p{Cntrl}]","_");
        String original=Paths.get(supplied).getFileName().toString().replaceAll("[\\p{Cntrl}]","_").trim();
        if(original.isBlank()) original="upload";
        if(original.length()>240) original=original.substring(original.length()-240);
        String key=UUID.randomUUID().toString();
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
                byte[] buf=new byte[8192]; int n;
                while((n=in.read(buf))!=-1){digest.update(buf,0,n);out.write(buf,0,n);}
            }
            return new StoredFile(key,original,media,file.getSize(),HexFormat.of().formatHex(digest.digest()),target);
        } catch(ResponseStatusException e) { throw e; }
        catch(Exception e) { throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,"文件保存失败",e); }
    }
    public StoredFile storeBytes(byte[] bytes,String originalName,String mediaType) {
        if(bytes==null||bytes.length==0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"生成文件为空");
        return store(new MemoryMultipartFile(bytes,originalName,mediaType),250L*1024*1024);
    }
    private static final class MemoryMultipartFile implements MultipartFile {
        private final byte[] bytes;
        private final String name;
        private final String media;
        MemoryMultipartFile(byte[] bytes,String name,String media) {
            this.bytes=bytes.clone(); this.name=name; this.media=media;
        }
        @Override public String getName() { return "file"; }
        @Override public String getOriginalFilename() { return name; }
        @Override public String getContentType() { return media; }
        @Override public boolean isEmpty() { return bytes.length==0; }
        @Override public long getSize() { return bytes.length; }
        @Override public byte[] getBytes() { return bytes.clone(); }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(bytes); }
        @Override public void transferTo(File dest) throws IOException { Files.write(dest.toPath(),bytes); }
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
