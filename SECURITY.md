# Security notes

- `VPN_APP_API_KEY` باید فقط از GitHub Actions Secrets وارد Build شود و در سورس Commit نشود.
- `Cron Token` فقط برای cron سمت سرور است.
- `Admin Gate` فقط برای ورود مخفی پنل است و نباید داخل اپ یا Repository قرار بگیرد.
- App API Key نهایتاً داخل APK قابل استخراج است؛ بنابراین آن را یک credential سطح اپ در نظر بگیرید، نه یک راز مدیریتی. در نسخه‌های بعدی Rate Limit و توکن دستگاهی کوتاه‌عمر توصیه می‌شود.
