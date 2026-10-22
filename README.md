# App Scheduler (prototype)

Kisi bhi app ko roz ek waqt par launch karna aur (optional) doosre waqt par close karna. Android.

## APK download

Har push par GitHub Actions APK banata hai. Mobile par ye page kholo aur sab se naya release ka `app-debug.apk` download karo:

https://github.com/itservices-svg/app-scheduler/releases

Is APK ki signing key fixed hai, to nayi version purani ke upar install ho jati hai aur permissions bani rehti hain.
(Sirf pehli baar jab key badli hai, purani app ek baar uninstall karni parti hai.)

## Features

| Feature | Detail |
|---|---|
| Launch | Schedule ke waqt app khulti hai (phone lock ho to bhi lock screen ke peeche khul jati hai) |
| Close (optional) | Close nahi / Soft close / Force stop, har schedule ke liye alag |
| Alarm | App khulte hi alarm bajta hai: default beep ya phone ka koi bhi alarm sound. 30 second tak ya Stop dabane tak |
| Lock screen | Screen on hoti hai aur system ka unlock prompt (fingerprint/PIN) aata hai. Unlock hote hi app samne aa jati hai |

### Close modes

- **Soft close (recommended):** pehle Home par jata hai, phir system se app ke background processes khatam karwata hai. App Info screen ya dialog nahi dikhta. Agar app kisi foreground service ki wajah se chalti rehti hai to ye usse band nahi kar sakta.
- **Force stop:** App Info screen kholkar "Force stop" dabata hai (Accessibility chahiye, Settings English mein honi chahiye). Zyada pakka hai magar screen kuch second dikhti hai.

### Lock screen ke baare mein

Android kisi app ko PIN/pattern/fingerprint khud nahi dalne deta, to phone **khud unlock nahi ho sakta**. App sirf screen on karke unlock prompt dikhati hai, user ko fingerprint/PIN dena parta hai. Phone khud unlocked rakhna ho to Android ki Smart Lock (trusted place/device) use karo, ya lock "Swipe" par rakho.

## Mobile par setup (pehli baar)

App kholo. Upar buttons hain:

| Button | Kya karna hai |
|---|---|
| Exact alarm | "Alarms & reminders" allow karo |
| Overlay | "Display over other apps" allow karo (background se app kholne ke liye) |
| Accessibility | App Scheduler service ON karo. Android 13+ par pehle App info -> menu (⋮) -> **Allow restricted settings** |
| Battery | App ko "Unrestricted" par rakho. Autostart bhi ON karo (OnePlus: Settings -> Battery -> Battery optimization) |
| Alarm stop | Bajta hua alarm band karta hai |

Pehli baar notification permission bhi maangi jati hai, allow kar do (alarm ka Stop button wahi dikhta hai).
Status line (`OK` / `NAHI`) bata degi kaun si permission baqi hai.

## Test

"+ Naya schedule" -> app -> launch time -> close mode -> (close time) -> alarm. Phir us row ka **Test** dabao:
5 second baad app khulegi (alarm ke saath), 25 second baad band hogi (agar close on hai).

Schedule badalna ho to Delete karke naya banao.

## Maloom limitations

- Force stop ke liye phone ki Settings **English** mein honi chahiye.
- Kuch phones battery/autostart ki wajah se alarm miss kar sakte hain.
- Prototype hai: Play Store ke liye accessibility/exact alarm ki approval alag mushkil hai.
