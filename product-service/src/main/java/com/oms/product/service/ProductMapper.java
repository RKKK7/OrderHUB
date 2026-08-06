package com.oms.product.service;

import com.oms.product.dto.ProductDTO;
import com.oms.product.model.Product;

public final class ProductMapper {

    private ProductMapper() {}

    /** Derive stock status from quantity and threshold */
    private static String stockStatus(Product p) {
        if (p.getStockQuantity() <= 0) return "OUT_OF_STOCK";
        if (p.getStockQuantity() <= p.getLowStockThreshold()) return "LOW_STOCK";
        return "IN_STOCK";
    }

    /** User-facing DTO — hides exact stock quantity and admin-only fields */
    public static ProductDTO toUserDTO(Product p) {
        if (p == null) return null;
        ProductDTO dto = new ProductDTO();
        dto.setId(p.getId());
        dto.setName(p.getName());
        dto.setDescription(p.getDescription());
        dto.setCategory(p.getCategory());
        dto.setPrice(p.getPrice());
        dto.setImageUrl(p.getImageUrl());
        dto.setStockStatus(stockStatus(p));
        // stockQuantity intentionally null — users don't see exact numbers
        dto.setCreatedAt(p.getCreatedAt() != null ? p.getCreatedAt().toString() : null);
        dto.setUpdatedAt(p.getUpdatedAt() != null ? p.getUpdatedAt().toString() : null);
        return dto;
    }

    /** Admin-facing DTO — includes exact stock, threshold, and active flag */
    public static ProductDTO toAdminDTO(Product p) {
        if (p == null) return null;
        ProductDTO dto = toUserDTO(p);
        dto.setStockQuantity(p.getStockQuantity());
        dto.setLowStockThreshold(p.getLowStockThreshold());
        dto.setActive(p.isActive());
        return dto;
    }
}
