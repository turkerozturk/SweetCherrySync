# Tek yönlü aktarım güvenilirlik yaması

Temel commit: 0309582ea5b7e027ff059b4d197438f6d66a82fd.

## Yeni davranış

Kaynak ve hedef JDBC veritabanlarıdır; kaynak SQLite ile sınırlı değildir.
SQLite kaynak seçildiğinde dosya mevcut olmalıdır; bağlantı gerçekten salt
okunurdur ve yanlış yol verilince boş kaynak oluşturulmaz. Aynı dosyanın kaynak ve
hedef seçilmesi, şema başlatılmadan reddedilir. Kaynak altı tablo tek bir SERIALIZABLE
transaction içinde okunur. Okuma bitince kaynak transaction kapanır; ardından
hedefteki bütün ekleme, güncelleme ve silmeler tek transaction içinde uygulanır.

Karşılaştırma zaman damgası veya satır sayısıyla sınırlı değildir. Metinler,
yapısal alanlar ve BLOB içerikleri karşılaştırılır. Değişmeyen kayıtlar yazılmaz.
Commit öncesinde hedef tekrar okunur ve tüm desteklenen alanlar karşılaştırılır.
Yazma veya doğrulama hatası transaction'ı geri alır. Sonraki tur tekrar dener.
İstatistik veritabanı ayrı olduğu için istatistik hatası başarılı veri commit'ini
başarısız göstermemelidir; arayüz bu durumda uyarı verir.

Bu akış altı tablonun tamamını belleğe okur. Büyük arşivler için bellek ve okuma
maliyeti ölçülmelidir. Düğüm kimliği/sıralama, alias/shared düğümler, bookmark,
resim/ek dosyalar, tablo ve codebox aktarımı korunur.

## Ayarlar ve kullanım

Harici application.yml içindeki ayarlar, yerleşik varsayılanları değiştirebilir.

```yaml
server:
  address: 127.0.0.1
  port: 8081
my:
  scheduler:
    enabled: true
    delayInSeconds: 60
sync:
  max-delete-percent: 20
  allow-empty-source: false
```

Hedefteki düğümlerin %20'sinden fazlasının silineceği bir tur, hiçbir değişiklik
uygulanmadan reddedilir. Küçük veritabanlarında bir düğüm silmek bile bu oranı
geçebilir. Boş kaynak ayrıca engellenir. Bilinçli toplu silme yapılacaksa önce
kaynak/hedef yollarını ve yedekleri kontrol edin; gereken yüzdeyi harici
ayarlardan geçici olarak yükseltin. Tam boşaltma için iki ayar da değiştirilmelidir:
`max-delete-percent: 100` ve `allow-empty-source: true`.

Yalnız elle aktarım için `my.scheduler.enabled: false` kullanın. Elle başlatma
ana sayfadaki düğmeyle veya `curl -X POST http://127.0.0.1:8081/sync` ile yapılır.
Eski GET /sync artık veri değiştirmez (405). Durum için GET /sync/status kullanın.
Güvenlik koruması nedeniyle reddedilen işlem 422 alır; yanıt korumanın nedenini
gösterir. Beklenmeyen hata 500 alır. Aynı uygulama içindeki ikinci çağrı 409 alır. Zamanlayıcı o turu atlar.

Ana sayfa son işlem durumunu ve son başarılı commit'in sayaçlarını gösterir.
Durum IDLE, RUNNING, SUCCEEDED veya FAILED olur. Görüntüyü yenilemek gerekir.
Durum işlem belleğindedir; yeniden başlatınca IDLE olur. Hata ayrıntıları yerel
uygulama günlüğündedir. JSON yanıtına bağlantı bilgileri konulmaz.

H2 konsolu varsayılan kapalıdır. Yönetim arayüzü yalnız localhost'ta dinler.
Kimlik doğrulama bu yamanın kapsamına eklenmedi; yönetim portunu doğrudan ağa
veya internete açmayın. Uzak not okumak için SweetCherry'nin kendi erişim
kontrollerini kullanın.

## Uyumluluk ve sınırlar

- Desteklenen sütun listeleri repository'deki altı tablo şemasına dayanır.
  Eski children tablosunda master_id bulunmayabilir; ayrıntılar aşağıdadır.
  Bilinmeyen/eksik sütun, yinelenen veya null kayıt kimliği aktarımı durdurur.
  Bu, yeni CherryTree şemalarının sessizce eksik aktarılmasını engeller; yeni
  sürüm desteği için şema incelemesi ve örnek CTB testleri gerekir.
- SQLite testleri bu yamada bulunmaktadır. PostgreSQL, MySQL ve SQL Server
  için SQL üretimi korunmuştur, fakat gerçek sunucu testleri ayrıca gereklidir.
- MySQL kaynak ve hedefindeki altı tablo InnoDB olmalıdır. Başka storage engine ile
  rollback garantisi verilmez ve aktarım engellenir. Yeni MySQL şema dosyası
  InnoDB belirtir; mevcut tabloları otomatik dönüştürmez.
- Hedef özel bir aynadır. Hedefte elle yapılan değişiklikler sonraki aktarımda
  kaynakla değiştirilir. Birbirinden bağımsız kaynaklar aynı hedefe bağlanmaz.
- Kilit aynı Java işlemi içindir. Aynı hedefe iki SweetCherrySync işlemi çalıştırmayın.
- Hedefi CherryTree ile düzenlemeye açık bırakmak desteklenen kullanım değildir.
- Kaynak salt okunur bağlantı bütünlüğü garanti etmez: zaten bozuk bir CTB'nin
  onarılması ve kaynak/hedef kimliğinin kalıcı eşleştirilmesi ayrı işlerdir.
