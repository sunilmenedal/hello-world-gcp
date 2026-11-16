# Hi World: IntelliJ → GitHub main → Google Cloud

A Java 21 Maven HTTP application with no runtime dependencies. Cloud Build tests and packages a JAR, builds a Docker image, pushes it to Artifact Registry and deploys a revision to Cloud Run. Each build uses its unique build ID as the image tag.

Cloud Run is a simple destination for this first HTTP example. A future continuously running Flink cluster should be designed separately, typically on GKE; this is not a Flink deployment template.

## 1. Develop locally in IntelliJ

Install a full **JDK 21**, Maven 3.9+, Git and optionally Docker Desktop. Install the Google Cloud CLI for deployment setup, or run the cloud commands below in Google Cloud Shell.

Extract the project. In IntelliJ, choose **File → Open** and select its `pom.xml`; open it as a project. Set **Project SDK** to JDK 21 and Maven's **Runner JRE** to JDK 21. Reload the Maven project. Run `com.example.hello.Main` or use IntelliJ's terminal:

```bash
mvn clean verify
java -jar target/hello-world.jar
```

Open http://localhost:8080 — expected response: `Hi World!`. `/health` returns `OK`. Stop with Ctrl+C. The application binds to `0.0.0.0` and honors Cloud Run's `PORT` environment variable.

Optional local Docker check (stop the local Java process first):

```bash
docker build -t hello-world:local .
docker run --rm -p 8080:8080 hello-world:local
```

## 2. Push to GitHub

Create an **empty** GitHub repository. From this extracted project folder:

```bash
git init
git add .
git commit -m "Add Java Hi World app and Google Cloud pipeline"
git branch -M main
git remote add origin https://github.com/YOUR_ACCOUNT/YOUR_REPOSITORY.git
git push -u origin main
```

On another machine, `git clone https://github.com/YOUR_ACCOUNT/YOUR_REPOSITORY.git`, then open `pom.xml` in IntelliJ. You can use IntelliJ's Commit and Push UI for subsequent changes. Do not commit `target/`, IDE settings or credential files.

## 3. One-time Google Cloud setup

You need a Google Cloud project with billing enabled and permission to enable APIs, create service accounts and grant IAM roles. The commands below are **Bash** commands for Cloud Shell, macOS/Linux or Windows WSL. Replace `YOUR_PROJECT_ID` with the project ID, not its display name.

```bash
export PROJECT_ID="YOUR_PROJECT_ID"
export REGION="us-central1"
export BUILD_SA="hello-builder@${PROJECT_ID}.iam.gserviceaccount.com"
export RUNTIME_SA="hello-runtime@${PROJECT_ID}.iam.gserviceaccount.com"
gcloud config set project "$PROJECT_ID"

gcloud services enable cloudbuild.googleapis.com artifactregistry.googleapis.com \
  run.googleapis.com logging.googleapis.com iam.googleapis.com

gcloud artifacts repositories create java-apps \
  --repository-format=docker --location="$REGION" \
  --description="Java application images"

gcloud iam service-accounts create hello-builder --display-name="Hi World build and deployment"
gcloud iam service-accounts create hello-runtime --display-name="Hi World runtime"

gcloud artifacts repositories add-iam-policy-binding java-apps \
  --location="$REGION" --member="serviceAccount:$BUILD_SA" \
  --role=roles/artifactregistry.writer

gcloud projects add-iam-policy-binding "$PROJECT_ID" \
  --member="serviceAccount:$BUILD_SA" --role=roles/logging.logWriter

gcloud projects add-iam-policy-binding "$PROJECT_ID" \
  --member="serviceAccount:$BUILD_SA" --role=roles/run.developer

gcloud iam service-accounts add-iam-policy-binding "$RUNTIME_SA" \
  --member="serviceAccount:$BUILD_SA" --role=roles/iam.serviceAccountUser
```

Run creation commands once; existing resources may report `ALREADY_EXISTS`. These permissions use a dedicated build account and a separate runtime account. No service-account JSON key is needed. The runtime account needs no application-specific roles for this demo. The person creating/running the trigger must have permission to submit builds and act as the build account (`iam.serviceAccounts.actAs`, commonly granted through Service Account User).

Cloud Run's Google-managed service agent normally gets image-read permissions in the same project automatically. Cross-project images require additional IAM configuration. Organization policies may restrict regions, service accounts, egress or public access.

## 4. Connect GitHub and trigger on main

