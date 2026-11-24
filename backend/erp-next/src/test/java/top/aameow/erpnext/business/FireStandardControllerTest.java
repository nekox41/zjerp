package top.aameow.erpnext.business;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
class FireStandardControllerTest {
    @Test void sourceOnlyAllowsSpecifiedWebsiteAndExplicitAccessHost(){
        assertEquals("https://b.1190119.com/article-423.htm",FireStandardController.source("https://b.1190119.com/article-423.htm",false));
        assertEquals("https://gf.cabr-fire.com/list-991.htm",FireStandardController.source("https://gf.cabr-fire.com/list-991.htm",true));
        for(String bad:new String[]{"http://www.1190119.com/","https://1190119.com.example.com/","https://example.com/1190119.com","https://www.1190119.com@example.com/","https://www.1190119.com:8080/","javascript:alert(1)","https://gf.cabr-fire.com/list-991.htm"})
            assertThrows(ResponseStatusException.class,()->FireStandardController.source(bad,false));
    }
}
