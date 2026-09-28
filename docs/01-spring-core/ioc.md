# IoC (Inversion of Control)

## Tầng 1 — What? Nó là gì?

**IoC (Inversion of Control — Đảo ngược quyền điều khiển)** là một nguyên lý thiết kế phần mềm, không phải một công cụ hay một class cụ thể.

* **Định nghĩa cốt lõi:** Trong một chương trình thông thường, code của bạn chủ động điều khiển toàn bộ luồng chạy — bạn quyết định khi nào tạo object nào,
gọi hàm nào, theo thứ tự nào. IoC đảo ngược lại: quyền điều khiển đó được chuyển giao cho một thực thể bên ngoài (framework, container,... ở đây là Spring Framework), còn code của bạn chỉ khai báo *"tôi cần gì"*, *"tôi làm gì khi được gọi"*, và bị động chờ được gọi/được cấp phát.
* Trong hệ sinh thái Spring, thực thể đứng ra nắm quyền điều khiển đó gọi là **IoC Container** (cụ thể là `ApplicationContext`). Nó chịu trách nhiệm:
  * Tạo object (gọi là **Bean**)
  * Biết Bean nào cần Bean nào
  * Tự động lắp ráp (wire) chúng lại với nhau
  * Quản lý vòng đời (tạo lúc nào, hủy lúc nào)

> **Lưu ý quan trọng:** IoC là nguyên lý, còn [**Dependency Injection (DI)**](dependency-injection.md) là một kỹ thuật cụ thể để hiện thực hóa nguyên lý đó (Spring còn có thể hiện thực IoC qua các cách khác như `ApplicationContextAware`, nhưng DI là cách phổ biến và được khuyến nghị nhất). Đừng nhầm lẫn hai khái niệm này — IoC là *"cái đích"* (ai nắm quyền điều khiển), DI là *"con đường"* (cách dependency được cung cấp).

---

## Tầng 2 — Why? Nó giải quyết vấn đề gì?

Hãy xét vấn đề khi **không có IoC**:

```java
public class OrderService {
    private EmailSender emailSender = new SmtpEmailSender();
    private OrderRepository orderRepository = new PostgresOrderRepository(new DataSource(...));
    private InventoryService inventoryService = new InventoryService(
        new PostgresInventoryRepository(new DataSource(...))
    );
}
```

Ba vấn đề nghiêm trọng phát sinh:

1. **Coupling chặt (Tight Coupling):** `OrderService` biết chính xác implementation nào nó đang dùng (`SmtpEmailSender`, `PostgresOrderRepository`...). Muốn đổi implementation (VD: đổi DB, đổi nhà cung cấp email), bạn phải sửa code bên trong `OrderService` — vi phạm nguyên tắc Open/Closed (mở để mở rộng, đóng để sửa đổi).
2. **Không thể test độc lập:** Bạn không thể test logic của `OrderService` mà không kéo theo việc tạo kết nối DB thật, gửi email thật. Vì object phụ thuộc được `new` cứng ngay trong class, không có "khe hở" nào để chèn phiên bản giả (mock/fake) vào.
3. **Không ai quản lý tập trung dependency graph:** Khi hệ thống có hàng chục, hàng trăm class phụ thuộc chéo nhau, việc chính bạn phải nhớ thứ tự khởi tạo, chia sẻ instance nào dùng chung (để tránh tạo lãng phí), object nào cần huỷ đúng lúc... trở thành gánh nặng khổng lồ và cực kỳ dễ lỗi khi dự án lớn lên.

> **Bản chất gốc rễ:** Khi class tự quyết định và tự tạo ra thứ nó phụ thuộc, nó đang gánh hai trách nhiệm cùng lúc — **(a) logic nghiệp vụ của chính nó**, và **(b) việc quản lý vòng đời/lắp ráp dependency**. IoC tách trách nhiệm (b) ra khỏi class, giao cho container — class chỉ còn lo trách nhiệm (a).

---

## Tầng 3 — How? Cơ chế hoạt động thế nào?

### Bước 1: [Component Scanning](component-scanning.md) (quét và đăng ký)
Khi ứng dụng Spring Boot khởi động, container (`ApplicationContext`) quét toàn bộ package (thường bắt đầu từ package chứa class `@SpringBootApplication`) để tìm các class được đánh dấu:

```java
@Component // hoặc @Service, @Repository, @Controller/@RestController
public class SmtpEmailSender implements EmailSender { ... }
```

Mỗi class như vậy được đăng ký thành một **Bean Definition** — tức "bản thiết kế" cho biết: class này cần được tạo, kiểu dữ liệu là gì, scope thế nào (mặc định Singleton).

### Bước 2: Dependency Resolution (phân tích cây phụ thuộc)
Container đọc constructor (hoặc field/setter có `@Autowired`) của từng Bean để biết Bean này cần Bean nào khác:

```java
@Service
public class OrderService {
    private final EmailSender emailSender;

    public OrderService(EmailSender emailSender) { // Container thấy: OrderService cần 1 EmailSender
        this.emailSender = emailSender;
    }
}
```

Từ đó container dựng lên một **dependency graph** (đồ thị phụ thuộc) trong nội bộ — biết Bean nào phải tạo trước, Bean nào tạo sau.

### Bước 3: Instantiation & Wiring (khởi tạo và lắp ráp)
Container khởi tạo Bean theo đúng thứ tự topo của graph — Bean không có dependency được tạo trước, sau đó tiêm (inject) vào Bean cần nó:

