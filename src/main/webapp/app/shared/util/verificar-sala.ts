import axios from 'axios';

/**
 * Confere no servidor se o código digitado é de uma sala que existe, ANTES de sair
 * da tela onde ele foi digitado.
 *
 * Sem esta checagem, um código errado levava o aluno até a tela de nome e turma, e
 * ele preenchia tudo para só então descobrir que a sala não existia - ou nem isso:
 * a tela mostrava o aviso e o formulário juntos, e ele tentava entrar mesmo assim.
 *
 * GET /api/salas/{codigo} devolve 404 para código inexistente; qualquer outra falha
 * (rede, servidor fora) NÃO pode acusar o código de errado - o aluno ficaria
 * conferindo letra por letra um código que está certo.
 */
export type ResultadoVerificacao = { status: 'ok'; tipo: 'TURMA' | 'UM_V_UM' } | { status: 'inexistente' } | { status: 'falha' };

export const verificarSala = async (codigo: string): Promise<ResultadoVerificacao> => {
  try {
    const { data } = await axios.get<{ tipo?: string }>(`/api/salas/${encodeURIComponent(codigo)}`);
    return { status: 'ok', tipo: data?.tipo === 'UM_V_UM' ? 'UM_V_UM' : 'TURMA' };
  } catch (err: any) {
    const status = err?.response?.status;
    return status === 404 || status === 400 ? { status: 'inexistente' } : { status: 'falha' };
  }
};
