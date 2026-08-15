import axios from 'axios';
import { createAsyncThunk, createSlice } from '@reduxjs/toolkit';

import { serializeAxiosError } from 'app/shared/reducers/reducer.utils';

const initialState = {
  loading: false,
  registrationSuccess: false,
  registrationFailure: false,
  registrationBloqueada: false,
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
        const erro = action.error as { message?: string; response?: { status?: number } };
        const status = erro?.response?.status ?? Number(/\b(\d{3})\b\s*$/.exec(erro?.message ?? '')?.[1]);

        return {
          ...initialState,
          registrationFailure: true,
          registrationBloqueada: status === 429,
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
