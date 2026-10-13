# SweetCherrySync bağlantı tanılama aracı — Swing önerisi

**Öneri: SweetCherrySync yanında çalışan küçük bir Java Swing aracı.** Kullanıcı laptopta çalıştırırsa Pi'ye erişimi senkronizasyonla aynı taraftan test eder. Pi'ye ikinci Spring Boot servisi kurmaya veya root yetkili bir web uygulaması geliştirmeye gerek kalmaz. Bu dosya tasarım önerisidir; henüz uygulanmış bir özellik değildir.

## Sekmeler

| Sekme | Alanlar | Testler ve sonuçlar |
|---|---|---|
| Genel | Profil adı, hedef IP/host, testin çalıştığı makine | Java/OS sürümü, yerel IP'ler, kaynak/hedef özeti |
| Sistem / Ağ | Host, SSH portu; isteğe bağlı SSH hesabı | DNS, TCP bağlantısı, isteğe bağlı SSH doğrulaması; Pi hizmet/log kontrolleri |
| MariaDB / MySQL | JDBC URL, veritabanı, kullanıcı, parola, TLS seçimi | Driver, bağlantı, sunucu sürümü, hesap doğrulaması, gerekli CTB tabloları ve alanları |
| PostgreSQL | Aynı bağlantı alanları, gerekirse schema | Bağlantı, sürüm, şema, tablolar/alanlar; rol ve yetki özeti |
| Kaynak CTB | Dosya seçici, salt okunur inceleme | Dosya mevcut/okunabilir mi, SQLite/CTB şeması uygun mu, eski şema açıklaması |
| Loglar / Rapor | Sekme filtresi, kopyala, dışarı aktar | Saat, süre, aşama, sonuç ve maskelenmiş teknik hata |

Her sekmede **Test Et**, **İptal**, kısa sonuç ve ayrıntıları açma bölümü bulunur. `Bağlantı başarılı` ile `Senkronizasyon için uygun` ayrı sonuçlardır. Parola alanında varsayılan olarak yıldızlar kullanılır. Göstermek için kullanıcı ayrıca tıklar.

**Önerilen Resim 13:** Araç geliştirildikten sonra MariaDB sekmesi; üstte sade bağlantı alanları, altta aşama listesi: TCP → JDBC → Kimlik → Şema. Şu anda ekran görüntüsü gerekmiyor.

## Test sırası

1. Alanların biçimi kontrol edilir; eksik URL/host/port anlaşılır biçimde açıklanır.
2. Host çözümleme ve TCP bağlantısı zaman aşımıyla denenir. Ping zorunlu başarı kriteri değildir.
3. JDBC driver yüklenir, bağlantı açılır; ürün/sürüm okunur.
4. `SELECT 1` gibi zararsız sorgu denenir.
5. CTB tabloları, gereken sütunlar ve şema uyumluluğu metadata/salt okunur sorgularla kontrol edilir.
6. Kaynak okuma ve hedef yetkileri raporlanır. Yetki incelemesi mümkün değilse `belirlenemedi` gösterilir; başarı uydurulmaz.

Varsayılan test **tablo oluşturmaz, kayıt yazmaz, şema yükseltmez, senkronizasyon başlatmaz**. Yazma yetkisinin sorgulanması gerçek aktarımın tüm koşullarda başarılı olacağını kanıtlamaz. Gerçek yazma testi ileride eklenirse ayrı ve açık onaylı olmalıdır; ilk sürüm için gerekli değil.

Örnek sonuç:

> PostgreSQL'e TCP bağlantısı kuruldu. Kullanıcı doğrulandı. `ctb` içindeki `children.master_id` bulunamadı. Bu bir ağ veya parola sorunu değil; şema uyumluluğunu inceleyin.

## Sistem ve SSH sınırı

İlk sürümde SSH portuna TCP testi yeterlidir. Kullanıcı OS hesabı, servis durumu ve günlükleri incelemek isterse sonraki adımda isteğe bağlı SSH desteği eklenebilir. PuTTY'nin kurulmuş olması Java aracına otomatik SSH yetkisi vermez.

