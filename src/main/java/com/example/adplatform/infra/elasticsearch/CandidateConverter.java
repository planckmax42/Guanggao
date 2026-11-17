package com.example.adplatform.infra.elasticsearch;

import com.example.adplatform.search.candidate.query.CandidateQueryResult;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.data.domain.Range;

@Mapper(componentModel = "spring")
public interface CandidateConverter {

    @Mapping(target = "id", source = "documentId")
    @Mapping(target = "ageRange", expression = "java(toAgeRange(result.getAgeMin(), result.getAgeMax()))")
    CandidateDocument queryResultToDocument(CandidateQueryResult result);

    default Range<Integer> toAgeRange(Integer min, Integer max) {
        return Range.of(
                min == null ? Range.Bound.unbounded() : Range.Bound.inclusive(min),
                max == null ? Range.Bound.unbounded() : Range.Bound.inclusive(max)
        );
    }
}
