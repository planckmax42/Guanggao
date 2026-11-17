package com.example.adplatform.infra.elasticsearch;

import com.example.adplatform.search.candidate.query.CandidateQueryResult;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.data.domain.Range;

@Mapper(componentModel = "spring")
public interface CandidateConverter {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "ageRange", expression = "java(toAgeRange(result.getAgeMin(), result.getAgeMax()))")
    CandidateDocument queryResultToDocument(CandidateQueryResult result);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "ageRange", expression = "java(toAgeRange(request.ageMin(), request.ageMax()))")
    CandidateDocument toDocument(CreateCandidateRequest request);

    QueryCandidateResponse toQueryResponse(CandidateDocument document);

    /** 年龄上下限包含边界，null 表示该方向不设限。 */
    default Range<Integer> toAgeRange(Integer min, Integer max) {
        return Range.of(
                min == null ? Range.Bound.unbounded() : Range.Bound.inclusive(min),
                max == null ? Range.Bound.unbounded() : Range.Bound.inclusive(max)
        );
    }
}
