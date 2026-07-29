package org.ali.projects.buraka.catalog.config;

import java.util.ArrayList;
import java.util.List;

import org.ali.projects.buraka.catalog.brand.Brand;
import org.ali.projects.buraka.catalog.brand.BrandRepository;
import org.ali.projects.buraka.catalog.category.Category;
import org.ali.projects.buraka.catalog.category.CategoryRepository;
import org.ali.projects.buraka.catalog.product.Product;
import org.ali.projects.buraka.catalog.product.ProductRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
public class DataSeeder implements ApplicationRunner {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final BrandRepository brandRepository;

    public DataSeeder(ProductRepository productRepository,
                      CategoryRepository categoryRepository,
                      BrandRepository brandRepository) {
        this.productRepository = productRepository;
        this.categoryRepository = categoryRepository;
        this.brandRepository = brandRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<Brand> brands = seedBrands();
        List<Category> categories = seedCategories();
        seedProducts(brands, categories);
    }

    private List<Brand> seedBrands() {
        List<Brand> brands = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            final int idx = i;
            String slug = "brand-" + idx;
            Brand brand = brandRepository.findBySlug(slug)
                    .orElseGet(() -> saveBrand(new Brand("Brand " + idx, "brand-" + idx)));
            brands.add(brand);
        }
        return brands;
    }

    private Brand saveBrand(Brand brand) {
        try {
            return brandRepository.save(brand);
        } catch (DataIntegrityViolationException e) {
            return brandRepository.findBySlug(brand.getSlug())
                    .orElseThrow(() -> new IllegalStateException("Brand disappeared during seeding", e));
        }
    }

    private List<Category> seedCategories() {
        List<Category> categories = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            final int idx = i;
            String parentSlug = "category-" + idx;
            Category parent = categoryRepository.findBySlug(parentSlug)
                    .orElseGet(() -> saveCategory(new Category(null, "Category " + idx, "category-" + idx)));
            categories.add(parent);

            String subSlug = parentSlug + "-sub";
            Category sub = categoryRepository.findBySlug(subSlug)
                    .orElseGet(() -> saveCategory(new Category(parent.getCategoryId(), "Category " + idx + " Sub", "category-" + idx + "-sub")));
            categories.add(sub);
        }
        return categories;
    }

    private Category saveCategory(Category category) {
        try {
            return categoryRepository.save(category);
        } catch (DataIntegrityViolationException e) {
            return categoryRepository.findBySlug(category.getSlug())
                    .orElseThrow(() -> new IllegalStateException("Category disappeared during seeding", e));
        }
    }

    private void seedProducts(List<Brand> brands, List<Category> categories) {
        for (int i = 1; i <= 200; i++) {
            final int idx = i;
            String slug = "product-" + idx;
            if (productRepository.findBySlug(slug).isPresent()) {
                continue;
            }
            Brand brand = brands.get((idx - 1) % brands.size());
            Category category = categories.get((idx - 1) % categories.size());
            try {
                productRepository.save(new Product(
                        slug,
                        "Product " + idx,
                        "Description for product " + idx,
                        100,
                        1000L * idx,
                        idx % 2 == 0 ? "black,white" : "black",
                        "https://picsum.photos/seed/product-" + idx + "/400/400",
                        category.getCategoryId(),
                        brand.getBrandId()));
            } catch (DataIntegrityViolationException e) {
                // another instance inserted this product concurrently
            }
        }
    }
}
