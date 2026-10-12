# News & Social Media Analytics Engine (Java)

Team 35 · Data Structures and Algorithms-3 (25CS2103E)

A web app that collects news / social-media text, runs NLP analysis (sentiment, keywords, topics, trends)
and shows the results on an interactive dashboard. Backend: **pure Java (JDK 11+), no Maven, no libraries**.
Frontend: HTML + CSS + JavaScript (served from `static/`).

## Run in VS Code

1. Install a **JDK 11 or newer** (check: `javac -version`) and the VS Code
   **"Extension Pack for Java"** (Microsoft).
2. **File → Open Folder…** → select `news-social-analytics-engine-java` (the folder that contains `src` and `static`).
3. Open `src/Main.java` and click **Run** above `public static void main` (or press F5).
4. Your browser opens at **http://localhost:8080** (open it manually if not).
5. Stop with the red stop button / **Ctrl + C**.

### Or from the terminal
```bash
# Windows
run.bat
# macOS / Linux
./run.sh
```
Different port: `java -cp bin Main 9090`.

> Always open the **project root** in VS Code so the `static/` folder is found.

## Project structure
```
news-social-analytics-engine-java/
├── src/
│   ├── Main.java        # HTTP server (com.sun.net.httpserver) + API endpoints
│   ├── NLP.java         # analytics: sentiment, keywords, topics, trends
│   ├── SampleData.java  # demo dataset generator (14 days of posts)
│   └── Json.java        # tiny JSON writer/parser (no external libs)
├── static/              # index.html, style.css, app.js (dashboard)
├── run.bat / run.sh
└── README.md
```

## API
| Method | URL               | Purpose                                   |
|--------|-------------------|-------------------------------------------|
| GET    | `/api/sample`     | analysis of the built-in demo dataset     |
| GET    | `/api/sample-text`| raw demo text (for the Analyze box)       |
| POST   | `/api/analyze`    | body `{"text": "..."}` -> analysis JSON   |

## Using your own data
In the **Analyze** section paste one post per line, or upload a `.txt` / `.csv`:
```
2026-10-11 | Twitter/X | I love the new update, it's amazing!
2026-10-11 | News | Government announces new AI regulation
A plain line without a date also works
```
Dates (`YYYY-MM-DD`) enable the *topic volume over time* chart and *emerging discussions*.

## How it maps to the project
| Stage   | Java method                                   | Data structure / algorithm                   |
|---------|-----------------------------------------------|----------------------------------------------|
| Collect | `NLP.parseItems()`                            | line parsing, regex                          |
| Process | `NLP.tokenize()`, `NLP.forms()`               | tokenisation, light stemming                 |
| Analyze | `sentiment()`, `classifyTopic()`, `docTerms()`| `HashMap`, `HashSet`, lexicon scoring        |
| Trends  | `NLP.analyze()` + `NLP.topK()`                | `PriorityQueue` min-heap Top-K, two-window comparison |
| Present | `static/app.js`                               | SVG donut, bars, line chart, keyword cloud   |

## Ideas to extend
* Replace the lexicon with Stanford CoreNLP / OpenNLP, or call a transformer model.
* Fetch live data (NewsAPI, Reddit API) with `java.net.http.HttpClient`.
* Persist results in SQLite via JDBC.
