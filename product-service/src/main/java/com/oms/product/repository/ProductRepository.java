package com.oms.product.repository;

import com.oms.product.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductRepository extends JpaRepository<Product, String>, JpaSpecificationExecutor<Product> {

    List<Product> findByActiveTrueOrderByNameAsc();

    List<Product> findByCategoryAndActiveTrueOrderByNameAsc(String category);

    @Query("SELECT DISTINCT p.category FROM Product p WHERE p.active = true ORDER BY p.category")
    List<String> findDistinctCategories();

    List<Product> findByActiveTrueAndStockQuantityLessThanEqualOrderByStockQuantityAsc(int threshold);

    List<Product> findByIdIn(List<String> ids);

    /**
     * Atomic conditional stock decrement — the core of oversell prevention.
     * Returns the number of rows affected (0 = insufficient stock or inactive product).
     * This single UPDATE is atomic at the Postgres level — no race condition possible.
     */
    @Modifying
    @Query("UPDATE Product p SET p.stockQuantity = p.stockQuantity - :qty, p.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE p.id = :id AND p.stockQuantity >= :qty AND p.active = true")
    int decrementStock(@Param("id") String id, @Param("qty") int qty);

    /**
     * Restore stock — used on order cancellation or compensation after failed order save.
     */
    @Modifying
    @Query("UPDATE Product p SET p.stockQuantity = p.stockQuantity + :qty, p.updatedAt = CURRENT_TIMESTAMP " +
           "WHERE p.id = :id")
    int incrementStock(@Param("id") String id, @Param("qty") int qty);

    /**
     * Low-stock query — products where stock is at or below their individual threshold.
     */
    @Query("SELECT p FROM Product p WHERE p.active = true AND p.stockQuantity <= p.lowStockThreshold ORDER BY p.stockQuantity ASC")
    List<Product> findLowStockProducts();
}
