# SweetCherrySync — runtime zamanlama, canlı sayaç ve değişiklik günlüğü

## Kapsam

Bu yama **Legacy Full** ve **Incremental** senkronizasyonların veritabanına yazma/karşılaştırma algoritmalarını değiştirmez. Web arayüzündeki sayaçları başarılı senkronizasyondan sonra otomatik yeniler ve commit sonrası metaveri değişikliklerini JSONL dosyasına ekler.

### Sayaçlar

`GET /sync/counters` yalnızca `Statistics` enum'unda tanımlı anahtarları döndürür. Tarayıcı `/sync/progress` yanıtında yeni bir `SUCCEEDED` / `finishedAt` görürse tablodaki altı satırı F5 gerekmeden yeniler. `FAILED` durumunda önceki başarılı commit sayaçları korunur.

### Değişiklik günlüğü

Varsayılan konum: `./logs/sync-node-changes.jsonl`. Bu yol uygulamanın **çalışma dizinine** göredir. CTB veya MariaDB içine ek tablo yazılmaz.

Her satır JSON nesnesidir: `committedAt` (UTC ISO-8601), `startedAt`, `runId`, `mode`, `table`, `action`, `nodeId`, `nodeName`, `key`.

- `CREATED`, `UPDATED`, `DELETED`: gerçek satır karşılaştırmasına dayalı değişiklikler (node/children/bookmark ve kapsam içindeki içerik tabloları).
- `REMOVED_NODE_DEPENDENTS`: Incremental modda silinen gerçek düğüme ait BLOB içerikleri okumadan yapılan temizlik. **Tek tek eski BLOB anahtarlarını bildirmez**; ilgili düğüm ve gerçekten silinen `affectedRows` sayısını bildirir. Satır yoksa kayıt oluşturmaz.
- Tam Sync tüm tablolardaki mevcut/önceki satırları zaten okuduğu için tek tek anahtarları gösterebilir.
- `png`, node metni ve diğer içerik alanları hiçbir şekilde günlüğe yazılmaz. **Düğüm isimleri özel bilgi içerebilir:** bu dosyayı paylaşmayın, yalnızca güvenilir kullanıcıların okumasına izin verin.
- Günlük ancak hedef transaction başarıyla döndükten sonra yazılır. Log yazımı başarısız olursa commit geri alınmaz; konsola WARN basılır. Harici dosya ve hedef veritabanı arasında tam atomiklik garanti edilmez (committen hemen sonra süreç çökerse log kaydı eksik olabilir).
- `sync.audit.enabled=false` ile devre dışı bırakılabilir; `sync.audit.path` ile dizin değiştirilebilir.

### Otomatik zamanlama

Eski sabit aralıklı `SchedulerConfig` varsayılan **pasif**. Yeni arayüz zamanlayıcısı başlangıçta kapalıdır; ayarlar `application.yml` içindeki `sync.auto` anahtarlarından okunur. Arayüzde *Enabled*, *Mode* ve *Interval* (30–86400 saniye) seçilip **Apply schedule** tıklanır.

`GET /sync/auto` yapılandırmayı/sonraki denemeyi verir. `PUT /sync/auto` JSON `{ "enabled": true, "mode": "INCREMENTAL", "intervalSeconds": 300 }` ile anında uygular. Yeni aralık **başvuru anından** hesaplanır, uygulama yeniden başlatıldığında konfigürasyon varsayılanına döner. Otomatik işler web düğmeleriyle aynı tek-iş kuyruğunu kullanır, yoğun olduğunda 10 saniye sonra tekrar dener. Her başarılı *başlatma* sonrasında yeni aralık hesaplanır (iş bitişi sonrası fixed delay değildir).

Eski scheduler yalnızca `my.scheduler.legacy-enabled=true` ile bilerek etkinleştirilir. **İki scheduler'ı birlikte açmayın.** Eski `my.scheduler.enabled` ayarı artık tek başına Legacy Scheduler'ı açmaz.

### Güvenlik / manuel test

1. Varolan veritabanlarının yedeğini alın; Incremental önizleme ile değişiklikleri görün.
2. Bir düğüm adı veya içeriği değiştirin, Incremental Sync'e basın; sayaçlar F5 olmadan güncellenmeli.
3. `logs/sync-node-changes.jsonl` içinde `UPDATED` ve ilgili node ID/adı görünmeli. Dosyanın içinde içerik ve `png` baytları bulunmamalı.
4. Bir bookmark silip yeniden deneyin; `bookmark` için `DELETED` kaydı bekleyin.
5. Hiç değişiklik yokken tekrar senkronize edin; gereksiz kayıt eklenmemeli.
6. Otomatik zamanlamayı önce 60 saniye `INCREMENTAL` seçerek deneyin, sonra kapatın. Web arayüzünden manuel sync ile çakışmada iki işlem eşzamanlı çalışmamalı.
7. Bir JDBC hatasında `FAILED` görünmeli; başarısız transaction için yeni audit kayıtları eklenmemeli.

Mevcut kaynak yorumları silinmediği için ayrıca yorum arşivi oluşturulmadı.
