package com.lovemaptually;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lovemaptually.auth.JwtService;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class JwtServiceTest {
    @Test void issuedTokenCanBeVerified() {
        JwtService jwt=new JwtService("test-secret-with-more-than-thirty-two-bytes",3600,new ObjectMapper());
        assertThat(jwt.verify(jwt.issue(42L))).isEqualTo(42L);
    }
    @Test void tamperedTokenIsRejected() {
        JwtService jwt=new JwtService("test-secret-with-more-than-thirty-two-bytes",3600,new ObjectMapper());
        String token=jwt.issue(42L);
        assertThatThrownBy(()->jwt.verify(token.substring(0,token.length()-1)+"x")).isInstanceOf(IllegalArgumentException.class);
    }
}
