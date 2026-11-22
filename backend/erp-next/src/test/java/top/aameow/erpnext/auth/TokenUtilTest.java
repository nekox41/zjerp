package top.aameow.erpnext.auth;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TokenUtilTest {
    @Test void generatedTokensAreHighEntropyAndOnlyPersistableAsHashes() {
        String first=TokenUtil.newToken(), second=TokenUtil.newToken();
        assertNotEquals(first,second);
        assertEquals(43,first.length());
        assertEquals(64,TokenUtil.sha256(first).length());
        assertNotEquals(first,TokenUtil.sha256(first));
        assertEquals(TokenUtil.sha256(first),TokenUtil.sha256(first));
    }
}