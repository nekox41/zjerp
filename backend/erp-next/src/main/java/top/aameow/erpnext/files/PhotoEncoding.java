package top.aameow.erpnext.files;

import javax.imageio.*;
import javax.imageio.stream.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Iterator;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Original evidence is immutable. Only resize/re-encode the copy embedded in a report or thumbnail. */
public final class PhotoEncoding {
    private PhotoEncoding(){}
    public static final double WIDTH_MM=81,HEIGHT_MM=108;
    public record Encoded(byte[] bytes,int width,int height,int sourceWidth,int sourceHeight,int orientation,int effectiveDpi){}
    public static int dpi(String mode){return switch(mode){case "compact"->240;case "print"->300;case "detail"->360;default->throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"无效照片质量模式");};}
    public static Encoded report(byte[] original,String mode){
        int dpi=dpi(mode);
        return encode(original,(int)Math.round(WIDTH_MM/25.4*dpi),(int)Math.round(HEIGHT_MM/25.4*dpi),mode.equals("compact")?.83f:mode.equals("detail")?.92f:.88f,true);
    }
    public static byte[] thumbnail(byte[] original){return encode(original,480,480,.82f,false).bytes();}
    private static Encoded encode(byte[] original,int maxW,int maxH,float quality,boolean report){
        try(var input=ImageIO.createImageInputStream(new ByteArrayInputStream(original))){
            Iterator<ImageReader> readers=ImageIO.getImageReaders(input);
            if(!readers.hasNext())throw new IOException("unsupported image");
            ImageReader reader=readers.next();
            try{
                reader.setInput(input,true,true);
                int sw=reader.getWidth(0),sh=reader.getHeight(0),orientation=jpegOrientation(original);
                if(sw<=0||sh<=0||(long)sw*sh>60000000L)throw new IOException("image dimensions");
                int ow=orientation>=5?sh:sw,oh=orientation>=5?sw:sh;
                double ratio=Math.min(1.0,Math.min((double)maxW/ow,(double)maxH/oh));
                int tw=Math.max(1,(int)Math.round(ow*ratio)),th=Math.max(1,(int)Math.round(oh*ratio));
                var param=reader.getDefaultReadParam();
                // Decode at a bounded size, leaving at least twice the final resolution for high-quality resampling.
                int sub=Math.max(1,(int)Math.floor(1.0/(ratio*2)));
                param.setSourceSubsampling(sub,sub,0,0);
                BufferedImage decoded=reader.read(0,param);
                if(decoded==null)throw new IOException("invalid image");
                int w=decoded.getWidth(),h=decoded.getHeight();
                AffineTransform rotate=switch(orientation){
                    case 2->new AffineTransform(-1,0,0,1,w,0);
                    case 3->new AffineTransform(-1,0,0,-1,w,h);
                    case 4->new AffineTransform(1,0,0,-1,0,h);
                    case 5->new AffineTransform(0,1,1,0,0,0);
                    case 6->new AffineTransform(0,1,-1,0,h,0);
                    case 7->new AffineTransform(0,-1,-1,0,h,w);
                    case 8->new AffineTransform(0,-1,1,0,0,w);
                    default->new AffineTransform();
                };
                BufferedImage resized=new BufferedImage(tw,th,BufferedImage.TYPE_INT_RGB);
                Graphics2D g=resized.createGraphics();
                try{
                    g.setColor(Color.WHITE);g.fillRect(0,0,tw,th);
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    g.setRenderingHint(RenderingHints.KEY_RENDERING,RenderingHints.VALUE_RENDER_QUALITY);
                    g.scale((double)tw/(orientation>=5?h:w),(double)th/(orientation>=5?w:h));
                    g.drawImage(decoded,rotate,null);
                }finally{g.dispose();decoded.flush();}
                var out=new ByteArrayOutputStream();
                ImageWriter writer=ImageIO.getImageWritersByFormatName("jpeg").next();
                try(var ios=ImageIO.createImageOutputStream(out)){
                    writer.setOutput(ios);var wp=writer.getDefaultWriteParam();wp.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);wp.setCompressionQuality(quality);
                    writer.write(null,new IIOImage(resized,null,null),wp);
                }finally{writer.dispose();resized.flush();}
                byte[] bytes=out.toByteArray();
                // Avoid generation loss on already-efficient originals, only when no orientation or scaling is needed.
                if(report&&orientation==1&&ratio==1.0&&original.length<=bytes.length*1.15)bytes=original;
                int effective=(int)Math.round(Math.max(tw/(WIDTH_MM/25.4),th/(HEIGHT_MM/25.4)));
                return new Encoded(bytes,tw,th,ow,oh,orientation,effective);
            }finally{reader.dispose();}
        }catch(IOException|RuntimeException e){if(e instanceof ResponseStatusException ex)throw ex;throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,"照片损坏或尺寸异常",e);}
    }
    // EXIF TIFF orientation in JPEG APP1. Read only the bounded segment and first IFD.
    static int jpegOrientation(byte[] b){
        if(b.length<4||(b[0]&255)!=255||(b[1]&255)!=216)return 1;
        try{
            int p=2;
            while(p+4<=b.length&&(b[p]&255)==255){
                while(p<b.length&&(b[p]&255)==255)p++;
                if(p>=b.length)return 1;
                int marker=b[p++]&255;if(marker==0xda||marker==0xd9)return 1;
                if(marker==0x01||marker>=0xd0&&marker<=0xd7)continue;
                if(p+2>b.length)return 1;
                int len=((b[p]&255)<<8)|(b[p+1]&255);if(len<2||p+len>b.length)return 1;
                int start=p+2,end=p+len;p=end;
                if(marker!=0xe1||end-start<14||b[start]!='E'||b[start+1]!='x'||b[start+2]!='i'||b[start+3]!='f'||b[start+4]!=0||b[start+5]!=0)continue;
                int t=start+6;boolean little=b[t]=='I'&&b[t+1]=='I';
                if(!little&&!(b[t]=='M'&&b[t+1]=='M'))return 1;
                if(u16(b,t+2,little)!=42)return 1;
                long off=u32(b,t+4,little);if(off<8||off>end-t-2)return 1;
                int ifd=t+(int)off,n=u16(b,ifd,little);
                for(int i=0;i<n;i++){
                    int entry=ifd+2+i*12;if(entry<0||entry+12>end)return 1;
                    if(u16(b,entry,little)==0x112&&u16(b,entry+2,little)==3&&u32(b,entry+4,little)==1){
                        int o=u16(b,entry+8,little);return o>=1&&o<=8?o:1;
                    }
                }
            }
        }catch(IndexOutOfBoundsException ignored){}
        return 1;
    }
    private static int u16(byte[] b,int p,boolean l){return l?(b[p]&255)|((b[p+1]&255)<<8):((b[p]&255)<<8)|(b[p+1]&255);}
    private static long u32(byte[] b,int p,boolean l){return l?(long)u16(b,p,true)|((long)u16(b,p+2,true)<<16):((long)u16(b,p,false)<<16)|u16(b,p+2,false);}
}
