package org.example.outboxed;

import jakarta.transaction.Transactional;
import org.example.CourierHttpClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class OutboxProcessor {
    @Autowired
    private OutboxRepository outboxRepository;
    @Autowired private CourierHttpClient courierClient;

    @Scheduled(fixedDelay = 5000) // Опрос раз в 5 секунд
    @Transactional
    public void processOutbox() {
        List<OutboxMessage> messages = outboxRepository.findByStatus("PENDING");

        for (OutboxMessage msg : messages) {
            try {
                // Реальный сетевой вызов
                courierClient.bookCourier(Integer.parseInt(msg.getPayload()));
                msg.setStatus("PROCESSED");
            } catch (Exception e) {
                msg.setStatus("FAILED");
                // Здесь можно реализовать логику повторов (Retry)
            }
            outboxRepository.save(msg);
        }
    }
}
