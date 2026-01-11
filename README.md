# Multithreaded Connection Pool (Java)

This project demonstrates a **multithreaded connection pool** implemented using Java concurrency primitives (`ReentrantLock` and `Condition`).

---

## Key Features

- Fixed-size connection pool
- Thread-safe acquire/release logic
- Waiting with timeout when no connections are available
- Proper shutdown handling
- Simulation of concurrent clients using `ExecutorService`

---

## Core Concepts Used

- `ReentrantLock` and `Condition` as a monitor replacement
- `Callable` and `Future` for task execution and result collection
- `ThreadLocalRandom` to simulate variable work duration per client
- Graceful handling of timeouts and pool shutdown

---

## Project Structure


config – application configuration and loading
pool – connection pool implementation
client – client worker simulation
entity – database connection abstraction
app – application entry point



---

## How It Works

1. The application loads configuration from a `.properties` file.
2. A fixed-size connection pool is initialized.
3. Multiple client tasks attempt to acquire connections concurrently.
4. If no connection is available, clients wait up to a configured timeout.
5. After completion, connections are released back to the pool.
6. On shutdown, all waiting threads are notified and resources are closed.

---

## Purpose

This project is intended for **educational purposes** and demonstrates:

- Low-level concurrency control
- Condition-based synchronization
- Safe resource management in multithreaded Java applications
