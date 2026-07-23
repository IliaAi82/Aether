## What's new in v1.2.0

This release ships the full **advanced feature set** and makes app updates **permanent**.

- **Amnezia-style anti-DPI obfuscation (Noize)** — Off / Light / Firewall / Balanced / GFW / Aggressive.
- **New "Ironclad" scan mode** for the hardest networks.
- **Endpoint selection** — Auto, Manual peer (`ip:port`), or **Custom range**.
- **Scan any IP range you type** (e.g. `8.6.112.x`, `188.114.96.0/24`) — the engine scans exactly those.
- **WireGuard keepalive**, **adjustable MTU** (default 1280), **TLS fragmentation**, **Encrypted Client Hello (ECH)**.
- **Proxy mode** (local SOCKS5/HTTP without a full-device VPN) and **per-app split tunneling** with an app picker.
- **Fixes:** correct "your IP" readout (no more cellular IPv6), correct upload/download traffic figures, MTU default 1280.
- **In-place updates that stick** — the signing key is persisted inside your GitHub repo, so as long as you keep building in the **same** repository, new versions install right on top with no uninstall. (Only if you ever see “App not installed”: uninstall once, reinstall, and it's permanent from then on.)
- **Fixed:** the advanced-settings sheet now scrolls all the way to the bottom without clipped controls.
- **Fixed:** full-device VPN mode now includes and verifies the complete embedded TUN-to-SOCKS native core; switching back from proxy mode works normally.

---

## تازه‌های نسخه ۱.۲.۰

این نسخه مجموعهٔ کامل قابلیت‌های پیشرفته را ارائه می‌دهد و آپدیت برنامه را **دائمی** می‌کند.

- **مبهم‌سازی ضدDPI سبک Amnezia (Noize)** — خاموش / سبک / فایروال / متعادل / GFW / تهاجمی.
- **حالت اسکن جدید «Ironclad»** برای سخت‌ترین شبکه‌ها.
- **انتخاب نقطهٔ اتصال** — خودکار، پییِر دستی (`ip:port`)، یا **رنج دلخواه**.
- **اسکن هر رنج آی‌پی که تایپ کنید** (مثل `8.6.112.x` یا `188.114.96.0/24`) — موتور دقیقاً همان را اسکن می‌کند.
- **Keepalive وایرگارد**، **MTU قابل تنظیم** (پیش‌فرض ۱۲۸۰)، **قطعه‌قطعه‌سازی TLS**، **Encrypted Client Hello (ECH)**.
- **حالت پراکسی** (SOCKS5/HTTP محلی بدون گرفتن کل دستگاه) و **تانل تفکیکی برای هر برنامه** با انتخابگر برنامه.
- **رفع اشکال:** نمایش درست «آی‌پی شما» (دیگر IPv6 سلولار نه)، اصلاح مقدار ترافیک آپلود/دانلود، پیش‌فرض MTU برابر ۱۲۸۰.
- **آپدیت بدون حذفِ ماندگار** — کلید امضا داخل مخزن گیت‌هابِ خودتان ذخیره می‌ماند، پس تا وقتی در **همان** مخزن بیلد بگیرید نسخه‌های جدید دقیقاً روی نسخهٔ قبلی نصب می‌شوند بدون حذف. (فقط اگر پیام «برنامه نصب نشد» دیدید: یک‌بار حذف و دوباره نصب کنید؛ از آن به بعد دائمی است.)
- **رفع شد:** پنل تنظیمات پیشرفته تا آخر اسکرول می‌شود و کنترل‌های پایین صفحه بریده نمی‌شوند.
- **رفع شد:** هستهٔ کامل TUN به SOCKS داخل APK قرار می‌گیرد و قبل از انتشار بررسی می‌شود؛ برگشتن از حالت پراکسی به VPN اصلی حالا درست کار می‌کند.

## Downloads

| File | Device |
| --- | --- |
| `*-arm64-v8a.apk` | Most modern phones (recommended) |
| `*-armeabi-v7a.apk` | Older 32-bit devices |
| `*-universal.apk` | Works on both (larger file) |
