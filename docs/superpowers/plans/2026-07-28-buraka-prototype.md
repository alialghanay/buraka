# Buraka Prototype Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a working prototype of two Buraka microservices (catalog-service, review-service) with simple React and Flutter frontends, plus a k6-based scalability test that validates the architecture document's caching and horizontal-scaling claims.

**Architecture:** Two standalone Spring Boot services backed by their own PostgreSQL databases, integrated asynchronously via RabbitMQ (`ReviewCreated`/`ReviewDeleted` events recalculate product rating aggregates), with Redis cache-aside on product detail reads. React (Vite) and Flutter apps consume the REST APIs. Docker Compose provides infrastructure; a separate scale compose file runs the catalog behind an NGINX load balancer for 1-vs-3-replica and cache-on-vs-off k6 experiments.

**Tech Stack:** Java 25, Spring Boot 4.1.0 (webmvc, data-jpa, data-redis, amqp, validation), PostgreSQL 16, Redis 7, RabbitMQ 3.13, Gradle 9.5.1 wrapper, React 18 + Vite + vitest, Flutter 3.44.5 + http, NGINX, k6 (via `grafana/k6` Docker image).

## Global Constraints

- Decisions (from user Q&A): implement **catalog-service + review-service** only; frontends **React web + Flutter**; scalability tested with **k6 + docker-compose scaling**.
- Prototype simplifications (deliberate, do not "fix"): no api-gateway, no auth-user-service, no order-service, no Moamalat. Users are identified by an `X-User-Id` request header (default `1` in frontends). CORS is `allowedOrigins("*")` — prototype only.
- Java toolchain: **25** (the machine has JDK 25 only; the scaffold's Java 17 toolchain must NOT be copied).
- Spring Boot **4.1.0**, plugin `io.spring.dependency-management` **1.1.7**, matching `buraka-back/Buraka/build.gradle`. Starter names follow the scaffold: `spring-boot-starter-webmvc`, tests `spring-boot-starter-webmvc-test`.
- Jackson 3-era class names (verified against Spring 4.x Javadocs — do not substitute): `org.springframework.amqp.support.converter.JacksonJsonMessageConverter` constructed with the trusted package `"org.ali.projects.buraka.shared.messaging"` (NOT the deprecated `Jackson2JsonMessageConverter`), and `org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer` (NOT `GenericJackson2JsonRedisSerializer`).
- Packages: `org.ali.projects.buraka.catalog` and `org.ali.projects.buraka.review`. The shared event contract class MUST be `org.ali.projects.buraka.shared.messaging.ReviewEvent` in **both** services (same fully-qualified name so RabbitMQ type headers resolve across services).
- Ports: catalog-service `8081` locally (`8080` inside Docker, behind NGINX LB on host `8080`); review-service `8082`; postgres-catalog `5433`; postgres-review `5434`; redis `6379`; rabbitmq `5672`/`15672`.
- Event contract: topic exchange `buraka.events` (durable); routing keys `review.created` / `review.deleted`; catalog queue `catalog.review-events` (durable, bound `review.*`); JSON payload `{"reviewId":N,"productId":N,"rating":N,"type":"CREATED"|"DELETED"}`.
- Cache: Redis key `product:slug:{slug}`, TTL 60 s, cache-aside, toggle `CACHE_ENABLED` (property `cache.enabled`, default `true`). Rating updates evict the key.
- Seed data (deterministic, idempotent — skip if products exist): 200 products with slugs `product-1`…`product-200`, 10 categories (5 parents + 5 children), 5 brands.
- The existing scaffold `buraka-back/Buraka/` is left untouched. No changes under `buraka-docs/`.
- Tests: JUnit 5 + AssertJ (bundled in `spring-boot-starter-webmvc-test`), Mockito for unit tests, `@WebMvcTest` + `@MockitoBean` for controller slices. NO `@SpringBootTest`, NO Testcontainers (keep the suite fast and infra-free).
- Every task ends with `git add` + `git commit`. Run focused tests while iterating; run the full service suite once before committing.
- Working directory for all commands: `/Users/alialghanay/repo/buraka`.

---

### Task 1: Service Scaffolds (catalog-service + review-service)

**Files:**
- Create: `buraka-back/catalog-service/settings.gradle`
- Create: `buraka-back/catalog-service/build.gradle`
- Create: `buraka-back/review-service/settings.gradle`
- Create: `buraka-back/review-service/build.gradle`
- Copy: `buraka-back/Buraka/gradlew`, `gradlew.bat`, `gradle/` into both new service directories
- Create: `buraka-back/catalog-service/src/main/resources/application.properties`
- Create: `buraka-back/review-service/src/main/resources/application.properties`
- Create: `buraka-back/catalog-service/src/main/java/org/ali/projects/buraka/catalog/CatalogServiceApplication.java`
- Create: `buraka-back/review-service/src/main/java/org/ali/projects/buraka/review/ReviewServiceApplication.java`

**Interfaces:**
- Consumes: nothing (first task).
- Produces: buildable, runnable empty services. Later tasks add code to these projects and rely on the ports and property keys defined here.

- [ ] **Step 1: Create the directory layout and copy the Gradle wrapper**

```bash
cd /Users/alialghanay/repo/buraka
mkdir -p buraka-back/catalog-service/src/main/java/org/ali/projects/buraka/catalog
mkdir -p buraka-back/catalog-service/src/main/resources
mkdir -p buraka-back/catalog-service/src/test/java/org/ali/projects/buraka/catalog
mkdir -p buraka-back/review-service/src/main/java/org/ali/projects/buraka/review
mkdir -p buraka-back/review-service/src/main/resources
mkdir -p buraka-back/review-service/src/test/java/org/ali/projects/buraka/review
for svc in catalog-service review-service; do
  cp buraka-back/Buraka/gradlew buraka-back/Buraka/gradlew.bat "buraka-back/$svc/"
  cp -r buraka-back/Buraka/gradle "buraka-back/$svc/"
done
```

- [ ] **Step 2: Write catalog-service build files**

`buraka-back/catalog-service/settings.gradle`:
```groovy
rootProject.name = 'catalog-service'
```

`buraka-back/catalog-service/build.gradle`:
```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '4.1.0'
    id 'io.spring.dependency-management' version '1.1.7'
}

group = 'org.ali.projects'
version = '0.0.1-SNAPSHOT'
description = 'Buraka Catalog Service'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-webmvc'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-data-redis'
    implementation 'org.springframework.boot:spring-boot-starter-amqp'
    runtimeOnly 'org.postgresql:postgresql'
    testImplementation 'org.springframework.boot:spring-boot-starter-webmvc-test'
    testRuntimeOnly 'com.h2database:h2'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

- [ ] **Step 3: Write review-service build files**

`buraka-back/review-service/settings.gradle`:
```groovy
rootProject.name = 'review-service'
```

`buraka-back/review-service/build.gradle`:
```groovy
plugins {
    id 'java'
    id 'org.springframework.boot' version '4.1.0'
    id 'io.spring.dependency-management' version '1.1.7'
}

group = 'org.ali.projects'
version = '0.0.1-SNAPSHOT'
description = 'Buraka Review Service'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter-webmvc'
    implementation 'org.springframework.boot:spring-boot-starter-data-jpa'
    implementation 'org.springframework.boot:spring-boot-starter-amqp'
    implementation 'org.springframework.boot:spring-boot-starter-validation'
    runtimeOnly 'org.postgresql:postgresql'
    testImplementation 'org.springframework.boot:spring-boot-starter-webmvc-test'
    testRuntimeOnly 'com.h2database:h2'
    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
}

