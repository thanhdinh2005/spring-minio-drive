# Dependency Injection (DI)

## Tầng 1 — What? Nó là gì?

**Dependency Injection (DI)** là kỹ thuật cụ thể để hiện thực hóa nguyên lý IoC đã học ở phần trước ([Inversion of Control (IoC)](ioc.md)).

* Nếu IoC là câu hỏi *"ai nắm quyền điều khiển việc tạo và nối object"*, thì DI trả lời câu hỏi hẹp hơn: *"dependency được cung cấp cho một object bằng cách nào?"*
* **Định nghĩa:** Một class không tự tạo ra (`new`) những object mà nó cần (gọi là dependency), mà những object đó được truyền từ bên ngoài vào — thông qua **constructor**, **setter**, hoặc **field**.

```java
@Service
public class OrderService {
    private final EmailSender emailSender; // đây là một dependency

    // Dependency được "tiêm" (injected) vào qua constructor
    public OrderService(EmailSender emailSender) {
        this.emailSender = emailSender;
    }
}
```

`OrderService` không biết `EmailSender` cụ thể là `SmtpEmailSender` hay implementation nào khác — nó chỉ biết interface. Ai đó (container) sẽ quyết định implementation nào được đưa vào.

Ba hình thức DI tồn tại: **Constructor Injection**, **Setter Injection**, **Field Injection** — sẽ phân tích kỹ ở Tầng 3.

---

## Tầng 2 — Why? Nó giải quyết vấn đề gì?

Ở phần IoC ([Chi tiết về IoC](ioc.md)), ta đã thấy vấn đề tổng quát: class tự `new` dependency $\rightarrow$ coupling chặt, khó test, khó quản lý. DI là câu trả lời kỹ thuật cụ thể cho vấn đề đó, nhưng bản thân việc chọn hình thức DI nào cũng giải quyết những vấn đề tinh tế hơn:

### Vấn đề 1: Immutability và tính an toàn
Nếu dependency có thể bị gán lại bất cứ lúc nào (như Field Injection cho phép), object của bạn không còn đảm bảo trạng thái ổn định trong suốt vòng đời. Constructor Injection giải quyết bằng cách cho phép khai báo `final` — dependency được gán đúng 1 lần, không đổi được.

### Vấn đề 2: Phát hiện lỗi thiếu dependency ở giai đoạn sớm nhất
Với Field Injection, nếu Spring vì lý do nào đó không tiêm được dependency, bạn chỉ biết khi gọi hàm và gặp `NullPointerException` — lúc runtime, có thể là khi ứng dụng đã chạy production được vài phút. Với Constructor Injection, nếu thiếu tham số, code không biên dịch được, hoặc container báo lỗi ngay khi khởi động — fail nhanh, fail sớm (*fail-fast*).

### Vấn đề 3: Testability thực sự (nhắc lại nhưng cụ thể hóa)
```java
// Field Injection: bạn KHÔNG THỂ làm thế này
OrderService service = new OrderService();
// service.emailSender vẫn đang null — vì @Autowired field chỉ hoạt động khi có Spring container

// Constructor Injection: dễ dàng
OrderService service = new OrderService(new FakeEmailSender());
// Test chạy độc lập, không cần khởi động Spring
```

### Vấn đề 4: "Cảm nhận" được thiết kế đang xấu đi (code smell detection)
Nếu một class cần constructor với 8 tham số, đó là tín hiệu rõ ràng: class đang làm quá nhiều việc (vi phạm Single Responsibility Principle), cần tách nhỏ. Field Injection che giấu tín hiệu này — bạn có thể thêm bao nhiêu `@Autowired private` tùy thích mà không cảm thấy "cồng kềnh", vì không có giới hạn tự nhiên nào nhắc nhở bạn.

---

## Tầng 3 — How? Cơ chế hoạt động thế nào?

### Ba hình thức DI

