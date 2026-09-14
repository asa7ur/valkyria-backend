package org.iesalixar.daw2.GarikAsatryan.valkyria.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.iesalixar.daw2.GarikAsatryan.valkyria.services.CustomUserDetailsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2AuthenticationSuccessHandler implements AuthenticationSuccessHandler {

    private final CustomUserDetailsService userDetailsService;
    private final OAuth2LoginCodeStore codeStore;

    @Value("${app.url}")
    private String appUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String email = oAuth2User.getAttribute("email");
        UserDetails userDetails = userDetailsService.loadUserByUsername(email);

        // La sesión HTTP solo hacía falta durante el flujo OAuth2; la API se autentica con JWT
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();

        if (!userDetails.isEnabled()) {
            response.sendRedirect(appUrl + "/login?error=account_disabled");
            return;
        }

        response.sendRedirect(appUrl + "/oauth2/callback?code=" + codeStore.issue(email));
    }
}
