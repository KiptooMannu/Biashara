import { useMemo, useState } from 'react'
import { Loader2, Minus, Plus, Receipt, Search, ShoppingCart, Trash2 } from 'lucide-react'
import { toast } from 'sonner'
import { api, errorMessage } from '@/lib/api'
import { moneyWhole, number } from '@/lib/format'
import { useApi } from '@/hooks/useApi'
import { useAuth } from '@/store/auth'
import type { ProductRow } from '@/lib/types'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select'
import { ScrollArea } from '@/components/ui/scroll-area'
import { PageHeader } from '@/components/shared/PageHeader'
import { DataState } from '@/components/shared/DataState'
import { cn } from '@/lib/utils'

interface CartLine {
  product: ProductRow
  quantity: number
}

interface CustomerOption {
  id: number
  name: string
  customerType?: string
}

const PAYMENT_METHODS = ['MPESA', 'CASH', 'CARD', 'BANK_TRANSFER', 'CREDIT']

/**
 * The till.
 *
 * Checkout posts the whole basket in one request; the server decrements stock,
 * writes the movement ledger, records the payment and updates the customer inside
 * one transaction, so a partial sale is impossible.
 */
export default function PosPage() {
  const currency = useAuth((state) => state.user?.currency) ?? 'KES'
  const [query, setQuery] = useState('')
  const [cart, setCart] = useState<CartLine[]>([])
  const [customerId, setCustomerId] = useState<string>('')
  const [method, setMethod] = useState('MPESA')
  const [submitting, setSubmitting] = useState(false)
  const [lastReceipt, setLastReceipt] = useState<{ invoice: string; total: number } | null>(null)

  const products = useApi<{ content: ProductRow[] }>('/inventory/products', { size: 100 })
  const customers = useApi<{ content: CustomerOption[] }>('/customers', { size: 100 })

  const catalogue = products.data?.content ?? []

  const filtered = useMemo(() => {
    const term = query.trim().toLowerCase()
    if (!term) return catalogue.slice(0, 40)
    return catalogue
      .filter(
        (product) =>
          product.name.toLowerCase().includes(term) ||
          product.sku.toLowerCase().includes(term) ||
          (product.barcode ?? '').includes(term),
      )
      .slice(0, 40)
  }, [catalogue, query])

  const totals = useMemo(() => {
    let net = 0
    let tax = 0
    for (const line of cart) {
      const lineNet = line.product.sellingPrice * line.quantity
      net += lineNet
      tax += lineNet * ((line.product.vatRate ?? 0) / 100)
    }
    return { net, tax, total: net + tax }
  }, [cart])

  function addToCart(product: ProductRow) {
    if (product.currentStock <= 0) {
      toast.error(`${product.name} is out of stock`)
      return
    }
    setCart((previous) => {
      const existing = previous.find((line) => line.product.id === product.id)
      if (existing) {
        if (existing.quantity >= product.currentStock) {
          toast.error(`Only ${product.currentStock} ${product.unit} of ${product.name} in stock`)
          return previous
        }
        return previous.map((line) =>
          line.product.id === product.id ? { ...line, quantity: line.quantity + 1 } : line,
        )
      }
      return [...previous, { product, quantity: 1 }]
    })
  }

  function changeQuantity(productId: number, delta: number) {
    setCart((previous) =>
      previous
        .map((line) => {
          if (line.product.id !== productId) return line
          const next = line.quantity + delta
          if (next > line.product.currentStock) {
            toast.error(`Only ${line.product.currentStock} in stock`)
            return line
          }
          return { ...line, quantity: next }
        })
        .filter((line) => line.quantity > 0),
    )
  }

  async function checkout() {
    if (cart.length === 0) return
    if (method === 'CREDIT' && !customerId) {
      toast.error('A credit sale needs a customer account to bill')
      return
    }

    setSubmitting(true)
    try {
      const { data } = await api.post('/sales/checkout', {
        customerId: customerId ? Number(customerId) : null,
        lines: cart.map((line) => ({
          productId: line.product.id,
          quantity: line.quantity,
          discount: 0,
        })),
        paymentMethod: method,
        amountPaid: totals.total,
      })

      setLastReceipt({ invoice: data.invoiceNumber, total: data.total })
      toast.success(`Sale ${data.invoiceNumber} recorded`, {
        description: `${moneyWhole(data.total, currency)} · stock and ledger updated`,
      })
      setCart([])
      setCustomerId('')
      // Refresh so the stock figures on the tiles reflect the sale just made.
      products.reload()
    } catch (caught) {
      toast.error('Could not complete the sale', { description: errorMessage(caught) })
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <>
      <PageHeader
        title="Point of Sale"
        subtitle="Scan or tap a product, take payment, and stock plus the movement ledger update together"
      />

      <div className="grid gap-4 lg:grid-cols-3">
        {/* Catalogue */}
        <Card className="lg:col-span-2">
          <CardHeader className="border-b py-4">
            <div className="relative">
              <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
              <Input
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                placeholder="Search by name, SKU or scan a barcode…"
                className="pl-9"
                autoFocus
              />
            </div>
          </CardHeader>
          <CardContent className="p-3">
            <DataState
              loading={products.loading}
              error={products.error}
              empty={filtered.length === 0}
              onRetry={products.reload}
              emptyTitle="No matching products"
            >
              <ScrollArea className="h-[calc(100vh-22rem)]">
                <div className="grid grid-cols-2 gap-2 sm:grid-cols-3 xl:grid-cols-4">
                  {filtered.map((product) => (
                    <button
                      key={product.id}
                      type="button"
                      onClick={() => addToCart(product)}
                      disabled={product.outOfStock}
                      className={cn(
                        'rounded-lg border p-3 text-left transition-colors',
                        product.outOfStock
                          ? 'cursor-not-allowed opacity-50'
                          : 'hover:border-primary hover:bg-accent',
                      )}
                    >
                      <p className="line-clamp-2 text-xs font-semibold leading-snug">{product.name}</p>
                      <p className="numeric mt-1.5 text-sm font-bold text-primary">
                        {moneyWhole(product.sellingPrice, currency)}
                      </p>
                      <div className="mt-1 flex items-center gap-1">
                        <Badge
                          variant={
                            product.outOfStock ? 'danger' : product.lowStock ? 'warning' : 'muted'
                          }
                          className="text-[10px]"
                        >
                          {product.outOfStock ? 'Out' : `${product.currentStock} ${product.unit}`}
                        </Badge>
                      </div>
                    </button>
                  ))}
                </div>
              </ScrollArea>
            </DataState>
          </CardContent>
        </Card>

        {/* Basket */}
        <Card className="flex flex-col">
          <CardHeader className="flex-row items-center justify-between space-y-0 border-b py-4">
            <CardTitle className="flex items-center gap-2 text-sm">
              <ShoppingCart className="h-4 w-4" /> Basket
              {cart.length > 0 && <Badge variant="default">{cart.length}</Badge>}
            </CardTitle>
            {cart.length > 0 && (
              <Button variant="ghost" size="sm" onClick={() => setCart([])}>
                <Trash2 /> Clear
              </Button>
            )}
          </CardHeader>

          <CardContent className="flex-1 p-0">
            {cart.length === 0 ? (
              <div className="flex h-full min-h-[200px] flex-col items-center justify-center px-6 text-center">
                <ShoppingCart className="mb-2 h-7 w-7 text-muted-foreground/40" />
                <p className="text-xs text-muted-foreground">
                  Tap a product to start a sale
                </p>
                {lastReceipt && (
                  <div className="mt-5 w-full rounded-lg border border-emerald-200 bg-emerald-50 p-3 dark:border-emerald-900 dark:bg-emerald-950/40">
                    <p className="flex items-center justify-center gap-1.5 text-[11px] font-semibold text-emerald-800 dark:text-emerald-300">
                      <Receipt className="h-3.5 w-3.5" /> Last sale
                    </p>
                    <p className="numeric mt-1 font-mono text-xs">{lastReceipt.invoice}</p>
                    <p className="numeric text-sm font-bold">
                      {moneyWhole(lastReceipt.total, currency)}
                    </p>
                  </div>
                )}
              </div>
            ) : (
              <ScrollArea className="max-h-[320px]">
                <div className="divide-y">
                  {cart.map((line) => (
                    <div key={line.product.id} className="flex items-center gap-2 p-3">
                      <div className="min-w-0 flex-1">
                        <p className="truncate text-xs font-medium">{line.product.name}</p>
                        <p className="numeric text-[11px] text-muted-foreground">
                          {moneyWhole(line.product.sellingPrice, currency)} ×{' '}
                          {number(line.quantity)}
                        </p>
                      </div>
                      <div className="flex shrink-0 items-center gap-1">
                        <Button
                          variant="outline"
                          size="icon"
                          className="h-7 w-7"
                          onClick={() => changeQuantity(line.product.id, -1)}
                        >
                          <Minus className="h-3 w-3" />
                        </Button>
                        <span className="numeric w-7 text-center text-xs font-semibold">
                          {line.quantity}
                        </span>
                        <Button
                          variant="outline"
                          size="icon"
                          className="h-7 w-7"
                          onClick={() => changeQuantity(line.product.id, 1)}
                        >
                          <Plus className="h-3 w-3" />
                        </Button>
                      </div>
                      <span className="numeric w-20 shrink-0 text-right text-xs font-bold">
                        {moneyWhole(line.product.sellingPrice * line.quantity, currency)}
                      </span>
                    </div>
                  ))}
                </div>
              </ScrollArea>
            )}
          </CardContent>

          <div className="space-y-3 border-t p-4">
            <div>
              <Label>Customer</Label>
              <Select value={customerId} onValueChange={setCustomerId}>
                <SelectTrigger className="mt-1.5">
                  <SelectValue placeholder="Walk-in customer" />
                </SelectTrigger>
                <SelectContent>
                  {(customers.data?.content ?? []).map((customer) => (
                    <SelectItem key={customer.id} value={String(customer.id)}>
                      {customer.name}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>

            <div>
              <Label>Payment method</Label>
              <Select value={method} onValueChange={setMethod}>
                <SelectTrigger className="mt-1.5">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {PAYMENT_METHODS.map((option) => (
                    <SelectItem key={option} value={option}>
                      {option
                        .toLowerCase()
                        .split('_')
                        .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
                        .join(' ')}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              {method === 'CREDIT' && (
                <p className="mt-1 text-[11px] text-amber-600">
                  A credit sale is billed to the customer's account and checked against their
                  credit limit.
                </p>
              )}
            </div>

            <div className="space-y-1 border-t pt-3 text-sm">
              <div className="flex justify-between text-muted-foreground">
                <span>Subtotal</span>
                <span className="numeric">{moneyWhole(totals.net, currency)}</span>
              </div>
              <div className="flex justify-between text-muted-foreground">
                <span>VAT</span>
                <span className="numeric">{moneyWhole(totals.tax, currency)}</span>
              </div>
              <div className="flex justify-between border-t pt-1.5 text-base font-bold">
                <span>Total</span>
                <span className="numeric">{moneyWhole(totals.total, currency)}</span>
              </div>
            </div>

            <Button
              className="w-full"
              size="lg"
              disabled={cart.length === 0 || submitting}
              onClick={checkout}
            >
              {submitting ? (
                <>
                  <Loader2 className="animate-spin" /> Recording…
                </>
              ) : (
                <>
                  <Receipt /> Complete sale
                </>
              )}
            </Button>
          </div>
        </Card>
      </div>
    </>
  )
}
