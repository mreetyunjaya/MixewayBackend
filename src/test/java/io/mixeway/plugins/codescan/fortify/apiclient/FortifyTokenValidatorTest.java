package io.mixeway.plugins.codescan.fortify.apiclient;

import org.assertj.core.api.Assertions;
import org.junit.Test;

import java.time.LocalDateTime;

public class FortifyTokenValidatorTest {

    @Test
    public void should_return_invalid_information_when_token_is_null() {
        //given
        FortifyTokenValidator validator = new FortifyTokenValidator();

        //when
        boolean tokenValid = validator.isTokenValid(null, null);

        //then
        Assertions.assertThat(tokenValid).isFalse();
    }

    @Test
    public void should_return_valid_information_if_expiration_date_is_before_date_now() {
        //given
        FortifyTokenValidator validator = new FortifyTokenValidator();

        //when
        boolean tokenValid = validator.isTokenValid("token", LocalDateTime.now().minusDays(1));

        //then
        Assertions.assertThat(tokenValid).isTrue();
    }

    @Test
    public void should_return_invalid_information_if_expiration_date_is_after_date_now() {
        //given
        FortifyTokenValidator validator = new FortifyTokenValidator();

        //when
        boolean tokenValid = validator.isTokenValid("token", LocalDateTime.now().plusDays(1));

        //then
        Assertions.assertThat(tokenValid).isFalse();
    }
}