package com.baseboot.common.service;

import com.alibaba.fastjson.JSONObject;
import com.baseboot.common.utils.BaseUtil;
import com.baseboot.common.config.QueueConfig;
import com.baseboot.common.utils.LogUtil;
import com.baseboot.common.utils.SpringContextUtil;
import com.baseboot.entry.dispatch.monitor.vehicle.Monitor;
import com.baseboot.entry.global.BaseConstant;
import com.baseboot.entry.global.LogType;
import com.baseboot.entry.global.Request;
import com.baseboot.entry.global.Response;
import com.baseboot.service.BaseCacheUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.SimpleMessageConverter;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
@DependsOn(value = {"springContextUtil", "rabbitTemplate"})
public class MqService implements InitializingBean {

    private final static String EXPIRE_TIME = "5000";

    @Autowired
    private RabbitAdmin admin;

    private static RabbitTemplate jsonTemplate = (RabbitTemplate) SpringContextUtil.getBean("rabbitTemplate");

    private static RabbitTemplate byteTemplate = (RabbitTemplate) SpringContextUtil.getBean("rabbitTemplate");

    static{
        jsonTemplate.setMessageConverter(new Jackson2JsonMessageConverter());
        byteTemplate.setMessageConverter(new SimpleMessageConverter());
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        createQueues();
        createExChanges();
        createBinds();
    }

    private void createQueues() {
        for (String queue : QueueConfig.queues) {
            admin.declareQueue(new Queue(queue, true, false, false));
        }
    }

    private void createExChanges() {
        for (String exchange : QueueConfig.topic_exchanges) {
            admin.declareExchange(new TopicExchange(exchange, true, false));
        }

        for (String exchange : QueueConfig.direct_exchanges) {
            admin.declareExchange(new DirectExchange(exchange, true, false));
        }
    }

    private void createBinds() {
        for (String[] bind : QueueConfig.binds) {
            admin.declareBinding(new Binding(bind[0], Binding.DestinationType.QUEUE, bind[1], bind[2], null));
        }
    }

    /**
     * 发送消息
     */
    private static void sendMessage(String exchange, String routeKey, String message, String messageId) {
        if (StringUtils.isAnyBlank(exchange, routeKey)) {
            log.error("exchange,routeKey isAnyBlank");
            return;
        }
        try {
            JSONObject json = JSONObject.parseObject(message);
            jsonTemplate.convertAndSend(exchange, routeKey, json, msg -> {
                msg.getMessageProperties().setMessageId(messageId);
                msg.getMessageProperties().setExpiration(EXPIRE_TIME);
                return msg;
            }, new CorrelationData(messageId));
        } catch (Exception e) {
            log.error("mq发送数据异常", e);
        }
    }

    /**
     * 发送二进制数据
     * */
    private static void sendMessage(String exchange, String routeKey, byte[] bytes, String messageId) {
        if (StringUtils.isAnyBlank(exchange, routeKey)) {
            log.info("exchange,routeKey isAnyBlank");
            return;
        }
        try {
            byteTemplate.convertAndSend(exchange, routeKey, bytes, msg -> {
                msg.getMessageProperties().setMessageId(messageId);
                msg.getMessageProperties().setExpiration(EXPIRE_TIME);
                msg.getMessageProperties();
                return msg;
            }, new CorrelationData(messageId));
        } catch (Exception e) {
            log.error("mq发送数据异常", e);
        }
    }

    /**
     * 响应请求
     * */
    public static void request(Request request) {
        if(request.isNeedPrint()){
            log.debug("发送请求消息:routeKey={},toWho={},{}",request.getRouteKey(),request.getToHwo(),BaseUtil.StringNotNull(request.getMessage())?request.getMessage():"byteLen="+request.getBytes().length);
        }
        request.setSendTime(new Date());
        if(!BaseUtil.StringNotNull(request.getMessageId())){
            request.setMessageId(String.valueOf(RedisService.generateId()));
        }
        BaseCacheUtil.addRequestMessage(request);
        if(BaseUtil.StringNotNull(request.getMessage())){//发送json
            sendMessage(request.getToHwo(), request.getRouteKey(),request.getMessage(), request.getMessageId());
        }

        if(null!=request.getBytes()){//发送bytes
            sendMessage(request.getToHwo(), request.getRouteKey(),request.getBytes(), request.getMessageId());
        }
    }


    /**
     * 响应请求
     * */
    public static void response(Response response) {
        Map<String, Object> result = new HashMap<>();
        result.put("message",response.getMessage());
        result.put("status",response.getCode());
        log.debug("发送响应消息:{}",response.toString());
        sendMessage(response.getToHwo(), response.getRouteKey(),BaseUtil.toJson(result), response.getMessageId());
    }


    public static void testData() {
        for (int i = 0; i < 100000; i++) {
            Monitor monitor = new Monitor();
            monitor.setFromVakCode(999);
            monitor.setLockedDeviceCode(i);
            sendMessage("TDExchangeReq","VehMonitor",BaseUtil.toJson(monitor), BaseConstant.MONITOR_VEHICLE_MESSAGE_PREFIX+999);
        }
    }
}
