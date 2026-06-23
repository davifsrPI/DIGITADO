package br.com.digitado.repository;

import br.com.digitado.domain.ListaPalavras;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;

/**
 * Utility repository to load bag relationships based on https://vladmihalcea.com/hibernate-multiplebagfetchexception/
 */
public class ListaPalavrasRepositoryWithBagRelationshipsImpl implements ListaPalavrasRepositoryWithBagRelationships {

    private static final String ID_PARAMETER = "id";
    private static final String LISTAPALAVRAS_PARAMETER = "listaPalavras";

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public Optional<ListaPalavras> fetchBagRelationships(Optional<ListaPalavras> listaPalavras) {
        return listaPalavras.map(this::fetchPalavras);
    }

    @Override
    public Page<ListaPalavras> fetchBagRelationships(Page<ListaPalavras> listaPalavras) {
        return new PageImpl<>(
            fetchBagRelationships(listaPalavras.getContent()),
            listaPalavras.getPageable(),
            listaPalavras.getTotalElements()
        );
    }

    @Override
    public List<ListaPalavras> fetchBagRelationships(List<ListaPalavras> listaPalavras) {
        return Optional.of(listaPalavras).map(this::fetchPalavras).orElse(Collections.emptyList());
    }

    ListaPalavras fetchPalavras(ListaPalavras result) {
        return entityManager
            .createQuery(
                "select listaPalavras from ListaPalavras listaPalavras left join fetch listaPalavras.palavras where listaPalavras.id = :id",
                ListaPalavras.class
            )
            .setParameter(ID_PARAMETER, result.getId())
            .getSingleResult();
    }

    List<ListaPalavras> fetchPalavras(List<ListaPalavras> listaPalavras) {
        HashMap<Object, Integer> order = new HashMap<>();
        IntStream.range(0, listaPalavras.size()).forEach(index -> order.put(listaPalavras.get(index).getId(), index));
        List<ListaPalavras> result = entityManager
            .createQuery(
                "select listaPalavras from ListaPalavras listaPalavras left join fetch listaPalavras.palavras where listaPalavras in :listaPalavras",
                ListaPalavras.class
            )
            .setParameter(LISTAPALAVRAS_PARAMETER, listaPalavras)
            .getResultList();
        Collections.sort(result, (o1, o2) -> Integer.compare(order.get(o1.getId()), order.get(o2.getId())));
        return result;
    }
}
