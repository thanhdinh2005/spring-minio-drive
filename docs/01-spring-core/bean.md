# Bean

## Tầng 1 — What? Nó là gì?

**Bean** là một object được IoC Container quản lý — nghĩa là container chịu trách nhiệm tạo ra nó,
quyết định vòng đời của nó (khi nào tạo, khi nào hủy), và cung cấp nó cho bất kỳ nơi nào cần thông qua DI.

Điểm mấu chốt để phân biệt: không phải mọi object trong ứng dụng Java của bạn đều là Bean.
Chỉ những object được container biết đến và quản lý mới là Bean.

```java
@Service
public class OrderService {
  // Đây LÀ Bean — Spring container tạo và quản lý instance này
}

public class OrderDto {
  // Đây KHÔNG PHẢI Bean — chỉ là object dữ liệu bạn tự new khi cần
  // OrderDto dto = new OrderDto();
}
```

Về bản chất kỹ thuật, mỗi Bean được mô tả bởi một `BeanDefinition` —
**bản thiết kế** chứa metadata: class nào, scope gì, dependency nào, khởi tạo bằng cách nào.
Container không lưu trực tiếp object — nó lưu `BeanDefinition`, rồi dùng đó để tạo ra instance thật khi cần,
và lưu instance đó vào một vùng nhớ nội bộ gọi là Bean cache (hoặc singleton registry).

Có 2 nhóm object bạn cần Spring biến thành Bean:

* Class do bạn viết → đánh dấu bằng `@Component/@Service/@Repository/@Controller`
* Class từ thư viện ngoài (bạn không sửa được code gốc để thêm annotation) →
khai báo tường minh qua `@Bean` trong một class `@Configuration`

---

## Tầng 2 — Why? Nó giải quyết vấn đề gì?

Ở phần IoC và DI, ta đã thấy: container cần tạo object và tiêm dependency.
Nhưng để làm được điều đó, container phải biết những gì cần quản lý, và quản lý theo quy tắc nào.
Khái niệm Bean giải quyết ba vấn đề cụ thể:

### Vấn đề 1: Container cần một "**danh sách chính thức**" để biết mình quản lý cái gì
Nếu không có khái niệm Bean rõ ràng, container không thể phân biệt "object nào
tôi tạo và chia sẻ" với "object nào người dùng tự tạo tùy ý"
(như DTO, Entity thông thường khi bạn new bằng tay). Bean chính là ranh giới đó.

### Vấn đề 2: Tránh lãng phí tài nguyên khi tạo object dùng chung
Nếu mỗi lần cần một `OrderService`, hệ thống lại tạo mới một instance
(và các dependency bên trong nó), sẽ rất lãng phí bộ nhớ và thời gian khởi tạo
— đặc biệt với object nặng (như kết nối DB pool). Cơ chế Bean (mặc định Singleton) đảm bảo:
tạo một lần, dùng lại nhiều lần, trừ khi bạn chủ động yêu cầu khác.

### Vấn đề 3: Cần quản lý object có vòng đời phức tạp hơn **tạo xong là xong**
Một số object cần logic khởi tạo bổ sung sau khi constructor chạy xong
(VD: mở kết nối, load cache ban đầu), hoặc cần dọn dẹp tài nguyên trước khi ứng dụng tắt (VD: đóng kết nối, flush log).
Nếu tự quản lý bằng tay, bạn phải nhớ gọi đúng thứ tự ở đúng chỗ trong toàn bộ ứng dụng — dễ sót, dễ sai.
Bean cung cấp các lifecycle hook chuẩn hóa để container tự gọi đúng lúc.

---

## Tầng 3 — How? Cơ chế hoạt động thế nào?

### Ba cách đăng ký một class thành Bean

#### Cách 1: Stereotype annotation

```java
@Component
public class SmtpEmailSender implements EmailSender {
  // ...
}
```

Container quét package (Component Scanning), thấy annotation này, tự tạo `BeanDefinition`.

#### Cách 2: `@Bean` trong class `@Configuration`

```java
@Configuration
public class AppConfig {
    @Bean
    public RestTemplate restTemplate() {
        RestTemplate template = new RestTemplate();
        template.setInterceptors(List.of(new LoggingInterceptor()));
        return template; // Object trả về được container quản lý như một Bean
    }
}
```

Dùng khi bạn cần gọi new theo cách đặc biệt (set thêm thuộc tính, gọi factory method của thư viện) mà annotation đơn thuần không làm được
— vì bạn không thể sửa code của RestTemplate để thêm `@Component` vào.

