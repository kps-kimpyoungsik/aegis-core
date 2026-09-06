package io.aegis.testkits;

import io.aegis.application.CreateWorkUseCase;
import io.aegis.application.InMemoryWorkRepository;
import io.aegis.contracts.WorkCommand;
import java.time.Clock;

public final class ContractSmokeTest {
    private ContractSmokeTest() {}
    public static void main(String[] args) {
        var repo = new InMemoryWorkRepository();
        var useCase = new CreateWorkUseCase(repo, Clock.systemUTC());
        var command = new WorkCommand("cmd-1", "idem-1", "tenant-a", "release AEGIS");
        var first = useCase.execute(command);
        var second = useCase.execute(command);
        if (!first.id().equals(second.id())) throw new AssertionError("idempotency regression");
        if (repo.findById("tenant-b", first.id()).isPresent()) throw new AssertionError("tenant isolation regression");
        System.out.println("ContractSmokeTest PASS");
    }
}
