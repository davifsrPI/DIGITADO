package br.com.digitado.config;

import static org.springframework.security.config.Customizer.withDefaults;

import br.com.digitado.security.*;
import br.com.digitado.web.filter.SpaWebFilter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorityAuthorizationManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationManagers;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer.FrameOptionsConfig;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.oauth2.server.resource.web.access.BearerTokenAccessDeniedHandler;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.IpAddressAuthorizationManager;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.servlet.util.matcher.MvcRequestMatcher;
import org.springframework.web.servlet.handler.HandlerMappingIntrospector;
import tech.jhipster.config.JHipsterProperties;

@Configuration
@EnableMethodSecurity(securedEnabled = true)
public class SecurityConfiguration {

    private final JHipsterProperties jHipsterProperties;

    // Token de raspagem do Prometheus (application.monitoramento.token-metricas, ver
    // ApplicationProperties.Monitoramento). Lido por @Value para a configuração de
    // segurança não depender do bean de propriedades da aplicação.
    private final String tokenMetricas;

    public SecurityConfiguration(
        JHipsterProperties jHipsterProperties,
        @Value("${application.monitoramento.token-metricas:}") String tokenMetricas
    ) {
        this.jHipsterProperties = jHipsterProperties;
        this.tokenMetricas = tokenMetricas;
    }

    /**
     * O Prometheus mandou o token de métricas configurado? Comparação em tempo
     * constante; sem token configurado, este caminho fica fechado.
     */
    private AuthorizationManager<RequestAuthorizationContext> tokenDeMetricas() {
        return (autenticacao, contexto) -> {
            String esperado = tokenMetricas;
            String recebido = contexto.getRequest().getHeader("X-Token-Metricas");
            boolean ok =
                esperado != null &&
                !esperado.isBlank() &&
                recebido != null &&
                MessageDigest.isEqual(esperado.getBytes(StandardCharsets.UTF_8), recebido.getBytes(StandardCharsets.UTF_8));
            return new AuthorizationDecision(ok);
        };
    }

    /**
     * Conta de JOGADOR: autenticada, com ROLE_USER e SEM ROLE_ADMIN.
     *
     * A conta de administração administra (tabelas, usuários, métricas) e não joga.
     * Ela tinha as duas roles e, por isso, entrava em qualquer sala com o poder de
     * professor, criava salas, somava XP no ranking e via as respostas da partida
     * ao vivo. Barrar só no menu do front não basta: a regra mora aqui.
     */
    static AuthorizationManager<RequestAuthorizationContext> somenteJogador() {
        return (autenticacao, contexto) -> new AuthorizationDecision(ehJogador(autenticacao.get()));
    }

    /**
     * Qualquer um, inclusive o visitante anônimo, MENOS a conta de administração.
     * Serve à palavra do dia, que o visitante joga sem conta.
     */
    static AuthorizationManager<RequestAuthorizationContext> excetoAdmin() {
        return (autenticacao, contexto) -> new AuthorizationDecision(!temAutoridade(autenticacao.get(), AuthoritiesConstants.ADMIN));
    }

    static boolean ehJogador(Authentication autenticacao) {
        return (
            autenticacao != null &&
            autenticacao.isAuthenticated() &&
            !(autenticacao instanceof AnonymousAuthenticationToken) &&
            temAutoridade(autenticacao, AuthoritiesConstants.USER) &&
            !temAutoridade(autenticacao, AuthoritiesConstants.ADMIN)
        );
    }

