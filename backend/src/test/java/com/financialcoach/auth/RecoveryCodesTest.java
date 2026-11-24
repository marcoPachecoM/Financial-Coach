package com.financialcoach.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecoveryCodesTest {

    @Test
    void generatesEightDistinctReadableCodes() {
        List<String> codes = RecoveryCodes.generate(new SecureRandom());

        assertThat(codes).hasSize(8).doesNotHaveDuplicates()
                .allSatisfy(code -> assertThat(code).matches("[a-hj-km-np-z2-9]{4}-[a-hj-km-np-z2-9]{4}-[a-hj-km-np-z2-9]{4}"));
    }

    @Test
    void caseDashesAndSpacesDoNotMatter() {
        assertThat(RecoveryCodes.hash(" K7M2 9XQP-h3vt ")).isEqualTo(RecoveryCodes.hash("k7m2-9xqp-h3vt"));
        assertThat(RecoveryCodes.hash("k7m2-9xqp-h3vt")).hasSize(64).isNotEqualTo(RecoveryCodes.hash("k7m2-9xqp-h3vu"));
    }

    @Test
    void passwordPolicy() {
        org.assertj.core.api.Assertions.assertThatCode(
                () -> PasswordPolicy.check("password", "correct horse battery", "ana@example.com", "ana"))
                .doesNotThrowAnyException();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> PasswordPolicy.check("password", "short", null, null))
                .hasMessageContaining("1 field");
        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> PasswordPolicy.check("password", "Ana@Example.com", "ana@example.com", null))
                .isInstanceOf(com.financialcoach.shared.error.ValidationException.class);
    }
}