tasks.named('test') {
    useJUnitPlatform()
}
```

- [ ] **Step 4: Write application.properties for both services**

`buraka-back/catalog-service/src/main/resources/application.properties`:
```properties
spring.application.name=catalog-service
server.port=8081
spring.datasource.url=jdbc:postgresql://localhost:5433/buraka_catalog_db
spring.datasource.username=buraka
spring.datasource.password=buraka
spring.jpa.hibernate.ddl-auto=update
spring.data.redis.host=localhost
spring.data.redis.port=6379
spring.rabbitmq.host=localhost
spring.rabbitmq.port=5672
spring.rabbitmq.username=guest
spring.rabbitmq.password=guest
cache.enabled=true
```

`buraka-back/review-service/src/main/resources/application.properties`:
```properties
spring.application.name=review-service
server.port=8082
spring.datasource.url=jdbc:postgresql://localhost:5434/buraka_review_db
spring.datasource.username=buraka
spring.datasource.password=buraka
spring.jpa.hibernate.ddl-auto=update
spring.rabbitmq.host=localhost
spring.rabbitmq.port=5672
spring.rabbitmq.username=guest
spring.rabbitmq.password=guest
```

- [ ] **Step 5: Write the two application entry points**

`buraka-back/catalog-service/src/main/java/org/ali/projects/buraka/catalog/CatalogServiceApplication.java`:
```java
package org.ali.projects.buraka.catalog;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CatalogServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CatalogServiceApplication.class, args);
    }
}
```

`buraka-back/review-service/src/main/java/org/ali/projects/buraka/review/ReviewServiceApplication.java`:
```java
package org.ali.projects.buraka.review;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ReviewServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReviewServiceApplication.class, args);
    }
}
```

- [ ] **Step 6: Verify both services compile**

Run: `cd buraka-back/catalog-service && ./gradlew compileJava -q && echo CATALOG_OK; cd ../review-service && ./gradlew compileJava -q && echo REVIEW_OK`
Expected: `CATALOG_OK` then `REVIEW_OK` (first run downloads Gradle 9.5.1 and dependencies — allow several minutes).

- [ ] **Step 7: Commit**

```bash
cd /Users/alialghanay/repo/buraka
git add buraka-back/catalog-service buraka-back/review-service
git commit -m "feat(prototype): scaffold catalog-service and review-service"
```

---

### Task 2: Catalog Domain, Persistence, and Seed Data

**Files:**
- Create: `buraka-back/catalog-service/src/main/java/org/ali/projects/buraka/catalog/product/Product.java`
- Create: `buraka-back/catalog-service/src/main/java/org/ali/projects/buraka/catalog/product/ProductRepository.java`
- Create: `buraka-back/catalog-service/src/main/java/org/ali/projects/buraka/catalog/category/Category.java`
- Create: `buraka-back/catalog-service/src/main/java/org/ali/projects/buraka/catalog/category/CategoryRepository.java`
- Create: `buraka-back/catalog-service/src/main/java/org/ali/projects/buraka/catalog/brand/Brand.java`
- Create: `buraka-back/catalog-service/src/main/java/org/ali/projects/buraka/catalog/brand/BrandRepository.java`
- Create: `buraka-back/catalog-service/src/main/java/org/ali/projects/buraka/catalog/config/DataSeeder.java`

**Interfaces:**
- Consumes: Task 1 scaffold.
- Produces: entities `Product`, `Category`, `Brand` and repositories used by Tasks 3–6. `Product` fields: `productId, slug, title, description, quantity, sold, price, colors, imageCover, categoryId, brandId, ratingsAverage, ratingsQuantity`. Seed slugs `product-1`…`product-200` are relied on by the k6 script in Task 10.

- [ ] **Step 1: Write the Product entity**

`Product.java`:
```java
package org.ali.projects.buraka.catalog.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long productId;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String title;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private int sold;

    @Column(nullable = false)
    private long price;

    private String colors;

    private String imageCover;

    @Column(nullable = false)
    private Long categoryId;

    @Column(nullable = false)
    private Long brandId;

    @Column(nullable = false)
    private double ratingsAverage;

    @Column(nullable = false)
    private int ratingsQuantity;

    protected Product() {
    }

    public Product(String slug, String title, String description, int quantity, long price,
                   String colors, String imageCover, Long categoryId, Long brandId) {
        this.slug = slug;
        this.title = title;
        this.description = description;
        this.quantity = quantity;
        this.sold = 0;
        this.price = price;
        this.colors = colors;
        this.imageCover = imageCover;
        this.categoryId = categoryId;
        this.brandId = brandId;
        this.ratingsAverage = 0.0;
        this.ratingsQuantity = 0;
    }

    public Long getProductId() {
        return productId;
    }

    public String getSlug() {
        return slug;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public int getQuantity() {
        return quantity;
    }

    public int getSold() {
        return sold;
    }

    public long getPrice() {
        return price;
    }

    public String getColors() {
        return colors;
    }

    public String getImageCover() {
        return imageCover;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public Long getBrandId() {
        return brandId;
    }

    public double getRatingsAverage() {
        return ratingsAverage;
    }

    public void setRatingsAverage(double ratingsAverage) {
        this.ratingsAverage = ratingsAverage;
    }

    public int getRatingsQuantity() {
        return ratingsQuantity;
    }

    public void setRatingsQuantity(int ratingsQuantity) {
        this.ratingsQuantity = ratingsQuantity;
    }
}
```

- [ ] **Step 2: Write Category and Brand entities**

`Category.java`:
```java
package org.ali.projects.buraka.catalog.category;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "categories")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long categoryId;

    private Long parentId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, unique = true)
    private String slug;

    protected Category() {
    }

    public Category(Long parentId, String title, String slug) {
        this.parentId = parentId;
        this.title = title;
        this.slug = slug;
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public Long getParentId() {
        return parentId;
    }

    public String getTitle() {
        return title;
    }

    public String getSlug() {
        return slug;
    }
}
```

`Brand.java`:
```java
package org.ali.projects.buraka.catalog.brand;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "brands")
public class Brand {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long brandId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    protected Brand() {
    }

    public Brand(String name, String slug) {
        this.name = name;
        this.slug = slug;
    }

    public Long getBrandId() {
        return brandId;
    }

    public String getName() {
        return name;
    }

    public String getSlug() {
        return slug;
    }
}
```

- [ ] **Step 3: Write the repositories**

`ProductRepository.java`:
```java
package org.ali.projects.buraka.catalog.product;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    Optional<Product> findBySlug(String slug);
}
```

`CategoryRepository.java`:
```java
package org.ali.projects.buraka.catalog.category;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {
}
```

`BrandRepository.java`:
```java
package org.ali.projects.buraka.catalog.brand;

import org.springframework.data.jpa.repository.JpaRepository;

public interface BrandRepository extends JpaRepository<Brand, Long> {
}
```

- [ ] **Step 4: Write the DataSeeder**

`DataSeeder.java`:
```java
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
```

- [ ] **Step 5: Verify compilation**

Run: `cd buraka-back/catalog-service && ./gradlew compileJava -q && echo OK`
Expected: `OK`

- [ ] **Step 6: Commit**

```bash
cd /Users/alialghanay/repo/buraka
git add buraka-back/catalog-service/src
git commit -m "feat(catalog): add domain entities, repositories, and deterministic seed data"
```

---

### Task 3: Catalog REST API

**Files:**
- Create: `buraka-back/catalog-service/src/main/java/org/ali/projects/buraka/catalog/product/ProductSpecifications.java`
- Create: `buraka-back/catalog-service/src/main/java/org/ali/projects/buraka/catalog/product/ProductService.java`
- Create: `buraka-back/catalog-service/src/main/java/org/ali/projects/buraka/catalog/product/ProductController.java`
- Create: `buraka-back/catalog-service/src/main/java/org/ali/projects/buraka/catalog/category/CategoryController.java`
- Create: `buraka-back/catalog-service/src/main/java/org/ali/projects/buraka/catalog/brand/BrandController.java`
- Create: `buraka-back/catalog-service/src/main/java/org/ali/projects/buraka/catalog/config/CorsConfig.java`
- Test: `buraka-back/catalog-service/src/test/java/org/ali/projects/buraka/catalog/product/ProductControllerTest.java`

**Interfaces:**
- Consumes: `Product`, `Category`, `Brand`, repositories from Task 2.
- Produces: REST endpoints consumed by frontends (Tasks 8–9) and k6 (Task 10):
  - `GET /api/products?page=0&size=20&categoryId=&brandId=&search=` → Spring `Page<Product>` JSON (`content`, `totalElements`)
  - `GET /api/products/{slug}` → `Product` JSON or 404
  - `GET /api/categories` → `List<Category>`; `GET /api/brands` → `List<Brand>`
  - `ProductService.list(Long, Long, String, Pageable)` and `ProductService.getBySlug(String)` — Task 4 adds caching inside `getBySlug`; Task 6 adds rating-mutation methods to the same class.

- [ ] **Step 1: Write the failing controller test**

`ProductControllerTest.java`:
```java
package org.ali.projects.buraka.catalog.product;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    private static Product sampleProduct() {
        return new Product("product-1", "Product 1", "desc", 100, 1000L,
                "black", "img", 1L, 1L);
    }

    @Test
    void listReturnsPageOfProducts() throws Exception {
        when(productService.list(isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sampleProduct()), PageRequest.of(0, 20), 1));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].slug").value("product-1"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void detailReturnsProduct() throws Exception {
        when(productService.getBySlug("product-1")).thenReturn(Optional.of(sampleProduct()));

        mockMvc.perform(get("/api/products/product-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Product 1"));
    }

    @Test
    void detailReturns404ForUnknownSlug() throws Exception {
        when(productService.getBySlug("nope")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/products/nope"))
                .andExpect(status().isNotFound());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd buraka-back/catalog-service && ./gradlew test --tests '*ProductControllerTest' -q`
Expected: FAIL — compilation error, `ProductController` and `ProductService` do not exist.

- [ ] **Step 3: Write ProductSpecifications, ProductService, and the controllers**

`ProductSpecifications.java`:
```java
package org.ali.projects.buraka.catalog.product;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.criteria.Predicate;

import org.springframework.data.jpa.domain.Specification;

public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    public static Specification<Product> filter(Long categoryId, Long brandId, String search) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (categoryId != null) {
                predicates.add(criteriaBuilder.equal(root.get("categoryId"), categoryId));
            }
            if (brandId != null) {
                predicates.add(criteriaBuilder.equal(root.get("brandId"), brandId));
            }
            if (search != null && !search.isBlank()) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("title")), "%" + search.toLowerCase() + "%"));
            }
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
```

`ProductService.java`:
```java
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
```

`ProductController.java`:
```java
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
```

`CategoryController.java`:
```java
package org.ali.projects.buraka.catalog.category;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryRepository categoryRepository;

    public CategoryController(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @GetMapping
    public List<Category> all() {
        return categoryRepository.findAll();
    }
}
```

`BrandController.java`:
```java
package org.ali.projects.buraka.catalog.brand;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/brands")
public class BrandController {

    private final BrandRepository brandRepository;

    public BrandController(BrandRepository brandRepository) {
        this.brandRepository = brandRepository;
    }

    @GetMapping
    public List<Brand> all() {
        return brandRepository.findAll();
    }
}
```

`CorsConfig.java`:
```java
package org.ali.projects.buraka.catalog.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**").allowedOrigins("*").allowedMethods("*");
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd buraka-back/catalog-service && ./gradlew test --tests '*ProductControllerTest' -q && echo PASS`
Expected: `PASS`

- [ ] **Step 5: Commit**

```bash
cd /Users/alialghanay/repo/buraka
git add buraka-back/catalog-service/src
git commit -m "feat(catalog): add product/category/brand REST API with filtering and pagination"
```

---

### Task 4: Redis Cache-Aside with Toggle

**Files:**
- Create: `buraka-back/catalog-service/src/main/java/org/ali/projects/buraka/catalog/cache/CacheConfig.java`
- Modify: `buraka-back/catalog-service/src/main/java/org/ali/projects/buraka/catalog/product/ProductService.java` (full rewrite below)
- Test: `buraka-back/catalog-service/src/test/java/org/ali/projects/buraka/catalog/product/ProductServiceTest.java`

**Interfaces:**
- Consumes: `ProductService` from Task 3.
- Produces: cache-aside `getBySlug` keyed `product:slug:{slug}` (TTL 60 s) gated by `cache.enabled`; `evict(String slug)` used by Task 6's event listener. Behavior contract (asserted in tests): cache hit → no DB call; cache miss → DB then cache write; `cache.enabled=false` → DB only, Redis untouched.

- [ ] **Step 1: Write the failing service test**

`ProductServiceTest.java`:
```java
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
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd buraka-back/catalog-service && ./gradlew test --tests '*ProductServiceTest' -q`
Expected: FAIL — compilation error, the 3-arg `ProductService` constructor does not exist.

- [ ] **Step 3: Rewrite ProductService with cache-aside and add CacheConfig**

Replace `ProductService.java` entirely:
```java
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
```

`CacheConfig.java`:
```java
package org.ali.projects.buraka.catalog.cache;

