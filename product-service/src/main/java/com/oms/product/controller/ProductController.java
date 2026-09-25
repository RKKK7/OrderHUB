package com.oms.product.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.oms.product.dto.ApiException;
import com.oms.product.dto.ProductDTO;
import com.oms.product.event.StockEventPublisher;
import com.oms.product.model.Product;
import com.oms.product.repository.ProductRepository;
import com.oms.product.service.ProductCacheService;
import com.oms.product.service.ProductMapper;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.*;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductRepository productRepo;
    private final ProductCacheService cache;
    private final StockEventPublisher stockEventPublisher;
    private final ObjectMapper mapper = new ObjectMapper();

    public ProductController(ProductRepository productRepo, ProductCacheService cache,
                            StockEventPublisher stockEventPublisher) {
        this.productRepo = productRepo;
        this.cache = cache;
        this.stockEventPublisher = stockEventPublisher;
    }

    private void requireAdmin(String role) {
        if (!"ADMIN".equals(role))
            throw new ApiException(403, "Admin access required");
    }

    private static String s(Map<String, Object> b, String k) {
        Object v = b.get(k);
        return v == null ? null : v.toString();
    }

    // ===================== PUBLIC ENDPOINTS =====================

    @GetMapping
    public Map<String, Object> listProducts(
            @RequestParam(defaultValue = "all") String category,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "name") String sort,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "12") int limit) {

        // If searching, use search cache; otherwise use list cache
        if (search != null && !search.isBlank()) {
            return searchProducts(search.trim().toLowerCase(), sort, page, limit);
        }

        // Check list cache
        String cached = cache.getList(category, sort, page);
        if (cached != null) {
            try { return mapper.readValue(cached, new TypeReference<>() {}); } catch (Exception ignored) {}
        }

        // Cache miss — query DB
        Specification<Product> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.isTrue(root.get("active")));
            if (!"all".equals(category)) ps.add(cb.equal(root.get("category"), category));
            return cb.and(ps.toArray(new Predicate[0]));
        };

        Sort sorting = switch (sort) {
            case "price_asc" -> Sort.by("price").ascending();
            case "price_desc" -> Sort.by("price").descending();
            default -> Sort.by("name").ascending();
        };

        List<Product> all = productRepo.findAll(spec, sorting);
        long total = all.size();
        int from = Math.max(0, (page - 1) * limit);
        int to = Math.min(all.size(), from + limit);
        List<ProductDTO> items = (from <= to ? all.subList(from, to) : List.<Product>of())
                .stream().map(ProductMapper::toUserDTO).toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("products", items);
        result.put("total", total);
        result.put("pages", (int) Math.ceil((double) total / limit));
        result.put("page", page);

        // Populate cache
        try { cache.putList(category, sort, page, mapper.writeValueAsString(result)); } catch (Exception ignored) {}

        return result;
    }

    private Map<String, Object> searchProducts(String query, String sort, int page, int limit) {
        // Check search cache
        String cached = cache.getSearch(query, sort, page);
        if (cached != null) {
            try { return mapper.readValue(cached, new TypeReference<>() {}); } catch (Exception ignored) {}
        }

        Specification<Product> spec = (root, q, cb) -> {
            String like = "%" + query + "%";
            return cb.and(
                    cb.isTrue(root.get("active")),
                    cb.or(
                            cb.like(cb.lower(root.get("name")), like),
                            cb.like(cb.lower(root.get("description")), like),
                            cb.like(cb.lower(root.get("category")), like)
                    )
            );
        };

        Sort sorting = switch (sort) {
            case "price_asc" -> Sort.by("price").ascending();
            case "price_desc" -> Sort.by("price").descending();
            default -> Sort.by("name").ascending();
        };

        List<Product> all = productRepo.findAll(spec, sorting);
        long total = all.size();
        int from = Math.max(0, (page - 1) * limit);
        int to = Math.min(all.size(), from + limit);
        List<ProductDTO> items = (from <= to ? all.subList(from, to) : List.<Product>of())
                .stream().map(ProductMapper::toUserDTO).toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("products", items);
        result.put("total", total);
        result.put("pages", (int) Math.ceil((double) total / limit));
        result.put("page", page);

        try { cache.putSearch(query, sort, page, mapper.writeValueAsString(result)); } catch (Exception ignored) {}

        return result;
    }

    @GetMapping("/categories")
    public List<String> getCategories() {
        String cached = cache.getCategories();
        if (cached != null) {
            try { return mapper.readValue(cached, new TypeReference<>() {}); } catch (Exception ignored) {}
        }

        List<String> categories = productRepo.findDistinctCategories();
        try { cache.putCategories(mapper.writeValueAsString(categories)); } catch (Exception ignored) {}
        return categories;
    }

    @GetMapping("/{id}")
    public ProductDTO getProduct(@PathVariable String id) {
        // Check cache
        ProductDTO cached = cache.getProduct(id);
        if (cached != null) return cached;

        Product p = productRepo.findById(id)
                .orElseThrow(() -> new ApiException(404, "Product not found"));
        if (!p.isActive()) throw new ApiException(404, "Product not found");

        ProductDTO dto = ProductMapper.toUserDTO(p);
        cache.putProduct(id, dto);
        return dto;
    }

    // ===================== ADMIN ENDPOINTS =====================

    @PostMapping
    public ProductDTO createProduct(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @RequestBody Map<String, Object> body) {
        requireAdmin(role);

        String name = s(body, "name"), category = s(body, "category");
        if (name == null || category == null || body.get("price") == null)
            throw new ApiException(400, "name, category, and price are required");

        Product p = new Product();
        p.setName(name);
        p.setCategory(category);
        p.setPrice(new BigDecimal(body.get("price").toString()));
        if (body.get("description") != null) p.setDescription(body.get("description").toString());
        if (body.get("imageUrl") != null) p.setImageUrl(body.get("imageUrl").toString());
        if (body.get("stockQuantity") instanceof Number n) p.setStockQuantity(n.intValue());
        if (body.get("lowStockThreshold") instanceof Number n) p.setLowStockThreshold(n.intValue());

        p = productRepo.save(p);
        cache.invalidateOnWrite(p.getId());
        return ProductMapper.toAdminDTO(p);
    }

    @PutMapping("/{id}")
    public ProductDTO updateProduct(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        requireAdmin(role);

        Product p = productRepo.findById(id)
                .orElseThrow(() -> new ApiException(404, "Product not found"));

        if (body.get("name") != null) p.setName(body.get("name").toString());
        if (body.get("description") != null) p.setDescription(body.get("description").toString());
        if (body.get("category") != null) p.setCategory(body.get("category").toString());
        if (body.get("price") != null) p.setPrice(new BigDecimal(body.get("price").toString()));
        if (body.get("imageUrl") != null) p.setImageUrl(body.get("imageUrl").toString());
        if (body.get("lowStockThreshold") instanceof Number n) p.setLowStockThreshold(n.intValue());

        p = productRepo.save(p);
        cache.invalidateOnWrite(id);
        return ProductMapper.toAdminDTO(p);
    }

    @PutMapping("/{id}/stock")
    public ProductDTO updateStock(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        requireAdmin(role);

        Product p = productRepo.findById(id)
                .orElseThrow(() -> new ApiException(404, "Product not found"));

        if (body.get("stockQuantity") == null)
            throw new ApiException(400, "stockQuantity is required");

        int qty = ((Number) body.get("stockQuantity")).intValue();
        if (qty < 0) throw new ApiException(400, "stockQuantity cannot be negative");

        int oldQty = p.getStockQuantity();
        p.setStockQuantity(qty);
        p = productRepo.save(p);
        cache.invalidateOnWrite(id);

        // Emit STOCK_UPDATED (quantityChanged is the signed delta from the manual edit)
        stockEventPublisher.publish("STOCK_UPDATED", p.getId(), p.getName(),
                qty - oldQty, qty, p.getLowStockThreshold());
        // If the admin set stock to/below threshold, also raise a low-stock alert
        if (qty <= p.getLowStockThreshold()) {
            stockEventPublisher.publish("LOW_STOCK_ALERT", p.getId(), p.getName(),
                    qty - oldQty, qty, p.getLowStockThreshold());
        }

        return ProductMapper.toAdminDTO(p);
    }

    @PutMapping("/{id}/status")
    public ProductDTO toggleStatus(
            @RequestHeader(value = "X-User-Role", required = false) String role,
            @PathVariable String id,
            @RequestBody Map<String, Object> body) {
        requireAdmin(role);

        Product p = productRepo.findById(id)
                .orElseThrow(() -> new ApiException(404, "Product not found"));

        if (body.get("active") instanceof Boolean active) {
            p.setActive(active);
        } else {
            throw new ApiException(400, "active (boolean) is required");
        }

        p = productRepo.save(p);
        cache.invalidateOnWrite(id);
        return ProductMapper.toAdminDTO(p);
    }

    @GetMapping("/low-stock")
    public List<ProductDTO> lowStock(
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        requireAdmin(role);

        // Never cached — admin needs real-time accuracy
        return productRepo.findLowStockProducts().stream()
                .map(ProductMapper::toAdminDTO).toList();
    }

    @GetMapping("/manage")
    public List<ProductDTO> manageList(
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        requireAdmin(role);
        // Returns ALL products (including inactive) with exact stock — admin only
        return productRepo.findAll().stream().map(ProductMapper::toAdminDTO).toList();
    }
}
