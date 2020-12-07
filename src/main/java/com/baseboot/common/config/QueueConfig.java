package com.baseboot.common.config;

public class QueueConfig {

    public final static String RESPONSE_MAP="DMExchangeReq";
    public final static String RESPONSE_BUSI="DBResponse";
    public final static String RESPONSE_COMM="DTExchangeReq";

    public final static String REQUEST_MAP="DMExchangeReq";
    public final static String REQUEST_BUSI="DBResponse";
    public final static String REQUEST_COMM="DTExchangeReq";

    public final static String RECEIVE_MAP="MDExchangeResp";
    public final static String RECEIVE_BUSI="BDRequest";
    public final static String RECEIVE_COMM="TDExchangeReq";

    public final static String[] listenerQueues = {"Busi2Disp", "Tran2Disp", "Map2Disp"};

    public final static String[] queues = {"Busi2Disp", "Disp2Busi", "Tran2Disp", "Disp2Tran", "Map2Disp", "Disp2Map"};

    public final static String[] topic_exchanges = {
            "BDExchangeReq", "BDExchangeResp", "DBRequest", "DBResponse","BDRequest",
    };

    public final static String[] direct_exchanges = {
            "TDExchangeReq", "TDExchangeResp", "DTExchangeReq", "DTExchangeResp",
            "MDExchangeReq", "MDExchangeResp", "DMExchangeReq", "DMExchangeResp"
    };

    public final static String[][] binds = {
            {"Busi2Disp", "BDRequest", "#"},

            {"Disp2Map", "DMExchangeReq", "getGlobalPath"},
            {"Disp2Map", "DMExchangeReq", "getInteractiveGlobalPath"},
            {"Disp2Map", "DMExchangeReq", "getTrajectory"},
            {"Disp2Map", "DMExchangeReq", "getTrajectoryByIdx"},
            {"Disp2Map", "DMExchangeReq", "getSemiStaticLayerInfo"},
            {"Disp2Map", "DMExchangeReq", "notifyGetMap"},
            {"Disp2Map", "DMExchangeReq", "getDipTaskAngle"},
            {"Disp2Map", "DMExchangeReq", "fittingGlobalPath"},

            {"Map2Disp", "MDExchangeResp", "getGlobalPath"},
            {"Map2Disp", "MDExchangeResp", "getInteractiveGlobalPath"},
            {"Map2Disp", "MDExchangeResp", "getTrajectory"},
            {"Map2Disp", "MDExchangeResp", "getTrajectoryByIdx"},
            {"Map2Disp", "MDExchangeResp", "getSemiStaticLayerInfo"},
            {"Map2Disp", "MDExchangeResp", "notifyGetMap"},
            {"Map2Disp", "MDExchangeResp", "getDipTaskAngle"},
            {"Map2Disp", "MDExchangeResp", "fittingGlobalPath"},

            {"Tran2Disp", "TDExchangeReq", "VehMonitor"},
            {"Tran2Disp", "TDExchangeReq", "excavatorHmiMonitor"},
            {"Tran2Disp", "TDExchangeReq", "excavatorGpsMonitor"},

            {"Disp2Tran", "DTExchangeReq", "excavatorSend"},

            {"Disp2Tran", "DTExchangeReq", "VehModeRemote"},
            {"Disp2Tran", "DTExchangeReq", "VehModeAuto"},
            {"Disp2Tran", "DTExchangeReq", "VehHeartbeat1"},
            {"Disp2Tran", "DTExchangeReq", "VehHeartbeat2"},
            {"Disp2Tran", "DTExchangeReq", "VehAutoStop"},
            {"Disp2Tran", "DTExchangeReq", "VehAutoStart"},
            {"Disp2Tran", "DTExchangeReq", "VehAutoStandby"},
            {"Disp2Tran", "DTExchangeReq", "VehAutoTrailFollowing"},
            {"Disp2Tran", "DTExchangeReq", "VehAutoLoadBrake"},
            {"Disp2Tran", "DTExchangeReq", "VehAutoParkingBrake"},
            {"Disp2Tran", "DTExchangeReq", "VehAutoEmergencyParking"},
            {"Disp2Tran", "DTExchangeReq", "VehAutoEmergencyParkingByPath"},
            {"Disp2Tran", "DTExchangeReq", "VehAutoSafeParking"},
            {"Disp2Tran", "DTExchangeReq", "VehAutoForcedStart"},
            {"Disp2Tran", "DTExchangeReq", "VehAutoClearEmergencyPark"},
            {"Disp2Tran", "DTExchangeReq", "VehAutoDump"},
            {"Disp2Tran", "DTExchangeReq", "VehAutoUnload"},
            {"Disp2Tran", "DTExchangeReq", "VehAutoMode"},
    };
}
