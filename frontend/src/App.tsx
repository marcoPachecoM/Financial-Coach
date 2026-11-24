import { useState } from "react";
import { HomeScreen } from "./components/HomeScreen";
import { LoginScreen } from "./components/LoginScreen";
import { RegisterScreen } from "./components/auth/RegisterScreen";
import { ResetPasswordScreen } from "./components/auth/ResetPasswordScreen";
import { useAuth } from "./contexts/AuthContext";

type AuthView = "login" | "register" | "reset";

function App() {
  const { isAuthenticated } = useAuth();
  const [authView, setAuthView] = useState<AuthView>("login");

  if (isAuthenticated) {
    return <HomeScreen />;
  }
  if (authView === "register") {
    return <RegisterScreen onBackToLogin={() => setAuthView("login")} />;
  }
  if (authView === "reset") {
    return <ResetPasswordScreen onBackToLogin={() => setAuthView("login")} />;
  }
  return (
    <LoginScreen onCreateAccount={() => setAuthView("register")} onForgotPassword={() => setAuthView("reset")} />
  );
}

export default App;
