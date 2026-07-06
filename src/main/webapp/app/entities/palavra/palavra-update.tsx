import React, { useEffect } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Button, Col, Row } from 'reactstrap';
import { Translate, ValidatedField, ValidatedForm, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { mapIdList } from 'app/shared/util/entity-utils';
import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntities as getUsuarios } from 'app/entities/usuario/usuario.reducer';
import { getEntities as getListaPalavras } from 'app/entities/lista-palavras/lista-palavras.reducer';
import { createEntity, getEntity, reset, updateEntity } from './palavra.reducer';

export const PalavraUpdate = () => {
  const dispatch = useAppDispatch();

  const navigate = useNavigate();

  const { id } = useParams<'id'>();
  const isNew = id === undefined;

  const usuarios = useAppSelector(state => state.usuario.entities);
  const listaPalavras = useAppSelector(state => state.listaPalavras.entities);
  const palavraEntity = useAppSelector(state => state.palavra.entity);
  const loading = useAppSelector(state => state.palavra.loading);
  const updating = useAppSelector(state => state.palavra.updating);
  const updateSuccess = useAppSelector(state => state.palavra.updateSuccess);

  const handleClose = () => {
    navigate('/palavra');
  };

  useEffect(() => {
    if (isNew) {
      dispatch(reset());
    } else {
      dispatch(getEntity(id));
    }

    dispatch(getUsuarios({}));
    dispatch(getListaPalavras({}));
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
      ...palavraEntity,
      ...values,
      criador: usuarios.find(it => it.id.toString() === values.criador?.toString()),
      listas: mapIdList(values.listas),
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
          ...palavraEntity,
          criador: palavraEntity?.criador?.id,
          listas: palavraEntity?.listas?.map(e => e.id.toString()),
        };

  return (
    <div>
      <Row className="justify-content-center">
        <Col md="8">
          <h2 id="digitadoApp.palavra.home.createOrEditLabel" data-cy="PalavraCreateUpdateHeading">
            <Translate contentKey="digitadoApp.palavra.home.createOrEditLabel">Create or edit a Palavra</Translate>
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
                  id="palavra-id"
                  label={translate('global.field.id')}
                  validate={{ required: true }}
                />
              ) : null}
              <ValidatedField
                label={translate('digitadoApp.palavra.texto')}
                id="palavra-texto"
                name="texto"
                data-cy="texto"
                type="text"
                validate={{
                  required: { value: true, message: translate('entity.validation.required') },
                }}
              />
              {/* dificuldade não é mais editável: é calculada pela taxa de acerto da palavra */}
              <ValidatedField
                label={translate('digitadoApp.palavra.categoria')}
                id="palavra-categoria"
                name="categoria"
                data-cy="categoria"
                type="text"
              />
              <ValidatedField
                label={translate('digitadoApp.palavra.idioma')}
                id="palavra-idioma"
                name="idioma"
                data-cy="idioma"
                type="text"
              />
              <ValidatedField
                label={translate('digitadoApp.palavra.possuiAcento')}
                id="palavra-possuiAcento"
                name="possuiAcento"
                data-cy="possuiAcento"
                check
                type="checkbox"
              />
              <ValidatedField
                label={translate('digitadoApp.palavra.ativa')}
                id="palavra-ativa"
                name="ativa"
                data-cy="ativa"
                check
                type="checkbox"
              />
              <ValidatedField
                id="palavra-criador"
                name="criador"
                data-cy="criador"
                label={translate('digitadoApp.palavra.criador')}
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
                label={translate('digitadoApp.palavra.listas')}
                id="palavra-listas"
                data-cy="listas"
                type="select"
                multiple
                name="listas"
              >
                <option value="" key="0" />
                {listaPalavras
                  ? listaPalavras.map(otherEntity => (
                      <option value={otherEntity.id} key={otherEntity.id}>
                        {otherEntity.id}
                      </option>
                    ))
                  : null}
              </ValidatedField>
              <Button tag={Link} id="cancel-save" data-cy="entityCreateCancelButton" to="/palavra" replace color="info">
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

export default PalavraUpdate;
