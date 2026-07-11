package com.example.adplatform.report.vo;

/**
 * 素材效果排行结果，用于看哪些素材带来的点击、转化或消耗更高。
 */
public record TopMaterialVO(
        Long planId,
        Long materialId,
        Long slotId,
        Long impressionCount,
        Long clickCount,
        Long conversionCount,
        Long costAmount,
        Double ctr,
        Double cvr) {
}
