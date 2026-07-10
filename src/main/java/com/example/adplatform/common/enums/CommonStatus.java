package com.example.adplatform.common.enums;

/**
 * 通用启停状态，适用于广告主、广告位、素材等只有启用/停用两种状态的资源。
 */
public final class CommonStatus {

    /**
     * 启用：资源可以被业务流程使用，例如广告位可接收投放、素材可参与召回。
     */
    public static final int ENABLED = 1;

    /**
     * 停用：资源被临时关闭，不参与对应业务流程。
     */
    public static final int DISABLED = 0;

    private CommonStatus() {
    }
}