SSH desteğinde sunucu host key doğrulaması gerekir; ilk bağlantıda parmak izi kullanıcıya gösterilir, sonradan değişirse bağlantı durdurulur. Özel anahtarın konumu seçilebilir; parola ve anahtar loglanmaz. Önceden tanımlı salt okunur komutlarla OS sürümü, `systemctl`, dinleyen portlar ve sınırlı loglar okunabilir. Sudo gerektiren inceleme ayrı gösterilir; parola komut satırına eklenmez.

Hesap oluşturma, root parolası değiştirme, firewall düzenleme ve veritabanı servislerini restart etme **Webmin'de kalır**. Tanılama aracı yönetim yetkisine ihtiyaç duymadan faydalı olmalıdır.

## Kodun yeri ve yeniden kullanım

SweetCherrySync deposunda ortak bir `diagnostics` Java katmanı ve bunun üzerinde Swing görünümü uygun olur. Ortak katman Spring bean'lerine veya Swing bileşenlerine bağlı olmamalıdır. Sonuçlar yapılandırılmış alanlarla döner: aşama, başarı/uyarı/hata, süre, güvenli açıklama ve maskelenmiş teknik ayrıntı.

Swing tarafı `SwingWorker`/arka plan yürütücüsü kullanır; test sırasında pencere donmaz. TCP/JDBC bağlantı ve sorgu zaman aşımı ayarlanır. İptal mümkün olan işleri durdurur, tüm bağlantılar kapanır. MariaDB/MySQL ve PostgreSQL testleri mevcut JDBC bağımlılıklarıyla yapılır; SSH kütüphanesi yalnız SSH özelliği eklendiğinde gerekir.

İleride SweetCherry masaüstü arayüzü veya web ekranı aynı sonuç üreten katmanı kullanabilir. Ancak web tarafında kullanıcıdan alınan keyfî host/JDBC URL, sunucudan iç ağa erişim yaratabilir; masaüstü aracını web endpoint'ine dönüştürmek ayrıca güvenlik tasarımı ister.

## Yapılandırma ve gizlilik

- İlk sürüm mevcut Sync application.yml dosyasını seçip gerekli alanları okuyabilir. **Otomatik kaydetmesin**; test ayarı ile canlı çalışma ayarı ayrı olsun.
- Parola ilk sürümde yalnız bellekte tutulabilir; kalıcı kayıt isteğe bağlı ve ayrı karardır.
- JDBC URL'nin query parametrelerinde de parola/token bulunabilir. Rapor dışarı aktarılırken URL, kullanıcı ve dosya yolları maskeleme önizlemesi sunulsun.
- Varsayılan tanılama raporu not içeriklerini ve SQL sonuç kayıtlarını içermez.
- Laptopta yapılan test ile Pi'de yapılan test farklı ağ yollarını ölçer. Sonuçta testin kaynak makinesi açıkça yazılsın.
- TLS etkinliği gösterilsin; yalnız parola doğrulanınca `güvenli bağlantı` yazılmasın. Sertifika doğrulaması kapatılarak testi başarılı gösteren otomatik düzeltme yapılmasın.

## Uygulama sırası

1. **İlk sürüm:** Genel, Sistem/Ağ TCP, MariaDB/MySQL, PostgreSQL, Kaynak CTB, Loglar. Bağlantı/şema kontrolü ve güvenli rapor.
2. **Sonra:** Profil hatırlama, mevcut YAML'den içe aktarma, kullanıcı onaylı yapılandırma güncellemesi.
3. **İhtiyaç olursa:** SSH ile servis ve log inceleme.

Bu araç başlangıçta manuel Sync testini kolaylaştırır; Webmin'in yerine geçmez. [Webmin rehberi](webmin_setup_tr.md), sistem yönetimi adımlarını tamamlar.
