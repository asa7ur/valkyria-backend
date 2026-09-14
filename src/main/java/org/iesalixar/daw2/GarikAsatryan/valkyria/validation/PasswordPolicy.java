package org.iesalixar.daw2.GarikAsatryan.valkyria.validation;

/**
 * Política de contraseñas común a registro, cambio de contraseña y reseteo por el administrador.
 */
public final class PasswordPolicy {

    // Al menos 8 caracteres con una minúscula, una mayúscula, un número y un carácter especial
    public static final String REGEX = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=!]).{8,}$";

    private PasswordPolicy() {
    }
}
