package top.aameow.erpnext.files;

import org.junit.jupiter.api.Test;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.security.MessageDigest;
import static org.junit.jupiter.api.Assertions.*;

class PhotoEncodingTest {
    private byte[] image(int w,int h)throws Exception{
        var image=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);var g=image.createGraphics();
        g.setPaint(new GradientPaint(0,0,Color.BLUE,w,h,Color.WHITE));g.fillRect(0,0,w,h);g.setColor(Color.BLACK);g.drawString("TEST",20,30);g.dispose();
        var out=new ByteArrayOutputStream();ImageIO.write(image,"png",out);image.flush();return out.toByteArray();
    }
    @Test void neverUpscalesAndKeepsOriginalBytes()throws Exception{
        var original=image(300,400);var before=MessageDigest.getInstance("SHA-256").digest(original);
        var output=PhotoEncoding.report(original,"print");
        assertEquals(300,output.width());assertEquals(400,output.height());assertTrue(output.effectiveDpi()<100);
        assertArrayEquals(before,MessageDigest.getInstance("SHA-256").digest(original));
    }
    @Test void fitsPrintAndThumbnailWithoutStretching()throws Exception{
        var original=image(1800,2400);var result=PhotoEncoding.report(original,"print");
        assertEquals(957,result.width());assertEquals(1276,result.height());assertEquals(300,result.effectiveDpi());
        var thumb=ImageIO.read(new ByteArrayInputStream(PhotoEncoding.thumbnail(original)));
        assertEquals(360,thumb.getWidth());assertEquals(480,thumb.getHeight());
        var compact=PhotoEncoding.report(original,"compact");assertTrue(compact.width()<result.width());
    }
}
