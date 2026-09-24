package com.example.adplatform.infra.elasticsearch.delivery;

import com.example.adplatform.infra.elasticsearch.delivery.Candidate.EsDocumentFactory;
import com.example.adplatform.search.candidate.model.AdCandidateDocument;
import com.example.adplatform.search.candidate.query.CandidateSourceRow;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.data.elasticsearch.core.convert.MappingElasticsearchConverter;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.SimpleElasticsearchMappingContext;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;

@ResourceLock("java.util.TimeZone.default")
class EsDocumentTimeTests {

    private TimeZone originalTimeZone;
    private MappingElasticsearchConverter converter;

    @BeforeEach
    void setUp() {
        originalTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        converter = new MappingElasticsearchConverter(new SimpleElasticsearchMappingContext());
        converter.afterPropertiesSet();
    }

    @AfterEach
    void restoreTimeZone() {
        TimeZone.setDefault(originalTimeZone);
    }

    @Test
    void shouldKeepMysqlLocalTimesAndRoundTripThroughEpochMillis() {
        CandidateSourceRow row = new CandidateSourceRow();
        row.setMaterialStatus(1);
        LocalDateTime start = LocalDateTime.of(2026, 9, 23, 14, 0);
        row.setStartTime(start);
        row.setEndTime(start.plusHours(1));
        row.setUpdatedAt(start.minusHours(1));
        AdCandidateDocument candidate = new EsDocumentFactory(new ObjectMapper()).from(row);

        assertThat((Object) candidate.getStartTime()).isEqualTo(start);
        Document stored = Document.create();
        converter.write(candidate, stored);
        assertThat(stored.get("startTime").toString()).isEqualTo("1790143200000");
        assertThat(stored.get("endTime").toString()).isEqualTo("1790146800000");
        assertThat(stored.get("updatedAt").toString()).isEqualTo("1790139600000");

        AdCandidateDocument restored = converter.read(AdCandidateDocument.class, stored);
        assertThat((Object) restored.getStartTime()).isEqualTo(start);
        assertThat((Object) restored.getEndTime()).isEqualTo(start.plusHours(1));
        assertThat((Object) restored.getUpdatedAt()).isEqualTo(start.minusHours(1));
    }

    @Test
    void shouldReadExistingEpochMillisDocumentsAsLocalTime() {
        Document stored = Document.from(Map.of(
                "startTime", 1790143200000L,
                "endTime", "1790146800000"));

        AdCandidateDocument candidate = converter.read(AdCandidateDocument.class, stored);

        assertThat((Object) candidate.getStartTime()).isEqualTo(LocalDateTime.of(2026, 9, 23, 14, 0));
        assertThat((Object) candidate.getEndTime()).isEqualTo(LocalDateTime.of(2026, 9, 23, 15, 0));
        assertThat(candidate.getUpdatedAt()).isNull();
    }
}
