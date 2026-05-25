package org.iesalixar.daw2.GarikAsatryan.valkyria.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.iesalixar.daw2.GarikAsatryan.valkyria.dtos.*;
import org.springframework.web.bind.annotation.RequestParam;
import org.iesalixar.daw2.GarikAsatryan.valkyria.services.UserService;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final MessageSource messageSource;

    /**
     * Devuelve el perfil del usuario autenticado.
     */
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseDTO<UserDTO>> getMe(Authentication authentication) {
        UserDTO data = userService.getMe(authentication.getName());
        return ResponseEntity.ok(ResponseDTO.success(getMessage("msg.user.me.success"), data));
    }

    /**
     * Actualiza los datos personales del usuario autenticado (sin email, roles ni estado).
     */
    @PutMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseDTO<UserDTO>> updateMe(
            Authentication authentication,
            @Valid @RequestBody ProfileUpdateDTO dto) {
        UserDTO updated = userService.updateMe(authentication.getName(), dto);
        return ResponseEntity.ok(ResponseDTO.success(getMessage("msg.user.me.update.success"), updated));
    }

    /**
     * Cambia la contraseña del usuario autenticado.
     */
    @PatchMapping("/me/password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseDTO<Void>> changeMyPassword(
            Authentication authentication,
            @Valid @RequestBody PasswordChangeDTO dto) {
        userService.changePasswordByEmail(authentication.getName(), dto);
        return ResponseEntity.ok(ResponseDTO.success(getMessage("msg.user.me.password.success"), null));
    }

    @PostMapping("/me/email")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ResponseDTO<Void>> requestEmailChange(
            Authentication authentication,
            @Valid @RequestBody EmailChangeRequestDTO dto) {
        userService.requestEmailChange(authentication.getName(), dto.getNewEmail());
        return ResponseEntity.ok(ResponseDTO.success(getMessage("msg.email.change.sent"), null));
    }

    @GetMapping("/me/email/confirm")
    public ResponseEntity<ResponseDTO<Void>> confirmEmailChange(@RequestParam("token") String token) {
        userService.confirmEmailChange(token);
        return ResponseEntity.ok(ResponseDTO.success(getMessage("msg.email.change.success"), null));
    }

    /**
     * Lista paginada de usuarios con soporte para términos de búsqueda.
     */
    @GetMapping
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ResponseDTO<List<UserDTO>>> getAllUsers(@ModelAttribute FilterDTO filterDTO) {
        List<UserDTO> data = userService.getAllUsers(filterDTO);
        return ResponseEntity.ok(ResponseDTO.success(
                getMessage("msg.user.list.success"),
                data,
                filterDTO
        ));
    }

    /**
     * Obtiene los detalles de un usuario por ID.
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    public ResponseEntity<ResponseDTO<UserDTO>> getUserById(@PathVariable Long id) {
        UserDTO data = userService.getUserById(id);
        return ResponseEntity.ok(ResponseDTO.success(getMessage("msg.user.get.success"), data));
    }

    /**
     * Crea un usuario desde el panel de administración.
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ResponseDTO<UserDTO>> createUser(@Valid @RequestBody UserRegistrationDTO dto) {
        UserDTO created = userService.createUser(dto);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ResponseDTO.success(getMessage("msg.user.create.success"), created));
    }

    /**
     * Actualiza un usuario existente.
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ResponseDTO<UserDTO>> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UserUpdateDTO dto) {
        UserDTO updated = userService.updateUser(id, dto);
        return ResponseEntity.ok(ResponseDTO.success(getMessage("msg.user.update.success"), updated));
    }

    /**
     * Elimina un usuario.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ResponseDTO<Void>> deleteUser(@PathVariable Long id) {
        userService.deleteUser(id);
        return ResponseEntity.ok(ResponseDTO.success(getMessage("msg.user.delete.success"), null));
    }

    /**
     * Endpoint específico para cambiar la contraseña.
     * Aunque el admin gestiona usuarios, para cambiar la contraseña se suele pedir la actual por seguridad,
     * o si es un cambio forzado por admin, podrías crear otro método sin la validación de la 'current'.
     */
    @PatchMapping("/{id}/password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ResponseDTO<Void>> changePassword(
            @PathVariable Long id,
            @Valid @RequestBody PasswordChangeDTO dto) {
        userService.changePassword(id, dto);
        return ResponseEntity.ok(ResponseDTO.success(getMessage("msg.user.password.success"), null));
    }

    /**
     * Utilidad para resolver mensajes traducidos según el locale del cliente.
     */
    private String getMessage(String key) {
        return messageSource.getMessage(key, null, LocaleContextHolder.getLocale());
    }
}