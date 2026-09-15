# وی پی ان من — Android

نسخه 1.0.0 اپ اندرویدی «وی پی ان من» — طراحی و توسعه: امید

## امکانات این نسخه

- رابط کاملاً فارسی و RTL با ظاهر مینیمال و انیمیشن اتصال
- فونت Vazirmatn که هنگام Build توسط GitHub Actions از منبع رسمی دریافت می‌شود
- اتصال مستقیم به VPN Panel ساخته‌شده با PHP/MySQL
- دریافت لیست سرورها از `/api/v1/manifest.php`
- تست Latency سرورها با TCP و انتخاب خودکار سریع‌ترین سرور
- نمایش تبلیغ تصویری قبل از اتصال برای پلن رایگان، همراه با لینک و شمارش معکوس
- اتصال واقعی Android VPN با `VpnService` و Xray Core
- پشتیبانی اولیه از VLESS، VMess، Trojan و Shadowsocks
- اعلان اتصال و دکمه قطع اتصال
- Build کامل APK روی GitHub Actions؛ نیازی به Android Studio روی کامپیوتر شما نیست

> SSR، Hysteria2 و TUIC در این نسخه از لیست اتصال فیلتر می‌شوند و در فاز بعد می‌توان برایشان Core مناسب اضافه کرد.

## 1) آپلود پروژه در GitHub

فایل ZIP را Extract کنید و **محتویات داخل پوشه** را در Repository خصوصی `vpn-man-android` آپلود کنید. پوشه `.github` را نیز حتماً آپلود کنید، چون Workflow ساخت APK داخل آن است.

ساختار ریشه Repository باید شبیه این باشد:

```text
.github/
app/
scripts/
build.gradle.kts
settings.gradle.kts
gradle.properties
README.md
```

یعنی نباید همه فایل‌ها داخل یک پوشه اضافه مثل `vpn-man-android/vpn-man-android/` قرار بگیرند.

## 2) تعریف GitHub Secrets

در Repository بروید به:

`Settings → Secrets and variables → Actions → New repository secret`

دو Secret زیر را بسازید:

### VPN_API_BASE_URL

برای نصب فعلی شما:

```text
https://www.emdadkhodroteh.com/test
```

بدون `/` آخر.

### VPN_APP_API_KEY

کلید App API Key که VPN Panel هنگام نصب نمایش داده است.

اگر آن را ندارید، داخل پنل بروید به **تنظیمات اپ → تولید App API Key جدید**. کلید جدید فقط همان یک‌بار نمایش داده می‌شود؛ آن را مستقیم در GitHub Secret ذخیره کنید.

**کلید API را داخل فایل‌های پروژه Commit نکنید.**

## 3) گرفتن APK

بعد از Upload و Commit، Workflow به‌صورت خودکار اجرا می‌شود. یا از مسیر زیر دستی اجرا کنید:

`Actions → Build Android APK → Run workflow`

پس از موفق شدن Build:

`Actions → آخرین Run → Artifacts → VPN-Man-Android-v1.0.0`

Artifact شامل این‌هاست:

```text
VPN-Man-v1.0.0-debug.apk
SHA256.txt
```

Debug APK برای تست مستقیم روی گوشی مناسب است. در فاز انتشار، Keystore دائمی و Signed Release APK/AAB اضافه می‌شود.

## هماهنگی با VPN Panel

اپ درخواست زیر را می‌زند:

```text
GET {VPN_API_BASE_URL}/api/v1/manifest.php
X-App-Key: {VPN_APP_API_KEY}
```

و از پاسخ فعلی پنل این موارد را استفاده می‌کند:

- `servers`
- `pre_connect_ad`
- `maintenance`
- `minimum_app_version`

URL اصلی Subscription از Backend به اپ تحویل داده نمی‌شود؛ اپ فقط Nodeهای استخراج‌شده را دریافت می‌کند.

## نکته امنیتی API Key

GitHub Secret باعث می‌شود کلید داخل Repository دیده نشود؛ اما چون اپ برای تماس با API به آن نیاز دارد، مقدار آن در APK نهایی قابل استخراج توسط مهندسی معکوس است. برای نسخه عمومی بعدی بهتر است VPN Panel را به Device Registration + Token کوتاه‌عمر ارتقا دهیم.

## تست‌های ضروری بعد از اولین APK

روی یک گوشی Android واقعی این موارد را بررسی کنید:

1. دریافت لیست سرورها
2. نمایش Ping/Latency
3. باز شدن تبلیغ قبل از اتصال
4. درخواست مجوز VPN اندروید
5. اتصال VLESS/VMess/Trojan/SS
6. باز شدن سایت‌ها بعد از اتصال
7. قطع اتصال از داخل اپ و Notification
8. اتصال مجدد بعد از تغییر Wi-Fi/Mobile Data

اگر یک نوع کانفیگ خاص متصل نشد، متن همان کانفیگ را بدون اطلاعات حساس یا فقط ساختار Query آن ارسال کنید تا Parser همان Transport تکمیل شود.
