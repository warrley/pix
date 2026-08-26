# PIX Project

This is our university project for Configuration Management. We are building a simple PIX system with accounts and transfers.

## Project Structure
- `/backend`: Ruby on Rails API
- `/frontend`: Next.js Web App

## How to run

### 1. Environment Variables
Before running the project, you must create a `.env` file in the root directory (this file is ignored by Git for security). Ask a team member for the development variables, or copy these defaults:

```env
SECRET_KEY_BASE=local_development_secret_key
POSTGRES_USER=postgres
POSTGRES_PASSWORD=postgres
```

### 2. Start the Project
Make sure you have Docker installed. To start the database and backend, run:

```bash
docker compose up
```

### 3. Fixing Common Permission Errors (Linux Users)
If you are on Linux and get a `Permission denied` error about the `backend/tmp` or `backend/log` folders crashing the container, it's because Docker creates these folders as the `root` user. Fix it by running:

```bash
sudo chmod -R 777 backend/tmp backend/log
```

If you get a permission error about `backend/config/master.key`, run:

```bash
sudo chmod 644 backend/config/master.key
```

### 4. Verify it's working!
Open your browser and navigate to the health check endpoint:
👉 [http://localhost/up](http://localhost/up)

If you see a green screen with the Rails logo, your local setup is perfect!

## Contributing
Check the `CONTRIBUTING.md` file for our team rules before pushing code.