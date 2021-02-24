package search

/**
  * A simple semaphore.  It is a `java.util.concurrent.Semaphore` in ''unfair'' mode.
  *
  * Instances of this class are ''thread-safe''.
  *
  * @see `java.util.concurrent.Semaphore`
  *
  * @param n the initial number of permits
  */
class Semaphore(n: Int) {

  private[this] val sem = new java.util.concurrent.Semaphore(n)

  /** Acquires a permit.  This method blocks if no permit is available. */
  @throws[InterruptedException]("if a thread is interrupted while waiting on acquire")
  def acquire(): Unit = sem.acquire()

  /**
    * Releases a permit.  This might unblock a thread blocked on `acquire`.  Any thread can
    * "release" a permit, including threads that never acquired one.
    */
  def release(): Unit = sem.release()
}
