## STOMP Toy 01 – Railway Deployment Guide

- **Stack**: Spring Boot (WebSocket), Maven wrapper, Java 21.
- **Goal**: Containerize the app and prepare a repeatable Railway deployment workflow.
- **Key Assets**: `stomp-toy-01/Dockerfile`, `stomp-toy-01/.dockerignore`.

### Local Development

- Install JDK 21+ and ensure `./stomp-toy-01/mvnw` is executable (`chmod +x mvnw` if needed).
- Start the application locally:
  - `cd stomp-toy-01`
  - `./mvnw spring-boot:run`
- Run tests anytime with `./mvnw test`.

### Build and Run with Docker

- Build the container image (from repository root):
  - `docker build -t stomp-toy-01 ./stomp-toy-01`
- Run the image locally:
  - `docker run --rm -p 8080:8080 stomp-toy-01`
- The container exposes port `8080`; Railway will automatically map traffic to the same port.

### Deploying on Railway

- Install the Railway CLI (`npm install -g @railway/cli` or use their install script).
- Authenticate: `railway login`
- Initialize the project (monorepo-aware):
  - `railway init --service stomp-toy-01 --root ./stomp-toy-01`
  - Accept or create a Railway project when prompted.
- (Optional) Configure environment variables:
  - `railway variables set SPRING_PROFILES_ACTIVE=prod`
- Trigger a deployment:
  - `railway up --service stomp-toy-01`
- Railway detects the `Dockerfile`, builds the image remotely, and runs `java -jar app.jar`.

### Continuous Deployment Tips

- Subsequent pushes can be deployed by re-running `railway up`.
- Use Railway environments (e.g., `railway environment create staging`) to isolate configs.
- Monitor deploys and logs in the Railway dashboard or via `railway status` and `railway logs`.
