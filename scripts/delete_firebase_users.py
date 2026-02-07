import argparse
import json
import os
import sys

import firebase_admin
from firebase_admin import auth, credentials


def init_firebase():
    if firebase_admin._apps:
        return
    credentials_json = os.getenv("FIREBASE_SERVICE_ACCOUNT_JSON")
    credentials_path = os.getenv("FIREBASE_CREDENTIALS_PATH", "backend/secrets/firebase-service-account.json")
    if credentials_json:
        cred = credentials.Certificate(json.loads(credentials_json))
    elif os.path.exists(credentials_path):
        cred = credentials.Certificate(credentials_path)
    else:
        raise RuntimeError(
            "Missing Firebase credentials. Set FIREBASE_SERVICE_ACCOUNT_JSON or FIREBASE_CREDENTIALS_PATH."
        )
    firebase_admin.initialize_app(cred)


def delete_all_users(dry_run: bool = True) -> int:
    deleted = 0
    if dry_run:
        for _ in auth.list_users().iterate_all():
            deleted += 1
        return deleted

    batch = []
    for user in auth.list_users().iterate_all():
        batch.append(user.uid)
        if len(batch) == 1000:
            auth.delete_users(batch)
            deleted += len(batch)
            batch = []
    if batch:
        auth.delete_users(batch)
        deleted += len(batch)
    return deleted


def main():
    parser = argparse.ArgumentParser(description="Delete all Firebase Auth users.")
    parser.add_argument("--confirm", action="store_true", help="Actually delete users.")
    args = parser.parse_args()

    init_firebase()

    if not args.confirm:
        count = delete_all_users(dry_run=True)
        print(f"Dry run: {count} users would be deleted.")
        print("Re-run with --confirm to delete.")
        return

    count = delete_all_users(dry_run=False)
    print(f"Deleted {count} users.")


if __name__ == "__main__":
    try:
        main()
    except Exception as exc:
        print(f"Error: {exc}")
        sys.exit(1)
