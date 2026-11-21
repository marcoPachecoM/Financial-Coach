import { useCallback, useEffect, useState, type ReactNode } from "react";
import { authStorage, SESSION_EXPIRED_EVENT } from "../auth/storage";

import { login as loginRequest, logout as logoutRequest } from "../auth/services";
import { AuthContext } from "../contexts/AuthContext";

interface AuthProviderProps {
  children: ReactNode;
}

export const AuthProvider = ({ children }: AuthProviderProps) => {
  const [isAuthenticated, setIsAuthenticated] = useState(authStorage.isAuthenticated());
  const [isLoading, setIsLoading] = useState(false);

  // The API client fires this when the refresh token no longer works: back to the login screen.
  useEffect(() => {
    const onExpired = () => setIsAuthenticated(false);
    window.addEventListener(SESSION_EXPIRED_EVENT, onExpired);
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, onExpired);
  }, []);

  const login = useCallback(async (email: string, password: string, rememberMe: boolean) => {
    setIsLoading(true);
    try {
      const { access_token, refresh_token } = await loginRequest({ email, password });
      authStorage.setTokens(access_token, refresh_token, rememberMe);
      setIsAuthenticated(true);
    } finally {
      setIsLoading(false);
    }
  }, []);

  const logout = useCallback(async () => {
    setIsLoading(true);
    try {
      await logoutRequest();
    } catch {
      // Logging out locally is enough if the server cannot be reached or the token is already invalid.
    } finally {
      authStorage.clear();
      setIsAuthenticated(false);
      setIsLoading(false);
    }
  }, []);

  return (
    <AuthContext.Provider value={{ isAuthenticated, isLoading, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
};
