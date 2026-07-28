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
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
    @Transactional
    public void run(ApplicationArguments args) {
        if (productRepository.count() > 0) {
            return;
        }
        List<Brand> brands = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            brands.add(brandRepository.save(new Brand("Brand " + i, "brand-" + i)));
        }
        List<Category> categories = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            Category parent = categoryRepository.save(new Category(null, "Category " + i, "category-" + i));
            categories.add(parent);
            categories.add(categoryRepository.save(
                    new Category(parent.getCategoryId(), "Category " + i + " Sub", "category-" + i + "-sub")));
        }
        for (int i = 1; i <= 200; i++) {
            Brand brand = brands.get((i - 1) % brands.size());
            Category category = categories.get((i - 1) % categories.size());
            productRepository.save(new Product(
                    "product-" + i,
                    "Product " + i,
                    "Description for product " + i,
                    100,
                    1000L * i,
                    i % 2 == 0 ? "black,white" : "black",
                    "https://picsum.photos/seed/product-" + i + "/400/400",
                    category.getCategoryId(),
                    brand.getBrandId()));
        }
    }
}
