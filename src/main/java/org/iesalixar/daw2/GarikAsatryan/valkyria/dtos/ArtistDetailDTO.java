package org.iesalixar.daw2.GarikAsatryan.valkyria.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ArtistDetailDTO {
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
    private String description;
    private String officialUrl;
    private String instagramUrl;
    private String tiktokUrl;
    private String youtubeUrl;
    private String tidalUrl;
    private String spotifyUrl;
    private List<ArtistImageDTO> images;
}