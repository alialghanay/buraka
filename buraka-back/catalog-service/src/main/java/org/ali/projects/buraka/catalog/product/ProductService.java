package org.ali.projects.buraka.catalog.product;

import java.time.Duration;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    @Transactional
    public void applyReviewCreated(long productId, int rating) {
        productRepository.findById(productId).ifPresent(product -> {
            double total = product.getRatingsAverage() * product.getRatingsQuantity() + rating;
            int count = product.getRatingsQuantity() + 1;
            product.setRatingsAverage(total / count);
            product.setRatingsQuantity(count);
            productRepository.save(product);
            evict(product.getSlug());
        });
    }

    @Transactional
    public void applyReviewDeleted(long productId, int rating) {
        productRepository.findById(productId).ifPresent(product -> {
            int count = product.getRatingsQuantity();
            if (count <= 1) {
                product.setRatingsAverage(0.0);
                product.setRatingsQuantity(0);
            } else {
                double total = product.getRatingsAverage() * count - rating;
                product.setRatingsQuantity(count - 1);
                product.setRatingsAverage(total / (count - 1));
            }
            productRepository.save(product);
            evict(product.getSlug());
        });
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
