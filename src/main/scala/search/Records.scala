package search

/** An iterator of words and their locations. */
trait Records {
  /** The word -> locations pairs. */
  val iterator: Iterator[(String, Locations)]

  /** Resource release, in case the iterator reads from I/O. */
  def close(): Unit
}

object Records {
  def fromIterator(iterator: Iterator[(String, Locations)], closeFn: () => Unit): Records =
    new IteratorRecords(iterator, closeFn)

  def fromMap(m: Map[String, Locations]): Records = fromIterator(m.iterator, () => ())

  def apply(pairs: (String, Locations)*): Records = fromIterator(pairs.iterator, () => ())
}

private class IteratorRecords(val iterator: Iterator[(String, Locations)], closeFn: () => Unit)
  extends Records {
  def close() = closeFn()
}
