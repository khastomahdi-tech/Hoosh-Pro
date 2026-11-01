# ⚡ هوش پرو — Hoosh Pro

دستیار هوشمند اندروید با **رابط کاربری دقیقاً شبیه ChatGPT** (تیره، راست‌چین، فارسی):
گفتگوی زنده‌ی استریمی + **ورودی صوتی آفلاین فارسی** + **تحلیل عکس روی خود دستگاه** + تولید عکس — اشتراک کاملاً رایگان.

## امکانات
- 💬 **چت زنده (استریم)** — پاسخ کلمه‌به‌کلمه تایپ می‌شود، دقیقاً حس ChatGPT
- 🎤 **ورودی صوتی** — ضبط با میکروفون، تبدیل گفتار به متن **آفلاین** (Vosk فارسی)، متن پردازش و پاسخ داده می‌شود
- 📷 **تحلیل عکس** — عکس از گالری → OCR لاتین + OCR فارسی (Tesseract) + برچسب‌گذاری اشیاء (ML Kit) روی خود دستگاه → داده‌ها به هوش مصنوعی
- 🎨 **تولید عکس** — از توضیح فارسی، با سه نسبت ۱:۱ / ۹:۱۶ / ۱۶:۹ (قابل ذخیره در گالری)
- 🔐 **ورود با ایمیل** — نشست کاربر در دیتابیس سرور (Cloudflare D1) ثبت و ذخیره می‌شود
- 🗂 **کشوی کناری ChatGPT** — لیست گفتگوها، حذف با لمس طولانی، «گفتگوی جدید»، پروفایل + نشان «اشتراک رایگان ✦»
- 📄 **تولید فایل** — لمس طولانی روی پاسخ → کپی / ذخیره‌ی فایل متنی در Downloads / اشتراک‌گذاری
- ✨ پیشنهادهای شروع (۶ کارت)، رندر مارک‌داون، نشانگر «در حال نوشتن»، پالت تیره‌ی ChatGPT (#212121)

## معماری
| بخش | فناوری |
|---|---|
| اپ | Kotlin + Vosk (صدا) + ML Kit (بینایی) + Tesseract (OCR فارسی) |
| بک‌اند | Cloudflare Pages Worker → [`hoosh-api.pages.dev`](https://hoosh-api.pages.dev) |
| دیتابیس | Cloudflare D1 (`hoosh-db` — جدول `hoosh_users`) |
| موتور گفتگو | Pollinations — استریم SSE از سرور (مسیر پشتیبان: تماس مستقیم دستگاه) |
| موتور عکس | Pollinations FLUX + ترجمه‌ی خودکار پرامپت فارسی |

## API سرور
```
POST /api/login  {email}                                   → {ok, token, uid, name}
POST /api/chat        {email, token, messages:[{role,content}]} → {ok, reply}
POST /api/chat/stream {email, token, messages:[{role,content}]} → SSE (پاسخ زنده)
POST /api/img    {email, token, prompt, ratio}             → image/jpeg
GET  /           صفحه‌ی وضعیت سرویس
```

## ساخت APK
هر push روی `main` در GitHub Actions بیلد و امضا می‌شود و APK در **Releases** منتشر می‌گردد:
```
https://github.com/khastomahdi-tech/Hoosh-Pro/releases/latest
```
