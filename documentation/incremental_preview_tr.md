# Incremental Sync — ilk aşama: salt okunur önizleme

`GET /sync/incremental/preview` kaynak ve hedef `node`, `children`, `bookmark` metaverilerini karşılaştırır.
`added`, `removed`, `changed`, `childrenChanged`, `bookmarksChanged` ve `affectedNodes` döndürür.
Hedefe **hiçbir yazma işlemi yapmaz**; BLOB verilerini okumaz; checkpoint oluşturmaz.
Mevcut `/sync` ve `/sync/start` uçları **Legacy Full Sync** olarak kalır.

## Güvenlik ve sınırlar

- Bu bir aktarım veya bütünlük doğrulaması değildir. Kaynak ve hedef aynı anda değişiyorsa tutarlı bir anlık görüntü garantisi vermez.
- `node.txt` içeriği büyük olabileceği için bu önizleme yalnızca node metaverisini okur. Yalnızca içerik değişmiş ve tarih/metaveri değişmemişse tespit edilemez.
- `image`, `grid`, `codebox` kayıtları doğrudan incelenmez. İleride gerçek incremental aktarımda yalnızca etkilenen düğümlere göre okunmalıdır.
- `children.master_id` eski şemalarda olmayabilir; bu alan mevcutsa okunur.
- `bookmark` kümesindeki değişiklik ayrıca raporlanır.
- Hedefte kötü niyetli veya bağımsız değişiklikler bu önizlemede tam olarak yakalanmayabilir; Legacy Full Sync güvenilir doğrulama seçeneğidir.
- Yeni aktarım modu uygulanmadan önce, tüm desteklenen veritabanları üzerinde şema ve tutarlılık testleri gerekir.

## Yorum arşivi

Bu yamada mevcut kaynak kod yorumları silinmedi; arşivlenecek yorum yok.
