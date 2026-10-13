# Raspberry Pi, Webmin ve SweetCherry bağlantıları

Bu rehber Raspberry Pi OS/Debian tabanlı bir sunucuda, **zaten kurulu MariaDB ve PostgreSQL** hizmetlerini Webmin üzerinden yönetmek ve Windows laptopta çalışan SweetCherrySync/SweetCherry ile bağlanmak içindir. Veritabanı sunucularının sıfırdan kurulumu kapsam dışıdır. Adımlar resmî kaynaklara ve mevcut proje yapılandırmasına göre hazırlanmıştır; belirtilen Webmin ekranları bu Pi üzerinde birebir denenmemiştir. Sürüme göre menü adları değişebilir.

## 1. Örnekleri kendi ortamına uyarlama

| Öğe | Rehberde kullanılan değer |
|---|---|
| Raspberry Pi | `192.168.1.114` |
| Windows laptop | `192.168.1.100` — örnektir; `ipconfig` ile gerçek IPv4 adresini bulun |
| SSH | TCP `22` |
| Webmin | Pi üzerinde HTTPS `10000`, laptop tünelinde `11000` |
| MariaDB | TCP `3306`, veritabanı `ctb` |
| PostgreSQL | TCP `5432`, veritabanı `ctb` |
| Senkronizasyon hesabı | `ctb_sync` |
| Salt okunur görüntüleme hesabı | `ctb_view` |

Modemde DHCP rezervasyonu yaparak Pi ve laptop adreslerinin değişmesini önleyebilirsiniz. Sunucu yönetim portlarını internete açmayın. Modemden SSH, Webmin veya veritabanı portu yönlendirmesi bu yerel kullanım için gerekmez. Uzak yönetimde VPN veya SSH tüneli kullanın. Caddy başka makinede bulunuyorsa onun adresi bu tabloda veritabanı istemcisi yerine kullanılmaz.

**Resim 1:** Windows `ipconfig` çıktısında kullanılan ağ adaptörünün IPv4 adresi; diğer adaptörleri ve kişisel bilgileri kırpın.

## 2. SSH ile sunucuya erişme ve Webmin kurulumu

PuTTY ile Pi'ye bağlanın. Mevcut sudo yetkili Linux hesabınızı kullanın; Linux root hesabını açmanız gerekmez. İşlem öncesinde ikinci bir PuTTY oturumunu açık tutmak, erişim ayarlarını yanlış değiştirirseniz düzeltmeyi kolaylaştırır.

```bash
cat /etc/os-release
uname -m
sudo -v
sudo systemctl status mariadb postgresql --no-pager
```

`postgresql` üst servisinin `active (exited)` görünmesi tek başına arıza değildir. Gerçek kümeyi `pg_lsclusters` ile kontrol edin. Pi 3'te tam sistem güncellemesini bu kurulumun içine zorunlu olarak eklemiyoruz.

Resmî Webmin deposunu tanıtın. İndirdiğiniz scripti çalıştırmadan önce inceleyebilirsiniz:

```bash
sudo apt-get update
sudo apt-get install ca-certificates curl
curl --fail --location --output webmin-setup-repo.sh https://raw.githubusercontent.com/webmin/webmin/master/webmin-setup-repo.sh
less webmin-setup-repo.sh
sudo sh webmin-setup-repo.sh
sudo apt-get update
sudo apt-get install webmin --install-recommends
sudo systemctl status webmin --no-pager
dpkg-query -W webmin
```

Kurulum mesajlarını okuyun ve sürümü kaydedin. Depo imza doğrulamasını kapatmayın. MariaDB ve PostgreSQL modülleri standart Webmin paketinin parçasıdır; ayrı bir rastgele eklenti indirmeniz gerekmez.

**Resim 2:** Başarılı kurulumun son satırları ve Webmin servis durumu; parola veya özel anahtar görünmemeli.

## 3. Webmin'i PuTTY tüneliyle açma

