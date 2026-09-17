VPN Man v1.1.1 - FULL FIXED SOURCE
=================================

این فایل، سورس کامل اپ اندروید است؛ Patch نیست.
بعد از Extract در همان سطح باید این موارد را ببینید:

.github/
app/
scripts/
build.gradle.kts
gradle.properties
settings.gradle.kts
README.md
WORKFLOW-COPY-build-apk.yml

رفع خطای Build #10 و #11:
خطای MyVpnService.kt: Unresolved reference 'Service' اصلاح شده و
import android.app.Service به فایل اضافه شده است.

روش آپلود پیشنهادی:
1) ZIP را Extract کنید.
2) تمام فایل‌ها و پوشه‌های داخل آن را در ریشه Repository vpn-man-android آپلود و Replace کنید.
3) دقت کنید فایل زیر نیز Replace شود:
   .github/workflows/build-apk.yml
   یک کپی قابل مشاهده از آن با نام WORKFLOW-COPY-build-apk.yml هم در ریشه گذاشته شده است.
4) دو Secret زیر باید حتماً موجود باشند:
   VPN_API_BASE_URL
   VPN_APP_API_KEY

Workflow جدید:
- اگر چهار Secret امضای Release تنظیم شده باشند، APK امضاشده Release می‌سازد.
- اگر هنوز Signing تنظیم نشده باشد، Build شکست نمی‌خورد و برای تست APK Debug می‌سازد.

برای اینکه نسخه‌های بعدی روی نسخه قبلی نصب شوند، چهار Secret امضای ثابت را تنظیم کنید:
ANDROID_KEYSTORE_BASE64
ANDROID_KEYSTORE_PASSWORD
ANDROID_KEY_ALIAS
ANDROID_KEY_PASSWORD

کلید امضا را داخل Repository عمومی قرار ندهید.