import org.ali.projects.buraka.catalog.product.Product;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class CacheConfig {

    @Bean
    public RedisTemplate<String, Product> productRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Product> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJacksonJsonRedisSerializer());
        return template;
    }
}
```

- [ ] **Step 4: Run tests to verify they pass (both new and existing)**

Run: `cd buraka-back/catalog-service && ./gradlew test -q && echo PASS`
Expected: `PASS` (ProductServiceTest + ProductControllerTest both green)

- [ ] **Step 5: Commit**

```bash
cd /Users/alialghanay/repo/buraka
git add buraka-back/catalog-service/src
git commit -m "feat(catalog): add redis cache-aside on product detail with cache.enabled toggle"
```

---

### Task 5: Review Service — Domain and REST API

**Files:**
- Create: `buraka-back/review-service/src/main/java/org/ali/projects/buraka/review/review/Review.java`
- Create: `buraka-back/review-service/src/main/java/org/ali/projects/buraka/review/review/ReviewRepository.java`
- Create: `buraka-back/review-service/src/main/java/org/ali/projects/buraka/review/review/CreateReviewRequest.java`
- Create: `buraka-back/review-service/src/main/java/org/ali/projects/buraka/review/review/ReviewService.java`
- Create: `buraka-back/review-service/src/main/java/org/ali/projects/buraka/review/review/ReviewController.java`
- Create: `buraka-back/review-service/src/main/java/org/ali/projects/buraka/review/config/CorsConfig.java`
- Test: `buraka-back/review-service/src/test/java/org/ali/projects/buraka/review/review/ReviewServiceTest.java`
- Test: `buraka-back/review-service/src/test/java/org/ali/projects/buraka/review/review/ReviewControllerTest.java`

**Interfaces:**
- Consumes: Task 1 scaffold.
- Produces: REST endpoints consumed by frontends (Tasks 8–9):
  - `GET /api/reviews?productId=N` → `List<Review>` (`reviewId, userId, productId, title, rating, createdAt`)
  - `POST /api/reviews` (header `X-User-Id`, body `{"productId":N,"title":"...","rating":1-5}`) → 201 `Review`; 400 on validation failure
  - `DELETE /api/reviews/{id}` (header `X-User-Id`) → 204; 403 if not owner; 404 if missing
  - `ReviewService.create(long userId, CreateReviewRequest)` / `delete(long userId, long reviewId)` — Task 6 wires event publishing into these methods via `ReviewEventPublisher`.

- [ ] **Step 1: Write the failing service test**

`ReviewServiceTest.java`:
```java
package org.ali.projects.buraka.review.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    private ReviewService service;

    @BeforeEach
    void setUp() {
        service = new ReviewService(reviewRepository);
    }

    @Test
    void createPersistsReviewWithUserId() {
        CreateReviewRequest request = new CreateReviewRequest(10L, "Great", 5);
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Review saved = service.create(7L, request);

        assertThat(saved.getUserId()).isEqualTo(7L);
        assertThat(saved.getProductId()).isEqualTo(10L);
        assertThat(saved.getRating()).isEqualTo(5);
        assertThat(saved.getCreatedAt()).isNotNull();
        verify(reviewRepository).save(any(Review.class));
    }

    @Test
    void deleteRejectsOtherUsersReview() {
        Review review = new Review(7L, 10L, "Great", 5, LocalDateTime.now());
        review.setReviewId(1L);
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> service.delete(8L, 1L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        verify(reviewRepository, never()).delete(any(Review.class));
    }

    @Test
    void deleteRemovesOwnReview() {
        Review review = new Review(7L, 10L, "Great", 5, LocalDateTime.now());
        review.setReviewId(1L);
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(review));

        service.delete(7L, 1L);

        verify(reviewRepository).delete(review);
    }

    @Test
    void deleteMissingReviewReturns404() {
        when(reviewRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(7L, 99L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd buraka-back/review-service && ./gradlew test --tests '*ReviewServiceTest' -q`
Expected: FAIL — compilation error, classes do not exist.

- [ ] **Step 3: Write the review domain and service**

`Review.java`:
```java
package org.ali.projects.buraka.review.review;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "reviews")
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long reviewId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long productId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private int rating;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected Review() {
    }

    public Review(Long userId, Long productId, String title, int rating, LocalDateTime createdAt) {
        this.userId = userId;
        this.productId = productId;
        this.title = title;
        this.rating = rating;
        this.createdAt = createdAt;
    }

    public Long getReviewId() {
        return reviewId;
    }

    public void setReviewId(Long reviewId) {
        this.reviewId = reviewId;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getProductId() {
        return productId;
    }

    public String getTitle() {
        return title;
    }

    public int getRating() {
        return rating;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
```

`ReviewRepository.java`:
```java
package org.ali.projects.buraka.review.review;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Long> {

    List<Review> findByProductId(Long productId);
}
```

`CreateReviewRequest.java`:
```java
package org.ali.projects.buraka.review.review;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateReviewRequest(
        @NotNull Long productId,
        @NotBlank @Size(max = 200) String title,
        @Min(1) @Max(5) int rating) {
}
```

`ReviewService.java`:
```java
package org.ali.projects.buraka.review.review;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;

    public ReviewService(ReviewRepository reviewRepository) {
        this.reviewRepository = reviewRepository;
    }

    public List<Review> forProduct(Long productId) {
        return reviewRepository.findByProductId(productId);
    }

    public Review create(long userId, CreateReviewRequest request) {
        Review review = new Review(userId, request.productId(), request.title(),
                request.rating(), LocalDateTime.now());
        return reviewRepository.save(review);
    }

    public void delete(long userId, long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "review not found"));
        if (review.getUserId() != userId) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "not your review");
        }
        reviewRepository.delete(review);
    }
}
```

`ReviewController.java`:
```java
package org.ali.projects.buraka.review.review;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping
    public List<Review> forProduct(@RequestParam Long productId) {
        return reviewService.forProduct(productId);
    }

    @PostMapping
    public ResponseEntity<Review> create(@RequestHeader("X-User-Id") long userId,
                                         @Valid @RequestBody CreateReviewRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(reviewService.create(userId, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@RequestHeader("X-User-Id") long userId, @PathVariable long id) {
        reviewService.delete(userId, id);
        return ResponseEntity.noContent().build();
    }
}
```

`CorsConfig.java`:
```java
package org.ali.projects.buraka.review.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**").allowedOrigins("*").allowedMethods("*").allowedHeaders("*");
    }
}
```

- [ ] **Step 4: Run service test to verify it passes**

Run: `cd buraka-back/review-service && ./gradlew test --tests '*ReviewServiceTest' -q && echo PASS`
Expected: `PASS`

- [ ] **Step 5: Write the controller test**

`ReviewControllerTest.java`:
```java
package org.ali.projects.buraka.review.review;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReviewController.class)
class ReviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ReviewService reviewService;

    @Test
    void createReturns201() throws Exception {
        Review review = new Review(7L, 10L, "Great", 5, LocalDateTime.now());
        when(reviewService.create(eq(7L), any(CreateReviewRequest.class))).thenReturn(review);

        mockMvc.perform(post("/api/reviews")
                        .header("X-User-Id", "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":10,\"title\":\"Great\",\"rating\":5}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Great"))
                .andExpect(jsonPath("$.userId").value(7));
    }

    @Test
    void createRejectsRatingAbove5() throws Exception {
        mockMvc.perform(post("/api/reviews")
                        .header("X-User-Id", "7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":10,\"title\":\"Bad\",\"rating\":6}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createRejectsMissingUserHeader() throws Exception {
        mockMvc.perform(post("/api/reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":10,\"title\":\"Great\",\"rating\":5}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void forProductReturnsReviews() throws Exception {
        when(reviewService.forProduct(10L))
                .thenReturn(List.of(new Review(7L, 10L, "Great", 5, LocalDateTime.now())));

        mockMvc.perform(get("/api/reviews").param("productId", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Great"));
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/reviews/1").header("X-User-Id", "7"))
                .andExpect(status().isNoContent());
    }
}
```

- [ ] **Step 6: Run full review-service suite and commit**

Run: `cd buraka-back/review-service && ./gradlew test -q && echo PASS`
Expected: `PASS`

```bash
cd /Users/alialghanay/repo/buraka
git add buraka-back/review-service/src
git commit -m "feat(review): add review domain, REST API, validation, and ownership rules"
```

---

### Task 6: RabbitMQ Events — ReviewCreated/ReviewDeleted Drive Rating Aggregates

**Files:**
- Create: `buraka-back/review-service/src/main/java/org/ali/projects/buraka/shared/messaging/ReviewEvent.java`
- Create: `buraka-back/review-service/src/main/java/org/ali/projects/buraka/review/messaging/RabbitConfig.java`
- Create: `buraka-back/review-service/src/main/java/org/ali/projects/buraka/review/messaging/ReviewEventPublisher.java`
- Modify: `buraka-back/review-service/src/main/java/org/ali/projects/buraka/review/review/ReviewService.java` (full rewrite below)
- Create: `buraka-back/catalog-service/src/main/java/org/ali/projects/buraka/shared/messaging/ReviewEvent.java`
- Create: `buraka-back/catalog-service/src/main/java/org/ali/projects/buraka/catalog/messaging/RabbitConfig.java`
- Create: `buraka-back/catalog-service/src/main/java/org/ali/projects/buraka/catalog/messaging/ReviewEventListener.java`
- Modify: `buraka-back/catalog-service/src/main/java/org/ali/projects/buraka/catalog/product/ProductService.java` (add two methods, shown below)
- Test: `buraka-back/review-service/src/test/java/org/ali/projects/buraka/review/review/ReviewServiceTest.java` (full rewrite below)
- Test: `buraka-back/catalog-service/src/test/java/org/ali/projects/buraka/catalog/messaging/ReviewEventListenerTest.java`

**Interfaces:**
- Consumes: `ReviewService` (Task 5), `ProductService` (Task 4).
- Produces: the event contract from Global Constraints. `ReviewEvent` record: `ReviewEvent(Long reviewId, Long productId, int rating, Type type)`, `Type { CREATED, DELETED }`, package `org.ali.projects.buraka.shared.messaging` in BOTH services (identical fully-qualified name — required for RabbitMQ type headers to deserialize across services). `ProductService.applyReviewCreated(long productId, int rating)` / `applyReviewDeleted(long productId, int rating)` used only by the listener.

- [ ] **Step 1: Rewrite the review-service test to require event publishing (failing)**

Replace `ReviewServiceTest.java` entirely:
```java
package org.ali.projects.buraka.review.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Optional;

import org.ali.projects.buraka.review.messaging.ReviewEventPublisher;
import org.ali.projects.buraka.shared.messaging.ReviewEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock
    private ReviewRepository reviewRepository;

    @Mock
    private ReviewEventPublisher eventPublisher;

    private ReviewService service;

    @BeforeEach
    void setUp() {
        service = new ReviewService(reviewRepository, eventPublisher);
    }

    @Test
    void createPersistsAndPublishesCreatedEvent() {
        CreateReviewRequest request = new CreateReviewRequest(10L, "Great", 5);
        when(reviewRepository.save(any(Review.class))).thenAnswer(invocation -> {
            Review r = invocation.getArgument(0);
            r.setReviewId(1L);
            return r;
        });

        Review saved = service.create(7L, request);

        assertThat(saved.getUserId()).isEqualTo(7L);
        verify(eventPublisher).publish(argThat(event ->
                event.type() == ReviewEvent.Type.CREATED
                        && event.reviewId() == 1L
                        && event.productId() == 10L
                        && event.rating() == 5));
    }

    @Test
    void deletePublishesDeletedEvent() {
        Review review = new Review(7L, 10L, "Great", 5, LocalDateTime.now());
        review.setReviewId(1L);
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(review));

        service.delete(7L, 1L);

        verify(reviewRepository).delete(review);
        verify(eventPublisher).publish(argThat(event ->
                event.type() == ReviewEvent.Type.DELETED
                        && event.reviewId() == 1L
                        && event.productId() == 10L
                        && event.rating() == 5));
    }

    @Test
    void deleteRejectsOtherUsersReviewWithoutPublishing() {
        Review review = new Review(7L, 10L, "Great", 5, LocalDateTime.now());
        review.setReviewId(1L);
        when(reviewRepository.findById(1L)).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> service.delete(8L, 1L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);

        verify(reviewRepository, never()).delete(any(Review.class));
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void deleteMissingReviewReturns404() {
        when(reviewRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(7L, 99L))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        verifyNoInteractions(eventPublisher);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd buraka-back/review-service && ./gradlew test --tests '*ReviewServiceTest' -q`
Expected: FAIL — compilation error, `ReviewEventPublisher` and `ReviewEvent` do not exist.

- [ ] **Step 3: Write the shared event, Rabbit config, publisher, and rewire ReviewService**

`buraka-back/review-service/src/main/java/org/ali/projects/buraka/shared/messaging/ReviewEvent.java`:
```java
package org.ali.projects.buraka.shared.messaging;

public record ReviewEvent(Long reviewId, Long productId, int rating, Type type) {

    public enum Type {
        CREATED, DELETED
    }
}
```

`buraka-back/review-service/src/main/java/org/ali/projects/buraka/review/messaging/RabbitConfig.java`:
```java
package org.ali.projects.buraka.review.messaging;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "buraka.events";

    @Bean
    public TopicExchange burakaEvents() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public JacksonJsonMessageConverter messageConverter() {
        return new JacksonJsonMessageConverter("org.ali.projects.buraka.shared.messaging");
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }
}
```

`ReviewEventPublisher.java`:
```java
package org.ali.projects.buraka.review.messaging;

import org.ali.projects.buraka.shared.messaging.ReviewEvent;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class ReviewEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public ReviewEventPublisher(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(ReviewEvent event) {
        String routingKey = event.type() == ReviewEvent.Type.CREATED ? "review.created" : "review.deleted";
        rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, routingKey, event);
    }
}
```

Replace `ReviewService.java` entirely:
```java
package org.ali.projects.buraka.review.review;

import java.time.LocalDateTime;
import java.util.List;

import org.ali.projects.buraka.review.messaging.ReviewEventPublisher;
import org.ali.projects.buraka.shared.messaging.ReviewEvent;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReviewService {

    private final ReviewRepository reviewRepository;
    private final ReviewEventPublisher eventPublisher;

    public ReviewService(ReviewRepository reviewRepository, ReviewEventPublisher eventPublisher) {
        this.reviewRepository = reviewRepository;
        this.eventPublisher = eventPublisher;
    }

    public List<Review> forProduct(Long productId) {
        return reviewRepository.findByProductId(productId);
    }

    public Review create(long userId, CreateReviewRequest request) {
        Review review = new Review(userId, request.productId(), request.title(),
                request.rating(), LocalDateTime.now());
        Review saved = reviewRepository.save(review);
        eventPublisher.publish(new ReviewEvent(saved.getReviewId(), saved.getProductId(),
                saved.getRating(), ReviewEvent.Type.CREATED));
        return saved;
    }

    public void delete(long userId, long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "review not found"));
        if (review.getUserId() != userId) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "not your review");
        }
        reviewRepository.delete(review);
        eventPublisher.publish(new ReviewEvent(review.getReviewId(), review.getProductId(),
                review.getRating(), ReviewEvent.Type.DELETED));
    }
}
```

- [ ] **Step 4: Run review-service tests to verify they pass**

Run: `cd buraka-back/review-service && ./gradlew test -q && echo PASS`
Expected: `PASS`

- [ ] **Step 5: Write the failing catalog listener test**

`buraka-back/catalog-service/src/test/java/org/ali/projects/buraka/catalog/messaging/ReviewEventListenerTest.java`:
```java
package org.ali.projects.buraka.catalog.messaging;

