export type LoginPayload = {
  email: string;
  password: string;
};

/** TokenPair of openapi.yaml: returned by login and by every refresh. */
export type TokenPair = {
  access_token: string;
  refresh_token: string;
};
