package br.com.digitado.domain;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Registro de cada tentativa na Palavra do Dia.
 *
 * - Usuário logado: o login é gravado e a constraint única (data, login) garante
 *   UMA tentativa por conta por dia — validada no backend, não no navegador.
 * - Visitante anônimo: a linha é gravada com login nulo (para contabilizar) e o
 *   bloqueio de repetição é feito por cookie httpOnly emitido pelo servidor.
 */
@Entity
@Table(name = "palavra_do_dia_tentativa")
public class PalavraDoDiaTentativa implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // Dia da palavra (fuso de Brasília)
    @Column(name = "data", nullable = false)
    private LocalDate data;

    // Login do usuário autenticado; nulo para visitantes anônimos
    @Column(name = "login", length = 100)
    private String login;

    @Column(name = "acertou", nullable = false)
    private Boolean acertou;

    // Palavra sorteada no dia — referencia palavra.id
    @Column(name = "palavra_id", nullable = false)
    private Long palavraId;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm = Instant.now();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDate getData() {
        return data;
    }

    public PalavraDoDiaTentativa data(LocalDate data) {
        this.data = data;
        return this;
    }

    public void setData(LocalDate data) {
        this.data = data;
    }

    public String getLogin() {
        return login;
    }

    public PalavraDoDiaTentativa login(String login) {
        this.login = login;
        return this;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public Boolean getAcertou() {
        return acertou;
    }

    public PalavraDoDiaTentativa acertou(Boolean acertou) {
        this.acertou = acertou;
        return this;
    }

    public void setAcertou(Boolean acertou) {
        this.acertou = acertou;
    }

    public Long getPalavraId() {
        return palavraId;
    }

    public PalavraDoDiaTentativa palavraId(Long palavraId) {
        this.palavraId = palavraId;
        return this;
    }

    public void setPalavraId(Long palavraId) {
        this.palavraId = palavraId;
    }

    public Instant getCriadoEm() {
        return criadoEm;
    }

    public void setCriadoEm(Instant criadoEm) {
        this.criadoEm = criadoEm;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PalavraDoDiaTentativa)) return false;
        return getId() != null && getId().equals(((PalavraDoDiaTentativa) o).getId());
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    @Override
    public String toString() {
        return (
            "PalavraDoDiaTentativa{id=" + getId() + ", data=" + getData() + ", login='" + getLogin() + "', acertou=" + getAcertou() + "}"
        );
    }
}
