package com.baseboot.entry.global;

import com.baseboot.common.utils.BaseUtil;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Data;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Request {

    private String messageId;

    private String routeKey;

    private String message;

    private byte[] bytes;

    private MessageStatus status = MessageStatus.NONE;//发送消息的状态

    private String toHwo;

    private boolean needPrint = true;

    private Date sendTime;

    public enum MessageStatus {

        SUCCESS("0", "成功"),
        FAIL("1", "失败"),
        NONE("2", "不需要处理");

        private String code;

        private String desc;

        MessageStatus(String code, String desc) {
            this.code = code;
            this.desc = desc;
        }

        @JsonValue
        public String getCode() {
            return this.code;
        }

        public String getDesc() {
            return this.desc;
        }
    }

    public Request withMessageId(String messageId) {
        this.setMessageId(messageId);
        return this;
    }

    public Request withRouteKey(String routeKey) {
        this.setRouteKey(routeKey);
        return this;
    }

    public Request withMessage(String desc) {
        this.setMessage(desc);
        return this;
    }

    public Request withBytes(byte[] bytes) {
        this.setBytes(bytes);
        return this;
    }


    public Request withStatus(MessageStatus code) {
        this.setStatus(code);
        return this;
    }

    public Request withToWho(String toHow) {
        this.setToHwo(toHow);
        return this;
    }

    public Request withNeedPrint(boolean needPrint) {
        this.setNeedPrint(needPrint);
        return this;
    }

    public String toJsonString() {
        Map<String, Object> request = new HashMap<>();
        request.put("routeKey", this.routeKey);
        request.put("message", this.message);
        request.put("toHwo", this.toHwo);
        return BaseUtil.toJson(request);
    }


    @Override
    public String toString() {
        return BaseUtil.format("toHwo={},routeKey={},messageId={},desc={},status={}",
                toHwo, routeKey, messageId, message, status);
    }
}
