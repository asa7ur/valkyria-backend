package org.iesalixar.daw2.GarikAsatryan.valkyria.dtos;

import jakarta.validation.constraints.*;
import lombok.Data;
import org.iesalixar.daw2.GarikAsatryan.valkyria.validation.FieldMatch;
import org.iesalixar.daw2.GarikAsatryan.valkyria.validation.IsAdult;
import org.iesalixar.daw2.GarikAsatryan.valkyria.validation.PasswordPolicy;

import java.time.LocalDate;

/**
 * DTO para el proceso de registro de nuevos usuarios.
 */
@Data
@FieldMatch(
        first = "password",
        second = "confirmPassword",
        message = "{msg.validation.password.match}"
)
public class UserRegistrationDTO {

    @NotBlank(message = "{msg.validation.required}")
    @Email(message = "{msg.validation.email}")
    @Size(max = 100, message = "{msg.validation.size}")
    private String email;

    @NotBlank(message = "{msg.validation.required}")
    @Pattern(regexp = PasswordPolicy.REGEX, message = "{msg.validation.password.complexity}")
    private String password;

    @NotBlank(message = "{msg.validation.required}")
    private String confirmPassword;

    @NotBlank(message = "{msg.validation.required}")
    @Size(max = 100, message = "{msg.validation.size}")
    private String firstName;

    @NotBlank(message = "{msg.validation.required}")
    @Size(max = 100, message = "{msg.validation.size}")
    private String lastName;

    @NotNull(message = "{msg.validation.required}")
    @IsAdult
    private LocalDate birthDate;

    @NotBlank(message = "{msg.validation.required}")
    @Size(max = 30, message = "{msg.validation.size}")
    private String phone;
}