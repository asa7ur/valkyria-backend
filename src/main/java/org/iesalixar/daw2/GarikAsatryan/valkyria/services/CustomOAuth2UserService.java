package org.iesalixar.daw2.GarikAsatryan.valkyria.services;

import lombok.RequiredArgsConstructor;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.Role;
import org.iesalixar.daw2.GarikAsatryan.valkyria.entities.User;
import org.iesalixar.daw2.GarikAsatryan.valkyria.repositories.RoleRepository;
import org.iesalixar.daw2.GarikAsatryan.valkyria.repositories.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;


@Service
@RequiredArgsConstructor
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private final OAuth2UserPersistenceService persistenceService;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);
        persistenceService.syncUser(oAuth2User);
        return oAuth2User;
    }

    @Service
    @RequiredArgsConstructor
    static class OAuth2UserPersistenceService {

        private final UserRepository userRepository;
        private final RoleRepository roleRepository;
        private final PasswordEncoder passwordEncoder;

        @Transactional
        public void syncUser(OAuth2User oAuth2User) {
            String email = oAuth2User.getAttribute("email");
            String givenName = oAuth2User.getAttribute("given_name");
            String familyName = oAuth2User.getAttribute("family_name");
            String providerId = oAuth2User.getAttribute("sub");

            userRepository.findByEmail(email).ifPresentOrElse(
                existing -> {
                    if (!"GOOGLE".equals(existing.getAuthProvider())) {
                        existing.setAuthProvider("GOOGLE");
                        existing.setProviderId(providerId);
                        userRepository.save(existing);
                    }
                },
                () -> {
                    User user = new User();
                    user.setEmail(email);
                    user.setFirstName(givenName != null ? givenName : email.split("@")[0]);
                    user.setLastName(familyName != null ? familyName : "");
                    user.setAuthProvider("GOOGLE");
                    user.setProviderId(providerId);
                    user.setEnabled(true);
                    // Password aleatorio que nadie puede adivinar — los usuarios OAuth no usan contraseña
                    user.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));

                    Role userRole = roleRepository.findByName("USER")
                        .orElseThrow(() -> new RuntimeException("Role USER not found"));
                    user.setRoles(List.of(userRole));
                    userRepository.save(user);
                }
            );
        }
    }
}
