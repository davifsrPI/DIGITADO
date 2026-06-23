export interface IConquista {
  id?: number;
  nome?: string;
  descricao?: string | null;
  xpRecompensa?: number | null;
}

export const defaultValue: Readonly<IConquista> = {};
