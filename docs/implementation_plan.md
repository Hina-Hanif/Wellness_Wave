# Wellness Wave: Comprehensive Implementation Roadmap

This document provides a granular, step-by-step guide for building the Wellness Wave app. It serves as our master checklist for the entire development lifecycle.

---

## 🔷 STAGE 1 — UI & FOUNDATION (COMPLETED)
- [x] Create Core UI Screens (Home, Mood, Trends, Settings).
- [x] Implement Navigation Suite (Bottom Nav).
- [x] Create Onboarding Flow to introduce permissions.

---

## 🔷 STAGE 2 — ADVANCED BEHAVIORAL MONITORING (ACTIVE)
This stage involves building the native Android services to collect "silent" behavioral data without reading personal content.

### 2.1 — Usage Stats & App Switching
1. **Initialize UsageStatsManager:** Set up the service to query total screen time and app package foreground time.
2. **Implement App Categorization:** Map package names (e.g., `com.whatsapp`) to categories (Social, Productive, Entertainment).
3. **Log App Switches:** Calculate the frequency of transitions between different apps per hour.

### 2.2 — Accessibility Service (The Pulse Tracker)
1. **Create `BehavioralAccessibilityService`:** Monitor accessibility events.
2. **Typing Rhythm:** Calculate Characters Per Second (CPS) and backspace frequency (no text characters are stored).
3. **Scroll Analysis:** Calculate scroll velocity and directional erraticness (std dev of scroll movements).
4. **Data Privacy Guard:** Ensure zero-content logging (only metadata).

### 2.3 — Notification Reaction Tracker
1. **Implement `NotificationListenerService`:** Capture time of notification arrival.
2. **Log Interactions:** Measure time elapsed until the user either clicks or dismisses the notification.

---

## 🔷 STAGE 3 — BACKEND INFRASTRUCTURE (PHASE 2 TARGET)
Building the bridge between the app and the ML model.

### 3.1 — FastAPI & Firestore Setup
1. **Initialize Backend Folder:** Set up virtual environment and install dependencies (`fastapi`, `firebase-admin`, `uvicorn`).
2. **Connect Firebase:** Initialize Firestore to store the `daily_metrics` collection.
3. **Define Pydantic Models:** Strict schema validation for incoming behavioral vectors.

### 3.2 — Multi-State API Endpoints
1. **POST `/daily-data`:** Endpoint to receive the full behavioral payload and store it in Firestore.
2. **GET `/prediction`:** Endpoint to trigger the model and return Stress/Anxiety/Burnout/Addiction results.

---

## 🔷 STAGE 4 — DATA COLLECTION & ML WORKFLOW
The "Real World" phase where we gather evidence to train the intelligence.

### 4.1 — Data Gathering (2-3 Weeks)
1. **User Recruitment:** Deploy APK to 10-20 friends.
2. **Passive Logging:** Use the app normally to generate ~300-500 rows of behavior-mood pairs.
3. **Labeling:** Ensure daily self-reported mood/state entries are consistent.

### 4.2 — Machine Learning Training
1. **Export Dataset:** Pull Firestore logs into a `dataset.csv`.
2. **Feature Engineering:** Calculate critical ratios (e.g., `social_time / productivity_time`).
3. **Train Random Forest:** Use Scikit-learn for multi-class classification.
4. **Model Export:** Save as `stress_model.pkl` and integrate into the FastAPI backend.

---

## 🔷 STAGE 5 — INTEGRATION & FEEDBACK LOOP
1. **Connect Retrofit:** Hook up the Android app to the production backend.
2. **Real-time Alert System:** Implement the notification/dashboard logic to show actionable advice based on ML output.
3. **Burnout Early Warning:** Specific UI alerts for erratic behavioral shifts.

---

## 🎯 FINAL DELIVERABLES
- [ ] Functional Android App (Kotlin/Compose)
- [ ] FastAPI Backend + Firestore
- [ ] Trained Random Forest Model (.pkl)
- [ ] 7-Day Trend Dashboard
- [ ] Actionable Mental Health Alerts
