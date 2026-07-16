package com.example.adplatform.search.event.service;

import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.json.JsonData;
import com.example.adplatform.common.exception.BusinessException;
import com.example.adplatform.common.exception.ErrorCode;
import com.example.adplatform.search.config.AdElasticsearchProperties;
import com.example.adplatform.search.event.dto.EventSearchRequest;
import com.example.adplatform.search.event.model.AdEventDocument;
import com.example.adplatform.search.event.vo.EventSearchItemVO;
import com.example.adplatform.search.event.vo.EventSearchPageVO;
import com.example.adplatform.tracking.entity.EventType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.IndicesOptions;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 广告事件 ES 检索服务，支持多条件过滤和 {@code search_after} 游标分页。
 *
 * <p>查询只访问时间窗口覆盖的日索引，并限制最大跨度，避免通配符扫描全部历史数据。
 * 排序由 {@code eventTime + eventId} 组成：时间倒序，eventId 负责在时间相同时提供稳定、
 * 唯一的第二排序键。</p>
 */
@Service
@RequiredArgsConstructor
public class EventSearchService {

    private final AdElasticsearchProperties properties;
    private final ElasticsearchOperations operations;
    private final EventIndexManager indexManager;
    private final EventCursorCodec cursorCodec;

    /**
     * 执行事件检索。未传时间时默认最近一段窗口，未传 cursor 时从第一页开始。
     */
    public EventSearchPageVO search(EventSearchRequest request) {
        if (!properties.isEnabled()) {
            throw new BusinessException(ErrorCode.DEPENDENCY_SERVICE_UNAVAILABLE, "Elasticsearch未启用");
        }
        LocalDateTime end = request.endTime() == null ? LocalDateTime.now() : request.endTime();
        LocalDateTime start = request.startTime() == null
                ? end.minus(properties.getEvent().getDefaultLookback())
                : request.startTime();
        if (end.isBefore(start)) {
            throw new BusinessException(ErrorCode.INVALID_TIME_RANGE, "结束时间不能早于开始时间");
        }
        if (Duration.between(start, end).compareTo(Duration.ofDays(properties.getEvent().getMaxQueryDays())) > 0) {
            throw new BusinessException(ErrorCode.INVALID_TIME_RANGE,
                    "事件检索时间范围不能超过" + properties.getEvent().getMaxQueryDays() + "天");
        }
        int size = request.size() == null ? 20 : Math.min(request.size(), 100);
        if (size < 1) {
            throw new BusinessException(ErrorCode.PARAM_VALIDATION_FAILED, "size必须大于0");
        }

        List<Query> filters = new ArrayList<>();
        addTerm(filters, "eventId", request.eventId());
        addTerm(filters, "requestId", request.requestId());
        if (StringUtils.hasText(request.eventType())) {
            filters.add(term("eventType", EventType.parse(request.eventType()).name()));
        }
        addTerm(filters, "planId", request.planId());
        addTerm(filters, "materialId", request.materialId());
        addTerm(filters, "slotId", request.slotId());
        addTerm(filters, "viewerId", request.viewerId());
        if (request.charged() != null) filters.add(term("charged", request.charged()));
        // API 使用无时区 LocalDateTime；转 epoch millis 后查询，避免 ES 默认 UTC 造成 8 小时偏移。
        filters.add(Query.of(q -> q.range(r -> r.field("eventTime").gte(JsonData.of(toEpochMillis(start))))));
        filters.add(Query.of(q -> q.range(r -> r.field("eventTime").lte(JsonData.of(toEpochMillis(end))))));

        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.bool(b -> b.filter(filters)))
                .withSort(s -> s.field(f -> f.field("eventTime").order(SortOrder.Desc)))
                .withSort(s -> s.field(f -> f.field("eventId").order(SortOrder.Desc)))
                // 多取一条只用于判断 hasMore，不启用高成本 track_total_hits。
                .withPageable(PageRequest.of(0, size + 1))
                .withSearchAfter(cursorCodec.decode(request.cursor()))
                // 某些日期尚无事件索引时跳过该索引，而不是让整个查询返回 404。
                .withIndicesOptions(IndicesOptions.LENIENT_EXPAND_OPEN)
                .withTrackTotalHits(false)
                .build();

        SearchHits<AdEventDocument> hits = operations.search(
                query,
                AdEventDocument.class,
                IndexCoordinates.of(indices(start.toLocalDate(), end.toLocalDate())));
        List<SearchHit<AdEventDocument>> hitList = hits.stream().toList();
        boolean hasMore = hitList.size() > size;
        List<SearchHit<AdEventDocument>> pageHits = hasMore ? hitList.subList(0, size) : hitList;
        List<EventSearchItemVO> records = pageHits.stream().map(hit -> toVO(hit.getContent())).toList();
        // 游标取本页最后一条记录的两个排序值，下一页由 ES 从其后继续扫描。
        String nextCursor = hasMore && !pageHits.isEmpty()
                ? cursorCodec.encode(pageHits.get(pageHits.size() - 1).getSortValues())
                : null;
        return new EventSearchPageVO(records, nextCursor, hasMore);
    }

    private String[] indices(LocalDate start, LocalDate end) {
        // 显式列出日索引，避免 ad-event-* 扫描超过请求窗口的分片。
        List<String> indices = new ArrayList<>();
        for (LocalDate date = start; !date.isAfter(end); date = date.plusDays(1)) {
            indices.add(indexManager.indexName(date));
        }
        return indices.toArray(String[]::new);
    }

    private void addTerm(List<Query> filters, String field, String value) {
        if (StringUtils.hasText(value)) filters.add(term(field, value));
    }

    private void addTerm(List<Query> filters, String field, Long value) {
        if (value != null) filters.add(term(field, value));
    }

    private Query term(String field, String value) {
        return Query.of(q -> q.term(t -> t.field(field).value(value)));
    }

    private Query term(String field, Long value) {
        return Query.of(q -> q.term(t -> t.field(field).value(value)));
    }

    private Query term(String field, boolean value) {
        return Query.of(q -> q.term(t -> t.field(field).value(value)));
    }

    private EventSearchItemVO toVO(AdEventDocument document) {
        return new EventSearchItemVO(
                document.getEventId(), document.getRequestId(), document.getEventType(), document.getPlanId(),
                document.getMaterialId(), document.getSlotId(), document.getViewerId(), document.getBillingType(),
                document.isCharged(), document.getCostAmount(), toLocalDateTime(document.getEventTime()),
                toLocalDateTime(document.getCreatedAt()));
    }

    private LocalDateTime toLocalDateTime(Instant value) {
        return value == null ? null : LocalDateTime.ofInstant(value, ZoneId.systemDefault());
    }

    static long toEpochMillis(LocalDateTime value) {
        return value.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
}
