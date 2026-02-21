package org.example;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.util.ArrayList;
import java.util.List;

@Entity
public class Order {
    @Id
    private Integer id;
    private List<String> items;

    public Order(List<String> items) {
        this.items = new ArrayList<>(items);
    }

    public Integer getId() {
        return id;
    }
}
