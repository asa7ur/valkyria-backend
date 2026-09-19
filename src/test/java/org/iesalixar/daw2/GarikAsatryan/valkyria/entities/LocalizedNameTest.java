package org.iesalixar.daw2.GarikAsatryan.valkyria.entities;

import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.CampingType;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.TicketType;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class LocalizedNameTest {

    @ParameterizedTest
    @CsvSource(nullValues = "null", value = {
            "en,    General Pass, General Pass",
            "en-GB, General Pass, General Pass",
            "es,    General Pass, Abono General",
            "en,    null,         Abono General",
            "en,    '  ',         Abono General"
    })
    void localizedName_usesEnglishOnlyWhenRequestedAndTranslated(String language, String nameEn, String expected) {
        Locale locale = Locale.forLanguageTag(language);

        TicketType ticketType = new TicketType();
        ticketType.setName("Abono General");
        ticketType.setNameEn(nameEn);

        CampingType campingType = new CampingType();
        campingType.setName("Abono General");
        campingType.setNameEn(nameEn);

        assertThat(ticketType.localizedName(locale)).isEqualTo(expected);
        assertThat(campingType.localizedName(locale)).isEqualTo(expected);
    }
}
