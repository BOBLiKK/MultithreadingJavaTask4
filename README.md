Multithreaded Connection Pool (Java)

This project demonstrates a multithreaded connection pool implemented using Java concurrency primitives (ReentrantLock and Condition).

Key Features

Fixed-size connection pool

Thread-safe acquire/release logic

Waiting with timeout when no connections are available

Proper shutdown handling

Simulation of concurrent clients using ExecutorService

Core Concepts Used

ReentrantLock and Condition as a monitor replacement

Callable and Future for task execution and result collection

ThreadLocalRandom to simulate variable work duration per client

Graceful handling of timeouts and pool shutdown

Project Structure

config – application configuration and loading

pool – connection pool implementation

client – client worker simulation

entity – database connection abstraction

app – application entry point

How It Works

The application loads configuration from a .properties file.

A fixed-size connection pool is initialized.

Multiple client tasks attempt to acquire connections concurrently.

If no connection is available, clients wait up to a configured timeout.

After completion, connections are released back to the pool.

On shutdown, all waiting threads are notified and resources are closed.

Purpose

The project is intended for educational purposes to illustrate:

low-level concurrency control

condition-based synchronization

safe resource management in multithreaded Java applications
