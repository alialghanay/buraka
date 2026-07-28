# 6. Architecture Diagrams

Course-mandatory diagrams are the System Context Diagram and the Component Diagram. Sequence diagrams and a data flow diagram are optional per the course and are included because they clarify the payment and event flows.

## 6.1 System Context Diagram (Required)

```mermaid
flowchart TB
    guest["Guest<br/>(Flutter mobile / React web)"]
    customer["Customer<br/>(Flutter mobile / React web)"]
    admin["Admin<br/>(React web)"]
    buraka["Buraka Online Shopping System<br/>API Gateway + 4 microservices"]
    moamalat["Moamalat Payment Gateway<br/>hosted payment page + signed callbacks"]
    smtp["Email Provider (SMTP)<br/>transactional email"]

    guest -->|"Browses catalog (HTTPS/REST)"| buraka
    customer -->|"Manages account, cart, orders,<br/>pays, writes reviews (HTTPS/REST)"| buraka
    admin -->|"Manages products, categories, brands (HTTPS/REST)"| buraka
    buraka -->|"Redirects customer to hosted payment page;<br/>receives HMAC-SHA256-signed payment callbacks"| moamalat
    buraka -->|"Sends password-reset and order-confirmation email"| smtp
```

## 6.2 Component Diagram (Required)

```mermaid
flowchart TB
    subgraph clients["Clients"]
        flutter["Flutter Mobile App"]
        react["React Web App"]
    end

    subgraph edge["Edge"]
        gateway["api-gateway<br/>Spring Cloud Gateway<br/>routing · JWT validation · rate limiting · CORS"]
    end

    subgraph services["Microservices (Spring Boot)"]
        auth["auth-user-service<br/>identity, tokens, profiles"]
        catalog["catalog-service<br/>products, categories, brands,<br/>stock & rating aggregates"]
        order["order-service<br/>cart, checkout, orders,<br/>Moamalat payment coordination"]
        review["review-service<br/>reviews and ratings"]
    end

    subgraph data["Data — database per service"]
        authdb[("buraka_auth_db<br/>PostgreSQL — USER")]
        catalogdb[("buraka_catalog_db<br/>PostgreSQL — PRODUCT, CATEGORY, BRAND")]
        orderdb[("buraka_order_db<br/>PostgreSQL — CART, CART_ITEM, ORDER, ORDER_ITEM")]
        reviewdb[("buraka_review_db<br/>PostgreSQL — REVIEW")]
        redis[("redis<br/>catalog cache + refresh tokens")]
    end

    mq[["rabbitmq<br/>event bus"]]
    moamalat["Moamalat Payment Gateway"]
    smtp["Email Provider (SMTP)"]

    flutter -->|"HTTPS"| gateway
    react -->|"HTTPS"| gateway
    gateway --> auth
    gateway --> catalog
    gateway --> order
    gateway --> review

    auth --> authdb
    auth --> redis
    catalog --> catalogdb
    catalog --> redis
    order --> orderdb
    review --> reviewdb

    order -->|"REST: product price/availability snapshot at checkout"| catalog
    review -->|"REST: purchase verification"| order

    auth -->|"UserRegistered · PasswordResetRequested"| mq
    order -->|"OrderPlaced · PaymentConfirmed · PaymentFailed · OrderCancelled"| mq
    review -->|"ReviewCreated · ReviewDeleted"| mq
    mq -->|"stock & rating events"| catalog
    mq -->|"notification events"| auth
    mq -->|"PaymentConfirmed"| order

    order -->|"hosted payment page redirect + HMAC-SHA256 callback"| moamalat
    auth -->|"reset / welcome email"| smtp
    order -->|"order confirmation email"| smtp
```

## 6.3 Sequence Diagram: Checkout and Moamalat Payment (Optional — included)

```mermaid
sequenceDiagram
    actor C as Customer
    participant FE as Flutter/React Client
    participant GW as api-gateway
    participant OS as order-service
    participant CS as catalog-service
    participant DB as buraka_order_db
    participant MQ as rabbitmq
    participant MM as Moamalat

    C->>FE: Checkout cart
    FE->>GW: POST /api/orders (JWT)
    GW->>GW: Validate JWT (RS256)
    GW->>OS: Forward request with identity headers
    OS->>CS: GET /api/products/{ids} — price & availability snapshot
    CS-->>OS: Product snapshots
    OS->>DB: Persist ORDER (isPaid=false) + ORDER_ITEMs (outbox: OrderPlaced)
    OS->>MQ: Publish OrderPlaced
    OS->>OS: Build Moamalat payment URL (HMAC-SHA256 secure hash)
    OS-->>FE: 201 Created + paymentUrl
    FE->>MM: Redirect customer to hosted payment page
    C->>MM: Pay with card
    MM->>OS: POST /api/payments/moamalat/callback (HMAC-SHA256 signed)
    OS->>OS: Verify HMAC; on success set isPaid=true, paidAt
    OS->>MQ: Publish PaymentConfirmed (or PaymentFailed)
    MQ-->>CS: Consume PaymentConfirmed → confirm stock decrement, sold += qty
    MQ-->>OS: Consume PaymentConfirmed → send order confirmation email
```

## 6.4 Sequence Diagram: Review Submission and Rating Update (Optional — included)

```mermaid
sequenceDiagram
    actor C as Customer
    participant GW as api-gateway
    participant RS as review-service
    participant OS as order-service
    participant RDB as buraka_review_db
    participant MQ as rabbitmq
    participant CS as catalog-service

    C->>GW: POST /api/reviews (JWT)
    GW->>RS: Forward with identity headers
    RS->>OS: GET /api/orders?productId={id}&paid=true (purchase verification)
    OS-->>RS: 200 OK (purchased) — otherwise 403
    RS->>RDB: Persist REVIEW
    RS->>MQ: Publish ReviewCreated {productId, rating}
    MQ-->>CS: Consume → recalculate ratingsAverage and ratingsQuantity; invalidate product cache keys
```

## 6.5 Data Flow Diagram — Level 0 (Optional — included)

```mermaid
flowchart LR
    customer(["Customer"])
    admin(["Admin"])
    moamalat(["Moamalat"])

    subgraph system["Buraka System"]
        p1["1.0 Identity &<br/>Profile"]
        p2["2.0 Catalog<br/>Browsing"]
        p3["3.0 Cart, Checkout &<br/>Payment"]
        p5["5.0 Reviews &<br/>Ratings"]
        d1[("User Data")]
        d2[("Catalog Data")]
        d3[("Order Data")]
        d4[("Review Data")]
    end

    customer -->|"credentials, profile"| p1
    customer -->|"search, filters"| p2
    customer -->|"cart ops, checkout, payment"| p3
    p3 <-->|"redirect / signed callback"| moamalat
    customer -->|"reviews"| p5
    admin -->|"catalog management"| p2

    p1 <--> d1
    p2 <--> d2
    p3 <--> d3
    p5 <--> d4
    p5 -->|"rating events"| p2
    p3 -->|"stock events"| p2
```

Level-0 DFD showing logical processes aligned with the microservice decomposition; payment coordination is part of order-service.
