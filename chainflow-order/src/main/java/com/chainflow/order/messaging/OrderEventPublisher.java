package com.chainflow.order.messaging;

import com.chainflow.order.model.SalesOrder;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "chainflow.messaging.rocketmq-enabled", havingValue = "true")
public class OrderEventPublisher {
    private final RocketMQTemplate rocketMQTemplate;
    public OrderEventPublisher(RocketMQTemplate rocketMQTemplate) { this.rocketMQTemplate = rocketMQTemplate; }
    public void publishPaid(SalesOrder order) {
        rocketMQTemplate.syncSend("chainflow-order-paid", MessageBuilder.withPayload(order).build());
    }
}
