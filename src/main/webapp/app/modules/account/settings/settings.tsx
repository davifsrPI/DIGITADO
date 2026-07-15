import './settings.scss';

import React, { useEffect, useMemo, useState } from 'react';
import { Storage, Translate, ValidatedField, ValidatedForm, isEmail, translate } from 'react-jhipster';
import { toast } from 'react-toastify';
import axios from 'axios';

import { languages, locales } from 'app/config/translation';
import { useAppDispatch, useAppSelector } from 'app/config/store';
import { getSession } from 'app/shared/reducers/authentication';
import { LoadingSpinner } from 'app/shared/layout/loading/loading-spinner';
import { reset, saveAccountSettings } from './settings.reducer';
import { useBodyClass } from 'app/shared/util/use-body-class';

export const SettingsPage = () => {
  const dispatch = useAppDispatch();
  const account = useAppSelector(state => state.authentication.account);
  const loading = useAppSelector(state => state.authentication.loading);
  const successMessage = useAppSelector(state => state.settings.successMessage);
  const formDefaultValues = useMemo(() => ({ ...account }), [account?.login]);

  useEffect(() => {
    dispatch(getSession());
    return () => {
      dispatch(reset());
    };
  }, []);

  // Classe no body: remove a moldura branca do card padrão do JHipster (jh-card)
  // e aplica o tema escuro da página, como nas demais telas do jogo
  useBodyClass('settings-page');

  useEffect(() => {
    if (successMessage) {
      toast.success(translate(successMessage));
    }
  }, [successMessage]);

  const handleValidSubmit = values => {
    dispatch(saveAccountSettings({ ...account, ...values }));
  };

  // ─── Direitos do titular (LGPD art. 18) ────────────────────────────────────
  const [confirmandoExclusao, setConfirmandoExclusao] = useState(false);
  const [senhaExclusao, setSenhaExclusao] = useState('');
  const [processando, setProcessando] = useState(false);

  // Exclusão: exige a senha atual (validada no backend) — token roubado não basta.
  // Após excluir, remove o token local e recarrega como visitante.
  const excluirConta = async () => {
    if (!senhaExclusao.trim() || processando) return;
    setProcessando(true);
    try {
      await axios.delete('/api/account', { data: { senha: senhaExclusao } });
      Storage.local.remove('jhi-authenticationToken');
      Storage.session.remove('jhi-authenticationToken');
      window.location.href = '/';
    } catch {
      toast.error('Senha incorreta ou falha na exclusão.');
      setProcessando(false);
    }
  };

  if (loading || !account?.login) {
    return <LoadingSpinner />;
  }

  return (
    <div className="st-wrapper">
      <div className="st-bg">
        <div className="st-shape one" />
        <div className="st-shape two" />
      </div>

      <div className="st-center">
        <h2 className="st-title">Configurações da conta</h2>
        <p className="st-sub">
          <Translate contentKey="settings.title" interpolate={{ username: account.login }}>
            Configurações de {account.login}
          </Translate>
        </p>

        <div className="st-card">
          <ValidatedForm id="settings-form" onSubmit={handleValidSubmit} defaultValues={formDefaultValues}>
            <div className="st-row">
              <ValidatedField
                className="st-field"
                name="firstName"
                label={translate('settings.form.firstname')}
                id="firstName"
                placeholder={translate('settings.form.firstname.placeholder')}
                validate={{
                  required: { value: true, message: translate('settings.messages.validate.firstname.required') },
                  minLength: { value: 1, message: translate('settings.messages.validate.firstname.minlength') },
                  maxLength: { value: 50, message: translate('settings.messages.validate.firstname.maxlength') },
                }}
                data-cy="firstname"
              />
              <ValidatedField
                className="st-field"
                name="lastName"
                label={translate('settings.form.lastname')}
                id="lastName"
                placeholder={translate('settings.form.lastname.placeholder')}
                validate={{
                  required: { value: true, message: translate('settings.messages.validate.lastname.required') },
                  minLength: { value: 1, message: translate('settings.messages.validate.lastname.minlength') },
                  maxLength: { value: 50, message: translate('settings.messages.validate.lastname.maxlength') },
                }}
                data-cy="lastname"
              />
            </div>

            {/* Apelido: o nome público do jogador — é o que os outros veem no
                ranking, no placar das salas e no pódio */}
            <ValidatedField
              className="st-field"
              name="apelido"
              label="Apelido (nome público no jogo)"
              id="apelido"
              placeholder="Como você quer aparecer no jogo"
              validate={{
                minLength: { value: 2, message: 'O apelido precisa de ao menos 2 caracteres.' },
                maxLength: { value: 30, message: 'O apelido pode ter no máximo 30 caracteres.' },
              }}
              data-cy="apelido"
            />

            <ValidatedField
              name="email"
              label={translate('global.form.email.label')}
              placeholder={translate('global.form.email.placeholder')}
              type="email"
              validate={{
                required: { value: true, message: translate('global.messages.validate.email.required') },
                minLength: { value: 5, message: translate('global.messages.validate.email.minlength') },
                maxLength: { value: 254, message: translate('global.messages.validate.email.maxlength') },
                validate: v => isEmail(v) || translate('global.messages.validate.email.invalid'),
              }}
              data-cy="email"
            />

            <ValidatedField type="select" id="langKey" name="langKey" label={translate('settings.form.language')} data-cy="langKey">
              {locales.map(locale => (
                <option value={locale} key={locale}>
                  {languages[locale].name}
                </option>
              ))}
            </ValidatedField>

            <button type="submit" className="st-save-btn" data-cy="submit">
              <Translate contentKey="settings.form.button">Salvar</Translate>
            </button>
          </ValidatedForm>
        </div>

        {/* ── Meus dados (LGPD art. 18): exclusão da conta ── */}
        <div className="st-card st-lgpd">
          <h3 className="st-lgpd-titulo">Meus dados (LGPD)</h3>
          <p className="st-lgpd-sub">Você pode excluir sua conta e todos os seus dados definitivamente.</p>

          {!confirmandoExclusao ? (
            <button type="button" className="st-lgpd-btn st-lgpd-btn--perigo" onClick={() => setConfirmandoExclusao(true)}>
              Excluir minha conta
            </button>
          ) : (
            <div className="st-lgpd-confirmacao" role="alertdialog" aria-label="Confirmar exclusão da conta">
              <p>
                <strong>Esta ação é irreversível.</strong> Sua conta, seu histórico de jogo, conquistas e pontuações serão apagados. Digite
                sua senha para confirmar:
              </p>
              <input
                type="password"
                className="st-lgpd-senha"
                placeholder="Sua senha atual"
                value={senhaExclusao}
                onChange={e => setSenhaExclusao(e.target.value)}
                autoComplete="current-password"
              />
              <div className="st-lgpd-confirmacao-acoes">
                <button type="button" className="st-lgpd-btn" onClick={() => setConfirmandoExclusao(false)}>
                  Cancelar
                </button>
                <button
                  type="button"
                  className="st-lgpd-btn st-lgpd-btn--perigo"
                  onClick={excluirConta}
                  disabled={!senhaExclusao.trim() || processando}
                  data-cy="confirmarExclusao"
                >
                  {processando ? 'Excluindo...' : 'Excluir definitivamente'}
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default SettingsPage;
