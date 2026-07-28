package org.ali.projects.buraka.catalog.product;

import java.time.Duration;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class ProductService {

    private static final Duration CACHE_TTL = Duration.ofSeconds(60);

    private final ProductRepository productRepository;
    private final RedisTemplate<String, Product> redisTemplate;
    private final boolean cacheEnabled;

    public ProductService(ProductRepository productRepository,
                          RedisTemplate<String, Product> redisTemplate,
                          @Value("${cache.enabled:true}") boolean cacheEnabled) {
        this.productRepository = productRepository;
        this.redisTemplate = redisTemplate;
        this.cacheEnabled = cacheEnabled;
    }

    public Page<Product> list(Long categoryId, Long brandId, String search, Pageable pageable) {
        return productRepository.findAll(
                ProductSpecifications.filter(categoryId, brandId, search), pageable);
    }

    public Optional<Product> getBySlug(String slug) {
        if (!cacheEnabled) {
            return productRepository.findBySlug(slug);
        }
        String key = key(slug);
        Product cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<Product> product = productRepository.findBySlug(slug);
        product.ifPresent(p -> redisTemplate.opsForValue().set(key, p, CACHE_TTL));
        return product;
    }

    public void evict(String slug) {
        if (cacheEnabled) {
            redisTemplate.delete(key(slug));
        }
    }

    private static String key(String slug) {
        return "product:slug:" + slug;
    }
}
