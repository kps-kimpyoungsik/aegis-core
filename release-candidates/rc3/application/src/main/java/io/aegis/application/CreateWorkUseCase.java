package io.aegis.application;

import io.aegis.contracts.WorkCommand;
import io.aegis.core.Work;
import io.aegis.core.WorkRepository;
import java.time.Clock;
import java.util.UUID;

public final class CreateWorkUseCase {
    private final WorkRepository repository;
    private final Clock clock;

    public CreateWorkUseCase(WorkRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public Work execute(WorkCommand command) {
        var work = new Work(UUID.randomUUID().toString(), command.tenantId(), command.objective(), 1L, clock.instant());
        return repository.saveNew(work, command.idempotencyKey());
    }
}
