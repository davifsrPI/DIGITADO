import { IPalavra } from 'app/shared/model/palavra.model';
import { IUsuario } from 'app/shared/model/usuario.model';

export interface IListaPalavras {
  id?: number;
  nomeLista?: string;
  descricao?: string | null;
  ativo?: boolean | null;
  palavras?: IPalavra[] | null;
  professor?: IUsuario | null;
}

export const defaultValue: Readonly<IListaPalavras> = {
  ativo: false,
};