#### Cách 3: XML configuration (cách cũ, hầu như không dùng trong dự án mới)

Chỉ nên biết là nó tồn tại — Spring Boot hiện đại gần như luôn dùng annotation.

#### Vòng đời đầy đủ của một Bean (Bean Lifecycle)

1. Container đọc BeanDefinition
2. Container gọi constructor → tạo instance
3. Container tiêm dependency (Constructor/Setter/Field Injection)
4. Container gọi các Aware interface nếu Bean implement (VD: ApplicationContextAware)
5. Container gọi phương thức đánh dấu `@PostConstruct` (nếu có)
6. Bean SẴN SÀNG SỬ DỤNG ── (đây là giai đoạn Bean tồn tại lâu nhất, dùng xuyên suốt ứng dụng)
7. Khi ứng dụng tắt: Container gọi phương thức đánh dấu `@PreDestroy` (nếu có)
8. Bean bị hủy

Ví dụ minh họa 2 hook quan trọng nhất:
```java
@Component
public class CacheWarmupService {

  @PostConstruct
  public void init() {
    // Chạy NGAY SAU khi constructor xong và dependency đã được tiêm đầy đủ
    // Phù hợp để: load dữ liệu ban đầu vào cache, validate cấu hình, mở kết nối bổ sung
    System.out.println("Đang nạp dữ liệu cache ban đầu...");
  }

  @PreDestroy
  public void cleanup() {
    // Chạy TRƯỚC KHI ứng dụng tắt (graceful shutdown)
    // Phù hợp để: đóng kết nối, flush buffer, lưu trạng thái cuối cùng
    System.out.println("Đang dọn dẹp tài nguyên...");
  }
}
```

#### Vì sao cần `@PostConstruct` thay vì để logic trong constructor?
Tại thời điểm constructor đang chạy, dependency injection có thể chưa hoàn tất 100% trong một số trường hợp phức tạp
(VD: có Setter Injection xảy ra sau constructor). `@PostConstruct` đảm bảo chạy sau khi toàn bộ quá trình tiêm dependency đã xong
— an toàn hơn để viết logic phụ thuộc vào các field đã được inject.

#### Bean Scope — quyết định **bao nhiêu instance, sống bao lâu**

```java
@Component
// Không ghi @Scope = mặc định Singleton
public class OrderService {
  //...
}
```

|Scope|Ý nghĩa   |Khi nào dùng   |
|---|---|---|
|singleton (mặc định)   |Cả ứng dụng chỉ 1 instance duy nhất, tạo lúc khởi động, dùng chung mãi mãi   |Hầu hết Service/Repository — vì chúng thường stateless (không lưu trạng thái riêng theo từng lần gọi)   |
|prototype   |Mỗi lần được yêu cầu inject/lấy ra, tạo instance mới   |Khi Bean cần giữ trạng thái riêng biệt mỗi lần dùng (hiếm gặp trong CRUD API thông thường)   |
|request   |1 instance cho mỗi HTTP request (chỉ có trong ứng dụng web)   |Khi cần lưu dữ liệu riêng theo từng request, dùng chung giữa nhiều Bean trong cùng request đó   |
|session   |1 instance cho mỗi HTTP session   |Lưu trạng thái theo phiên đăng nhập người dùng   |

```java
@Component
@Scope("prototype")
public class ReportGenerator {
    // Mỗi lần cần tạo báo cáo mới, muốn có instance sạch, không dính state cũ
}
```
#### Cơ chế nội bộ: Singleton Bean được lưu ở đâu?

Container Spring có một `Map<String, Object>` nội bộ gọi là **Singleton Bean Registry**
— khóa là tên Bean (mặc định là tên class viết thường chữ đầu,
VD: `orderService`), giá trị là instance thực.
Mỗi lần có yêu cầu inject `OrderService`, container tra map này và trả về cùng một reference
— đây chính là lý do **Singleton** có nghĩa là chia sẻ cùng 1 instance trong bộ nhớ.

## Tầng 4 — Where? Code/config nào của project sử dụng nó?

1. Toàn bộ class tầng Controller/Service/Repository — mỗi class này là 1 Bean:
```java
@RestController // Bean
@Service        // Bean
@Repository     // Bean (hoặc interface, Spring Data JPA tự tạo Bean implementation)
```

