package com.example.adplatform.report.response;

/**
 * 素材效果排行结果，用于看哪些素材带来的点击、转化或消耗更高。
 */
public record TopMaterialResponse(
        String planPublicId,
        String materialPublicId,
        String slotPublicId,
        Long impressionCount,
        Long clickCount,
        Long conversionCount,
        Long costAmount,
        Double ctr,
        Double cvr) {
}