import static org.mockito.Mockito.verify;

import org.ali.projects.buraka.catalog.product.ProductService;
import org.ali.projects.buraka.shared.messaging.ReviewEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReviewEventListenerTest {

    @Mock
    private ProductService productService;

    @InjectMocks
    private ReviewEventListener listener;

    @Test
    void createdEventAppliesRatingIncrement() {
        listener.onReviewEvent(new ReviewEvent(1L, 10L, 5, ReviewEvent.Type.CREATED));

        verify(productService).applyReviewCreated(10L, 5);
    }

    @Test
    void deletedEventAppliesRatingDecrement() {
        listener.onReviewEvent(new ReviewEvent(1L, 10L, 5, ReviewEvent.Type.DELETED));

        verify(productService).applyReviewDeleted(10L, 5);
    }
}
```

- [ ] **Step 6: Run test to verify it fails**

Run: `cd buraka-back/catalog-service && ./gradlew test --tests '*ReviewEventListenerTest' -q`
Expected: FAIL — compilation error, `ReviewEventListener` and `ReviewEvent` do not exist.

- [ ] **Step 7: Write the catalog-side event, Rabbit config, listener, and rating methods**

`buraka-back/catalog-service/src/main/java/org/ali/projects/buraka/shared/messaging/ReviewEvent.java`:
```java
package org.ali.projects.buraka.shared.messaging;

public record ReviewEvent(Long reviewId, Long productId, int rating, Type type) {

    public enum Type {
        CREATED, DELETED
    }
}
```

`buraka-back/catalog-service/src/main/java/org/ali/projects/buraka/catalog/messaging/RabbitConfig.java`:
```java
package org.ali.projects.buraka.catalog.messaging;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "buraka.events";
    public static final String QUEUE = "catalog.review-events";

    @Bean
    public TopicExchange burakaEvents() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue reviewEventsQueue() {
        return QueueBuilder.durable(QUEUE).build();
    }

    @Bean
    public Binding reviewEventsBinding(Queue reviewEventsQueue, TopicExchange burakaEvents) {
        return BindingBuilder.bind(reviewEventsQueue).to(burakaEvents).with("review.*");
    }

    @Bean
    public JacksonJsonMessageConverter messageConverter() {
        return new JacksonJsonMessageConverter("org.ali.projects.buraka.shared.messaging");
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }
}
```

`ReviewEventListener.java`:
```java
package org.ali.projects.buraka.catalog.messaging;

