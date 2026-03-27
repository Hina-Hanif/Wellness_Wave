# Product Requirements Document (PRD)

## 1. Product Overview
- **Product Name:** Wellness Wave
- **Product Type:** Mobile-based behavioral wellness monitoring system.
- **Vision:** To help users become aware of their mental states—including **stress, anxiety, burnout, and phone addiction**—by analyzing phone usage patterns and daily self-reported mood, without accessing personal content.

### Problem Statement
- Mental health issues like burnout and anxiety are increasing due to high digital dependency.
- Most apps rely only on manual tracking, which is subjective and often ignored.
- There is a lack of non-invasive tools that correlate objective behavioral phone usage with mental health states.

### Proposed Solution
A mobile app that:
- Collects behavioral phone usage metrics (non-content).
- Analyzes **typing behavior, scrolling, app switching, and notification reactions**.
- Collects daily self-reported mental state scores.
- Uses Machine Learning to predict states like Stress, Anxiety, Burnout, or Addiction.
- Displays trends and provides actionable feedback.

> ⚠️ **This app does NOT diagnose medical conditions.**

---

## 2. Goals & Objectives

### Primary Goal
Build an end-to-end system that:
1. Passive-monitors behavioral traits (typing, scrolling, etc.).
2. Learns correlations with mental states using Random Forest.
3. Provides predictive insights for Stress, Anxiety, Burnout, and Addiction.

### Success Criteria
- Reliable collection of expanded behavioral traits.
- ML model trained with ≥70% accuracy for state categories.
- Dashboard visualization of 7-day trends for multiple mental states.

---

## 3. Scope

### ✅ In Scope (V1)
**Behavioral Tracking (Non-Invasive)**
- **Typing Behavior:** Speed, frequency, pause duration (No text content).
- **Scrolling Behavior:** Speed, duration, erratic vs. smooth.
- **App Switching:** Frequency and duration between different app categories.
- **Notification Reactions:** Response time and dismissal patterns.
- **Native Usage:** Screen time, unlocks, night usage (11 PM – 5 AM).

**Mental State Prediction**
- **Stress:** Categorized as Low/Medium/High.
- **Anxiety:** Detection of high-arousal behavioral patterns.
- **Burnout:** Detection of low-engagement or erratic usage shifts.
- **Phone Addiction:** Analysis of usage consistency and unlock intensity.

### ❌ Out of Scope (V1)
- iOS support.
- Reading private messages, emails, or call recordings.
- Voice/Audio analysis.
- Clinical diagnosis of clinical depression or medical disorders.

---

## 4. User Flow
1. User installs app and grants Usage and Accessibility permissions.
2. App monitors behavioral data in the background (no content access).
3. User logs daily subjective mental state.
4. Data is sent to the FastAPI backend.
5. Random Forest model predicts the mental state.
6. User receives actionable feedback (e.g., "High anxiety detected, try a breathing exercise").

---

## 5. ML & Data Requirements
- **Features:** `typing_speed`, `scroll_erraticness`, `app_switch_rate`, `notification_latency`, `screen_time`, `night_ratio`, etc.
- **Target:** Multi-class classification (Stress, Anxiety, Burnout, Addiction, Normal).
- **Minimum Data:** 300–500 rows for initial stable training.
