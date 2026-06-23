import { ISala } from 'app/shared/model/sala.model';
import { TipoUsuario } from 'app/shared/model/enumerations/tipo-usuario.model';

export interface IUsuario {
  id?: number;
  nome?: string;
  sobrenome?: string;
  email?: string;
  senha?: string;
  tipoUsuario?: keyof typeof TipoUsuario;
  ativo?: boolean | null;
  salasAlunos?: ISala[] | null;
}

export const defaultValue: Readonly<IUsuario> = {
  ativo: false,
};
