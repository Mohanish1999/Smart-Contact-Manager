package com.smart.Dao;

import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import com.smart.entity.MyOrder;

@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class MyOrderRepositoryTest {

    @Autowired
    private MyOrderRepository myOrderRepository;

    @Test
    void testFindByOrderId() {

        MyOrder order = new MyOrder();

        order.setOrderId("TEST123");
        order.setAmount("500");
        order.setReceipt("receipt123");
        order.setStatus("created");
        order.setPaymentId("payment123");

        myOrderRepository.save(order);

        MyOrder result = myOrderRepository.findByOrderId("TEST123");

        assertNotNull(result);
        assertEquals("TEST123", result.getOrderId());
    }
}