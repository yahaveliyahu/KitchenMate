# 🍳 KitchenMate
KitchenMate is a smart Android app for adapting recipes based on the user's available ingredients.
The app allows for intelligent search, scanning, saving, and management of recipes, while integrating external sources and advanced recognition capabilities.

---

## ✨ Main Features
- Ingredient matching against your pantry items
- Recipe Management - User-generated recipes + admin approval workflow (pending → recipes)
- GET integration with Spoonacular to enrich results / fallback when Firestore has no good match
- On-device ML model: recognize fruits & vegetables from the camera
- Barcode scanner for fast item entry (ZXing)
- OCR + PDF/TXT import to capture recipes without typing
- Personal favorites (❤️)recipes tracking
- Images stored in Firebase Storage
- Android (Kotlin), Firebase (Auth/Firestore/Storage), Retrofit/OkHttp, Glide
- Viewing history – Displays the latest recipes the user has opened and saving time stamp
- Finding recipes by searching for the recipe name

  ---

## 🎮 Watch the App in Action

▶️ [Click here to watch the video](https://streamable.com/493zsk)

---

## 🛠 Technologies used

- **Development language**: Kotlin
- **Database**: Firebase Firestore
- **Image storage**: Firebase Storage
- **External APIs**: Spoonacular, OpenFoodFacts
- **Machine Learning**: MobileNet v2 + TensorFlow Lite
- **OCR**: ML Kit / Tesseract OCR
- **UI**: Android XML Layouts, Glide for loading images

  
