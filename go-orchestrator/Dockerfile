# ── Build stage ──────────────────────────────────────────
FROM golang:1.26-alpine AS builder

WORKDIR /app

COPY go.mod go.sum* ./
RUN go mod download 2>/dev/null || true

COPY . .

RUN CGO_ENABLED=0 GOOS=linux go build -o /server ./cmd/server

# ── Runtime stage ────────────────────────────────────────
FROM alpine:3.20

RUN apk --no-cache add ca-certificates

WORKDIR /app

COPY --from=builder /server .
COPY --from=builder /app/internal/infrastructure/templates ./internal/infrastructure/templates
COPY --from=builder /app/internal/infrastructure/static    ./internal/infrastructure/static

EXPOSE 8080

ENV PORT=8080

CMD ["./server"]
