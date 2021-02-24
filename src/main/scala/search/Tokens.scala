package search

import java.net.URL

import scala.collection.AbstractIterator
import scala.io.Source

/** File tokens (words). */
trait Tokens[+A] {
  /** The words. */
  val iterator: Iterator[String]

  /** Resource release, in case the iterator reads from I/O. */
  def close(): Unit

  /** The source of the words (file, url, etc.), for indexing purposes. */
  def source: A
}

/** Self-closing iterators, in case users forget to close. */
private class IteratorTokens[A](
                                 val source: A,
                                 iter:       Iterator[String],
                                 closeFn:    => () => Unit
                               ) extends Tokens[A] {
  def close() = closeFn()

  val iterator: Iterator[String] = new AbstractIterator[String] {
    def hasNext = {
      val nonEmpty = iter.hasNext
      if (!nonEmpty)
        closeFn()
      nonEmpty
    }

    def next() = iter.next()
  }
}

object Tokens {
  /** Tokens from a file (one word per line). */
  def fromURL(url: URL): Tokens[URL] = {
    val source = Source.fromURL(url)
    fromIterator(url, source.getLines(), () => source.close())
  }

  /** Tokens from an iterator. */
  def fromIterator[A](
                       source:   A,
                       iterator: Iterator[String],
                       closeFn:  () => Unit       = () => ()
                     ): Tokens[A] =
    new IteratorTokens(source, iterator, closeFn)

  /** Tokens from words. */
  def apply[A](source: A)(tokens: String*): Tokens[A] = fromIterator(source, tokens.iterator)
}
