package org.example.outboxed;

import jakarta.persistence.*;

@Entity
@Table(name = "outbox")
public class OutboxMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    public OutboxMessage(String payload, String status) {
        this.payload = payload;
        this.status = status;
    }

    private String payload; // Данные для вызова (например, JSON с orderId)
    private String status;

    public String getPayload() {
        return payload;
    }

    public void setStatus(String status) {
        this.status = status;
    }
    // PENDING, PROCESSED, FAILED
}

