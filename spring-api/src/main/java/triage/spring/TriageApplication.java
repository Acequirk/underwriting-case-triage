package triage.spring;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import triage.DemoData;
import triage.repository.CaseRepository;
import triage.repository.InMemoryCaseRepository;
import triage.service.TriageService;

import java.time.Clock;

/**
 * Spring wiring for the framework-free core. The core classes are not annotated; we create them
 * here as beans, so the business logic stays testable without Spring.
 */
@SpringBootApplication
public class TriageApplication {

    public static void main(String[] args) {
        SpringApplication.run(TriageApplication.class, args);
    }

    @Bean
    CaseRepository caseRepository() {
        return new InMemoryCaseRepository();
    }

    @Bean
    TriageService triageService(CaseRepository repository) {
        return new TriageService(repository, Clock.systemUTC());
    }

    @Bean
    CommandLineRunner seed(TriageService service, @Value("${triage.seed-demo}") boolean seed) {
        return args -> {
            if (seed) DemoData.load(service);
        };
    }
}
