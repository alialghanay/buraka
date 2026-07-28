package org.ali.projects.buraka.catalog.product;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public Page<Product> list(@RequestParam(required = false) Long categoryId,
                              @RequestParam(required = false) Long brandId,
                              @RequestParam(required = false) String search,
                              @RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "20") int size) {
        return productService.list(categoryId, brandId, search, PageRequest.of(page, size));
    }

    @GetMapping("/{slug}")
    public ResponseEntity<Product> detail(@PathVariable String slug) {
        Optional<Product> product = productService.getBySlug(slug);
        return product.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
