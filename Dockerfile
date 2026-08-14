# ==========================================================================
#  Imagem do DIGITADO: compila o projeto inteiro (back-end + front-end) e
#  guarda o resultado pronto para rodar.
#
#  A maquina que sobe esta imagem nao precisa de JDK, Maven nem Node: eles
#  existem apenas na etapa 'build', que e descartada. A imagem final leva
#  somente o JRE 17 e o .jar da aplicacao.
#
#  Nao e usada sozinha: o docker-compose.yml da raiz sobe esta imagem junto
#  com o MySQL. Veja iniciar-container.ps1.
# ==========================================================================

# ------------------------------------------------------------  1. build ---
FROM maven:3.9-eclipse-temurin-17 AS build

WORKDIR /projeto

COPY . .

# O perfil 'prod' do pom.xml compila o front-end com o webpack e coloca o
# resultado dentro do .jar (target/classes/static). Por isso um processo so
# atende a API e a interface na porta 8080 - nao existe 'npm start' aqui.
#
# O Node nao vem da imagem: o frontend-maven-plugin baixa a versao do
# pom.xml (v22.15.0) dentro da pasta do projeto, entao a compilacao usa
# exatamente a mesma versao da maquina de desenvolvimento.
RUN mvn -B -ntp -Pprod -DskipTests clean package

# ----------------------------------------------------------  2. runtime ---
FROM eclipse-temurin:17-jre AS runtime

# curl atende ao healthcheck do docker-compose.yml, que e como o compose
# descobre que a aplicacao terminou de subir (e nao apenas que o processo
# iniciou). A imagem do Temurin nao traz curl.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

# Rodar como root dentro do container e desnecessario: a aplicacao so precisa
# ler o proprio .jar e abrir a porta 8080.
RUN useradd --system --create-home --uid 1001 digitado
USER digitado
WORKDIR /app

COPY --from=build --chown=digitado /projeto/target/*.jar /app/digitado.jar

ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-Xms256m -Xmx768m"

EXPOSE 8080

# Forma 'sh -c' para que $JAVA_OPTS seja expandido; o 'exec' faz o Java virar
# o PID 1, entao o 'docker stop' chega no processo certo e o desligamento
# gracioso do Spring (server.shutdown: graceful) acontece de verdade.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/digitado.jar"]
