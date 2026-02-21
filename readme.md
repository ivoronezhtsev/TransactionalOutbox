Классическая проблема «распределенной транзакции для бедных». Когда внешний вызов (RPC/HTTP) происходит внутри блока @Transactional, откат базы данных никак не влияет на то, что запрос уже улетел в сеть.
Вот пример на Java (Spring Boot), где заказ «пропадает», а курьер уже едет:
```
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
```

**Почему это происходит**:<br>
**Несогласованность**: БД умеет откатывать изменения (INSERT), но она не может «откатать» HTTP-запрос, который уже обработан другим сервером.<br>
**Длинные транзакции**: Пока courierClient ждет ответа от сети, транзакция в БД остается открытой, удерживая соединения и блокировки.<br>
**Порядок не спасает**: Даже если вынести вызов курьера в самый конец метода, исключение при коммите транзакции (например, ошибка констрейнта в БД в момент flush) все равно приведет к тому, что курьер вызван, а данных нет.<br>

**Как это решает Transactional Outbox:**

Вместо вызова courierClient напрямую, вы записываете событие CourierBookingEvent в специальную таблицу outbox в той же транзакции, что и заказ.
* Если транзакция падает — запись в outbox не появляется.
* Если проходит — отдельный процесс (Relay) прочитает запись и гарантированно вызовет курьера.<br>

**Шаг 1: Создание таблицы Outbox**

  Сначала добавьте сущность для хранения исходящих сообщений в вашу БД:
```
  @Entity
  @Table(name = "outbox")
  public class OutboxMessage {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;
  private String payload; // Данные для вызова (например, JSON с orderId)
  private String status;  // PENDING, PROCESSED, FAILED
  }
```
**Шаг 2: Изменение бизнес-логики**<br>

Теперь сервис сохраняет заказ и сообщение в Outbox в рамках одной атомарной транзакции. Если транзакция откатится, сообщение в Outbox просто не появится.<br>
```
@Service
public class OrderService {
    @Autowired private OrderRepository orderRepository;
    @Autowired private OutboxRepository outboxRepository;

    @Transactional
    public void createOrder(OrderRequest request) {
        // 1. Сохраняем заказ
        Order order = orderRepository.save(new Order(request.getItems()));

        // 2. Вместо вызова HTTP-клиента пишем задачу в Outbox
        OutboxMessage message = new OutboxMessage();
        message.setPayload(String.valueOf(order.getId()));
        message.setStatus("PENDING");
        outboxRepository.save(message);

        // Теперь, если здесь случится RuntimeException, 
        // откатится и заказ, и запись в Outbox. Курьер НЕ будет вызван.
    }
}
```
**Шаг 3: Обработчик Outbox (Message Relay)**<br>
Отдельный компонент (фоновый процесс) периодически проверяет таблицу и выполняет реальные сетевые вызовы.
```
@Component
public class OutboxProcessor {
@Autowired private OutboxRepository outboxRepository;
@Autowired private CourierHttpClient courierClient;

    @Scheduled(fixedDelay = 5000) // Опрос раз в 5 секунд
    @Transactional
    public void processOutbox() {
        List<OutboxMessage> messages = outboxRepository.findByStatus("PENDING");

        for (OutboxMessage msg : messages) {
            try {
                // Реальный сетевой вызов
                courierClient.bookCourier(Long.parseLong(msg.getPayload()));
                msg.setStatus("PROCESSED");
            } catch (Exception e) {
                msg.setStatus("FAILED");
                // Здесь можно реализовать логику повторов (Retry)
            }
            outboxRepository.save(msg);
        }
    }
}
```
**Ключевые преимущества:**<br>
* Атомарность: Либо заказ и задача для курьера сохраняются вместе, либо ничего.
* Гарантия доставки (At-least-once): Если внешний сервис курьеров временно недоступен, фоновый процесс будет пробовать отправить сообщение снова и снова.
* Производительность: Основная транзакция заказа завершается мгновенно, не дожидаясь ответа от сети. 