2. Class cấu hình @Configuration khi cần tích hợp thư viện ngoài
```java
@Configuration
public class DatabaseConfig {

    @Bean
    public DataSource dataSource(
        @Value("${spring.datasource.url}") String url,
        @Value("${spring.datasource.username}") String username,
        @Value("${spring.datasource.password}") String password
    ) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername(username);
        ds.setPassword(password);
        return ds; // Bean này sau đó được inject vào các Repository cần kết nối DB
    }
}
```
(Trong thực tế, Spring Boot Auto-configuration đã tự làm việc này dựa trên `application.properties/application.yaml`
— nhưng hiểu cơ chế `@Bean` giúp bạn biết cách ghi đè hoặc tùy chỉnh khi cần.)

3. @PostConstruct trong Service cần khởi tạo dữ liệu ban đầu

```java
@Service
public class ProductCacheService {
    private final ProductRepository productRepository;
    private Map<Long, Product> cache;

    public ProductCacheService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @PostConstruct
    public void loadCache() {
        // An toàn vì productRepository chắc chắn đã được inject xong tại đây
        this.cache = productRepository.findAll().stream()
            .collect(Collectors.toMap(Product::getId, p -> p));
    }
}
```

4. `application.properties/application.yml`
```yaml
spring:
   datasource:
   url: jdbc:postgresql://localhost::5432/backend
   username: postgre
   password: secret
```

Những giá trị này không tự nó là Bean, nhưng điều khiển cách Spring Boot tự động tạo
các Bean hạ tầng (như `DataSource`, `EntityManagerFactory`) thông qua cơ chế Auto-configuration.

## Tầng 5 — So what? Nó có ý nghĩa gì đối với kiến trúc/project?

1. Bean là đơn vị **kiến trúc** nhỏ nhất mà Spring hiểu được
Toàn bộ tư duy thiết kế trong Spring xoay quanh câu hỏi:
"phần này nên là 1 Bean, hay chỉ là object thông thường (POJO) tôi tự `new`?"
— Entity, DTO thường không nên là Bean (vì chúng đại diện dữ liệu, được tạo mới liên tục theo từng request/record),
còn Service/Repository/Controller nên là Bean (vì chúng đại diện hành vi, dùng chung xuyên suốt ứng dụng).

2. Singleton mặc định đặt ra một ràng buộc thiết kế cực kỳ quan trọng: Bean nên Stateless
Vì hầu hết Bean là Singleton (dùng chung 1 instance cho mọi request, mọi user cùng lúc),
bạn tuyệt đối không nên lưu dữ liệu thay đổi theo từng request vào field của Bean:

```java
@Service
public class OrderService {
    private Order currentOrder; // NGUY HIỂM — nhiều user cùng lúc sẽ ghi đè lẫn nhau!

    public void process(Order order) {
        this.currentOrder = order; // Race condition khi có concurrent request
    }
}
```
Thay vào đó, dữ liệu theo từng request nên được truyền qua tham số phương thức, không lưu vào field.

3. Lifecycle hook (`@PostConstruct/@PreDestroy`) là nền tảng cho graceful startup/shutdown
Khi ứng dụng của bạn phát triển lớn hơn (VD: cần warm-up cache trước khi nhận request đầu tiên,
hoặc cần đóng kết nối message queue an toàn khi restart), hiểu cơ chế lifecycle giúp bạn tránh các lỗi khó debug như
"ứng dụng vừa start đã nhận request nhưng cache chưa kịp load".

4. Phân biệt rõ Bean vs POJO giúp tránh lạm dụng Spring
Người mới thường có xu hướng gắn `@Component` vào mọi thứ,
kể cả những class không cần container quản lý (VD: một class tiện ích thuần túy chỉ có static method, hoặc DTO).
Điều này làm tăng thời gian khởi động ứng dụng (container phải quét, tạo, quản lý nhiều Bean hơn mức cần thiết) và làm mất đi tính rõ ràng của kiến trúc — Bean nên dành cho object đại diện hành vi/dịch vụ dùng chung,
không phải mọi class trong project.

5. Hiểu Bean là điều kiện để đọc hiểu lỗi Spring thường gặp
Hầu hết lỗi phổ biến khi mới học Spring đều xoay quanh khái niệm Bean:
* `NoSuchBeanDefinitionException` (không tìm thấy Bean phù hợp)
* `NoUniqueBeanDefinitionException` (nhiều Bean cùng loại)
* `BeanCurrentlyInCreationException` (circular dependency).
Nắm chắc cơ chế Bean ở tầng này giúp bạn tự chẩn đoán được phần lớn lỗi khi thực hành.
