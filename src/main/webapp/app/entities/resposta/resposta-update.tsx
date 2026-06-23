import React, { useEffect } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Button, Col, Row } from 'reactstrap';
import { Translate, ValidatedField, ValidatedForm, translate } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import { convertDateTimeFromServer, convertDateTimeToServer, displayDefaultDateTime } from 'app/shared/util/date-utils';
import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntities as getAtividades } from 'app/entities/atividade/atividade.reducer';
import { getEntities as getUsuarios } from 'app/entities/usuario/usuario.reducer';
import { getEntities as getPalavras } from 'app/entities/palavra/palavra.reducer';
import { createEntity, getEntity, reset, updateEntity } from './resposta.reducer';

export const RespostaUpdate = () => {
  const dispatch = useAppDispatch();

  const navigate = useNavigate();

  const { id } = useParams<'id'>();
  const isNew = id === undefined;

  const atividades = useAppSelector(state => state.atividade.entities);
  const usuarios = useAppSelector(state => state.usuario.entities);
  const palavras = useAppSelector(state => state.palavra.entities);
  const respostaEntity = useAppSelector(state => state.resposta.entity);
  const loading = useAppSelector(state => state.resposta.loading);
  const updating = useAppSelector(state => state.resposta.updating);
  const updateSuccess = useAppSelector(state => state.resposta.updateSuccess);

  const handleClose = () => {
    navigate('/resposta');
  };

  useEffect(() => {
    if (isNew) {
      dispatch(reset());
    } else {
      dispatch(getEntity(id));
    }

    dispatch(getAtividades({}));
    dispatch(getUsuarios({}));
    dispatch(getPalavras({}));
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
    if (values.tempoResposta !== undefined && typeof values.tempoResposta !== 'number') {
      values.tempoResposta = Number(values.tempoResposta);
    }
    if (values.pontuacao !== undefined && typeof values.pontuacao !== 'number') {
      values.pontuacao = Number(values.pontuacao);
    }
    values.dataResposta = convertDateTimeToServer(values.dataResposta);

    const entity = {
      ...respostaEntity,
      ...values,
      atividade: atividades.find(it => it.id.toString() === values.atividade?.toString()),
      aluno: usuarios.find(it => it.id.toString() === values.aluno?.toString()),
      palavra: palavras.find(it => it.id.toString() === values.palavra?.toString()),
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
          dataResposta: displayDefaultDateTime(),
        }
      : {
          ...respostaEntity,
          dataResposta: convertDateTimeFromServer(respostaEntity.dataResposta),
          atividade: respostaEntity?.atividade?.id,
          aluno: respostaEntity?.aluno?.id,
          palavra: respostaEntity?.palavra?.id,
        };

  return (
    <div>
      <Row className="justify-content-center">
        <Col md="8">
          <h2 id="digitadoApp.resposta.home.createOrEditLabel" data-cy="RespostaCreateUpdateHeading">
            <Translate contentKey="digitadoApp.resposta.home.createOrEditLabel">Create or edit a Resposta</Translate>
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
                  id="resposta-id"
                  label={translate('global.field.id')}
                  validate={{ required: true }}
                />
              ) : null}
              <ValidatedField
                label={translate('digitadoApp.resposta.respostaDigitada')}
                id="resposta-respostaDigitada"
                name="respostaDigitada"
                data-cy="respostaDigitada"
                type="text"
              />
              <ValidatedField
                label={translate('digitadoApp.resposta.correta')}
                id="resposta-correta"
                name="correta"
                data-cy="correta"
                check
                type="checkbox"
              />
              <ValidatedField
                label={translate('digitadoApp.resposta.tempoResposta')}
                id="resposta-tempoResposta"
                name="tempoResposta"
                data-cy="tempoResposta"
                type="text"
              />
              <ValidatedField
                label={translate('digitadoApp.resposta.pontuacao')}
                id="resposta-pontuacao"
                name="pontuacao"
                data-cy="pontuacao"
                type="text"
              />
              <ValidatedField
                label={translate('digitadoApp.resposta.dataResposta')}
                id="resposta-dataResposta"
                name="dataResposta"
                data-cy="dataResposta"
                type="datetime-local"
                placeholder="YYYY-MM-DD HH:mm"
              />
              <ValidatedField
                id="resposta-atividade"
                name="atividade"
                data-cy="atividade"
                label={translate('digitadoApp.resposta.atividade')}
                type="select"
              >
                <option value="" key="0" />
                {atividades
                  ? atividades.map(otherEntity => (
                      <option value={otherEntity.id} key={otherEntity.id}>
                        {otherEntity.id}
                      </option>
                    ))
                  : null}
              </ValidatedField>
              <ValidatedField
                id="resposta-aluno"
                name="aluno"
                data-cy="aluno"
                label={translate('digitadoApp.resposta.aluno')}
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
                id="resposta-palavra"
                name="palavra"
                data-cy="palavra"
                label={translate('digitadoApp.resposta.palavra')}
                type="select"
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
              <Button tag={Link} id="cancel-save" data-cy="entityCreateCancelButton" to="/resposta" replace color="info">
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

export default RespostaUpdate;
