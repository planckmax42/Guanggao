package com.example.adplatform.infra.elasticsearch.delivery.Candidate;

import com.example.adplatform.infra.redis.delivery.search.CandidateIndexRebuildGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Range;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.ByQueryResponse;
import org.springframework.data.elasticsearch.core.query.ScriptType;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 候选字段局部更新，不回源 MySQL，不删除、重建候选文档。
 *
 * <p>四个参数依次是所属实体 ID、CandidateDocument 字段名、旧值、新值。
 * 预算和计费字段按计划更新，定向字段按规则更新，关联字段按素材更新。
 * 列表和年龄范围整体替换；null 表示字段无值。只修改当前值等于旧值的文档，
 * 其余文档记为 noop。返回 ES 响应，调用方需检查超时、冲突和失败信息。
 *
 * <p>此类未接入原来的消费链路。旧值比较不能代替消息版本控制，调用方仍需保证
 * 同一实体的变更顺序；此类不负责新增候选、停投标记或其他关联字段的联动更新。
 */
@Service
@RequiredArgsConstructor
public class CandidateUpdateServiceImpl {

    private final EsProperties properties;
    private final ElasticsearchOperations operations;
    private final CandidateIndexRebuildGuard rebuildGuard;

    private static final String UPDATE_SCRIPT = """
            def current = ctx._source[params.field];
            if (current == params.oldValue) {
                if (current == params.newValue) {
                    ctx.op = 'noop';
                } else {
                    ctx._source[params.field] = params.newValue;
                }
            } else {
                ctx.op = 'noop';
            }
            """;

    /** id 是 ES 的 _id，修改主键需要迁移文档，不能通过字段更新实现。 */
    public ByQueryResponse updateId(Long materialId, String field, String oldValue, String newValue) {
        throw new UnsupportedOperationException("CandidateDocument.id 是 ES 文档主键，不支持原地更新");
    }

    public ByQueryResponse updateMaterialId(Long materialId, String field, Long oldValue, Long newValue) {
        return update("materialId", materialId, "materialId", field, oldValue, newValue);
    }

    public ByQueryResponse updateSlotId(Long materialId, String field, Long oldValue, Long newValue) {
        return update("materialId", materialId, "slotId", field, oldValue, newValue);
    }

    public ByQueryResponse updatePlanId(Long materialId, String field, Long oldValue, Long newValue) {
        return update("materialId", materialId, "planId", field, oldValue, newValue);
    }

    public ByQueryResponse updateBudgetTotal(Long planId, String field, Long oldValue, Long newValue) {
        return update("planId", planId, "budget_total", field, oldValue, newValue);
    }

    public ByQueryResponse updateBudgetDaily(Long planId, String field, Long oldValue, Long newValue) {
        return update("planId", planId, "budget_daily", field, oldValue, newValue);
    }

    public ByQueryResponse updateBidPrice(Long planId, String field, Long oldValue, Long newValue) {
        return update("planId", planId, "bidPrice", field, oldValue, newValue);
    }

    public ByQueryResponse updateBillingType(Long planId, String field, String oldValue, String newValue) {
        return update("planId", planId, "billingType", field, oldValue, newValue);
    }

    /** 修改计划关联的规则 ID；新规则的定向内容需由调用方另行同步。 */
    public ByQueryResponse updateRuleId(Long planId, String field, Long oldValue, Long newValue) {
        return update("planId", planId, "ruleId", field, oldValue, newValue);
    }

    public ByQueryResponse updateDeviceTypes(Long ruleId, String field, List<String> oldValue,
                                            List<String> newValue) {
        return update("ruleId", ruleId, "deviceTypes", field, oldValue, newValue);
    }

    public ByQueryResponse updateGender(Long ruleId, String field, String oldValue, String newValue) {
        return update("ruleId", ruleId, "gender", field, oldValue, newValue);
    }

    public ByQueryResponse updateAgeRange(Long ruleId, String field, Range<Integer> oldValue,
                                         Range<Integer> newValue) {
        return update("ruleId", ruleId, "ageRange", field, toRangeValue(oldValue), toRangeValue(newValue));
    }

    public ByQueryResponse updateRegions(Long ruleId, String field, List<String> oldValue,
                                        List<String> newValue) {
        return update("ruleId", ruleId, "regions", field, oldValue, newValue);
    }

    private ByQueryResponse update(String idField, Long id, String expectedField, String field,
                                   Object oldValue, Object newValue) {
        Objects.requireNonNull(id, idField + " 不能为空");
        if (!expectedField.equals(field)) {
            throw new IllegalArgumentException("该方法只允许修改字段：" + expectedField);
        }
        if (rebuildGuard.isRebuilding()) {
            throw new IllegalStateException("ES正在重建，请稍后重试");//todo:后续加入补偿机制，过一段时间重试？
        }

        NativeQuery query = NativeQuery.builder()
                .withQuery(q -> q.term(t -> t.field(idField).value(id.toString())))
                .build();
        // HashMap 允许 null，支持从无值更新或将字段清空。
        Map<String, Object> params = new HashMap<>();
        params.put("field", field);
        params.put("oldValue", oldValue);
        params.put("newValue", newValue);
        UpdateQuery updateQuery = UpdateQuery.builder(query)
                .withScriptType(ScriptType.INLINE)
                .withLang("painless")
                .withScript(UPDATE_SCRIPT)
                .withParams(params)
                .withAbortOnVersionConflict(true)
                .build();
        IndexCoordinates index = IndexCoordinates.of(properties.getCandidate().getWriteAlias());
        ByQueryResponse response = operations.updateByQuery(updateQuery, index);
        // 让紧接着的字段变更查询看到本次结果，避免连续更新使用旧搜索快照。
        operations.indexOps(index).refresh();
        return response;
    }

    /** 将 Spring Range 转成 ES integer_range 的 JSON 结构，保留开闭区间和无界语义。 */
    private Map<String, Object> toRangeValue(Range<Integer> range) {
        if (range == null) {
            return null;
        }
        Map<String, Object> value = new HashMap<>();
        Range.Bound<Integer> lower = range.getLowerBound();
        Range.Bound<Integer> upper = range.getUpperBound();
        lower.getValue().ifPresent(bound -> value.put(lower.isInclusive() ? "gte" : "gt", bound));
        upper.getValue().ifPresent(bound -> value.put(upper.isInclusive() ? "lte" : "lt", bound));
        return value;
    }
}
