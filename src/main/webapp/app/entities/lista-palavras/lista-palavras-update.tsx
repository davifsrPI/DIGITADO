import React, { useEffect } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Button, Col, Row } from 'reactstrap';
import { Translate, ValidatedField, ValidatedForm, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { mapIdList } from 'app/shared/util/entity-utils';
import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntities as getPalavras } from 'app/entities/palavra/palavra.reducer';
import { getEntities as getUsuarios } from 'app/entities/usuario/usuario.reducer';
import { createEntity, getEntity, reset, updateEntity } from './lista-palavras.reducer';

export const ListaPalavrasUpdate = () => {
  const dispatch = useAppDispatch();

  const navigate = useNavigate();

  const { id } = useParams<'id'>();
  const isNew = id === undefined;

  const palavras = useAppSelector(state => state.palavra.entities);
  const usuarios = useAppSelector(state => state.usuario.entities);
  const listaPalavrasEntity = useAppSelector(state => state.listaPalavras.entity);
  const loading = useAppSelector(state => state.listaPalavras.loading);
  const updating = useAppSelector(state => state.listaPalavras.updating);
  const updateSuccess = useAppSelector(state => state.listaPalavras.updateSuccess);

  const handleClose = () => {
    navigate('/lista-palavras');
  };

  useEffect(() => {
    if (isNew) {
      dispatch(reset());
    } else {
      dispatch(getEntity(id));
    }

    dispatch(getPalavras({}));
    dispatch(getUsuarios({}));
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
      ...listaPalavrasEntity,
      ...values,
      palavras: mapIdList(values.palavras),
      professor: usuarios.find(it => it.id.toString() === values.professor?.toString()),
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
          ...listaPalavrasEntity,
          palavras: listaPalavrasEntity?.palavras?.map(e => e.id.toString()),
          professor: listaPalavrasEntity?.professor?.id,
        };

  return (
    <div>
      <Row className="justify-content-center">
        <Col md="8">
          <h2 id="digitadoApp.listaPalavras.home.createOrEditLabel" data-cy="ListaPalavrasCreateUpdateHeading">
            <Translate contentKey="digitadoApp.listaPalavras.home.createOrEditLabel">Create or edit a ListaPalavras</Translate>
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
                  id="lista-palavras-id"
                  label={translate('global.field.id')}
                  validate={{ required: true }}
                />
              ) : null}
              <ValidatedField
                label={translate('digitadoApp.listaPalavras.nomeLista')}
                id="lista-palavras-nomeLista"
                name="nomeLista"
                data-cy="nomeLista"
                type="text"
                validate={{
                  required: { value: true, message: translate('entity.validation.required') },
                }}
              />
              <ValidatedField
                label={translate('digitadoApp.listaPalavras.descricao')}
                id="lista-palavras-descricao"
                name="descricao"
                data-cy="descricao"
                type="textarea"
              />
              <ValidatedField
                label={translate('digitadoApp.listaPalavras.ativo')}
                id="lista-palavras-ativo"
                name="ativo"
                data-cy="ativo"
                check
                type="checkbox"
              />
              <ValidatedField
                label={translate('digitadoApp.listaPalavras.palavras')}
                id="lista-palavras-palavras"
                data-cy="palavras"
                type="select"
                multiple
                name="palavras"
              >
                <option value="" key="0" />
                {palavras
                  ? palavras.map(otherEntity => (
                      <option value={otherEntity.id} key={otherEntity.id}>
                        {otherEntity.id}
                      </option>
                    ))
                  : null}
              </ValidatedField>
              <ValidatedField
                id="lista-palavras-professor"
                name="professor"
                data-cy="professor"
                label={translate('digitadoApp.listaPalavras.professor')}
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
              <Button tag={Link} id="cancel-save" data-cy="entityCreateCancelButton" to="/lista-palavras" replace color="info">
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

export default ListaPalavrasUpdate;
