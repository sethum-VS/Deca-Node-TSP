# ── Build stage ──────────────────────────────────────────
FROM golang:1.26-alpine AS builder

WORKDIR /app

COPY go.mod go.sum* ./
RUN go mod download 2>/dev/null || true

COPY . .

RUN CGO_ENABLED=0 GOOS=linux go build -o /server ./cmd/server

# Fix Alpine glibc compatibility for the Tailwind standalone binary
RUN apk add --no-cache libc6-compat

# Download Tailwind CLI v3.4.17 (pinned — v4 uses incompatible CSS engine) and build CSS
RUN mkdir -p ./public/css \
    && wget https://github.com/tailwindlabs/tailwindcss/releases/download/v3.4.17/tailwindcss-linux-x64 \
    && chmod +x tailwindcss-linux-x64 \
    && ./tailwindcss-linux-x64 -i ./internal/static/css/input.css -o ./public/css/tailwind.css --minify

# ── Runtime stage ────────────────────────────────────────
FROM alpine:3.20

RUN apk --no-cache add ca-certificates

WORKDIR /app

COPY --from=builder /server .
COPY --from=builder /app/internal/templates ./internal/templates
COPY --from=builder /app/public ./public

CMD ["./server"]
