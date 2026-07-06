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

    // jhipster-needle-application-properties-property

    public Liquibase getLiquibase() {
        return liquibase;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
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
    }
    // jhipster-needle-application-properties-property-class
}
