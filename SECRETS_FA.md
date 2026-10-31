# راهنمای Secrets و اجرای بیلد UC MAX

## کارهایی که باید دستی انجام دهی (فقط یک‌بار)

۱. **ساخت توکن GH_PAT**
   - GitHub → Settings → Developer settings → Personal access tokens → Fine-grained token
   - Repository access: فقط همین ریپو
   - Permissions: Secrets → Read and write
   - توکن را کپی کن.

۲. **ذخیره توکن در ریپو**
   - ریپو → Settings → Secrets and variables → Actions → New repository secret
   - Name: `GH_PAT` و Value: همان توکن.

۳. **اجرای ساخت keystore**
   - تب Actions → Keystore bootstrap → Run workflow
   - بعد از اتمام، secrets زیر خودکار ساخته می‌شوند: `KEYSTORE_BASE64`، `KEYSTORE_PASSWORD`، `KEY_PASSWORD`، `KEY_ALIAS`.
   - فایل پشتیبان `uc_max.jks` را از بخش Artifacts همان اجرا دانلود کن و **در جای امن نگه دار**. این artifact فقط ۱ روز می‌ماند. اگر keystore را گم کنی، دیگر نمی‌توانی روی نسخه‌های منتشرشده آپدیت بدهی.
   - بعد از ذخیره، فایل artifact را حذف کن.

۴. **حذف GH_PAT** (اختیاری و توصیه‌شده)
   - بعد از ساخت keystore، `GH_PAT` را از Secrets حذف کن.

## بیلد و انتشار
- Actions → Build UC MAX → Run workflow، یا با ساختن تگ `v1.0.0` و push کردن آن.
- سه APK جدا تولید می‌شوند: `arm64-v8a`، `armeabi-v7a` و `universal` (شامل هر دو).
- با تگ، APKها خودکار در Releases منتشر می‌شوند.

## نکته درباره Psiphon (بخش بعدی)
برای ادغام shirokhorshid باید دو مورد دیگر هم آماده شود، که در این مرحله هنوز انجام نشده است:
- فایل `ca.psiphon.aar` (هسته تونل Psiphon) که در ریپوی tunnel-core ساخته می‌شود.
- مقادیر `PSIPHON_PROPAGATION_CHANNEL_ID` و `PSIPHON_SPONSOR_ID` که مال خودت باید باشند. این دو به Secrets اضافه می‌شوند.

## لیست سرور داخلی Psiphon (اختیاری ولی لازم برای اتصال در شرایط فیلتر)
- Secret جدید: `PSIPHON_EMBEDDED_SERVER_LIST` را در Settings → Secrets and variables → Actions بساز.
- مقدار: هر ورودی سرور Psiphon (رشتهٔ base64) را در یک خط یا با کاما جدا کن.
- اگر خالی باشد، بیلد ادامه پیدا می‌کند اما هشدار داده می‌شود و اتصال فقط به لیست راه‌دور (که ممکن است مسدود باشد) وابسته است.

## لیست سرور داخلی Psiphon (اختیاری ولی لازم برای اتصال در شرایط فیلتر)
- Secret جدید: `PSIPHON_EMBEDDED_SERVER_LIST` را در Settings → Secrets and variables → Actions بساز.
- مقدار: هر ورودی سرور Psiphon (رشتهٔ base64) را در یک خط یا با کاما جدا کن.
- اگر خالی باشد، بیلد ادامه پیدا می‌کند اما هشدار داده می‌شود و اتصال فقط به لیست راه‌دور (که ممکن است مسدود باشد) وابسته است.
