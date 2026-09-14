package org.iesalixar.daw2.GarikAsatryan.valkyria.dtos;


import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AuthRequestDTO {
    @NotBlank(message = "{msg.validation.required}")
    private String username;

    @NotBlank(message = "{msg.validation.required}")
    private String password;
}
