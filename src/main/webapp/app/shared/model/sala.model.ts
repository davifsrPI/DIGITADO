import { IUsuario } from 'app/shared/model/usuario.model';

export interface ISala {
  // O código de acesso é a chave primária da sala — não existe id numérico
  codigo?: string;
  nome?: string;
  descricao?: string | null;
  ativo?: boolean | null;
  professor?: IUsuario | null;
  alunos?: IUsuario[] | null;
}

export const defaultValue: Readonly<ISala> = {
  ativo: false,
};
