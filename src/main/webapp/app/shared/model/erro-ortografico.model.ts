import { IResposta } from 'app/shared/model/resposta.model';
import { TipoErro } from 'app/shared/model/enumerations/tipo-erro.model';

export interface IErroOrtografico {
  id?: number;
  tipoErro?: keyof typeof TipoErro | null;
  descricao?: string | null;
  resposta?: IResposta | null;
}

export const defaultValue: Readonly<IErroOrtografico> = {};
