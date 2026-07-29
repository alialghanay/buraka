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
