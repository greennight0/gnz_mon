import http from "node:http";

const identifyPath = "/v1/species/identify";
const healthPath = "/healthz";
const maxRequestBytes = 8 * 1024 * 1024;

export function createServer({ apiKey = process.env.GEMINI_API_KEY, fetchImpl = fetch } = {}) {
  return http.createServer(async (request, response) => {
    if (request.method === "GET" && request.url === healthPath) {
      return sendJson(response, apiKey ? 200 : 503, { status: apiKey ? "ok" : "misconfigured" });
    }
    if (request.method !== "POST" || request.url !== identifyPath) {
      return sendJson(response, 404, { error: { message: "Not found" } });
    }
    if (!apiKey) return sendJson(response, 503, { error: { message: "Backend is not configured" } });
    if (!(request.headers["content-type"] || "").toLowerCase().startsWith("application/json")) {
      return sendJson(response, 415, { error: { message: "Content-Type must be application/json" } });
    }

    try {
      const rawBody = await readBody(request);
      const payload = JSON.parse(rawBody);
      if (!Array.isArray(payload.contents) || typeof payload.generationConfig !== "object") {
        return sendJson(response, 400, { error: { message: "Invalid generateContent payload" } });
      }
      const upstream = await fetchImpl(
        `https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=${encodeURIComponent(apiKey)}`,
        { method: "POST", headers: { "content-type": "application/json" }, body: rawBody }
      );
      const body = await upstream.text();
      response.writeHead(upstream.status, {
        "content-type": upstream.headers.get("content-type") || "application/json; charset=utf-8",
        "cache-control": "no-store"
      });
      response.end(body);
    } catch (error) {
      const status = error?.code === "REQUEST_TOO_LARGE" ? 413 : error instanceof SyntaxError ? 400 : 502;
      sendJson(response, status, { error: { message: status === 502 ? "Gemini upstream unavailable" : error.message } });
    }
  });
}

function readBody(request) {
  return new Promise((resolve, reject) => {
    let size = 0;
    const chunks = [];
    request.on("data", chunk => {
      size += chunk.length;
      if (size > maxRequestBytes) {
        const error = new Error("Request too large");
        error.code = "REQUEST_TOO_LARGE";
        reject(error);
        request.destroy();
      } else chunks.push(chunk);
    });
    request.on("end", () => resolve(Buffer.concat(chunks).toString("utf8")));
    request.on("error", reject);
  });
}

function sendJson(response, status, value) {
  response.writeHead(status, { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" });
  response.end(JSON.stringify(value));
}

if (import.meta.url === `file://${process.argv[1]}`) {
  createServer().listen(Number(process.env.PORT || 8080), "0.0.0.0");
}
