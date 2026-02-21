package org.example;

import org.springframework.web.client.RestTemplate;

public class CourierHttpClient {
    private final RestTemplate restTemplate = new RestTemplate();

    public void bookCourier(Integer orderId) {
        restTemplate.postForEntity(
                "http://delivery-service/api/notify", //Оповещение внешнего сервиса (вызов курьера)
                orderId,
                Void.class);
    }
}
