import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { Header } from './components/Header'
import { AuthProvider } from './context/AuthContext'
import { CartProvider } from './context/CartContext'
import { CartPage } from './pages/CartPage'
import { CatalogPage } from './pages/CatalogPage'
import { DashboardPage } from './pages/DashboardPage'
import { LoginPage } from './pages/LoginPage'
import { OrderStatusPage } from './pages/OrderStatusPage'

function App() {
  return (
    <AuthProvider>
      <CartProvider>
        <BrowserRouter>
          <Header />
          <Routes>
            <Route path="/" element={<CatalogPage />} />
            <Route path="/login" element={<LoginPage />} />
            <Route path="/cart" element={<CartPage />} />
            <Route path="/orders/:orderId" element={<OrderStatusPage />} />
            <Route path="/dashboard" element={<DashboardPage />} />
          </Routes>
        </BrowserRouter>
      </CartProvider>
    </AuthProvider>
  )
}

export default App
