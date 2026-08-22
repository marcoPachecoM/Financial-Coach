import { useCallback, useState, type ReactNode } from "react";
import { authStorage } from "../auth/storage";

import {
  login as loginRequest,
  logout as logoutRequest,
} from "../auth/services";
import { AuthContext } from "../contexts/AuthContext";

interface AuthProviderProps {
  children: ReactNode;
}

export const AuthProvider = ({ children }: AuthProviderProps) => {
  const [isAuthenticated, setIsAuthenticated] = useState(
    authStorage.isAuthenticated(),
  );

  const [isLoading, setIsLoading] = useState(false);

  const login = useCallback(
    async (email: string, password: string, rememberMe: boolean) => {
      setIsLoading(true);

      try {
        const { access_token, refresh_token } = await loginRequest({
          email,
          password,
        });

        authStorage.setTokens(access_token, refresh_token, rememberMe);

        setIsAuthenticated(true);
      } finally {
        setIsLoading(false);
      }
    },
    [],
  );

  const logout = useCallback(async () => {
    setIsLoading(true);

    try {
      await logoutRequest();
    } finally {
      authStorage.clear();
      setIsAuthenticated(false);
      setIsLoading(false);
    }
  }, []);

  return (
    <AuthContext.Provider
      value={{
        isAuthenticated,
        isLoading,
        login,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
};
