package com.baseboot.common.config;

import com.baseboot.common.utils.BaseUtil;
import com.baseboot.common.utils.DateUtil;
import com.baseboot.common.utils.SpringContextUtil;
import com.baseboot.entry.global.BaseConstant;
import com.baseboot.entry.global.BasePool;
import com.baseboot.interfaces.receive.BusiReceive;
import com.baseboot.interfaces.receive.CommReceive;
import com.baseboot.interfaces.receive.MapReceive;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;
import org.springframework.amqp.support.ConsumerTagStrategy;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.scheduling.annotation.Async;

import java.util.UUID;

@Configuration
@Slf4j
public class RabbitMqConfig {

    @Bean
    @Primary
    public ConnectionFactory createFactory(RabbitProperties properties) {
        CachingConnectionFactory factory = new CachingConnectionFactory();
        factory.setVirtualHost(properties.getVirtualHost());
        factory.setUsername(properties.getUsername());
        factory.setPassword(properties.getPassword());
        factory.setHost(properties.getHost());
        factory.setPort(properties.getPort());
        factory.setPublisherConfirms(true);
        factory.setPublisherReturns(true);
        return factory;
    }

    @Bean
    @Primary
    public RabbitAdmin createRabbitAdmin(ConnectionFactory factory) {
        RabbitAdmin rabbitAdmin = new RabbitAdmin(factory);
        rabbitAdmin.setAutoStartup(true);//程序启动的时候自动创建这些队列等
        return rabbitAdmin;
    }


    @Bean(name = "rabbitTemplate")
    @Scope(value = "prototype")
    public RabbitTemplate rabbitTemplate(ConnectionFactory factory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(factory);
        rabbitTemplate.setMessageConverter(new Jackson2JsonMessageConverter());
        rabbitTemplate.setMandatory(true);
        rabbitTemplate.setConfirmCallback(new ConfirmCallBackListener());
        rabbitTemplate.setReturnCallback(new ReturnCallBackListener());
        return rabbitTemplate;
    }

    @Bean(name = "containerFactory")
    @Primary
    public SimpleRabbitListenerContainerFactory createContainerFactory(ConnectionFactory factory) {
        SimpleRabbitListenerContainerFactory containerFactory = new SimpleRabbitListenerContainerFactory();
        containerFactory.setConnectionFactory(factory);
        containerFactory.setMessageConverter(new Jackson2JsonMessageConverter());
        return containerFactory;
    }

    /**
     * 消息监听容器,可以设置多个消费者参数
     */
    @Bean
    @Primary
    public SimpleMessageListenerContainer messageContainer(ConnectionFactory factory) {
        SimpleMessageListenerContainer messageContainer = new SimpleMessageListenerContainer(factory);
        messageContainer.setQueueNames(QueueConfig.listenerQueues);
        messageContainer.setConcurrentConsumers(2);
        messageContainer.setMaxConcurrentConsumers(10);
        messageContainer.setAcknowledgeMode(AcknowledgeMode.NONE);
        messageContainer.setExposeListenerChannel(true);
        messageContainer.setConsumerTagStrategy(new ConsumerTagStrategy() {
            @Override
            public String createConsumerTag(String queue) {
                return queue + UUID.randomUUID();
            }
        });
        messageContainer.setMessageListener(new ChannelAwareMessageListener() {
            @Override
            public void onMessage(Message message, Channel channel) throws Exception {
                RabbitMqConfig config = SpringContextUtil.getBean(RabbitMqConfig.class);
                config.handleMessage(message);
            }
        });
        return messageContainer;
    }


    public void handleMessage(Message message) {
        String messageId = message.getMessageProperties().getMessageId();
        if (null != messageId && (messageId.startsWith(BaseConstant.MONITOR_VEHICLE_MESSAGE_PREFIX)||
                messageId.startsWith(BaseConstant.MONITOR_HMI_MESSAGE_PREFIX))){
            String jsonStr = new String(message.getBody());
            String routingKey = message.getMessageProperties().getReceivedRoutingKey();
            String methodName = BaseUtil.toLowCaseFirst(routingKey);
            BasePool.commit(BaseUtil.getCurTime(), messageId, () -> {
                CommReceive.dispense(jsonStr, methodName, messageId);
            });
        } else {
            moreThreadHandle(message);
        }
    }

    @Async(value = "baseAsyncThreadPool")
    public void moreThreadHandle(Message message) {
        String jsonStr = new String(message.getBody());
        String messageId = message.getMessageProperties().getMessageId();
        String routingKey = message.getMessageProperties().getReceivedRoutingKey();
        String methodName = BaseUtil.toLowCaseFirst(routingKey);
        String exchange = message.getMessageProperties().getReceivedExchange();
        switch (exchange) {
            case QueueConfig.RECEIVE_MAP:
                MapReceive.dispense(message.getBody(), methodName, messageId);
                break;
            case QueueConfig.RECEIVE_BUSI:
                BusiReceive.dispense(jsonStr, methodName, messageId);
                break;
            case QueueConfig.RECEIVE_COMM:
                CommReceive.dispense(jsonStr, methodName, messageId);
                break;
            default:
                log.error("没有对应处理器!,routingKey={},exchange={}", routingKey, exchange);
        }
    }

    public class ConfirmCallBackListener implements RabbitTemplate.ConfirmCallback {
        /**
         * 消息确认
         */
        @Override
        public void confirm(CorrelationData correlationData, boolean ack, String cause) {
            Message message = correlationData.getReturnedMessage();
            if (ack && null != message) {
                log.error("----------mq消息失败退回，交换机不存在------------");
                String s = new String(message.getBody());
                log.error("回退消息:{}", s);
            } else if (ack) {
                //log.debug("----------mq消息发送成功------------");
            } else if (!ack) {
                log.error("----------mq消息发送失败------------");
            }
        }
    }

    public class ReturnCallBackListener implements RabbitTemplate.ReturnCallback {

        /**
         * 消息失败退回
         */
        @Override
        public void returnedMessage(Message message, int replyCode, String replyText, String exchange, String routingKey) {
            log.error("-----------mq消息被拒收-----------");
        }
    }
}
