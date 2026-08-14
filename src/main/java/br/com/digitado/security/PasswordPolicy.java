package br.com.digitado.security;

import java.util.regex.Pattern;

/**
 * Política ÚNICA de senha do DIGITADO, aplicada no servidor: mínimo 8 caracteres
 * com pelo menos 1 letra maiúscula, 1 minúscula, 1 número e 1 caractere especial.
 *
 * Usada em TODOS os caminhos que aceitam senha nova - registro, troca e
 * redefinição da conta (AccountResource) e a senha legada do perfil de jogo
 * (UsuarioResource.alterarSenha, que também confirma a exclusão LGPD da conta).
 * O frontend replica a regra em senha-utils.ts apenas como UX.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 100;

    private static final Pattern SENHA_FORTE = Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).+$");

    private PasswordPolicy() {}

    public static boolean isInvalid(String password) {
        return (
            password == null ||
            password.isBlank() ||
            password.length() < MIN_LENGTH ||
            password.length() > MAX_LENGTH ||
            !SENHA_FORTE.matcher(password).matches()
        );
    }
}
