# Incremental Sync: aday planının doğrulanması (salt okunur)

Bu aşama, mevcut `/sync/incremental/preview` uç noktasının fark listesini genişletir.
**Hedefe yazma yapılmaz.** Mevcut Legacy Full Sync algoritması değişmez.

## Tasarım kararları

- `node`, `children`, `bookmark` tablolarının **tam metaveri/kimlik karşılaştırması** yapılır.
  BLOB içeren `image`, `grid`, `codebox` tabloları önizlemede okunmaz.
- Hedefin en büyük `ts_lastsave` değeri `destinationLatestModified` olarak gösterilir;
  `timestampCandidates` yalnızca teşhis amaçlıdır. Bu değerler **filtre olarak kullanılmaz**.
  Hedefte çok yeni bir düğüm olması, daha eski tarihli başka bir düğümün sonradan
  değişmesini görünmez kılmamalıdır. Eşit timestamp'ler de tek başına kesin eşitlik kanıtı değildir.
- Gerçek düğümün var/yok durumu `node` tablosu ile; `children` içindeki eklenen,
  silinen ve değişen satırlar ayrıca ele alınır. `master_id` shared node ilişkisi
  nedeniyle bir `children` satırının silinmesi, gerçek düğümün mutlaka silindiği anlamına gelmez.
- `bookmark` her çalıştırmada tam karşılaştırılır; sırası değişen ve silinen kayıtlar
  düğüm tarihi değişmese de tespit edilir.
- `contentAffectedNodes`: eklenen, silinen veya **node metaverisi değişen** gerçek düğümler.
  Gelecekte yalnızca bu düğümlerin bağlı `image`, `grid`, `codebox` kayıtları incelenecek.
- `metadataAffectedNodes`: yukarıdakilere ek olarak `children` ve `bookmark` farklılıkları.
  `affectedNodes` eski API istemcileri için aynı geniş kümeyi sunar.

## Bilinen sınırlar

1. Kaynakta bağlı içerik değişmesine rağmen node metaverisi değişmemişse bu algoritma
   değişikliği göremez. CherryTree'nin normal davranışı varsayılır; doğrudan SQL müdahalesi
   veya hatalı yazılım bu varsayımı bozabilir.
2. Hedefte bir BLOB doğrudan değiştirilmiş veya silinmişse, ilgili node metaverisi
   aynı kalabileceğinden önizleme bunu fark etmeyebilir. Legacy Full Sync referans kontroldür.
3. Önizleme okumaları henüz tek bir tutarlı kaynak/ hedef transaction snapshot'ı değildir.
   Gerçek yazma modu eklenmeden önce bu konu giderilmelidir.
4. Eski CTB dosyalarında `children.master_id` eksik olabilir; önceki destek korunur.
5. İlerideki gerçek incremental aktarımda silme eşikleri, şema kontrolü, transactional
   uygulama, etkilenen kayıtların doğrulanması ve shared-node senaryoları ayrıca test edilecektir.

## Deneme

`GET http://localhost:8081/sync/incremental/preview`

Yeni JSON alanları: `childrenAdded`, `childrenRemoved`, `bookmarksAdded`,
`bookmarksRemoved`, `bookmarksModified`, `metadataAffectedNodes`,
`contentAffectedNodes`, `destinationLatestModified`, `timestampCandidates`.

Mevcut API alanları korunur. **Bu endpoint senkronizasyon yapmaz.**

## Kaldırılan yorumlar

Bu yamada mevcut kaynak yorumlarından hiçbir yorum kaldırılmadı.
