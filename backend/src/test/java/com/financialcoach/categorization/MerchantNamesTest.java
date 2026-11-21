package com.financialcoach.categorization;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MerchantNamesTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            La Tiendita de la Esquina    | la tiendita de la esquina
            '  OXXO #1234 - Centro '     | oxxo centro
            Café Niño                    | cafe nino
            UBER*TRIP_HELP/2026          | uber trip help
            Walmart   Express            | walmart express
            """)
    void normalizes(String input, String expected) {
        assertThat(MerchantNames.normalize(input)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({"'12345'", "'  #*/ '"})
    void namesWithoutLettersBecomeEmpty(String input) {
        assertThat(MerchantNames.normalize(input)).isEmpty();
    }
}
