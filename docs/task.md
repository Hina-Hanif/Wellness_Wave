# Wellness Wave - Project Task Checklist

## 🔷 STAGE 1: UI & Onboarding (COMPLETED)
- [x] Create Home, Mood, Trends, & Settings screens
- [x] Implement NavigationSuiteScaffold
- [x] Create Onboarding Flow
- [x] Sync documentation with Multi-State requirements

## 🔷 STAGE 2: Advanced Behavioral Trackers
- [x] Implement `UsageStatsManager` logic for App Tracking
- [x] Create `BehavioralAccessibilityService` for Typing & Scrolling
- [x] Implement `NotificationListenerService` for reaction latency
- [x] Add "Usage Access" & "Accessibility" permission handlers

## 🔷 STAGE 3: Backend & Data Management
- [x] Initialize FastAPI project with Python virtualenv
- [x] Setup Firebase Admin SDK & Firestore connection (Requires key)
- [x] Create Pydantic data schemas for behavioral vectors
- [x] Implement `POST /daily-data` endpoint
- [x] Implement `GET /prediction` endpoint (with placeholder logic)

## 🔷 STAGE 4: Machine Learning (The IQ)
- [ ] Collect data for 2-3 weeks (Manual tracking phase)
- [ ] Export Firestore logs to CSV
- [ ] Clean and preprocess data for Random Forest
- [ ] Train Multi-class Scikit-learn model
- [ ] Serialize model to `.pkl` and integrate into Backend

## 🔷 STAGE 5: UI Integration & Feedback
- [x] Configure Retrofit in Android project
- [x] Implement Mood-Entry sync with Backend
- [ ] Create Dashboard Alert system for Stress/Anxiety/Burnout
- [ ] Implement History Chart with real API data
- [ ] Final end-to-end verification