PuTTY'de bağlantı ayarlarını açın:

1. Host: `192.168.1.114`, SSH port: `22`.
2. **Connection → SSH → Tunnels** altında Source port: `11000`, Destination: `127.0.0.1:10000`.
3. **Local**, **Auto** seçin ve **Add** düğmesine basın. Yerel portun diğer bilgisayarlardan bağlantı kabul etmesi seçeneğini açmayın.
4. Session bölümünde ayarları bir adla kaydedin; bağlantıyı açıp Linux hesabınızla giriş yapın.
5. Bu SSH penceresi açıkken laptopta `https://127.0.0.1:11000` açın.

İlk erişimde Webmin'in kendinden imzalı sertifikası uyarı verebilir. Sertifika parmak izini sunucudan doğruladıktan sonra bu yerel bağlantıya istisna tanıyın. Kullanılan sertifika yolunu bulun:

```bash
sudo awk -F= '/^(ssl|keyfile|certfile)=/ {print}' /etc/webmin/miniserv.conf
```

`certfile` varsa o dosyada, yoksa sertifikayı da içeren `keyfile` PEM dosyasında şu komutu kullanın; örnek yolu gerçek yol ile değiştirin:

```bash
sudo openssl x509 -in /GERCEK/SERTIFIKA/DOSYASI -noout -fingerprint -sha256
```

Tarayıcıdaki sertifika ayrıntılarıyla karşılaştırın. Özel anahtar dosyasını paylaşmayın. SSH sunucusunun host key uyarısını da ilk kurulumda doğrulayın.

Webmin'e kurulumun yetkilendirdiği yönetici hesabıyla giriş yapın. Mevcut sudo hesabınızın kabul edilmesi kurulumun Unix/PAM ayarlarına bağlıdır; her Linux hesabının otomatik Webmin hesabı olduğunu varsaymayın. Giriş reddedilirse Linux root hesabının şifresini değiştirerek çözmeye çalışmayın; [resmî Webmin giriş kurtarma yönergesini](https://webmin.com/docs/tutorials/changing-a-webmin-password-when-you-cant-log-in/) ve kurulum çıktısını izleyin.

**Resim 3:** PuTTY Tunnels ekranında eklenmiş `L11000 127.0.0.1:10000` satırı.

**Resim 4:** Webmin ana paneli: sürüm, sistem ve bellek/hizmet özeti. Kullanıcı adını gerekirse gizleyin.

## 4. Yönetim erişimini sınırlama

Webmin Configuration altındaki IP erişim denetiminde tünel için `127.0.0.1` ve kullanılıyorsa `::1` adresini izinli bırakın. SSH tüneli üzerinden Webmin, laptopun LAN adresini değil bu yerel adresi görür. Değişiklikten sonra açık oturumu kapatmadan ikinci bağlantıyla doğrulayın.

Doğrudan LAN erişimi isterseniz `https://192.168.1.114:10000` kullanılır; Webmin erişim listesi ve mevcut sunucu firewall'ı yalnız gerekli istemciyi kabul etmelidir. Yönetim arayüzünde HTTPS'i kapatmayın.

Firewall yönetiminde önce mevcut sistemi belirleyin:

```bash
sudo nft list ruleset
# UFW kuruluysa:
sudo ufw status verbose
```

Aktif UFW kullanılıyorsa, gerçek laptop IP'siyle veritabanı portlarını açabilirsiniz:

```bash
sudo ufw allow from 192.168.1.100 to any port 3306 proto tcp
sudo ufw allow from 192.168.1.100 to any port 5432 proto tcp
```

Bu komutlar mevcut SSH erişiminin korunmasının yerini tutmaz. UFW'yi körlemesine etkinleştirmeyin; nftables/başka firewall ile çakıştırmayın. Modem firewall'ı ve sunucu firewall'ı farklı katmanlardır. Webmin tüneli JDBC bağlantısını tünellemez: laptop veritabanına doğrudan LAN üzerinden bağlanacaksa yukarıdaki veritabanı erişimi ayrıca gerekir.

