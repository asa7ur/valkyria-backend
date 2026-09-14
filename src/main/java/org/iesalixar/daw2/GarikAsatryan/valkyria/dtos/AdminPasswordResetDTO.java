package org.iesalixar.daw2.GarikAsatryan.valkyria.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import org.iesalixar.daw2.GarikAsatryan.valkyria.validation.FieldMatch;
import org.iesalixar.daw2.GarikAsatryan.valkyria.validation.PasswordPolicy;

/**
 * DTO para que el administrador establezca una nueva contraseña a un usuario (no conoce la actual).
 */
@Data
@FieldMatch(first = "newPassword", second = "confirmPassword", message = "{msg.register.error.passwords-match}")
public class AdminPasswordResetDTO {

    @NotBlank(message = "{msg.validation.password.required}")
    @Pattern(regexp = PasswordPolicy.REGEX, message = "{msg.validation.password.complexity}")
    private String newPassword;

    @NotBlank(message = "{msg.validation.password.required}")
    private String confirmPassword;
}