    private static boolean temAutoridade(Authentication autenticacao, String autoridade) {
        return (
            autenticacao != null &&
            autenticacao.getAuthorities() != null &&
            autenticacao.getAuthorities().stream().anyMatch(a -> autoridade.equals(a.getAuthority()))
        );
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
                    // O JOGO é da conta de jogador (ver somenteJogador). A de administração
                    // não cria nem comanda sala, não joga o individual, o treino de idiomas
                    // nem a palavra do dia. Precisa vir ANTES de /api/public/** e das
                    // regras gerais abaixo: a primeira correspondência vence.
                    // Ficam com o admin só as telas de Entidades de sala: listar, ver,
                    // editar (PUT) e excluir, em /api/salas e /api/salas/{codigo}.
                    .requestMatchers(mvc.pattern(HttpMethod.POST, "/api/public/palavra-do-dia/tentar")).access(excetoAdmin())
                    // EXCEÇÃO: o duelo 1v1. O admin pode duelar - é o "desenvolvedor" da
                    // conquista "Vença de um Desenvolvedor". Ficam abertas a ele só as rotas
                    // que o duelo usa, e o SalaResource confere, sala por sala, que é um
                    // DUELO (criar sala de turma ou abrir uma continua proibido ao admin).
                    .requestMatchers(
                        mvc.pattern(HttpMethod.POST, "/api/salas"),
                        mvc.pattern(HttpMethod.GET, "/api/salas/codigo/novo"),
                        mvc.pattern(HttpMethod.GET, "/api/salas/1v1/publicas"),
                        mvc.pattern(HttpMethod.GET, "/api/salas/*/sou-professor"),
                        mvc.pattern(HttpMethod.GET, "/api/salas/*/configuracao"),
                        mvc.pattern(HttpMethod.GET, "/api/salas/*/audio"),
                        mvc.pattern(HttpMethod.GET, "/api/salas/*/meu-resumo"),
                        mvc.pattern(HttpMethod.GET, "/api/palavras/buscar"),
                        mvc.pattern(HttpMethod.POST, "/api/palavras/sugerir")
                    ).authenticated()
                    .requestMatchers(
                        mvc.pattern("/api/individual/**"),
                        mvc.pattern("/api/meu-desempenho"),
                        mvc.pattern(HttpMethod.POST, "/api/salas"),
                        mvc.pattern(HttpMethod.PATCH, "/api/salas/*"),
                        // tudo o que é DA PARTIDA: /codigo/novo, /1v1/publicas, /{codigo}/audio,
                        // /palavra-atual, /configuracao, /palavras, /identificacao, /relatorio,
                        // /estatisticas, /participantes, /meu-resumo, /resumo/{login}...
                        mvc.pattern("/api/salas/*/*"),
                        mvc.pattern("/api/salas/*/*/*"),
                        mvc.pattern(HttpMethod.POST, "/api/palavras/sugerir"),
                        mvc.pattern(HttpMethod.GET, "/api/palavras/buscar"),
                        mvc.pattern(HttpMethod.GET, "/api/palavras/sortear"),
                        mvc.pattern(HttpMethod.GET, "/api/conquistas/minhas")
                    ).access(somenteJogador())
                    .requestMatchers(mvc.pattern("/api/public/**")).permitAll()
                    .requestMatchers(mvc.pattern("/websocket/**")).permitAll()
                    // Endpoint de escrita usado pelo próprio titular: aberto a qualquer usuário autenticado.
                    // Precisa vir ANTES das regras de bloqueio abaixo (a primeira correspondência vence).
                    // (sugerir palavra, buscar, sortear e as próprias conquistas são do jogo: ver acima)
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
                    // Tabelas do banco (telas de Entidades): LEITURA e escrita só para ADMIN.
                    // Antes só a escrita era barrada, e qualquer conta logada lia pela API as
                    // respostas, os rankings e os cadastros de todo mundo - o menu escondido
                    // não protegia nada.
                    .requestMatchers(
                        mvc.pattern("/api/atividades/**"),
                        mvc.pattern("/api/respostas/**"),
                        mvc.pattern("/api/rankings/**"),
                        mvc.pattern("/api/lista-palavras/**"),
                        mvc.pattern("/api/erro-ortograficos/**"),
                        mvc.pattern("/api/usuarios/**"),
                        mvc.pattern("/api/usuario-conquistas/**"),
                        mvc.pattern("/api/conquistas/**"),
                        mvc.pattern("/api/palavras/**"),
                        mvc.pattern("/api/users/**"),
                        mvc.pattern("/api/authorities/**")
                    ).hasAuthority(AuthoritiesConstants.ADMIN)
                    .requestMatchers(mvc.pattern("/api/admin/**")).hasAuthority(AuthoritiesConstants.ADMIN)
                    .requestMatchers(mvc.pattern("/api/**")).authenticated()
                    .requestMatchers(mvc.pattern("/v3/api-docs/**")).hasAuthority(AuthoritiesConstants.ADMIN)
                    .requestMatchers(mvc.pattern("/management/health")).permitAll()
                    .requestMatchers(mvc.pattern("/management/health/**")).permitAll()
                    .requestMatchers(mvc.pattern("/management/info")).permitAll()
                    // O Prometheus raspa as métricas sem JWT (não tem como renovar um): aberto
                    // para quem chama da própria máquina, para quem manda o token de métricas
                    // (application.monitoramento.token-metricas) e para o admin logado
                    .requestMatchers(mvc.pattern("/management/prometheus")).access(
                        AuthorizationManagers.anyOf(
                            IpAddressAuthorizationManager.hasIpAddress("127.0.0.1"),
                            IpAddressAuthorizationManager.hasIpAddress("::1"),
                            tokenDeMetricas(),
                            AuthorityAuthorizationManager.hasAuthority(AuthoritiesConstants.ADMIN)
                        )
                    )
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
