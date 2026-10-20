# Incremental Sync — ilk yazma sürümü

Bu geliştirme `Legacy Full Sync` yerine geçmez. Var olan `/sync`, `/sync/start`,
`/sync/progress` ve salt okunur `/sync/incremental/preview` korunur.
Yeni `POST /sync/incremental/start` ve web arayüzündeki **Incremental Sync** düğmesi,
hedefte gerçek değişiklik yapar.

## İşlem akışı

1. Kaynak ve hedefin aynı SQLite dosyasını göstermediğini kontrol eder.
2. Kaynağı read-only SERIALIZABLE transaction içinde okur; hedef için ayrıca
   SERIALIZABLE transaction açar. Şemaları ve MySQL/MariaDB'deki InnoDB koşulunu
   doğrular.
3. Tüm `node` metaverisini (BLOB veya `node.txt` olmadan), `children` ilişkilerini
   ve `bookmark` kayıtlarını okuyarak kimlik, ilişki ve metaveri farklarını hesaplar.
   `MAX(ts_lastsave)` yalnızca önizleme tanısıdır; eşik filtrelemesi yapılmaz.
4. Yalnızca **eklenen veya node metaverisi değişen gerçek düğümlerin** `node`,
   `image`, `grid`, `codebox` içeriklerini kaynak ve hedeften okur.
5. Silinmiş gerçek düğümlerin bağlı kayıtlarını **BLOB indirmeden** `DELETE` ile
   temizler. Ardından `children` ve `bookmark` ekleme/silme/sıralama değişikliklerini,
   düğüm ve bağlı içerik farklarını uygular.
6. Commit öncesinde tüm `node`/`children`/`bookmark` metaverisini yeniden karşılaştırır;
   yalnızca etkilenen düğümlerin tam satırlarını ve silinen düğümlerle ilişkili
   kayıtların yokluğunu doğrular. Hata oluşursa hedef transaction geri alınır.
7. Commit başarılıysa son işlem sayaçlarını günceller.

## Bilinçli sınırlar

- CherryTree'nin normal davranışında `node.ts_lastsave` içerik düzenlemelerinde
  güncellenir. Kaynaktaki bağlı içerik değiştirilip bu alan ve diğer node metaverisi
  **hiç değişmezse** incremental değişikliği göremez.
- Hedefte kaynağa danışılmadan bozulmuş veya silinmiş, fakat node metaverisi
  değişmemiş resimler/ekler incremental tarafından bulunamaz. **Legacy Full Sync**
  tüm kayıtları kontrol eden referans/kurtarma modudur.
- Silinmiş gerçek düğümler ile yalnızca silinmiş shared `children` satırları ayrıdır.
- Silme yüzdesi (`sync.max-delete-percent`) ve boş kaynak koruması devam eder.
- 1000'den fazla içerik adayı varsa bellek maliyeti nedeniyle Incremental Sync
  güvenli biçimde durdurulur; Legacy Full Sync kullanılabilir.
- Toplu tam transferi içerebilen ilk kurulumlar için Legacy Full Sync tercih edilir.
- Bu ilk sürümde commit edilen node değişiklikleri için CTB dışı kalıcı JSONL
  denetim günlüğü henüz yoktur; sonraki aşamaya bırakılmıştır.

## Manuel deneme

Önce önemli CTB ve hedef MariaDB'nin yedeğini alın. Proje kökünde:

```bat
 git apply --check sweetcherrysync-incremental-write.patch
 git apply sweetcherrysync-incremental-write.patch
 git diff --check
 mvnw.cmd clean package
```

1. `GET /sync/incremental/preview` çıktısını inceleyin.
2. Web ana sayfasındaki **Incremental Sync** düğmesini tıklayın.
3. `SUCCEEDED` görüldüğünde `/sync/incremental/preview` yeniden çağrıldığında
   `added`, `removed`, `changed`, `childrenChanged` ve `bookmarksChanged` boş/false
   olmalıdır. Başarılı ancak hedefte beklenmeyen başka BLOB müdahaleleri yapılmışsa
   bu çıktı tek başına tam bütünlük kanıtı değildir.
4. CherryTree'de sırasıyla bir node ekleme, yeniden adlandırma, içerik/ek değiştirme,
   shared child satırı değiştirme, bookmark sırasını değiştirme ve silme yaparak
   her birinden sonra Incremental Sync'i ve bağlı tabloları kontrol edin.
5. İsteğe bağlı olarak Legacy Full Sync çalıştırıp iki yöntemin veri sonucunu
   karşılaştırın. İlk sürümde gerçek MariaDB testi özellikle önemlidir.

## Kaldırılan kaynak kod yorumları

Bu yamada mevcut kaynak yorumları kaldırılmamıştır.