**Resim 5:** Webmin IP erişim ekranında yalnız gereken adresler; uygulamadan önceki ayarları ayrıca kaydedin.

## 5. Linux hesaplarını yönetme

**System → Users and Groups** bölümünden mevcut hesapları listeleyin. Kendi yönetici hesabınızın gruplarını ve parola değişimini buradan yönetebilirsiniz. Parola mevcut haliyle gösterilemez; yenisi atanabilir.

Linux `root`, MariaDB `root@localhost` ve PostgreSQL `postgres` birbirinden bağımsız hesaplardır. `mysql`, `postgres`, `www-data` gibi servis hesaplarının kilitli veya etkileşimli girişe kapalı olması normaldir. Bunlara uygulama kullanıcısı gibi şifre vermeyin. SSH erişiminde IP kısıtı kullanıcı listesinden değil ağ/firewall ve SSH ayarlarından yönetilir.

Webmin Users bölümünde Webmin yönetici yetkileri ayrıca belirlenir. Bu panel yüksek yetkilidir; SweetCherry kullanıcı hesabıyla aynı hesabı/parolayı kullanmayın.

**Resim 6:** Linux hesap listesi ve kendi hesabınızın grupları; parola alanlarını göstermeyin.

## 6. MariaDB: yönetim, hesaplar ve IP erişimi

**Servers → MySQL Database Server** modülü MariaDB için de kullanılır. Modül görünmüyorsa kullanılmayan modülleri kontrol edip modül listesini yenileyin. Module Config'te executable ve yönetim bağlantısı yollarını doğrulayın:

```bash
command -v mariadb
sudo mariadb -e 'SELECT VERSION();'
```

MariaDB root yerel `unix_socket` kimlik doğrulaması kullanabilir. Webmin modülünün bağlantısını buna göre yapılandırın; sırf panel girişi için root'u uzaktan erişilebilir veya yalnız parola kullanan hesaba çevirmeyin. Yönetim modülü bağlanamasa bile PuTTY'de `sudo mariadb` çalışması, veritabanı servisinin çalıştığını gösterir.

MySQL modülünün kullanıcı ve izin ekranlarında uygulama hesaplarını ayrı oluşturun. MariaDB'de hesap kimliği **kullanıcı + host** birleşimidir; `ctb_sync@192.168.1.100`, `ctb_sync@localhost` ile aynı hesap değildir. `%` yerine laptopun gerçek adresini seçin.

Var olan `ctb` veritabanı için örnek yetkiler:

```sql
CREATE USER 'ctb_sync'@'192.168.1.100' IDENTIFIED BY 'BURAYA_FARKLI_GUCLU_PAROLA';
GRANT SELECT, INSERT, UPDATE, DELETE ON ctb.* TO 'ctb_sync'@'192.168.1.100';
CREATE USER 'ctb_view'@'192.168.1.100' IDENTIFIED BY 'BURAYA_BASKA_GUCLU_PAROLA';
GRANT SELECT ON ctb.* TO 'ctb_view'@'192.168.1.100';
```

Bunlar örnektir; mevcut hesabı tekrar CREATE etmeyin. Parola değiştirmeyi Webmin parola alanıyla yapabilirsiniz; SQL kullanıyorsanız parola içeren sorgunun terminal/geçmiş/loglara kaydedilebileceğini bilin. Uygulama hesaplarına tüm sunucu üzerinde ALL PRIVILEGES vermeyin. İlk şema oluşturulacaksa repo `schema-mysql.sql` dosyasını yönetici olarak `ctb` içinde uygulayın; sınırlı senkronizasyon hesabının CREATE yetkisi olmadan otomatik tablo oluşturması beklenmez.

