package org.iesalixar.daw2.GarikAsatryan.valkyria.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ArtistDTO {
    private Long id;
    private String name;
    // Datos de contacto: solo se envían a MANAGER/ADMIN (para el público quedan a null y no se serializan)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String phone;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String email;
    private String genre;
    private String country;
    private String logo;
    private List<ArtistImageDTO> images;
}