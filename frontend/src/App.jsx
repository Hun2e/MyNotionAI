import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { useAuthStore } from "./store";
import Home from "./pages/Home";
import Calendar from "./pages/Calendar";
import Login from "./pages/Login";
import AppLayout from "./layout/AppLayout";

const bypassAuth = import.meta.env.VITE_BYPASS_AUTH === "true";

function ProtectedRoute({ element }) {
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated);
  return isAuthenticated || bypassAuth ? element : <Navigate to="/login" />;
}

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route
          path="/"
          element={<ProtectedRoute element={<AppLayout />} />}
        >
          <Route index element={<Home />} />
          <Route path="calendar" element={<Calendar />} />
        </Route>
        <Route path="*" element={<Navigate to="/" />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;
