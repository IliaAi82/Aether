## Aether v1.2.2

Engine maintenance moves into the build pipeline, the in-app updater is gone, the unreliable country picker is gone and endpoint selection is back in the engine’s hands, and 1.2.1's performance and compatibility problems are fixed at the root.

**⚡ You can install this directly over 1.2.1 — no uninstall, and your settings are kept.**

### 🔄 Automatic engine (core) upgrades in CI
- Every build checks the official [Aether Core repo](https://github.com/CluvexStudio/Aether) and upgrades the vendored engine automatically when a newer release exists. The stable reference is **v1.4**.
- The app's own engine patches are preserved across upgrades; upstream conflicts produce a loud warning instead of silent data loss.
- If GitHub is unreachable the build keeps the current engine and continues — the sync only moves forward, never backwards.
- After a successful upgrade CI edits the README changelog itself and commits it back, so docs can never drift from the shipped engine. The core version is now visible in the About panel.

### 🗑️ In-app update system removed
> For the security of our users, for complete transparency, and to guarantee the authenticity of the code they receive, the direct in-app update capability has been removed. From now on, all updates will be available exclusively from the project's official GitHub Release page, officially signed — preventing any unwanted download from unknown sources.

The update checker, the in-app update card, the `REQUEST_INSTALL_PACKAGES` permission and the `FileProvider` are all deleted. **The app can no longer download or install anything.**

### 🌐 No server/location list — the engine picks the endpoint itself

Aether has no country list and no server list, and it never had a real one. It connects you to **Cloudflare's WARP network**, whose addresses are **anycast**: the very same IP is announced from every Cloudflare datacenter at once, so the datacenter that answers is decided by your operator's routing — not by the app. A country menu in the app could only ever be a label; it could not move you to that country. That is why the picker (and its whole catalogue) was removed in this release.

- **The engine chooses the endpoint.** It scans its built-in WARP ranges, measures them and connects to whichever edge answers best on your network at that moment.
- **Smart Auto still does its real job:** fingerprinting the network's DPI and choosing the protocol / obfuscation ladder.
- **Your own settings still win.** A hand-typed peer (`ip:port`) or a custom scan range in Settings pins the scan exactly as before, and quick reconnect works normally.
- **No forced IP filtering.** Blocking exits by country is not reliable either — an experimental "never Iran" gate rejected almost every edge on real Iranian networks and left the app retrying until it gave up, so it was removed. Connection reliability comes first.

### ⚡ Performance, compatibility & signature
- **Memory leak fixed**: the diagnostics log is now a bounded 800-line ring buffer with throttled UI updates, batched background disk writes and a 512 KiB rotating file.
- **Much lower idle CPU**: the VPN supervisor blocks on the engine process instead of polling every two seconds — crash detection is faster *and* cheaper.
- No more busy-wait after connect; geolocation probes share one low-priority thread pool; port probing backs off adaptively.
- **v2rayNG conflict fixed**: LAN-share moved from 10808/10809 to **10810/10811**, and known neighbours (v2rayNG, Clash, Psiphon, Privoxy) are detected and named in any port error.
- **Signature fixed at the root**: CI pins the signer's SHA-256 fingerprint and **fails the build** if it ever changes, guaranteeing over-install from 1.2.1 works.

- **Switching protocol no longer stalls the app.** Disconnecting and then connecting on a different protocol felt like it "took forever to start", on every protocol except Smart. Two real bugs caused it. First, stopping the engine was fire-and-forget: the app sent the process a polite terminate signal and immediately moved on, so the old engine was often still alive and still holding the local SOCKS5 port `127.0.0.1:1819` while the next one was already starting — the new attempt either could not bind or verified itself against the dying socket and had to time out and retry. The engine is now really waited for (and force-killed if it does not exit), and a new attempt waits for the local port to be released before it starts. Second, a connect tapped while the previous session was still winding down was **silently dropped** ("a run is already active → return"), which is exactly the "I pressed connect and nothing happened for a while" symptom; the new session now takes ownership, joins the old one, tears it down and starts immediately.
- **MASQUE (and any hand-picked protocol) gets a real second chance.** A protocol chosen by hand used to get exactly **one** attempt with the full scan budget of the selected scan mode — up to 150 s on Balanced, 300 s on Thorough — and no fallback, while Smart mode walks a ladder of short, hardened attempts. On a network where UDP/QUIC is throttled that meant staring at "Connecting" for minutes and then failing. Now the chosen protocol runs a capped first pass exactly as configured, and if that fails the **same** protocol is retried with anti-DPI hardening (obfuscation on, plus HTTP/2, TLS fragmentation and ECH for MASQUE) on the full budget. The protocol you picked is never swapped for another one.
- **Disconnect is instant again (30–50 s freeze fixed at the root).** Tapping disconnect could leave the button on "Disconnecting…" for up to a minute on every protocol. Root cause: the service supervisor parks on the engine process with `Process.waitFor`, which is a **blocking** Java call — and coroutine cancellation cannot interrupt a blocking call. The teardown asked the session to stop and then *waited for it to finish before killing anything*, so it sat inside that wait until the whole 60-second window expired (the log shows the engine being stopped exactly 60.0 s after connect, not when the button was tapped). Two changes fix it for good: the engine wait is now interruptible, so cancellation aborts it in milliseconds, and the teardown order was inverted — cancel, kill the natives immediately, flip the UI to idle, and only *then* reap the finished coroutine off the critical path. Reconnecting on another protocol follows the same order, so it no longer inherits the old session’s wait either. Stopping the engine also escalates to a hard kill after 250 ms instead of waiting seconds.
- **The release pipeline can no longer be broken by files left over from 1.2.1.** Copying 1.2.2 over an existing 1.2.1 repository overwrites changed files but cannot delete the files 1.2.2 *removed* (the in-app updater, the location picker, the forced-exit policy). Those orphans still referenced symbols and string resources that no longer exist, so `compileReleaseKotlin` failed with `Unresolved reference 'GITHUB_REPO'`, `'update_available'` and friends — which is exactly why the same sources built fine in a fresh repository and failed in the real one. The build now purges them itself: `scripts/purge-stale-sources.sh` deletes every path listed in `.github/removed-sources.txt` before compiling, commits the cleanup back to the branch, and additionally hard-fails in one second with a precise file+line message if *any* Kotlin source still references a string resource that does not exist.
- **The signing certificate of 1.2.1 is now protected by the build itself.** Android only installs an update when the old and the new APK carry the same signature, so the key must never change. Two guards were added: the build refuses to mint a new CI keystore in a repository that has already published with one (it stops with an explicit “restore `.github/ci-keystore.jks.b64`” error instead of silently producing an uninstallable APK), and the signer fingerprint is now pinned and enforced in CI-key mode too, per repository (`.github/expected-signer-ci.txt`). If the certificate ever differs from the one the previous release shipped with, the build fails instead of publishing. Nothing about this leaks between repositories: a scratch repository pins its own value.
- **The aurora animation is gone; the background is now a flat colour.** Three large radial gradients were being composited full-screen behind every screen for as long as the app was open. Even after the redraw rate was capped it still cost real GPU and CPU work on every frame budget the UI needed. The backdrop is now a single static fill that never invalidates — no animation runs behind the UI any more, so menus, sheets and the connect screen get the whole frame budget.
- **The main menu no longer runs while it is closed.** Android's navigation drawer composes its contents even when the drawer is shut, so the diagnostics, sharing, advanced and about cards were live at all times, recomposing on every settings change and on every engine log line behind a panel nobody was looking at. They are now built only when the drawer is actually open.
- **The diagnostics log only subscribes while it is open.** During a scan the engine emits hundreds of log lines; each one used to recompose the whole diagnostics card — and the drawer around it — even with the log console collapsed. Only the open console listens now.
- **The Advanced sheet opens instantly.** Its ~40 controls used to be laid out in the same frame the sheet starts its slide-in animation, which visibly stuttered the opening. The sheet now animates in first and fills itself immediately afterwards.

### 🔒 Security audit
This project underwent a **100% line-by-line security audit** for 1.2.2 and the critical vulnerabilities identified were remediated per mobile audit standards — secrets, cryptography/MitM, DNS/IPv6/real-IP leaks, tunnel bypass, local storage, permissions & manifest, logging, dependencies and network config. Full report: `docs/SECURITY_AUDIT_1.2.2.md`.

---

<div dir="rtl">

## نسخهٔ ۱.۲.۲ Aether

مدیریت نسخهٔ هسته به خط‌لولهٔ بیلد منتقل شد، به‌روزرسانی درون‌برنامه‌ای حذف شد، بخش انتخاب کشور حذف شد و انتخاب اندپوینت به خود هسته سپرده شد و مشکلات عملکردی و سازگاری نسخهٔ ۱.۲.۱ از ریشه رفع گردید.

**⚡ این نسخه را مستقیماً روی ۱.۲.۱ نصب کنید — بدون حذف برنامه و با حفظ تمام تنظیمات.**

### 🔄 ارتقای خودکار هسته در CI
- در هر بیلد، مخزن رسمی هسته بررسی و در صورت وجود نسخهٔ جدیدتر، هسته به‌صورت خودکار ارتقا می‌یابد (مرجع پایدار: **۱.۴**).
- پچ‌های اختصاصی برنامه حفظ می‌شوند و در صورت تعارض، هشدار صریح صادر می‌شود.
- اگر گیت‌هاب در دسترس نباشد، بیلد با همان هستهٔ موجود ادامه می‌دهد.
- پس از ارتقا، CI خودش بخش تغییرات ردمی را ویرایش و کامیت می‌کند؛ نسخهٔ هسته در بخش «درباره» قابل مشاهده است.

### 🗑️ حذف سیستم به‌روزرسانی درون‌برنامه‌ای
> به منظور ارتقای امنیت کاربران، شفاف‌سازی کامل و اطمینان از اصالت کدهای دریافتی، قابلیت به‌روزرسانی مستقیم درون‌برنامه‌ای حذف گردید. از این پس تمامی به‌روزرسانی‌ها صرفاً از طریق صفحه رسمی انتشار (Release) در گیت‌هاب پروژه به صورت امضاشده و رسمی قابل دریافت خواهند بود تا از هرگونه دانلود ناخواسته از منابع ناشناس جلوگیری شود.

ماژول بررسی به‌روزرسانی، کارت نصب درون‌برنامه، مجوز `REQUEST_INSTALL_PACKAGES` و `FileProvider` کاملاً حذف شدند. **برنامه دیگر توانایی دانلود یا نصب هیچ فایلی را ندارد.**

### 🌐 بدون لیست سرور و لوکیشن — انتخاب اندپوینت برعهدهٔ خود هسته

این برنامه نه لیست کشور دارد و نه لیست سرور؛ واقعاً هم هیچ‌وقت نداشته است. شما را به **شبکهٔ WARP کلاودفلر** وصل می‌کند که آدرس‌هایش **anycast** هستند: یک آدرس یکسان هم‌زمان از همهٔ دیتاسنترهای کلاودفلر در دنیا اعلام می‌شود و اینکه کدام دیتاسنتر جواب بدهد را مسیریابی اپراتور شما تعیین می‌کند، نه برنامه. پس منوی انتخاب کشور فقط یک برچسب بود و عملاً شما را به آن کشور نمی‌برد؛ به همین دلیل در این نسخه کاملاً حذف شد.

- **هسته خودش اندپوینت را انتخاب می‌کند.** رنج‌های داخلی WARP را اسکن و اندازه‌گیری می‌کند و به بهترین لبه‌ای که در آن لحظه روی شبکهٔ شما جواب بدهد وصل می‌شود.
- **Smart Auto کار اصلی خودش را انجام می‌دهد:** تشخیص وضعیت DPI شبکه و انتخاب پروتکل و مبهم‌سازی مناسب.
- **تنظیمات دستی شما همچنان اولویت دارد.** اندپوینت دستی (`ip:port`) یا رنج اختصاصی در تنظیمات، دقیقاً مانند گذشته اسکن را پین می‌کند و quick-reconnect هم عادی کار می‌کند.
- **بدون فیلتر اجباری آی‌پی.** اجباری‌کردن خروجی غیرایرانی هم در عمل جواب نمی‌دهد؛ نسخهٔ آزمایشی «هرگز ایران» روی شبکهٔ واقعی ایران تقریباً همهٔ لبه‌ها را رد می‌کرد و برنامه تا شکست نهایی در حلقه می‌ماند، پس حذف شد. اولویت با پایداری اتصال است.

### ⚡ پایداری، منابع و امضا
- **رفع نشت حافظه**: بافر حلقوی ۸۰۰ خطی، به‌روزرسانی UI محدودشده، نوشتن دسته‌ای در پس‌زمینه و فایل لاگ چرخشی ۵۱۲ کیلوبایتی.
- **کاهش چشمگیر مصرف CPU**: ناظر سرویس به‌جای پویش هر دو ثانیه، روی خود پروسهٔ هسته بلاک می‌شود.
- حذف انتظار فعال پس از اتصال، استخر نخ مشترک برای پراب‌ها و پویش پورت تطبیقی.
- **رفع تداخل با v2rayNG**: پورت‌های اشتراک‌گذاری از ۱۰۸۰۸/۱۰۸۰۹ به **۱۰۸۱۰/۱۰۸۱۱** منتقل شد و همسایه‌های شناخته‌شده در پیام خطا نام‌برده می‌شوند.
- **حل ریشه‌ای مشکل امضا**: CI اثر انگشت SHA-256 امضاکننده را پین می‌کند و در صورت تغییر، بیلد را متوقف می‌کند؛ بنابراین نصب روی ۱.۲.۱ تضمین شده است.

- **جابه‌جایی بین پروتکل‌ها دیگر برنامه را معطل نمی‌کند.** قطع اتصال و سپس اتصال روی پروتکلی دیگر (روی همهٔ پروتکل‌ها به‌جز اسمارت) این حس را می‌داد که «خیلی دیر راه می‌افتد». دو اشکال واقعی باعثش بود. اول اینکه توقف هسته «بفرست و فراموش کن» بود: برنامه فقط سیگنال خاتمه را می‌فرستاد و بلافاصله جلو می‌رفت، بنابراین هستهٔ قبلی اغلب هنوز زنده بود و پورت محلی <span dir="ltr">`127.0.0.1:1819`</span> را در اختیار داشت در حالی که هستهٔ بعدی داشت اجرا می‌شد؛ تلاش جدید یا نمی‌توانست پورت را بگیرد یا خودش را روی سوکتِ در حال مرگ راستی‌آزمایی می‌کرد و مجبور به تایم‌اوت و تلاش دوباره می‌شد. اکنون واقعاً منتظر خاتمهٔ پروسه می‌مانیم (و در صورت نیاز آن را به‌اجبار می‌بندیم) و تلاش تازه پیش از شروع، منتظر آزاد شدن پورت محلی می‌ماند. دوم اینکه اگر دکمهٔ اتصال در حالی زده می‌شد که نشست قبلی هنوز کاملاً جمع نشده بود، آن درخواست **بی‌صدا دور انداخته می‌شد** («یک اجرا فعال است ← خروج») و این دقیقاً همان حالت «زدم کانکت ولی مدتی هیچ اتفاقی نیفتاد» است؛ حالا نشست جدید کنترل را به دست می‌گیرد، منتظر پایان نشست قبلی می‌ماند، آن را برمی‌چیند و بی‌درنگ شروع می‌کند.
- **مسک (و هر پروتکلی که دستی انتخاب شود) یک فرصت دوم واقعی می‌گیرد.** پروتکلی که کاربر دستی انتخاب می‌کرد فقط **یک** تلاش داشت، آن هم با کل بودجهٔ اسکنِ حالت انتخاب‌شده — تا ۱۵۰ ثانیه در Balanced و ۳۰۰ ثانیه در Thorough — و هیچ جایگزینی نداشت؛ در حالی که حالت اسمارت یک نردبان از تلاش‌های کوتاه و مقاوم‌شده را طی می‌کند. روی شبکه‌ای که UDP/QUIC را محدود می‌کند، نتیجه این بود که کاربر دقایقی روی «در حال اتصال» می‌ماند و بعد شکست می‌خورد. حالا پروتکل انتخابی ابتدا یک پاس با سقف زمانی و دقیقاً با تنظیمات خودتان اجرا می‌شود و اگر موفق نشد، **همان پروتکل** یک بار دیگر با مقاوم‌سازی ضد DPI (روشن‌کردن مبهم‌سازی، و برای مسک: HTTP/2 و قطعه‌قطعه‌سازی TLS و ECH) با بودجهٔ کامل تکرار می‌شود. پروتکلی که انتخاب کرده‌اید هرگز با پروتکل دیگری عوض نمی‌شود.
- **قطع اتصال دوباره فوری شد (رفع ریشه‌ای معطلی ۳۰ تا ۵۰ ثانیه‌ای).** با زدن دکمهٔ قطع، دکمه روی «در حال قطع…» تا یک دقیقه روی همهٔ پروتکل‌ها می‌ماند. ریشهٔ مشکل: ناطر سرویس روی پروسهٔ هسته با <span dir="ltr">`Process.waitFor`</span> پارک می‌شود که یک فراخوانی **مسدودکننده** جاواست و لغو کردن کوروتین نمی‌تواند آن را قطع کند. مسیر خاموشی اول از نشست می‌خواست تمام شود و *منتطر پایان آن می‌ماند پیش از اینکه چیزی را بکشد*؛ پس تا پایان کل پنجرهٔ ۶۰ ثانیه‌ای در همان انتطار می‌ماند (در لاگ، توقف هسته دقیقاً ۶۰ ثانیه پس از اتصال ثبت شده، نه لحطه‌ای که دکمه زده شد). دو اصلاح این را برای همیشه حل کرد: انتطار برای هسته اکنون قابل وقفه است و در حد میلی‌ثانیه لغو می‌شود، و ترتیب خاموشی معکوس شد: لغو، بلافاصله کشتن هسته و تونل، رفتن فوری رابط کاربری به حالت بی‌کار، و فقط *پس از آن* جمع‌کردن کوروتین تمام‌شده در پس‌زمینه. اتصال مجدد روی پروتکلی دیگر هم از همین ترتیب پیروی می‌کند، پس دیگر انتطار نشست قبلی را به ارث نمی‌برد. توقف هسته نیز بعد از ۲۵۰ میلی‌ثانیه به کشتن قطعی می‌رسد به‌جای چند ثانیه انتطار.
- **خط لولهٔ انتشار دیگر با فایل‌های جامانده از ۱.۲.۱ خراب نمی‌شود.** ریختن ۱.۲.۲ روی مخزن ۱.۲.۱ فایل‌های تغییریافته را جایگزین می‌کند اما فایل‌هایی را که ۱.۲.۲ *حذف* کرده (آپدیتر درون‌برنامه‌ای، انتخاب لوکیشن، سیاست اجباری خروج) پاک نمی‌کند. آن فایل‌های یتیم هنوز به نمادها و رشته‌های حذف‌شده ارجاع می‌دادند و به همین دلیل <span dir="ltr">`compileReleaseKotlin`</span> با خطاهای <span dir="ltr">`Unresolved reference 'GITHUB_REPO' / 'update_available'`</span> شکست می‌خورد — دقیقاً همین علت است که همین کد در مخزن تازه بدون مشکل بیلد می‌شد و در مخزن اصلی خطا می‌داد. اکنون خود بیلد آن‌ها را پاک می‌کند: <span dir="ltr">`scripts/purge-stale-sources.sh`</span> هر مسیر فهرست‌شده در <span dir="ltr">`.github/removed-sources.txt`</span> را پیش از کامپایل حذف می‌کند، پاکسازی را به شاخه کامیت می‌کند، و علاوه بر آن اگر هر فایل کاتلینی به رشته‌ای ناموجود ارجاع داده باشد، در یک ثانیه با نام فایل و شمارهٔ خط خطا می‌دهد.
- **امضای نسخهٔ ۱.۲.۱ اکنون توسط خود بیلد محافظت می‌شود.** اندروید فقط وقتی به‌روزرسانی را نصب می‌کند که امضای نسخهٔ قدیم و جدید یکی باشد؛ پس کلید هرگز نباید عوض شود. دو محافظ اضافه شد: بیلد در مخزنی که قبلاً با کلید CI منتشر کرده، دیگر کلید جدید نمی‌سازد (با پیام صریح «فایل <span dir="ltr">`.github/ci-keystore.jks.b64`</span> را برگردانید» متوقف می‌شود به‌جای ساختن خاموشانهٔ APK غیرقابل‌نصب)، و اثر انگشت امضاکننده اکنون در حالت کلید CI هم پین و اعمال می‌شود، مخزن‌به‌مخزن (<span dir="ltr">`.github/expected-signer-ci.txt`</span>). اگر گواهی با نسخهٔ قبلی فرق داشته باشد، بیلد فیل می‌شود و منتشر نمی‌کند. مخزن آزمایشی مقدار خودش را پین می‌کند و روی مخزن اصلی اثری ندارد.
- **انیمیشن شفق حذف شد؛ پس‌زمینه اکنون یک رنگ ساده و ساکن است.** سه گرادیان شعاعی بزرگ، تا وقتی \برنامه باز بود، تمام‌صفحه پشت همهٔ صفحه‌ها ترکیب می‌شدند و حتی با کاهش نرخ ترسیم هم هنوز سهم قابل‌توجهی از GPU و CPU را می‌گرفتند. اکنون پس‌زمینه یک پرِ رنگی ساکن است که هرگز بازترسیم نمی‌شود — دیگر هیچ انیمیشنی پشت رابط کاربری اجرا نمی‌شود و کل بودجهٔ فریم در اختیار منوها، شیت‌ها و صفحهٔ اتصال است.
- **منوی اصلی وقتی بسته است دیگر کار نمی‌کند.** کشوی ناوبری اندروید محتوای خود را حتی در حالت بسته هم می‌سازد؛ بنابراین کارت‌های تشخیص، اشتراک‌گذاری، تنظیمات پیشرفته و درباره همیشه زنده بودند و با هر تغییر تنظیمات و هر خط لاگ هسته دوباره ساخته می‌شدند، آن هم پشت پنلی که کسی نگاهش نمی‌کرد. اکنون فقط زمانی ساخته می‌شوند که کشو واقعاً باز باشد.
- **لاگ تشخیصی فقط وقتی باز است گوش می‌دهد.** هسته هنگام اسکن صدها خط لاگ تولید می‌کند؛ پیش‌تر هر خط، کل کارت تشخیص — و کشوی اطرافش — را بازسازی می‌کرد، حتی وقتی کنسول لاگ بسته بود. حالا فقط کنسول بازشده مشترک لاگ است.
- **شیت «تنظیمات پیشرفته» فوری باز می‌شود.** حدود ۴۰ کنترل این بخش در همان فریمی چیده می‌شدند که انیمیشن باز شدن شیت شروع می‌شد و همین باعث لگ محسوس در باز شدن بود. اکنون ابتدا شیت باز می‌شود و بلافاصله بعد، محتوا داخلش قرار می‌گیرد.

### 🔒 ممیزی امنیتی
این پروژه برای نسخهٔ ۱.۲.۲ تحت **ممیزی امنیتی ۱۰۰ درصدی و خط‌به‌خط** قرار گرفت و آسیب‌پذیری‌های بحرانی بر اساس استانداردهای ممیزی موبایل رفع شدند. گزارش کامل: `docs/SECURITY_AUDIT_1.2.2.md`

</div>
