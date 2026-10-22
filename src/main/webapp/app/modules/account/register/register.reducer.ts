import axios from 'axios';
import { createAsyncThunk, createSlice } from '@reduxjs/toolkit';

import { serializeAxiosError } from 'app/shared/reducers/reducer.utils';

// Exportado para o spec nao precisar manter uma copia: foi por essa copia
// desatualizada que os testes quebraram quando registrationBloqueada entrou.
export const initialState = {
  loading: false,
  registrationSuccess: false,
  registrationFailure: false,
  registrationBloqueada: false,
  // Qual cadastro já existe, quando é esse o motivo da recusa: o servidor diz se foi
  // o e-mail ou o login, e a tela avisa o que corrigir
  registrationConflito: null as 'EMAIL' | 'LOGIN' | null,
  errorMessage: null,
  successMessage: null,
};

export type RegisterState = Readonly<typeof initialState>;

// Actions

export const handleRegister = createAsyncThunk(
  'register/create_account',
  async (data: { login: string; apelido?: string; email: string; password: string; langKey?: string }) =>
    axios.post<any>('api/register', data),
  { serializeError: serializeAxiosError },
);

export const RegisterSlice = createSlice({
  name: 'register',
  initialState: initialState as RegisterState,
  reducers: {
    reset() {
      return initialState;
    },
  },
  extraReducers(builder) {
    builder
      .addCase(handleRegister.pending, state => {
        state.loading = true;
      })
      .addCase(handleRegister.rejected, (state, action) => {
        // 429 vem do RateLimitFilter, e precisa de aviso próprio: dizer "erro no
        // cadastro" faria a pessoa tentar de novo, o que renova o bloqueio.
        const erro = action.error as { message?: string; response?: { status?: number; data?: { message?: string } } };
        const status = erro?.response?.status ?? Number(/\b(\d{3})\b\s*$/.exec(erro?.message ?? '')?.[1]);
        // EmailAlreadyUsedException / LoginAlreadyUsedException do servidor
        const chave = erro?.response?.data?.message;

        return {
          ...initialState,
          registrationFailure: true,
          registrationBloqueada: status === 429,
          registrationConflito: chave === 'error.emailexists' ? 'EMAIL' : chave === 'error.userexists' ? 'LOGIN' : null,
          errorMessage: erro?.message,
        };
      })
      .addCase(handleRegister.fulfilled, () => ({
        ...initialState,
        registrationSuccess: true,
        successMessage: 'register.messages.success',
      }));
  },
});

export const { reset } = RegisterSlice.actions;

// Reducer
export default RegisterSlice.reducer;
