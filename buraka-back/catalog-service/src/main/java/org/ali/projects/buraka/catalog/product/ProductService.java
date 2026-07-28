package org.ali.projects.buraka.catalog.product;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public Page<Product> list(Long categoryId, Long brandId, String search, Pageable pageable) {
        return productRepository.findAll(
                ProductSpecifications.filter(categoryId, brandId, search), pageable);
    }

    public Optional<Product> getBySlug(String slug) {
        return productRepository.findBySlug(slug);
    }
}
