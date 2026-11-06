package com.demo.syncpay.payment.config;

import com.demo.syncpay.payment.consumer.KafkaCorrelationInterceptor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;

/** Wires the correlation interceptor into Kafka listener container factories. */
@Configuration
public class KafkaListenerConfig {

    @Bean
    public static BeanPostProcessor kafkaRecordInterceptorPostProcessor() {
        KafkaCorrelationInterceptor interceptor = new KafkaCorrelationInterceptor();
        return new BeanPostProcessor() {
            @Override
            @SuppressWarnings("unchecked")
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof ConcurrentKafkaListenerContainerFactory<?, ?> factory) {
                    @SuppressWarnings({"unchecked", "rawtypes"})
                    var raw = (ConcurrentKafkaListenerContainerFactory) factory;
                    raw.setRecordInterceptor(interceptor);
                }
                return bean;
            }
        };
    }
}
