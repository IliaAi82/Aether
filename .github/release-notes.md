## What's new in v1.2.1

This release makes connecting **smarter, faster and more honest**, fixes Persian-locale text bugs, and hardens security.

- **Auto mode rebuilt from the root and renamed to "Smart"** («هوشمند» in Persian) — the old Auto passed **no protocol flag** to the engine and simply hoped the engine default would work: no intelligence, no fallback. On filtered networks it hung right there, while picking a protocol manually worked. Smart mode now acts like an engineer:
  - **Network DPI discovery before the engine even starts** — a few seconds of parallel probes on your carrier's real path: UDP health (real DNS queries to 1.1.1.1 / 8.8.8.8), an SNI-DPI test (full TLS handshake with SNI toward Cloudflare), reachability + latency of each WARP IP range over TCP, and operator detection (name + SIM country + mobile/Wi‑Fi — with no extra permissions).
  - **The network is classified into four classes**: open / SNI-filtering / UDP-throttled / hostile.
  - **A strategy ladder per class** — an ordered list of "protocol + obfuscation (noize) level + fragment/ECH + only the IP ranges that actually answered", from most to least likely, plus one full last-resort attempt.
  - **Step-by-step connect** — every strategy gets a real connection validated by the same 4-point health check; the first one that passes wins, and its config is kept for automatic reconnects.
  - Live-range selection keeps each attempt fast; light obfuscation is applied automatically on Iranian mobile networks; a manually set endpoint/range is **never overwritten**; and every probe result and decision is written to the log so it's fully transparent why Smart picked what it picked. A new **"Analyzing your network (Smart mode)…"** status was added to the UI.
