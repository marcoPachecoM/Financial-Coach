export type LoginPayload = {
  email: string;
  password: string;
};

/** TokenPair of openapi.yaml: returned by login and by every refresh. */
export type TokenPair = {
  access_token: string;
  refresh_token: string;
};

export type RegisterPayload = {
  username: string;
  email: string;
  password: string;
};

/** Signed in right away; the recovery codes are shown once. */
export type RegisterResponse = TokenPair & {
  recovery_codes: string[];
};

export type PasswordResetPayload = {
  email: string;
  recovery_code: string;
  new_password: string;
};

export type PasswordResetResponse = {
  message: string;
  recovery_codes_remaining: number;
};
