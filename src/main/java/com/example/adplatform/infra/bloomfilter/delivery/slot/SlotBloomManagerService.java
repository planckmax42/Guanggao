package com.example.adplatform.infra.bloomfilter.delivery.slot;

public interface SlotBloomManagerService {//todo:治理完毕之后再回来拆分接口 保证面向接口编程
}
//  但这里存在并发语义问题：执行 definiteNotContain() 时过滤器
//  可能未就绪，等 MySQL 返回时过滤器变成了就绪，最终会错误记
//  录一次误判。
//
//  更推荐让查询方法返回三态结果：
//
//  public enum BloomProbeResult {
//      NOT_READY,
//      DEFINITELY_ABSENT,
//      POSSIBLY_PRESENT
//  }