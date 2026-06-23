import { IUsuario } from 'app/shared/model/usuario.model';

export interface ISala {
  id?: number;
  nome?: string;
  codigo?: string;
  descricao?: string | null;
  ativo?: boolean | null;
  professor?: IUsuario | null;
  alunos?: IUsuario[] | null;
}

export const defaultValue: Readonly<ISala> = {
  ativo: false,
};
