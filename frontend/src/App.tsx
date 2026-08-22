import { HomeScreen } from "./components/HomeScreen";
import { LoginScreen } from "./components/LoginScreen";
import { useAuth } from "./contexts/AuthContext";

function App() {
  const { isAuthenticated } = useAuth();

  return isAuthenticated ? <HomeScreen /> : <LoginScreen />;
}

export default App;
