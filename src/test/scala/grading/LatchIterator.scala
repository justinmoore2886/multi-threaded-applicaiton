package grading

import java.util.concurrent.{ CopyOnWriteArraySet, CountDownLatch }
import scala.collection.JavaConverters._

class LatchIterator[A](source: Iterator[A], val latch: CountDownLatch = new CountDownLatch(1))
  extends Iterator[A] {

  private[this] val callerThreads = new CopyOnWriteArraySet[Thread]

  def callers: Set[Thread] = callerThreads.asScala.toSet

  def hasNext: Boolean = {
    val nonEmpty = source.hasNext
    nonEmpty || {
      latch.await()
      false
    }
  }

  def next(): A = {
    callerThreads.add(Thread.currentThread)
    source.next()
  }
}
