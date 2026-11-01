# ⚡ هوش پرو — Hoosh Pro

دستیار هوشمند اندروید: **گفتگوی هوشمند بدون محدودیت + تولید عکس از توضیح فارسی**، با رابط کاربری تیره‌ی پیشرفته و راست‌چین.

## امکانات
- 🔐 **ورود با ایمیل** — نشست کاربر در دیتابیس سرور (Cloudflare D1) ثبت می‌شود و باقی می‌ماند
- 💬 **چت هوشمند** — پاسخ هر سؤال به هر زبانی (فارسی، انگلیسی و…) با حافظه‌ی مکالمه
- 🎨 **تولید عکس** — از توضیح فارسی کاربر، عکس ساخته می‌شود (قابل ذخیره در گالری)
- 🗂 **حافظه‌ی گفتگوها** — تاریخچه در دیتابیس محلی؛ ادامه‌ی گفتگوی قبلی، حذف یا پاک‌سازی
- 📄 **تولید فایل** — ذخیره‌ی پاسخ‌ها به‌عنوان فایل متنی در Downloads
- ✨ رندر مارک‌داون (بولد، لیست، کد)، نشانگر تایپ متحرک، انیمیشن‌های نرم، فونت وزیر

## معماری
| بخش | فناوری |
|---|---|
| اپ | Kotlin — بدون هیچ وابستگی خارجی (فقط Android SDK) |
| بک‌اند | Cloudflare Pages Worker → [`hoosh-api.pages.dev`](https://hoosh-api.pages.dev) |
| دیتابیس | Cloudflare D1 (`hoosh-db` — جدول `hoosh_users`) |
| موتور گفتگو | Pollinations (مسیر اصلی: اتصال مستقیم دستگاه + مسیر پشتیبان: پراکسی سرور با retry) |
| موتور عکس | Pollinations FLUX + ترجمه‌ی خودکار پرامپت فارسی |

## API سرور
```
POST /api/login  {email}                                   → {ok, token, uid, name}
POST /api/chat   {email, token, messages:[{role,content}]} → {ok, reply}
POST /api/img    {email, token, prompt, ratio}             → image/jpeg
GET  /           صفحه‌ی وضعیت سرویس
```

## ساخت APK
هر push روی `main` در GitHub Actions بیلد و امضا می‌شود و APK در **Releases** منتشر می‌گردد:
```
https://github.com/khastomahdi-tech/Hoosh-Pro/releases/latest
```