Sunucunun dinleme ayarında örnek olarak `bind-address = 192.168.1.114` kullanın. Debian'da sık görülen dosya `/etc/mysql/mariadb.conf.d/50-server.cnf` olmakla birlikte gerçek dosyayı mevcut kurulumdan doğrulayın. Değişiklik sonrası MariaDB'yi yeniden başlatın; diğer istemciler de etkilenir.

```bash
sudo systemctl restart mariadb
sudo ss -ltnp | grep ':3306'
```

**Resim 7:** MariaDB kullanıcı/host listesi ve `ctb` üzerindeki izinler; parola görünmemeli.

## 7. PostgreSQL: roller ve pg_hba.conf

**Servers → PostgreSQL Database Server** modülünde bağlantı ve küme yollarını kontrol edin. Yerel yönetici bağlantısı çoğunlukla `postgres` OS hesabı ve peer doğrulamasıyla sağlanır; bunu değiştirmeden çalıştırabilirsiniz:

```bash
pg_lsclusters
sudo -u postgres psql -c 'SHOW config_file;'
sudo -u postgres psql -c 'SHOW hba_file;'
sudo -u postgres psql -c 'SHOW listen_addresses;'
sudo -u postgres psql -c 'SHOW port;'
```

Roller ekranında `ctb_sync` ve `ctb_view` için LOGIN ve parola ayarlayın. SUPERUSER, CREATEDB ve CREATEROLE uygulama hesabında gereksizdir. PostgreSQL'de rolün IP'si MariaDB'deki host alanından farklı olarak `pg_hba.conf` ile belirlenir.

Yöneticiyle `ctb` veritabanına bağlanarak mevcut tablolar için:

```sql
GRANT CONNECT ON DATABASE ctb TO ctb_sync, ctb_view;
GRANT USAGE ON SCHEMA public TO ctb_sync, ctb_view;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO ctb_sync;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO ctb_view;
```

Roller önce oluşturulmalıdır. Yeni tablo/sequence eklenirse izinler tekrar değerlendirilir; bu komutlar gelecekteki tüm nesnelere otomatik izin vermez. Şema yoksa repo `schema-postgresql.sql` dosyasını yönetici/şema sahibiyle uygulayın.

Gerçek `postgresql.conf` dosyasında:

```conf
listen_addresses = 'localhost,192.168.1.114'
```

Gerçek `pg_hba.conf` içinde yerel yönetim satırlarını koruyarak, uygun sıraya:

```conf
host  ctb  ctb_sync  192.168.1.100/32  scram-sha-256
host  ctb  ctb_view  192.168.1.100/32  scram-sha-256
```

İlk eşleşen HBA satırı uygulanır; daha yukarıdaki geniş bir kural bu satırlardan önce eşleşebilir. SCRAM parolalarıyla hazırlanmış hesaplar kullanın. `trust` kullanmayın. PostgreSQL TLS kurulmuşsa `hostssl` ve sertifika doğrulayan JDBC ayarları tercih edilir. Yukarıdaki `host` satırı **trafiğin şifreli olduğunu garanti etmez**; parola doğrulaması ve bağlantı şifrelemesi ayrı konulardır.

Dinleme adresi değişimi restart, yalnız HBA değişimi reload gerektirir. Önce doğrulayın:

```bash
sudo -u postgres psql -c 'SELECT line_number, error FROM pg_hba_file_rules WHERE error IS NOT NULL;'
sudo -u postgres psql -c 'SELECT pg_reload_conf();'
# listen_addresses değiştiyse, kurulumunuzdaki doğru küme/servisi yeniden başlatın:
sudo systemctl restart postgresql
```

**Resim 8:** PostgreSQL rol listesi ve uygulama rolünün superuser olmadığını gösteren ayrıntılar.

**Resim 9:** Veritabanına ve laptop IP'sine özel HBA satırları; ilgisiz ağları gizleyin.

## 8. Laptop tarafından bağlantıyı doğrulama

