# Deploy ServiceForce on Railway

The browser interface and Java backend are packaged in one service. Railway detects the repository's root `Dockerfile` and builds the runnable JAR. The live app uses a SQLite database on an attached Railway volume.

## Before deploying

Use a [Railway account](https://railway.com/) and the [Java GitHub repository](https://github.com/itsme07-07/OOPS-project-Java). Railway currently has a $0 Free plan with a limited monthly resource allowance. Check [Railway pricing](https://railway.com/pricing) before leaving a Java service running continuously; the trial and Free allowances may not cover an always-on app.

## Create the service

1. In Railway, create a new project and choose **Deploy from GitHub repo**. Connect `itsme07-07/OOPS-project-Java`, branch `main`.
2. Attach a **volume** to the service and set its mount path to `/data`. The app reads Railway's `RAILWAY_VOLUME_MOUNT_PATH` and stores `/data/itsm.sqlite3` there. Railway's filesystem outside the volume is temporary.
3. In the service **Variables** tab, add these values. Choose your own name, real email and strong password; do not put the password in Git:

   | Variable | Value |
   |---|---|
   | `SERVICEFORCE_ADMIN_NAME` | Your administrator display name |
   | `SERVICEFORCE_ADMIN_EMAIL` | Your administrator contact email |
   | `SERVICEFORCE_ADMIN_PASSWORD` | A private administrator password of at least 8 characters |

4. In service settings, set the healthcheck path to `/healthz`. Generate a public HTTP domain under **Networking**. Railway supplies the `PORT` variable; ServiceForce listens on that port and on `0.0.0.0` automatically.
5. Deploy the staged changes. Open the generated HTTPS domain and sign in with the administrator email and password from step 3. The system also generates a role-based username for that account, visible under **My profile**.

The demo accounts from the README are **not** created on Railway. The first deployment creates a fresh database. ServiceForce uses a secure session cookie behind Railway HTTPS.

## After deployment

- You can remove the three first-administrator variables after the administrator account exists; they are only read when the volume has no active admin. Keep your administrator password safe. To change it, use **My profile**.
- Back up the Railway volume. Incident attachments, knowledge articles and account data are stored in SQLite on that volume.
- Use one service instance with this SQLite database. Additional replicas would require a shared database service and related application changes.
- Railway's Free plan has limited resources. Watch usage in Railway and stop the service when you no longer need it.

For local development, `./mvnw clean verify` and `java -jar target/relay.jar` still start the app at `http://127.0.0.1:8000/` with local demo accounts.
