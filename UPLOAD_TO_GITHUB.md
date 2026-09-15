# آپلود و ساخت APK — وی پی ان من v1.0.1

1. Repository را ترجیحاً Private کنید.
2. تمام محتویات این پوشه را در ریشه Repository آپلود کنید؛ پوشه `.github` نیز باید وجود داشته باشد.
3. در GitHub به `Settings > Secrets and variables > Actions` بروید.
4. یک Repository Secret با نام دقیق `VPN_APP_API_KEY` بسازید و **App API Key جدید پنل** را در آن قرار دهید.
5. Base URL در این نسخه روی `https://www.emdadkhodroteh.com/test` تنظیم شده و Secret جداگانه لازم ندارد.
6. به `Actions > Build Android APK > Run workflow` بروید.
7. پس از موفقیت، Artifact با نام `VPN-Man-Android-v1.0.1` را دانلود کنید.

> Cron Token و Admin Gate هرگز در GitHub یا APK قرار نگیرند.