Windows PowerShell'de:

```powershell
Test-NetConnection 192.168.1.114 -Port 22
Test-NetConnection 192.168.1.114 -Port 3306
Test-NetConnection 192.168.1.114 -Port 5432
```

TCP başarılı olması kullanıcı/parolanın veya şemanın doğru olduğunu kanıtlamaz. JDBC bağlantısını uygulamayla doğrulayın. ICMP/ping kapalıysa bağlantı çalışmasına rağmen ping başarısız olabilir.

| Sonuç | İncelenecek katman |
|---|---|
| Timeout | IP, rota, firewall, dinleme adresi |
| Connection refused | Servis durmuş veya o portta dinlemiyor |
| Access denied | MariaDB kullanıcı/host/parola ve yetkiler |
| no pg_hba.conf entry | PostgreSQL istemci IP'si ve kural sırası |
| Password authentication failed | Rol/parola, SCRAM ayarı |
| Table/column missing | `ctb` şeması ve senkronizasyon sürümü |
| TCP açık, JDBC başarısız | Driver, URL, TLS, hesap, veritabanı adı |

**Resim 10:** Laptopta TCP test sonucu. Başarılı bağlantının yalnız ağ katmanı testi olduğunu başlıkta belirtin.

## 9. SweetCherrySync yapılandırması ve ilk aktarım

Bu bölüm depodaki güncel kaynak sürüm içindir. Eski derlenmiş 1.0.0 paketi, [güvenilirlik rehberindeki](reliability_tr.md) yeni kontrolleri içermeyebilir. Çalıştırdığınız sürümü kaydedin.

Önce scheduler'ı kapatın; mevcut application.yml içindeki bölümleri birleştirin. Aynı anahtarı ikinci kez eklemeyin:

```yaml
server:
  address: 127.0.0.1
  port: 8081
my:
  scheduler:
    enabled: false
    delayInSeconds: 60
from:
  datasource:
    dialect: org.hibernate.community.dialect.SQLiteDialect
    url: jdbc:sqlite:C:/Notes/demo.ctb
    driver-class-name: org.sqlite.JDBC
    username:
    password:
    init-mode: always
```

Kaynak CTB mevcut olmalıdır. İç H2/Spring yapılandırmasını gelişigüzel değiştirmeyin. Kaynak başka JDBC veritabanı da olabilir; bu yalnız yerel CTB örneğidir.

Hedef için **yalnız birini** kullanın:

```yaml
# MariaDB'ye mevcut MySQL JDBC driver ile bağlanma örneği
destination:
  datasource:
    dialect: org.hibernate.dialect.MariaDBDialect
    url: jdbc:mysql://192.168.1.114:3306/ctb
    driver-class-name: com.mysql.cj.jdbc.Driver
    username: ctb_sync
    password: 'BURAYA_GERCEK_PAROLA'
    init-mode: always
    schema: schema-mysql.sql
```

```yaml
destination:
  datasource:
    dialect: org.hibernate.dialect.PostgreSQLDialect
    url: jdbc:postgresql://192.168.1.114:5432/ctb
    driver-class-name: org.postgresql.Driver
    username: ctb_sync
    password: 'BURAYA_GERCEK_PAROLA'
    init-mode: always
    schema: schema-postgresql.sql
```

SweetCherrySync'teki kaynak/hedef dialect ayarı, SweetCherry'nin tenant başına otomatik yönlendirmesinden ayrı bir mekanizmadır. MariaDB'ye MySQL driver ile bağlanırken otomatik metadata uyumsuzluklarına karşı MariaDB dialect'i açıkça seçilir. TLS gerekli ortamda JDBC URL'sine ilgili driver'ın sertifika doğrulama ayarlarını ekleyin; bu sade LAN örnekleri TLS'i zorunlu kılmaz.

Uygulamayı mevcut çalıştırma yöntemiyle başlatın. Laptopta:

