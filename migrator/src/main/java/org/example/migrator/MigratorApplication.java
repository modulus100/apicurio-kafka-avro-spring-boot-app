package org.example.migrator;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class MigratorApplication {

    public static void main(String[] args) {
        // Run and exit when CommandLineRunner completes
        int exit = SpringApplication.exit(SpringApplication.run(MigratorApplication.class, args));
        System.exit(exit);
    }

    @Bean
    CommandLineRunner run(MigrationService migrationService) {
        return args -> {
            migrationService.registerSchema();
        };
    }
}
