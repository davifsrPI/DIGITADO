// Política de senha do DIGITADO — mesma regra imposta pelo backend (AccountResource):
// mínimo 8 caracteres com pelo menos 1 maiúscula, 1 minúscula, 1 número e 1 especial.
// A validação aqui é só UX (feedback imediato); a palavra final é do servidor.
export const SENHA_FORTE_REGEX = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9]).{8,}$/;

export const MENSAGEM_SENHA_FORTE = 'A senha deve ter no mínimo 8 caracteres, com letra maiúscula, minúscula, número e caractere especial.';

// Validador no formato esperado pelo ValidatedField (react-jhipster):
// true quando válida; a mensagem de erro quando inválida
export const validarSenhaForte = (valor: string): true | string => SENHA_FORTE_REGEX.test(valor ?? '') || MENSAGEM_SENHA_FORTE;
