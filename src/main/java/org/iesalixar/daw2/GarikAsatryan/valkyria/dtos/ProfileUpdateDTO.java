package org.iesalixar.daw2.GarikAsatryan.valkyria.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.iesalixar.daw2.GarikAsatryan.valkyria.validation.IsAdult;

import java.time.LocalDate;

@Data
public class ProfileUpdateDTO {

    @NotBlank(message = "{msg.validation.required}")
    @Size(max = 100, message = "{msg.validation.size}")
    private String firstName;

    @NotBlank(message = "{msg.validation.required}")
    @Size(max = 100, message = "{msg.validation.size}")
    private String lastName;

    @NotBlank(message = "{msg.validation.required}")
    @Size(max = 30, message = "{msg.validation.size}")
    private String phone;

    @NotNull(message = "{msg.validation.required}")
    @IsAdult
    private LocalDate birthDate;
}
