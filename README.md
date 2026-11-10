# BINANCE PRICE ALERT (Android)

Binance Spot USDT কয়েনের প্রাইস অ্যালার্ট অ্যাপ। শুধু Binance-এর পাবলিক WebSocket ডেটা ব্যবহার করে।
কোনো লগইন, API Key, ট্রেডিং বা ট্র্যাকিং নেই। সব অ্যালার্ট শুধু আপনার ফোনে থাকে।

## APK বানানোর ধাপ (Android Studio ছাড়া)

1. GitHub-এ নতুন Repository খুলুন (Private রাখতে পারেন)।
2. এই প্রজেক্টের **সব ফাইল ও ফোল্ডার** (`.github`, `app`, `build.gradle.kts`, `settings.gradle.kts`,
   `gradle.properties`, `.gitignore`, `README.md`) Repository-তে Upload করুন।
   ফোল্ডারের গঠন যেমন আছে ঠিক তেমন থাকতে হবে।
3. Branch-এর নাম `main` হতে হবে। Upload (Commit) করলেই Build নিজে শুরু হবে।
4. **Actions** ট্যাবে যান → **Build Android APK** → সবুজ টিক এলে ওপেন করুন।
   (হাতে চালাতে: Actions → Build Android APK → **Run workflow**)
5. পেজের নিচে **Artifacts** অংশ থেকে `BINANCE-PRICE-ALERT-debug-apk` ডাউনলোড করুন, ZIP খুলুন।
6. `app-debug.apk` ফোনে দিয়ে Install করুন (প্রয়োজনে "Install unknown apps" অনুমতি দিন)।

## প্রথমবার ব্যবহার

1. অ্যাপ খুললে Notification অনুমতি চাইবে → **Allow**।
2. Coins ট্যাবে কয়েন খুঁজুন, 🔔 চাপুন বা Add Alert ট্যাবে কয়েন ও Exact Target Price দিন।
3. Alert Save করলে Monitoring নিজে ON হয়ে যায়। Dashboard থেকেও ON/OFF করা যায়।
4. Settings → Battery optimization থেকে অ্যাপটিকে **Don't optimize** করুন।

## সীমাবদ্ধতা (সত্যি কথা)

- অ্যাপ Force Stop করলে, ফোন বন্ধ থাকলে বা ইন্টারনেট না থাকলে মনিটরিং চলবে না।
- কিছু ফোনে (Xiaomi, Oppo, Vivo, Huawei, Samsung) আলাদা Autostart/Background সেটিং চালু করতে হয়।
- ফোন রিস্টার্টের পর অ্যাপ নিজে চালু হওয়ার চেষ্টা করে, কিন্তু Android বা কোম্পানি তা আটকাতে পারে।
