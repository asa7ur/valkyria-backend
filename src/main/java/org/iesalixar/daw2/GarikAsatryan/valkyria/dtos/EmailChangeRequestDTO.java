package org.iesalixar.daw2.GarikAsatryan.valkyria.dtos;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class EmailChangeRequestDTO {

    @NotBlank(message = "{msg.validation.required}")
    @Email(message = "{msg.validation.email}")
    @Size(max = 100, message = "{msg.validation.size}")
    private String newEmail;
}
