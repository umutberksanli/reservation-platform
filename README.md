# reservation-platform

Kontenjanlı etkinlik rezervasyonu ve FIFO (ilk gelen ilk alır) bekleme listesi sunan bir REST API.

> Durum: **1. aşama – proje iskeleti.** Henüz veritabanı ve iş kuralları yok.

## Teknolojiler

- Java 21 (JDK 21 veya üstüyle derlenir)
- Spring Boot 4.2 (Spring Web MVC)
- Maven Wrapper (Maven kurmak gerekmez)
- MySQL (sonraki aşamalarda)

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

## Yol haritası

1. [x] Spring Web iskeleti
2. [ ] MySQL + JPA ile Etkinlik modeli
3. [ ] Kontenjanlı rezervasyon
4. [ ] FIFO bekleme listesi ve otomatik terfi
5. [ ] Eşzamanlılık (aynı anda gelen istekler) testleri
