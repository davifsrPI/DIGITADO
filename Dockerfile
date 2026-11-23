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
#
# espeak-ng gera no SERVIDOR o audio das palavras do ditado. Sem ele, o texto da
# palavra tem de ser transmitido ao aparelho do aluno para o navegador dele falar -
# e o estado do jogo vai para o topico que TODOS os alunos assinam, ou seja, a
# resposta chegaria ao aluno antes de ele responder. Ver PalavraAudioService.
#
# A voz do espeak e mais robotica que a do navegador; o espanhol do treino de
# idiomas usa o Piper, instalado logo abaixo.
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl espeak-ng \
    && rm -rf /var/lib/apt/lists/*

# Piper + voz em espanhol do treino de idiomas, embutidos na imagem: a voz sai
# do SERVIDOR e o aluno recebe so o som (nunca o texto da palavra). Cada arquivo
# e conferido pelo SHA-256 - os mesmos hashes de VozPiper.java; troque os dois
# juntos ao atualizar uma versao. Hash diferente, o build falha.
ARG PIPER_URL=https://github.com/rhasspy/piper/releases/download/2023.11.14-2/piper_linux_x86_64.tar.gz
ARG PIPER_SHA256=a50cb45f355b7af1f6d758c1b360717877ba0a398cc8cbe6d2a7a3a26e225992
ARG VOZ_ES_URL=https://huggingface.co/rhasspy/piper-voices/resolve/v1.0.0/es/es_ES/davefx/medium/es_ES-davefx-medium.onnx
ARG VOZ_ES_SHA256=6658b03b1a6c316ee4c265a9896abc1393353c2d9e1bca7d66c2c442e222a917
ARG VOZ_ES_JSON_SHA256=0e0dda87c732f6f38771ff274a6380d9252f327dca77aa2963d5fbdf9ec54842
RUN mkdir -p /opt/piper/vozes \
    && curl -fsSL -o /tmp/piper.tar.gz "$PIPER_URL" \
    && echo "$PIPER_SHA256  /tmp/piper.tar.gz" | sha256sum -c - \
    && tar -xzf /tmp/piper.tar.gz -C /opt/piper \
    && rm /tmp/piper.tar.gz \
    && curl -fsSL -o /opt/piper/vozes/es_ES-davefx-medium.onnx "$VOZ_ES_URL" \
    && echo "$VOZ_ES_SHA256  /opt/piper/vozes/es_ES-davefx-medium.onnx" | sha256sum -c - \
    && curl -fsSL -o /opt/piper/vozes/es_ES-davefx-medium.onnx.json "$VOZ_ES_URL.json" \
    && echo "$VOZ_ES_JSON_SHA256  /opt/piper/vozes/es_ES-davefx-medium.onnx.json" | sha256sum -c - \
    && test -x /opt/piper/piper/piper

# Rodar como root dentro do container e desnecessario: a aplicacao so precisa
# ler o proprio .jar e abrir a porta 8080.
RUN useradd --system --create-home --uid 1001 digitado
USER digitado
WORKDIR /app

COPY --from=build --chown=digitado /projeto/target/*.jar /app/digitado.jar

ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-Xms256m -Xmx768m" \
    DIGITADO_AUDIOPALAVRA_PIPER_PASTA=/opt/piper

EXPOSE 8080

# Forma 'sh -c' para que $JAVA_OPTS seja expandido; o 'exec' faz o Java virar
# o PID 1, entao o 'docker stop' chega no processo certo e o desligamento
# gracioso do Spring (server.shutdown: graceful) acontece de verdade.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/digitado.jar"]