#### a) Constructor Injection
```java
@Service
public class OrderService {
    private final EmailSender emailSender;
    private final OrderRepository orderRepository;

    public OrderService(EmailSender emailSender, OrderRepository orderRepository) {
        this.emailSender = emailSender;
        this.orderRepository = orderRepository;
    }
}
```
* **Cơ chế:** Spring dùng Reflection để đọc constructor, thấy nó cần `EmailSender` và `OrderRepository`, tìm 2 Bean tương ứng trong container, gọi constructor với 2 tham số đó.
* *Lưu ý:* Nếu class chỉ có duy nhất 1 constructor, `@Autowired` là không bắt buộc — Spring tự hiểu đây là constructor để inject (từ Spring 4.3 trở đi).

#### b) Setter Injection
```java
@Service
public class OrderService {
    private EmailSender emailSender;

    @Autowired
    public void setEmailSender(EmailSender emailSender) {
        this.emailSender = emailSender;
    }
}
```
* **Cơ chế:** Container tạo object bằng constructor không tham số trước (`new OrderService()`), sau đó gọi setter để gán dependency. Cho phép dependency optional (có thể không set) và thay đổi sau khi tạo — nhưng đây cũng chính là điểm yếu (*mutable state*).

#### c) Field Injection
```java
@Service
public class OrderService {
    @Autowired
    private EmailSender emailSender;
}
```
* **Cơ chế:** Container tạo object bằng constructor không tham số, sau đó dùng Reflection để truy cập trực tiếp vào field (kể cả private) và gán giá trị — bỏ qua hoàn toàn constructor/setter thông thường. Đây là lý do bạn không thể tự `new` object này trong test và mong field có giá trị.

---

### Cơ chế Type Matching (khớp kiểu để tìm Bean phù hợp)
Khi container thấy `OrderService` cần một `EmailSender`, nó tìm trong danh sách Bean đã đăng ký:
1. **Bước 1:** Tìm tất cả Bean có `kiểu = EmailSender` (hoặc implement/extend từ `EmailSender`).
2. **Bước 2:**
  * Nếu tìm thấy đúng 1 $\rightarrow$ dùng luôn.
  * Nếu tìm thấy 0 $\rightarrow$ ném lỗi `NoSuchBeanDefinitionException`.
  * Nếu tìm thấy > 1 $\rightarrow$ ném lỗi `NoUniqueBeanDefinitionException`, TRỪ KHI có `@Primary` hoặc `@Qualifier` chỉ định rõ.

**Ví dụ xử lý khi có nhiều implementation:**
```java
@Component
@Primary // Bean mặc định khi không chỉ định rõ
public class SmtpEmailSender implements EmailSender { ... }

@Component
public class SendGridEmailSender implements EmailSender { ... }

@Service
public class OrderService {
    public OrderService(@Qualifier("sendGridEmailSender") EmailSender emailSender) {
        // Ép container chọn đúng SendGridEmailSender, bỏ qua @Primary
    }
}
```

---

### Vấn đề Circular Dependency (phụ thuộc vòng)
Nếu A cần B, và B cần A:
```java
@Service
public class A {
    public A(B b) { ... }
}

@Service
public class B {
    public B(A a) { ... } // Lỗi!
}
```
Với Constructor Injection, container không thể giải quyết được — vì để tạo A cần có B sẵn, nhưng để tạo B lại cần A sẵn $\rightarrow$ bế tắc vòng lặp vô hạn. Kết quả: `BeanCurrentlyInCreationException` khi khởi động ứng dụng.

> **Đánh giá kiến trúc:** Đây thực chất là một tín hiệu thiết kế xấu — Spring "trừng phạt" bạn bằng lỗi ngay lập tức thay vì cho phép circular dependency tồn tại ngầm (với Field Injection, Spring có thể xử lý qua cơ chế proxy phức tạp — nhưng đây lại là lý do NỮA để tránh Field Injection, vì nó che giấu vấn đề thiết kế đáng lẽ cần refactor).

