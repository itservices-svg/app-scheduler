# App Scheduler (prototype)

Kisi bhi app ko roz ek waqt par launch karna aur doosre waqt par close karna (Android).

## APK kaise banegi (laptop/Android Studio ke baghair)

1. github.com par naya repository banao (naam kuch bhi, e.g. `app-scheduler`).
2. Is zip ke andar ki **saari files** (hidden `.github` folder samet) repo mein upload karo.
   - Agar `.github` folder upload na ho: repo mein **Add file -> Create new file** dabao, naam mein
     `.github/workflows/build.yml` likho aur `build.yml` ka content paste kar do.
3. Repo ke **Actions** tab mein jao, "Build APK" run hone do (2-5 minute).
4. Run khatam hone par neeche **Artifacts** mein `app-debug-apk` download karo, zip kholo, `app-debug.apk` mobile par install karo
   ("Install unknown apps" allow karna padega).

Agar build fail ho to Actions ka error log copy karke mujhe bhej do, main fix kar dunga.

## Mobile par pehli baar setup

App kholo. Upar 4 buttons hain:

| Button | Kya karna hai |
|---|---|
| Exact alarm | "Alarms & reminders" allow karo |
| Overlay | "Display over other apps" allow karo (background se app kholne ke liye) |
| Accessibility | App Scheduler service ON karo (app band karne ke liye). Android 13+ par pehle App info -> menu (⋮) -> **Allow restricted settings**, phir Accessibility ON |
| Battery | App ko "Unrestricted" / no restrictions par rakho. Xiaomi/Oppo/Vivo par Autostart bhi ON karo |

Status line (`OK` / `NAHI`) bata degi kaun si permission baqi hai.

## Test

"+ Naya schedule" -> app chuno -> launch time -> close time. Phir us row ka **Test** dabao:
5 second baad app khulegi, 25 second baad band hogi. Pehle Test se hi check kar lo, phir asli waqt lagao.

## Maloom limitations

- Close karne ke liye phone ki Settings **English** mein honi chahiye (button text "Force stop" / "OK" dhoondha jata hai).
- Close ke waqt App Info screen kuch second ke liye nazar aayegi, phir Home par wapas.
- Kuch phones (Xiaomi, Oppo, Vivo, Samsung ke kuch models) battery/autostart ki wajah se alarm miss kar sakte hain.
- Ye prototype hai: Play Store ke liye accessibility/exact alarm ki approval alag mushkil hai.
