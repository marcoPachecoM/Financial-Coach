import { isAxiosError } from "axios";
import type { ApiFieldError, ProblemDetail } from "../types/api";

/** The Problem Details body of a failed request, if the API sent one. */
export const getProblem = (error: unknown): ProblemDetail | null => {
  if (isAxiosError(error) && error.response?.data && typeof error.response.data === "object") {
    const data = error.response.data as Partial<ProblemDetail>;
    if (typeof data.code === "string") {
      return data as ProblemDetail;
    }
  }
  return null;
};

/** A message to show to the user. */
export const getErrorMessage = (error: unknown, fallback = "Something went wrong."): string => {
  const problem = getProblem(error);
  if (problem?.detail) {
    return problem.detail;
  }
  if (isAxiosError(error) && !error.response) {
    return "Cannot reach the server. Is the backend running?";
  }
  return error instanceof Error ? error.message : fallback;
};

export const getFieldErrors = (error: unknown): ApiFieldError[] => getProblem(error)?.errors ?? [];