- **"Connected" now means connected** — the app stays in a new **"Verifying connection…"** state until all four health checks (port, handshake, internet, IP) pass. No more being told you're connected while nothing actually loads yet.
- **IP & flag appear much faster** — the three IP-lookup services are now queried **in parallel** (the fastest one on *your* network wins) instead of one-by-one, the self-test checks run concurrently, and retry delays are shorter. This especially helps networks where DPI slows some providers down.
- **Fixed digits getting scrambled** while typing in the custom IP-range and manual endpoint fields (and everywhere `ip:port` is displayed) when the phone language is Persian — a right-to-left (BiDi) text issue, fixed at the root.
- **New Reset button** at the bottom of advanced settings — one tap restores every setting to its defaults.
- **Security hardening** (full audit report in `docs/SECURITY_AUDIT.md`): TLS hostname verification on the built-in probes (blocks man-in-the-middle), engine output no longer mirrored to Logcat in release builds, cleartext HTTP denied app-wide, stricter backup rules.
- **In-app updates (Telegram-style) — beta** — when a new version is published on GitHub Releases, the app itself shows an "Update" banner on the home screen; one tap downloads the right APK for your device and opens the installer. No need to visit GitHub. **This feature is currently in beta (experimental)** and is still being stabilized.
- **Proper release signing — beta** — every build is signed with one stable key (the repo's CI keystore, or your own via `keystore.properties` / repo Secrets; see `docs/SIGNING.md`), so updates keep installing right on top with no uninstall. If you're coming from a build signed with a *different* key, uninstall once — it's permanent from then on. **The signing mechanism is likewise in beta (experimental)** while it is validated across devices.

---

## تازه‌های نسخهٔ ۱.۲.۱

این نسخه اتصال را **هوشمندتر، سریع‌تر و صادقانه‌تر** می‌کند، باگ‌های متنیِ زبان فارسی را رفع می‌کند و امنیت را بالا می‌برد.

- **حالت Auto از ریشه بازسازی شد و به «Smart / هوشمند» تغییر نام داد** — حالت Auto قبلاً هیچ فلگ پروتکلی به موتور نمی‌داد و صرفاً امیدوار بود پیش‌فرض موتور کار کند — نه هوشی داشت، نه fallback — و روی شبکه‌های فیلترشده همان‌جا گیر می‌کرد؛ در حالی که انتخاب دستی پروتکل جواب می‌داد. حالا حالت «هوشمند» مثل یک مهندس عمل می‌کند:
  - **کشف DPI شبکه قبل از اجرای موتور** — چند پراب موازیِ چندثانیه‌ای روی مسیر واقعی اپراتور: سلامت UDP (کوئری واقعی DNS به 1.1.1.1 و 8.8.8.8)، تست SNI-DPI (هندشیک کامل TLS با SNI به سمت کلادفلر)، سنجش در دسترس بودن + تأخیر تک‌تک رنج‌های WARP با اتصال TCP، و شناسایی اپراتور (نام + کد کشور سیم‌کارت + موبایل/وای‌فای، بدون هیچ مجوز اضافه).
  - **طبقه‌بندی شبکه در چهار کلاس**: باز / فیلترینگ SNI / خفه‌کردن UDP / متخاصم.
  - **نردبان راهبردها** — برای هر کلاس، یک لیست مرتبِ «پروتکل + سطح مبهم‌سازی (noize) + فرگمنت/ECH + فقط رنج‌های آی‌پی‌ای که واقعاً جواب دادند»، از محتمل‌ترین به کم‌احتمال، به‌علاوه یک تلاش نهاییِ کامل به‌عنوان آخرین راه.
  - **اتصال گام‌به‌گام** — هر راهبرد یک اتصال واقعی می‌گیرد که با همان تست ۴مرحله‌ای سنجیده می‌شود؛ اولین راهبردی که پاس شود برنده است و همان کانفیگ برای اتصال مجدد خودکار حفظ می‌شود.
  - انتخاب رنج‌های زنده هر تلاش را سریع نگه می‌دارد؛ روی موبایل ایران به‌طور خودکار حداقل مبهم‌سازی سبک اعمال می‌شود؛ اگر خودتان اندپوینت/رنج دستی داده باشید هرگز بازنویسی نمی‌شود؛ و تمام تصمیم‌ها و نتایج پراب‌ها در بخش لاگ نوشته می‌شود تا کاملاً شفاف باشد چرا «هوشمند» چه چیزی را انتخاب کرده. وضعیت جدید «در حال تحلیل شبکه (اتصال هوشمند)…» هم به UI اضافه شد.
- **«متصل» یعنی واقعاً متصل** — برنامه در وضعیت جدید «در حال بررسی سلامت اتصال…» می‌ماند تا هر ۴ تیک سبز (پورت، هندشیک، اینترنت، آی‌پی) پاس شوند؛ دیگر خبری از «متصل»ی که هنوز هیچ سایتی با آن باز نمی‌شود نیست.
- **آی‌پی و پرچم خیلی سریع‌تر ظاهر می‌شوند** — هر ۳ سرویس تشخیص آی‌پی حالا **هم‌زمان (موازی)** صدا زده می‌شوند و سریع‌ترینِ آن‌ها در شبکهٔ *شما* برنده است؛ تست‌های سلامت هم موازی شده و فاصله‌های تلاش مجدد کوتاه‌تر شده‌اند. مخصوصاً برای شبکه‌هایی که DPI بعضی سرویس‌ها را کند می‌کند فرق بزرگی است.
- **رفع به‌هم‌ریختگی اعداد هنگام تایپ** در فیلد رنج آی‌پی و اندپوینت دستی (و هر جایی که `ip:port` نمایش داده می‌شود) وقتی زبان گوشی فارسی است — مشکل از الگوریتم راست‌به‌چپ متن بود و ریشه‌ای حل شد.
- **دکمهٔ بازنشانی جدید** در انتهای تنظیمات پیشرفته — با یک لمس همهٔ تنظیمات به پیش‌فرض برمی‌گردد.
- **تقویت امنیتی** (گزارش کامل در `docs/SECURITY_AUDIT.md`): تأیید نام هاست در اتصال‌های TLS (بستن راه حملهٔ MitM)، حذف خروجی موتور از Logcat در نسخهٔ نهایی، مسدودشدن HTTP ناامن در کل برنامه، سخت‌گیرانه‌ترشدن قوانین بکاپ.
- **آپدیت داخلی مثل تلگرام (بتا)** — از این به بعد وقتی نسخهٔ جدیدی در گیت‌هاب منتشر شود، خود برنامه در صفحهٔ اصلی خبر می‌دهد و با زدن دکمهٔ «آپدیت»، فایل مناسب گوشی دانلود و نصب می‌شود؛ دیگر لازم نیست به گیت‌هاب سر بزنید. **این قابلیت فعلاً در حالت بتا و آزمایشی است.**
- **امضای درست نسخهٔ نهایی (بتا)** — همهٔ بیلدها با یک کلید ثابت امضا می‌شوند (کلید CI داخل مخزن، یا کلید خودتان با `keystore.properties`/Secrets؛ `docs/SIGNING.md` را ببینید) تا آپدیت‌ها همیشه بدون حذف نصب شوند. اگر نسخهٔ فعلی‌تان با کلید *دیگری* امضا شده بود، فقط یک‌بار حذف/نصب کنید — از آن به بعد دائمی است. **مکانیزم امضا هم فعلاً در حالت بتا و آزمایشی است.**

## Downloads

| File | Device |
| --- | --- |
| `*-arm64-v8a.apk` | Most modern phones (recommended) |
| `*-armeabi-v7a.apk` | Older 32-bit devices |
| `*-universal.apk` | Works on both (larger file) |
