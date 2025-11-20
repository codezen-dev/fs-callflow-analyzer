package com.fscallflow.analyzer.model;

public enum FsEventType {
    INVITE_INBOUND,
    INVITE_OUTBOUND,
    ANSWER,
    HANGUP,
    DIALPLAN_STEP,
    DIALPLAN_ACTION,
    DTMF,
    CALLCENTER_EVENT,
    BRIDGE,
    SCRIPT_EXEC,
    HTTP_REQUEST,
    RTP_EVENT,
    OTHER
}