import org.ali.projects.buraka.catalog.product.ProductService;
import org.ali.projects.buraka.shared.messaging.ReviewEvent;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ReviewEventListener {

    private final ProductService productService;

    public ReviewEventListener(ProductService productService) {
        this.productService = productService;
    }

    @RabbitListener(queues = RabbitConfig.QUEUE)
    public void onReviewEvent(ReviewEvent event) {
        if (event.type() == ReviewEvent.Type.CREATED) {
            productService.applyReviewCreated(event.productId(), event.rating());
        } else {
            productService.applyReviewDeleted(event.productId(), event.rating());
        }
    }
}
```

Add these two methods to `ProductService.java` (immediately before the `evict` method):
```java
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
```

- [ ] **Step 8: Add rating-math tests to ProductServiceTest**

Append these tests inside `ProductServiceTest.java` (before the closing brace):
```java
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
```

- [ ] **Step 9: Run full catalog suite and commit both services**

Run: `cd buraka-back/catalog-service && ./gradlew test -q && echo PASS`
Expected: `PASS` (ProductServiceTest incl. rating math, ProductControllerTest, ReviewEventListenerTest)

```bash
cd /Users/alialghanay/repo/buraka
git add buraka-back/catalog-service/src buraka-back/review-service/src
git commit -m "feat(events): publish/consume review events to maintain rating aggregates via rabbitmq"
```

---

### Task 7: Docker Compose Infrastructure + End-to-End Smoke Test

**Files:**
- Create: `buraka-back/docker-compose.yml`
- Create: `buraka-back/docker-compose.scale.yml`
- Create: `buraka-back/nginx/lb.conf`
- Create: `buraka-back/catalog-service/Dockerfile`
- Create: `buraka-back/catalog-service/.dockerignore`
- Create: `buraka-back/smoke-test.sh` (executable)

**Interfaces:**
- Consumes: both services (Tasks 1–6).
- Produces: `docker compose -f buraka-back/docker-compose.yml up -d` brings up postgres×2, redis, rabbitmq. `docker-compose.scale.yml` + `nginx/lb.conf` provide the LB'd catalog used by Task 10. `smoke-test.sh` proves the event flow end-to-end (review → RabbitMQ → rating update).

- [ ] **Step 1: Write docker-compose.yml (infrastructure only)**

`buraka-back/docker-compose.yml`:
```yaml
services:
  postgres-catalog:
    image: postgres:16
    environment:
      POSTGRES_DB: buraka_catalog_db
      POSTGRES_USER: buraka
      POSTGRES_PASSWORD: buraka
    ports:
      - "5433:5432"

  postgres-review:
    image: postgres:16
    environment:
      POSTGRES_DB: buraka_review_db
      POSTGRES_USER: buraka
      POSTGRES_PASSWORD: buraka
    ports:
      - "5434:5432"

  redis:
    image: redis:7
    ports:
      - "6379:6379"

  rabbitmq:
    image: rabbitmq:3.13-management
    ports:
      - "5672:5672"
      - "15672:15672"
```

- [ ] **Step 2: Write the scale compose file, NGINX config, and catalog Dockerfile**

`buraka-back/docker-compose.scale.yml`:
```yaml
services:
  catalog-service:
    build: ./catalog-service
    environment:
      SERVER_PORT: "8080"
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres-catalog:5432/buraka_catalog_db
      SPRING_DATASOURCE_USERNAME: buraka
      SPRING_DATASOURCE_PASSWORD: buraka
      SPRING_DATA_REDIS_HOST: redis
      SPRING_RABBITMQ_HOST: rabbitmq
      CACHE_ENABLED: ${CACHE_ENABLED:-true}
    depends_on:
      - postgres-catalog
      - redis
      - rabbitmq

  catalog-lb:
    image: nginx:1.27
    ports:
      - "8080:80"
    volumes:
      - ./nginx/lb.conf:/etc/nginx/conf.d/default.conf:ro
    depends_on:
      - catalog-service
```

`buraka-back/nginx/lb.conf` (the `resolver` + variable `proxy_pass` makes NGINX re-resolve the service name via Docker DNS, so replicas added by `--scale` are picked up without restarting the LB):
```nginx
server {
    listen 80;

    resolver 127.0.0.11 valid=5s;

    location / {
        set $upstream "http://catalog-service:8080";
        proxy_pass $upstream$request_uri;
        proxy_set_header Host $host;
    }
}
```

`buraka-back/catalog-service/Dockerfile`:
```dockerfile
FROM eclipse-temurin:25-jdk AS build
WORKDIR /app
COPY . .
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

`buraka-back/catalog-service/.dockerignore`:
```
.gradle
build
```

- [ ] **Step 3: Write the smoke test script**

`buraka-back/smoke-test.sh`:
```bash
#!/usr/bin/env bash
# End-to-end check: seed data present, review flows to catalog rating via RabbitMQ.
# Prereqs: infra up (docker compose -f buraka-back/docker-compose.yml up -d),
#          catalog-service on :8081, review-service on :8082 (./gradlew bootRun in each).
set -euo pipefail

CATALOG=http://localhost:8081
REVIEW=http://localhost:8082

echo "1/5 catalog list endpoint"
curl -sf "$CATALOG/api/products?page=0&size=5" | grep -q '"product-1"' || { echo "FAIL: product-1 not in list"; exit 1; }

echo "2/5 catalog detail endpoint"
curl -sf "$CATALOG/api/products/product-1" | grep -q '"slug":"product-1"' || { echo "FAIL: detail"; exit 1; }

PRODUCT_ID=$(curl -sf "$CATALOG/api/products/product-1" | sed -n 's/.*"productId":\([0-9]*\).*/\1/p')
echo "3/5 posting review for productId=$PRODUCT_ID"
curl -sf -X POST "$REVIEW/api/reviews" \
  -H 'Content-Type: application/json' \
  -H 'X-User-Id: 1' \
  -d "{\"productId\":$PRODUCT_ID,\"title\":\"Smoke\",\"rating\":5}" > /dev/null || { echo "FAIL: create review"; exit 1; }

echo "4/5 review readable"
curl -sf "$REVIEW/api/reviews?productId=$PRODUCT_ID" | grep -q '"rating":5' || { echo "FAIL: read review"; exit 1; }

echo "5/5 waiting for rating propagation via rabbitmq (up to 15s)"
for i in $(seq 1 15); do
  COUNT=$(curl -sf "$CATALOG/api/products/product-1" | sed -n 's/.*"ratingsQuantity":\([0-9]*\).*/\1/p')
  if [ "$COUNT" -ge 1 ] 2>/dev/null; then
    echo "PASS: ratingsQuantity=$COUNT after ${i}s"
    exit 0
  fi
  sleep 1
done
echo "FAIL: rating did not propagate within 15s"
exit 1
```

Run: `chmod +x buraka-back/smoke-test.sh`

- [ ] **Step 4: Start infrastructure and run the smoke test**

```bash
cd /Users/alialghanay/repo/buraka
docker compose -f buraka-back/docker-compose.yml up -d
```
Expected: 4 containers started (`postgres-catalog`, `postgres-review`, `redis`, `rabbitmq`).

Then in two separate terminals (or background):
```bash
cd buraka-back/catalog-service && ./gradlew bootRun
cd buraka-back/review-service && ./gradlew bootRun
```

Then:
```bash
./buraka-back/smoke-test.sh
```
Expected: ends with `PASS: ratingsQuantity=1 after Ns` (N ≤ 15). If the catalog fails to start on database connection, wait for postgres health (`docker compose ... ps` → healthy/started) and retry — first postgres init takes a few seconds.

- [ ] **Step 5: Commit (leave services running or stop them; compose stays up for later tasks)**

```bash
cd /Users/alialghanay/repo/buraka
git add buraka-back/docker-compose.yml buraka-back/docker-compose.scale.yml buraka-back/nginx buraka-back/catalog-service/Dockerfile buraka-back/catalog-service/.dockerignore buraka-back/smoke-test.sh
git commit -m "feat(infra): add docker-compose infra, nginx lb, catalog dockerfile, and e2e smoke test"
```

---

### Task 8: React Web App

**Files:**
- Create: `buraka-fornt/web/` (Vite React scaffold via `npm create vite`)
- Modify: `buraka-fornt/web/vite.config.js` (proxy + vitest config, full content below)
- Modify: `buraka-fornt/web/src/App.jsx` (full content below)
- Create: `buraka-fornt/web/src/api.js`
- Create: `buraka-fornt/web/src/components/ProductList.jsx`
- Create: `buraka-fornt/web/src/components/ProductDetail.jsx`
- Create: `buraka-fornt/web/src/components/ReviewForm.jsx`
- Test: `buraka-fornt/web/src/api.test.js`

**Interfaces:**
- Consumes: catalog `:8081` (`/api/products`, `/api/products/{slug}`, `/api/categories`), review `:8082` (`/api/reviews`).
- Produces: working web app on `http://localhost:5173`. API client functions `fetchProducts`, `fetchProduct`, `fetchCategories`, `fetchReviews`, `createReview` — mirrored by the Flutter `BurakaApi` in Task 9.

- [ ] **Step 1: Scaffold the Vite app and install vitest**

```bash
cd /Users/alialghanay/repo/buraka/buraka-fornt
npm create vite@latest web -- --template react
cd web
npm install
npm install -D vitest
```
Expected: `buraka-fornt/web/` contains `package.json`, `vite.config.js`, `src/`.

Delete the demo content: `rm -f src/App.css src/index.css src/assets/react.svg && rm -rf public/vite.svg src/assets` — the components below carry minimal inline styling.

- [ ] **Step 2: Write the failing API client test**

`buraka-fornt/web/src/api.test.js`:
```js
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { fetchProducts, fetchProduct, createReview } from './api'

describe('api client', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('builds products URL with pagination and category', async () => {
    const json = vi.fn().mockResolvedValue({ content: [] })
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json }))

    await fetchProducts({ page: 2, size: 12, categoryId: 5 })

    expect(fetch).toHaveBeenCalledWith('/api/products?page=2&size=12&categoryId=5')
  })

  it('fetches product detail by slug', async () => {
    const json = vi.fn().mockResolvedValue({ slug: 'product-1' })
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json }))

    const product = await fetchProduct('product-1')

    expect(fetch).toHaveBeenCalledWith('/api/products/product-1')
    expect(product.slug).toBe('product-1')
  })

  it('throws when response is not ok', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 500 }))

    await expect(fetchProducts()).rejects.toThrow('500')
  })

  it('sends review with X-User-Id header and JSON body', async () => {
    const json = vi.fn().mockResolvedValue({ reviewId: 1 })
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: true, json }))

    await createReview({ productId: 10, title: 'Great', rating: 5, userId: 7 })

    const [url, options] = fetch.mock.calls[0]
    expect(url).toBe('/api/reviews')
    expect(options.headers['X-User-Id']).toBe('7')
    expect(JSON.parse(options.body)).toEqual({ productId: 10, title: 'Great', rating: 5 })
  })
})
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd buraka-fornt/web && npx vitest run`
Expected: FAIL — `Cannot find module './api'`.

