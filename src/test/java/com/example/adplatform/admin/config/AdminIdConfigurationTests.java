package com.example.adplatform.admin.config;

import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;

class AdminIdConfigurationTests {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(AdminIdConfiguration.class);

    @Test
    void requiresExplicitNodeAllocation() {
        context.run(app -> assertThat(app).hasFailed());
        context.withPropertyValues("admin.id.worker-id=1").run(app -> assertThat(app).hasFailed());
    }

    @Test
    void rejectsNodeNumbersOutsideFiveBitRange() {
        for (String worker : new String[]{"-1", "32"}) {
            context.withPropertyValues("admin.id.worker-id=" + worker, "admin.id.datacenter-id=1")
                    .run(app -> assertThat(app).hasFailed());
        }
        context.withPropertyValues("admin.id.worker-id=1", "admin.id.datacenter-id=32")
                .run(app -> assertThat(app).hasFailed());
    }

    @Test
    void generatesPositiveDistinctIdsWithExplicitNode() {
        context.withPropertyValues("admin.id.worker-id=1", "admin.id.datacenter-id=2").run(app -> {
            assertThat(app).hasNotFailed();
            var generator = app.getBean(IdentifierGenerator.class);
            var ids = new HashSet<Long>();
            for (int i = 0; i < 10000; i++) {
                long id = generator.nextId(new Object()).longValue();
                assertThat(id).isPositive();
                assertThat(ids.add(id)).isTrue();
                assertThat((id >> 12) & 31).isEqualTo(1);
                assertThat((id >> 17) & 31).isEqualTo(2);
            }
        });
    }
}
