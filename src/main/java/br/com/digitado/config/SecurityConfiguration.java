package br.com.digitado.config;

import static org.springframework.security.config.Customizer.withDefaults;

import br.com.digitado.security.*;
import br.com.digitado.web.filter.SpaWebFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer.FrameOptionsConfig;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.servlet.util.matcher.MvcRequestMatcher;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;
import tech.jhipster.config.JHipsterProperties;

@Configuration
@EnableMethodSecurity(securedEnabled = true)
public class SecurityConfiguration {

    private final JHipsterProperties jHipsterProperties;

    public SecurityConfiguration(JHipsterProperties jHipsterProperties) {
        this.jHipsterProperties = jHipsterProperties;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, MvcRequestMatcher.Builder mvc) throws Exception {
        http
            .cors(withDefaults())
            .csrf(csrf -> csrf.disable())
            .addFilterAfter(new SpaWebFilter(), BasicAuthenticationFilter.class)
            .headers(headers ->
                headers
                    .contentSecurityPolicy(csp -> csp.policyDirectives(jHipsterProperties.getSecurity().getContentSecurityPolicy()))
                    .frameOptions(FrameOptionsConfig::sameOrigin)
                    .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                    .permissionsPolicyHeader(permissions ->
                        permissions.policy(
                            "camera=(), fullscreen=(self), geolocation=(), gyroscope=(), magnetometer=(), microphone=(), midi=(), payment=(), sync-xhr=()"
                        )
                    )
            )
            .authorizeHttpRequests(authz ->
                // prettier-ignore
                authz
                    .requestMatchers(mvc.pattern("/index.html"), mvc.pattern("/*.js"), mvc.pattern("/*.txt"), mvc.pattern("/*.json"), mvc.pattern("/*.map"), mvc.pattern("/*.css")).permitAll()
                    .requestMatchers(mvc.pattern("/*.ico"), mvc.pattern("/*.png"), mvc.pattern("/*.svg"), mvc.pattern("/*.webapp")).permitAll()
                    .requestMatchers(mvc.pattern("/app/**")).permitAll()
                    .requestMatchers(mvc.pattern("/i18n/**")).permitAll()
                    .requestMatchers(mvc.pattern("/content/**")).permitAll()
                    .requestMatchers(mvc.pattern("/swagger-ui/**")).hasAuthority(AuthoritiesConstants.ADMIN)
                    .requestMatchers(mvc.pattern(HttpMethod.POST, "/api/authenticate")).permitAll()
                    .requestMatchers(mvc.pattern(HttpMethod.GET, "/api/authenticate")).permitAll()
                    .requestMatchers(mvc.pattern("/api/register")).permitAll()
                    .requestMatchers(mvc.pattern("/api/activate")).permitAll()
                    .requestMatchers(mvc.pattern("/api/account/reset-password/init")).permitAll()
                    .requestMatchers(mvc.pattern("/api/account/reset-password/finish")).permitAll()
                    .requestMatchers(mvc.pattern("/api/public/**")).permitAll()
                    .requestMatchers(mvc.pattern("/websocket/**")).permitAll()
                    // Endpoints de escrita usados pelo próprio jogador: permanecem acessíveis a qualquer usuário autenticado.
                    // Precisam vir ANTES das regras de bloqueio abaixo (a primeira correspondência vence).
                    .requestMatchers(mvc.pattern(HttpMethod.POST, "/api/palavras/sugerir")).authenticated()
                    .requestMatchers(mvc.pattern(HttpMethod.POST, "/api/usuarios/alterar-senha")).authenticated()
                    // Manipulação do banco (CRUD administrativo): apenas ADMIN pode escrever.
                    .requestMatchers(mvc.pattern(HttpMethod.POST, "/api/atividades")).hasAuthority(AuthoritiesConstants.ADMIN)
                    .requestMatchers(mvc.pattern(HttpMethod.POST, "/api/palavras")).hasAuthority(AuthoritiesConstants.ADMIN)
                    .requestMatchers(mvc.pattern(HttpMethod.POST, "/api/conquistas")).hasAuthority(AuthoritiesConstants.ADMIN)
                    .requestMatchers(mvc.pattern(HttpMethod.POST, "/api/rankings")).hasAuthority(AuthoritiesConstants.ADMIN)
                    .requestMatchers(mvc.pattern(HttpMethod.POST, "/api/respostas")).hasAuthority(AuthoritiesConstants.ADMIN)
                    .requestMatchers(mvc.pattern(HttpMethod.POST, "/api/lista-palavras")).hasAuthority(AuthoritiesConstants.ADMIN)
                    .requestMatchers(mvc.pattern(HttpMethod.POST, "/api/erro-ortograficos")).hasAuthority(AuthoritiesConstants.ADMIN)
                    .requestMatchers(mvc.pattern(HttpMethod.POST, "/api/usuarios")).hasAuthority(AuthoritiesConstants.ADMIN)
                    .requestMatchers(mvc.pattern(HttpMethod.POST, "/api/usuario-conquistas")).hasAuthority(AuthoritiesConstants.ADMIN)
                    .requestMatchers(mvc.pattern(HttpMethod.PUT, "/api/atividades/**"), mvc.pattern(HttpMethod.PATCH, "/api/atividades/**"), mvc.pattern(HttpMethod.DELETE, "/api/atividades/**")).hasAuthority(AuthoritiesConstants.ADMIN)
                    .requestMatchers(mvc.pattern(HttpMethod.PUT, "/api/palavras/**"), mvc.pattern(HttpMethod.PATCH, "/api/palavras/**"), mvc.pattern(HttpMethod.DELETE, "/api/palavras/**")).hasAuthority(AuthoritiesConstants.ADMIN)
                    .requestMatchers(mvc.pattern(HttpMethod.PUT, "/api/conquistas/**"), mvc.pattern(HttpMethod.PATCH, "/api/conquistas/**"), mvc.pattern(HttpMethod.DELETE, "/api/conquistas/**")).hasAuthority(AuthoritiesConstants.ADMIN)
                    .requestMatchers(mvc.pattern(HttpMethod.PUT, "/api/rankings/**"), mvc.pattern(HttpMethod.PATCH, "/api/rankings/**"), mvc.pattern(HttpMethod.DELETE, "/api/rankings/**")).hasAuthority(AuthoritiesConstants.ADMIN)
                    .requestMatchers(mvc.pattern(HttpMethod.PUT, "/api/respostas/**"), mvc.pattern(HttpMethod.PATCH, "/api/respostas/**"), mvc.pattern(HttpMethod.DELETE, "/api/respostas/**")).hasAuthority(AuthoritiesConstants.ADMIN)
                    .requestMatchers(mvc.pattern(HttpMethod.PUT, "/api/lista-palavras/**"), mvc.pattern(HttpMethod.PATCH, "/api/lista-palavras/**"), mvc.pattern(HttpMethod.DELETE, "/api/lista-palavras/**")).hasAuthority(AuthoritiesConstants.ADMIN)
                    .requestMatchers(mvc.pattern(HttpMethod.PUT, "/api/erro-ortograficos/**"), mvc.pattern(HttpMethod.PATCH, "/api/erro-ortograficos/**"), mvc.pattern(HttpMethod.DELETE, "/api/erro-ortograficos/**")).hasAuthority(AuthoritiesConstants.ADMIN)
                    .requestMatchers(mvc.pattern(HttpMethod.PUT, "/api/usuarios/**"), mvc.pattern(HttpMethod.PATCH, "/api/usuarios/**"), mvc.pattern(HttpMethod.DELETE, "/api/usuarios/**")).hasAuthority(AuthoritiesConstants.ADMIN)
                    .requestMatchers(mvc.pattern(HttpMethod.PUT, "/api/usuario-conquistas/**"), mvc.pattern(HttpMethod.PATCH, "/api/usuario-conquistas/**"), mvc.pattern(HttpMethod.DELETE, "/api/usuario-conquistas/**")).hasAuthority(AuthoritiesConstants.ADMIN)
                    .requestMatchers(mvc.pattern("/api/admin/**")).hasAuthority(AuthoritiesConstants.ADMIN)
                    .requestMatchers(mvc.pattern("/api/**")).authenticated()
                    .requestMatchers(mvc.pattern("/v3/api-docs/**")).hasAuthority(AuthoritiesConstants.ADMIN)
                    .requestMatchers(mvc.pattern("/management/health")).permitAll()
                    .requestMatchers(mvc.pattern("/management/health/**")).permitAll()
                    .requestMatchers(mvc.pattern("/management/info")).permitAll()
                    .requestMatchers(mvc.pattern("/management/prometheus")).hasAuthority(AuthoritiesConstants.ADMIN)
                    .requestMatchers(mvc.pattern("/management/**")).hasAuthority(AuthoritiesConstants.ADMIN)
            )
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptions ->
                exceptions
                    .authenticationEntryPoint(new BearerTokenAuthenticationEntryPoint())
                    .accessDeniedHandler(new BearerTokenAccessDeniedHandler())
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(withDefaults()));
        return http.build();
    }

    @Bean
    MvcRequestMatcher.Builder mvc(HandlerMappingIntrospector introspector) {
        return new MvcRequestMatcher.Builder(introspector);
    }
}