---

## Tầng 4 — Where? Code/config nào của project sử dụng nó?

### 1. Toàn bộ tầng Controller — Service — Repository
```java
@RestController
public class OrderController {
    private final OrderService orderService;

    public OrderController(OrderService orderService) { // DI ở đây
        this.orderService = orderService;
    }
}

@Service
public class OrderService {
    private final OrderRepository orderRepository;
    private final EmailSender emailSender;

    public OrderService(OrderRepository orderRepository, EmailSender emailSender) { // và ở đây
        this.orderRepository = orderRepository;
        this.emailSender = emailSender;
    }
}
```

### 2. Khi cần nhiều implementation cùng lúc (ví dụ nhiều cổng thanh toán)
```java
public interface PaymentGateway {
    void pay(Order order);
}

@Component("momoGateway")
public class MomoPaymentGateway implements PaymentGateway { ... }

@Component("vnpayGateway")
public class VnpayPaymentGateway implements PaymentGateway { ... }

@Service
public class PaymentService {
    public PaymentService(
        @Qualifier("momoGateway") PaymentGateway momoGateway,
        @Qualifier("vnpayGateway") PaymentGateway vnpayGateway
    ) {
        // Có thể giữ cả 2, chọn dùng cái nào tùy logic runtime
    }
}
```

### 3. Test — nơi lợi ích của Constructor Injection thể hiện rõ nhất
```java
@Test
void testPlaceOrder_shouldSendConfirmationEmail() {
    EmailSender fakeEmailSender = mock(EmailSender.class);
    OrderRepository fakeRepo = mock(OrderRepository.class);

    OrderService service = new OrderService(fakeRepo, fakeEmailSender); // không cần Spring container
    service.placeOrder(new Order(...));

    verify(fakeEmailSender).send(any(), any());
}
```

### 4. `application.properties` gián tiếp ảnh hưởng đến DI
Không trực tiếp, nhưng khi bạn cấu hình `spring.datasource.url=...`, Spring Boot tự tạo Bean `DataSource` với cấu hình đó — Bean này sau đó được inject vào các Repository/Service cần kết nối DB, dù bạn không thấy dòng code `new DataSource(...)` nào.

---

## Tầng 5 — So what? Nó có ý nghĩa gì đối với kiến trúc/project?

1. **Chọn Constructor Injection = chọn kiến trúc dễ bảo trì lâu dài:** Đây không chỉ là "best practice" mang tính hình thức — nó ép buộc bạn thiết kế class theo hướng tường minh: mọi dependency bắt buộc phải có ngay khi tạo object, không có trạng thái "nửa vời" (object tồn tại nhưng thiếu dependency).
2. **Constructor dài = tín hiệu tái cấu trúc (refactoring signal) miễn phí:** Bạn không cần công cụ phân tích code phức tạp để biết class nào đang "phình to" — chỉ cần nhìn số tham số constructor. Đây là lợi ích kiến trúc tự nhiên mà Field Injection tước mất khỏi bạn.
3. **Testability quyết định tốc độ phát triển dài hạn:** Một codebase với DI đúng cách (constructor-based) cho phép viết unit test nhanh, cô lập, không cần khởi động Spring container (chạy trong mili-giây thay vì giây). Khi codebase lớn dần với hàng nghìn test, sự khác biệt này quyết định việc CI/CD chạy trong 2 phút hay 20 phút.
4. **Circular Dependency bị chặn = buộc bạn thiết kế đúng ngay từ đầu:** Việc Spring "cứng rắn" từ chối circular dependency với Constructor Injection không phải là hạn chế — đó là cơ chế bảo vệ kiến trúc. Nó buộc bạn phát hiện và sửa vấn đề thiết kế ngay tại thời điểm phát triển, thay vì để nó âm ỉ thành nợ kỹ thuật (*technical debt*).
