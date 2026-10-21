package com.smart.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name="orders")
public class MyOrder {
	
	@Id
	@GeneratedValue(strategy = GenerationType.AUTO)
	private Long myorderId;
	private String orderId;
	private String amount;
	private String receipt;
	private String status;
	@ManyToOne
	private User user;
	private String paymentId;
	// ---- Added for the Razorpay flow (additive, all nullable so existing rows stay valid) ----
	private String currency;
	private Long amountPaise;
	private String failureReason;
	private LocalDateTime createdAt;
	private LocalDateTime updatedAt;
	private LocalDateTime paidAt;

	@PrePersist
	void onCreate() {
		LocalDateTime now = LocalDateTime.now();
		this.createdAt = now;
		this.updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		this.updatedAt = LocalDateTime.now();
	}
	
	
	
	public MyOrder() {
		super();
		// TODO Auto-generated constructor stub
	}

	public MyOrder(Long myorderId, String orderId, String amount, String receipt, String status, User user,
			String paymentId) {
		super();
		this.myorderId = myorderId;
		this.orderId = orderId;
		this.amount = amount;
		this.receipt = receipt;
		this.status = status;
		this.user = user;
		this.paymentId = paymentId;
	}

	public Long getMyorderId() {
		return myorderId;
	}

	public void setMyorderId(Long myorderId) {
		this.myorderId = myorderId;
	}

	public String getOrderId() {
		return orderId;
	}

	public void setOrderId(String orderId) {
		this.orderId = orderId;
	}

	public String getAmount() {
		return amount;
	}

	public void setAmount(String amount) {
		this.amount = amount;
	}

	public String getReceipt() {
		return receipt;
	}

	public void setReceipt(String receipt) {
		this.receipt = receipt;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public User getUser() {
		return user;
	}

	public void setUser(User user) {
		this.user = user;
	}

	public String getPaymentId() {
		return paymentId;
	}

	public void setPaymentId(String paymentId) {
		this.paymentId = paymentId;
	}

	public String getCurrency() {
		return currency;
	}

	public void setCurrency(String currency) {
		this.currency = currency;
	}

	public Long getAmountPaise() {
		return amountPaise;
	}

	public void setAmountPaise(Long amountPaise) {
		this.amountPaise = amountPaise;
	}

	public String getFailureReason() {
		return failureReason;
	}

	public void setFailureReason(String failureReason) {
		this.failureReason = failureReason;
	}

	public LocalDateTime getCreatedAt() {
		return createdAt;
	}

	public LocalDateTime getUpdatedAt() {
		return updatedAt;
	}

	public LocalDateTime getPaidAt() {
		return paidAt;
	}

	public void setPaidAt(LocalDateTime paidAt) {
		this.paidAt = paidAt;
	}

	@Override
	public String toString() {
		return "MyOrder [myorderId=" + myorderId + ", orderId=" + orderId + ", amount=" + amount
				+ ", currency=" + currency + ", status=" + status + ", paymentId=" + paymentId + "]";
	}
	
	
	

}
