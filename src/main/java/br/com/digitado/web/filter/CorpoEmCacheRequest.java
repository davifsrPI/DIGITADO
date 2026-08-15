package br.com.digitado.web.filter;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * Guarda o corpo da requisição em memória para que ele possa ser lido mais de uma vez.
 *
 * O RateLimitFilter precisa espiar QUAL conta está sendo tentada antes de deixar a
 * requisição seguir - é o que permite contar senha errada por conta, e não só por IP,
 * de modo que um aluno errando a senha não bloqueie os colegas que saem do mesmo IP
 * da escola. O corpo de uma requisição só pode ser lido uma vez: sem este wrapper, a
 * leitura no filtro esvaziaria o stream e o controller receberia um corpo vazio.
 *
 * Usado apenas no /api/authenticate, cujo corpo é um JSON de poucas dezenas de bytes.
 */
class CorpoEmCacheRequest extends HttpServletRequestWrapper {

    private final byte[] corpo;

    CorpoEmCacheRequest(HttpServletRequest request) throws IOException {
        super(request);
        this.corpo = request.getInputStream().readAllBytes();
    }

    byte[] corpo() {
        return corpo;
    }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream fonte = new ByteArrayInputStream(corpo);

        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return fonte.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
                // Leitura síncrona: o corpo já está inteiro em memória.
            }

            @Override
            public int read() {
                return fonte.read();
            }
        };
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }
}
