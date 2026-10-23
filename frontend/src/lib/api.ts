export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
  ) {
    super(message);
  }
}
type Csrf = { token: string; headerName: string };
let csrf: Promise<Csrf> | undefined;
export function resetCsrf() {
  csrf = undefined;
}
let onExpired: (message?: string) => void = () => {};
export function setSessionExpiredHandler(handler: (message?: string) => void) {
  onExpired = handler;
}

async function decode(response: Response) {
  if (response.status === 204) return undefined;
  const text = await response.text();
  if (!text) return undefined;
  try {
    return JSON.parse(text);
  } catch {
    throw new ApiError(
      response.status,
      "INVALID_RESPONSE",
      "Máy chủ trả về dữ liệu không hợp lệ.",
    );
  }
}

export async function api<T>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const method = options.method || "GET";
  const headers = new Headers(options.headers);
  if (options.body) headers.set("Content-Type", "application/json");
  if (!["GET", "HEAD", "OPTIONS"].includes(method.toUpperCase())) {
    csrf ??= fetch("/api/auth/csrf", {
      credentials: "same-origin",
      cache: "no-store",
    })
      .then(async (r) => {
        if (!r.ok)
          throw new ApiError(
            r.status,
            "CSRF_UNAVAILABLE",
            "Chưa thể xác thực yêu cầu.",
          );
        return decode(r);
      })
      .catch((error) => {
        resetCsrf();
        throw error instanceof ApiError
          ? error
          : new ApiError(
              0,
              "OFFLINE",
              "Không kết nối được máy chủ. Kiểm tra mạng và thử lại.",
            );
      });
    const token = await csrf;
    headers.set(token.headerName, token.token);
  }
  let response: Response;
  try {
    response = await fetch(path, {
      ...options,
      method,
      headers,
      credentials: "same-origin",
      cache: "no-store",
    });
  } catch (error) {
    if (options.signal?.aborted) throw error;
    throw new ApiError(
      0,
      "OFFLINE",
      "Không kết nối được máy chủ. Kiểm tra mạng và thử lại.",
    );
  }
  const data = await decode(response);
  if (!response.ok) {
    if (
      path !== "/api/auth/login" &&
      (response.status === 401 ||
        (response.status === 403 &&
          ["ACCOUNT_TEMP_BANNED", "ACCOUNT_INDEFINITE_BANNED"].includes(
            data?.code,
          )))
    )
      onExpired(data?.message);
    if (data?.code === "CSRF_INVALID") resetCsrf();
    // Never replay a write automatically: its result may already be committed.
    throw new ApiError(
      response.status,
      data?.code || "REQUEST_FAILED",
      data?.message || "Yêu cầu chưa thực hiện được. Vui lòng thử lại.",
    );
  }
  return data as T;
}
export function post<T>(path: string, body?: unknown) {
  return api<T>(path, {
    method: "POST",
    body: body === undefined ? undefined : JSON.stringify(body),
  });
}
