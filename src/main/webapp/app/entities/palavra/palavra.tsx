import React, { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Button, Table } from 'reactstrap';
import { Translate, getSortState } from 'react-jhipster';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faSort, faSortDown, faSortUp } from '@fortawesome/free-solid-svg-icons';
import { ASC, DESC } from 'app/shared/util/pagination.constants';
import { overrideSortStateWithQueryParams } from 'app/shared/util/entity-utils';
import { useAppDispatch, useAppSelector } from 'app/config/store';

import { getEntities } from './palavra.reducer';

export const Palavra = () => {
  const dispatch = useAppDispatch();

  const pageLocation = useLocation();
  const navigate = useNavigate();

  const [sortState, setSortState] = useState(overrideSortStateWithQueryParams(getSortState(pageLocation, 'id'), pageLocation.search));

  const palavraList = useAppSelector(state => state.palavra.entities);
  const loading = useAppSelector(state => state.palavra.loading);

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
      <h2 id="palavra-heading" data-cy="PalavraHeading">
        <Translate contentKey="digitadoApp.palavra.home.title">Palavras</Translate>
        <div className="d-flex justify-content-end">
          <Button className="me-2" color="info" onClick={handleSyncList} disabled={loading}>
            <FontAwesomeIcon icon="sync" spin={loading} />{' '}
            <Translate contentKey="digitadoApp.palavra.home.refreshListLabel">Refresh List</Translate>
          </Button>
          <Link to="/palavra/new" className="btn btn-primary jh-create-entity" id="jh-create-entity" data-cy="entityCreateButton">
            <FontAwesomeIcon icon="plus" />
            &nbsp;
            <Translate contentKey="digitadoApp.palavra.home.createLabel">Create new Palavra</Translate>
          </Link>
        </div>
      </h2>
      <div className="table-responsive">
        {palavraList && palavraList.length > 0 ? (
          <Table responsive>
            <thead>
              <tr>
                <th className="hand" onClick={sort('id')}>
                  <Translate contentKey="digitadoApp.palavra.id">ID</Translate> <FontAwesomeIcon icon={getSortIconByFieldName('id')} />
                </th>
                <th className="hand" onClick={sort('texto')}>
                  <Translate contentKey="digitadoApp.palavra.texto">Texto</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('texto')} />
                </th>
                <th className="hand" onClick={sort('dificuldade')}>
                  <Translate contentKey="digitadoApp.palavra.dificuldade">Dificuldade</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('dificuldade')} />
                </th>
                <th className="hand" onClick={sort('categoria')}>
                  <Translate contentKey="digitadoApp.palavra.categoria">Categoria</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('categoria')} />
                </th>
                <th className="hand" onClick={sort('idioma')}>
                  <Translate contentKey="digitadoApp.palavra.idioma">Idioma</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('idioma')} />
                </th>
                <th className="hand" onClick={sort('possuiAcento')}>
                  <Translate contentKey="digitadoApp.palavra.possuiAcento">Possui Acento</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('possuiAcento')} />
                </th>
                <th className="hand" onClick={sort('ativa')}>
                  <Translate contentKey="digitadoApp.palavra.ativa">Ativa</Translate>{' '}
                  <FontAwesomeIcon icon={getSortIconByFieldName('ativa')} />
                </th>
                <th>
                  <Translate contentKey="digitadoApp.palavra.criador">Criador</Translate> <FontAwesomeIcon icon="sort" />
                </th>
                <th>
                  <Translate contentKey="digitadoApp.palavra.listas">Listas</Translate> <FontAwesomeIcon icon="sort" />
                </th>
                <th />
              </tr>
            </thead>
            <tbody>
              {palavraList.map((palavra, i) => (
                <tr key={`entity-${i}`} data-cy="entityTable">
                  <td>
                    <Button tag={Link} to={`/palavra/${palavra.id}`} color="link" size="sm">
                      {palavra.id}
                    </Button>
                  </td>
                  <td>{palavra.texto}</td>
                  <td>
                    <Translate contentKey={`digitadoApp.Dificuldade.${palavra.dificuldade}`} />
                  </td>
                  <td>{palavra.categoria}</td>
                  <td>{palavra.idioma}</td>
                  <td>{palavra.possuiAcento ? 'true' : 'false'}</td>
                  <td>{palavra.ativa ? 'true' : 'false'}</td>
                  <td>{palavra.criador ? <Link to={`/usuario/${palavra.criador.id}`}>{palavra.criador.id}</Link> : ''}</td>
                  <td>
                    {palavra.listas
                      ? palavra.listas.map((val, j) => (
                          <span key={j}>
                            <Link to={`/lista-palavras/${val.id}`}>{val.id}</Link>
                            {j === palavra.listas.length - 1 ? '' : ', '}
                          </span>
                        ))
                      : null}
                  </td>
                  <td className="text-end">
                    <div className="btn-group flex-btn-group-container">
                      <Button tag={Link} to={`/palavra/${palavra.id}`} color="info" size="sm" data-cy="entityDetailsButton">
                        <FontAwesomeIcon icon="eye" />{' '}
                        <span className="d-none d-md-inline">
                          <Translate contentKey="entity.action.view">View</Translate>
                        </span>
                      </Button>
                      <Button tag={Link} to={`/palavra/${palavra.id}/edit`} color="primary" size="sm" data-cy="entityEditButton">
                        <FontAwesomeIcon icon="pencil-alt" />{' '}
                        <span className="d-none d-md-inline">
                          <Translate contentKey="entity.action.edit">Edit</Translate>
                        </span>
                      </Button>
                      <Button
                        onClick={() => (window.location.href = `/palavra/${palavra.id}/delete`)}
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
              <Translate contentKey="digitadoApp.palavra.home.notFound">No Palavras found</Translate>
            </div>
          )
        )}
      </div>
    </div>
  );
};

export default Palavra;
