import axios from 'axios';
import sinon from 'sinon';
import { configureStore } from '@reduxjs/toolkit';
import { TranslatorContext } from 'react-jhipster';

import register, { handleRegister, initialState, reset } from './register.reducer';

describe('Creating account tests', () => {
  beforeAll(() => {
    TranslatorContext.registerTranslations('pt-br', {});
  });

  it('should return the initial state', () => {
    expect(register(undefined, { type: '' })).toEqual({
      ...initialState,
    });
  });

  it('should detect a request', () => {
    expect(register(undefined, { type: handleRegister.pending.type })).toEqual({
      ...initialState,
      loading: true,
    });
  });

  it('should handle RESET', () => {
    expect(
      register(
        {
          loading: true,
          registrationSuccess: true,
          registrationFailure: true,
          registrationBloqueada: true,
          errorMessage: '',
          successMessage: '',
        },
        reset(),
      ),
    ).toEqual({
      ...initialState,
    });
  });

  it('should handle CREATE_ACCOUNT success', () => {
    expect(
      register(undefined, {
        type: handleRegister.fulfilled.type,
        payload: 'fake payload',
      }),
    ).toEqual({
      ...initialState,
      registrationSuccess: true,
      successMessage: 'register.messages.success',
    });
  });

  it('should handle CREATE_ACCOUNT failure', () => {
    const error = { message: 'fake error' };
    expect(
      register(undefined, {
        type: handleRegister.rejected.type,
        error,
      }),
    ).toEqual({
      ...initialState,
      registrationFailure: true,
      errorMessage: error.message,
    });
  });

  // O 429 do RateLimitFilter tem aviso proprio na tela: sem esta flag a pessoa
  // leria "erro no cadastro", tentaria de novo e renovaria o bloqueio.
  it('should mark CREATE_ACCOUNT failure as blocked on 429', () => {
    const resultado = register(undefined, {
      type: handleRegister.rejected.type,
      error: { message: 'Request failed with status code 429' },
    });
    expect(resultado.registrationBloqueada).toBe(true);
    expect(resultado.registrationFailure).toBe(true);
  });

  it('should not mark other failures as blocked', () => {
    const resultado = register(undefined, {
      type: handleRegister.rejected.type,
      error: { message: 'Request failed with status code 500' },
    });
    expect(resultado.registrationBloqueada).toBe(false);
    expect(resultado.registrationFailure).toBe(true);
  });

  describe('Actions', () => {
    let store;

    const resolvedObject = { value: 'whatever' };
    const getState = jest.fn();
    const dispatch = jest.fn();
    const extra = {};
    beforeEach(() => {
      store = configureStore({
        reducer: (state = [], action) => [...state, action],
      });
      axios.post = sinon.stub().returns(Promise.resolve(resolvedObject));
    });

    it('dispatches CREATE_ACCOUNT_PENDING and CREATE_ACCOUNT_FULFILLED actions', async () => {
      const arg = { login: '', email: '', password: '' };

      const result = await handleRegister(arg)(dispatch, getState, extra);

      const pendingAction = dispatch.mock.calls[0][0];
      expect(pendingAction.meta.requestStatus).toBe('pending');
      expect(handleRegister.fulfilled.match(result)).toBe(true);
      expect(result.payload).toBe(resolvedObject);
    });
    it('dispatches RESET actions', async () => {
      await store.dispatch(reset());
      expect(store.getState()).toEqual([expect.any(Object), expect.objectContaining(reset())]);
    });
  });
});
