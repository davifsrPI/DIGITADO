import { IUsuario } from 'app/shared/model/usuario.model';

export interface ISala {
  // O código de acesso é a chave primária da sala, não existe id numérico
  codigo?: string;
  nome?: string;
  // O servidor devolve {descricao, modo} (coluna JSON); salas antigas, texto puro
  descricao?: string | { descricao?: string | null; modo?: string } | null;
  tipo?: string | null;
  privada?: boolean | null;
  dataCriacao?: string | null;
  ativo?: boolean | null;
  professor?: IUsuario | null;
  alunos?: IUsuario[] | null;
}

/**
 * O texto da descrição, venha ela como objeto JSON ou como texto puro.
 *
 * A tela de Salas renderizava o objeto direto na tabela - e React não desenha
 * objeto: a página inteira caía em "An unexpected error has occurred".
 */
export const textoDescricaoSala = (descricao: ISala['descricao']): string =>
  descricao == null ? '' : typeof descricao === 'string' ? descricao : (descricao.descricao ?? '');

export const defaultValue: Readonly<ISala> = {
  ativo: false,
};
