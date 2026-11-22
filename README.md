# reservation-platform

Kontenjanlı etkinlik rezervasyonu ve FIFO (ilk gelen ilk alır) bekleme listesi sunan bir REST API.

> Durum: **5. aşama – Kullanıcı kaydı API'si.** Etkinlik ve kullanıcı oluşturulabiliyor; henüz rezervasyon ve bekleme listesi yok.

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

**Kararlar**

- **Uygulanmış migration değiştirilmez.** Şema değişiklikleri yeni dosyayla yapılır (`V2__...`, `V3__...`). Eski dosya değişirse Flyway checksum uyuşmazlığı verip açılışı durdurur.
- **`event`**: `name` (en fazla 200 karakter, boş olamaz), `starts_at` (tarih + saat, `DATETIME(6)`), `capacity`.
  Kapasite `CHECK (capacity > 0)` ile **veritabanında** da korunur; uygulamadaki bir hata bile 0 ya da negatif kapasiteli etkinlik yazamaz.
  Yaklaşan etkinlikleri listelemek için `starts_at` üzerinde indeks var.
- **`app_user`**: `email` (kimlik/giriş anahtarı, **benzersiz**), `full_name`. Tablonun adı `user` değil, çünkü `USER` MySQL'de ve birçok veritabanında anahtar kelime.
  Karşılaştırma ayarı (`utf8mb4_0900_ai_ci`) büyük/küçük harf duyarsız olduğu için `Ayse@x.com` ile `ayse@x.com` aynı kabul edilir.
  Şifre/kimlik doğrulama alanları bu aşamada **yok**; gerektiğinde ayrı bir migration ile eklenecek.
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

{"detail":"Event 999 was not found","status":404,"title":"Event not found"}
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

İstek kuralları (`RegisterUserRequest`): `email` geçerli bir e-posta, en fazla 254 karakter; `fullName` boş olamaz, en fazla 100 karakter.
Baş/son boşluklar doğrulamadan önce kırpılır. E-posta **küçük harfe çevrilerek** saklanır.

**E-posta benzersizliği iki katmanda korunur:**

1. **Java ön kontrolü** (`existsByEmail`): kayıtlı e-postayı veritabanına yazmaya çalışmadan yakalar.
2. **Veritabanı kısıtı** (V1'deki `uk_app_user_email`): iki istek aynı anda gelip ikisi de 1. adımdan geçerse
   MySQL ikinci kaydı reddeder; servis bu hatayı yakalayıp yine `409` döndürür.
   Sütunun karşılaştırma ayarı (`utf8mb4_0900_ai_ci`) büyük/küçük harf duyarsız olduğu için
   `AYSE@x.com` ile `ayse@x.com` veritabanında da aynı kabul edilir.

> `Location` adresi kaydın kalıcı adresini gösterir; `GET /api/users/{id}` ucu henüz yok (ilerideki bir aşamada eklenecek).

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

## Testler

```powershell
.\mvnw.cmd test
```

- `HealthControllerTest` veritabanı olmadan her zaman çalışır.
- Diğer testler gerçek MySQL'e, `test` profiliyle **ayrı test veritabanına** (`DB_TEST_URL`) bağlanır; geliştirme verisine dokunmaz.
  `DB_PASSWORD` tanımlı değilse **atlanır** (Skipped).
  - `FlywayMigrationTest`: test veritabanını boşaltır, uygulamayı iki kez başlatır; ilk açılışta V1'in uygulandığını ve tabloların oluştuğunu, ikinci açılışta tekrar uygulanmadığını doğrular.
  - `SchemaConstraintsTest`: pozitif kapasite, boş olmayan ad ve benzersiz e-posta kurallarını dener (değişiklikler geri alınır).
  - `EventApiTest`: HTTP üzerinden oluşturma (201 + `Location`, UTC saklama), sıralı listeleme, detay, olmayan ID için 404 ve
    geçersiz istek için 400 davranışını dener (değişiklikler geri alınır).
  - `UserRegistrationApiTest`: geçerli kayıt (201 + `Location`, boşluk kırpma, küçük harf), 8 farklı boş/geçersiz alan (400, hiçbir şey yazılmaz),
    farklı harfle ikinci kayıt (409) ve Java ön kontrolü bilerek atlandığında **veritabanı kısıtının** da 409 ürettiğini dener (değişiklikler geri alınır).

## Yol haritası

1. [x] Spring Web iskeleti
2. [x] MySQL bağlantısı
3. [x] Flyway ve ilk şema (`event`, `app_user`)
4. [x] Etkinlik API'si (oluşturma, listeleme, detay)
5. [x] Kullanıcı kaydı API'si
6. [ ] Kontenjanlı rezervasyon
7. [ ] FIFO bekleme listesi ve otomatik terfi
8. [ ] Eşzamanlılık (aynı anda gelen istekler) testleri
