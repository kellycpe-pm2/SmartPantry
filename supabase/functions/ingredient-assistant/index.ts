
import { serve } from "https://deno.land/std@0.224.0/http/server.ts";

const GROQ_API_URL =
  "https://api.groq.com/openai/v1/chat/completions";

const GROQ_MODEL = "openai/gpt-oss-120b";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers":
    "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

function jsonResponse(
  data: unknown,
  status = 200
): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      ...corsHeaders,
      "Content-Type": "application/json",
    },
  });
}

async function callGroq(
  messages: Array<{ role: string; content: string }>,
  jsonMode = false
): Promise<string> {
  const apiKey = Deno.env.get("GROQ_API_KEY");

  if (!apiKey) {
    throw new Error("GROQ_API_KEY is not configured.");
  }

  const requestBody: Record<string, unknown> = {
    model: GROQ_MODEL,
    messages,
    max_completion_tokens: 2048,
  };

  if (jsonMode) {
    requestBody.response_format = {
      type: "json_object",
    };
  }

  const response = await fetch(GROQ_API_URL, {
    method: "POST",
    headers: {
      "Authorization": `Bearer ${apiKey}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(requestBody),
  });

  const responseText = await response.text();

  if (!response.ok) {
    console.error("Groq API error:", response.status, responseText);

    throw new Error(
      `Groq API request failed (${response.status}).`
    );
  }

  const result = JSON.parse(responseText);

  const content =
    result.choices?.[0]?.message?.content;

  if (!content) {
    throw new Error("Groq returned an empty response.");
  }

  return content;
}

async function suggestIngredient(
  ingredientName: string
) {
  const systemPrompt = `
You are SmartPantry, an intelligent food ingredient assistant.

Your task is to identify food ingredients and provide useful
storage and expiry information.

Return ONLY a valid JSON object with these exact fields:

{
  "normalizedName": "string",
  "category": "string",
  "unit": "string",
  "minExpiryDays": 0,
  "maxExpiryDays": 0,
  "description": "string",
  "storageAdvice": "string"
}

Rules:
- Normalize the ingredient name.
- Category should be a suitable food category.
- Unit should be an appropriate measurement unit.
- Expiry days must be reasonable estimates.
- Use whole numbers for expiry days.
- minExpiryDays must not exceed maxExpiryDays.
- Explain storage advice clearly.
- Do not include Markdown.
- Do not include text outside the JSON object.
`;

  const content = await callGroq(
    [
      {
        role: "system",
        content: systemPrompt,
      },
      {
        role: "user",
        content: `Ingredient: ${ingredientName}`,
      },
    ],
    true
  );

  let suggestion;

  try {
    suggestion = JSON.parse(content);
  } catch {
    throw new Error(
      "AI returned invalid JSON for ingredient suggestion."
    );
  }

  const requiredFields = [
    "normalizedName",
    "category",
    "unit",
    "minExpiryDays",
    "maxExpiryDays",
    "description",
    "storageAdvice",
  ];

  for (const field of requiredFields) {
    if (suggestion[field] === undefined) {
      throw new Error(
        `AI suggestion is missing field: ${field}`
      );
    }
  }

  return suggestion;
}

async function chatWithIngredient(
  ingredientName: string,
  message: string,
  history: Array<{ role: string; text: string }>
) {
  const systemPrompt = `
You are SmartPantry's ingredient assistant.

Help users with:
- Food storage advice
- Ingredient shelf life
- Food freshness
- Cooking suggestions
- Food waste reduction
- Ingredient substitutions

Current ingredient: ${ingredientName}

Give friendly, clear and practical answers.

Do not claim that food is safe to eat just because it looks normal.
Remind users to follow food safety practices when appropriate.
`;

  const messages: Array<{
    role: string;
    content: string;
  }> = [
    {
      role: "system",
      content: systemPrompt,
    },
  ];

  for (const item of history) {
    if (
      (item.role === "user" || item.role === "assistant") &&
      typeof item.text === "string"
    ) {
      messages.push({
        role: item.role,
        content: item.text,
      });
    }
  }

  messages.push({
    role: "user",
    content: message,
  });

  return await callGroq(messages);
}

serve(async (req: Request) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", {
      headers: corsHeaders,
    });
  }

  if (req.method !== "POST") {
    return jsonResponse(
      { error: "Method not allowed." },
      405
    );
  }

  try {
    // Supabase gateway verifies the user's JWT before
    // this function runs when verify_jwt is enabled.

    const authorization =
      req.headers.get("Authorization");

    if (!authorization) {
      return jsonResponse(
        {
          error: "Missing authorization header.",
        },
        401
      );
    }

    const body = await req.json();

    const action = body.action;
    const ingredientName = body.ingredientName;

    if (
      typeof ingredientName !== "string" ||
      !ingredientName.trim()
    ) {
      return jsonResponse(
        {
          error: "Ingredient name is required.",
        },
        400
      );
    }

    if (action === "suggest") {
      const suggestion =
        await suggestIngredient(ingredientName);

      return jsonResponse({
        suggestion,
      });
    }

    if (action === "chat") {
      const message = body.message;

      if (
        typeof message !== "string" ||
        !message.trim()
      ) {
        return jsonResponse(
          {
            error: "Chat message is required.",
          },
          400
        );
      }

      const history = Array.isArray(body.history)
        ? body.history
        : [];

      const reply = await chatWithIngredient(
        ingredientName,
        message,
        history
      );

      return jsonResponse({
        reply,
      });
    }

    return jsonResponse(
      {
        error: "Invalid action. Use suggest or chat.",
      },
      400
    );

  } catch (error) {
    console.error("Ingredient assistant error:", error);

    return jsonResponse(
      {
        error:
          error instanceof Error
            ? error.message
            : "Internal server error.",
      },
      500
    );
  }
});
