package org.example.outboxed;

import jakarta.transaction.Transactional;
import org.example.Order;
import org.example.OrderRepository;
import org.example.OrderRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OutboxRepository outboxRepository;

    public OrderService(@Autowired OrderRepository orderRepository,
                        @Autowired OutboxRepository outboxRepository) {
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
    }

    @Transactional
    public void createOrder(OrderRequest request) {
        // 1. Сохраняем заказ
        Order order = orderRepository.save(new Order(request.getItems()));

        // 2. Вместо вызова HTTP-клиента пишем задачу в Outbox
        OutboxMessage message = new OutboxMessage(
                String.valueOf(order.getId()),
                "PENDING"
        );
        outboxRepository.save(message);

        // Теперь, если здесь случится RuntimeException,
        // откатится и заказ, и запись в Outbox. Курьер НЕ будет вызван.
    }
}

