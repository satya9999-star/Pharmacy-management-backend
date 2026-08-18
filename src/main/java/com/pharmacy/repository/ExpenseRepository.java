package com.pharmacy.repository;

import com.pharmacy.model.Expense;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface ExpenseRepository extends JpaRepository<Expense, Long> {
    @Query("select coalesce(sum(e.amount), 0) from Expense e where e.category = :category and e.expenseDate between :from and :to")
    BigDecimal sumByCategoryAndBetween(@Param("category") String category, @Param("from") LocalDate from, @Param("to") LocalDate to);
}
