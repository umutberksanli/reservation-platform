# reservation-platform

Kontenjanlı etkinlik rezervasyonu ve FIFO (ilk gelen ilk alır) bekleme listesi sunan bir REST API.

> Durum: **7. aşama – FIFO bekleme listesi, iptal ve otomatik terfi.** Aynı anda gelen istekler kapasiteyi aşamaz, sıradaki kimse atlanmaz.

## Teknolojiler

- Java 21 (JDK 21 veya üstüyle derlenir)
- Spring Boot 4.2 (Spring Web MVC, Spring Data JPA)
- MySQL 8+ (MySQL Connector/J)
- Flyway (sürümlü veritabanı migration'ları)
- Maven Wrapper (Maven kurmak gerekmez)

## Veritabanı

Uygulama kendi veritabanını ve yalnızca o veritabanına yetkili kendi kullanıcısını kullanır.
Bağlantı bilgileri ortam değişkenlerinden okunur; şifre hiçbir dosyaya yazılmaz, Git'e girmez.

| Ortam değişkeni | Varsayılan | Açıklama |
|---|---|---|
| `DB_URL` | `jdbc:mysql://localhost:3306/reservation_platform` | JDBC adresi |
| `DB_USERNAME` | `reservation_app` | Uygulama kullanıcısı |
| `DB_PASSWORD` | – (**zorunlu**) | Uygulama kullanıcısının şifresi |
| `DB_TEST_URL` | `jdbc:mysql://localhost:3306/reservation_platform_test` | Testlerin kullandığı ayrı veritabanı |

### Projeye özel yerel MySQL (önerilen, Windows)

[`scripts/dev-mysql.ps1`](scripts/dev-mysql.ps1), bilgisayarda kurulu MySQL programını kullanarak
**ayrı bir veri klasörü ve ayrı bir port (3307)** ile projeye özel ikinci bir MySQL sunucusu çalıştırır.
Ana MySQL sunucusuna (3306), verilerine ve root şifresine dokunmaz; yönetici izni gerektirmez.

```powershell
# İlk kurulum: veri klasörü + reservation_platform veritabanı + reservation_app kullanıcısı, sonra başlatır
powershell -ExecutionPolicy Bypass -File .\scripts\dev-mysql.ps1 init

# Bilgisayar yeniden başladıktan sonra
powershell -ExecutionPolicy Bypass -File .\scripts\dev-mysql.ps1 start

# Durum / durdurma
powershell -ExecutionPolicy Bypass -File .\scripts\dev-mysql.ps1 status
powershell -ExecutionPolicy Bypass -File .\scripts\dev-mysql.ps1 stop
```

`init`:
- Rastgele şifreler üretir ve yalnızca Windows **kullanıcı ortam değişkenlerine** kaydeder
  (`DB_PASSWORD`, `RESERVATION_MYSQL_ROOT_PASSWORD`); şifreler ekrana veya log'a yazılmaz.
- `reservation_platform` (geliştirme) ve `reservation_platform_test` (testler) veritabanlarını oluşturur.
- `DB_URL` ve `DB_TEST_URL` değişkenlerini port 3307'yi gösterecek şekilde ayarlar.
- Tekrar çalıştırılabilir: mevcut kurulumda eksik veritabanlarını ve yetkileri tamamlar.
- Veriler: `%USERPROFILE%\dev\local-mysql\reservation-platform\` (repo dışında).

> `init` sonrası **terminali / IntelliJ'i kapatıp yeniden aç**: yeni ortam değişkenlerini yalnızca yeni açılan programlar görür.

### Kendi MySQL sunucunu kullanmak istersen

Root ile bağlanıp şunu çalıştır, sonra `DB_PASSWORD` (ve gerekirse `DB_URL`) değişkenini ayarla:

```sql
CREATE DATABASE reservation_platform CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE reservation_platform_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'reservation_app'@'localhost' IDENTIFIED BY '<sifre>';
GRANT ALL PRIVILEGES ON reservation_platform.* TO 'reservation_app'@'localhost';
GRANT ALL PRIVILEGES ON reservation_platform_test.* TO 'reservation_app'@'localhost';
```

## Veritabanı şeması

Tabloları **yalnızca Flyway** oluşturur; Hibernate'in tablo oluşturması kapalıdır (`spring.jpa.hibernate.ddl-auto=none`).
Migration dosyaları `src/main/resources/db/migration/` altındadır ve uygulama her açıldığında otomatik çalışır.
Flyway uygulananları `flyway_schema_history` tablosuna yazar; bir migration ikinci kez uygulanmaz.

| Migration | İçerik |
|---|---|
| `V1__create_event_and_app_user.sql` | `event` ve `app_user` tabloları |
| `V2__create_reservation.sql` | `reservation` tablosu (foreign key'ler, durum kuralı, "tek aktif rezervasyon" kuralı) |
| `V3__add_waitlist_to_reservation.sql` | Bekleme listesi: `WAITLISTED` durumu, kalıcı `waitlist_number`, `promoted_at`, `cancelled_at` |

**Kararlar**

- **Uygulanmış migration değiştirilmez.** Şema değişiklikleri yeni dosyayla yapılır (`V2__...`, `V3__...`). Eski dosya değişirse Flyway checksum uyuşmazlığı verip açılışı durdurur.
- **`event`**: `name` (en fazla 200 karakter, boş olamaz), `starts_at` (tarih + saat, `DATETIME(6)`), `capacity`.
  Kapasite `CHECK (capacity > 0)` ile **veritabanında** da korunur; uygulamadaki bir hata bile 0 ya da negatif kapasiteli etkinlik yazamaz.
  Yaklaşan etkinlikleri listelemek için `starts_at` üzerinde indeks var.
- **`app_user`**: `email` (kimlik/giriş anahtarı, **benzersiz**), `full_name`. Tablonun adı `user` değil, çünkü `USER` MySQL'de ve birçok veritabanında anahtar kelime.
  Karşılaştırma ayarı (`utf8mb4_0900_ai_ci`) büyük/küçük harf duyarsız olduğu için `Ayse@x.com` ile `ayse@x.com` aynı kabul edilir.
  Şifre/kimlik doğrulama alanları bu aşamada **yok**; gerektiğinde ayrı bir migration ile eklenecek.
- **`reservation`**: `event_id` → `event`, `user_id` → `app_user` (foreign key; olmayan etkinlik/kullanıcıya rezervasyon yazılamaz),
  `status` (`CONFIRMED` / `CANCELLED`, `CHECK` ile korunur).
  **Aynı kullanıcı aynı etkinlikte yalnızca bir aktif rezervasyon** tutabilir. MySQL'de koşullu (partial) unique index olmadığı için
  hesaplanmış bir sütun kullanılır: `active_key` = aktifse `1`, değilse `NULL`; `UNIQUE (event_id, user_id, active_key)`.
  `NULL` değerler unique indekste birbiriyle çakışmadığından kural yalnızca aktif kayıtlara uygulanır; iptal edilmiş kayıtlar yeni rezervasyonu engellemez.
- **Bekleme listesi (V3)** ayrı bir tablo değil, aynı `reservation` tablosunda `status = 'WAITLISTED'` satırlarıdır.
  V3 ile "aktif" = `CONFIRMED` **veya** `WAITLISTED` oldu; böylece **tek** `UNIQUE (event_id, user_id, active_key)` indeksi hem
  "aynı kullanıcı hem onaylı hem bekleyen olamaz" hem de "sıraya iki kez giremez" kuralını veritabanında korur.
  (Ayrı bir bekleme tablosu olsaydı bu iki tablo arası kural ancak trigger ile korunabilirdi.)
- **Sıra numarası (`waitlist_number`)**: etkinlik içinde 1, 2, 3 … diye verilen **kalıcı** numara. `UNIQUE (waitlist_number, event_id)` ile tek anlamlıdır,
  `CHECK` ile pozitiftir ve bekleyen her kayıtta zorunludur. Terfi/iptal sonrası satırda kalır, bu yüzden bir numara asla tekrar kullanılmaz.
  Sıradaki kişi = bekleyenler içindeki en küçük numara; **sıra konumu** = kendinden küçük numaralı bekleyen sayısı + 1 (sorguda hesaplanır).
- **MySQL 9.7 tuzağı:** bu benzersizlik indeksi `(event_id, waitlist_number)` sırasıyla sonradan eklendiğinde MySQL onu foreign key indeksi olarak seçti ve
  `fk_reservation_event` kontrolünü **sessizce bıraktı** (olmayan etkinliğe kayıt eklenebildi). `SchemaConstraintsTest` bunu yakaladı;
  sütun sırası `(waitlist_number, event_id)` yapılarak çözüldü (iki sütunlu `UNIQUE`'te sıra anlamı değiştirmez).
- **Ortak alanlar:** `id` `BIGINT AUTO_INCREMENT`, `created_at` veritabanı tarafından otomatik doldurulur.
- **Kısıtlara isim verildi** (`chk_event_capacity_positive`, `uk_app_user_email` …), böylece hata mesajlarında hangi kuralın ihlal edildiği okunabilir.
- **Zaman:** `DATETIME` saat dilimi bilgisi tutmaz; bu yüzden tüm zamanlar **UTC** olarak saklanır
  (`spring.jpa.properties.hibernate.jdbc.time_zone=UTC`). API'de zamanlar ISO-8601 UTC biçimindedir: `2027-01-15T19:00:00Z`.

## Çalıştırma (Windows)

```powershell
.\mvnw.cmd spring-boot:run
```

Ardından tarayıcıda: <http://localhost:8080/api/health>

```json
{"status":"UP","app":"event-reservation"}
```

## Etkinlik API'si

| Metot | Adres | Başarılı cevap | Hata |
|---|---|---|---|
| `POST` | `/api/events` | `201 Created` + `Location: /api/events/{id}` | `400` geçersiz istek |
| `GET` | `/api/events` | `200 OK`, başlangıç zamanına göre sıralı liste | – |
| `GET` | `/api/events/{id}` | `200 OK` | `404` bulunamadı |

İstek kuralları (`CreateEventRequest`): `name` boş olamaz, en fazla 200 karakter; `startsAt` zorunlu (UTC); `capacity` pozitif tam sayı.
Hatalar [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) `application/problem+json` biçiminde döner.
API'de entity yerine ayrı DTO'lar (`CreateEventRequest`, `EventResponse`) kullanılır.

### Örnek istekler (PowerShell)

Uygulama çalışırken (`.\mvnw.cmd spring-boot:run`) ayrı bir PowerShell penceresinde:

```powershell
# Etkinlik oluştur
curl.exe -i -X POST http://localhost:8080/api/events `
  -H "Content-Type: application/json" `
  -d '{\"name\": \"Spring Boot Workshop\", \"startsAt\": \"2027-01-15T19:00:00Z\", \"capacity\": 30}'
```

```http
HTTP/1.1 201
Location: http://localhost:8080/api/events/1
Content-Type: application/json

{"id":1,"name":"Spring Boot Workshop","startsAt":"2027-01-15T19:00:00Z","capacity":30,"createdAt":"2026-11-22T19:51:45.961544Z"}
```

```powershell
# Listele
curl.exe http://localhost:8080/api/events

# Detay (id'yi Location başlığından al)
curl.exe http://localhost:8080/api/events/1

# Olmayan ID -> 404
curl.exe -i http://localhost:8080/api/events/999
```

```http
HTTP/1.1 404
Content-Type: application/problem+json

{"detail":"Event 999 was not found","status":404,"title":"Event not found","code":"EVENT_NOT_FOUND"}
```

Geçersiz istek (ör. boş ad, `capacity: 0`) `400 Bad Request` döner ve veritabanına hiçbir şey yazılmaz.

> `curl.exe` örneklerindeki `\"` kaçışları **Windows PowerShell 5.1** içindir. Git Bash'te tek tırnak içinde düz `"` kullan.

PowerShell'in kendi komutuyla da gönderilebilir:

```powershell
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/events -ContentType 'application/json' `
  -Body (@{ name = 'Java Meetup'; startsAt = '2027-04-10T17:30:00Z'; capacity = 50 } | ConvertTo-Json)
```

## Kullanıcı kaydı API'si

| Metot | Adres | Başarılı cevap | Hata |
|---|---|---|---|
| `POST` | `/api/users` | `201 Created` + `Location: /api/users/{id}` | `400` boş/geçersiz alan, `409` e-posta zaten kayıtlı |
| `GET` | `/api/users/{id}` | `200 OK` | `404` bulunamadı (`code: USER_NOT_FOUND`) |

İstek kuralları (`RegisterUserRequest`): `email` geçerli bir e-posta, en fazla 254 karakter; `fullName` boş olamaz, en fazla 100 karakter.
Baş/son boşluklar doğrulamadan önce kırpılır. E-posta **küçük harfe çevrilerek** saklanır.

**E-posta benzersizliği iki katmanda korunur:**

1. **Java ön kontrolü** (`existsByEmail`): kayıtlı e-postayı veritabanına yazmaya çalışmadan yakalar.
2. **Veritabanı kısıtı** (V1'deki `uk_app_user_email`): iki istek aynı anda gelip ikisi de 1. adımdan geçerse
   MySQL ikinci kaydı reddeder; servis bu hatayı yakalayıp yine `409` döndürür.
   Sütunun karşılaştırma ayarı (`utf8mb4_0900_ai_ci`) büyük/küçük harf duyarsız olduğu için
   `AYSE@x.com` ile `ayse@x.com` veritabanında da aynı kabul edilir.

Kayıt cevabındaki `Location` adresi `GET /api/users/{id}` ile açılabilir:

```powershell
curl.exe http://localhost:8080/api/users/1
```

### Örnek istekler (PowerShell)

```powershell
# Kayıt
curl.exe -i -X POST http://localhost:8080/api/users `
  -H "Content-Type: application/json" `
  -d '{\"email\": \"Ayse.Yilmaz@Example.com\", \"fullName\": \"Ayse Yilmaz\"}'
```

```http
HTTP/1.1 201
Location: http://localhost:8080/api/users/1
Content-Type: application/json

{"id":1,"email":"ayse.yilmaz@example.com","fullName":"Ayse Yilmaz","createdAt":"2026-11-22T20:01:06.356721Z"}
```

```powershell
# Aynı e-posta, farklı büyük/küçük harf -> 409
curl.exe -i -X POST http://localhost:8080/api/users `
  -H "Content-Type: application/json" `
  -d '{\"email\": \"AYSE.YILMAZ@example.com\", \"fullName\": \"Baska Biri\"}'
```

```http
HTTP/1.1 409
Content-Type: application/problem+json

{"detail":"A user with this email address is already registered","status":409,"title":"Email already registered"}
```

```powershell
# Geçersiz alanlar -> 400
curl.exe -i -X POST http://localhost:8080/api/users `
  -H "Content-Type: application/json" `
  -d '{\"email\": \"not-an-email\", \"fullName\": \"\"}'
```

```http
HTTP/1.1 400
Content-Type: application/problem+json

{"detail":"Invalid request content.","status":400,"title":"Bad Request"}
```

## Rezervasyon API'si

| Metot | Adres | Ne yapar |
|---|---|---|
| `POST` | `/api/events/{eventId}/reservations` (gövde: `{"userId": 1}`) | Yer ayırır ya da bekleme listesine alır |
| `GET` | `/api/reservations/{id}` | Kaydın durumu ve (bekliyorsa) sıra konumu |
| `GET` | `/api/events/{eventId}/users/{userId}/reservation` | Kullanıcının o etkinlikteki **aktif** kaydı: durum ve sıra konumu |
| `POST` | `/api/reservations/{id}/cancel` | Onaylı ya da bekleyen kaydı iptal eder; boşalan yeri sıradakine verir |
| `GET` | `/api/users/{userId}/reservations?status=&page=0&size=20` | Kullanıcının tüm kayıtları (en yeni önce), etkinlik özeti ve sıra konumuyla; `status` = `CONFIRMED` / `WAITLISTED` / `CANCELLED`, `size` 1–100 |

### Yanıt kodları

| İstek | Durum | HTTP | `code` / gövde |
|---|---|---|---|
| Rezervasyon | Yer var → onaylandı | **`201 Created`** + `Location` | `status: CONFIRMED` |
| Rezervasyon | Etkinlik dolu (ya da sırada bekleyen var) → bekleme listesine alındı | **`202 Accepted`** + `Location` | `status: WAITLISTED`, `waitlistNumber`, `waitlistPosition` |
| Rezervasyon | Kullanıcının bu etkinlikte zaten onaylı **ya da** bekleyen kaydı var | `409` | `ALREADY_RESERVED` + `currentStatus` |
| Rezervasyon | Etkinlik / kullanıcı yok | `404` | `EVENT_NOT_FOUND` / `USER_NOT_FOUND` |
| Rezervasyon | Gövde geçersiz (`userId` yok, ≤ 0, sayı değil, JSON bozuk) | `400` | – |
| İptal | İptal edildi | `200 OK` | `alreadyCancelled: false`, `promoted: [terfi eden kayıt]` (yoksa `[]`) |
| İptal | **Tekrar iptal** (kayıt zaten iptal) | `200 OK` | `alreadyCancelled: true`, `promoted: []` — **hiçbir şey değişmez** |
| İptal / GET | Rezervasyon yok | `404` | `RESERVATION_NOT_FOUND` |
| Durum sorgusu | Kullanıcının bu etkinlikte onaylı/bekleyen kaydı yok | `404` | `NO_ACTIVE_RESERVATION` |

`202 Accepted` seçildi çünkü istek **kabul edildi ama henüz yerine getirilmedi**: kullanıcı yer almadı, sıraya girdi.
İstemci 201 ile 202'yi ayırarak "yer aldım" ile "bekliyorum"u gövdeyi okumadan da anlayabilir. Hatalar `application/problem+json` döner.

**Tekrar iptal neden 200?** İptal **idempotent**tir: ağ hatası yüzünden tekrar gönderilen bir iptal isteği hata almamalı.
Ama ikinci istek **kesinlikle ikinci bir terfiye yol açmaz**; `alreadyCancelled: true` ile ilk isteğin zaten iş gördüğünü söyler.
Aynı anda gelen 10 iptalden yalnızca biri gerçekten iptal eder (bkz. testler).

**Bekleyen kayıt da iptal edilebilir:** sıradan çıkar, kimse terfi etmez, arkasındakilerin konumu bir öne kayar (sıra **numaraları** değişmez).
İptal eden kullanıcı yeniden rezervasyon yapabilir; etkinlik doluysa sıranın **sonuna** yeni bir numarayla girer.

### Tasarım kararı: aynı anda gelen istekler kapasiteyi nasıl aşamaz?

**Sorun.** "Say → kontrol et → yaz" adımları kilitsiz çalışırsa, aynı anda gelen istekler aynı anda "0 rezervasyon var" görür ve hepsi yazar.
Bu proje ilk önce bilerek böyle yazıldı; `ReservationConcurrencyTest` kapasitesi **1** olan etkinliğe **20 eşzamanlı** HTTP isteği gönderdiğinde
5 turda sırasıyla **5, 2, 10, 6, 10** rezervasyon oluştu.

**Çözüm** (`ReservationService.create`), tek bir veritabanı transaction'ı içinde:

1. **Önce etkinlik satırı kilitlenir:** `SELECT ... FROM event WHERE id = ? FOR UPDATE` (JPA `PESSIMISTIC_WRITE`).
   Aynı etkinliğe gelen diğer istekler bu satırda, ilk transaction commit/rollback olana kadar **sırayla bekler**.
   Farklı etkinliklere gelen istekler birbirini beklemez.
2. **Transaction `READ COMMITTED` seviyesinde çalışır.** Kilit alındıktan sonraki sayım, öndeki isteklerin commit ettiği rezervasyonları görür.
   MySQL'in varsayılanı `REPEATABLE READ`'de transaction, **ilk** okumasındaki "fotoğrafı" kullanır. Kilitten önce herhangi bir okuma yapılırsa
   (ör. kullanıcıyı kilitten önce sorgulamak) sayım eski fotoğrafa bakar. Bu varyant denendiğinde kilit olmasına rağmen
   **7, 8, 9, 10, 8** rezervasyon oluştu. Bu yüzden hem "önce kilit" hem `READ COMMITTED` birlikte kullanılır.
3. Kullanıcı kontrolü, mükerrer kontrolü ve kapasite sayımı kilit altında yapılır; yer varsa kayıt eklenir.
4. **Son savunma hattı veritabanıdır:** mükerrer rezervasyon `uk_reservation_active_user_event` kısıtına takılır ve `409 ALREADY_RESERVED`'e çevrilir.

Sonuç: aynı test 5 turda da veritabanında **tam 1** onaylı rezervasyon. (Bekleme listesinden önce diğer 19 istek `409 EVENT_FULL` alıyordu;
artık `202` ile sıraya giriyorlar ve sıra numaraları **1…19, boşluksuz ve tekrarsız**.)

### Tasarım kararı: bekleme listesi, iptal ve terfi

Oluşturma, iptal ve terfi **aynı kurala** uyar: **önce etkinlik satırı kilitlenir**, transaction **`READ COMMITTED`** çalışır.
Böylece bir etkinlikteki bütün değişiklikler tek sırada olur ve her işlem kilitten sonra en güncel durumu görür.

- **Oluşturma:** kilit → kullanıcı ve mükerrer kontrolü → **önce boş yerleri sıradakilere ver** → hâlâ yer varsa onayla (`201`),
  yoksa `en büyük numara + 1` ile sıraya al (`202`). Bu sıralama sayesinde **sırada bekleyen biri varken boşalan yer yeni gelene verilmez.**
- **İptal:** önce yalnızca kaydın `event_id`'si okunur (hiç değişmeyen bir değer) → etkinlik kilitlenir → kaydın **durumu kilitten sonra** okunur.
  Zaten iptalse hiçbir şey yapılmaz. Değilse `CANCELLED` yapılır ve **aynı transaction'da** boş yer kaldıkça
  en küçük numaralı bekleyen `CONFIRMED`'e geçirilir (`promoted_at` dolar).
- **`SKIP LOCKED` kullanılmaz.** `SKIP LOCKED`, kilitli satırı atlayıp bir sonrakini seçer; bu da sıradakinin atlanması demektir.
  Burada sıradaki kişi, etkinlik kilidi altında sıralı bir sorguyla (`ORDER BY waitlist_number LIMIT 1`) seçilir.

**Testlerin gerçekten koruduğunu kanıtlamak için** servise bilerek hata eklenip eşzamanlılık testi çalıştırıldı (sonra geri alındı):

| Bilerek eklenen hata | Sonuç |
|---|---|
| İptalde kaydın durumunu **kilitten önce** okumak | Aynı anda 10 iptalin birden fazlası "iptal ettim" dedi → test yakaladı |
| İptalde terfi etmemek | Boşalan yer bekleyene verilmedi → test yakaladı |
| İptali `REPEATABLE READ` ile çalıştırmak | Eski "fotoğraf" yüzünden aynı belirti → test yakaladı |

**Neden bu yol?** Değerlendirilen seçenekler:

| Yaklaşım | Artı | Eksi |
|---|---|---|
| **Etkinlik satırını kilitlemek (seçilen)** | Rezervasyon tablosu tek doğruluk kaynağı; kod kısa ve okunur; iptal/bekleme listesi aynı kilit altında çalışır | Aynı etkinliğe gelen istekler sıraya girer (çok yoğun tek etkinlikte darboğaz olabilir) |
| `event` tablosuna sayaç + atomik `UPDATE ... SET reserved = reserved + 1 WHERE reserved < capacity` | Tek SQL ile çok hızlı | Sayaç ile rezervasyon tablosu senkron tutulmalı (ek migration, iptalde azaltma, tutarsızlık riski) |
| Optimistic locking (`@Version`) | Kilit beklemez | Yoğun yarışta isteklerin çoğu çakışıp tekrar denenmeli; kapasite 1 gibi "sıcak" durumda verimsiz |
| `SERIALIZABLE` izolasyon | Kod değişmez | MySQL'de geniş kilitler ve deadlock'lar; tüm sorguları yavaşlatır |

### Örnek istekler (PowerShell)

Kapasitesi **1** olan etkinlik 1; Ayşe (1), Mehmet (2), Zeynep (3) sırayla rezervasyon yapar (çıktılar gerçek çalıştırmadan kısaltıldı):

```powershell
curl.exe -i -X POST http://localhost:8080/api/events/1/reservations -H "Content-Type: application/json" -d '{\"userId\": 1}'
```

```http
HTTP/1.1 201                                   # Ayşe: yer aldı
Location: http://localhost:8080/api/reservations/1
{"id":1,"eventId":1,"userId":1,"status":"CONFIRMED","waitlistNumber":null,"waitlistPosition":null,...}

HTTP/1.1 202                                   # Mehmet: sıraya girdi
Location: http://localhost:8080/api/reservations/2
{"id":2,"eventId":1,"userId":2,"status":"WAITLISTED","waitlistNumber":1,"waitlistPosition":1,...}

HTTP/1.1 202                                   # Zeynep: sıraya girdi
{"id":3,"eventId":1,"userId":3,"status":"WAITLISTED","waitlistNumber":2,"waitlistPosition":2,...}

HTTP/1.1 409                                   # Zeynep tekrar denerse
{"detail":"User 3 is already on the waitlist for event 1","status":409,"title":"Already reserved","code":"ALREADY_RESERVED","currentStatus":"WAITLISTED"}
```

```powershell
# Zeynep durumunu sorgular
curl.exe http://localhost:8080/api/events/1/users/3/reservation
# -> {"id":3,...,"status":"WAITLISTED","waitlistNumber":2,"waitlistPosition":2,...}

# Ayşe iptal eder -> Mehmet AYNI istekte onaylanır
curl.exe -X POST http://localhost:8080/api/reservations/1/cancel
```

```json
{"reservation":{"id":1,"status":"CANCELLED","cancelledAt":"2026-11-22T20:39:32.466940Z",...},
 "alreadyCancelled":false,
 "promoted":[{"id":2,"userId":2,"status":"CONFIRMED","waitlistNumber":1,"promotedAt":"2026-11-22T20:39:32.484429Z",...}]}
```

```powershell
# Ayşe aynı iptali tekrar gönderir -> 200, hiçbir şey değişmez
curl.exe -X POST http://localhost:8080/api/reservations/1/cancel
# -> {"reservation":{"id":1,"status":"CANCELLED",...},"alreadyCancelled":true,"promoted":[]}

# Zeynep artık sıradaki: numarası 2, konumu 1
curl.exe http://localhost:8080/api/events/1/users/3/reservation
# -> {"id":3,...,"status":"WAITLISTED","waitlistNumber":2,"waitlistPosition":1,...}
```

## Testler

```powershell
.\mvnw.cmd test
```

- `HealthControllerTest` veritabanı olmadan her zaman çalışır.
- Diğer testler gerçek MySQL'e, `test` profiliyle **ayrı test veritabanına** (`DB_TEST_URL`) bağlanır; geliştirme verisine dokunmaz.
  `DB_PASSWORD` tanımlı değilse **atlanır** (Skipped).
  - `FlywayMigrationTest`: test veritabanını boşaltır, uygulamayı iki kez başlatır; ilk açılışta V1–V3'ün uygulandığını ve tabloların beklenen sütunlarla oluştuğunu, ikinci açılışta tekrar uygulanmadığını doğrular.
  - `SchemaConstraintsTest`: pozitif kapasite, boş olmayan ad ve benzersiz e-posta kurallarını dener (değişiklikler geri alınır).
  - `EventApiTest`: HTTP üzerinden oluşturma (201 + `Location`, UTC saklama), sıralı listeleme, detay, olmayan ID için 404 ve
    geçersiz istek için 400 davranışını dener (değişiklikler geri alınır).
  - `UserRegistrationApiTest`: geçerli kayıt (201 + `Location`, boşluk kırpma, küçük harf), 8 farklı boş/geçersiz alan (400, hiçbir şey yazılmaz),
    farklı harfle ikinci kayıt (409) ve Java ön kontrolü bilerek atlandığında **veritabanı kısıtının** da 409 ürettiğini dener; ayrıca `GET /api/users/{id}` (200 / 404) (değişiklikler geri alınır).
  - `ReservationApiTest` (tek tek istekler, geri alınır): 201 + `Location`, 404'ler, 400'ler; **FIFO** sıraya giriş (202, numara ve konum),
    onaylı kullanıcının sıraya girememesi, sıraya iki kez girilememesi, durum sorgusu, **iptal + aynı istekte terfi**, bekleyenin iptali,
    **tekrar iptal** (200, ikinci terfi yok), **son bekleyenin terfisiyle sıranın boşalması** ve ardından yeni gelenin doğrudan onaylanması,
    **bekleyen varken boş yerin yeni gelene verilmemesi**, iptal sonrası yeniden katılımın sıranın sonuna gitmesi, veritabanı kısıtının son savunma hattı olması.
  - `ReservationConcurrencyTest` (**gerçek HTTP sunucusu**, eşzamanlı): kapasite 1'e 20 istek (5 tur) → 1 onay + sıra 1…19;
    aynı kullanıcıdan 10 istek (boş ve dolu etkinlikte) → tek kayıt; **3 iptal + 5 yeni istek aynı anda** (3 tur) → terfi edenler tam olarak sıradaki ilk 3 kişi,
    yeni gelenler sonda; **aynı iptal 10 kez aynı anda** → tek gerçek iptal, tek terfi.
    Bu test transaction'lı olmadığı için yalnızca **kendi oluşturduğu** kayıtları test veritabanından siler.
  - **Her senaryonun sonunda** (`WaitlistInvariants`) veritabanında doğrulanır: onaylı ≤ kapasite; bekleyen varken boş yer yok;
    terfi edenlerin numarası hâlâ bekleyenlerin hepsinden küçük (**kimse atlanmadı**); bekleyen numaraları tekrarsız ve sıralı.
  - `SchemaConstraintsTest` V2/V3 kurallarını da dener: foreign key'ler, tek aktif kayıt (onaylı + bekleyen birlikte olamaz, sıraya iki kez girilemez),
    sıra numarasının etkinlik içinde tekilliği, pozitifliği ve bekleyende zorunluluğu, iptal edilmiş kayıtların engellememesi.

## Yol haritası

1. [x] Spring Web iskeleti
2. [x] MySQL bağlantısı
3. [x] Flyway ve ilk şema (`event`, `app_user`)
4. [x] Etkinlik API'si (oluşturma, listeleme, detay)
5. [x] Kullanıcı kaydı API'si (kayıt + detay)
6. [x] Kontenjanlı rezervasyon ve eşzamanlılık testi
7. [x] Rezervasyon iptali
8. [x] FIFO bekleme listesi ve iptalde otomatik terfi
9. [ ] Kimlik doğrulama (şu an kimin iptal edebileceği kısıtlı değil)
