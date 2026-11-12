# Dark Repo Player (StreamBox)

Telefon ve Android TV için oynatıcı:

- **CloudStream `.cs3` eklentileri**: repo.json / plugins.json repolarını ekler, eklentileri indirir ve çalıştırır.
- **Nuvio eklentileri**: `manifest.json` (scrapers) repolarını ekler. JavaScript eklentileri uygulamanın içinde çalışır, CORS engeli yoktur.
- **Afişli katalog**: TMDB'den trend, popüler ve Türk yapımları; CloudStream kaynaklarının kendi ana sayfaları.
- **IPTV**: M3U / M3U8 listeleri, `group-title` kategorilerine göre; logo, arama, `#EXTVLCOPT` başlıkları.
- **Oynatıcı**: ExoPlayer (HLS, DASH, MP4, TS); altyazı, resim içinde resim, TV kumandası desteği.

## APK nasıl alınır

Her `main` dalına yükleme yapıldığında GitHub Actions APK'yı derler:

- **Releases → "latest" → `StreamBox.apk`** (telefondan doğrudan indirilebilir)
- ya da **Actions → son derleme → Artifacts**

Tüm sürümler aynı anahtarla imzalanır, yeni APK eskisinin üstüne kurulur.

## Kullanım

1. **Eklentiler → CloudStream**: repo adresini (`repo.json`, `cloudstreamrepo://…` ya da kısa kod) yapıştır → *Eklentiler* → *Kur*.
2. **Eklentiler → Nuvio**: `manifest.json` adresini ya da `kullanıcı/repo` yaz → eklentileri aç/kapat.
3. **Ana Sayfa**: üstteki çiplerden kaynak seç; afişe tıkla → bölüm/film → yayın seç.
4. **IPTV**: M3U adresi ekle → kategori çipleri → kanal.

## Teknik notlar

- `.cs3` dosyaları CloudStream'in kendi kütüphanesiyle (`com.github.recloudstream.cloudstream:library`) çalışır.
  Sürüm `gradle.properties` içindeki `cloudstreamVersion` ile belirlenir.
- Bir eklenti CloudStream uygulamasının yalnızca kendi içinde bulunan bir sınıfı isterse yüklenmez; nedeni **Ayarlar → Günlük**'te yazar.
- Nuvio ortamı: `fetch`, `require('cheerio-without-node-native' | 'cheerio' | 'crypto-js' | 'axios')`, `module.exports`, `TMDB_API_KEY`.
- İmza anahtarı: AOSP test anahtarı (`keystore/streambox.p12`, parola `streambox`).

Uygulama içerik barındırmaz; eklentileri ve listeleri kullanıcı ekler.
