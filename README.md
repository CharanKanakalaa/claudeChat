# Claude Java Starter

A small, dependency-free Java project for learning how to build real things
with the Claude API — starting from a CLI tool and ending with a working
web app. Everything here uses only the standard JDK (Java 17+): no Maven,
no Gradle, no external libraries.

## What's in here

| File | What it does |
|---|---|
| `ClaudeChat.java` | Command-line chat: type a question, get an answer |
| `FileReviewer.java` | Command-line: reads a text file + an instruction, sends both to Claude |
| `ClaudeWebApp.java` + `public/index.html` | A small web app: type a message and/or attach a file in the browser, a Java backend calls Claude and returns the answer |

## Setup (do this once)

1. Get an API key from https://console.anthropic.com/
2. Set it as an environment variable in your terminal:

   ```bash
   export ANTHROPIC_API_KEY=your_key_here          # macOS/Linux
   $env:ANTHROPIC_API_KEY="your_key_here"           # Windows PowerShell
   ```

## 1. Run the CLI chat tool

```bash
javac ClaudeChat.java
java ClaudeChat
```

Type a question, get an answer. Type `exit` to quit.

## 2. Run the file reviewer

```bash
javac FileReviewer.java
java FileReviewer notes.txt "Summarize this in 3 bullet points"
```

## 3. Run the web app

```bash
javac ClaudeWebApp.java
java ClaudeWebApp
```

Then open **http://localhost:8080** in your browser. Type a message,
optionally attach a text file, hit Send.

> Run this one from inside the `claude-java-starter` folder — it looks for
> `public/index.html` relative to where you launch it.

## How the web app fits together

```
Browser (public/index.html)
   |  fetch('/api/chat', { message, fileContent })
   v
ClaudeWebApp.java  (Java backend, built-in HttpServer)
   |  combines message + fileContent into one prompt
   v
Claude API (api.anthropic.com)
   |  returns the answer
   v
back to the browser, shown in the Response panel
```

This is the same basic shape as most real AI products: a frontend collects
input, a backend holds the API key and calls the model, the answer flows
back to the UI.

## Pushing this to GitHub

```bash
cd claude-java-starter
git init
git add .
git commit -m "claudeChat"
git branch -M main
git remote add origin https://github.com/<your-username>/<repo-name>.git
git push -u origin main
```

**Important:** never commit your API key. This project only reads it from
an environment variable, so as long as you don't hardcode it anywhere,
you're safe to push. Consider adding a `.gitignore` if you start saving
any local config files.

## Next steps once this feels comfortable

1. **Add tool calling** — teach Claude to call a function you write (a
   calculator, a lookup, etc.). This is the core mechanism behind AI
   agents, and the natural next step from here.
2. **Swap in a real JSON library** — this project parses JSON manually
   with string searching to stay dependency-free, which is fine for
   learning but not for production. Try `org.json` or Jackson next.
3. **Add conversation memory** — right now each request is a fresh
   conversation. Try keeping a running message history so Claude
   remembers earlier turns.
4. **Deploy it** — put the web app on a small server (Render, Railway,
   a VPS) so it's reachable outside your machine.
