package org.example;


import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CourierHttpClient courierClient; // Внешний сервис

    @Transactional
    public void createOrder(OrderRequest request) {
        // 1. Сохраняем заказ в БД
        Order order = new Order(request.getItems());
        orderRepository.save(order);

        // 2. Внешний сетевой вызов (Side Effect)
        // Если этот вызов прошел успешно, курьер забронирован.
        courierClient.bookCourier(order.getId());

        // 3. Искусственная или непредвиденная ошибка
        if (true) {
            throw new RuntimeException("Упс! Ошибка после вызова курьера");
        }

        // Транзакция завершается здесь.
        // Из-за RuntimeException произойдет ROLLBACK.
        // Результат: Заказа в БД нет, но сервис курьеров уже получил команду "ЕХАТЬ".
    }
}
