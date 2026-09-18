VPN Man v1.1.4 - رفع رگرسیون اتصال

این نسخه مسیر TUN/Xray را به رفتار نسخه 1.1.0 که روی دستگاه کار می‌کرد برمی‌گرداند، در حالی که امکانات نسخه‌های جدید (تفکیک شخصی/رایگان، UI و Collector) حفظ شده‌اند.

تغییرات اتصال:
1) setBlocking(true) برای TUN در Android 10+ بازگردانده شد.
2) مسیر IPv6 و DNS IPv6 داخل VPN بازگردانده شد.
3) MTU از 1400 به 1500 بازگردانده شد.
4) port=0 از inbound نوع TUN حذف شد تا ساختار Xray مثل نسخه سالم 1.1.0 باشد.
5) تاخیر مصنوعی 250ms و generation path از سرویس VPN حذف شد.

نصب در GitHub:
- کل محتوای این پوشه را روی ریشه Repository جایگزین کنید.
- فایل .github/workflows/build-apk.yml نیز حتما جایگزین شود.
- نسخه Gradle برابر 1.1.4 / versionCode=8 است.
- Artifact صحیح باید VPN-Man-Android-v1.1.4-arm64-release باشد (در صورت تنظیم Signing Secrets).

نکته: پنل PHP برای این Hotfix نیاز به تغییر ندارد.
