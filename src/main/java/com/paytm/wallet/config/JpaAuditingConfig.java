package com.paytm.wallet.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Enables {@code @CreatedDate}/{@code @LastModifiedDate} population on entities.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
