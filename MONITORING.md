# Monitoramento e Debugging — DIGITADO

Como o projeto atende às 10 regras de observabilidade para produção, o que já vem do
JHipster/Spring Boot e o que foi adicionado.

## 1. Request ID único por requisição

- **Implementação**: [`RequestIdFilter`](src/main/java/br/com/digitado/web/filter/RequestIdFilter.java)
  roda antes de tudo, aceita um `X-Request-ID` externo (gateway/proxy) ou gera um UUID,
  coloca no MDC e devolve no header da resposta.
- **Uso**: todo log da requisição carrega o ID (padrão do console e JSON). Peça o
  `X-Request-ID` ao usuário que reportou o problema e filtre os logs por ele.

## 2. Erros com stack trace completo e contexto

- **Implementação**: [`ExceptionTranslator`](src/main/java/br/com/digitado/web/rest/errors/ExceptionTranslator.java)
  loga todo erro com método HTTP, path, status e usuário autenticado.
  5xx sai como `ERROR` com stack trace completo; 4xx sai como `WARN` sem stack (ruído).
- O corpo da resposta segue RFC 7807 (Problem Details); em produção, mensagens internas
  são higienizadas (comportamento padrão JHipster).

## 3. Logs estruturados em JSON

- **Implementação**: `jhipster.logging.use-json-format: true` em
  [`application-prod.yml`](src/main/resources/config/application-prod.yml) — cada linha vira
  um objeto JSON (timestamp, nível, logger, mensagem, stack trace, MDC incluindo `requestId`),
  pronto para Loki/ELK/CloudWatch.
- Em dev os logs continuam legíveis para humanos, com o `requestId` no padrão do console
  ([`logback-spring.xml`](src/main/resources/logback-spring.xml)).
- Para encaminhar a um Logstash via TCP: `jhipster.logging.logstash.enabled: true`.

## 4. Health check com status detalhado

- `GET /management/health` — agregado; detalhes completos para ADMIN
  (`show-details: when_authorized`).
- `GET /management/health/liveness` e `/readiness` — probes para orquestradores
  (o readiness inclui o banco).
- **Adicionado**: [`JogoSalaHealthIndicator`](src/main/java/br/com/digitado/service/JogoSalaHealthIndicator.java)
  expõe o estado do domínio: salas em memória, jogos em andamento e alunos conectados.
  Importante em incidentes: o estado dos jogos é em memória — restart com
  `jogosEmAndamento > 0` derruba partidas.

## 5. Query logging com tempo

- **Adicionado**: `hibernate.session.events.log.LOG_QUERIES_SLOWER_THAN_MS: 250` em
  [`application.yml`](src/main/resources/config/application.yml) — qualquer query acima de
  250 ms é logada com o tempo de execução (logger `org.hibernate.SQL_SLOW`, em todos os perfis).
- Em dev, `org.hibernate.SQL: DEBUG` já loga todas as queries.
- Métricas de tempo por repositório Spring Data já são coletadas
  (`management.metrics.data.repository.autotime`) e expostas no Prometheus.

## 6. Cache hit/miss tracking

- **Estado atual**: a aplicação não tem camada de cache (sem Spring Cache, second-level
  cache do Hibernate desabilitado) — não há hit/miss a rastrear.
- **Quando adicionar cache**: use Spring Cache (`@EnableCaching` + Caffeine/Ehcache);
  o Micrometer registra automaticamente `cache_gets{result="hit|miss"}` por cache,
  visível em `/management/prometheus` sem código extra.

## 7. Métricas de performance (tempo, memória, CPU)

- `GET /management/jhimetrics` — visão consolidada (ADMIN).
- `GET /management/prometheus` — formato Prometheus, **habilitado também em prod**
  (estava desligado; corrigido em `application-prod.yml`).
- Já coletados: latência HTTP com percentis (p50/p75/p95/p99) por endpoint, heap/GC,
  CPU do processo e do sistema, threads, pool Hikari, tempo por repositório.
- `GET /management/threaddump` para depurar travamentos (ADMIN).

## 8. Testes de regressão dos fluxos críticos

- **Adicionado**: [`MinhasConquistasResourceIT`](src/test/java/br/com/digitado/web/rest/MinhasConquistasResourceIT.java)
  cobre o fluxo de conquistas: resolução da identidade pelo token, isolamento entre contas,
  401 para anônimo, 403 para auto-premiação/alteração de catálogo e presença do `X-Request-ID`.
- Já existentes: ITs de todos os recursos REST (`src/test/java/br/com/digitado/web/rest/`).
- Rodar: `npm run backend:unit:test` (backend) e `npm test` (frontend).

## 9. Alertas configuráveis para anomalias

- **Adicionado**: [`alert-rules.yml`](src/main/docker/prometheus/alert-rules.yml) com alertas de:
  instância fora do ar, taxa de 5xx > 5%, latência p95 > 1s, heap > 90%, CPU > 90% e
  pool de conexões esgotado. Thresholds ajustáveis por edição do arquivo.
- Roteamento por [`alertmanager.yml`](src/main/docker/alertmanager/alertmanager.yml)
  (troque o receiver placeholder por Slack/e-mail/webhook do time).
- Subir o stack completo (Prometheus + Alertmanager + Grafana):
  ```bash
  docker compose -f src/main/docker/monitoring.yml up -d
  ```
  Prometheus: `http://localhost:9090` · Alertmanager: `http://localhost:9093` ·
  Grafana: `http://localhost:3000` (admin/admin, dashboard JVM provisionado).

## 10. Deploy monitorado com rollback automático

- **Adicionado**: [`scripts/deploy-with-rollback.sh`](scripts/deploy-with-rollback.sh):
  preserva o jar atual, sobe a nova versão, monitora `/management/health/readiness`
  (30 tentativas × 5s, configurável) e, em caso de falha, restaura e religa a versão
  anterior automaticamente. Exit codes: `0` sucesso, `1` rollback executado, `2` intervenção manual.
- O `server.shutdown: graceful` (prod) garante que requisições em andamento terminem
  antes da troca de versão.
- Em Kubernetes, o equivalente nativo é usar os probes de liveness/readiness (regra 4)
  com `RollingUpdate` — o kubelet faz o rollback sozinho.

## Fluxo de debugging em produção (resumo)

1. Alerta dispara (regra 9) → veja qual regra e desde quando no Alertmanager.
2. Abra o Grafana/Prometheus (regra 7) para dimensionar: latência? erro? recurso?
3. Filtre os logs JSON (regra 3) por `level=ERROR` na janela do alerta — cada erro tem
   stack trace e contexto (regra 2) e um `requestId` (regra 1) para reconstruir a requisição.
4. Se for lentidão, procure `org.hibernate.SQL_SLOW` (regra 5) e o health detalhado (regra 4).
5. Corrija, rode os testes de regressão (regra 8) e faça deploy com rollback automático (regra 10).
