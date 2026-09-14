import assert from "node:assert/strict";
import { once } from "node:events";
import test from "node:test";
import { createServer } from "./server.mjs";

async function withServer(options, callback) {
  const server = createServer(options).listen(0, "127.0.0.1");
  await once(server, "listening");
  try { await callback(`http://127.0.0.1:${server.address().port}`); }
  finally { server.close(); await once(server, "close"); }
}

test("health check contains no image and verifies server configuration", async () => {
  await withServer({ apiKey: "server-secret" }, async base => {
    const response = await fetch(`${base}/healthz`);
    assert.equal(response.status, 200);
    assert.deepEqual(await response.json(), { status: "ok" });
  });
});

test("identify proxies the generateContent contract and preserves status", async () => {
  let upstream;
  await withServer({ apiKey: "server-secret", fetchImpl: async (url, init) => {
    upstream = { url, init };
    return new Response(JSON.stringify({ candidates: [] }), { status: 429, headers: { "content-type": "application/json" } });
  } }, async base => {
    const payload = { contents: [{ parts: [{ text: "prompt" }] }], generationConfig: { temperature: 0.2 } };
    const response = await fetch(`${base}/v1/species/identify`, {
      method: "POST", headers: { "content-type": "application/json" }, body: JSON.stringify(payload)
    });
    assert.equal(response.status, 429);
    assert.match(upstream.url, /gemini-2\.5-flash:generateContent/);
    assert.deepEqual(JSON.parse(upstream.init.body), payload);
  });
});
