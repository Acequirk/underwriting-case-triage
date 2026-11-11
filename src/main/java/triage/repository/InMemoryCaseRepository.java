package triage.repository;

import triage.model.Application;
import triage.model.UnderwritingCase;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Simple in-memory store. A database-backed implementation can replace it without touching the service. */
public final class InMemoryCaseRepository implements CaseRepository {
    private final Map<Long, UnderwritingCase> cases = new LinkedHashMap<>();
    private long nextId = 1;

    @Override
    public synchronized UnderwritingCase create(Application application, Instant receivedAt) {
        UnderwritingCase created = new UnderwritingCase(nextId++, application, receivedAt);
        cases.put(created.id(), created);
        return created;
    }

    @Override
    public synchronized Optional<UnderwritingCase> find(long id) {
        return Optional.ofNullable(cases.get(id));
    }

    @Override
    public synchronized List<UnderwritingCase> findAll() {
        return new ArrayList<>(cases.values());
    }
}
