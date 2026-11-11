package triage.repository;

import triage.model.Application;
import triage.model.UnderwritingCase;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CaseRepository {
    UnderwritingCase create(Application application, Instant receivedAt);

    Optional<UnderwritingCase> find(long id);

    List<UnderwritingCase> findAll();
}
