import React, { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Button, Table } from 'reactstrap';
import { TextFormat, Translate, getSortState } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faSort, faSortDown, faSortUp } from '@fortawesome/free-solid-svg-icons';
import { APP_DATE_FORMAT } from 'app/config/constants';
import { ASC, DESC } from 'app/shared/util/pagination.constants';
import { overrideSortStateWithQueryParams } from 'app/shared/util/entity-utils';
import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntities } from './usuario-conquista.reducer';

export const UsuarioConquista = () => {
  const dispatch = useAppDispatch();

  const pageLocation = useLocation();
  const navigate = useNavigate();

  const [sortState, setSortState] = useState(overrideSortStateWithQueryParams(getSortState(pageLocation, 'id'), pageLocation.search));

  const usuarioConquistaList = useAppSelector(state => state.usuarioConquista.entities);
  const loading = useAppSelector(state => state.usuarioConquista.loading);

  const getAllEntities = () => {
    dispatch(
      getEntities({
        sort: `${sortState.sort},${sortState.order}`,
      }),
    );
  };

  const sortEntities = () => {
    getAllEntities();
    const endURL = `?sort=${sortState.sort},${sortState.order}`;
    if (pageLocation.search !== endURL) {
      navigate(`${pageLocation.pathname}${endURL}`);
    }
  };

  useEffect(() => {
    sortEntities();
  }, [sortState.order, sortState.sort]);

  const sort = p => () => {
    setSortState({
      ...sortState,
      order: sortState.order === ASC ? DESC : ASC,
      sort: p,
    });
  };

  const handleSyncList = () => {
    sortEntities();
  };

  const getSortIconByFieldName = (fieldName: string) => {
    const sortFieldName = sortState.sort;
    const order = sortState.order;
    if (sortFieldName !== fieldName) {
      return faSort;
    }
    return order === ASC ? faSortUp : faSortDown;
  };

  return (
    <div>
      <h2 id="usuario-conquista-heading" data-cy="UsuarioConquistaHeading">
        <Translate contentKey="digitadoApp.usuarioConquista.home.title">Usuario Conquistas</Translate>
        <div className="d-flex justify-content-end">
          <Button className="me-2" color="info" onClick={handleSyncList} disabled={loading}>
            <FontAwesomeIcon icon="sync" spin={loading} />{' '}
            <Translate contentKey="digitadoApp.usuarioConquista.home.refreshListLabel">Refresh List</Translate>
          </Button>
          <Link to="/usuario-conquista/new" className="btn btn-primary jh-create-entity" id="jh-create-entity" data-cy="entityCreateButton">
            <FontAwesomeIcon icon="plus" />
            &nbsp;
            <Translate contentKey="digitadoApp.usuarioConquista.home.createLabel">Create new Usuario Conquista</Translate>
          </Link>
        </div>
      </h2>
      <div className="table-responsive">
        {usuarioConquistaList && usuarioConquistaList.length > 0 ? (
          <Table responsive>
            <thead>
              <tr>
                <th className="hand" onClick={sort('id')}>
                  <Translate contentKey="digitadoApp.usuarioConquista.id">ID</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('id')} />
                </th>
                <th className="hand" onClick={sort('dataConquista')}>
                  <Translate contentKey="digitadoApp.usuarioConquista.dataConquista">Data Conquista</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('dataConquista')} />
                </th>
                <th className="hand" onClick={sort('progresso')}>
                  <Translate contentKey="digitadoApp.usuarioConquista.progresso">Progresso</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('progresso')} />
                </th>
                <th className="hand" onClick={sort('concluida')}>
                  <Translate contentKey="digitadoApp.usuarioConquista.concluida">Concluida</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('concluida')} />
                </th>
                <th>
                  <Translate contentKey="digitadoApp.usuarioConquista.aluno">Aluno</Translate> <FontAwesomeIcon icon="sort" />
                </th>
                <th>
                  <Translate contentKey="digitadoApp.usuarioConquista.conquista">Conquista</Translate> <FontAwesomeIcon icon="sort" />
                </th>
                <th />
              </tr>
            </thead>
            <tbody>
              {usuarioConquistaList.map((usuarioConquista, i) => (
                <tr key={`entity-${i}`} data-cy="entityTable">
                  <td>
                    <Button tag={Link} to={`/usuario-conquista/${usuarioConquista.id}`} color="link" size="sm">
                      {usuarioConquista.id}
                    </Button>
                  </td>
                  <td>
                    {usuarioConquista.dataConquista ? (
                      <TextFormat type="date" value={usuarioConquista.dataConquista} format={APP_DATE_FORMAT} />
                    ) : null}
                  </td>
                  <td>{usuarioConquista.progresso}</td>
                  <td>{usuarioConquista.concluida ? 'true' : 'false'}</td>
                  <td>
                    {usuarioConquista.aluno ? <Link to={`/usuario/${usuarioConquista.aluno.id}`}>{usuarioConquista.aluno.id}</Link> : ''}
                  </td>
                  <td>
                    {usuarioConquista.conquista ? (
                      <Link to={`/conquista/${usuarioConquista.conquista.id}`}>{usuarioConquista.conquista.id}</Link>
                    ) : (
                      ''
                    )}
                  </td>
                  <td className="text-end">
                    <div className="btn-group flex-btn-group-container">
                      <Button
                        tag={Link}
                        to={`/usuario-conquista/${usuarioConquista.id}`}
                        color="info"
                        size="sm"
                        data-cy="entityDetailsButton"
                      >
                        <FontAwesomeIcon icon="eye" />{' '}
                        <span className="d-none d-md-inline">
                          <Translate contentKey="entity.action.view">View</Translate>
                        </span>
                      </Button>
                      <Button
                        tag={Link}
                        to={`/usuario-conquista/${usuarioConquista.id}/edit`}
                        color="primary"
                        size="sm"
                        data-cy="entityEditButton"
                      >
                        <FontAwesomeIcon icon="pencil-alt" />{' '}
                        <span className="d-none d-md-inline">
                          <Translate contentKey="entity.action.edit">Edit</Translate>
                        </span>
                      </Button>
                      <Button
                        onClick={() => (window.location.href = `/usuario-conquista/${usuarioConquista.id}/delete`)}
                        color="danger"
                        size="sm"
                        data-cy="entityDeleteButton"
                      >
                        <FontAwesomeIcon icon="trash" />{' '}
                        <span className="d-none d-md-inline">
                          <Translate contentKey="entity.action.delete">Delete</Translate>
                        </span>
                      </Button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </Table>
        ) : (
          !loading && (
            <div className="alert alert-warning">
              <Translate contentKey="digitadoApp.usuarioConquista.home.notFound">No Usuario Conquistas found</Translate>
            </div>
          )
        )}
      </div>
    </div>
  );
};

export default UsuarioConquista;
