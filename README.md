# reservation-platform

Kontenjanlı etkinlik rezervasyonu ve FIFO (ilk gelen ilk alır) bekleme listesi sunan bir REST API.

> Durum: **2. aşama – MySQL bağlantısı.** Henüz Event modeli ve iş kuralları yok.

## Teknolojiler

- Java 21 (JDK 21 veya üstüyle derlenir)
- Spring Boot 4.2 (Spring Web MVC, Spring Data JPA)
- MySQL 8+ (MySQL Connector/J)
- Maven Wrapper (Maven kurmak gerekmez)

## Veritabanı

Uygulama kendi veritabanını ve yalnızca o veritabanına yetkili kendi kullanıcısını kullanır.
Bağlantı bilgileri ortam değişkenlerinden okunur; şifre hiçbir dosyaya yazılmaz, Git'e girmez.

| Ortam değişkeni | Varsayılan | Açıklama |
|---|---|---|
| `DB_URL` | `jdbc:mysql://localhost:3306/reservation_platform` | JDBC adresi |
| `DB_USERNAME` | `reservation_app` | Uygulama kullanıcısı |
| `DB_PASSWORD` | – (**zorunlu**) | Uygulama kullanıcısının şifresi |

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
- `DB_URL` değişkenini `jdbc:mysql://localhost:3307/reservation_platform` olarak ayarlar.
- Veriler: `%USERPROFILE%\dev\local-mysql\reservation-platform\` (repo dışında).

> `init` sonrası **terminali / IntelliJ'i kapatıp yeniden aç**: yeni ortam değişkenlerini yalnızca yeni açılan programlar görür.

### Kendi MySQL sunucunu kullanmak istersen

Root ile bağlanıp şunu çalıştır, sonra `DB_PASSWORD` (ve gerekirse `DB_URL`) değişkenini ayarla:

```sql
CREATE DATABASE reservation_platform CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'reservation_app'@'localhost' IDENTIFIED BY '<sifre>';
GRANT ALL PRIVILEGES ON reservation_platform.* TO 'reservation_app'@'localhost';
```

## Çalıştırma (Windows)

```powershell
.\mvnw.cmd spring-boot:run
```

Ardından tarayıcıda: <http://localhost:8080/api/health>

```json
{"status":"UP","app":"event-reservation"}
```

## Testler

```powershell
.\mvnw.cmd test
```

- `HealthControllerTest` veritabanı olmadan her zaman çalışır.
- `EventReservationApplicationTests` ve `MySqlConnectionTest` gerçek MySQL'e bağlanır; `DB_PASSWORD` tanımlı değilse **atlanır** (Skipped).

## Yol haritası

1. [x] Spring Web iskeleti
2. [x] MySQL bağlantısı
3. [ ] JPA ile Etkinlik (Event) modeli
4. [ ] Kontenjanlı rezervasyon
5. [ ] FIFO bekleme listesi ve otomatik terfi
6. [ ] Eşzamanlılık (aynı anda gelen istekler) testleri
