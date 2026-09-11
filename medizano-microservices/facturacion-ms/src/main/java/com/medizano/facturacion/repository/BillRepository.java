package com.medizano.facturacion.repository;

import com.medizano.facturacion.entity.Bill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BillRepository extends JpaRepository<Bill, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Bill b WHERE b.id = :id")
    Optional<Bill> findByIdForUpdate(@Param("id") Long id);
    Optional<Bill> findByBillNumber(String billNumber);
    
    @Query("SELECT b FROM Bill b WHERE b.billDate BETWEEN :startDate AND :endDate")
    List<Bill> findBillsByDateRange(@Param("startDate") LocalDateTime startDate, 
                                     @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT b FROM Bill b WHERE b.cashierId = :cashierId AND b.billDate BETWEEN :startDate AND :endDate")
    List<Bill> findBillsByCashierAndDateRange(@Param("cashierId") Long cashierId,
                                                @Param("startDate") LocalDateTime startDate,
                                                @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT b FROM Bill b WHERE b.cancelled = false ORDER BY b.billDate DESC")
    List<Bill> findAllOrderByBillDateDesc();

    List<Bill> findByPaymentStatusAndCancelledFalseOrderByBillDateDesc(Bill.PaymentStatus paymentStatus);
}