- [ ] **Step 4: Write the API client and vite config**

`buraka-fornt/web/src/api.js`:
```js
export async function fetchProducts({ page = 0, size = 12, categoryId } = {}) {
  const params = new URLSearchParams({ page: String(page), size: String(size) })
  if (categoryId) params.set('categoryId', String(categoryId))
  const res = await fetch(`/api/products?${params}`)
  if (!res.ok) throw new Error(`products fetch failed: ${res.status}`)
  return res.json()
}

export async function fetchProduct(slug) {
  const res = await fetch(`/api/products/${slug}`)
  if (!res.ok) throw new Error(`product fetch failed: ${res.status}`)
  return res.json()
}

export async function fetchCategories() {
  const res = await fetch('/api/categories')
  if (!res.ok) throw new Error(`categories fetch failed: ${res.status}`)
  return res.json()
}

export async function fetchReviews(productId) {
  const res = await fetch(`/api/reviews?productId=${productId}`)
  if (!res.ok) throw new Error(`reviews fetch failed: ${res.status}`)
  return res.json()
}

export async function createReview({ productId, title, rating, userId = 1 }) {
  const res = await fetch('/api/reviews', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-User-Id': String(userId) },
    body: JSON.stringify({ productId, title, rating }),
  })
  if (!res.ok) throw new Error(`review create failed: ${res.status}`)
  return res.json()
}
```

Replace `buraka-fornt/web/vite.config.js`:
```js
/// <reference types="vitest/config" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api/products': 'http://localhost:8081',
      '/api/categories': 'http://localhost:8081',
      '/api/brands': 'http://localhost:8081',
      '/api/reviews': 'http://localhost:8082',
    },
  },
  test: {
    environment: 'node',
  },
})
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd buraka-fornt/web && npx vitest run`
Expected: 4 tests passed.

- [ ] **Step 6: Write the app components**

Replace `buraka-fornt/web/src/App.jsx`:
```jsx
import { useState } from 'react'
import ProductList from './components/ProductList'
import ProductDetail from './components/ProductDetail'

export default function App() {
  const [selectedSlug, setSelectedSlug] = useState(null)

  return (
    <div style={{ maxWidth: 960, margin: '0 auto', padding: 16, fontFamily: 'system-ui, sans-serif' }}>
      <h1 style={{ cursor: 'pointer' }} onClick={() => setSelectedSlug(null)}>
        Buraka Store
      </h1>
      {selectedSlug ? (
        <ProductDetail slug={selectedSlug} onBack={() => setSelectedSlug(null)} />
      ) : (
        <ProductList onSelect={setSelectedSlug} />
      )}
    </div>
  )
}
```

`buraka-fornt/web/src/components/ProductList.jsx`:
```jsx
import { useEffect, useState } from 'react'
import { fetchProducts, fetchCategories } from '../api'

export default function ProductList({ onSelect }) {
  const [page, setPage] = useState(0)
  const [categoryId, setCategoryId] = useState('')
  const [data, setData] = useState({ content: [], totalElements: 0 })
  const [categories, setCategories] = useState([])
  const [error, setError] = useState(null)

  useEffect(() => {
    fetchCategories().then(setCategories).catch((e) => setError(e.message))
  }, [])

  useEffect(() => {
    fetchProducts({ page, size: 12, categoryId: categoryId || undefined })
      .then(setData)
      .catch((e) => setError(e.message))
  }, [page, categoryId])

  if (error) return <p>Error: {error} — is catalog-service running on :8081?</p>

  return (
    <div>
      <select value={categoryId} onChange={(e) => { setCategoryId(e.target.value); setPage(0) }}>
        <option value="">All categories</option>
        {categories.map((c) => (
          <option key={c.categoryId} value={c.categoryId}>{c.title}</option>
        ))}
      </select>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 12, marginTop: 12 }}>
        {data.content.map((p) => (
          <div key={p.productId} onClick={() => onSelect(p.slug)}
               style={{ border: '1px solid #ddd', borderRadius: 8, padding: 12, cursor: 'pointer' }}>
            <div style={{ fontWeight: 600 }}>{p.title}</div>
            <div>★ {p.ratingsAverage.toFixed(1)} ({p.ratingsQuantity})</div>
            <div>{(p.price / 100).toFixed(2)} LYD</div>
          </div>
        ))}
      </div>
      <div style={{ marginTop: 12 }}>
        <button disabled={page === 0} onClick={() => setPage(page - 1)}>Prev</button>
        <span style={{ margin: '0 8px' }}>Page {page + 1}</span>
        <button disabled={data.content.length < 12} onClick={() => setPage(page + 1)}>Next</button>
      </div>
    </div>
  )
}
```

`buraka-fornt/web/src/components/ProductDetail.jsx`:
```jsx
import { useCallback, useEffect, useState } from 'react'
import { fetchProduct, fetchReviews } from '../api'
import ReviewForm from './ReviewForm'

export default function ProductDetail({ slug, onBack }) {
  const [product, setProduct] = useState(null)
  const [reviews, setReviews] = useState([])
  const [error, setError] = useState(null)

  const reload = useCallback(() => {
    fetchProduct(slug).then(setProduct).catch((e) => setError(e.message))
    if (product) {
      fetchReviews(product.productId).then(setReviews).catch(() => {})
    }
  }, [slug, product?.productId])

  useEffect(() => {
    fetchProduct(slug).then((p) => {
      setProduct(p)
      fetchReviews(p.productId).then(setReviews).catch(() => {})
    }).catch((e) => setError(e.message))
  }, [slug])

  if (error) return <p>Error: {error}</p>
  if (!product) return <p>Loading…</p>

  return (
    <div>
      <button onClick={onBack}>← Back</button>
      <h2>{product.title}</h2>
      <p>{product.description}</p>
      <p>Price: {(product.price / 100).toFixed(2)} LYD · In stock: {product.quantity}</p>
      <p>★ {product.ratingsAverage.toFixed(1)} ({product.ratingsQuantity} reviews)</p>

      <h3>Reviews</h3>
      {reviews.length === 0 && <p>No reviews yet.</p>}
      {reviews.map((r) => (
        <div key={r.reviewId} style={{ borderTop: '1px solid #eee', padding: '8px 0' }}>
          <strong>{r.title}</strong> — ★ {r.rating} <em>(user {r.userId})</em>
        </div>
      ))}

      <h3>Write a review</h3>
      <ReviewForm productId={product.productId} onSubmitted={reload} />
    </div>
  )
}
```

`buraka-fornt/web/src/components/ReviewForm.jsx`:
```jsx
import { useState } from 'react'
import { createReview } from '../api'

export default function ReviewForm({ productId, onSubmitted }) {
  const [title, setTitle] = useState('')
  const [rating, setRating] = useState(5)
  const [status, setStatus] = useState(null)

  async function submit(e) {
    e.preventDefault()
    setStatus('sending')
    try {
      await createReview({ productId, title, rating: Number(rating) })
      setTitle('')
      setStatus('published — rating aggregate updates in a moment')
      setTimeout(onSubmitted, 800) // allow the ReviewCreated event to propagate
    } catch (e2) {
      setStatus(`failed: ${e2.message}`)
    }
  }

  return (
    <form onSubmit={submit}>
      <input value={title} onChange={(e) => setTitle(e.target.value)}
             placeholder="Review title" required maxLength={200} style={{ width: 300 }} />
      <select value={rating} onChange={(e) => setRating(e.target.value)}>
        {[5, 4, 3, 2, 1].map((r) => <option key={r} value={r}>{r}★</option>)}
      </select>
      <button type="submit">Submit</button>
      {status && <span style={{ marginLeft: 8 }}>{status}</span>}
    </form>
  )
}
```

- [ ] **Step 7: Verify the app builds and manually smoke it**

Run: `cd buraka-fornt/web && npm run build`
Expected: `✓ built in …` with no errors.

Manual check (services from Task 7 still running): `npm run dev`, open `http://localhost:5173`, confirm: product grid renders, detail page opens, submitting a review updates the rating average within ~1 s.

- [ ] **Step 8: Commit**

```bash
cd /Users/alialghanay/repo/buraka
git add buraka-fornt/web
git commit -m "feat(web): add react storefront with product list, detail, and review form"
```

---

### Task 9: Flutter App

**Files:**
- Create: `buraka-fornt/mobile/` (Flutter scaffold via `flutter create`)
- Modify: `buraka-fornt/mobile/pubspec.yaml` (add `http` via `flutter pub add`)
- Modify: `buraka-fornt/mobile/lib/main.dart` (full content below)
- Create: `buraka-fornt/mobile/lib/models.dart`
- Create: `buraka-fornt/mobile/lib/api.dart`
- Create: `buraka-fornt/mobile/lib/product_list_page.dart`
- Create: `buraka-fornt/mobile/lib/product_detail_page.dart`
- Create: `buraka-fornt/mobile/test/models_test.dart`
- Delete: `buraka-fornt/mobile/test/widget_test.dart` (generated counter test)

**Interfaces:**
- Consumes: same REST contracts as Task 8 (catalog `:8081`, review `:8082`).
- Produces: Flutter app runnable with `flutter run -d macos` (or `-d chrome`). `BurakaApi` mirrors the React `api.js`: `fetchProducts`, `fetchProduct`, `fetchReviews`, `createReview`.