- Ayna sürümlü yedek değildir. Kaynak silmeleri hedefe aktarılır. CherryTree
  yedekleri ve ayrıca sürümlü hedef yedekleri saklanmalıdır.
- Hedef transaction commit olurken bağlantı koparsa sunucunun gerçekten commit
  edip etmediği belirsiz olabilir. Sonraki karşılaştırma turu bunu uzlaştırır;
  kullanıcı commit sonucunu kontrol etmelidir.

## Doğrulama

```bat
git apply --check sweetcherrysync-0309582-reliability.patch
git apply sweetcherrysync-0309582-reliability.patch
git diff --check
mvnw.cmd clean package
```

Linux: `bash ./mvnw clean package`.

Otomatik testler gerçek geçici SQLite dosyaları kullanır. Sonradan ek aktarım
hatasında rollback, sessiz hedef değişikliğinde doğrulama hatası, aynı zaman
damgasıyla içerik değişikliği, BLOB, shared node, silme koruması, salt okunur
kaynak, aynı kaynak/hedef, eşzamanlı çağrı, WAL sırasında kaynak kaydı ve tekrar
deneme senaryolarını kapsar. Controller testleri GET/POST ve 409/422/500 yanıtlarını
kontrol eder. Başlangıç testi gerçek Spring bağlamını ve Thymeleaf ana sayfasını da
kontrol eder.

Yerel kullanım öncesi küçük bir CTB kopyasıyla ilk aktarımı, değişiklikleri,
normal silmeyi ve kaynak dosya kapatılıp tekrar açıldığında aktarımı deneyin.
Gerçek dosyalar üzerinde kullanmadan önce şema uyumluluğu ve hedef veritabanı
sürücüsüyle test sonuçları doğrulanmalıdır.


## İkinci yama: genel JDBC kaynağı ve eski şema

Bu ek yama ilk reliability yamasının üzerine uygulanır. Kaynakta SQLite,
PostgreSQL, MySQL, SQL Server veya uygun JDBC sürücüsüyle başka bir veritabanı
kullanılabilir. Engine, CherryTree'nin altı tablo tanımını kullanmaya devam eder;
herhangi bir farklı uygulamanın şemasını otomatik keşfetmez.

SQLite dışı kaynak için `from.datasource.url`, `driver-class-name`, `dialect`,
`username` ve `password` ayarlanır. Kullanıcı adı ve parola varsayılan olarak
boştur. Örneğin PostgreSQL:

```yaml
from:
  datasource:
    url: jdbc:postgresql://localhost:5432/notes
    driver-class-name: org.postgresql.Driver
    dialect: org.hibernate.dialect.PostgreSQLDialect
    username: notes_reader
    password: YOUR_PASSWORD
```

Kaynak havuzu ve okuma transaction'ı read-only isteği gönderir. Bunun katı bir
veritabanı yetkisi olup olmadığı sürücüye bağlıdır. Sunucuda yalnız SELECT
izinli bir kullanıcı tanımlamak gerçek yazma korumasıdır. SQLite bağlantısının
dosya düzeyinde read-only koruması ayrıca korunur. SERIALIZABLE izolasyonunu
sağlayamayan bağlantılar aktarımı hata ile durdurabilir; sessizce zayıf izolasyona
geçilmez. MySQL kaynak tablolarında da InnoDB kontrol edilir.

### children.master_id

- Kaynakta sütun yoksa normal düğüm kabul edilir; iç karşılaştırmada master_id=0.
- Kaynak/hedefte NULL master_id de paylaşılmamış düğüm anlamında 0'a normalleştirilir.
- Eski kaynak → yeni hedef: master_id=0 yazılır.
- Eski kaynak → eski hedef: sütun eklemeden aktarılır.
- Yeni kaynak → eski hedef: bütün master_id değerleri 0/NULL ise aktarılır.
- Kaynakta gerçek shared/alias düğümü varsa, master_id olmayan hedefe aktarım
  422 ile reddedilir. Bütün hedef transaction geri alınır; paylaşım ilişkisi
  sessizce kaybedilmez. Hedef şemayı önce ayrıca yükseltmek gerekir.

Diğer eksik veya bilinmeyen sütunlar için koruma devam eder. Eski şema desteği,
master_id değişikliği dışında şemanın aynı olduğu kabulüne dayanır.

### SQL Server şeması

schema-mssql.sql tablo/sütun isimlerini köşeli parantezle belirtir. Var olan
başka şemadaki aynı adlı tabloyu yanlışlıkla esas almamak için dbo tablosu
OBJECT_ID ile kontrol edilir. time, ts_creation ve ts_lastsave alanları BIGINT
olur; 32-bit INTEGER sınırını aşan zaman değerleri taşınabilir. Unicode metin
alanları NVARCHAR, ikili içerik VARBINARY(MAX) olarak korunur.

Bu dosya yalnız eksik tabloları oluşturur; mevcut SQL Server tablolarını
otomatik değiştirmez. SQL Server bağlantısının varsayılan şeması dbo olmalıdır.
Gerçek SQL Server sunucusunda syntax, sürücü, yetki ve içerik testleri henüz
çalıştırılmadı. H2 çapraz sürücü testi SQL Server testinin yerine geçmez.

Kaynaklar:
- https://learn.microsoft.com/en-us/sql/t-sql/data-types/int-bigint-smallint-and-tinyint-transact-sql
- https://learn.microsoft.com/en-us/sql/relational-databases/databases/database-identifiers

Ek otomatik testler H2 → SQLite ve SQLite → H2 aktarımı, Unicode/BLOB/64-bit
zaman değerleri, sunucu kaynak kullanıcı bilgileri, eski kaynak/hedef şemaları
ve shared node ilişkisinin eski hedefte reddedilmesini kapsar.
