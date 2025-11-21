package com.fscallflow.analyzer.model;

import lombok.Data;

@Data
public class CallLeg {

    /** 这一腿的 uuid（就是日志里的 uuid） */
    private String legUuid;

    /** 腿类型：CALLER / AGENT / IVR / QUEUE / OTHER */
    private LegType legType = LegType.OTHER;

    /** 与之桥接的另一条腿 uuid（Peer-UUID） */
    private String peerLegUuid;

    /** 聚合出来的 callId，通常用主叫腿的 uuid */
    private String callId;

    public enum LegType {
        CALLER, AGENT, IVR, QUEUE, OTHER
    }
}