In the Google Cloud console:

1. Open **Cloud Build → Repositories** and create a GitHub connection using the supported GitHub integration (for example, a regional second-generation connection). Install/authorize the Google Cloud Build GitHub app for the chosen repository. Link that repository. Keep the connection and trigger in the same region where required.
2. Open **Cloud Build → Triggers → Create trigger**. Choose that connection/repository and **Push to a branch**. Set the branch regular expression to **`^main$`**.
3. Select **Cloud Build configuration file**, repository path **`cloudbuild.yaml`**.
4. Select the dedicated **`hello-builder` service account**. In substitution variables set `_REGION` to your region and `_RUNTIME_SERVICE_ACCOUNT` to the full `hello-runtime@YOUR_PROJECT_ID.iam.gserviceaccount.com` email. Defaults for `_REPOSITORY` (`java-apps`) and `_SERVICE` (`hello-world`) can remain.
5. Save. Run the trigger once manually against `main`, or push a new commit after creating it. Initial repository pushes made before the trigger existed are not replayed automatically.

A successful build performs these sequential steps:

```text
main push → mvn clean verify → target/hello-world.jar
          → docker build → Artifact Registry push → Cloud Run deploy
```

Tests or packaging failures stop the pipeline before deployment. Cloud Build fetches the triggering commit, so you do not need to install GitHub credentials on your laptop for the cloud build. The deployed app is **authenticated by default**. `cloudbuild.yaml` deliberately leaves service IAM access policy to your administrator; it does not change an existing public/private policy.

Do not run overlapping deployments to the same service: completion order can differ from commit order. For this first demo, wait for each build to finish before pushing again. Add deployment serialization or revision promotion when evolving the pipeline.

## 5. Optional manual first build

This validates the same pipeline before creating a trigger. Authenticate (`gcloud auth login`) and select the project, or use Cloud Shell. Run from the project folder:

```bash
gcloud builds submit . --region="$REGION" --config=cloudbuild.yaml \
  --service-account="projects/$PROJECT_ID/serviceAccounts/$BUILD_SA" \
  --substitutions="_REGION=$REGION,_RUNTIME_SERVICE_ACCOUNT=$RUNTIME_SA"
```

Manual source upload also requires the build account to read the staging bucket's source objects, and the submitter must be allowed to upload them. If needed, grant **Storage Object Viewer on that staging bucket** to `hello-builder`; keep this scoped to the bucket. The GitHub trigger path handles source fetching through its configured repository connection.

## 6. Test your deployed service

Find its URL in Cloud Run, or:

```bash
SERVICE_URL=$(gcloud run services describe hello-world \
  --region="$REGION" --format='value(status.url)')
```

Grant your testing identity Cloud Run Invoker on the service if it does not already have it. You can test authenticated access using:

```bash
gcloud run services proxy hello-world --region="$REGION" --port=8081
```

Open http://localhost:8081 in your browser. The proxy uses your signed-in Google identity. Stop it with Ctrl+C. An administrator can instead make this demo public after reviewing organization policy, but public access is not required to test the pipeline.

Change the greeting in `Main.java`, run tests and update their expected text, then commit and push to `main`. Watch **Cloud Build → History** and **Cloud Run → Revisions**. The new successful revision receives traffic. For this demo, **merging to main deploys directly**; use branch protection if others will contribute.

## Troubleshooting and cleanup

- `release version 21 not supported`: IntelliJ/Maven is using an older JDK; check `mvn -version`.
- Image push denied: confirm Artifact Registry region/repository and build-account writer role.
- `iam.serviceAccounts.actAs` denied: check build-account access to the runtime account and your permission to act as the build account.
- Deploy denied: verify the trigger uses `hello-builder`, with Cloud Run Developer permissions.
- Cloud Run 403: service is private; use the authenticated proxy and ensure your identity has Invoker access.
- Startup failure: inspect revision logs; the server must listen on `0.0.0.0:$PORT`.
- Organization policy or billing errors: resolve them with the project administrator.

Cloud Build, stored container images and Cloud Run may incur charges. Scaling to zero reduces idle compute, but does not remove stored images. To stop the demo, disable/delete its Cloud Build trigger first, then delete its Cloud Run service. Delete the `java-apps` repository only if it contains no images you need; remove demo service accounts/IAM grants when finished.

This repository does not provision resources or connect accounts automatically. You own the one-time console/IAM setup. Jenkins and Kubernetes are not required for this first example.
