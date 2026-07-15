import '../settings/settings.scss';

import React, { useEffect, useState } from 'react';
import { Translate, ValidatedField, ValidatedForm, translate } from 'react-jhipster';
import { toast } from 'react-toastify';

import { useAppDispatch, useAppSelector } from 'app/config/store';
import { getSession } from 'app/shared/reducers/authentication';
import PasswordStrengthBar from 'app/shared/layout/password/password-strength-bar';
import { validarSenhaForte } from 'app/shared/util/senha-utils';
import { reset, savePassword } from './password.reducer';
import { useBodyClass } from 'app/shared/util/use-body-class';

// Tela de troca de senha — mesmo tema escuro e mesma estrutura visual das
// Configurações da conta (reusa as classes st-* de settings.scss)
export const PasswordPage = () => {
  const [password, setPassword] = useState('');
  const dispatch = useAppDispatch();

  useEffect(() => {
    dispatch(reset());
    dispatch(getSession());
    return () => {
      dispatch(reset());
    };
  }, []);

  // Classe no body: remove a moldura branca do card padrão do JHipster (jh-card)
  // e aplica o tema escuro — o estilo mora em settings.scss (body.settings-page)
  useBodyClass('settings-page');

  const handleValidSubmit = ({ currentPassword, newPassword }) => {
    dispatch(savePassword({ currentPassword, newPassword }));
  };

  const updatePassword = event => setPassword(event.target.value);

  const account = useAppSelector(state => state.authentication.account);
  const successMessage = useAppSelector(state => state.password.successMessage);
  const errorMessage = useAppSelector(state => state.password.errorMessage);

  useEffect(() => {
    if (successMessage) {
      toast.success(translate(successMessage));
    } else if (errorMessage) {
      toast.error(translate(errorMessage));
    }
    dispatch(reset());
  }, [successMessage, errorMessage]);

  return (
    <div className="st-wrapper">
      <div className="st-bg">
        <div className="st-shape one" />
        <div className="st-shape two" />
      </div>

      <div className="st-center">
        <h2 className="st-title" id="password-title">
          Alterar senha
        </h2>
        <p className="st-sub">
          <Translate contentKey="password.title" interpolate={{ username: account.login }}>
            Password for {account.login}
          </Translate>
        </p>

        <div className="st-card">
          <ValidatedForm id="password-form" onSubmit={handleValidSubmit}>
            <ValidatedField
              className="st-field"
              name="currentPassword"
              label={translate('global.form.currentpassword.label')}
              placeholder={translate('global.form.currentpassword.placeholder')}
              type="password"
              validate={{
                required: { value: true, message: translate('global.messages.validate.newpassword.required') },
              }}
              data-cy="currentPassword"
            />
            <ValidatedField
              className="st-field"
              name="newPassword"
              label={translate('global.form.newpassword.label')}
              placeholder={translate('global.form.newpassword.placeholder')}
              type="password"
              validate={{
                required: { value: true, message: translate('global.messages.validate.newpassword.required') },
                minLength: { value: 8, message: translate('global.messages.validate.newpassword.minlength') },
                maxLength: { value: 50, message: translate('global.messages.validate.newpassword.maxlength') },
                validate: validarSenhaForte,
              }}
              onChange={updatePassword}
              data-cy="newPassword"
            />
            <PasswordStrengthBar password={password} />
            <ValidatedField
              className="st-field"
              name="confirmPassword"
              label={translate('global.form.confirmpassword.label')}
              placeholder={translate('global.form.confirmpassword.placeholder')}
              type="password"
              validate={{
                required: { value: true, message: translate('global.messages.validate.confirmpassword.required') },
                minLength: { value: 8, message: translate('global.messages.validate.confirmpassword.minlength') },
                maxLength: { value: 50, message: translate('global.messages.validate.confirmpassword.maxlength') },
                validate: v => v === password || translate('global.messages.error.dontmatch'),
              }}
              data-cy="confirmPassword"
            />
            <button type="submit" className="st-save-btn" data-cy="submit">
              <Translate contentKey="password.form.button">Save</Translate>
            </button>
          </ValidatedForm>
        </div>
      </div>
    </div>
  );
};

export default PasswordPage;
