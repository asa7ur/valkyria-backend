package org.iesalixar.daw2.GarikAsatryan.valkyria.entities;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.Locale;

@Entity
@Table(name = "camping_types")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CampingType {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "name_en", length = 50)
    private String nameEn;

    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "stock_total", nullable = false)
    private Integer stockTotal;

    @Column(name = "stock_available", nullable = false)
    private Integer stockAvailable;

    /** Nombre en inglés si el idioma es inglés y hay traducción; si no, el nombre en español. */
    public String localizedName(Locale locale) {
        boolean english = Locale.ENGLISH.getLanguage().equals(locale.getLanguage());
        return english && nameEn != null && !nameEn.isBlank() ? nameEn : name;
    }
}