package grading

import java.util.concurrent.{ CopyOnWriteArraySet, CountDownLatch }

import search.Tokens

import scala.collection.AbstractIterator
import scala.collection.JavaConverters._

class LatchTokens[A](tokens: Tokens[A], val latch: CountDownLatch = new CountDownLatch(1)) extends Tokens[A] {

  def source: A = tokens.source
  def close(): Unit = tokens.close()
  val iterator = new LatchIterator[String](tokens.iterator, latch)
  def callers: Set[Thread] = iterator.callers
}
