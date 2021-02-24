package search

/**
  * Mutable thread-safe queue.  Elements can be added at the back or at the front of the queue.
  * Queues are unbounded (adding operations never block).
  * Instances of this class can safely be shared among threads.
  *
  * @constructor creates an empty queue
  *
  * Modified this with a comment so I can re-submit program 5. Weird tag issue
  *
  */
class SafeQueue[A] {
  private var in  = List[A]()
  private var out = List[A]()
  private val semaphore = new Semaphore(0)

  /** Add to the back. */
  def addLast(elem: A): Unit = {
    this.synchronized(in = elem :: in)
    semaphore.release()
  }

  /** Add to the front. */
  def addFirst(elem: A): Unit = {
    this.synchronized(out = elem :: out)
    semaphore.release()
  }

  /**
    * Takes the first element from the front.  The element is removed from the queue.
    * If the queue is empty, this method is blocking.
    */
  @throws[InterruptedException]("if a thread is interrupted while blocked on take")
  def take(): A = {
      try {
          semaphore.acquire()

          this.synchronized {

            // Queue isn't entirely empty
            if (out.isEmpty && in.nonEmpty) {
              out = in.reverse
              in = Nil
            }

            // Queue isn't empty at all
            val elem = out.head
            out = out.tail
            elem
          }
      }
      catch {
        case _: InterruptedException => throw new InterruptedException("blocked thread interrupted in take()")
      }
  }
}

/** Companion object of the [[search.SafeQueue]] class. */
object SafeQueue {

  /** A new empty queue. */
  def empty[A] = new SafeQueue[A]
}
