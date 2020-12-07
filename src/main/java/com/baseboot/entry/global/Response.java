package com.baseboot.entry.global;

import com.baseboot.common.utils.BaseUtil;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonValue;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class Response {

    private String messageId;

    private String routeKey;

    private String message;

    private String code;

    private String toHwo;


    public enum StatusCode {

        SUCCESS("0", "成功"),
        FAIL("1", "失败");

        private String code;

        private String desc;

        StatusCode(String code, String desc) {
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

    public Response withMessageId(String messageId) {
        this.setMessageId(messageId);
        return this;
    }

    public Response withRouteKey(String routeKey) {
        this.setRouteKey(routeKey);
        return this;
    }

    public Response withMessage(String desc) {
        this.setMessage(desc);
        return this;
    }

    public Response withSucMessage(String desc) {
        this.setMessage(desc);
        this.setCode(StatusCode.SUCCESS.getCode());
        return this;
    }

    public Response withFailMessage(String desc) {
        this.setMessage(desc);
        this.setCode(StatusCode.FAIL.getCode());
        return this;
    }

    public Response withCode(StatusCode code) {
        this.setCode(code.getCode());
        return this;
    }

    public Response withCode(String code) {
        this.setCode(code);
        return this;
    }

    public Response withToWho(String toHow) {
        this.setToHwo(toHow);
        return this;
    }

    public String toJsonString() {
        Map<String, Object> response = new HashMap<>();
        response.put("routeKey", this.routeKey);
        response.put("message", this.message);
        response.put("toHwo", this.toHwo);
        response.put("code", this.code);
        return BaseUtil.toJson(response);
    }

    @Override
    public String toString() {
        return BaseUtil.format("toHwo={},routeKey={},messageId={},desc={},status={}",
                toHwo, routeKey, messageId, message, code);
    }
}
