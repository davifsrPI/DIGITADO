import React, { useEffect } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Button, Col, Row } from 'reactstrap';
import { Translate, ValidatedField, ValidatedForm, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { mapIdList } from 'app/shared/util/entity-utils';
import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntities as getSalas } from 'app/entities/sala/sala.reducer';
import { TipoUsuario } from 'app/shared/model/enumerations/tipo-usuario.model';
import { createEntity, getEntity, reset, updateEntity } from './usuario.reducer';

export const UsuarioUpdate = () => {
  const dispatch = useAppDispatch();

  const navigate = useNavigate();

  const { id } = useParams<'id'>();
  const isNew = id === undefined;

  const salas = useAppSelector(state => state.sala.entities);
  const usuarioEntity = useAppSelector(state => state.usuario.entity);
  const loading = useAppSelector(state => state.usuario.loading);
  const updating = useAppSelector(state => state.usuario.updating);
  const updateSuccess = useAppSelector(state => state.usuario.updateSuccess);
  const tipoUsuarioValues = Object.keys(TipoUsuario);

  const handleClose = () => {
    navigate('/usuario');
  };

  useEffect(() => {
    if (isNew) {
      dispatch(reset());
    } else {
      dispatch(getEntity(id));
    }

    dispatch(getSalas({}));
  }, []);

  useEffect(() => {
    if (updateSuccess) {
      handleClose();
    }
  }, [updateSuccess]);

  const saveEntity = values => {
    if (values.id !== undefined && typeof values.id !== 'number') {
      values.id = Number(values.id);
    }

    const entity = {
      ...usuarioEntity,
      ...values,
      salasAlunos: mapIdList(values.salasAlunos),
    };

    if (isNew) {
      dispatch(createEntity(entity));
    } else {
      dispatch(updateEntity(entity));
    }
  };

  const defaultValues = () =>
    isNew
      ? {}
      : {
          tipoUsuario: 'PROFESSOR',
          ...usuarioEntity,
          salasAlunos: usuarioEntity?.salasAlunos?.map(e => e.id.toString()),
        };

  return (
    <div>
      <Row className="justify-content-center">
        <Col md="8">
          <h2 id="digitadoApp.usuario.home.createOrEditLabel" data-cy="UsuarioCreateUpdateHeading">
            <Translate contentKey="digitadoApp.usuario.home.createOrEditLabel">Create or edit a Usuario</Translate>
          </h2>
        </Col>
      </Row>
      <Row className="justify-content-center">
        <Col md="8">
          {loading ? (
            <p>Loading...</p>
          ) : (
            <ValidatedForm defaultValues={defaultValues()} onSubmit={saveEntity}>
              {!isNew ? (
                <ValidatedField
                  name="id"
                  required
                  readOnly
                  id="usuario-id"
                  label={translate('global.field.id')}
                  validate={{ required: true }}
                />
              ) : null}
              <ValidatedField
                label={translate('digitadoApp.usuario.nome')}
                id="usuario-nome"
                name="nome"
                data-cy="nome"
                type="text"
                validate={{
                  required: { value: true, message: translate('entity.validation.required') },
                }}
              />
              <ValidatedField
                label={translate('digitadoApp.usuario.sobrenome')}
                id="usuario-sobrenome"
                name="sobrenome"
                data-cy="sobrenome"
                type="text"
                validate={{
                  required: { value: true, message: translate('entity.validation.required') },
                }}
              />
              <ValidatedField
                label={translate('digitadoApp.usuario.email')}
                id="usuario-email"
                name="email"
                data-cy="email"
                type="text"
                validate={{
                  required: { value: true, message: translate('entity.validation.required') },
                }}
              />
              <ValidatedField
                label={translate('digitadoApp.usuario.senha')}
                id="usuario-senha"
                name="senha"
                data-cy="senha"
                type="text"
                validate={{
                  required: { value: true, message: translate('entity.validation.required') },
                }}
              />
              <ValidatedField
                label={translate('digitadoApp.usuario.tipoUsuario')}
                id="usuario-tipoUsuario"
                name="tipoUsuario"
                data-cy="tipoUsuario"
                type="select"
              >
                {tipoUsuarioValues.map(tipoUsuario => (
                  <option value={tipoUsuario} key={tipoUsuario}>
                    {translate(`digitadoApp.TipoUsuario.${tipoUsuario}`)}
                  </option>
                ))}
              </ValidatedField>
              <ValidatedField
                label={translate('digitadoApp.usuario.ativo')}
                id="usuario-ativo"
                name="ativo"
                data-cy="ativo"
                check
                type="checkbox"
              />
              <ValidatedField
                label={translate('digitadoApp.usuario.salasAluno')}
                id="usuario-salasAluno"
                data-cy="salasAluno"
                type="select"
                multiple
                name="salasAlunos"
              >
                <option value="" key="0" />
                {salas
                  ? salas.map(otherEntity => (
                      <option value={otherEntity.id} key={otherEntity.id}>
                        {otherEntity.id}
                      </option>
                    ))
                  : null}
              </ValidatedField>
              <Button tag={Link} id="cancel-save" data-cy="entityCreateCancelButton" to="/usuario" replace color="info">
                <FontAwesomeIcon icon="arrow-left" />
                &nbsp;
                <span className="d-none d-md-inline">
                  <Translate contentKey="entity.action.back">Back</Translate>
                </span>
              </Button>
              &nbsp;
              <Button color="primary" id="save-entity" data-cy="entityCreateSaveButton" type="submit" disabled={updating}>
                <FontAwesomeIcon icon="save" />
                &nbsp;
                <Translate contentKey="entity.action.save">Save</Translate>
              </Button>
            </ValidatedForm>
          )}
        </Col>
      </Row>
    </div>
  );
};

export default UsuarioUpdate;
