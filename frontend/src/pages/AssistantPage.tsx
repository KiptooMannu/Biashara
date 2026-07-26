import { useEffect, useRef, useState } from 'react'
import { Database, Loader2, Send, Sparkles, User as UserIcon } from 'lucide-react'
import { api, errorMessage } from '@/lib/api'
import { useAuth } from '@/store/auth'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { PageHeader } from '@/components/shared/PageHeader'
import { cn } from '@/lib/utils'

interface Turn {
  role: 'USER' | 'ASSISTANT'
  content: string
  dataPoints?: string[]
  dataSource?: string
  followUps?: string[]
}

/**
 * The business assistant.
 *
 * The answer panel shows the figures the reply was derived from and names the
 * query behind it. That is the point: the assistant is auditable rather than
 * merely fluent, so an owner can check a claim before acting on it.
 */
export default function AssistantPage() {
  const user = useAuth((state) => state.user)
  const [turns, setTurns] = useState<Turn[]>([])
  const [question, setQuestion] = useState('')
  const [conversationId, setConversationId] = useState<string | null>(null)
  const [thinking, setThinking] = useState(false)
  const [suggestions, setSuggestions] = useState<string[]>([])
  const endRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    api
      .get<{ questions: string[] }>('/ai/suggested-questions')
      .then(({ data }) => setSuggestions(data.questions))
      .catch(() => setSuggestions([]))
  }, [])

  useEffect(() => {
    endRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [turns, thinking])

  async function ask(text: string) {
    const trimmed = text.trim()
    if (!trimmed || thinking) return

    setTurns((previous) => [...previous, { role: 'USER', content: trimmed }])
    setQuestion('')
    setThinking(true)

    try {
      const { data } = await api.post('/ai/ask', { question: trimmed, conversationId })
      setConversationId(data.conversationId)
      setTurns((previous) => [
        ...previous,
        {
          role: 'ASSISTANT',
          content: data.answer,
          dataPoints: data.dataPoints,
          dataSource: data.dataSource,
          followUps: data.suggestedFollowUps,
        },
      ])
    } catch (caught) {
      setTurns((previous) => [
        ...previous,
        { role: 'ASSISTANT', content: errorMessage(caught, 'I could not answer that just now.') },
      ])
    } finally {
      setThinking(false)
    }
  }

  return (
    <>
      <PageHeader
        title="AI Assistant"
        subtitle="Ask about your business. Every answer is computed from your live data and shows its working."
      />

      <Card className="flex h-[calc(100vh-14rem)] flex-col">
        <CardContent className="flex-1 overflow-y-auto p-5">
          {turns.length === 0 ? (
            <div className="flex h-full flex-col items-center justify-center text-center">
              <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-2xl bg-primary/10">
                <Sparkles className="h-6 w-6 text-primary" />
              </div>
              <h3 className="text-base font-bold">
                What would you like to know, {user?.fullName?.split(' ')[0]}?
              </h3>
              <p className="mt-1.5 max-w-md text-sm text-muted-foreground">
                I answer by running queries against your business rather than guessing, so
                I can only tell you what your figures actually support.
              </p>

              <div className="mt-6 grid w-full max-w-2xl gap-2 sm:grid-cols-2">
                {suggestions.map((suggestion) => (
                  <button
                    key={suggestion}
                    type="button"
                    onClick={() => ask(suggestion)}
                    className="rounded-lg border p-3 text-left text-xs font-medium transition-colors hover:border-primary hover:bg-accent"
                  >
                    {suggestion}
                  </button>
                ))}
              </div>
            </div>
          ) : (
            <div className="mx-auto max-w-3xl space-y-5">
              {turns.map((turn, index) => (
                <div
                  key={index}
                  className={cn('flex gap-3', turn.role === 'USER' && 'justify-end')}
                >
                  {turn.role === 'ASSISTANT' && (
                    <div className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-primary/10">
                      <Sparkles className="h-4 w-4 text-primary" />
                    </div>
                  )}

                  <div className={cn('min-w-0 max-w-[85%]', turn.role === 'USER' && 'order-first')}>
                    <div
                      className={cn(
                        'rounded-xl px-4 py-3 text-sm',
                        turn.role === 'USER'
                          ? 'bg-primary text-primary-foreground'
                          : 'border bg-card',
                      )}
                    >
                      <p className="whitespace-pre-wrap leading-relaxed">{turn.content}</p>
                    </div>

                    {/* The working: the figures behind the answer. */}
                    {turn.dataPoints && turn.dataPoints.length > 0 && (
                      <div className="mt-2 rounded-lg border bg-muted/40 p-3">
                        <p className="flex items-center gap-1.5 text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                          <Database className="h-3 w-3" /> Figures used
                        </p>
                        <ul className="mt-1.5 space-y-0.5">
                          {turn.dataPoints.map((point) => (
                            <li key={point} className="numeric text-[11px] text-muted-foreground">
                              • {point}
                            </li>
                          ))}
                        </ul>
                        {turn.dataSource && (
                          <p className="mt-2 font-mono text-[10px] text-muted-foreground/70">
                            source: {turn.dataSource}
                          </p>
                        )}
                      </div>
                    )}

                    {turn.followUps && turn.followUps.length > 0 && (
                      <div className="mt-2 flex flex-wrap gap-1.5">
                        {turn.followUps.map((followUp) => (
                          <button
                            key={followUp}
                            type="button"
                            onClick={() => ask(followUp)}
                            className="rounded-full border px-2.5 py-1 text-[11px] font-medium transition-colors hover:border-primary hover:bg-accent"
                          >
                            {followUp}
                          </button>
                        ))}
                      </div>
                    )}
                  </div>

                  {turn.role === 'USER' && (
                    <div className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-muted">
                      <UserIcon className="h-4 w-4 text-muted-foreground" />
                    </div>
                  )}
                </div>
              ))}

              {thinking && (
                <div className="flex gap-3">
                  <div className="mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-primary/10">
                    <Sparkles className="h-4 w-4 text-primary" />
                  </div>
                  <div className="flex items-center gap-2 rounded-xl border bg-card px-4 py-3">
                    <Loader2 className="h-3.5 w-3.5 animate-spin text-primary" />
                    <span className="text-xs text-muted-foreground">
                      Querying your business data…
                    </span>
                  </div>
                </div>
              )}

              <div ref={endRef} />
            </div>
          )}
        </CardContent>

        <div className="border-t p-4">
          <form
            className="mx-auto flex max-w-3xl gap-2"
            onSubmit={(event) => {
              event.preventDefault()
              void ask(question)
            }}
          >
            <Input
              value={question}
              onChange={(event) => setQuestion(event.target.value)}
              placeholder="Why did profits fall? What should I reorder?"
              disabled={thinking}
            />
            <Button type="submit" disabled={thinking || !question.trim()}>
              {thinking ? <Loader2 className="animate-spin" /> : <Send />}
            </Button>
          </form>
          <p className="mt-2 text-center text-[11px] text-muted-foreground">
            Rule-based over live queries — no language model, so no invented numbers.
            <Badge variant="muted" className="ml-2">
              Spring AI ready
            </Badge>
          </p>
        </div>
      </Card>
    </>
  )
}
