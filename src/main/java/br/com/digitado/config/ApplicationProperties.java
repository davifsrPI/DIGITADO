package br.com.digitado.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Properties specific to DIGITADO.
 * <p>
 * Properties are configured in the {@code application.yml} file.
 * See {@link tech.jhipster.config.JHipsterProperties} for a good example.
 */
@ConfigurationProperties(prefix = "application", ignoreUnknownFields = false)
public class ApplicationProperties {

    private final Liquibase liquibase = new Liquibase();

    private final RateLimit rateLimit = new RateLimit();

    private final Websocket websocket = new Websocket();

    // jhipster-needle-application-properties-property

    public Liquibase getLiquibase() {
        return liquibase;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public Websocket getWebsocket() {
        return websocket;
    }

    // jhipster-needle-application-properties-property-getter

    public static class Liquibase {

        private Boolean asyncStart = true;

        public Boolean getAsyncStart() {
            return asyncStart;
        }

        public void setAsyncStart(Boolean asyncStart) {
            this.asyncStart = asyncStart;
        }
    }

    /**
     * Limite de requisições por identidade (proteção contra abuso/ataques).
     * Ajustável por ambiente em application*.yml sob "application.rate-limit".
     */
    public static class RateLimit {

        // Liga/desliga o filtro sem redeploy de código
        private boolean enabled = true;

        // Requisições por minuto em /api/** para cada identidade (login ou IP)
        private int requisicoesPorMinuto = 100;

        // Limite mais rígido para autenticação (barra força bruta de senha)
        private int autenticacaoPorMinuto = 10;

        // só liga se estiver atrás de um proxy confiável (nginx/Caddy) que
        // reescreve o X-Forwarded-For. Direto na internet dá pra forjar o header
        // e furar o rate limit.
        private boolean confiarXForwardedFor = false;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getRequisicoesPorMinuto() {
            return requisicoesPorMinuto;
        }

        public void setRequisicoesPorMinuto(int requisicoesPorMinuto) {
            this.requisicoesPorMinuto = requisicoesPorMinuto;
        }

        public int getAutenticacaoPorMinuto() {
            return autenticacaoPorMinuto;
        }

        public void setAutenticacaoPorMinuto(int autenticacaoPorMinuto) {
            this.autenticacaoPorMinuto = autenticacaoPorMinuto;
        }

        public boolean isConfiarXForwardedFor() {
            return confiarXForwardedFor;
        }

        public void setConfiarXForwardedFor(boolean confiarXForwardedFor) {
            this.confiarXForwardedFor = confiarXForwardedFor;
        }
    }

    /**
     * Configuração do endpoint WebSocket (application.websocket em application*.yml).
     */
    public static class Websocket {

        /**
         * Origens aceitas no handshake, separadas por vírgula.
         * - "*": qualquer origem (conveniência de DEV — o front roda em localhost:9000
         *   e o backend em localhost:8080; a autenticação real continua sendo o JWT);
         * - vazio/em branco: NENHUM padrão é registrado e vale o default do Spring,
         *   que só aceita handshake da MESMA origem — é o valor de PRODUÇÃO, já que
         *   front e backend são servidos pelo mesmo domínio;
         * - lista explícita (ex.: "https://app.exemplo.com.br"): para front hospedado
         *   em domínio separado.
         */
        private String allowedOrigins = "*";

        public String getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(String allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }
    }
    // jhipster-needle-application-properties-property-class
}
