package com.example.adplatform.common.id;

import java.util.Set;
import java.util.UUID;

/** 生成不可枚举、带资源类型前缀的对外公开标识。 */
public final class PublicIdGenerator {

    public static final String ADVERTISER_PREFIX = "adv";
    public static final String SLOT_PREFIX = "slot";
    public static final String PLAN_PREFIX = "plan";
    public static final String MATERIAL_PREFIX = "mat";
    public static final String RULE_PREFIX = "rule";
    public static final String EVENT_PREFIX = "evt";

    public static final String ADVERTISER_PATTERN = "^adv_[0-9a-f]{32}$";
    public static final String SLOT_PATTERN = "^slot_[0-9a-f]{32}$";
    public static final String PLAN_PATTERN = "^plan_[0-9a-f]{32}$";
    public static final String MATERIAL_PATTERN = "^mat_[0-9a-f]{32}$";
    public static final String RULE_PATTERN = "^rule_[0-9a-f]{32}$";
    public static final String EVENT_PATTERN = "^evt_[0-9a-f]{32}$";

    private static final Set<String> SUPPORTED_PREFIXES = Set.of(
            ADVERTISER_PREFIX,
            SLOT_PREFIX,
            PLAN_PREFIX,
            MATERIAL_PREFIX,
            RULE_PREFIX,
            EVENT_PREFIX);

    private PublicIdGenerator() {
    }

    /** 生成“业务前缀 + 128 位 UUIDv4 随机值”。 */
    public static String generate(String prefix) {
        if (!SUPPORTED_PREFIXES.contains(prefix)) {
            throw new IllegalArgumentException("不支持的 publicId 前缀: " + prefix);
        }
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }
}
