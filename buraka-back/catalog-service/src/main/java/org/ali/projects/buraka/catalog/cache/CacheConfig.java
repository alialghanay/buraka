package org.ali.projects.buraka.catalog.cache;

import org.ali.projects.buraka.catalog.product.Product;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;

@Configuration
public class CacheConfig {

    @Bean
    public RedisTemplate<String, Product> productRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Product> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        GenericJacksonJsonRedisSerializer serializer = GenericJacksonJsonRedisSerializer.create(builder ->
                builder.enableDefaultTyping(BasicPolymorphicTypeValidator.builder()
                        .allowIfBaseType("org.ali.projects.buraka.catalog")
                        .build()));
        template.setValueSerializer(serializer);
        return template;
    }
}
