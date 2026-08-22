import { createContext, useContext } from "react";

export const AuthContext = createContext<AuthContextValue | undefined>(
  undefined,
);

interface AuthContextValue {
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (
    email: string,
    password: string,
    rememberMe: boolean,
  ) => Promise<void>;
  logout: () => Promise<void>;
}

export const useAuth = () => {
  const context = useContext(AuthContext);

  if (!context) {
    throw new Error("useAuth must be used within an AuthProvider");
  }

  return context;
};
