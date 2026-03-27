import firebase_admin
from firebase_admin import credentials, firestore
import os
from dotenv import load_dotenv

load_dotenv()

# Initialize Firebase
# Path to service account key should be in .env or default to serviceAccountKey.json
cred_path = os.getenv("FIREBASE_SERVICE_ACCOUNT_PATH", "serviceAccountKey.json")

db = None

if os.path.exists(cred_path):
    cred = credentials.Certificate(cred_path)
    firebase_admin.initialize_app(cred)
    db = firestore.client()
else:
    print(f"WARNING: Firebase service account key not found at {cred_path}. Firestore features will be disabled.")

def store_daily_metrics(metrics: dict):
    if db:
        user_id = metrics.get("user_id")
        date = metrics.get("date")
        db.collection("users").document(user_id).collection("daily_metrics").document(date).set(metrics)
        return True
    return False

def get_latest_metrics(user_id: str):
    if db:
        docs = db.collection("users").document(user_id).collection("daily_metrics").order_by("date", direction=firestore.Query.DESCENDING).limit(1).get()
        if docs:
            return docs[0].to_dict()
    return None