```powershell
Invoke-RestMethod -Method Post -Uri http://127.0.0.1:8081/sync
Invoke-RestMethod -Uri http://127.0.0.1:8081/sync/status
```

Başarı durumunu ve logları kontrol edin. Sonra SweetCherry'de `ctb_view` hesabıyla tenant tanıtıp aynı düğümleri görüntüleyin. Sorun yoksa scheduler'ı etkinleştirip Sync'i yeniden başlatın. Sync HTTP hizmetini kimlik doğrulama varmış gibi internete/LAN'a açmayın; bu örnekte localhost'a bağlı kalır.

**Senkronizasyon geçmişli yedek değildir. Kaynaktaki silinme/değişiklik hedefe taşınabilir. Senkronizasyon hedefini SweetCherry'de düzenlerseniz, sonraki aktarım değişikliklerinizi silebilir.** Düzenleme için bağımsız veritabanı kullanın. SweetCherry otomatik yedek üretmez; bu rehber de yeni bir yedekleme özelliği eklemez.

**Resim 11:** `/sync/status` başarı bilgisi ve SweetCherry'de aynı hedef veritabanının açılması; dosya yollarını ve özel notları gizleyin.

## 10. Parola değişikliği, günlükler ve izleme

Parola değiştirdikten sonra Sync yapılandırmasını güncelleyip yeniden başlatın. SweetCherry tenant dosyasını güncelleyin, veri kaynaklarını yenileyip bağlantıyı yeniden seçin. Açık havuzdaki eski bağlantıların hemen kesileceğini varsaymayın.

Webmin hizmetler ve sistem günlüklerini incelemeyi kolaylaştırır. PuTTY karşılıkları:

```bash
sudo journalctl -u mariadb -n 50 --no-pager
sudo journalctl -u postgresql -n 50 --no-pager
sudo journalctl -u webmin -n 50 --no-pager
pg_lsclusters
```

PostgreSQL küme günlükleri dağıtıma göre ayrıca dosyada veya küme servisinde bulunabilir. Webmin System and Server Status modülünde servis/TCP kontrolleri ayarlayabilirsiniz; bunlar **Pi tarafından** yapılan testlerdir. Laptopun firewall/JDBC erişimini doğrulamak için laptop testleri gerekir.

**Resim 12:** Webmin'de hizmet durumu ve kısa log görünümü. Hata varsa parola, URL içindeki sırlar ve not içeriğini maskeleyin.

## Kaynaklar ve sonraki adım

- [Webmin kurulumu](https://webmin.com/download/)
- [Webmin ayarları ve IP denetimi](https://webmin.com/docs/modules/webmin-configuration/)
- [Webmin kullanıcıları](https://webmin.com/docs/modules/webmin-users/)
- [Linux hesap modülü](https://webmin.com/docs/modules/users-and-groups/)
- [MariaDB/MySQL modülü](https://webmin.com/docs/modules/mysql-database-server/)
- [PostgreSQL modülü](https://webmin.com/docs/modules/postgresql-database-server/)
- [MariaDB yerel root doğrulaması](https://mariadb.com/docs/server/security/user-account-management/authentication-from-mariadb-10-4)
- [PostgreSQL HBA kuralları](https://www.postgresql.org/docs/current/auth-pg-hba-conf.html)
- [PostgreSQL bağlantı ayarları](https://www.postgresql.org/docs/current/runtime-config-connection.html)
- [PuTTY port forwarding](https://tartarus.org/~simon/putty-snapshots/htmldoc/Chapter3.html#using-port-forwarding)
- [Swing bağlantı tanılama aracı önerisi](connection_diagnostics_swing_plan_tr.md)

Bir hata için destek isterken OS/Java/uygulama sürümünü, testi hangi makineden yaptığınızı, hedef türü/portunu, beklenen sonucu ve maskelenmiş hatayı paylaşın. Parolayı, özel anahtarı ve not içeriklerini paylaşmayın.
