import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { Link, Route, Routes } from 'react-router-dom';
import ProtectedRoute from './components/ProtectedRoute';
import Login from './pages/Login';
import Register from './pages/Register';
import './app.css';

const queryClient = new QueryClient();

function Placeholder({ title }: { title: string }) {
  return (
    <main>
      <h1>{title}</h1>
      <p>Full screen lands at M7.</p>
      <nav>
        <Link to="/">Dashboard</Link>
      </nav>
    </main>
  );
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />
        <Route
          path="/"
          element={
            <ProtectedRoute>
              <Placeholder title="Dashboard" />
            </ProtectedRoute>
          }
        />
      </Routes>
    </QueryClientProvider>
  );
}
