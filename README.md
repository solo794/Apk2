# 💰 متابعة مصاريفي — تطبيق أندرويد (APK)

تطبيق أندرويد حقيقي (APK) لمتابعة المصاريف الشهرية، مبني بتغليف نفس واجهة الويب
(`www/index.html`) داخل تطبيق أندرويد أصلي باستخدام [Capacitor](https://capacitorjs.com).

كل منطق التطبيق (الحسابات، التبويبات، التخزين، التصدير) موجود في ملف واحد:
`www/index.html`. مجلد `android/` هو مشروع أندرويد أصلي (Gradle) بيتفتح مباشرة
في Android Studio أو بيتبني من سطر الأوامر / GitHub Actions.

---

## 🆕 إيه اللي اتغيّر عن نسخة الويب

نسخة الويب الأصلية كانت بتعتمد على تحميل الملفات (`<a download>` + Blob URLs)،
وده مش بيشتغل جوه WebView الأندرويد. اتضافت طبقة توافق بسيطة:

- `saveOrShareFile()` في نهاية `<script>` — لو التطبيق شغال كـ APK حقيقي
  (`Capacitor.isNativePlatform()`)، بيكتب الملف عن طريق `@capacitor/filesystem`
  وبعدين يفتح شاشة المشاركة الأصلية (`@capacitor/share`) عشان تحفظه فين ما تحب
  (الملفات، درايف، واتساب...). لو شغال في متصفح عادي، برضو بيرجع لطريقة التحميل
  القديمة (نفس الملف يشتغل كصفحة ويب برضو لو حبيت).
- زرار الرجوع في الأندرويد (`backButton`) بيقفل أي مودال مفتوح، وبعدين يرجع
  لتبويب الرئيسية، وبعدين يقفل التطبيق (`@capacitor/app`).
- شريط الحالة (Status Bar) اتلوّن بلون هوية التطبيق (`@capacitor/status-bar`).
- أيقونة وشاشة بداية (Splash) بلوان التطبيق (`resources/` + `@capacitor/assets`).

كل حاجة تانية (التبويبات، الحسابات، الفئات، التخزين المحلي...) زي ما هي بالظبط.

---

## 📦 بناء الـ APK

### الطريقة الأسهل: GitHub Actions (مفيش حاجة تتثبت على جهازك)

في كل `push` لفرع `main`/`master`، أو يدويًا من تبويب **Actions**، الووركفلو
`.github/workflows/build-android.yml` بيبني **Debug APK** ويرفعه كـ Artifact
تقدر تنزّله مباشرة (اسمه `masarifi-debug-apk`) وتثبته على موبايلك مباشرة
(محتاج تسمح بـ"تثبيت من مصادر غير معروفة" في إعدادات الأندرويد أول مرة).

### محليًا (لازم Android Studio / Android SDK مُثبت على جهازك)

```bash
npm install
npx cap sync android

# Debug APK (للتجربة، متوقّع تثبيته مباشرة)
cd android && ./gradlew assembleDebug
# الناتج: android/app/build/outputs/apk/debug/app-debug.apk

# أو افتح المشروع في Android Studio مباشرة
npx cap open android
```

> ⚠️ هذه البيئة (سيشن Claude) مفيهاش Android SDK ومفيش وصول لسيرفرات
> `dl.google.com`، فمش قادرة تبني الـ APK محليًا بنفسها — عشان كده الاعتماد
> على GitHub Actions (اللي عنده إنترنت كامل) أو جهازك الشخصي.

### بناء نسخة Release (موقّعة، جاهزة للتوزيع خارج Google Play)

1. أنشئ Keystore (مرة واحدة فقط، واحتفظ بيه في مكان آمن — لو ضاع مينفعش تحدّث
   التطبيق تاني):
   ```bash
   keytool -genkeypair -v -keystore masarifi-release.keystore \
     -alias masarifi -keyalg RSA -keysize 2048 -validity 10000
   ```
2. اربطه في `android/app/build.gradle` (قسم `signingConfigs`) أو مرّر بيانات
   التوقيع عن طريق متغيرات بيئة في CI — راجع
   [توثيق Capacitor عن التوقيع](https://capacitorjs.com/docs/android/deploying-to-google-play#generating-a-signing-key).
3. `cd android && ./gradlew assembleRelease`

---

## 🧩 هيكل المشروع

```
www/index.html         ← التطبيق كامل (HTML+CSS+JS) — ده اللي بيتعدّل لو غيّرت أي فيتشر
capacitor.config.json  ← إعدادات Capacitor (اسم التطبيق، الـ App ID)
android/                ← مشروع أندرويد الأصلي (Gradle)
resources/              ← صور الأيقونة وشاشة البداية المصدرية
scripts/gen-icon.js     ← سكريبت بايثون/نود بسيط ولّد الأيقونة بألوان الهوية
.github/workflows/      ← بناء تلقائي للـ APK عبر GitHub Actions
```

- **App ID:** `com.salman.masarifi`
- **اسم التطبيق:** متابعة مصاريفي

---

## ⚠️ قيود معروفة (زي ما هي من نسخة الويب)

1. **البيانات محلية على الجهاز فقط** (`localStorage` داخل الـ WebView) — مفيش
   مزامنة تلقائية بين الأجهزة؛ استخدم "تصدير/استيراد نسخة احتياطية" من تبويب
   الإعدادات لنقل البيانات.
2. **مفيش قراءة تلقائية لرسائل البنك (SMS)** — محتاجة صلاحية `READ_SMS` وكود
   أصلي إضافي (Capacitor plugin مخصص)؛ مش موجودة في النسخة الحالية.
3. **تصدير الإكسيل محتاج إنترنت أول مرة** لتحميل مكتبة SheetJS من الإنترنت.
4. **التطبيق مش على Google Play** — الـ APK الناتج بيتثبت مباشرة (Sideload)،
   مش موزّع رسميًا؛ لو حابب تنزله على Google Play محتاج حساب مطوّر + نسخة
   Release موقّعة (App Bundle `.aab`).
