# Drill — Study Materials Generator

Drill is a full-stack web application that transforms PowerPoint lecture slides into interactive study materials (flashcards, multiple-choice questions) using AI. Built to explore practical multi-file processing workflows and API integration with large language models.

**Live demo:** [drillapi.onrender.com](https://drillapi.onrender.com) (backend) | [Frontend on Vercel](https://study-cs.vercel.app/))

---

## Architecture

**Backend:** Spring Boot REST API (Java) on Render  
**Frontend:** React + TypeScript + Vite on Vercel  
**AI Engine:** Google Gemini API  

```
User → React UI → Spring Boot API → Gemini API → SQLite → JSON Response
                     ↑
              Multi-file upload
              PowerPoint parsing
              Prompt engineering
```

---

## Key Features

- **PowerPoint Upload**: Drag-and-drop multi-file uploads with Apache POI for file parsing
- **AI-Driven Questions**: Prompts Gemini API to generate context-aware flashcards and quiz questions
- **Flexible Question Types**: Multiple-choice and open-ended questions with answer explanations
- **Persistent Storage**: SQLite database tracks uploaded materials and generated questions
- **CORS-Enabled API**: Designed for cross-origin requests from frontend consumers

---

## Technical Decisions

### Spring Boot 4.1.0
Chose Spring Boot for its mature ecosystem and built-in MVC patterns. This was my first production Spring project, so robustness mattered over experimentation. The framework's dependency injection and auto-configuration saved time during the multi-file upload iteration.

### Apache POI for PowerPoint Parsing
PowerPoint files (.pptx) are ZIP archives containing XML. Using POI abstracted away that complexity and let me focus on question generation logic rather than file format parsing.

### Gemini API (not OpenAI)
Wanted to explore Google's model and reduce vendor lock-in. Gemini's pricing and prompt engineering constraints (token limits, response format) taught me how to write prompts that reliably produce structured JSON even with API cost sensitivity.

### SQLite for Local Storage
Kept deployment simple—no external database to manage on Render's free tier. SQLite is sufficient for demonstrating data persistence; production would require PostgreSQL.

### Render + Vercel Deployment
Two-tier deployment reduces coupling between frontend and backend builds. Render's free tier has limitations (sleeps if inactive), which is fine for a portfolio project but shows understanding of production constraints.

---

## Setup & Deployment

### Local Development

```bash
# Backend
git clone https://github.com/ybelai2/drillapi.git
cd drillapi
export GEMINI_API_KEY=your_key_here
mvn spring-boot:run

# Frontend (separate repo)
git clone https://github.com/ybelai2/os439-frontend.git
npm install
npm run dev
```

### Environment Variables

```
GEMINI_API_KEY        # Google Gemini API key (https://ai.google.dev)
SERVER_PORT           # Default: 8080
SQLITE_DB_PATH        # Default: ./data/drill.db
```

### Production Deployment

**Backend on Render:**
- Connected to GitHub repo with automatic deploys on push
- Uses Dockerfile for containerization
- Cold start issue: Free tier dyno sleeps after 15 min inactivity

**Frontend on Vercel:**
- Automatic deployments on main branch push
- Environment variable: `VITE_API_URL` points to Render backend

---

## API Endpoints

### POST `/api/materials/upload`
Accepts multipart file upload (PowerPoint files)
```bash
curl -X POST http://localhost:8080/api/materials/upload \
  -F "files=@lecture1.pptx" \
  -F "files=@lecture2.pptx"
```

**Response:**
```json
{
  "materialId": "abc123",
  "fileName": "lecture1.pptx",
  "status": "processing",
  "uploadedAt": "2024-07-22T10:30:00Z"
}
```

### GET `/api/materials/{materialId}/questions`
Fetch generated questions for a material

**Response:**
```json
{
  "questions": [
    {
      "id": "q1",
      "type": "multiple-choice",
      "text": "What is the primary purpose of virtual memory?",
      "options": ["A", "B", "C", "D"],
      "correctAnswer": "B",
      "explanation": "..."
    }
  ],
  "generatedAt": "2024-07-22T10:35:00Z"
}
```

---

## Known Limitations & Trade-offs

1. **Synchronous Processing**: Large PowerPoint files block the upload endpoint. Production should use async job queues (SQS).
2. **Gemini API Cost**: Each upload makes multiple API calls. No rate limiting implemented; production needs throttling.
3. **No Authentication**: Anyone can upload/download. Multipart form validation exists but no user auth layer.
4. **SQLite Scalability**: Good for <10K uploaded files. Would switch to PostgreSQL for production scale.
5. **Prompt Engineering**: Gemini doesn't always return valid JSON; added retry logic but it's brittle.

---

## What I Learned

- **Real-world file parsing**: Zip archives, XML, binary data aren't abstractions anymore
- **API integration complexity**: Structuring prompts for reliable JSON output, handling model limitations
- **Full-stack iteration**: Debugging CORS issues, managing state across three layers (frontend, API, AI)
- **Deployment friction**: Cold starts, environment variable management, containerization basics
- **Trade-off thinking**: When to use free tiers (Render) vs when they become limitations

---

## Future Improvements

- [ ] Async processing with job queue (Redis + background workers)
- [ ] User authentication and material ownership tracking
- [ ] Gemini multimodal: Extract and analyze images from slides
- [ ] Question difficulty scoring (based on Bloom's taxonomy)
- [ ] Rate limiting and quota management per user
- [ ] Export to Anki deck format

---

## Running Tests

```bash
mvn test
```

Current test coverage: file upload validation, Gemini prompt structure, API response parsing.

---

## License

MIT

---

## Questions?

Open an issue on GitHub or reach out: [your-email@example.com]
