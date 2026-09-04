package com.example.adplatform.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InfrastructureBoundaryTests {

    private static final List<String> BUSINESS_PACKAGES = List.of(
            "admin", "delivery", "tracking", "report", "search", "health");

    private static final List<String> FORBIDDEN_IMPORTS = List.of(
            "import com.example.adplatform.infra.",
            "import org.springframework.data.redis.",
            "import org.springframework.kafka.",
            "import org.apache.kafka.",
            "import org.springframework.data.elasticsearch.core.",
            "import org.elasticsearch.client.",
            "import io.github.resilience4j.");

    @Test
    void businessPackagesShouldOnlyReachInfrastructureThroughPorts() throws IOException {
        Path sourceRoot = Path.of("src/main/java/com/example/adplatform");
        for (String businessPackage : BUSINESS_PACKAGES) {
            try (var files = Files.walk(sourceRoot.resolve(businessPackage))) {
                for (Path sourceFile : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                    String source = Files.readString(sourceFile);
                    for (String forbiddenImport : FORBIDDEN_IMPORTS) {
                        assertThat(source)
                                .as("%s 不应依赖 %s", sourceFile, forbiddenImport)
                                .doesNotContain(forbiddenImport);
                    }
                }
            }
        }
    }

    @Test
    void infrastructureShouldBeGroupedByComponentAndBusinessPurpose() {
        Path infraRoot = Path.of("src/main/java/com/example/adplatform/infra");
        assertThat(infraRoot.resolve("redis/delivery")).isDirectory();
        assertThat(infraRoot.resolve("redis/tracking")).isDirectory();
        assertThat(infraRoot.resolve("redis/report")).isDirectory();
        assertThat(infraRoot.resolve("kafka/tracking")).isDirectory();
        assertThat(infraRoot.resolve("kafka/admin")).isDirectory();
        assertThat(infraRoot.resolve("elasticsearch/delivery")).isDirectory();
        assertThat(infraRoot.resolve("bloomfilter/delivery")).isDirectory();
        assertThat(infraRoot.resolve("bloomfilter/tracking")).isDirectory();
        assertThat(infraRoot.resolve("resilience/delivery")).isDirectory();
        assertThat(infraRoot.resolve("resilience/tracking")).isDirectory();
    }
}
