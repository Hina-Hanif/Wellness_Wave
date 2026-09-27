import sqlite3
import os
import datetime

db_path = r"c:\wellness wave\Wellness_Wave\live_behavior_database"

conn = sqlite3.connect(db_path)
cursor = conn.cursor()

# Check tables
cursor.execute("SELECT name FROM sqlite_master WHERE type='table';")
tables = cursor.fetchall()
print("Tables in database:", tables)

# Check schema of behavior_records
cursor.execute("PRAGMA table_info(behavior_records);")
columns = cursor.fetchall()
print("\nColumns in behavior_records:")
for col in columns:
    print(col)

# Get row count
cursor.execute("SELECT COUNT(*) FROM behavior_records;")
count = cursor.fetchone()[0]
print(f"\nTotal rows in behavior_records: {count}")

# Get latest 10 rows
cursor.execute("SELECT id, timestamp, screenTime, unlockCount, nightUsage, appSwitchCount, scrollSpeed FROM behavior_records ORDER BY timestamp DESC LIMIT 10;")
rows = cursor.fetchall()

print("\nLatest 10 records:")
print(f"{'id':<5} | {'timestamp':<15} | {'readable date/time':<22} | {'screenTime':<10} | {'unlockCount':<11} | {'nightUsage':<10} | {'appSwitchCount':<15} | {'scrollSpeed':<11}")
print("-" * 115)

for r in rows:
    rid, ts, st, uc, nu, ac, ss = r
    dt_str = datetime.datetime.fromtimestamp(ts / 1000.0).strftime("%Y-%m-%d %H:%M:%S")
    print(f"{rid:<5} | {ts:<15} | {dt_str:<22} | {st:<10} | {uc:<11} | {nu:<10} | {ac:<15} | {ss:<11.2f}")

conn.close()
