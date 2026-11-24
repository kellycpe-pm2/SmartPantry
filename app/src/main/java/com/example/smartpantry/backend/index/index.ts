
const PRIMARY_MODEL = "llama-3.3-70b-versatile";

function getGroqUrl() {
  return "https://api.groq.com/openai/v1/chat/completions";
}

async function fetchGroqWithRetry(
  apiKey: string,
  model: string,
  payload: Record<string, unknown>,
  maxRetries = 2
) {
  for (let attempt = 0; attempt <= maxRetries; attempt++) {
    const response = await fetch(getGroqUrl(), {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "Authorization": `Bearer ${apiKey}`,
      },
      body: JSON.stringify({
        model,
        ...payload,
      }),
    });

    const data = await response.json();

    if (response.ok) {
      return { ok: true as const, data };
    }

    // Retry on rate limits and temporary server errors
    if (
      (response.status === 429 || response.status >= 500) &&
      attempt < maxRetries
    ) {
      await new Promise((r) =>
        setTimeout(r, 800 * (attempt + 1))
      );
      continue;
    }

    return {
      ok: false as const,
      status: response.status,
      data,
    };
  }

  return {
    ok: false as const,
    status: 503,
    data: null,
  };
}

async function callGroq(
  systemPrompt: string,
  userPrompt: string,
  schema?: Record<string, unknown>
) {
  const apiKey = Deno.env.get("GROQ_API_KEY");

  if (!apiKey) {
    throw new Error(
      "GROQ_API_KEY is not configured in Supabase Edge Function secrets."
    );
  }

  const payload: Record<string, unknown> = {
    messages: [
      {
        role: "system",
        content: systemPrompt,
      },
      {
        role: "user",
        content: userPrompt,
      },
    ],
    temperature: 0.2,
    ...(schema && {
      response_format: {
        type: "json_object",
      },
    }),
  };

  const res = await fetchGroqWithRetry(
    apiKey,
    PRIMARY_MODEL,
    payload
  );

  if (!res.ok) {
    console.error(
      "Groq request failed:",
      res.status,
      res.data?.error?.message
    );

    throw new Error(
      `Groq API error ${res.status}: ${
        res.data?.error?.message ??
        "Service temporarily busy, please try again."
      }`
    );
  }

  const text =
    res.data?.choices?.[0]?.message?.content;

  if (!text) {
    throw new Error("Groq returned an empty response.");
  }

  return schema ? parseJsonText(text) : text;
}
