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

### Lock screen aur PIN unlock

- **Unlock PIN/Password button:** apna phone ka PIN ya password (letters + numbers dono chalte hain) yahan save karo. Number PIN ho to PIN pad ke digits dabte hain, password ho to password field mein text set hota hai. Ye PIN sirf isi phone par Android Keystore se encrypt hokar rehta hai, kahin upload nahi hota. Khali Save karne se PIN hat jata hai.
- Schedule ke waqt phone lock ho to app screen on karti hai, Accessibility se swipe-up karke PIN pad ke digits dabati hai, phir target app kholti hai.
- Ye sirf **PIN** ke liye hai. Fingerprint, face ya pattern automate nahi ho sakte. Pattern/fingerprint wale phone par "Phone lock ho to unlock prompt dikhao" switch ON rakho: screen on hogi aur tum khud unlock karoge.
- Accessibility service ON honi zaroori hai. PIN pad dhoondhne ke liye ye OnePlus (OxygenOS) ka standard PIN screen assume karti hai. Kaam na kare to batao.
- Phone ka PIN save karna security risk hai: koi tumhara phone khole aur app ke andar jaye to PIN badal/dekh sakta hai (app PIN dikhati nahi, magar isi phone par saved hai). Sirf apne personal phone par use karo.

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

## Lock screen unlock test

1. App update ke baad **Accessibility ko ek baar OFF karke dobara ON karo** (nayi permission tab hi lagti hai).
2. **Unlock PIN/Password** button se apna password save karo (status mein "saved" aana chahiye).
3. Schedule ke row par **Lock test** dabao, phir foran power button se phone lock kar do.
4. 20 second baad screen on hogi, unlock screen aayegi, password khud dalega aur app khulegi.

Kaam na kare to screen par jo toast likha aaye wo mujhe bata do.

## Maloom limitations

- Force stop ke liye phone ki Settings **English** mein honi chahiye.
- Kuch phones battery/autostart ki wajah se alarm miss kar sakte hain.
- Prototype hai: Play Store ke liye accessibility/exact alarm ki approval alag mushkil hai.
