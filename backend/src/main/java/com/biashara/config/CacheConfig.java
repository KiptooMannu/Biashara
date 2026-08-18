package com.biashara.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Cache configuration for performance optimization.
 * 
 * Uses Caffeine (high-performance Java caching library) to reduce database load
 * for frequently accessed data like dashboard KPIs and analytics.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        
        // Configure cache for dashboard data (5-minute TTL)
        cacheManager.registerCustomCache("dashboard", Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.MINUTES)
                .maximumSize(100)
                .recordStats()
                .build());
        
        // Configure cache for KPI calculations (2-minute TTL)
        cacheManager.registerCustomCache("kpi", Caffeine.newBuilder()
                .expireAfterWrite(2, TimeUnit.MINUTES)
                .maximumSize(500)
                .recordStats()
                .build());
        
        // Configure cache for user permissions (10-minute TTL)
        cacheManager.registerCustomCache("permissions", Caffeine.newBuilder()
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .maximumSize(1000)
                .recordStats()
                .build());
        
        // Configure cache for product data (3-minute TTL)
        cacheManager.registerCustomCache("products", Caffeine.newBuilder()
                .expireAfterWrite(3, TimeUnit.MINUTES)
                .maximumSize(200)
                .recordStats()
                .build());
        
        return cacheManager;
    }
}