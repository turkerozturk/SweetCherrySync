# SweetCherrySync 1.1.0-alpha.1 — Release hazırlığı

Bu belge 2026-10-21 tarihindeki alfa sürüm hazırlığı içindir. Yeni senkronizasyon algoritması, JUnit testi veya yeni veritabanı optimizasyonu içermez.

## Sürüm kararı

- Son yayımlanan sürüm: `1.0.0` (2025-04-18).
- Yeni Maven sürümü: `1.1.0-alpha.1`.
- Yeni Git etiketi: `v1.1.0-alpha.1` (eski `1.0.0` etiketi değiştirilmez).
- GitHub Releases ekranında **Set as a pre-release** işaretlenmeli. Henüz genel kararlı sürüm olduğu iddia edilmiyor.

## İçerik ve paket

Paket: `dist/SweetCherrySync-v1.1.0-alpha.1.zip` ve SHA-256 kontrol dosyası (`.zip.sha256`).

- `sweetcherrysync-1.1.0-alpha.1.jar`: Spring Boot executable JAR (Java 17+ kurulu olmalı).
- `application.example.yml`: Gerçek kullanıcı bilgisi içermeyen örnek; `application.yml` adıyla kopyalanıp düzenlenmeli.
- `schema-*.sql`: **Boş ve özel olarak ayrılmış** hedef DB için şema örnekleri. SQL dosyaları mevcut şemaları migrate etmez.
- `start.cmd` / `start.sh`: Örnek konfigürasyonu zorunlu tutar, JAR'ı o klasörden başlatır.
- `README.md`, `RELEASE_NOTES_1.1.0-alpha.1.md`, `LICENSE` ve `documentation/` rehberleri.

## Paket üretim adımları (Windows)

Proje kökünde, patch commit edildikten sonra:

```bat
mvnw.cmd -DskipTests package
powershell -NoProfile -ExecutionPolicy Bypass -File release\package-release.ps1
```

`-DskipTests` ile JUnit çalıştırılmaz. Packaging betiği de Maven çağırmaz, yalnızca **önceden üretilmiş JAR** dosyasını ZIP içine koyar. En son geliştirici tarafından çalıştırılan testlerde 38 test başarılıydı; bu yeni paketin ayrıca test edildiği anlamına gelmez.

Paket içeriğini Windows Explorer üzerinden gözle kontrol etmek isteğe bağlıdır; bu aşamada yeni senkronizasyon testleri planlanmıyor.

## Git / GitHub release yayını

1. Yamanın `git apply --check` sonucu uygun olursa uygula, `git diff --check` ardından commit/push yap.
2. Ana daldaki commit üzerinden `v1.1.0-alpha.1` etiketi oluştur: GitHub **Releases → Draft a new release → Choose a tag → Create new tag**.
3. Başlık: `SweetCherrySync 1.1.0-alpha.1 — Incremental Sync (Alpha)`.
4. **Set as a pre-release** seçili olsun.
5. Açıklamaya `RELEASE_NOTES_1.1.0-alpha.1.md` içeriğini ekle. `SweetCherrySync-v1.1.0-alpha.1.zip` ve `.zip.sha256` dosyalarını release asset olarak yükle.
6. GitHub'ın otomatik **Source code (zip/tar.gz)** paketleri çalışan JAR içermez; kullanıcıları derlenmiş ZIP asset'ine yönlendir.
7. URL oluşunca README'nin releases sayfası doğru yeni varlığı gösterecek; eski 1.0.0 indirme bağlantısına yönlendirme yapılmıyor.

## Yayın metnindeki güvenlik uyarıları

- Bu yazılım tek yönlü mirror'dur. Kaynakta silme varsa hedefe de yansıyabilir; bağımsız yedek önerilir.
- Hedefe ayrılmış veritabanı kullan. Var olan tablo yapısını SQL betikleri değiştirmeyebilir; migration yapılmaz.
- Web arayüzünde login yok; localhost varsayılanını değiştirmek veya portu internete açmak risklidir.
- Incremental mode bozulmuş/izinsiz değiştirilmiş eski BLOB'ları otomatik onarmaz, Full Sync çalıştırılmalı.
- UI'daki otomatik zamanlama ayarları restart sonrası resetlenir; JSONL log commit'ten sonra best-effort yazılır.

## Korunan kaynaklar

`schema-mysql.sql` ve `schema-postgresql.sql` dahil dört şema dosyasının hiçbirinin içeriği bu yamada değişmez. Kullanıcının Pi ortamına uygun son şema değişiklikleri olduğu gibi korunur. Mevcut kod yorumlarından hiçbirisi kaldırılmaz; ayrıca yorum arşivi gerekmiyor.
