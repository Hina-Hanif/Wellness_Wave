# Wellness Wave - Detailed Implementation Plan

This plan focuses on a streamlined execution to get your ML-powered stress tracking app running quickly.

## 1. Phase 1: Android UI Finalization (CURRENT)
- **Onboarding:** Screen to explain why we need "Usage Access" permissions.
- **Manual Permission Intent:** Button to open system settings for usage stats.
- **Mood Input:** Slider (1-10) with local storage (DataStore).
- **Trends:** Simple chart showing daily stress levels.

## 2. Phase 2: FastAPI Backend & Firebase (NEXT)
- **Setup:** Create `wellness_wave_backend` folder.
- **FastAPI:** Hello world API with Pydantic validation.
- **Firestore:** Initialize connection to store daily logs.
- **Deployment:** For testing, we can run locally and use `ngrok` or similar to expose the URL to Android.

## 3. Phase 3: ML Workflow (The Prediction Engine)
### Data Collection (The 2-Week Phase)
- You cannot train a model without data.
- **Step 1:** Use the app yourself for 3 weeks to generate ~20 rows. 
- **Step 2:** Recruit 10 friends to use the app for 14 days.
- **Result:** You will have ~150-300 rows in Firestore.

### Training (Easy Workflow)
- **Export:** Export Firestore collection to `dataset.csv`.
- **Script:** A simple `.ipynb` or `.py` script using `scikit-learn`:
  ```python
  from sklearn.ensemble import RandomForestClassifier
  model = RandomForestClassifier()
  model.fit(X_train, y_train)
  joblib.dump(model, "stress_model.pkl")
  ```
- **Integrate:** Copy `stress_model.pkl` to the backend folder.

## 4. Phase 4: Full Integration
- Android sends daily JSON to Backend.
- Backend stores JSON in DB.
- Backend runs `model.predict(JSON)` and returns the stress score.
- Android shows the result instantly.

---

## Verification Steps
1. **App Level:** Can I navigate and enter mood?
2. **System Level:** Does my mood entry show up in Firestore?
3. **ML Level:** Does the API return a "Medium" prediction if I send high screen time?