- [ ] **Step 1: Scaffold the Flutter app and add http**

```bash
cd /Users/alialghanay/repo/buraka/buraka-fornt
flutter create --org org.ali.projects --project-name buraka_mobile mobile
cd mobile
flutter pub add http
rm test/widget_test.dart
```

- [ ] **Step 2: Write the failing model test**

`buraka-fornt/mobile/test/models_test.dart`:
```dart
import 'package:buraka_mobile/models.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  test('Product.fromJson parses all fields', () {
    final product = Product.fromJson({
      'productId': 1,
      'slug': 'product-1',
      'title': 'Product 1',
      'description': 'desc',
      'quantity': 100,
      'sold': 0,
      'price': 1000,
      'imageCover': 'img',
      'ratingsAverage': 4.5,
      'ratingsQuantity': 2,
    });

    expect(product.slug, 'product-1');
    expect(product.ratingsAverage, 4.5);
    expect(product.ratingsQuantity, 2);
  });

  test('Product.fromJson tolerates integer ratingsAverage', () {
    final product = Product.fromJson({
      'productId': 1,
      'slug': 'product-1',
      'title': 'Product 1',
      'description': 'desc',
      'quantity': 100,
      'sold': 0,
      'price': 1000,
      'imageCover': 'img',
      'ratingsAverage': 0,
      'ratingsQuantity': 0,
    });

    expect(product.ratingsAverage, 0.0);
  });

  test('Review.fromJson parses all fields', () {
    final review = Review.fromJson({
      'reviewId': 1,
      'userId': 7,
      'productId': 10,
      'title': 'Great',
      'rating': 5,
      'createdAt': '2026-07-28T10:00:00',
    });

    expect(review.title, 'Great');
    expect(review.rating, 5);
  });
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `cd buraka-fornt/mobile && flutter test`
Expected: FAIL — `Error: Couldn't find the package 'buraka_mobile/models.dart'`.

- [ ] **Step 4: Write models and API client**

`buraka-fornt/mobile/lib/models.dart`:
```dart
class Product {
  final int productId;
  final String slug;
  final String title;
  final String description;
  final int quantity;
  final int sold;
  final int price;
  final String imageCover;
  final double ratingsAverage;
  final int ratingsQuantity;

  Product({
    required this.productId,
    required this.slug,
    required this.title,
    required this.description,
    required this.quantity,
    required this.sold,
    required this.price,
    required this.imageCover,
    required this.ratingsAverage,
    required this.ratingsQuantity,
  });

  factory Product.fromJson(Map<String, dynamic> json) => Product(
        productId: json['productId'] as int,
        slug: json['slug'] as String,
        title: json['title'] as String,
        description: json['description'] as String? ?? '',
        quantity: json['quantity'] as int,
        sold: json['sold'] as int,
        price: json['price'] as int,
        imageCover: json['imageCover'] as String? ?? '',
        ratingsAverage: (json['ratingsAverage'] as num).toDouble(),
        ratingsQuantity: json['ratingsQuantity'] as int,
      );
}

class Review {
  final int reviewId;
  final int userId;
  final int productId;
  final String title;
  final int rating;

  Review({
    required this.reviewId,
    required this.userId,
    required this.productId,
    required this.title,
    required this.rating,
  });

  factory Review.fromJson(Map<String, dynamic> json) => Review(
        reviewId: json['reviewId'] as int,
        userId: json['userId'] as int,
        productId: json['productId'] as int,
        title: json['title'] as String,
        rating: json['rating'] as int,
      );
}
```

`buraka-fornt/mobile/lib/api.dart`:
```dart
import 'dart:convert';

import 'package:http/http.dart' as http;

import 'models.dart';

class BurakaApi {
  final String catalogBase;
  final String reviewBase;

  BurakaApi({
    this.catalogBase = 'http://localhost:8081',
    this.reviewBase = 'http://localhost:8082',
  });

  Future<List<Product>> fetchProducts({int page = 0, int size = 20}) async {
    final res = await http.get(Uri.parse('$catalogBase/api/products?page=$page&size=$size'));
    if (res.statusCode != 200) {
      throw Exception('products fetch failed: ${res.statusCode}');
    }
    final body = jsonDecode(res.body) as Map<String, dynamic>;
    return (body['content'] as List)
        .map((e) => Product.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  Future<Product> fetchProduct(String slug) async {
    final res = await http.get(Uri.parse('$catalogBase/api/products/$slug'));
    if (res.statusCode != 200) {
      throw Exception('product fetch failed: ${res.statusCode}');
    }
    return Product.fromJson(jsonDecode(res.body) as Map<String, dynamic>);
  }

  Future<List<Review>> fetchReviews(int productId) async {
    final res = await http.get(Uri.parse('$reviewBase/api/reviews?productId=$productId'));
    if (res.statusCode != 200) {
      throw Exception('reviews fetch failed: ${res.statusCode}');
    }
    return (jsonDecode(res.body) as List)
        .map((e) => Review.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  Future<void> createReview({
    required int productId,
    required String title,
    required int rating,
    int userId = 1,
  }) async {
    final res = await http.post(
      Uri.parse('$reviewBase/api/reviews'),
      headers: {'Content-Type': 'application/json', 'X-User-Id': '$userId'},
      body: jsonEncode({'productId': productId, 'title': title, 'rating': rating}),
    );
    if (res.statusCode != 201) {
      throw Exception('review create failed: ${res.statusCode}');
    }
  }
}
```

- [ ] **Step 5: Run test to verify it passes**

Run: `cd buraka-fornt/mobile && flutter test`
Expected: `+3: All tests passed!`

- [ ] **Step 6: Write the app UI**

Replace `buraka-fornt/mobile/lib/main.dart`:
```dart
import 'package:flutter/material.dart';

import 'product_list_page.dart';

void main() {
  runApp(const BurakaApp());
}

class BurakaApp extends StatelessWidget {
  const BurakaApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Buraka Store',
      theme: ThemeData(colorSchemeSeed: Colors.teal, useMaterial3: true),
      home: const ProductListPage(),
    );
  }
}
```

`buraka-fornt/mobile/lib/product_list_page.dart`:
```dart
import 'package:flutter/material.dart';

import 'api.dart';
import 'models.dart';
import 'product_detail_page.dart';

class ProductListPage extends StatefulWidget {
  const ProductListPage({super.key});

  @override
  State<ProductListPage> createState() => _ProductListPageState();
}

class _ProductListPageState extends State<ProductListPage> {
  final BurakaApi api = BurakaApi();
  late Future<List<Product>> future = api.fetchProducts();

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Buraka Store')),
      body: FutureBuilder<List<Product>>(
        future: future,
        builder: (context, snapshot) {
          if (snapshot.hasError) {
            return Center(child: Text('Error: ${snapshot.error}\nIs catalog-service on :8081?'));
          }
          if (!snapshot.hasData) {
            return const Center(child: CircularProgressIndicator());
          }
          final products = snapshot.data!;
          return ListView.builder(
            itemCount: products.length,
            itemBuilder: (context, index) {
              final p = products[index];
              return ListTile(
                title: Text(p.title),
                subtitle: Text('${(p.price / 100).toStringAsFixed(2)} LYD'),
                trailing: Text('★ ${p.ratingsAverage.toStringAsFixed(1)} (${p.ratingsQuantity})'),
                onTap: () => Navigator.push(
                  context,
                  MaterialPageRoute(builder: (_) => ProductDetailPage(slug: p.slug, api: api)),
                ),
              );
            },
          );
        },
      ),
    );
  }
}
```

`buraka-fornt/mobile/lib/product_detail_page.dart`:
```dart
import 'package:flutter/material.dart';

import 'api.dart';
import 'models.dart';

class ProductDetailPage extends StatefulWidget {
  final String slug;
  final BurakaApi api;

  const ProductDetailPage({super.key, required this.slug, required this.api});

  @override
  State<ProductDetailPage> createState() => _ProductDetailPageState();
}

class _ProductDetailPageState extends State<ProductDetailPage> {
  Product? product;
  List<Review> reviews = [];
  String? error;

  final TextEditingController titleController = TextEditingController();
  int rating = 5;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    try {
      final p = await widget.api.fetchProduct(widget.slug);
      final r = await widget.api.fetchReviews(p.productId);
      setState(() {
        product = p;
        reviews = r;
        error = null;
      });
    } catch (e) {
      setState(() => error = e.toString());
    }
  }

  Future<void> _submitReview() async {
    if (product == null || titleController.text.trim().isEmpty) return;
    try {
      await widget.api.createReview(
        productId: product!.productId,
        title: titleController.text.trim(),
        rating: rating,
      );
      titleController.clear();
      // Allow the ReviewCreated event to propagate before reloading.
      await Future.delayed(const Duration(milliseconds: 800));
      await _load();
    } catch (e) {
      setState(() => error = e.toString());
    }
  }

  @override
  Widget build(BuildContext context) {
    final p = product;
    return Scaffold(
      appBar: AppBar(title: Text(p?.title ?? 'Product')),
      body: error != null
          ? Center(child: Text('Error: $error'))
          : p == null
              ? const Center(child: CircularProgressIndicator())
              : ListView(
                  padding: const EdgeInsets.all(16),
                  children: [
                    Text(p.title, style: Theme.of(context).textTheme.headlineSmall),
                    const SizedBox(height: 8),
                    Text(p.description),
                    const SizedBox(height: 8),
                    Text('Price: ${(p.price / 100).toStringAsFixed(2)} LYD · In stock: ${p.quantity}'),
                    Text('★ ${p.ratingsAverage.toStringAsFixed(1)} (${p.ratingsQuantity} reviews)'),
                    const Divider(height: 32),
                    Text('Reviews', style: Theme.of(context).textTheme.titleMedium),
                    if (reviews.isEmpty) const Text('No reviews yet.'),
                    for (final r in reviews)
                      ListTile(
                        dense: true,
                        title: Text(r.title),
                        subtitle: Text('user ${r.userId}'),
                        trailing: Text('★ ${r.rating}'),
                      ),
                    const Divider(height: 32),
                    Text('Write a review', style: Theme.of(context).textTheme.titleMedium),
                    TextField(
                      controller: titleController,
                      maxLength: 200,
                      decoration: const InputDecoration(labelText: 'Review title'),
                    ),
                    DropdownButton<int>(
                      value: rating,
                      items: [5, 4, 3, 2, 1]
                          .map((r) => DropdownMenuItem(value: r, child: Text('$r★')))
                          .toList(),
                      onChanged: (v) => setState(() => rating = v ?? 5),
                    ),
                    ElevatedButton(onPressed: _submitReview, child: const Text('Submit')),
                  ],
                ),
    );
  }
}
```

