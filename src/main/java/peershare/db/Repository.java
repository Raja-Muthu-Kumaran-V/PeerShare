package peershare.db;

import java.util.List;
import java.util.Optional;

/**
 * A minimal, generic data-access contract: {@code T} is the entity type,
 * {@code ID} is its identifier type. Any concrete store (MySQL-backed,
 * in-memory, etc.) can implement this the same way, which is the point of
 * using a type parameter here rather than hard-coding {@code TransferRecord}
 * and {@code Long} directly into the interface.
 */
public interface Repository<T, ID> {

    /** Persists a new entity and returns it with its generated ID populated. */
    T save(T entity);

    List<T> findAll();

    Optional<T> findById(ID id);

    /** @return true if a row/entry existed and was removed. */
    boolean deleteById(ID id);
}