1. Container tạo `SmtpEmailSender` (không cần dependency nào khác).
2. Container thấy `OrderService` cần `EmailSender`.
3. Container tìm trong danh sách Bean đã có: có `SmtpEmailSender` implement `EmailSender`.
4. Container gọi: `new OrderService(smtpEmailSenderInstance)`.
5. `OrderService` giờ đã sẵn sàng, được lưu vào container. (Đây cũng là Quy ước chia tách Service Layer sau này)

Toàn bộ quá trình này bạn không viết một dòng `new` nào — bạn chỉ khai báo qua constructor *"tôi cần gì"*, container tự lo phần còn lại.

### Bước 4: Lifecycle Management (quản lý vòng đời)
Với scope mặc định (Singleton), container tạo đúng 1 lần duy nhất cho mỗi Bean khi ứng dụng khởi động, lưu lại trong bộ nhớ nội bộ (Bean cache), và trả về cùng một instance cho mọi nơi yêu cầu, suốt vòng đời ứng dụng — cho đến khi ứng dụng tắt.

### Cơ chế nền: Reflection
Về mặt kỹ thuật, Spring dùng **Java Reflection API** để: đọc annotation trên class, đọc tham số constructor, và gọi constructor để tạo instance một cách "động" (không biết trước tên class lúc biên dịch) — đây là lý do container có thể tạo và nối object mà không cần bạn viết code `new` tường minh ở bất kỳ đâu.

---

## Tầng 4 — Where? Code/config nào của project sử dụng nó?

Trong project, IoC hiện diện ở khắp nơi:

### 1. Điểm khởi động container
```java
@SpringBootApplication
public class BackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(MyApplication.class, args); // dòng này khởi tạo ApplicationContext
    }
}
```
Chính lệnh `SpringApplication.run()` là nơi container được tạo ra và bắt đầu quét, đăng ký, khởi tạo toàn bộ Bean.

### 2. Mọi class tầng Controller / Service / Repository
```java
@RestController
public class AuthController {
    private final AuthService authService; // dependency được container tiêm

    public AuthController(AuthService authService) {
        this.authService = authService;
    }
}

@Service
public class AuthService {
    private final UserRepository userRepository; // lại tiếp tục được tiêm

    public AuthService(AuthRepository userRepository) {
        this.userRepository = userRepository;
    }
}

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    // Spring Data JPA tự động tạo implementation và đăng ký làm Bean — bạn còn không viết class thật!
}
```
Đây chính là dependency graph thực tế: `Controller → Service → Repository`, toàn bộ do container lắp ráp.

### 3. Class cấu hình (`@Configuration`)
Khi bạn cần tạo Bean từ thư viện bên ngoài (không thể gắn `@Component` vào code của họ), bạn khai báo tường minh:

```java
@Configuration
public class AppConfig {
    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate(); // Container quản lý object này như một Bean
    }
}
```

### 4. File cấu hình `application.properties` / `application.yml`
Không trực tiếp tạo Bean, nhưng cấu hình hành vi của các Bean có sẵn (VD: DataSource Bean tự động được Spring Boot tạo dựa trên các dòng `spring.datasource.url=...`).

---

## Tầng 5 — So what? Nó có ý nghĩa gì đối với kiến trúc/project?

1. **Testability trở thành khả thi thực sự:** Vì dependency được tiêm từ ngoài vào (qua constructor), bạn có thể test `OrderService` hoàn toàn cô lập:
   ```java
   OrderService service = new OrderService(mockEmailSender, mockOrderRepository);
   ```
   Không cần khởi động Spring container, không cần kết nối DB thật, không cần mạng — test chạy nhanh và đáng tin cậy.
2. **Kiến trúc tầng (Layered Architecture) trở nên tự nhiên:** Vì mỗi tầng (Controller/Service/Repository) chỉ phụ thuộc vào interface/abstraction của tầng dưới (không phụ thuộc implementation cụ thể), bạn có thể đổi implementation ở tầng dưới (VD: đổi từ JPA sang MongoDB Repository) mà không đụng vào tầng trên — đây chính là biểu hiện thực tế của **Dependency Inversion Principle** (chữ D trong SOLID).
3. **Khả năng mở rộng không phá vỡ code cũ:** Muốn thêm một implementation mới của `PaymentGateway` (VD: thêm Momo bên cạnh VNPay)? Chỉ cần viết class mới gắn `@Component`, dùng `@Qualifier` để chỉ định nơi cần dùng — không phải sửa `OrderService` đã có.
4. **Giảm gánh nặng "quản lý thủ công" khi hệ thống lớn lên:** Với 5 class, tự `new` và nối tay còn ổn. Với 200 class, việc này bất khả thi nếu không có container đứng ra tự động hóa — đây là lý do IoC gần như bắt buộc phải có trong bất kỳ ứng dụng enterprise nào ở quy mô vừa trở lên.
5. **Đánh đổi cần ý thức được:** IoC không miễn phí — nó thêm một lớp "ma thuật": object được tạo ra ở đâu, khi nào, không còn tường minh 100% khi đọc code (bạn phải hiểu cơ chế Spring mới truy vết được). Đây là lý do hiểu rõ cơ chế (Tầng 3) quan trọng hơn việc chỉ biết "dán annotation là chạy" — khi debug lỗi liên quan đến Bean (như `NoSuchBeanDefinitionException`, circular dependency...), bạn cần hiểu container đang làm gì phía sau để chẩn đoán đúng vấn đề.
