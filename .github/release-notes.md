## What's new in v1.1.0

- **Quick Settings tile** — connect/disconnect Aether right from the notification shade, no need to open the app. Add it once: swipe down → tap the pencil/edit button in Quick Settings → drag the **Aether** tile in.
- **Share the VPN with your laptop or another phone** — open the side menu → **Share VPN**. Your phone exposes an **HTTP proxy (port 8118)** and a **SOCKS5 proxy (port 1080)** on your Wi‑Fi/hotspot. Tap the copy button next to the exact `ip:port` and enter it in the other device's system proxy settings.
- **Advanced settings on the home screen** — new tune button (top-right) opens protocol, scan mode, IP version and the rest in a bottom sheet.
- **In-place updates from now on** — every build is now signed with one stable key, so future versions install right on top of the old one. Note: updating **from v1.0.0** still needs a one-time uninstall, because v1.0.0 was signed with a temporary key.
- **Fix:** the Share VPN panel now reliably shows the copyable `ip:port` right after you flip the switch (socket setup was silently failing on the UI thread).
- Cleaner release titles (no more "(build N)" suffix).

---

## تازه‌های نسخه ۱.۱.۰

- **تایل تنظیمات سریع (Quick Settings)** — روشن/خاموش کردن VPN مستقیم از منوی بالای گوشی، بدون باز کردن برنامه. یک بار اضافه‌اش کنید: منو را پایین بکشید ← دکمه مداد/ویرایش ← تایل **اِتِر** را بکشید داخل.
- **اشتراک‌گذاری VPN با لپ‌تاپ یا گوشی دیگر** — از منوی کناری ← **اشتراک‌گذاری VPN**. گوشی شما یک پراکسی **HTTP (پورت 8118)** و یک پراکسی **SOCKS5 (پورت 1080)** روی وای‌فای/هات‌اسپات باز می‌کند. آدرس دقیق `ip:port` قابل کپی است و کافی‌است در تنظیمات پراکسی سیستم دستگاه دیگر وارد شود.
- **تنظیمات پیشرفته در صفحه اصلی** — دکمه جدید بالا–راست صفحه، پروتکل، حالت اسکن، نسخه IP و بقیه تنظیمات را در یک پنل پایینی باز می‌کند.
- **آپدیت بدون حذف برنامه از این به بعد** — همه بیلدها از این نسخه با یک کلید ثابت امضا می‌شوند، پس نسخه‌های بعدی مستقیم روی نسخه قبلی نصب می‌شوند. نکته: برای آپدیت **از نسخه 1.0.0** فقط یک بار باید برنامه قبلی حذف شود، چون آن نسخه با کلید موقت امضا شده بود.
- **رفع اشکال:** پنل اشتراک‌گذاری VPN حالا بلافاصله بعد از روشن کردن سوییچ، آدرس `ip:port` قابل کپی را نشان می‌دهد.
- عنوان ریلیزها تمیزتر شد (دیگر پسوند "(build N)" ندارد).

## Downloads

| File | Device |
| --- | --- |
| `*-arm64-v8a.apk` | Most modern phones (recommended) |
| `*-armeabi-v7a.apk` | Older 32-bit devices |
| `*-universal.apk` | Works on both (larger file) |
