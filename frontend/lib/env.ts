/**
 * Central place for reading public runtime configuration. Only NEXT_PUBLIC_*
 * variables are ever exposed to the browser; secrets stay on the backend.
 */
export const API_URL: string =
  process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";
