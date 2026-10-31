package com.team7.rental.global.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import com.team7.rental.global.policy.PolicyProperties;
import com.team7.rental.payment.client.TossPaymentProperties;

@Configuration
@EnableConfigurationProperties({PolicyProperties.class, TossPaymentProperties.class})
public class PolicyConfig {}
