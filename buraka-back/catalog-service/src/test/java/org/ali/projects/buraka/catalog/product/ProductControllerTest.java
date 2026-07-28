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
