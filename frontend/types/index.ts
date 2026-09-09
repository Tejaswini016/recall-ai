/** Shared API types. Populated feature-by-feature as backend endpoints land. */
export interface ApiError {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
}