- [ ] **Step 7: Verify analyze + test + a manual run**

Run: `cd buraka-fornt/mobile && flutter analyze && flutter test`
Expected: `No issues found!` and `+3: All tests passed!`

Manual check (services from Task 7 running): `flutter run -d macos` — product list renders, detail opens, review submission updates the rating.

- [ ] **Step 8: Commit**

```bash
cd /Users/alialghanay/repo/buraka
git add buraka-fornt/mobile
git commit -m "feat(mobile): add flutter storefront with product list, detail, and review form"
```

---

### Task 10: k6 Scalability Experiments + AGENTS.md Update

**Files:**
- Create: `buraka-back/loadtest/catalog-read.js`
- Create: `buraka-back/loadtest/README.md`
- Create: `buraka-back/loadtest/results.md`
- Modify: `AGENTS.md` (append prototype section, content below)

**Interfaces:**
- Consumes: `docker-compose.yml` + `docker-compose.scale.yml` + `nginx/lb.conf` + `catalog-service/Dockerfile` (Task 7); seed slugs `product-1`…`product-200` (Task 2).
- Produces: three measured k6 runs recorded in `results.md`, validating NFR-PER-01 (cached p95 < 300 ms) and the scalability claim (throughput scales with replicas).

- [ ] **Step 1: Write the k6 script**

`buraka-back/loadtest/catalog-read.js`:
```js
import http from 'k6/http';
import { check, sleep } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://host.docker.internal:8080';

export const options = {
  scenarios: {
    catalog_load: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 50 },
        { duration: '1m', target: 150 },
        { duration: '30s', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
  },
};

export default function () {
  const roll = Math.random();
  if (roll < 0.7) {
    const id = 1 + Math.floor(Math.random() * 200);
    const res = http.get(`${BASE_URL}/api/products/product-${id}`);
    check(res, { 'detail 200': (r) => r.status === 200 });
  } else {
    const page = Math.floor(Math.random() * 10);
    const res = http.get(`${BASE_URL}/api/products?page=${page}&size=20`);
    check(res, { 'list 200': (r) => r.status === 200 });
  }
  sleep(0.1);
}
```

- [ ] **Step 2: Write the experiment guide and results template**

`buraka-back/loadtest/README.md`:
````markdown
# Buraka Scalability Experiments

Load-tests the catalog read path behind the NGINX load balancer, validating the architecture document's claims:

- **NFR-PER-01:** cached catalog reads p95 < 300 ms (Run 2)
- **NFR-SCA-01:** throughput scales horizontally with replicas (Run 3 vs Run 2)

All commands run from the repository root. k6 runs inside Docker (`grafana/k6`), so no local install is needed; `host.docker.internal` lets the k6 container reach the host-published NGINX port.

## Setup (once)

```bash
docker compose -f buraka-back/docker-compose.yml -f buraka-back/docker-compose.scale.yml build
```

The first build downloads Gradle and dependencies inside Docker — allow 10+ minutes.

## Run 1 — 1 replica, cache OFF (baseline, uncached)

```bash
CACHE_ENABLED=false docker compose -f buraka-back/docker-compose.yml -f buraka-back/docker-compose.scale.yml up -d --scale catalog-service=1
sleep 20   # allow seed data load
docker run --rm -i -e BASE_URL=http://host.docker.internal:8080 grafana/k6 run - < buraka-back/loadtest/catalog-read.js
docker compose -f buraka-back/docker-compose.yml -f buraka-back/docker-compose.scale.yml down
```

## Run 2 — 1 replica, cache ON (validates NFR-PER-01)

Same commands with `CACHE_ENABLED=true` and `--scale catalog-service=1`.

## Run 3 — 3 replicas, cache ON (validates NFR-SCA-01)

Same commands with `CACHE_ENABLED=true` and `--scale catalog-service=3`. The NGINX config re-resolves Docker DNS every 5 s, so all three replicas receive traffic without restarting the LB.

## What to record

From each k6 end-of-run summary, copy into `results.md`: `http_reqs` (total/s rate), `http_req_duration` avg/p(95)/p(99), `http_req_failed` rate.

## Expected outcomes

- Run 2 p(95) is dramatically lower than Run 1 and under the 300 ms cached target — the Redis cache-aside absorbs the hot product-detail traffic (70% of requests).
- Run 3 sustains a higher request rate than Run 2 at similar latency — horizontal scaling behind the LB adds capacity.
- Failure rate stays under 1% in all runs (k6 threshold fails the run otherwise).
````

`buraka-back/loadtest/results.md`:
```markdown
# Scalability Experiment Results

| Run | Replicas | Cache | Total reqs | Req rate (req/s) | p50 (ms) | p95 (ms) | p99 (ms) | Error rate |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| 1 | 1 | off | | | | | | |
| 2 | 1 | on | | | | | | |
| 3 | 3 | on | | | | | | |

## Notes

- Hardware: (fill in — machine, CPU, RAM)
- Date: (fill in)
- Does Run 2 meet NFR-PER-01 (p95 < 300 ms cached)? (fill in)
- Does Run 3 show horizontal scaling over Run 2? (fill in)
```

- [ ] **Step 3: Execute the three runs and record results**

Follow `buraka-back/loadtest/README.md` exactly: build once, then Runs 1–3, filling in `results.md` after each run. Use `docker stats` during Run 3 to confirm all three catalog containers receive CPU load.

Expected: `results.md` fully filled; Run 2 p95 markedly below Run 1; Run 3 request rate above Run 2. If Run 1 errors (>1% failures), reduce the k6 peak to 100 VUs in `catalog-read.js` and re-run all three for comparability — record the change in Notes.

- [ ] **Step 4: Update AGENTS.md with the prototype layout**

Append this section to `/Users/alialghanay/repo/buraka/AGENTS.md` (at the end of the file):

```markdown

## Prototype (added 2026-07-28)

A working prototype now exists alongside the architecture document:

- `buraka-back/catalog-service/` — Spring Boot 4.1 service (port 8081): products/categories/brands REST API, Redis cache-aside (`cache.enabled` toggle), consumes `review.*` RabbitMQ events to maintain rating aggregates. Database `buraka_catalog_db` (postgres on 5433).
- `buraka-back/review-service/` — Spring Boot 4.1 service (port 8082): review CRUD with `X-User-Id` header auth (prototype stand-in), publishes `review.created`/`review.deleted` events. Database `buraka_review_db` (postgres on 5434).
- `buraka-back/docker-compose.yml` — infrastructure: postgres×2, redis, rabbitmq. `docker-compose.scale.yml` + `nginx/lb.conf` — catalog behind NGINX LB on 8080 for scale tests.
- `buraka-back/smoke-test.sh` — end-to-end check (review → event → rating update).
- `buraka-back/loadtest/` — k6 scalability experiments (run via `grafana/k6` Docker image); results in `results.md`.
- `buraka-fornt/web/` — React (Vite) storefront on 5173 with dev proxies to both services.
- `buraka-fornt/mobile/` — Flutter storefront (`buraka_mobile`), runs on macOS/Chrome.

Prototype simplifications (deliberate): no api-gateway, no auth/order services, no Moamalat; CORS is `*`; users identified by `X-User-Id` header. The shared event contract `org.ali.projects.buraka.shared.messaging.ReviewEvent` is duplicated into both services with the identical fully-qualified name — this is required for RabbitMQ JSON type headers to resolve; keep both copies in sync.

Run backend tests: `cd buraka-back/catalog-service && ./gradlew test` (same for `review-service`). Frontend tests: `cd buraka-fornt/web && npx vitest run`; `cd buraka-fornt/mobile && flutter test`.
```

- [ ] **Step 5: Commit**

```bash
cd /Users/alialghanay/repo/buraka
git add buraka-back/loadtest AGENTS.md
git commit -m "feat(loadtest): add k6 scalability experiments, results, and agents.md prototype docs"
```

---

## Self-Review Notes

- **Spec coverage:** two services (Tasks 1–6) ✓; React + Flutter frontends (Tasks 8–9) ✓; scalability testing via k6 + compose scaling (Tasks 7, 10) ✓; event-driven showcase + smoke test (Tasks 6–7) ✓; cache toggle for cache on/off comparison (Task 4) ✓.
- **No placeholders:** every file is given in full, including tests, configs, Docker files, and shell scripts. `results.md` intentionally starts as an empty table — it is filled by executing Task 10 Step 3, not a plan placeholder.
- **Type consistency:** `ReviewEvent(reviewId, productId, rating, type)` and routing keys `review.created`/`review.deleted`/`review.*` identical on both sides; `ProductService` constructor `(ProductRepository, RedisTemplate, boolean)` consistent between Tasks 4 and 6; frontend API shapes match backend JSON (`Page.content`, `Review.userId`, etc.); k6 slugs `product-1…200` match the Task 2 seeder.
- **Known accepted trade-offs:** Page JSON from Spring (warning about unstable structure) acceptable for prototype; `results.md` requires manual run execution; Flutter/React review-submit flows use an 800 ms delay for event propagation (eventual consistency made visible).
