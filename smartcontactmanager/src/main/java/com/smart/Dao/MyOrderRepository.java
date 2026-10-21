package com.smart.Dao;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.smart.entity.MyOrder;

import jakarta.persistence.LockModeType;

public interface MyOrderRepository extends JpaRepository <MyOrder, Long>{
	public MyOrder findByOrderId(String orderId);

	/**
	 * Loads an order with a row lock so that two concurrent verification requests
	 * for the same order are processed one after the other (prevents double processing).
	 * Must be called inside a transaction.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select o from MyOrder o where o.orderId = :orderId")
	Optional<MyOrder> findByOrderIdForUpdate(@Param("orderId") String orderId);

	boolean existsByPaymentId(String paymentId);

	/** Keeps donation records when an account is deleted, but detaches them from the removed user. */
	@Modifying
	@Query("update MyOrder o set o.user = null where o.user.id = :userId")
	int detachUser(@Param("userId") int userId);
}
