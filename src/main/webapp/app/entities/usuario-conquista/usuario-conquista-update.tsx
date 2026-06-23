import React, { useEffect } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Button, Col, Row } from 'reactstrap';
import { Translate, ValidatedField, ValidatedForm, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { convertDateTimeFromServer, convertDateTimeToServer, displayDefaultDateTime } from 'app/shared/util/date-utils';
import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntities as getUsuarios } from 'app/entities/usuario/usuario.reducer';
import { getEntities as getConquistas } from 'app/entities/conquista/conquista.reducer';
import { createEntity, getEntity, reset, updateEntity } from './usuario-conquista.reducer';

export const UsuarioConquistaUpdate = () => {
  const dispatch = useAppDispatch();

  const navigate = useNavigate();

  const { id } = useParams<'id'>();
  const isNew = id === undefined;

  const usuarios = useAppSelector(state => state.usuario.entities);
  const conquistas = useAppSelector(state => state.conquista.entities);
  const usuarioConquistaEntity = useAppSelector(state => state.usuarioConquista.entity);
  const loading = useAppSelector(state => state.usuarioConquista.loading);
  const updating = useAppSelector(state => state.usuarioConquista.updating);
  const updateSuccess = useAppSelector(state => state.usuarioConquista.updateSuccess);

  const handleClose = () => {
    navigate('/usuario-conquista');
  };

  useEffect(() => {
    if (isNew) {
      dispatch(reset());
    } else {
      dispatch(getEntity(id));
    }

    dispatch(getUsuarios({}));
    dispatch(getConquistas({}));
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
    values.dataConquista = convertDateTimeToServer(values.dataConquista);
    if (values.progresso !== undefined && typeof values.progresso !== 'number') {
      values.progresso = Number(values.progresso);
    }

    const entity = {
      ...usuarioConquistaEntity,
      ...values,
      aluno: usuarios.find(it => it.id.toString() === values.aluno?.toString()),
      conquista: conquistas.find(it => it.id.toString() === values.conquista?.toString()),
    };

    if (isNew) {
      dispatch(createEntity(entity));
    } else {
      dispatch(updateEntity(entity));
    }
  };

  const defaultValues = () =>
    isNew
      ? {
          dataConquista: displayDefaultDateTime(),
        }
      : {
          ...usuarioConquistaEntity,
          dataConquista: convertDateTimeFromServer(usuarioConquistaEntity.dataConquista),
          aluno: usuarioConquistaEntity?.aluno?.id,
          conquista: usuarioConquistaEntity?.conquista?.id,
        };

  return (
    <div>
      <Row className="justify-content-center">
        <Col md="8">
          <h2 id="digitadoApp.usuarioConquista.home.createOrEditLabel" data-cy="UsuarioConquistaCreateUpdateHeading">
            <Translate contentKey="digitadoApp.usuarioConquista.home.createOrEditLabel">Create or edit a UsuarioConquista</Translate>
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
                  id="usuario-conquista-id"
                  label={translate('global.field.id')}
                  validate={{ required: true }}
                />
              ) : null}
              <ValidatedField
                label={translate('digitadoApp.usuarioConquista.dataConquista')}
                id="usuario-conquista-dataConquista"
                name="dataConquista"
                data-cy="dataConquista"
                type="datetime-local"
                placeholder="YYYY-MM-DD HH:mm"
              />
              <ValidatedField
                label={translate('digitadoApp.usuarioConquista.progresso')}
                id="usuario-conquista-progresso"
                name="progresso"
                data-cy="progresso"
                type="text"
              />
              <ValidatedField
                label={translate('digitadoApp.usuarioConquista.concluida')}
                id="usuario-conquista-concluida"
                name="concluida"
                data-cy="concluida"
                check
                type="checkbox"
              />
              <ValidatedField
                id="usuario-conquista-aluno"
                name="aluno"
                data-cy="aluno"
                label={translate('digitadoApp.usuarioConquista.aluno')}
                type="select"
              >
                <option value="" key="0" />
                {usuarios
                  ? usuarios.map(otherEntity => (
                      <option value={otherEntity.id} key={otherEntity.id}>
                        {otherEntity.id}
                      </option>
                    ))
                  : null}
              </ValidatedField>
              <ValidatedField
                id="usuario-conquista-conquista"
                name="conquista"
                data-cy="conquista"
                label={translate('digitadoApp.usuarioConquista.conquista')}
                type="select"
              >
                <option value="" key="0" />
                {conquistas
                  ? conquistas.map(otherEntity => (
                      <option value={otherEntity.id} key={otherEntity.id}>
                        {otherEntity.id}
                      </option>
                    ))
                  : null}
              </ValidatedField>
              <Button tag={Link} id="cancel-save" data-cy="entityCreateCancelButton" to="/usuario-conquista" replace color="info">
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

export default UsuarioConquistaUpdate;
