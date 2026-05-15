# Firestore Rules Deployment Note

PCOSina stores mobile profile data in Firestore under `profiles/{userId}`. Firebase Authentication must be enforced at the Firestore rules layer so a signed-in user can read and update only the document whose ID matches `request.auth.uid`.

The repository includes a baseline `firestore.rules` template:

- `profiles/{userId}` is readable and writable only when `request.auth.uid == userId`.
- All other collections are denied by default.
- Backend operator tools use the Firebase Admin SDK and must still enforce backend RBAC before reading profile snapshots.

Before staging or respondent testing, deploy equivalent rules in the Firebase Console or Firebase CLI and verify them with the Firebase Rules Simulator. The repository includes `firebase.json`, so the CLI command is:

```powershell
firebase deploy --only firestore:rules --project pcosina
```

Do not treat client-side screen hiding or app navigation checks as Firestore authorization.
