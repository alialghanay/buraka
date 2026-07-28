package org.ali.projects.buraka.catalog.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private RedisTemplate<String, Product> redisTemplate;

    @Mock
    private ValueOperations<String, Product> valueOperations;

    private ProductService service;

    private static Product sampleProduct() {
        return new Product("product-1", "Product 1", "desc", 100, 1000L,
                "black", "img", 1L, 1L);
    }

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void getBySlugCacheMissLoadsFromDbAndCaches() {
        service = new ProductService(productRepository, redisTemplate, true);
        Product product = sampleProduct();
        when(valueOperations.get("product:slug:product-1")).thenReturn(null);
        when(productRepository.findBySlug("product-1")).thenReturn(Optional.of(product));

        Optional<Product> result = service.getBySlug("product-1");

        assertThat(result).contains(product);
        verify(valueOperations).set(eq("product:slug:product-1"), eq(product), eq(Duration.ofSeconds(60)));
    }

    @Test
    void getBySlugCacheHitSkipsDb() {
        service = new ProductService(productRepository, redisTemplate, true);
        Product product = sampleProduct();
        when(valueOperations.get("product:slug:product-1")).thenReturn(product);

        Optional<Product> result = service.getBySlug("product-1");

        assertThat(result).contains(product);
        verifyNoInteractions(productRepository);
    }

    @Test
    void getBySlugCacheDisabledGoesStraightToDb() {
        service = new ProductService(productRepository, redisTemplate, false);
        Product product = sampleProduct();
        when(productRepository.findBySlug("product-1")).thenReturn(Optional.of(product));

        Optional<Product> result = service.getBySlug("product-1");

        assertThat(result).contains(product);
        verifyNoInteractions(redisTemplate);
    }

    @Test
    void applyReviewCreatedUpdatesAverageAndCount() {
        service = new ProductService(productRepository, redisTemplate, true);
        Product product = sampleProduct();
        product.setRatingsAverage(4.0);
        product.setRatingsQuantity(2);
        when(productRepository.findById(10L)).thenReturn(java.util.Optional.of(product));

        service.applyReviewCreated(10L, 5);

        assertThat(product.getRatingsQuantity()).isEqualTo(3);
        assertThat(product.getRatingsAverage()).isCloseTo(13.0 / 3.0, org.assertj.core.data.Offset.offset(0.0001));
        verify(productRepository).save(product);
        verify(redisTemplate).delete("product:slug:product-1");
    }

    @Test
    void applyReviewDeletedRecomputesAverage() {
        service = new ProductService(productRepository, redisTemplate, true);
        Product product = sampleProduct();
        product.setRatingsAverage(4.0);
        product.setRatingsQuantity(2);
        when(productRepository.findById(10L)).thenReturn(java.util.Optional.of(product));

        service.applyReviewDeleted(10L, 3);

        assertThat(product.getRatingsQuantity()).isEqualTo(1);
        assertThat(product.getRatingsAverage()).isCloseTo(5.0, org.assertj.core.data.Offset.offset(0.0001));
        verify(redisTemplate).delete("product:slug:product-1");
    }

    @Test
    void applyReviewDeletedWithSingleReviewResetsToZero() {
        service = new ProductService(productRepository, redisTemplate, true);
        Product product = sampleProduct();
        product.setRatingsAverage(5.0);
        product.setRatingsQuantity(1);
        when(productRepository.findById(10L)).thenReturn(java.util.Optional.of(product));

        service.applyReviewDeleted(10L, 5);

        assertThat(product.getRatingsQuantity()).isZero();
        assertThat(product.getRatingsAverage()).isZero();
    }
}
